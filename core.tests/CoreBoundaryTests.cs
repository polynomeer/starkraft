using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>Enforces the rules that make the core reproducible, so they cannot erode through review alone.</summary>
public class CoreBoundaryTests
{
    private static readonly Assembly Core = typeof(Simulation).Assembly;

    private static readonly Type[] FloatingTypes = { typeof(float), typeof(double), typeof(decimal) };

    private static readonly (string Pattern, string Reason)[] BannedSource =
    {
        (@"\b(float|double|decimal|Single|Double|Decimal|MathF)\b", "floating point"),
        (@"\bMath\.(Sqrt|Pow|Exp|Log\w*|Sin|Cos|Tan|Asin|Acos|Atan\d?|Round|Floor|Ceiling)\b", "floating point math"),
        (@"\b(File|FileStream|Directory|Console|Socket|HttpClient|Environment)\b", "input or output"),
        (@"\b(DateTime|DateTimeOffset|Stopwatch|Guid)\b", "clock or machine state"),
        (@"\bnew Random\b|\bSystem\.Random\b", "unseeded or unpinned random"),
        (@"\b(Parallel|Task|Thread|ThreadPool)\b", "concurrency"),
        (@"\bGodot\b", "engine dependency"),
    };

    [Fact]
    public void Core_only_references_the_runtime()
    {
        string[] others = Core.GetReferencedAssemblies()
            .Select(reference => reference.Name ?? "")
            .Where(name => !name.StartsWith("System.", StringComparison.Ordinal))
            .ToArray();
        Assert.Empty(others);
    }

    [Fact]
    public void Core_signatures_contain_no_floating_point()
    {
        Assert.Empty(FloatingPointMembers(Core.GetTypes()));
    }

    [Fact]
    public void Signature_check_detects_floating_point()
    {
        Assert.NotEmpty(FloatingPointMembers(new[] { typeof(FloatingPointSample) }));
    }

    [Fact]
    public void Core_source_contains_no_banned_constructs()
    {
        string coreDirectory = TestContent.RepositoryPath("core");
        var violations = new List<string>();
        string[] files = Directory.GetFiles(coreDirectory, "*.cs", SearchOption.AllDirectories)
            .Where(path => !IsBuildOutput(path))
            .ToArray();
        Assert.NotEmpty(files);
        foreach (string path in files)
        {
            violations.AddRange(ScanSource(Path.GetFileName(path), File.ReadAllLines(path)));
        }

        Assert.Empty(violations);
    }

    [Fact]
    public void Core_never_iterates_hash_ordered_collections()
    {
        string coreDirectory = TestContent.RepositoryPath("core");
        var violations = new List<string>();
        foreach (string path in Directory.GetFiles(coreDirectory, "*.cs", SearchOption.AllDirectories).Where(path => !IsBuildOutput(path)))
        {
            violations.AddRange(ScanHashIteration(Path.GetFileName(path), File.ReadAllLines(path)));
        }

        Assert.Empty(violations);
    }

    [Theory]
    [InlineData("private readonly Dictionary<int, int> _claims = new();", "foreach (int id in _claims.Keys)")]
    [InlineData("var seen = new HashSet<string>();", "foreach (string id in seen)")]
    [InlineData("private readonly HashSet<int> _tiles = new();", "int[] all = _tiles.ToArray();")]
    public void Hash_iteration_scan_detects_loops(string declaration, string use)
    {
        Assert.NotEmpty(ScanHashIteration("sample.cs", new[] { declaration, use }));
    }

    [Theory]
    [InlineData("private readonly Dictionary<int, int> _claims = new();", "_claims.TryGetValue(id, out int owner);")]
    [InlineData("private readonly SortedDictionary<int, bool[]> _visible = new();", "foreach (bool[] tiles in _visible.Values)")]
    public void Hash_iteration_scan_allows_lookups_and_sorted_loops(string declaration, string use)
    {
        Assert.Empty(ScanHashIteration("sample.cs", new[] { declaration, use }));
    }

