using Godot;

namespace Samjae.Client;

/// <summary>World (tile units, x right-down, y left-down) to canvas pixels. The inverse is what mouse picking relies on.</summary>
public static class Iso
{
    public const int TileWidth = 128;
    public const int TileHeight = 64;

    public static Vector2 ToCanvas(float worldX, float worldY) =>
        new((worldX - worldY) * TileWidth / 2f, (worldX + worldY) * TileHeight / 2f);

    public static Vector2 ToWorld(Vector2 canvas)
    {
        float a = canvas.X / (TileWidth / 2f);
        float b = canvas.Y / (TileHeight / 2f);
        return new Vector2((a + b) / 2f, (b - a) / 2f);
    }
}
