using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.Json;

namespace Samjae.Core;

/// <summary>
/// Replays as JSON text. Fixed-point values are written as their raw integers, so nothing is lost or rounded.
/// The caller reads and writes files; the core only converts text.
/// </summary>
public static class ReplayCodec
{
    public const string Format = "samjae-replay";
    public const int Version = 1;

    public static string Write(Replay replay)
    {
        using var stream = new MemoryStream();
        using (var w = new Utf8JsonWriter(stream, new JsonWriterOptions { Indented = true }))
        {
            w.WriteStartObject();
            w.WriteString("format", Format);
            w.WriteNumber("version", Version);
            w.WriteString("contentHash", Hex(replay.ContentHash));
            WriteScenario(w, replay.Scenario);
            w.WriteStartArray("ticks");
            foreach (ReplayTick tick in replay.Ticks)
            {
                w.WriteStartObject();
                w.WriteNumber("tick", tick.Tick);
                w.WriteStartArray("commands");
                foreach (PlayerCommand command in tick.Commands)
                {
                    CommandJson.Write(w, command);
                }

                w.WriteEndArray();
                w.WriteEndObject();
            }

            w.WriteEndArray();
            w.WriteStartArray("checkpoints");
            foreach (ReplayCheckpoint checkpoint in replay.Checkpoints)
            {
                w.WriteStartObject();
                w.WriteNumber("tick", checkpoint.Tick);
                w.WriteString("hash", Hex(checkpoint.Hash));
                w.WriteEndObject();
            }

            w.WriteEndArray();
            w.WriteStartObject("end");
            w.WriteNumber("tick", replay.EndTick);
            if (replay.Winner is int winner)
            {
                w.WriteNumber("winner", winner);
            }

            if (replay.EndReason is MatchEndReason reason)
            {
                w.WriteString("reason", reason.ToString());
            }

            w.WriteEndObject();
            w.WriteEndObject();
        }

        return Encoding.UTF8.GetString(stream.ToArray()) + "\n";
    }

    public static Replay Read(string json)
    {
        using JsonDocument document = JsonDocument.Parse(json);
        JsonElement root = document.RootElement;
        if (root.GetProperty("format").GetString() != Format || root.GetProperty("version").GetInt32() != Version)
        {
            throw new InvalidDataException("Not a samjae replay of a supported version.");
        }

        var ticks = new List<ReplayTick>();
        foreach (JsonElement tick in root.GetProperty("ticks").EnumerateArray())
        {
            var commands = new List<PlayerCommand>();
            foreach (JsonElement command in tick.GetProperty("commands").EnumerateArray())
            {
                commands.Add(CommandJson.Read(command));
            }

            ticks.Add(new ReplayTick(tick.GetProperty("tick").GetInt32(), commands));
        }

        var checkpoints = new List<ReplayCheckpoint>();
        foreach (JsonElement checkpoint in root.GetProperty("checkpoints").EnumerateArray())
        {
            checkpoints.Add(new ReplayCheckpoint(checkpoint.GetProperty("tick").GetInt32(), ParseHex(checkpoint.GetProperty("hash").GetString()!)));
        }

        JsonElement end = root.GetProperty("end");
        int? winner = end.TryGetProperty("winner", out JsonElement w) ? w.GetInt32() : null;
        MatchEndReason? reason = end.TryGetProperty("reason", out JsonElement r) ? Enum.Parse<MatchEndReason>(r.GetString()!) : null;
        return new Replay(
            ParseHex(root.GetProperty("contentHash").GetString()!), ReadScenario(root.GetProperty("scenario")),
            ticks, checkpoints, end.GetProperty("tick").GetInt32(), winner, reason);
    }

    private static void WriteScenario(Utf8JsonWriter w, Scenario scenario)
    {
        w.WriteStartObject("scenario");
        w.WriteNumber("width", scenario.MapWidth);
        w.WriteNumber("height", scenario.MapHeight);
        w.WriteString("seed", scenario.Seed.ToString(CultureInfo.InvariantCulture));
        w.WriteStartArray("blocked");
        foreach ((int x, int y) in scenario.BlockedTiles)
        {
            w.WriteStartArray();
            w.WriteNumberValue(x);
            w.WriteNumberValue(y);
            w.WriteEndArray();
        }

        w.WriteEndArray();
        w.WriteStartArray("stockpiles");
        foreach ((int faction, int ore, int gas) in scenario.Stockpiles)
        {
            w.WriteStartObject();
            w.WriteNumber("faction", faction);
            w.WriteNumber("ore", ore);
            w.WriteNumber("gas", gas);
            w.WriteEndObject();
        }

        w.WriteEndArray();
        w.WriteStartArray("entities");
        foreach (ScenarioEntity entity in scenario.Entities)
        {
            WriteEntity(w, entity);
        }

        w.WriteEndArray();
        w.WriteEndObject();
    }

