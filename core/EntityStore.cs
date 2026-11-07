using System;

namespace Samjae.Core;

/// <summary>
/// Components indexed directly by entity id. Iteration is always ascending by id regardless of insertion history,
/// which removes the ordering hazard of a hash map. Ids are never reused.
/// </summary>
public sealed class EntityStore<T>
{
    private T[] _values = new T[64];
    private bool[] _present = new bool[64];

    public int Count { get; private set; }

    public bool Has(int id) => id >= 0 && id < _present.Length && _present[id];

    public void Set(int id, T value)
    {
        EnsureCapacity(id);
        if (!_present[id])
        {
            _present[id] = true;
            Count++;
        }

        _values[id] = value;
    }

    public ref T Get(int id)
    {
        if (!Has(id))
        {
            throw new InvalidOperationException($"Entity {id} has no {typeof(T).Name}.");
        }

        return ref _values[id];
    }

    public bool TryGet(int id, out T value)
    {
        if (Has(id))
        {
            value = _values[id];
            return true;
        }

        value = default!;
        return false;
    }

    public bool Remove(int id)
    {
        if (!Has(id))
        {
            return false;
        }

        _present[id] = false;
        _values[id] = default!;
        Count--;
        return true;
    }

    /// <summary>Safe while removing the current or any other id during the loop.</summary>
    public Enumerator GetEnumerator() => new(this);

    private void EnsureCapacity(int id)
    {
        if (id < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(id));
        }

        if (id < _present.Length)
        {
            return;
        }

        int size = _present.Length;
        while (size <= id)
        {
            size *= 2;
        }

        Array.Resize(ref _values, size);
        Array.Resize(ref _present, size);
    }

    public struct Enumerator
    {
        private readonly EntityStore<T> _store;
        private int _id;

        internal Enumerator(EntityStore<T> store)
        {
            _store = store;
            _id = -1;
        }

        public int Current => _id;

        public bool MoveNext()
        {
            bool[] present = _store._present;
            while (++_id < present.Length)
            {
                if (present[_id])
                {
                    return true;
                }
            }

            return false;
        }
    }
}
