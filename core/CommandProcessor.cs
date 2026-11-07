using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>
/// Validates and applies player commands at the start of a tick, in the order given. Kotlin applied script commands
/// without checking who owned the units; here a player can only command its own living entities.
/// An order that is not queued replaces the unit's orders and also ends any harvesting or building job, so a worker
/// can be pulled off its work. Kotlin kept the job, and the harvest system kept sending the worker back.
/// </summary>
public sealed class CommandProcessor
{
    private readonly Simulation _simulation;
    private readonly World _world;

    public CommandProcessor(Simulation simulation)
    {
        _simulation = simulation;
        _world = simulation.World;
    }

    public CommandResult Apply(int index, PlayerCommand command)
    {
        if (_world.MatchEnded)
        {
            return new CommandResult(index, CommandRejection.MatchEnded);
        }

        return command switch
        {
            PlayerCommand.Move c => OrderPoint(index, c.Player, c.Units, c.X, c.Y, c.Queued, (x, y, _) => new Order.Move(x, y)),
            PlayerCommand.AttackMove c => OrderPoint(index, c.Player, c.Units, c.X, c.Y, c.Queued, (x, y, _) => new Order.AttackMove(x, y)),
            PlayerCommand.Patrol c => OrderPoint(index, c.Player, c.Units, c.X, c.Y, c.Queued, (x, y, from) => new Order.Patrol(from.X, from.Y, x, y)),
            PlayerCommand.Hold c => OrderUnits(index, c.Player, c.Units, c.Queued, _ => new Order.Hold()),
            PlayerCommand.Attack c => AttackTarget(index, c),
            PlayerCommand.Harvest c => HarvestNode(index, c),
            PlayerCommand.Construct c => ConstructSite(index, c),
            PlayerCommand.Build c => Build(index, c),
            PlayerCommand.CancelBuild c => CancelBuild(index, c),
            PlayerCommand.Train c => Train(index, c),
            PlayerCommand.CancelTrain c => CancelTrain(index, c),
            PlayerCommand.Research c => Research(index, c),
            PlayerCommand.CancelResearch c => CancelResearch(index, c),
            PlayerCommand.Rally c => Rally(index, c),
            PlayerCommand.Surrender c => Surrender(index, c),
            _ => throw new System.ArgumentException($"Unhandled command {command.GetType().Name}."),
        };
    }

    private CommandResult OrderPoint(
        int index, int player, IReadOnlyList<int> units, Fix x, Fix y, bool queued, System.Func<Fix, Fix, Position, Order> make)
    {
        if (!_world.Map.InBounds(x.FloorToInt(), y.FloorToInt()))
        {
            return new CommandResult(index, CommandRejection.OutOfBounds);
        }

        return OrderUnits(index, player, units, queued, id => make(x, y, _world.Positions.Get(id)));
    }

    private CommandResult AttackTarget(int index, PlayerCommand.Attack command)
    {
        if (!_world.IsEnemyOf(command.Target, command.Player))
        {
            return new CommandResult(index, CommandRejection.InvalidTarget);
        }

        return OrderUnits(index, command.Player, command.Units, command.Queued, _ => new Order.Attack(command.Target));
    }

    private CommandResult HarvestNode(int index, PlayerCommand.Harvest command)
    {
        if (!_world.ResourceNodes.Has(command.Node) || !_world.Positions.TryGet(command.Node, out Position node))
        {
            return new CommandResult(index, CommandRejection.InvalidTarget);
        }

        List<int> workers = OwnUnits(command.Player, command.Units, workersOnly: true);
        if (workers.Count == 0)
        {
            return new CommandResult(index, CommandRejection.NoOwnUnits);
        }

        foreach (int id in workers)
        {
            PrepareOrders(id, command.Queued);
            _world.Harvesters.Set(id, new Harvester(command.Node));
            _world.Orders.Get(id).Add(new Order.Move(node.X, node.Y));
        }

        return new CommandResult(index, null, workers.Count);
    }

    private CommandResult ConstructSite(int index, PlayerCommand.Construct command)
    {
        if (!_world.ConstructionSites.Has(command.Site) || !IsOwnedBy(command.Site, command.Player))
        {
            return new CommandResult(index, CommandRejection.InvalidTarget);
        }

        List<int> workers = OwnUnits(command.Player, command.Units, workersOnly: true);
        if (workers.Count == 0)
        {
            return new CommandResult(index, CommandRejection.NoOwnUnits);
        }

        foreach (int id in workers)
        {
            PrepareOrders(id, command.Queued);
            _world.BuilderTasks.Set(id, command.Site);
            _world.Orders.Get(id).Add(new Order.Construct(command.Site));
        }

        return new CommandResult(index, null, workers.Count);
    }

    private CommandResult OrderUnits(int index, int player, IReadOnlyList<int> units, bool queued, System.Func<int, Order> make)
    {
        List<int> own = OwnUnits(player, units, workersOnly: false);
        if (own.Count == 0)
        {
            return new CommandResult(index, CommandRejection.NoOwnUnits);
        }

        foreach (int id in own)
        {
            PrepareOrders(id, queued);
            _world.Orders.Get(id).Add(make(id));
        }

        return new CommandResult(index, null, own.Count);
    }

