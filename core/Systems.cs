namespace Samjae.Core;

/// <summary>One step of the tick. The order is declared once, in <see cref="Simulation"/>.</summary>
public interface ITickSystem
{
    void Tick();
}

public sealed class AliveSystem : ITickSystem
{
    private readonly World _world;

    public AliveSystem(World world) => _world = world;

    public void Tick() => _world.UpdateAliveSnapshot();
}

public sealed class OccupancySystem : ITickSystem
{
    private readonly World _world;
    private readonly GameContent _content;

    public OccupancySystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
    }

    /// <summary>Air units take no tile.</summary>
    public void Tick()
    {
        _world.Occupancy.ClearDynamic();
        foreach (int id in _world.AliveIds)
        {
            if (_world.Positions.TryGet(id, out Position position) && !Layers.IsAir(_world, _content, id))
            {
                _world.Occupancy.AddDynamic(position.X.FloorToInt(), position.Y.FloorToInt());
            }
        }
    }
}
