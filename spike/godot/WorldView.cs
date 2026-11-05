using System;
using System.Collections.Generic;
using Godot;
using Samjae.Core;

namespace Samjae.Client;

/// <summary>Draws the core's state with interpolation between ticks. It only reads from the core.</summary>
public partial class WorldView : Node2D
{
    private const float UnitScale = 0.3f;
    private const float PickHalfWidth = 22f;
    private const float PickHeight = 62f;

    private static readonly string[] UnitSprites = { "gunner", "artisan", "brute" };
    private static readonly string[] BuildingSprites = { "hall", "still", "granary" };

    private Simulation _simulation = null!;
    private TileMapLayer _tiles = null!;
    private Node2D _entities = null!;
    private Sprite2D[] _unitNodes = Array.Empty<Sprite2D>();
    private float[] _previousX = Array.Empty<float>();
    private float[] _previousY = Array.Empty<float>();
    private Vector2[] _canvasPositions = Array.Empty<Vector2>();

    public TileMapLayer Tiles => _tiles;
    public HashSet<int> Selected { get; } = new();
    public Vector2 CanvasOrigin { get; private set; }
    public Vector2? MoveMarker { get; set; }
    public Rect2? DragBox { get; set; }

    public void Build(Simulation simulation, Art art)
    {
        _simulation = simulation;
        BuildTerrain();
        CanvasOrigin = _tiles.MapToLocal(Vector2I.Zero) - Iso.ToCanvas(0.5f, 0.5f);
        _entities = new Node2D { YSortEnabled = true };
        AddChild(_entities);
        BuildBuildings(art);
        BuildUnits(art);
    }

    public Vector2 WorldToCanvas(float x, float y) => CanvasOrigin + Iso.ToCanvas(x, y);
    public Vector2 CanvasToWorld(Vector2 canvas) => Iso.ToWorld(canvas - CanvasOrigin);
    public Vector2 UnitCanvas(int id) => _canvasPositions[id];

    public static Rect2 PickRect(Vector2 feet) =>
        new(feet.X - PickHalfWidth, feet.Y - PickHeight, PickHalfWidth * 2f, PickHeight + 8f);

    /// <summary>The front-most unit whose body contains the point, or -1.</summary>
    public int PickUnit(Vector2 canvas)
    {
        int best = -1;
        float bestY = float.NegativeInfinity;
        for (int id = 0; id < _canvasPositions.Length; id++)
        {
            Vector2 feet = _canvasPositions[id];
            if (feet.Y > bestY && PickRect(feet).HasPoint(canvas))
            {
                best = id;
                bestY = feet.Y;
            }
        }

        return best;
    }

    public void SelectInBox(Rect2 box, int owner)
    {
        Selected.Clear();
        for (int id = 0; id < _canvasPositions.Length; id++)
        {
            if (_simulation.UnitOwner[id] == owner && box.HasPoint(_canvasPositions[id]))
            {
                Selected.Add(id);
            }
        }
    }

    public Vector2 Centroid(int owner)
    {
        Vector2 sum = Vector2.Zero;
        int count = 0;
        for (int id = 0; id < _canvasPositions.Length; id++)
        {
            if (_simulation.UnitOwner[id] == owner)
            {
                sum += _canvasPositions[id];
                count++;
            }
        }

        return count == 0 ? sum : sum / count;
    }

    public int CountUnitsIn(Rect2 rect)
    {
        int count = 0;
        foreach (Vector2 position in _canvasPositions)
        {
            if (rect.HasPoint(position))
            {
                count++;
            }
        }

        return count;
    }

    public void CapturePreviousPositions()
    {
        for (int id = 0; id < _previousX.Length; id++)
        {
            _previousX[id] = _simulation.UnitXRaw[id] / (float)Fix.OneRaw;
            _previousY[id] = _simulation.UnitYRaw[id] / (float)Fix.OneRaw;
        }
    }

