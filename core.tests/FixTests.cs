using System;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

public class FixTests
{
    [Fact]
    public void Multiply_and_divide_keep_sign_and_precision()
    {
        Assert.Equal(Fix.FromRatio(3, 2), Fix.FromInt(3) * Fix.Half);
        Assert.Equal(-(3 * Fix.OneRaw / 2), (Fix.FromInt(-3) * Fix.Half).Raw);
        Assert.Equal(Fix.OneRaw / 4, (Fix.FromInt(1) / Fix.FromInt(4)).Raw);
    }

    [Fact]
    public void Floor_rounds_toward_negative_infinity()
    {
        Assert.Equal(-1, Fix.FromRaw(-1).FloorToInt());
        Assert.Equal(1, Fix.FromRatio(3, 2).FloorToInt());
    }

    [Fact]
    public void Square_root_is_the_exact_integer_floor()
    {
        Assert.Equal(Fix.FromInt(2), Fix.Sqrt(Fix.FromInt(4)));
        Assert.Equal(Fix.Zero, Fix.Sqrt(Fix.Zero));
        for (int raw = 1; raw < 2_000_000; raw += 977)
        {
            long root = Fix.Sqrt(Fix.FromRaw(raw)).Raw;
            long scaled = (long)raw << Fix.FractionBits;
            Assert.True(root * root <= scaled && (root + 1) * (root + 1) > scaled, $"raw {raw}");
        }
    }

    [Fact]
    public void Square_root_of_negative_throws()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Fix.Sqrt(Fix.FromInt(-1)));
    }

    [Fact]
    public void Overflow_throws_instead_of_wrapping()
    {
        Assert.Throws<OverflowException>(() => Fix.FromInt(30000) * Fix.FromInt(30000));
        Assert.Throws<OverflowException>(() => Fix.FromInt(40000));
    }

    [Theory]
    [InlineData("0", 0)]
    [InlineData("4.5", 4 * 65536 + 32768)]
    [InlineData("-0.5", -32768)]
    [InlineData("0.095", 6225)] // 0.095 * 65536 = 6225.92, rounded toward zero
    [InlineData("-0.095", -6225)]
    public void Text_parses_exactly(string text, int raw)
    {
        Assert.Equal(raw, Fix.FromText(text).Raw);
    }

    [Theory]
    [InlineData("")]
    [InlineData(".5")]
    [InlineData("5.")]
    [InlineData("1e3")]
    [InlineData("+1")]
    [InlineData("1.2.3")]
    [InlineData("1.0000000001")]
    public void Text_rejects_anything_but_plain_numbers(string text)
    {
        Assert.Throws<FormatException>(() => Fix.FromText(text));
    }

    [Fact]
    public void Text_rejects_values_out_of_range()
    {
        Assert.Throws<OverflowException>(() => Fix.FromText("40000"));
    }

    [Fact]
    public void Length_and_range_checks_use_exact_squares()
    {
        Assert.Equal(Fix.FromInt(5), Fix.Length(Fix.FromInt(3), Fix.FromInt(-4)));
        Assert.True(Fix.IsWithin(Fix.FromInt(3), Fix.FromInt(4), Fix.FromInt(5)));
        Assert.False(Fix.IsWithin(Fix.FromInt(3), Fix.FromRaw(Fix.FromInt(4).Raw + 1), Fix.FromInt(5)));
        Assert.Equal(1L, Fix.LengthSquaredRaw(Fix.FromRaw(1), Fix.Zero));
    }

    [Fact]
    public void Length_handles_distances_too_large_for_a_fixed_square()
    {
        Assert.Equal(Fix.FromInt(25000), Fix.Length(Fix.FromInt(15000), Fix.FromInt(20000)));
    }

    [Fact]
    public void Divide_by_zero_throws()
    {
        Assert.Throws<DivideByZeroException>(() => Fix.One / Fix.Zero);
    }
}
