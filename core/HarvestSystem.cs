using System.Collections.Generic;

namespace Samjae.Core;

public enum HarvestEventKind : byte
{
    Pickup = 1,
    Deposit = 2,
}

public readonly record struct HarvestEvent(HarvestEventKind Kind, int Faction, int Worker, int Node, int Dropoff, ResourceKind Resource, int Amount);

public readonly record struct NodeHarvest(int Node, int Harvested, int Remaining, bool Depleted);

/// <summary>
/// Workers pick up one load at a node, carry it to the nearest compatible dropoff, and go back. Reach is measured
/// from body and footprint edges, the same as weapons, so a worker standing next to a node or a building can use it.
/// </summary>
public sealed class HarvestSystem : ITickSystem
{
    private readonly World _world;
    private readonly GameContent _content;
    private readonly Reach _reach;
    private readonly List<HarvestEvent> _events = new();
    private readonly List<NodeHarvest> _nodes = new();

    public HarvestSystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
        _reach = new Reach(world, content);
    }

    /// <summary>Pickups and deposits this tick, for presentation. Not state.</summary>
    public IReadOnlyList<HarvestEvent> Events => _events;

    /// <summary>One entry per node harvested this tick, summed over workers.</summary>
    public IReadOnlyList<NodeHarvest> Nodes => _nodes;

    public int PickedUp(int faction, ResourceKind kind)
    {
        int total = 0;
        foreach (HarvestEvent e in _events)
        {
            total += e.Kind == HarvestEventKind.Pickup && e.Faction == faction && e.Resource == kind ? e.Amount : 0;
        }

        return total;
    }

    public void Tick()
    {
        _events.Clear();
        _nodes.Clear();
        foreach (int id in _world.Harvesters)
        {
            if (!_world.Tags.TryGet(id, out UnitTag tag) || !_world.Positions.TryGet(id, out Position position))
            {
                continue;
            }

            Harvester harvester = _world.Harvesters.Get(id);
            if (harvester.CargoAmount > 0)
            {
                TickReturnTrip(id, tag.Faction, position, harvester);
            }
            else
            {
                TickGather(id, tag.Faction, position, harvester);
            }
        }
    }

    private void TickGather(int id, int faction, Position position, Harvester harvester)
    {
        int nodeId = harvester.TargetNodeId;
        if (nodeId < 0 || !_world.ResourceNodes.TryGet(nodeId, out ResourceNode node) || node.Remaining <= 0
            || !_world.Positions.TryGet(nodeId, out Position nodePosition))
        {
            return;
        }

        if (!_reach.IsInReach(id, position, nodeId, harvester.Range))
        {
            EnsureLeadingMove(id, nodePosition);
            return;
        }

        int yieldCap = node.YieldPerTick > 0 ? node.YieldPerTick : int.MaxValue;
        int amount = System.Math.Min(System.Math.Min(harvester.HarvestPerTick, harvester.CargoCapacity), System.Math.Min(node.Remaining, yieldCap));
        if (amount <= 0)
        {
            return;
        }

        node.Remaining -= amount;
        harvester.CargoKind = node.Kind;
        harvester.CargoAmount = amount;
        harvester.ReturnTargetId = NearestDropoff(position, faction, node.Kind);
        if (_world.Positions.TryGet(harvester.ReturnTargetId, out Position dropoff))
        {
            EnsureLeadingMove(id, dropoff);
        }

        RecordNode(nodeId, amount, node.Remaining);
        _events.Add(new HarvestEvent(HarvestEventKind.Pickup, faction, id, nodeId, harvester.ReturnTargetId, node.Kind, amount));
    }

    private void TickReturnTrip(int id, int faction, Position position, Harvester harvester)
    {
        ResourceKind kind = harvester.CargoKind ?? ResourceKind.Ore;
        if (harvester.ReturnTargetId < 0 || !_world.Footprints.Has(harvester.ReturnTargetId))
        {
            harvester.ReturnTargetId = NearestDropoff(position, faction, kind);
        }

        int dropoffId = harvester.ReturnTargetId;
        if (dropoffId < 0 || !_world.Positions.TryGet(dropoffId, out Position dropoff))
        {
            return;
        }

        if (!_reach.IsInReach(id, position, dropoffId, harvester.DropoffRange))
        {
            EnsureLeadingMove(id, dropoff);
            return;
        }

        ResourceStockpile stockpile = _world.Stockpile(faction);
        if (kind == ResourceKind.Gas)
        {
            stockpile.Gas += harvester.CargoAmount;
        }
        else
        {
            stockpile.Ore += harvester.CargoAmount;
        }

        _events.Add(new HarvestEvent(HarvestEventKind.Deposit, faction, id, harvester.TargetNodeId, dropoffId, kind, harvester.CargoAmount));
        harvester.CargoKind = null;
        harvester.CargoAmount = 0;
        harvester.ReturnTargetId = -1;
        if (_world.ResourceNodes.TryGet(harvester.TargetNodeId, out ResourceNode next) && next.Remaining > 0
            && _world.Positions.TryGet(harvester.TargetNodeId, out Position nextPosition))
        {
            EnsureLeadingMove(id, nextPosition);
        }
    }

    /// <summary>Puts a move to the target at the front of the queue, replacing a different leading move.</summary>
    private void EnsureLeadingMove(int id, Position target)
    {
        if (!_world.Orders.TryGet(id, out List<Order>? orders))
        {
            return;
        }

        if (orders.Count > 0 && orders[0] is Order.Move move)
        {
            if (move.X == target.X && move.Y == target.Y)
            {
                return;
            }

            orders.RemoveAt(0);
        }

        orders.Insert(0, new Order.Move(target.X, target.Y));
    }

    /// <summary>Prefers a dropoff that accepts the resource, then any dropoff, then any own building. Ties go to the lowest id.</summary>
    private int NearestDropoff(Position from, int faction, ResourceKind kind)
    {
        (int compatible, int dropoff, int any) = (-1, -1, -1);
        (long compatibleDistance, long dropoffDistance, long anyDistance) = (long.MaxValue, long.MaxValue, long.MaxValue);
        foreach (int building in _world.Footprints)
        {
            if (!_world.Tags.TryGet(building, out UnitTag tag) || tag.Faction != faction || !_world.Positions.TryGet(building, out Position at))
            {
                continue;
            }

            long distance = Fix.LengthSquaredRaw(at.X - from.X, at.Y - from.Y);
            Keep(ref any, ref anyDistance, building, distance);
            if (_content.BuildingOrNull(tag.TypeId) is BuildingDef { SupportsDropoff: true } def)
            {
                Keep(ref dropoff, ref dropoffDistance, building, distance);
                if (def.DropoffResourceKinds.Count == 0 || System.Linq.Enumerable.Contains(def.DropoffResourceKinds, kind))
                {
                    Keep(ref compatible, ref compatibleDistance, building, distance);
                }
            }
        }

        return compatible >= 0 ? compatible : dropoff >= 0 ? dropoff : any;
    }

    private static void Keep(ref int best, ref long bestDistance, int candidate, long distance)
    {
        if (distance < bestDistance)
        {
            best = candidate;
            bestDistance = distance;
        }
    }

    private void RecordNode(int node, int amount, int remaining)
    {
        for (int i = 0; i < _nodes.Count; i++)
        {
            if (_nodes[i].Node == node)
            {
                _nodes[i] = new NodeHarvest(node, _nodes[i].Harvested + amount, remaining, _nodes[i].Depleted || remaining == 0);
                return;
            }
        }

        _nodes.Add(new NodeHarvest(node, amount, remaining, remaining == 0));
    }
}
