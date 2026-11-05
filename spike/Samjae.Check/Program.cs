using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using Samjae.Core;

// Dependency-free test and hash runner for the P0 spike.
//   (no args)  run the rule tests
//   hash       print checkpoint hashes for the cross-OS comparison
//   bench      print per-tick cost of the core
return args.FirstOrDefault() switch
{
    "hash" => PrintHashes(),
    "bench" => PrintBench(args.Length > 1 ? int.Parse(args[1]) : 200),
    null => RunTests(),
    _ => throw new ArgumentException($"Unknown mode: {args[0]}"),
};

static int PrintHashes()
{
    foreach ((int tick, ulong hash) in RunScenario(MatchSetup.Baseline, 6000, 600))
    {
        Console.WriteLine($"{tick} {hash:x16}");
    }

    return 0;
}

static IEnumerable<(int Tick, ulong Hash)> RunScenario(MatchSetup setup, int ticks, int every)
{
    Simulation simulation = Simulation.Create(setup);
    var scenario = new BaselineScenario(setup.Seed);
    var commands = new List<MoveCommand>();
    yield return (0, simulation.ComputeHash());
    for (int i = 1; i <= ticks; i++)
    {
        commands.Clear();
        scenario.AppendCommands(simulation, commands);
        simulation.Step(commands.ToArray());
        if (i % every == 0)
        {
            yield return (i, simulation.ComputeHash());
        }
    }
}

static int PrintBench(int unitCount)
{
    MatchSetup setup = MatchSetup.Baseline with { UnitCount = unitCount };
    Simulation simulation = Simulation.Create(setup);
    var scenario = new BaselineScenario(setup.Seed);
    var commands = new List<MoveCommand>();
    var samples = new List<double>();
    for (int i = 0; i < 3000; i++)
    {
        commands.Clear();
        scenario.AppendCommands(simulation, commands);
        MoveCommand[] batch = commands.ToArray();
        long start = Stopwatch.GetTimestamp();
        simulation.Step(batch);
        samples.Add(Stopwatch.GetElapsedTime(start).TotalMilliseconds);
    }

    samples.Sort();
    Console.WriteLine(
        $"units={unitCount} ticks=3000 p50={samples[1500]:F3}ms p99={samples[2970]:F3}ms max={samples[2999]:F3}ms");
    return 0;
}

static int RunTests()
{
    var tests = new (string Name, Action Body)[]
    {
        ("multiply and divide keep sign and precision", FixArithmetic),
        ("square root is the exact integer floor", FixSquareRoot),
        ("overflow throws instead of wrapping", FixOverflow),
        ("same setup and commands give the same hash", SameRunSameHash),
        ("different seed gives a different hash", DifferentSeedDifferentHash),
        ("save and load continue to the same hash", SaveLoadContinues),
        ("units never stand on blocked tiles", UnitsStayWalkable),
        ("baseline places all buildings without overlap", BuildingsDoNotOverlap),
        ("a moved unit reaches its goal", UnitReachesGoal),
        ("a group settles near its goal", GroupSettles),
        ("commands for other players' units are ignored", ForeignUnitsIgnored),
    };

    int failures = 0;
    foreach ((string name, Action body) in tests)
    {
        try
        {
            body();
            Console.WriteLine($"ok    {name}");
        }
        catch (Exception exception)
        {
            failures++;
            Console.WriteLine($"FAIL  {name}: {exception.Message}");
        }
    }

    Console.WriteLine($"{tests.Length - failures}/{tests.Length} passed");
    return failures == 0 ? 0 : 1;
}

static void Require(bool condition, string message)
{
    if (!condition)
    {
        throw new InvalidOperationException(message);
    }
}

static void FixArithmetic()
{
    Require(Fix.FromInt(3) * Fix.Half == Fix.FromRatio(3, 2), "3 * 0.5");
    Require((Fix.FromInt(-3) * Fix.Half).Raw == -(3 * Fix.OneRaw / 2), "-3 * 0.5");
    Require(Fix.FromInt(1) / Fix.FromInt(4) == Fix.FromRaw(Fix.OneRaw / 4), "1 / 4");
    Require(Fix.FromRaw(-1).FloorToInt() == -1, "floor rounds toward negative infinity");
}

static void FixSquareRoot()
{
    Require(Fix.Sqrt(Fix.FromInt(4)) == Fix.FromInt(2), "sqrt 4");
    Require(Fix.Sqrt(Fix.Zero) == Fix.Zero, "sqrt 0");
    for (int raw = 1; raw < 2_000_000; raw += 977)
    {
        long root = Fix.Sqrt(Fix.FromRaw(raw)).Raw;
        long scaled = (long)raw << Fix.FractionBits;
        Require(root * root <= scaled && (root + 1) * (root + 1) > scaled, $"floor sqrt of raw {raw}");
    }
}

static void FixOverflow()
{
    bool threw = false;
    try
    {
        _ = Fix.FromInt(30000) * Fix.FromInt(30000);
    }
    catch (OverflowException)
    {
        threw = true;
    }

    Require(threw, "expected OverflowException");
}

