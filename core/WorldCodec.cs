using System;
using System.Collections.Generic;
using System.IO;

namespace Samjae.Core;

/// <summary>The one serialised layout of <see cref="World"/>. Read must mirror Write field for field.</summary>
internal static class WorldCodec
{
    public static void Write(BinaryWriter w, World world)
    {
        w.Write(world.NextEntityId);
        w.Write(world.MatchEnded);
        WriteOptionalInt(w, world.WinnerFaction);
        w.Write(world.EndReason.HasValue ? (byte)world.EndReason.Value : (byte)0);
        world.Map.Write(w);
        world.Occupancy.Write(w);
        world.PathRequests.Write(w);

        WriteStore(w, world.Positions, (o, c) => { o.Write(c.X.Raw); o.Write(c.Y.Raw); });
        WriteStore(w, world.Tags, (o, c) => { o.Write(c.Faction); o.Write(c.TypeId); });
        WriteStore(w, world.Healths, (o, c) => { o.Write(c.Hp); o.Write(c.MaxHp); o.Write(c.Armor); });
        WriteStore(w, world.Weapons, (o, c) => { o.Write(c.Id); o.Write(c.CooldownTicks); });
        WriteStore(w, world.Orders, WriteOrders);
        WriteStore(w, world.VisionRanges, (o, c) => o.Write(c.Raw));
        WriteStore(w, world.PathFollows, WritePathFollow);
        WriteStore(w, world.RepathCooldowns, (o, c) => o.Write(c));
        WriteStore(w, world.Footprints, WriteFootprint);
        WriteStore(w, world.ConstructionSites, WriteSite);
        WriteStore(w, world.BuilderTasks, (o, c) => o.Write(c));
        WriteStore(w, world.RallyPoints, (o, c) => { o.Write(c.X.Raw); o.Write(c.Y.Raw); });
        WriteStore(w, world.ProductionQueues, WriteProduction);
        WriteStore(w, world.ResearchQueues, WriteResearch);
        WriteStore(w, world.Stucks, (o, c) => { o.Write(c.LastX.Raw); o.Write(c.LastY.Raw); o.Write(c.Ticks); });
        WriteStore(w, world.ResourceNodes, (o, c) => { o.Write((byte)c.Kind); o.Write(c.Remaining); o.Write(c.YieldPerTick); });
        WriteStore(w, world.Harvesters, WriteHarvester);
        WriteStore(w, world.AutoAttackTargets, (o, c) => o.Write(c));
        WriteStore(w, world.Statuses, WriteStatuses);
        WriteStore(w, world.Lifetimes, (o, c) => o.Write(c));
        WriteFactionTables(w, world);
    }

    public static void Read(BinaryReader r, World world)
    {
        world.NextEntityId = r.ReadInt32();
        world.MatchEnded = r.ReadBoolean();
        world.WinnerFaction = ReadOptionalInt(r);
        byte reason = r.ReadByte();
        world.EndReason = reason == 0 ? null : (MatchEndReason)reason;
        world.Map.Read(r);
        world.Occupancy.Read(r);
        world.PathRequests.Read(r);

        ReadStore(r, world.Positions, i => new Position(Fix.FromRaw(i.ReadInt32()), Fix.FromRaw(i.ReadInt32())));
        ReadStore(r, world.Tags, i => new UnitTag(i.ReadInt32(), i.ReadString()));
        ReadStore(r, world.Healths, i => new Health(i.ReadInt32(), i.ReadInt32(), i.ReadInt32()));
        ReadStore(r, world.Weapons, i => new WeaponRef(i.ReadString(), i.ReadInt32()));
        ReadStore(r, world.Orders, ReadOrders);
        ReadStore(r, world.VisionRanges, i => Fix.FromRaw(i.ReadInt32()));
        ReadStore(r, world.PathFollows, ReadPathFollow);
        ReadStore(r, world.RepathCooldowns, i => i.ReadInt32());
        ReadStore(r, world.Footprints, i => new BuildingFootprint(i.ReadInt32(), i.ReadInt32(), i.ReadInt32(), i.ReadInt32(), i.ReadInt32()));
        ReadStore(r, world.ConstructionSites, i => new ConstructionSite(i.ReadInt32(), i.ReadInt32(), i.ReadInt32(), i.ReadInt32(), i.ReadInt32()));
        ReadStore(r, world.BuilderTasks, i => i.ReadInt32());
        ReadStore(r, world.RallyPoints, i => new RallyPoint(Fix.FromRaw(i.ReadInt32()), Fix.FromRaw(i.ReadInt32())));
        ReadStore(r, world.ProductionQueues, ReadProduction);
        ReadStore(r, world.ResearchQueues, ReadResearch);
        ReadStore(r, world.Stucks, i => new StuckTracker(Fix.FromRaw(i.ReadInt32()), Fix.FromRaw(i.ReadInt32()), i.ReadInt32()));
        ReadStore(r, world.ResourceNodes, i => new ResourceNode((ResourceKind)i.ReadByte(), i.ReadInt32(), i.ReadInt32()));
        ReadStore(r, world.Harvesters, ReadHarvester);
        ReadStore(r, world.AutoAttackTargets, i => i.ReadInt32());
        ReadStore(r, world.Statuses, ReadStatuses);
        ReadStore(r, world.Lifetimes, i => i.ReadInt32());
        ReadFactionTables(r, world);
    }