    private static void WriteEntity(Utf8JsonWriter w, ScenarioEntity entity)
    {
        w.WriteStartObject();
        switch (entity)
        {
            case ScenarioEntity.Unit u:
                w.WriteString("kind", "unit");
                w.WriteNumber("faction", u.Faction);
                w.WriteString("type", u.TypeId);
                w.WriteNumber("x", u.X.Raw);
                w.WriteNumber("y", u.Y.Raw);
                break;
            case ScenarioEntity.Building b:
                w.WriteString("kind", "building");
                w.WriteNumber("faction", b.Faction);
                w.WriteString("type", b.TypeId);
                w.WriteNumber("tileX", b.TileX);
                w.WriteNumber("tileY", b.TileY);
                break;
            case ScenarioEntity.Node n:
                w.WriteString("kind", "node");
                w.WriteString("resource", n.Kind == ResourceKind.Gas ? "gas" : "ore");
                w.WriteNumber("x", n.X.Raw);
                w.WriteNumber("y", n.Y.Raw);
                w.WriteNumber("amount", n.Amount);
                w.WriteNumber("yield", n.YieldPerTick);
                break;
        }

        w.WriteEndObject();
    }

    private static Scenario ReadScenario(JsonElement e)
    {
        var blocked = new List<(int, int)>();
        foreach (JsonElement tile in e.GetProperty("blocked").EnumerateArray())
        {
            blocked.Add((tile[0].GetInt32(), tile[1].GetInt32()));
        }

        var stockpiles = new List<(int, int, int)>();
        foreach (JsonElement s in e.GetProperty("stockpiles").EnumerateArray())
        {
            stockpiles.Add((s.GetProperty("faction").GetInt32(), s.GetProperty("ore").GetInt32(), s.GetProperty("gas").GetInt32()));
        }

        var entities = new List<ScenarioEntity>();
        foreach (JsonElement entity in e.GetProperty("entities").EnumerateArray())
        {
            entities.Add(ReadEntity(entity));
        }

        return new Scenario(
            e.GetProperty("width").GetInt32(), e.GetProperty("height").GetInt32(),
            ulong.Parse(e.GetProperty("seed").GetString()!, CultureInfo.InvariantCulture), blocked, stockpiles, entities);
    }

    private static ScenarioEntity ReadEntity(JsonElement e)
    {
        Fix X(string name) => Fix.FromRaw(e.GetProperty(name).GetInt32());
        return e.GetProperty("kind").GetString() switch
        {
            "unit" => new ScenarioEntity.Unit(e.GetProperty("faction").GetInt32(), e.GetProperty("type").GetString()!, X("x"), X("y")),
            "building" => new ScenarioEntity.Building(
                e.GetProperty("faction").GetInt32(), e.GetProperty("type").GetString()!, e.GetProperty("tileX").GetInt32(), e.GetProperty("tileY").GetInt32()),
            "node" => new ScenarioEntity.Node(
                e.GetProperty("resource").GetString() == "gas" ? ResourceKind.Gas : ResourceKind.Ore, X("x"), X("y"),
                e.GetProperty("amount").GetInt32(), e.GetProperty("yield").GetInt32()),
            string other => throw new InvalidDataException($"Unknown scenario entity '{other}'."),
            null => throw new InvalidDataException("Scenario entity without a kind."),
        };
    }

    internal static string Hex(ulong value) => value.ToString("x16", CultureInfo.InvariantCulture);

    internal static ulong ParseHex(string text) => ulong.Parse(text, NumberStyles.HexNumber, CultureInfo.InvariantCulture);
}

