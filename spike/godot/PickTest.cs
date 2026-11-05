using Godot;
using Samjae.Core;

namespace Samjae.Client;

/// <summary>
/// Checks click accuracy through the real input path: screen-space mouse events are pushed into the viewport and must
/// select the expected unit or order the expected tile, at several zoom levels and camera positions.
/// Also compares our projection against the engine's TileMapLayer tile lookup.
/// </summary>
public sealed class PickTest
{
    private static readonly float[] Zooms = { 0.5f, 0.75f, 1f, 1.5f };

    private readonly Main _main;
    private readonly WorldView _world;
    private readonly Camera2D _camera;
    private readonly Simulation _simulation;
    private readonly Viewport _viewport;

    public PickTest(Main main, WorldView world, Camera2D camera, Simulation simulation, Viewport viewport)
    {
        _main = main;
        _world = world;
        _camera = camera;
        _simulation = simulation;
        _viewport = viewport;
    }

    public int Run()
    {
        GD.Print($"pick-test window={_viewport.GetWindow().Size} visible={_viewport.GetVisibleRect().Size}");
        int failures = CheckTileLookup() + CheckLeftClicks() + CheckRightClicks();
        GD.Print($"pick-test failures={failures}");
        return failures;
    }

    private int CheckTileLookup()
    {
        int failures = 0;
        const int points = 20000;
        var random = new DeterministicRandom(99);
        for (int i = 0; i < points; i++)
        {
            // Stay away from tile edges, where both answers are legitimately ambiguous within float precision.
            float x = random.NextInt(64) + (50 + random.NextInt(900)) / 1000f;
            float y = random.NextInt(64) + (50 + random.NextInt(900)) / 1000f;
            var expected = new Vector2I(Mathf.FloorToInt(x), Mathf.FloorToInt(y));
            if (_world.Tiles.LocalToMap(_world.WorldToCanvas(x, y)) != expected)
            {
                failures++;
            }
        }

        GD.Print($"tile-lookup checked={points} failures={failures}");
        return failures;
    }

    private int CheckLeftClicks()
    {
        int failures = 0;
        int clicks = 0;
        foreach (float zoom in Zooms)
        {
            for (int id = 0; id < _simulation.UnitCount; id++)
            {
                AimCamera(_world.UnitCanvas(id), id, zoom);
                Vector2 point = _world.UnitCanvas(id) + new Vector2(0f, -20f);
                PushClick(CanvasToScreen(point), MouseButton.Left);
                clicks++;
                int picked = _world.Selected.Count == 1 ? System.Linq.Enumerable.First(_world.Selected) : -1;
                if (!IsAcceptablePick(picked, id, point))
                {
                    failures++;
                }
            }
        }

        GD.Print($"left-click checked={clicks} failures={failures}");
        return failures;
    }

    private int CheckRightClicks()
    {
        int failures = 0;
        int clicks = 0;
        var random = new DeterministicRandom(7);
        _world.Selected.Clear();
        _world.Selected.Add(0);
        foreach (float zoom in Zooms)
        {
            for (int i = 0; i < 500; i++)
            {
                int tileX = random.NextInt(64);
                int tileY = random.NextInt(64);
                float x = tileX + (100 + random.NextInt(800)) / 1000f;
                float y = tileY + (100 + random.NextInt(800)) / 1000f;
                Vector2 canvas = _world.WorldToCanvas(x, y);
                AimCamera(canvas, i, zoom);
                PushClick(CanvasToScreen(canvas), MouseButton.Right);
                clicks++;
                if (_main.LastOrderTile != new Vector2I(tileX, tileY))
                {
                    failures++;
                }
            }
        }

        GD.Print($"right-click checked={clicks} failures={failures}");
        return failures;
    }

    private void AimCamera(Vector2 target, int variation, float zoom)
    {
        _camera.Zoom = new Vector2(zoom, zoom);
        _camera.Position = target + new Vector2(variation % 7 * 11f, variation % 5 * -13f);
        _camera.ForceUpdateScroll();
    }

    private Vector2 CanvasToScreen(Vector2 canvas) => _viewport.GetScreenTransform() * _viewport.CanvasTransform * canvas;

    private void PushClick(Vector2 screen, MouseButton button)
    {
        foreach (bool pressed in new[] { true, false })
        {
            _viewport.PushInput(new InputEventMouseButton
            {
                ButtonIndex = button,
                Pressed = pressed,
                Position = screen,
                GlobalPosition = screen,
            });
        }
    }

    /// <summary>A unit standing in front of the target may legitimately take the click.</summary>
    private bool IsAcceptablePick(int picked, int target, Vector2 point)
    {
        if (picked == target)
        {
            return true;
        }

        return picked >= 0
            && WorldView.PickRect(_world.UnitCanvas(picked)).HasPoint(point)
            && _world.UnitCanvas(picked).Y >= _world.UnitCanvas(target).Y;
    }
}
