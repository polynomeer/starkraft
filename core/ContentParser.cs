using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;

namespace Samjae.Core;

/// <summary>Turns JSON text into <see cref="GameContent"/>. The caller reads the files, so the core itself does no IO.</summary>
public static class ContentParser
{
    /// <summary>Statuses and abilities may be left out for content that uses none.</summary>
    public static GameContent Parse(
        string unitsJson, string weaponsJson, string buildingsJson, string techsJson, string? statusesJson = null, string? abilitiesJson = null) =>
        new(
            ParseList(unitsJson, "units", ParseUnit),
            ParseList(weaponsJson, "weapons", ParseWeapon),
            ParseList(buildingsJson, "buildings", ParseBuilding),
            ParseList(techsJson, "techs", ParseTech),
            statusesJson == null ? null : ParseList(statusesJson, "statuses", ParseStatus),
            abilitiesJson == null ? null : ParseList(abilitiesJson, "abilities", ParseAbility));

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
        f.Fixed("radius"),
        f.Fixed("speedTilesPerSecond") / Fix.FromInt(Simulation.TicksPerSecond),
        f.OptionalText("weaponId"),
        f.Int("oreCost", 0),
        f.Int("gasCost", 0),
        GameContent.MillisecondsToTicks(f.Int("buildMs", 0)),
        f.Int("footprintWidth", 0),
        f.Int("footprintHeight", 0),
        f.TextList("producerTypes"),
        f.TextList("requiredBuildingTypes"),
        f.TextList("requiredResearchIds"),
        ParseTriggers(f),
        f.TextList("abilities"),
        f.OptionalText("layer") switch
        {
            null or "ground" => MoveLayer.Ground,
            "air" => MoveLayer.Air,
            string other => throw new InvalidDataException($"{f.Where}: unknown layer '{other}'."),
        });

    private static WeaponDef ParseWeapon(Fields f) => new(
        f.Text("id"),
        f.RequiredInt("damage", 0),
        f.Fixed("range"),
        GameContent.MillisecondsToTicks(f.RequiredInt("cooldownMs", 0)),
        ParseWeaponTargets(f));

    /// <summary>Weapons hit only the ground unless they say otherwise.</summary>
    private static LayerMask ParseWeaponTargets(Fields f)
    {
        if (!f.Has("targets"))
        {
            return LayerMask.Ground;
        }

        LayerMask mask = 0;
        foreach (string name in f.TextList("targets"))
        {
            mask |= name switch
            {
                "ground" => LayerMask.Ground,
                "air" => LayerMask.Air,
                _ => throw new InvalidDataException($"{f.Where}: unknown target layer '{name}'."),
            };
        }

        return mask != 0 ? mask : throw new InvalidDataException($"{f.Where}: 'targets' needs at least one layer.");
    }

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

    private static StatusDef ParseStatus(Fields f) => new(
        f.Text("id"),
        GameContent.MillisecondsToTicks(f.RequiredInt("durationMs", 1)),
        f.Int("maxStacks", 1, minimum: 1),
        f.SignedInt("damagePerStack"),
        f.SignedInt("armorPerStack"),
        f.SignedInt("speedPercentPerStack"));

    private static List<TriggerDef> ParseTriggers(Fields f)
    {
        var triggers = new List<TriggerDef>();
        foreach (Fields t in f.Objects("triggers"))
        {
            TriggerEvent on = t.Text("on") switch
            {
                "kill" => TriggerEvent.Kill,
                "death" => TriggerEvent.Death,
                "damaged" => TriggerEvent.Damaged,
                "hit" => TriggerEvent.Hit,
                string other => throw new InvalidDataException($"{t.Where}: unknown trigger '{other}'."),
            };
            triggers.Add(new TriggerDef(on, ParseEffects(t, "a trigger")));
            t.RejectUnknownKeys();
        }

        return triggers;
    }

    private static List<EffectDef> ParseEffects(Fields f, string owner)
    {
        var effects = new List<EffectDef>();
        foreach (Fields e in f.Objects("effects"))
        {
            effects.Add(ParseEffect(e));
            e.RejectUnknownKeys();
        }

        if (effects.Count == 0)
        {
            throw new InvalidDataException($"{f.Where}: {owner} needs at least one effect.");
        }

        return effects;
    }

    /// <summary>
    /// A unit target names which side it may be cast on, and only targeted abilities have a range. Effects may use
    /// <c>other</c> only when there is one: a target unit, or a point for where a summon appears.
    /// </summary>
    private static AbilityDef ParseAbility(Fields f)
    {
        AbilityTargetKind kind = f.Text("target") switch
        {
            "none" => AbilityTargetKind.None,
            "unit" => AbilityTargetKind.Unit,
            "point" => AbilityTargetKind.Point,
            string other => throw new InvalidDataException($"{f.Where}: unknown ability target '{other}'."),
        };
        string? factionText = f.OptionalText("targetFaction");
        if ((kind == AbilityTargetKind.Unit) != (factionText != null))
        {
            throw new InvalidDataException($"{f.Where}: 'targetFaction' is required for a unit target and allowed only there.");
        }

        if ((kind == AbilityTargetKind.None) == f.Has("range"))
        {
            throw new InvalidDataException($"{f.Where}: 'range' is required for a unit or point target and allowed only there.");
        }

        Fix range = kind == AbilityTargetKind.None ? Fix.Zero : f.Fixed("range");
        if (range < Fix.Zero)
        {
            throw new InvalidDataException($"{f.Where}: 'range' must not be negative.");
        }

        var ability = new AbilityDef(
            f.Text("id"),
            kind,
            factionText == null ? TargetFaction.Any : ParseFaction(f, factionText),
            range,
            GameContent.MillisecondsToTicks(f.Int("cooldownMs", 0)),
            ParseEffects(f, "an ability"));
        foreach (EffectDef effect in ability.Effects)
        {
            RequireOtherExists(f, kind, effect);
        }

        return ability;
    }

    private static void RequireOtherExists(Fields f, AbilityTargetKind kind, EffectDef effect)
    {
        bool usesOther = effect.Target.Shape == TargetShape.Other;
        bool summon = effect.Kind == EffectKind.Summon;
        bool hasPlace = kind != AbilityTargetKind.None;
        bool hasUnit = kind == AbilityTargetKind.Unit;
        if ((usesOther && !(summon ? hasPlace : hasUnit)) || (effect.Summon?.CopyOther == true && !hasUnit))
        {
            throw new InvalidDataException($"{f.Where}: an effect uses 'other', but this ability has no target for it.");
        }
    }

    private static TargetFaction ParseFaction(Fields f, string text) => text switch
    {
        "any" => TargetFaction.Any,
        "ally" => TargetFaction.Ally,
        "enemy" => TargetFaction.Enemy,
        string other => throw new InvalidDataException($"{f.Where}: unknown faction '{other}'."),
    };

    private static EffectDef ParseEffect(Fields f)
    {
        if (f.Text("type") == "summon")
        {
            return ParseSummon(f);
        }

        EffectTarget target = ParseTarget(f.Object("target"));
        return f.Text("type") switch
        {
            "applyStatus" => new EffectDef(EffectKind.ApplyStatus, target, f.Int("stacks", 1, minimum: 1), f.Text("status")),
            "damage" => new EffectDef(EffectKind.Damage, target, f.RequiredInt("amount", 1), null),
            "heal" => new EffectDef(EffectKind.Heal, target, f.RequiredInt("amount", 1), null),
            string other => throw new InvalidDataException($"{f.Where}: unknown effect type '{other}'."),
        };
    }

    /// <summary>A summon names a unit or copies the other party, never both, and appears at the owner or the other party.</summary>
    private static EffectDef ParseSummon(Fields f)
    {
        string? unit = f.OptionalText("unit");
        bool copyOther = f.Flag("copyOther");
        if ((unit == null) == !copyOther)
        {
            throw new InvalidDataException($"{f.Where}: a summon needs exactly one of 'unit' and 'copyOther'.");
        }

        TargetShape at = f.Text("at") switch
        {
            "self" => TargetShape.Self,
            "other" => TargetShape.Other,
            string other => throw new InvalidDataException($"{f.Where}: a summon appears at 'self' or 'other', not '{other}'."),
        };
        int lifetimeMs = f.Int("lifetimeMs", 0);
        int lifetime = lifetimeMs == 0 ? 0 : GameContent.MillisecondsToTicks(lifetimeMs);
        return new EffectDef(
            EffectKind.Summon,
            new EffectTarget(at, TargetFaction.Any, Fix.Zero),
            f.Int("count", 1, minimum: 1),
            null,
            new SummonDef(unit, copyOther, lifetime));
    }

    /// <summary>A circle must say which side it hits, so an area effect never lands on friends by default.</summary>
    private static EffectTarget ParseTarget(Fields f)
    {
        TargetShape shape = f.Text("shape") switch
        {
            "self" => TargetShape.Self,
            "other" => TargetShape.Other,
            "circle" => TargetShape.Circle,
            string other => throw new InvalidDataException($"{f.Where}: unknown target shape '{other}'."),
        };
        string? factionText = f.OptionalText("faction");
        if (shape == TargetShape.Circle && factionText == null)
        {
            throw new InvalidDataException($"{f.Where}: a circle needs 'faction'.");
        }

        TargetFaction faction = ParseFaction(f, factionText ?? "any");
        Fix radius = shape == TargetShape.Circle ? f.Fixed("radius") : Fix.Zero;
        if (shape != TargetShape.Circle && f.Has("radius"))
        {
            throw new InvalidDataException($"{f.Where}: only a circle has a 'radius'.");
        }

        if (radius < Fix.Zero)
        {
            throw new InvalidDataException($"{f.Where}: 'radius' must not be negative.");
        }

        f.RejectUnknownKeys();
        return new EffectTarget(shape, faction, radius);
    }

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

        public int Int(string key, int fallback, int minimum = 0) =>
            TryGet(key, out JsonElement value) ? ReadInt(key, value, minimum) : fallback;

        public int SignedInt(string key) => TryGet(key, out JsonElement value) ? ReadInt(key, value, int.MinValue) : 0;

        public bool Has(string key) => TryGet(key, out _);

        public Fields Object(string key)
        {
            if (!TryGet(key, out JsonElement value))
            {
                throw new InvalidDataException($"{Where}: missing '{key}'.");
            }

            return new Fields(value, $"{Where}.{key}");
        }

        public List<Fields> Objects(string key)
        {
            var items = new List<Fields>();
            if (!TryGet(key, out JsonElement value))
            {
                return items;
            }

            RequireKind(key, value, JsonValueKind.Array);
            int index = 0;
            foreach (JsonElement item in value.EnumerateArray())
            {
                items.Add(new Fields(item, $"{Where}.{key}[{index++}]"));
            }

            return items;
        }

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