/// <summary>Player commands as JSON objects. Shared by replays now and by the network protocol later.</summary>
public static class CommandJson
{
    public static void Write(Utf8JsonWriter w, PlayerCommand command)
    {
        w.WriteStartObject();
        w.WriteNumber("player", command.Player);
        switch (command)
        {
            case PlayerCommand.Move c: Point(w, "move", c.Units, c.X, c.Y, c.Queued); break;
            case PlayerCommand.AttackMove c: Point(w, "attackMove", c.Units, c.X, c.Y, c.Queued); break;
            case PlayerCommand.Patrol c: Point(w, "patrol", c.Units, c.X, c.Y, c.Queued); break;
            case PlayerCommand.Hold c: Units(w, "hold", c.Units, c.Queued); break;
            case PlayerCommand.Attack c: Units(w, "attack", c.Units, c.Queued); w.WriteNumber("target", c.Target); break;
            case PlayerCommand.Harvest c: Units(w, "harvest", c.Units, c.Queued); w.WriteNumber("target", c.Node); break;
            case PlayerCommand.Construct c: Units(w, "construct", c.Units, c.Queued); w.WriteNumber("target", c.Site); break;
            case PlayerCommand.Build c:
                w.WriteString("type", "build");
                w.WriteString("unitType", c.TypeId);
                w.WriteNumber("tileX", c.TileX);
                w.WriteNumber("tileY", c.TileY);
                break;
            case PlayerCommand.CancelBuild c: Building(w, "cancelBuild", c.Building); break;
            case PlayerCommand.Train c: Building(w, "train", c.Building); w.WriteString("unitType", c.TypeId); break;
            case PlayerCommand.CancelTrain c: Building(w, "cancelTrain", c.Building); break;
            case PlayerCommand.Research c: Building(w, "research", c.Building); w.WriteString("tech", c.TechId); break;
            case PlayerCommand.CancelResearch c: Building(w, "cancelResearch", c.Building); break;
            case PlayerCommand.Rally c: Building(w, "rally", c.Building); w.WriteNumber("x", c.X.Raw); w.WriteNumber("y", c.Y.Raw); break;
            case PlayerCommand.Surrender: w.WriteString("type", "surrender"); break;
            default: throw new ArgumentException($"Unserialised command {command.GetType().Name}.");
        }

        w.WriteEndObject();
    }

    public static PlayerCommand Read(JsonElement e)
    {
        int player = e.GetProperty("player").GetInt32();
        Fix F(string name) => Fix.FromRaw(e.GetProperty(name).GetInt32());
        int I(string name) => e.GetProperty(name).GetInt32();
        string S(string name) => e.GetProperty(name).GetString()!;
        bool queued = e.TryGetProperty("queued", out JsonElement q) && q.GetBoolean();
        return S("type") switch
        {
            "move" => new PlayerCommand.Move(player, ReadUnits(e), F("x"), F("y"), queued),
            "attackMove" => new PlayerCommand.AttackMove(player, ReadUnits(e), F("x"), F("y"), queued),
            "patrol" => new PlayerCommand.Patrol(player, ReadUnits(e), F("x"), F("y"), queued),
            "hold" => new PlayerCommand.Hold(player, ReadUnits(e), queued),
            "attack" => new PlayerCommand.Attack(player, ReadUnits(e), I("target"), queued),
            "harvest" => new PlayerCommand.Harvest(player, ReadUnits(e), I("target"), queued),
            "construct" => new PlayerCommand.Construct(player, ReadUnits(e), I("target"), queued),
            "build" => new PlayerCommand.Build(player, S("unitType"), I("tileX"), I("tileY")),
            "cancelBuild" => new PlayerCommand.CancelBuild(player, I("building")),
            "train" => new PlayerCommand.Train(player, I("building"), S("unitType")),
            "cancelTrain" => new PlayerCommand.CancelTrain(player, I("building")),
            "research" => new PlayerCommand.Research(player, I("building"), S("tech")),
            "cancelResearch" => new PlayerCommand.CancelResearch(player, I("building")),
            "rally" => new PlayerCommand.Rally(player, I("building"), F("x"), F("y")),
            "surrender" => new PlayerCommand.Surrender(player),
            string other => throw new InvalidDataException($"Unknown command type '{other}'."),
        };
    }

    private static void Point(Utf8JsonWriter w, string type, IReadOnlyList<int> units, Fix x, Fix y, bool queued)
    {
        Units(w, type, units, queued);
        w.WriteNumber("x", x.Raw);
        w.WriteNumber("y", y.Raw);
    }

    private static void Units(Utf8JsonWriter w, string type, IReadOnlyList<int> units, bool queued)
    {
        w.WriteString("type", type);
        w.WriteStartArray("units");
        foreach (int id in units)
        {
            w.WriteNumberValue(id);
        }

        w.WriteEndArray();
        if (queued)
        {
            w.WriteBoolean("queued", true);
        }
    }

    private static void Building(Utf8JsonWriter w, string type, int building)
    {
        w.WriteString("type", type);
        w.WriteNumber("building", building);
    }

    private static List<int> ReadUnits(JsonElement e)
    {
        var units = new List<int>();
        foreach (JsonElement id in e.GetProperty("units").EnumerateArray())
        {
            units.Add(id.GetInt32());
        }

        return units;
    }
}
