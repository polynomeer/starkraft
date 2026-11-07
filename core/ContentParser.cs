using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;

namespace Samjae.Core;

/// <summary>Turns JSON text into <see cref="GameContent"/>. The caller reads the files, so the core itself does no IO.</summary>
public static class ContentParser
{
    public static GameContent Parse(string unitsJson, string weaponsJson, string buildingsJson, string techsJson) =>
        new(
            ParseList(unitsJson, "units", ParseUnit),
            ParseList(weaponsJson, "weapons", ParseWeapon),
            ParseList(buildingsJson, "buildings", ParseBuilding),
            ParseList(techsJson, "techs", ParseTech));

    private static List<T> ParseList<T>(string json, string fileName, Func<Fields, T> parseItem)
    {
        using JsonDocument document = JsonDocument.Parse(json);
        JsonElement root = document.RootElement;
        if (root.ValueKind != JsonValueKind.Object || !root.TryGetProperty("list", out JsonElement list)
            || list.ValueKind != JsonValueKind.Array)
        {
            throw new InvalidDataException($"{fileName}: expected an object with a 'list' array.");
        }

        var items = new List<T>();
        int index = 0;
        foreach (JsonElement element in list.EnumerateArray())
        {
            var fields = new Fields(element, $"{fileName}[{index++}]");
            items.Add(parseItem(fields));
            fields.RejectUnknownKeys();
        }

        return items;
    }

    private static UnitDef ParseUnit(Fields f) => new(
        f.Text("id"),
        f.Text("archetype"),
        f.RequiredInt("hp", 1),
        f.Int("armor", 0),
        f.Fixed("speedTilesPerSecond") / Fix.FromInt(Simulation.TicksPerSecond),
        f.OptionalText("weaponId"),
        f.Int("oreCost", 0),
        f.Int("gasCost", 0),
        GameContent.MillisecondsToTicks(f.Int("buildMs", 0)),
        f.Int("footprintWidth", 0),
        f.Int("footprintHeight", 0),
        f.TextList("producerTypes"),
        f.TextList("requiredBuildingTypes"),
        f.TextList("requiredResearchIds"));

    private static WeaponDef ParseWeapon(Fields f) => new(
        f.Text("id"),
        f.RequiredInt("damage", 0),
        f.Fixed("range"),
        GameContent.MillisecondsToTicks(f.RequiredInt("cooldownMs", 0)));

    private static BuildingDef ParseBuilding(Fields f) => new(
        f.Text("id"),
        f.Text("archetype"),
        f.RequiredInt("hp", 1),
        GameContent.MillisecondsToTicks(f.Int("buildMs", 0)),
        f.Int("armor", 0),
        f.RequiredInt("footprintWidth", 1),
        f.RequiredInt("footprintHeight", 1),
        f.Int("placementClearance", 0),
        f.Flag("supportsTraining"),
        f.Flag("supportsResearch"),
        f.Flag("supportsRally"),
        f.Flag("supportsDropoff"),
        ParseKinds(f),
        f.Int("productionQueueLimit", 5),
        f.Fixed("rallyOffsetX", Fix.Zero),
        f.Fixed("rallyOffsetY", Fix.Zero),
        f.Int("oreCost", 0),
        f.Int("gasCost", 0),
        f.TextList("requiredBuildingTypes"),
        f.TextList("requiredResearchIds"));

    private static TechDef ParseTech(Fields f) => new(
        f.Text("id"),
        GameContent.MillisecondsToTicks(f.RequiredInt("buildMs", 0)),
        f.Int("oreCost", 0),
        f.Int("gasCost", 0),
        f.TextList("producerTypes"),
        f.TextList("requiredBuildingTypes"),
        f.TextList("requiredResearchIds"));