    private static void WriteStore<T>(BinaryWriter w, EntityStore<T> store, Action<BinaryWriter, T> writeItem)
    {
        w.Write(store.Count);
        foreach (int id in store)
        {
            w.Write(id);
            writeItem(w, store.Get(id));
        }
    }

    private static void ReadStore<T>(BinaryReader r, EntityStore<T> store, Func<BinaryReader, T> readItem)
    {
        int count = r.ReadInt32();
        for (int i = 0; i < count; i++)
        {
            int id = r.ReadInt32();
            store.Set(id, readItem(r));
        }
    }

    private static void WriteOptionalInt(BinaryWriter w, int? value)
    {
        w.Write(value.HasValue);
        w.Write(value ?? 0);
    }

    private static int? ReadOptionalInt(BinaryReader r)
    {
        bool has = r.ReadBoolean();
        int value = r.ReadInt32();
        return has ? value : null;
    }

    private static void WriteStatuses(BinaryWriter w, List<StatusInstance> statuses)
    {
        w.Write(statuses.Count);
        foreach (StatusInstance status in statuses)
        {
            w.Write(status.StatusId);
            w.Write(status.Stacks);
            w.Write(status.RemainingTicks);
        }
    }

    private static List<StatusInstance> ReadStatuses(BinaryReader r)
    {
        int count = r.ReadInt32();
        var statuses = new List<StatusInstance>(count);
        for (int i = 0; i < count; i++)
        {
            statuses.Add(new StatusInstance(r.ReadString(), r.ReadInt32(), r.ReadInt32()));
        }

        return statuses;
    }

    private static void WriteOrders(BinaryWriter w, List<Order> orders)
    {
        w.Write(orders.Count);
        foreach (Order order in orders)
        {
            WriteOrder(w, order);
        }
    }

    private static void WriteOrder(BinaryWriter w, Order order)
    {
        switch (order)
        {
            case Order.Move o:
                w.Write((byte)1); w.Write(o.X.Raw); w.Write(o.Y.Raw);
                break;
            case Order.AttackMove o:
                w.Write((byte)2); w.Write(o.X.Raw); w.Write(o.Y.Raw);
                break;
            case Order.Patrol o:
                w.Write((byte)3); w.Write(o.AX.Raw); w.Write(o.AY.Raw); w.Write(o.BX.Raw); w.Write(o.BY.Raw); w.Write(o.TowardB);
                break;
            case Order.Hold:
                w.Write((byte)4);
                break;
            case Order.Construct o:
                w.Write((byte)5); w.Write(o.Target);
                break;
            case Order.Attack o:
                w.Write((byte)6); w.Write(o.Target);
                break;
            default:
                throw new InvalidOperationException($"Unserialised order type {order.GetType().Name}.");
        }
    }

    private static List<Order> ReadOrders(BinaryReader r)
    {
        int count = r.ReadInt32();
        var orders = new List<Order>(count);
        for (int i = 0; i < count; i++)
        {
            orders.Add(ReadOrder(r));
        }

        return orders;
    }

    private static Order ReadOrder(BinaryReader r)
    {
        byte tag = r.ReadByte();
        return tag switch
        {
            1 => new Order.Move(ReadFix(r), ReadFix(r)),
            2 => new Order.AttackMove(ReadFix(r), ReadFix(r)),
            3 => new Order.Patrol(ReadFix(r), ReadFix(r), ReadFix(r), ReadFix(r), r.ReadBoolean()),
            4 => new Order.Hold(),
            5 => new Order.Construct(r.ReadInt32()),
            6 => new Order.Attack(r.ReadInt32()),
            _ => throw new InvalidDataException($"Unknown order tag {tag}."),
        };
    }

    private static Fix ReadFix(BinaryReader r) => Fix.FromRaw(r.ReadInt32());

    /// <summary>Writes only the used part of the node buffer, so spare capacity cannot change a hash.</summary>
    private static void WritePathFollow(BinaryWriter w, PathFollow p)
    {
        w.Write(p.Length);
        for (int i = 0; i < p.Length; i++)
        {
            w.Write(p.Nodes[i]);
        }

        w.Write(p.Index);
        w.Write(p.GoalX);
        w.Write(p.GoalY);
    }

