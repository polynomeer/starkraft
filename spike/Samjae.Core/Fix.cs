using System;

namespace Samjae.Core;

/// <summary>Q16.16 fixed-point number. All game rules use this type so every platform computes identical bits.</summary>
public readonly struct Fix : IEquatable<Fix>, IComparable<Fix>
{
    public const int FractionBits = 16;
    public const int OneRaw = 1 << FractionBits;

    public readonly int Raw;

    private Fix(int raw) => Raw = raw;

    public static readonly Fix Zero = new(0);
    public static readonly Fix One = new(OneRaw);
    public static readonly Fix Half = new(OneRaw / 2);

    public static Fix FromRaw(int raw) => new(raw);
    public static Fix FromInt(int value) => new(checked(value * OneRaw));
    public static Fix FromRatio(int numerator, int denominator) => FromInt(numerator) / FromInt(denominator);

    public int FloorToInt() => Raw >> FractionBits;

    public static Fix operator +(Fix a, Fix b) => new(checked(a.Raw + b.Raw));
    public static Fix operator -(Fix a, Fix b) => new(checked(a.Raw - b.Raw));
    public static Fix operator -(Fix a) => new(checked(-a.Raw));
    public static Fix operator *(Fix a, Fix b) => new(checked((int)(((long)a.Raw * b.Raw) >> FractionBits)));
    public static Fix operator /(Fix a, Fix b) => new(checked((int)(((long)a.Raw << FractionBits) / b.Raw)));
    public static bool operator <(Fix a, Fix b) => a.Raw < b.Raw;
    public static bool operator >(Fix a, Fix b) => a.Raw > b.Raw;
    public static bool operator <=(Fix a, Fix b) => a.Raw <= b.Raw;
    public static bool operator >=(Fix a, Fix b) => a.Raw >= b.Raw;
    public static bool operator ==(Fix a, Fix b) => a.Raw == b.Raw;
    public static bool operator !=(Fix a, Fix b) => a.Raw != b.Raw;

    /// <summary>Floor of the exact square root, computed with integers only.</summary>
    public static Fix Sqrt(Fix value)
    {
        if (value.Raw < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(value), "Square root of a negative number.");
        }

        return new Fix((int)IntegerSqrt((ulong)value.Raw << FractionBits));
    }

    private static ulong IntegerSqrt(ulong value)
    {
        ulong result = 0;
        ulong bit = 1UL << 62;
        while (bit > value)
        {
            bit >>= 2;
        }

        while (bit != 0)
        {
            if (value >= result + bit)
            {
                value -= result + bit;
                result = (result >> 1) + bit;
            }
            else
            {
                result >>= 1;
            }

            bit >>= 2;
        }

        return result;
    }

    public bool Equals(Fix other) => Raw == other.Raw;
    public override bool Equals(object? obj) => obj is Fix other && Equals(other);
    public override int GetHashCode() => Raw;
    public int CompareTo(Fix other) => Raw.CompareTo(other.Raw);
    public override string ToString() => $"Fix(raw={Raw})";
}