    private static List<ResourceKind> ParseKinds(Fields f)
    {
        var kinds = new List<ResourceKind>();
        foreach (string name in f.TextList("dropoffResourceKinds"))
        {
            kinds.Add(name switch
            {
                "ore" => ResourceKind.Ore,
                "gas" => ResourceKind.Gas,
                _ => throw new InvalidDataException($"{f.Where}: unknown resource kind '{name}'."),
            });
        }

        return kinds;
    }

    /// <summary>Reads one JSON object and remembers which keys were used, so a typo in the data is an error rather than a silent default.</summary>
    private sealed class Fields
    {
        private readonly JsonElement _element;
        private readonly HashSet<string> _used = new();

        public string Where { get; }

        public Fields(JsonElement element, string where)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                throw new InvalidDataException($"{where}: expected an object.");
            }

            _element = element;
            Where = where;
        }

        public string Text(string key) =>
            OptionalText(key) ?? throw new InvalidDataException($"{Where}: missing '{key}'.");

        public string? OptionalText(string key)
        {
            if (!TryGet(key, out JsonElement value))
            {
                return null;
            }

            RequireKind(key, value, JsonValueKind.String);
            return value.GetString();
        }

        public int Int(string key, int fallback) => TryGet(key, out JsonElement value) ? ReadInt(key, value, 0) : fallback;

        public int RequiredInt(string key, int minimum)
        {
            if (!TryGet(key, out JsonElement value))
            {
                throw new InvalidDataException($"{Where}: missing '{key}'.");
            }

            return ReadInt(key, value, minimum);
        }

        public Fix Fixed(string key)
        {
            if (!TryGet(key, out JsonElement value))
            {
                throw new InvalidDataException($"{Where}: missing '{key}'.");
            }

            return ReadFixed(key, value);
        }

        public Fix Fixed(string key, Fix fallback) => TryGet(key, out JsonElement value) ? ReadFixed(key, value) : fallback;

        public bool Flag(string key)
        {
            if (!TryGet(key, out JsonElement value))
            {
                return false;
            }

            if (value.ValueKind is not (JsonValueKind.True or JsonValueKind.False))
            {
                throw new InvalidDataException($"{Where}: '{key}' must be true or false.");
            }

            return value.GetBoolean();
        }

        public List<string> TextList(string key)
        {
            var items = new List<string>();
            if (!TryGet(key, out JsonElement value))
            {
                return items;
            }

            RequireKind(key, value, JsonValueKind.Array);
            foreach (JsonElement item in value.EnumerateArray())
            {
                RequireKind(key, item, JsonValueKind.String);
                items.Add(item.GetString()!);
            }

            return items;
        }

        public void RejectUnknownKeys()
        {
            foreach (JsonProperty property in _element.EnumerateObject())
            {
                if (!_used.Contains(property.Name))
                {
                    throw new InvalidDataException($"{Where}: unknown key '{property.Name}'.");
                }
            }
        }

        private bool TryGet(string key, out JsonElement value)
        {
            _used.Add(key);
            return _element.TryGetProperty(key, out value);
        }

        private int ReadInt(string key, JsonElement value, int minimum)
        {
            RequireKind(key, value, JsonValueKind.Number);
            if (!value.TryGetInt32(out int number) || number < minimum)
            {
                throw new InvalidDataException($"{Where}: '{key}' must be a whole number of at least {minimum}.");
            }

            return number;
        }

        private Fix ReadFixed(string key, JsonElement value)
        {
            RequireKind(key, value, JsonValueKind.Number);
            try
            {
                return Fix.FromText(value.GetRawText());
            }
            catch (Exception exception) when (exception is FormatException or OverflowException)
            {
                throw new InvalidDataException($"{Where}: '{key}' is not usable: {exception.Message}");
            }
        }

        private void RequireKind(string key, JsonElement value, JsonValueKind kind)
        {
            if (value.ValueKind != kind)
            {
                throw new InvalidDataException($"{Where}: '{key}' must be {kind}.");
            }
        }
    }
}