    /// <summary>Clears orders, path and work for a fresh order. A queued order leaves everything in place.</summary>
    private void PrepareOrders(int id, bool queued)
    {
        if (queued)
        {
            return;
        }

        _world.Orders.Get(id).Clear();
        _world.ClearPath(id);
        _world.Harvesters.Remove(id);
        _world.BuilderTasks.Remove(id);
    }

    /// <summary>The player's own living mobile units among the ids given, each once, in ascending id order.</summary>
    private List<int> OwnUnits(int player, IReadOnlyList<int> ids, bool workersOnly)
    {
        var sorted = new SortedSet<int>(ids);
        var own = new List<int>();
        foreach (int id in sorted)
        {
            if (IsOwnedBy(id, player) && _world.IsAttackable(id) && !_world.Footprints.Has(id) && _world.Orders.Has(id)
                && (!workersOnly || _simulation.Content.UnitOrNull(_world.Tags.Get(id).TypeId)?.Archetype == ConstructionSystem.WorkerArchetype))
            {
                own.Add(id);
            }
        }

        return own;
    }

    private bool IsOwnedBy(int id, int player) => _world.Tags.TryGet(id, out UnitTag tag) && tag.Faction == player;

    private bool IsOwnBuilding(int id, int player) => _world.Footprints.Has(id) && IsOwnedBy(id, player);

    private CommandResult Build(int index, PlayerCommand.Build command)
    {
        PlacementResult result = _simulation.Buildings.Place(command.Player, command.TypeId, command.TileX, command.TileY);
        return result.Succeeded
            ? new CommandResult(index, null, EntityId: result.EntityId)
            : new CommandResult(index, CommandRejection.Build, Detail: result.Failure.ToString());
    }

    private CommandResult CancelBuild(int index, PlayerCommand.CancelBuild command)
    {
        if (!IsOwnBuilding(command.Building, command.Player))
        {
            return new CommandResult(index, CommandRejection.NotYourBuilding);
        }

        CancelConstructionFailure? failure = _simulation.Buildings.CancelConstruction(command.Building);
        return failure == null ? new CommandResult(index, null) : new CommandResult(index, CommandRejection.CancelBuild, Detail: failure.ToString());
    }

    private CommandResult Train(int index, PlayerCommand.Train command)
    {
        if (!IsOwnBuilding(command.Building, command.Player))
        {
            return new CommandResult(index, CommandRejection.NotYourBuilding);
        }

        TrainFailure? failure = _simulation.Production.Enqueue(command.Building, command.TypeId);
        return failure == null ? new CommandResult(index, null) : new CommandResult(index, CommandRejection.Train, Detail: failure.ToString());
    }

    private CommandResult CancelTrain(int index, PlayerCommand.CancelTrain command)
    {
        if (!IsOwnBuilding(command.Building, command.Player))
        {
            return new CommandResult(index, CommandRejection.NotYourBuilding);
        }

        return _simulation.Production.CancelLast(command.Building)
            ? new CommandResult(index, null)
            : new CommandResult(index, CommandRejection.NothingToCancel);
    }

    private CommandResult Research(int index, PlayerCommand.Research command)
    {
        if (!IsOwnBuilding(command.Building, command.Player))
        {
            return new CommandResult(index, CommandRejection.NotYourBuilding);
        }

        ResearchFailure? failure = _simulation.Research.Enqueue(command.Building, command.TechId);
        return failure == null ? new CommandResult(index, null) : new CommandResult(index, CommandRejection.Research, Detail: failure.ToString());
    }

    private CommandResult CancelResearch(int index, PlayerCommand.CancelResearch command)
    {
        if (!IsOwnBuilding(command.Building, command.Player))
        {
            return new CommandResult(index, CommandRejection.NotYourBuilding);
        }

        return _simulation.Research.CancelLast(command.Building) == null
            ? new CommandResult(index, null)
            : new CommandResult(index, CommandRejection.NothingToCancel);
    }

    private CommandResult Rally(int index, PlayerCommand.Rally command)
    {
        if (!IsOwnBuilding(command.Building, command.Player))
        {
            return new CommandResult(index, CommandRejection.NotYourBuilding);
        }

        if (_simulation.Content.BuildingOrNull(_world.Tags.Get(command.Building).TypeId) is not { SupportsRally: true })
        {
            return new CommandResult(index, CommandRejection.UnsupportedRally);
        }

        if (!_world.Map.InBounds(command.X.FloorToInt(), command.Y.FloorToInt()))
        {
            return new CommandResult(index, CommandRejection.OutOfBounds);
        }

        _world.RallyPoints.Set(command.Building, new RallyPoint(command.X, command.Y));
        return new CommandResult(index, null, EntityId: command.Building);
    }

    private CommandResult Surrender(int index, PlayerCommand.Surrender command)
    {
        SortedSet<int> factions = VictorySystem.LivingFactions(_world);
        if (!factions.Contains(command.Player))
        {
            return new CommandResult(index, CommandRejection.NotInMatch);
        }

        factions.Remove(command.Player);
        _world.MatchEnded = true;
        _world.WinnerFaction = factions.Count == 1 ? factions.Min : null;
        _world.EndReason = MatchEndReason.Surrender;
        return new CommandResult(index, null);
    }
}
