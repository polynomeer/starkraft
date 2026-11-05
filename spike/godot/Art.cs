using System.Collections.Generic;
using Godot;

namespace Samjae.Client;

/// <summary>Loads existing sprites straight from the Kotlin client's resources and paints placeholder terrain, so the spike adds no binary files.</summary>
public sealed class Art
{
    private const string SpriteRoot = "../../sim/src/main/resources/assets/sprites/sampajeon/";

    private readonly Dictionary<string, Texture2D> _textures = new();

    public Texture2D Sprite(string name)
    {
        if (_textures.TryGetValue(name, out Texture2D? cached))
        {
            return cached;
        }

        string path = ProjectSettings.GlobalizePath("res://" + SpriteRoot + name + ".png");
        Image image = Image.LoadFromFile(path);
        image.GenerateMipmaps();
        Texture2D texture = ImageTexture.CreateFromImage(image);
        _textures[name] = texture;
        return texture;
    }

    /// <summary>Atlas with grass variants in columns 0-3, dirt in 4 and rock in 5.</summary>
    public static TileSet BuildTileSet()
    {
        const int columns = 6;
        var atlasImage = Image.CreateEmpty(Iso.TileWidth * columns, Iso.TileHeight, false, Image.Format.Rgba8);
        var noise = new FastNoiseLite { Frequency = 0.09f, Seed = 11 };
        for (int column = 0; column < columns; column++)
        {
            PaintDiamond(atlasImage, column, noise);
        }

        var source = new TileSetAtlasSource
        {
            Texture = ImageTexture.CreateFromImage(atlasImage),
            TextureRegionSize = new Vector2I(Iso.TileWidth, Iso.TileHeight),
        };
        for (int column = 0; column < columns; column++)
        {
            source.CreateTile(new Vector2I(column, 0));
        }

        var tileSet = new TileSet
        {
            TileShape = TileSet.TileShapeEnum.Isometric,
            TileLayout = TileSet.TileLayoutEnum.DiamondDown,
            TileSize = new Vector2I(Iso.TileWidth, Iso.TileHeight),
        };
        tileSet.AddSource(source, 0);
        return tileSet;
    }

    private static void PaintDiamond(Image atlas, int column, FastNoiseLite noise)
    {
        Color baseColor = column switch
        {
            4 => new Color("7a6a48"),
            5 => new Color("56544c"),
            _ => new Color("5f6d38").Lerp(new Color("6f7a3e"), column / 3f),
        };
        float halfWidth = Iso.TileWidth / 2f;
        float halfHeight = Iso.TileHeight / 2f;
        for (int y = 0; y < Iso.TileHeight; y++)
        {
            for (int x = 0; x < Iso.TileWidth; x++)
            {
                float dx = Mathf.Abs(x + 0.5f - halfWidth) / halfWidth;
                float dy = Mathf.Abs(y + 0.5f - halfHeight) / halfHeight;
                if (dx + dy > 1.02f)
                {
                    continue;
                }

                float shade = noise.GetNoise2D(x + column * 37, y * 2) * 0.13f;
                atlas.SetPixel(column * Iso.TileWidth + x, y, baseColor.Lightened(shade).Darkened(-Mathf.Min(shade, 0f)));
            }
        }
    }
}