    public void UpdateUnits(float alpha)
    {
        for (int id = 0; id < _unitNodes.Length; id++)
        {
            float x = Mathf.Lerp(_previousX[id], _simulation.UnitXRaw[id] / (float)Fix.OneRaw, alpha);
            float y = Mathf.Lerp(_previousY[id], _simulation.UnitYRaw[id] / (float)Fix.OneRaw, alpha);
            Vector2 next = WorldToCanvas(x, y);
            float deltaX = next.X - _canvasPositions[id].X;
            if (Mathf.Abs(deltaX) > 0.05f)
            {
                _unitNodes[id].FlipH = deltaX < 0f;
            }

            _canvasPositions[id] = next;
            _unitNodes[id].Position = next;
        }

        QueueRedraw();
    }

    public override void _Draw()
    {
        foreach (int id in Selected)
        {
            DrawEllipse(_canvasPositions[id], 24f, new Color("e8c860"));
        }

        if (MoveMarker is Vector2 marker)
        {
            DrawEllipse(marker, 30f, new Color("7fd08a"));
        }
    }

    private void DrawEllipse(Vector2 center, float radius, Color color)
    {
        DrawSetTransform(center, 0f, new Vector2(1f, 0.5f));
        DrawArc(Vector2.Zero, radius, 0f, Mathf.Tau, 24, color, 2.5f);
        DrawSetTransform(Vector2.Zero, 0f, Vector2.One);
    }

    private void BuildTerrain()
    {
        _tiles = new TileMapLayer { TileSet = Art.BuildTileSet(), ZIndex = -1 };
        AddChild(_tiles);
        var noise = new FastNoiseLite { Frequency = 0.07f, Seed = 5 };
        ReadOnlySpan<byte> blocked = _simulation.Blocked;
        for (int y = 0; y < _simulation.MapHeight; y++)
        {
            for (int x = 0; x < _simulation.MapWidth; x++)
            {
                bool isBlocked = blocked[y * _simulation.MapWidth + x] != 0;
                int column = isBlocked ? 5 : noise.GetNoise2D(x, y) > 0.28f ? 4 : (x * 7 + y * 13) % 4;
                _tiles.SetCell(new Vector2I(x, y), 0, new Vector2I(column, 0));
            }
        }
    }

    private void BuildBuildings(Art art)
    {
        foreach (BuildingInfo building in _simulation.Buildings)
        {
            Texture2D texture = art.Sprite(BuildingSprites[building.Kind]);
            float tilesWide = building.Width + building.Height;
            float scale = tilesWide * Iso.TileWidth / 2f / texture.GetWidth();
            float footX = building.TileX + building.Width;
            float footY = building.TileY + building.Height;
            _entities.AddChild(new Sprite2D
            {
                Texture = texture,
                TextureFilter = TextureFilterEnum.LinearWithMipmaps,
                Centered = false,
                Offset = new Vector2(-texture.GetWidth() / 2f, -texture.GetHeight()),
                Scale = new Vector2(scale, scale),
                Position = WorldToCanvas(footX, footY),
                Modulate = building.Owner == 0 ? Colors.White : new Color(1f, 0.82f, 0.78f),
            });
        }
    }

    private void BuildUnits(Art art)
    {
        int count = _simulation.UnitCount;
        _unitNodes = new Sprite2D[count];
        _previousX = new float[count];
        _previousY = new float[count];
        _canvasPositions = new Vector2[count];
        CapturePreviousPositions();
        for (int id = 0; id < count; id++)
        {
            Texture2D texture = art.Sprite(UnitSprites[id % UnitSprites.Length]);
            var node = new Sprite2D
            {
                Texture = texture,
                TextureFilter = TextureFilterEnum.LinearWithMipmaps,
                Centered = false,
                Offset = new Vector2(-texture.GetWidth() / 2f, -texture.GetHeight()),
                Scale = new Vector2(UnitScale, UnitScale),
                Modulate = _simulation.UnitOwner[id] == 0 ? Colors.White : new Color(1f, 0.7f, 0.66f),
            };
            _entities.AddChild(node);
            _unitNodes[id] = node;
        }

        UpdateUnits(1f);
    }
}