static void SameRunSameHash()
{
    ulong first = RunScenario(MatchSetup.Baseline, 1000, 1000).Last().Hash;
    ulong second = RunScenario(MatchSetup.Baseline, 1000, 1000).Last().Hash;
    Require(first == second, $"{first:x16} != {second:x16}");
}

static void DifferentSeedDifferentHash()
{
    ulong first = RunScenario(MatchSetup.Baseline, 200, 200).Last().Hash;
    ulong second = RunScenario(MatchSetup.Baseline with { Seed = 7 }, 200, 200).Last().Hash;
    Require(first != second, "seeds produced the same hash");
}

static void SaveLoadContinues()
{
    MatchSetup setup = MatchSetup.Baseline;
    Simulation original = Simulation.Create(setup);
    var scenario = new BaselineScenario(setup.Seed);
    var commands = new List<MoveCommand>();
    for (int i = 0; i < 350; i++)
    {
        commands.Clear();
        scenario.AppendCommands(original, commands);
        original.Step(commands.ToArray());
    }

    Simulation restored = Simulation.LoadState(original.SaveState());
    Require(restored.ComputeHash() == original.ComputeHash(), "hash changed by the round trip");
    var move = new MoveCommand[] { new(0, new[] { 0, 2, 4 }, 30, 30) };
    for (int i = 0; i < 300; i++)
    {
        original.Step(i == 0 ? move : Array.Empty<MoveCommand>());
        restored.Step(i == 0 ? move : Array.Empty<MoveCommand>());
    }

    Require(restored.ComputeHash() == original.ComputeHash(), "runs diverged after load");
}

static void UnitsStayWalkable()
{
    MatchSetup setup = MatchSetup.Baseline;
    Simulation simulation = Simulation.Create(setup);
    var scenario = new BaselineScenario(setup.Seed);
    var commands = new List<MoveCommand>();
    for (int i = 0; i < 2000; i++)
    {
        commands.Clear();
        scenario.AppendCommands(simulation, commands);
        simulation.Step(commands.ToArray());
        for (int id = 0; id < simulation.UnitCount; id++)
        {
            int tile = TileOf(simulation, id);
            Require(simulation.Blocked[tile] == 0, $"unit {id} on blocked tile {tile} at tick {i}");
        }
    }
}

static int TileOf(Simulation simulation, int id) =>
    (simulation.UnitYRaw[id] >> Fix.FractionBits) * simulation.MapWidth + (simulation.UnitXRaw[id] >> Fix.FractionBits);

static void BuildingsDoNotOverlap()
{
    Simulation simulation = Simulation.Create(MatchSetup.Baseline);
    Require(simulation.Buildings.Length == 20, "building count");
    BuildingInfo[] buildings = simulation.Buildings.ToArray();
    for (int i = 0; i < buildings.Length; i++)
    {
        for (int j = i + 1; j < buildings.Length; j++)
        {
            BuildingInfo a = buildings[i];
            BuildingInfo b = buildings[j];
            bool apart = a.TileX + a.Width <= b.TileX || b.TileX + b.Width <= a.TileX
                || a.TileY + a.Height <= b.TileY || b.TileY + b.Height <= a.TileY;
            Require(apart, $"buildings {i} and {j} overlap");
        }
    }
}

static void UnitReachesGoal()
{
    Simulation simulation = Simulation.Create(MatchSetup.Baseline with { UnitCount = 1 });
    int goal = simulation.NearestWalkableTile(40, 40);
    var move = new MoveCommand[] { new(0, new[] { 0 }, 40, 40) };
    simulation.Step(move);
    for (int i = 0; i < 2000 && simulation.UnitTarget[0] != Simulation.NoTarget; i++)
    {
        simulation.Step(Array.Empty<MoveCommand>());
    }

    Require(TileOf(simulation, 0) == goal, $"unit stopped on tile {TileOf(simulation, 0)}, goal {goal}");
}

static void GroupSettles()
{
    Simulation simulation = Simulation.Create(MatchSetup.Baseline);
    int[] ids = Enumerable.Range(0, simulation.UnitCount).Where(id => id % 2 == 0).ToArray();
    var move = new MoveCommand[] { new(0, ids, 32, 32) };
    simulation.Step(move);
    for (int i = 0; i < 3000; i++)
    {
        simulation.Step(Array.Empty<MoveCommand>());
    }

    foreach (int id in ids)
    {
        Require(simulation.UnitTarget[id] == Simulation.NoTarget, $"unit {id} never settled");
        int x = simulation.UnitXRaw[id] >> Fix.FractionBits;
        int y = simulation.UnitYRaw[id] >> Fix.FractionBits;
        Require(Math.Abs(x - 32) + Math.Abs(y - 32) <= 24, $"unit {id} settled far away at {x},{y}");
    }
}

static void ForeignUnitsIgnored()
{
    Simulation simulation = Simulation.Create(MatchSetup.Baseline);
    var move = new MoveCommand[] { new(0, new[] { 1 }, 32, 32) };
    simulation.Step(move);
    Require(simulation.UnitTarget[1] == Simulation.NoTarget, "player 0 ordered a player 1 unit");
}
