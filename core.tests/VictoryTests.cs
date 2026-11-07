using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Ported from VictorySystemTest. Its fifth case, "declares timeout on invalid unit transform", guarded against NaN
/// positions, which fixed-point numbers cannot hold, so it is not ported.
/// </summary>
public class VictoryTests
{
    private static Simulation New() => Simulation.Create(new MatchSetup(16, 16, 1UL), TestContent.Real);

    private static int Spawn(Simulation s, int faction, string type, int hp = 45) =>
        s.World.Spawn(new Position(Fix.FromInt(2 + s.World.NextEntityId), Fix.FromInt(2)), new UnitTag(faction, type), new Health(hp, hp));

    [Fact]
    public void Declares_a_winner_when_only_one_faction_has_anything_alive()
    {
        Simulation s = New();
        Spawn(s, 1, "gunner");
        int node = Spawn(s, 2, "ore", 1000);
        s.World.ResourceNodes.Set(node, new ResourceNode(ResourceKind.Ore, 100));
        s.Step();
        Assert.True(s.World.MatchEnded);
        Assert.Equal(1, s.World.WinnerFaction);
        Assert.Equal(MatchEndReason.Elimination, s.World.EndReason);
    }

    [Fact]
    public void Does_not_end_while_two_factions_remain()
    {
        Simulation s = New();
        Spawn(s, 1, "gunner");
        Spawn(s, 2, "brute");
        s.Step();
        Assert.False(s.World.MatchEnded);
        Assert.Null(s.World.WinnerFaction);
        Assert.Null(s.World.EndReason);
    }

    [Fact]
    public void Declares_a_draw_when_no_faction_remains()
    {
        Simulation s = New();
        s.Step();
        Assert.True(s.World.MatchEnded);
        Assert.Null(s.World.WinnerFaction);
        Assert.Equal(MatchEndReason.Draw, s.World.EndReason);
    }

    [Fact]
    public void Declares_a_draw_when_both_sides_fall_together()
    {
        Simulation s = New();
        int a = Spawn(s, 1, "gunner");
        int b = Spawn(s, 2, "brute");
        s.World.Healths.Get(a).Hp = 0;
        s.World.Healths.Get(b).Hp = 0;
        s.Step();
        Assert.Equal(MatchEndReason.Draw, s.World.EndReason);
        Assert.Null(s.World.WinnerFaction);
    }

    [Fact]
    public void Battle_to_the_end_names_the_survivor()
    {
        Simulation s = New();
        int strong = s.World.Spawn(new Position(Fix.FromInt(4), Fix.FromInt(4)), new UnitTag(1, "gunner"), new Health(500, 500), new WeaponRef("matchlock"));
        s.World.Spawn(new Position(Fix.FromInt(7), Fix.FromInt(4)), new UnitTag(2, "gunner"), new Health(20, 20), new WeaponRef("matchlock"));
        for (int i = 0; i < 100 && !s.World.MatchEnded; i++)
        {
            s.Step();
        }

        Assert.Equal((1, MatchEndReason.Elimination), (s.World.WinnerFaction!.Value, s.World.EndReason!.Value));
        Assert.True(s.World.Healths.Has(strong));
    }
}

public class VisionTests
{
    [Fact]
    public void Units_reveal_a_disc_for_their_own_faction_only()
    {
        Simulation s = Simulation.Create(new MatchSetup(20, 20, 1UL), TestContent.Real);
        s.World.Spawn(new Position(Fix.FromInt(10) + Fix.Half, Fix.FromInt(10) + Fix.Half), new UnitTag(1, "gunner"), new Health(45, 45), null, Fix.FromRatio(5, 2));
        s.World.Spawn(new Position(Fix.FromInt(2), Fix.FromInt(2)), new UnitTag(2, "gunner"), new Health(45, 45));
        s.Step();
        // A 2.5 range rounds up to 3 tiles.
        Assert.True(s.Vision.IsVisible(1, 13, 10));
        Assert.False(s.Vision.IsVisible(1, 14, 10));
        Assert.True(s.Vision.IsVisible(1, 12, 12));
        Assert.False(s.Vision.IsVisible(1, 13, 13));
        Assert.False(s.Vision.IsVisible(2, 10, 10));
        Assert.Equal(29, s.Vision.VisibleCount(1));
    }
}
