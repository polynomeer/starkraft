using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>Ported from ResourceSystemTest.</summary>
public class ResourceTests
{
    [Fact]
    public void Spend_and_refund_change_the_stockpile()
    {
        var world = new World(4, 4);
        var resources = new Resources(world);
        resources.Set(1, 150, 25);
        Assert.True(resources.CanAfford(1, 100, 20));
        Assert.True(resources.Spend(1, 100, 20));
        Assert.Equal((50, 5), (world.Stockpile(1).Ore, world.Stockpile(1).Gas));

        Assert.False(resources.Spend(1, 60));
        resources.Refund(1, 25, 10);
        Assert.Equal((75, 15), (world.Stockpile(1).Ore, world.Stockpile(1).Gas));
    }

    [Fact]
    public void Spend_and_refund_are_recorded_until_cleared()
    {
        var world = new World(4, 4);
        var resources = new Resources(world);
        resources.Set(1, 200, 50);
        Assert.True(resources.Spend(1, 75, 20));
        resources.Refund(1, 25, 5);
        Assert.Equal(
            new[]
            {
                new ResourceChange(ResourceChangeKind.Spend, 1, 75, 20),
                new ResourceChange(ResourceChangeKind.Refund, 1, 25, 5),
            },
            resources.Changes);
        resources.ClearChanges();
        Assert.Empty(resources.Changes);
    }
}