    /// <summary>
    /// A word boundary before the type name leaves out SortedDictionary and SortedSet.
    /// Dictionary and HashSet enumerate in an order that depends on hashing and insertion history, so rules may look
    /// things up in them but never walk them. Sorted collections and entity stores walk in key order.
    /// </summary>
    private static IEnumerable<string> ScanHashIteration(string name, string[] lines)
    {
        var declared = new HashSet<string>();
        var declaration = new Regex(@"\b(Dictionary|HashSet)<.*?>\s+(\w+)\s*[=;]");
        var implicitDeclaration = new Regex(@"\bvar\s+(\w+)\s*=\s*new\s+(Dictionary|HashSet)<");
        foreach (string line in lines.Select(StripLineComment))
        {
            Match explicitMatch = declaration.Match(line);
            if (explicitMatch.Success)
            {
                declared.Add(explicitMatch.Groups[2].Value);
            }

            Match implicitMatch = implicitDeclaration.Match(line);
            if (implicitMatch.Success)
            {
                declared.Add(implicitMatch.Groups[1].Value);
            }
        }

        for (int number = 0; number < lines.Length; number++)
        {
            string code = StripLineComment(lines[number]);
            foreach (string variable in declared)
            {
                string loop = $@"\bforeach\s*\([^)]*\bin\s+{variable}(\.Keys|\.Values)?\s*\)";
                string linq = $@"\b{variable}(\.Keys|\.Values)?\.(Select|Where|OrderBy|First|Last|ToArray|ToList|Aggregate|Min|Max|Sum)\b";
                if (Regex.IsMatch(code, loop) || Regex.IsMatch(code, linq))
                {
                    yield return $"{name}:{number + 1} iterates hash-ordered '{variable}': {lines[number].Trim()}";
                }
            }
        }
    }

    [Theory]
    [InlineData("double speed = 1;")]
    [InlineData("var value = Math.Sqrt(2);")]
    [InlineData("File.ReadAllText(path);")]
    [InlineData("var now = DateTime.UtcNow;")]
    [InlineData("var random = new Random();")]
    public void Source_scan_detects_banned_code(string line)
    {
        Assert.NotEmpty(ScanSource("sample.cs", new[] { line }));
    }

    [Theory]
    [InlineData("int clamped = Math.Clamp(value, 0, 10);")]
    [InlineData("private DeterministicRandom _random;")]
    [InlineData("// A comment may mention double or File.")]
    public void Source_scan_allows_safe_code(string line)
    {
        Assert.Empty(ScanSource("sample.cs", new[] { line }));
    }

    private static IEnumerable<string> ScanSource(string name, string[] lines)
    {
        for (int number = 0; number < lines.Length; number++)
        {
            string code = StripLineComment(lines[number]);
            foreach ((string pattern, string reason) in BannedSource)
            {
                if (Regex.IsMatch(code, pattern))
                {
                    yield return $"{name}:{number + 1} {reason}: {lines[number].Trim()}";
                }
            }
        }
    }

    private static string StripLineComment(string line)
    {
        int comment = line.IndexOf("//", StringComparison.Ordinal);
        return comment < 0 ? line : line[..comment];
    }

    private static bool IsBuildOutput(string path) =>
        path.Split(Path.DirectorySeparatorChar).Any(part => part is "bin" or "obj");

    private static IEnumerable<string> FloatingPointMembers(IEnumerable<Type> types)
    {
        const BindingFlags all = BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance
            | BindingFlags.Static | BindingFlags.DeclaredOnly;
        foreach (Type type in types)
        {
            foreach (FieldInfo field in type.GetFields(all).Where(field => IsFloating(field.FieldType)))
            {
                yield return $"{type.FullName}.{field.Name}";
            }

            foreach (MethodBase method in type.GetMethods(all).Cast<MethodBase>().Concat(type.GetConstructors(all)))
            {
                bool returnsFloating = method is MethodInfo info && IsFloating(info.ReturnType);
                if (returnsFloating || method.GetParameters().Any(parameter => IsFloating(parameter.ParameterType)))
                {
                    yield return $"{type.FullName}.{method.Name}";
                }
            }
        }
    }

    private static bool IsFloating(Type type)
    {
        Type element = type.HasElementType ? type.GetElementType()! : type;
        return FloatingTypes.Contains(element) || element.GetGenericArguments().Any(IsFloating);
    }

    private sealed class FloatingPointSample
    {
        public float Value = 0;
    }
}
