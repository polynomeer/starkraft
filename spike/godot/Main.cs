using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Linq;
using System.Text.Json;
using Godot;
using Samjae.Core;

namespace Samjae.Client;

/// <summary>
/// P0 baseline scene. Command-line flags after "--":
///   --units N  --bench SECONDS  --bench-cam crowd|map  --select N  --out FILE.json  --shot FILE.png  --pick-test
/// </summary>
public partial class Main : Node2D
{
    private const int LocalPlayer = 0;
    private const double TickSeconds = 1.0 / Simulation.TicksPerSecond;
    private const float CameraSpeed = 900f;
    private const float DragThreshold = 6f;
    private const float BenchMapZoom = 0.17f;

    private readonly List<MoveCommand> _pending = new();
    private readonly List<double> _frameMs = new();
    private readonly List<double> _stepMs = new();
    private Dictionary<string, string> _flags = new();
    private Simulation _simulation = null!;
    private BaselineScenario? _scenario;
    private WorldView _world = null!;
    private Hud _hud = null!;
    private Camera2D _camera = null!;
    private double _accumulator;
    private double _benchSeconds;
    private double _elapsed;
    private long _lastFrame;
    private Vector2? _dragStart;
    private ulong _hash;
    private bool _benchMapView;
    private long _visibleUnitSamples;

    public Vector2I LastOrderTile { get; private set; }

    public override void _Ready()
    {
        _flags = ParseFlags(OS.GetCmdlineUserArgs());
        int units = _flags.TryGetValue("units", out string? unitText) ? int.Parse(unitText, CultureInfo.InvariantCulture) : MatchSetup.Baseline.UnitCount;
        MatchSetup setup = MatchSetup.Baseline with { UnitCount = units };
        _simulation = Simulation.Create(setup);

        var art = new Art();
        _world = new WorldView();
        AddChild(_world);
        _world.Build(_simulation, art);

        _camera = new Camera2D { Position = _world.WorldToCanvas(14f, 14f), Zoom = new Vector2(0.75f, 0.75f) };
        AddChild(_camera);
        _camera.MakeCurrent();

        _hud = new Hud();
        AddChild(_hud);
        _hud.Build(_simulation, _world, art, BodyFont(), DisplayFont());
        _hud.Minimap.WorldClicked += world => _camera.Position = _world.WorldToCanvas(world.X, world.Y);

        if (_flags.TryGetValue("bench", out string? benchText))
        {
            _benchSeconds = double.Parse(benchText, CultureInfo.InvariantCulture);
            _scenario = new BaselineScenario(setup.Seed);
            DisplayServer.WindowSetVsyncMode(DisplayServer.VSyncMode.Disabled);
            Engine.MaxFps = 0;
            _benchMapView = _flags.TryGetValue("bench-cam", out string? cam) && cam == "map";
            if (_benchMapView)
            {
                _camera.Position = _world.WorldToCanvas(32f, 32f);
                _camera.Zoom = new Vector2(BenchMapZoom, BenchMapZoom);
            }
        }

        if (_flags.TryGetValue("select", out string? selectText))
        {
            int wanted = int.Parse(selectText, CultureInfo.InvariantCulture);
            foreach (int id in Enumerable.Range(0, _simulation.UnitCount).Where(id => _simulation.UnitOwner[id] == LocalPlayer).Take(wanted))
            {
                _world.Selected.Add(id);
            }
        }

        _lastFrame = Stopwatch.GetTimestamp();
        if (_flags.ContainsKey("pick-test"))
        {
            CallDeferred(MethodName.StartPickTest);
        }
    }

