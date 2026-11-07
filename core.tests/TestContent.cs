using System;
using System.IO;
using Samjae.Core;

namespace Samjae.Core.Tests;

/// <summary>Finds and loads the repository's real content/ files.</summary>
internal static class TestContent
{
    public static readonly GameContent Real = LoadReal();

    public static string RepositoryPath(string relative)
    {
        for (DirectoryInfo? directory = new(AppContext.BaseDirectory); directory != null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, "core", "Samjae.Core.csproj")))
            {
                return Path.Combine(directory.FullName, relative);
            }
        }

        throw new DirectoryNotFoundException("Could not find the repository root above the test output.");
    }

    private static GameContent LoadReal() => ContentParser.Parse(
        File.ReadAllText(RepositoryPath("content/units.json")),
        File.ReadAllText(RepositoryPath("content/weapons.json")),
        File.ReadAllText(RepositoryPath("content/buildings.json")),
        File.ReadAllText(RepositoryPath("content/techs.json")));
}