    private static PathFollow ReadPathFollow(BinaryReader r)
    {
        int length = r.ReadInt32();
        var nodes = new int[length];
        for (int i = 0; i < length; i++)
        {
            nodes[i] = r.ReadInt32();
        }

        return new PathFollow(nodes, length, r.ReadInt32(), r.ReadInt32(), r.ReadInt32());
    }

    private static void WriteFootprint(BinaryWriter w, BuildingFootprint f)
    {
        w.Write(f.TileX);
        w.Write(f.TileY);
        w.Write(f.Width);
        w.Write(f.Height);
        w.Write(f.Clearance);
    }

    private static void WriteSite(BinaryWriter w, ConstructionSite s)
    {
        w.Write(s.RemainingTicks);
        w.Write(s.TotalTicks);
        w.Write(s.MaxHp);
        w.Write(s.OreCost);
        w.Write(s.GasCost);
    }

    private static void WriteProduction(BinaryWriter w, List<ProductionJob> jobs)
    {
        w.Write(jobs.Count);
        foreach (ProductionJob job in jobs)
        {
            w.Write(job.TypeId);
            w.Write(job.RemainingTicks);
            w.Write(job.OreCost);
            w.Write(job.GasCost);
        }
    }

    private static List<ProductionJob> ReadProduction(BinaryReader r)
    {
        int count = r.ReadInt32();
        var jobs = new List<ProductionJob>(count);
        for (int i = 0; i < count; i++)
        {
            jobs.Add(new ProductionJob(r.ReadString(), r.ReadInt32(), r.ReadInt32(), r.ReadInt32()));
        }

        return jobs;
    }

    private static void WriteResearch(BinaryWriter w, List<ResearchJob> jobs)
    {
        w.Write(jobs.Count);
        foreach (ResearchJob job in jobs)
        {
            w.Write(job.TechId);
            w.Write(job.RemainingTicks);
            w.Write(job.OreCost);
            w.Write(job.GasCost);
        }
    }

    private static List<ResearchJob> ReadResearch(BinaryReader r)
    {
        int count = r.ReadInt32();
        var jobs = new List<ResearchJob>(count);
        for (int i = 0; i < count; i++)
        {
            jobs.Add(new ResearchJob(r.ReadString(), r.ReadInt32(), r.ReadInt32(), r.ReadInt32()));
        }

        return jobs;
    }

    private static void WriteHarvester(BinaryWriter w, Harvester h)
    {
        w.Write(h.TargetNodeId);
        w.Write(h.HarvestPerTick);
        w.Write(h.Range.Raw);
        w.Write(h.CargoCapacity);
        w.Write(h.DropoffRange.Raw);
        w.Write(h.CargoKind.HasValue ? (byte)(h.CargoKind.Value + 1) : (byte)0);
        w.Write(h.CargoAmount);
        w.Write(h.ReturnTargetId);
    }

    private static Harvester ReadHarvester(BinaryReader r)
    {
        var harvester = new Harvester(r.ReadInt32(), r.ReadInt32(), ReadFix(r), r.ReadInt32(), ReadFix(r));
        byte kind = r.ReadByte();
        harvester.CargoKind = kind == 0 ? null : (ResourceKind)(kind - 1);
        harvester.CargoAmount = r.ReadInt32();
        harvester.ReturnTargetId = r.ReadInt32();
        return harvester;
    }

    private static void WriteFactionTables(BinaryWriter w, World world)
    {
        w.Write(world.Stockpiles.Count);
        foreach ((int faction, ResourceStockpile stockpile) in world.Stockpiles)
        {
            w.Write(faction);
            w.Write(stockpile.Ore);
            w.Write(stockpile.Gas);
        }

        w.Write(world.UnlockedTechs.Count);
        foreach ((int faction, SortedSet<string> techs) in world.UnlockedTechs)
        {
            w.Write(faction);
            w.Write(techs.Count);
            foreach (string tech in techs)
            {
                w.Write(tech);
            }
        }
    }

    private static void ReadFactionTables(BinaryReader r, World world)
    {
        int stockpiles = r.ReadInt32();
        for (int i = 0; i < stockpiles; i++)
        {
            ResourceStockpile stockpile = world.Stockpile(r.ReadInt32());
            stockpile.Ore = r.ReadInt32();
            stockpile.Gas = r.ReadInt32();
        }

        int factions = r.ReadInt32();
        for (int i = 0; i < factions; i++)
        {
            int faction = r.ReadInt32();
            int techs = r.ReadInt32();
            for (int t = 0; t < techs; t++)
            {
                world.UnlockTech(faction, r.ReadString());
            }
        }
    }
}