    private async void StartPickTest()
    {
        await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);
        int failures = new PickTest(this, _world, _camera, _simulation, GetViewport()).Run();
        GetTree().Quit(failures == 0 ? 0 : 1);
    }

    public override void _Process(double delta)
    {
        if (_flags.ContainsKey("pick-test"))
        {
            return;
        }

        RecordFrame();
        MoveCamera((float)delta);
        AdvanceSimulation(delta);
        _world.UpdateUnits((float)(_accumulator / TickSeconds));
        if (_benchSeconds > 0)
        {
            FollowBenchCrowd();
        }

        _hud.Refresh(_simulation.Tick, _hash, Engine.GetFramesPerSecond(), _world.Selected, _simulation);

        _elapsed += delta;
        if (_benchSeconds > 0 && _elapsed >= _benchSeconds)
        {
            _benchSeconds = 0;
            FinishBench();
        }
    }

    public override void _UnhandledInput(InputEvent @event)
    {
        switch (@event)
        {
            case InputEventMouseButton { ButtonIndex: MouseButton.WheelUp, Pressed: true }:
                ZoomBy(1.1f);
                break;
            case InputEventMouseButton { ButtonIndex: MouseButton.WheelDown, Pressed: true }:
                ZoomBy(1f / 1.1f);
                break;
            case InputEventMouseButton { ButtonIndex: MouseButton.Left } left:
                HandleLeft(left);
                break;
            case InputEventMouseButton { ButtonIndex: MouseButton.Right, Pressed: true } right:
                OrderMove(CanvasPosition(right));
                break;
            case InputEventMouseMotion motion when _dragStart is Vector2 start:
                _world.DragBox = new Rect2(start, CanvasPosition(motion) - start).Abs();
                break;
        }
    }

    /// <summary>Uses the event's own position, so a click maps to the same point however late it is processed.</summary>
    private Vector2 CanvasPosition(InputEventMouse mouse) => ((InputEventMouse)MakeInputLocal(mouse)).Position;

    private void HandleLeft(InputEventMouseButton left)
    {
        Vector2 canvas = CanvasPosition(left);
        if (left.Pressed)
        {
            _dragStart = canvas;
            return;
        }

        Vector2 start = _dragStart ?? canvas;
        _dragStart = null;
        _world.DragBox = null;
        if (start.DistanceTo(canvas) > DragThreshold / _camera.Zoom.X)
        {
            _world.SelectInBox(new Rect2(start, canvas - start).Abs(), LocalPlayer);
            return;
        }

        SelectSingle(_world.PickUnit(canvas));
    }

    private void SelectSingle(int id)
    {
        _world.Selected.Clear();
        if (id >= 0)
        {
            _world.Selected.Add(id);
        }
    }

    private void OrderMove(Vector2 canvas)
    {
        if (_world.Selected.Count == 0)
        {
            return;
        }

        Vector2 world = _world.CanvasToWorld(canvas);
        int[] ids = _world.Selected.Where(id => _simulation.UnitOwner[id] == LocalPlayer).OrderBy(id => id).ToArray();
        if (ids.Length > 0)
        {
            LastOrderTile = new Vector2I(Mathf.FloorToInt(world.X), Mathf.FloorToInt(world.Y));
            _pending.Add(new MoveCommand(LocalPlayer, ids, LastOrderTile.X, LastOrderTile.Y));
            _world.MoveMarker = canvas;
        }
    }

    private void ZoomBy(float factor)
    {
        float zoom = Mathf.Clamp(_camera.Zoom.X * factor, 0.35f, 1.6f);
        _camera.Zoom = new Vector2(zoom, zoom);
    }

    private void MoveCamera(float delta)
    {
        Vector2 direction = Vector2.Zero;
        direction.X += Input.IsPhysicalKeyPressed(Key.Right) ? 1f : Input.IsPhysicalKeyPressed(Key.Left) ? -1f : 0f;
        direction.Y += Input.IsPhysicalKeyPressed(Key.Down) ? 1f : Input.IsPhysicalKeyPressed(Key.Up) ? -1f : 0f;
        _camera.Position += direction * CameraSpeed * delta / _camera.Zoom.X;
    }

    private void AdvanceSimulation(double delta)
    {
        _accumulator += delta;
        while (_accumulator >= TickSeconds)
        {
            _accumulator -= TickSeconds;
            _world.CapturePreviousPositions();
            _scenario?.AppendCommands(_simulation, _pending);
            MoveCommand[] batch = _pending.ToArray();
            _pending.Clear();
            long start = Stopwatch.GetTimestamp();
            _simulation.Step(batch);
            _stepMs.Add(Stopwatch.GetElapsedTime(start).TotalMilliseconds);
            _hash = _simulation.ComputeHash();
        }
    }

    /// <summary>Keeps the units on screen, otherwise culling would make the benchmark measure an empty scene.</summary>
    private void FollowBenchCrowd()
    {
        if (!_benchMapView)
        {
            _camera.Position = _camera.Position.Lerp(_world.Centroid(LocalPlayer), 0.1f);
        }

        Rect2 visible = GetCanvasTransform().AffineInverse() * GetViewportRect();
        _visibleUnitSamples += _world.CountUnitsIn(visible);
    }

    private void RecordFrame()
    {
        long now = Stopwatch.GetTimestamp();
        _frameMs.Add(Stopwatch.GetElapsedTime(_lastFrame, now).TotalMilliseconds);
        _lastFrame = now;
    }

    private void FinishBench()
    {
        if (_flags.TryGetValue("shot", out string? shotPath))
        {
            GetViewport().GetTexture().GetImage().SavePng(shotPath);
        }

        double[] frames = _frameMs.Skip(_frameMs.Count / 10).OrderBy(x => x).ToArray();
        double[] steps = _stepMs.OrderBy(x => x).ToArray();
        var report = new Dictionary<string, object>
        {
            ["units"] = _simulation.UnitCount,
            ["renderer"] = RenderingServer.GetCurrentRenderingMethod(),
            ["adapter"] = RenderingServer.GetVideoAdapterName(),
            ["frames"] = frames.Length,
            ["avg_visible_units"] = _visibleUnitSamples / Math.Max(1, _frameMs.Count),
            ["avg_fps"] = 1000.0 / frames.Average(),
            ["frame_p50_ms"] = Percentile(frames, 0.50),
            ["frame_p99_ms"] = Percentile(frames, 0.99),
            ["frame_max_ms"] = frames[^1],
            ["tick_p50_ms"] = Percentile(steps, 0.50),
            ["tick_p99_ms"] = Percentile(steps, 0.99),
            ["final_tick"] = _simulation.Tick,
            ["final_hash"] = _hash.ToString("x16", CultureInfo.InvariantCulture),
        };
        string json = JsonSerializer.Serialize(report, new JsonSerializerOptions { WriteIndented = true });
        GD.Print(json);
        if (_flags.TryGetValue("out", out string? outPath))
        {
            System.IO.File.WriteAllText(outPath, json);
        }

        GetTree().Quit();
    }

    private static double Percentile(double[] sorted, double fraction) =>
        sorted[Math.Min(sorted.Length - 1, (int)(sorted.Length * fraction))];

    private static Dictionary<string, string> ParseFlags(string[] args)
    {
        var flags = new Dictionary<string, string>();
        for (int i = 0; i < args.Length; i++)
        {
            if (args[i].StartsWith("--", StringComparison.Ordinal))
            {
                bool hasValue = i + 1 < args.Length && !args[i + 1].StartsWith("--", StringComparison.Ordinal);
                flags[args[i][2..]] = hasValue ? args[++i] : "";
            }
        }

        return flags;
    }

    private static Font BodyFont() => new SystemFont
    {
        FontNames = new[] { "Apple SD Gothic Neo", "Malgun Gothic", "Noto Sans CJK KR", "Noto Sans KR" },
    };

    private static Font DisplayFont() => new SystemFont
    {
        FontNames = new[] { "AppleMyungjo", "Batang", "Noto Serif CJK KR", "Noto Serif KR" },
        FontWeight = 700,
    };
}
