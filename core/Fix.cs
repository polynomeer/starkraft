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
    public static readonly Fix MaxValue = new(int.MaxValue);

    public static Fix FromRaw(int raw) => new(raw);
    public static Fix FromInt(int value) => new(checked(value * OneRaw));
    public static Fix FromRatio(int numerator, int denominator) => FromInt(numerator) / FromInt(denominator);

    /// <summary>Exact parse of text such as "4.75" or "-0.5", rounding toward zero. No binary floating point is involved.</summary>
    public static Fix FromText(string text)
    {
        const int maxFractionDigits = 9;
        bool negative = text.StartsWith('-');
        string digits = negative ? text[1..] : text;
        int point = digits.IndexOf('.');
        string whole = point < 0 ? digits : digits[..point];
        string fraction = point < 0 ? "" : digits[(point + 1)..];
        if (whole.Length == 0 || (point >= 0 && fraction.Length == 0) || fraction.Length > maxFractionDigits
            || !IsAllDigits(whole) || !IsAllDigits(fraction))
        {
            throw new FormatException($"Not a plain number: '{text}'.");
        }

        long scale = 1;
        long numerator = 0;
        foreach (char digit in fraction)
        {
            numerator = numerator * 10 + (digit - '0');
            scale *= 10;
        }

        long wholeValue = 0;
        foreach (char digit in whole)
        {
            wholeValue = checked(wholeValue * 10 + (digit - '0'));
        }

        long raw = checked((wholeValue << FractionBits) + (numerator << FractionBits) / scale);
        return new Fix(checked((int)(negative ? -raw : raw)));
    }

    private static bool IsAllDigits(string text)
    {
        foreach (char c in text)
        {
            if (c < '0' || c > '9')
            {
                return false;
            }
        }

        return true;
    }

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

    /// <summary>dx² + dy² with 32 fraction bits, so it neither overflows a map-sized distance nor loses small ones.</summary>
    public static long LengthSquaredRaw(Fix dx, Fix dy) =>
        checked((long)dx.Raw * dx.Raw + (long)dy.Raw * dy.Raw);

    public static Fix Length(Fix dx, Fix dy) => new(checked((int)IntegerSqrt((ulong)LengthSquaredRaw(dx, dy))));

    public static bool IsWithin(Fix dx, Fix dy, Fix range) => LengthSquaredRaw(dx, dy) <= (long)range.Raw * range.Raw;

    public static Fix Min(Fix a, Fix b) => a.Raw <= b.Raw ? a : b;

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
