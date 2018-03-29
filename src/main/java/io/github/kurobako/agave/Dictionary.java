package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringJoiner;

import static io.github.kurobako.agave.BiFunction.flip;
import static io.github.kurobako.agave.Option.fromNullable;
import static io.github.kurobako.agave.Pair.pair;
import static java.lang.System.arraycopy;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;

@Immutable
public abstract class Dictionary<K, V> implements Foldable<Pair<K, V>>, Iterable<Pair<K, V>> {
  private Dictionary() {}

  public final @Nonnull Dictionary<K, V> insert(K key, V value) {
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("key");
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return insert(0, key, key.hashCode(), value);
  }

  abstract @Nonnull Dictionary<K, V> insert(int depth, K key, int hash, V value);

  public final @Nonnull V lookup(K key, Value<V> ifAbsent) {
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("key");
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("ifAbsent");
    V result = lookup(0, key, key.hashCode());
    return result != null ? result : ifAbsent.get();
  }

  public final @Nullable V lookupNullable(K key) {
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("key");
    return lookup(0, key, key.hashCode());
  }

  public final @Nonnull Option<V> lookup(K key) {
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("key");
    return fromNullable(lookup(0, key, key.hashCode()));
  }

  abstract @Nullable V lookup(int depth, K key, int hash);

  public final @Nonnull Dictionary<K, V> delete(K key) {
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("key");
    final Dictionary<K, V> result = delete(0, key, key.hashCode());
    return result != null ? result : this;
  }

  abstract @Nullable Dictionary<K, V> delete(int depth, K key, int hash);

  @Override
  public final @Nonnull <B> B foldLeft(BiFunction<? super B, ? super Pair<K, V>, ? extends B> function, B initial) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return fold(function, initial);
  }

  @Override
  public final @Nonnull <B> B foldRight(BiFunction<? super Pair<K, V>, ? super B, ? extends B> function, B initial) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return fold(flip(function), initial);
  }

  abstract @Nonnull <B> B fold(BiFunction<? super B, ? super Pair<K, V>, ? extends B> function, B initial);

  public abstract @Nonnull <U> Dictionary<K, U> map(Function<? super V, ? extends U> function);

  public abstract @Nonnull Map<K, V> asJavaUtilMap();

  public final boolean isEmpty() {
    return size() == 0;
  }

  public abstract int size();

  @Override
  public final int hashCode() {
    return fold((i, kv) -> 31 * i + kv.hashCode(), 0);
  }

  @Override
  @SuppressWarnings("unchecked")
  public final boolean equals(Object o) {
    if (!(o instanceof Dictionary)) return false;
    final Dictionary that = (Dictionary) o;
    Object key;
    int hash;
    Object value;
    for (Pair kv : this) {
      key = kv.first();
      hash = key.hashCode();
      value = kv.second();
      if (!value.equals(that.lookup(0, key, hash))) return false;
    }
    return true;
  }

  @Override
  public final String toString() {
    final StringJoiner result = new StringJoiner(", ", "{", "}");
    forEachLeft(kv -> result.add(kv.first() + " = " + kv.second()));
    return result.toString();
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <K, V> Dictionary<K, V> dictionary() {
    return (Dictionary<K, V>) Bitmap.EMPTY;
  }

  public static @Nonnull <K, V> Dictionary<K, V> dictionary(K key, V value) {
    //noinspection ConstantConditions
    if (key == null) throw new NullPointerException("key");
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Bitmap<>(0, key, key.hashCode(), value);
  }

  private static @Nonnull <A> A[] arraySet(A[] src, int idx, @Nullable A val) {
    assert idx < src.length;
    final A[] result = src.clone();
    result[idx] = val;
    return result;
  }

  @SuppressWarnings("unchecked")
  private static @Nonnull <A> A[] arrayRemove(A[] src, int idx) {
    assert idx < src.length;
    A[] result = (A[]) new Object[src.length - 1];
    System.arraycopy(src, 0, result, 0, idx);
    System.arraycopy(src, idx + 1, result, idx, result.length - idx);
    return result;
  }

  private static abstract class AsMap<K, V> extends Dictionary<K, V> implements Map<K, V> {

    @Override
    public final @Nonnull Map<K, V> asJavaUtilMap() {
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object o) {
      return lookupNullable((K) o) != null;
    }

    @Override
    public boolean containsValue(Object o) {
      for (Pair<K, V> kv : this) if (kv.second().equals(o)) return true;
      return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object o) {
      return lookupNullable((K) o);
    }

    @Override
    public V put(K k, V v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nonnull Set<K> keySet() {
      final HashSet<K> result = new HashSet<>();
      for (Pair<K, V> kv : this) result.add(kv.first());
      return unmodifiableSet(result);
    }

    @Override
    public @Nonnull Collection<V> values() {
      final ArrayList<V> result = new ArrayList<>();
      for (Pair<K, V> kv : this) result.add(kv.second());
      return unmodifiableCollection(result);
    }

    @Override
    public @Nonnull Set<Entry<K, V>> entrySet() {
      final HashSet<Entry<K, V>> result = new HashSet<>();
      for (Pair<K, V> kv : this) result.add(new AbstractMap.SimpleImmutableEntry<>(kv.first(), kv.second()));
      return unmodifiableSet(result);
    }
  }

  private static final class Array<K, V> extends AsMap<K, V> {
    final int n;
    final Dictionary<K, V>[] data;
    int size = -1;

    Array(int n, Dictionary<K, V>[] data) {
      this.n = n;
      this.data = data;
    }

    @Override
    @Nonnull Dictionary<K, V> insert(int depth, K key, int hash, V value) {
      final int idx = (hash >>> depth) & 0x01f;
      final Dictionary<K, V> dict = data[idx];
      if (dict == null) return new Array<>(n + 1, arraySet(data, idx, new Bitmap<>(depth + 5, key, hash, value)));
      Dictionary<K, V> newDict = dict.insert(depth + 5, key, hash, value);
      if (dict != newDict) return new Array<>(n, arraySet(data, idx, newDict));
      return this;
    }

    @Override
    @Nullable V lookup(int depth, K key, int hash) {
      final Dictionary<K, V> dict = data[(hash >>> depth) & 0x01f];
      return dict == null ? null : dict.lookup(depth + 5, key, hash);
    }

    @Override
    @Nonnull Dictionary<K, V> delete(int depth, K key, int hash) {
      final int idx = (hash >>> depth) & 0x01f;
      final Dictionary<K, V> dict = data[idx];
      if (dict == null) return this;
      final Dictionary<K, V> newDict = dict.delete(depth + 5, key, hash);
      if (dict != newDict) {
        if (newDict != null) return new Array<>(n, arraySet(data, idx, newDict));
        if (n > 8) return new Array<>(n, arraySet(data, idx, null));
        final Object[] bmpData = new Object[n - 1];
        int bitmap = 0;
        Object cursor;
        for (int i = 0; i < data.length; i++) {
          cursor = data[i];
          if (cursor != null && i != idx) {
            bmpData[i] = cursor;
            bitmap |= 1 << i;
          }
        }
        return new Bitmap<>(bitmap, bmpData);
      }
      return this;
    }

    @Override
    @Nonnull<B> B fold(BiFunction<? super B, ? super Pair<K, V>, ? extends B> function, B initial) {
      B result = initial;
      for (Dictionary<K, V> dict : data) {
        if (dict != null) result = dict.fold(function, result);
      }
      return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull<U> Dictionary<K, U> map(Function<? super V, ? extends U> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      final int dataLength = data.length;
      final Dictionary<K, U>[] newData = (Dictionary<K, U>[]) new Dictionary[dataLength];
      Dictionary<K, V> cursor;
      for (int i = 0; i < dataLength; i++) {
        cursor = data[i];
        if (cursor != null) newData[i] = cursor.map(function);
      }
      return new Array<>(n, newData);
    }

    @Override
    public int size() {
      return size == -1 ? calculateSize() : size;
    }

    private int calculateSize() {
      int result = 0;
      for (Dictionary<K, V> o : data) {
        if (o != null) result += o.size();
      }
      size = result;
      return result;
    }

    @Override
    public @Nonnull Iterator<Pair<K, V>> iterator() {
      return new Iterator<Pair<K, V>>() {
        int cursor;
        @Nullable Iterator<Pair<K, V>> inner;

        @Override
        public boolean hasNext() {
          while (true) {
            if (inner != null) {
              if (inner.hasNext()) return true;
              inner = null;
            }
            if (cursor < data.length) {
              final Dictionary<K, V> dict = data[cursor++];
              if (dict != null) inner = dict.iterator();
            } else return false;
          }
        }

        @Override
        public Pair<K, V> next() {
          if (!hasNext()) throw new NoSuchElementException();
          return inner.next();
        }
      };
    }
  }

  private static final class Bitmap<K, V> extends AsMap<K, V> {
    static final @Nonnull Bitmap<Object, Object> EMPTY = new Bitmap<>(0, new Object[]{});

    final int bitmap;
    final @Nonnull Object[] data;
    int size = -1;

    Bitmap(int bitmap, Object[] data) {
      this.bitmap = bitmap;
      this.data = data;
    }

    Bitmap(int depth, K key, int hash, V value) {
      bitmap = 1 << ((hash >>> depth) & 0x01f);
      data = new Object[1];
      data[0] = pair(key, value);
      size = 1;
    }

    private int index(int bit){
      return Integer.bitCount(bitmap & (bit - 1));
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nonnull Dictionary<K, V> insert(int depth, K key, int hash, V value) {
      int bit = 1 << ((hash >>> depth) & 0x01f);
      int idx = index(bit);
      if ((bitmap & bit) != 0) {
        final Object o = data[idx];
        if (o instanceof Dictionary) {
          final Dictionary<K, V> oldDict = (Dictionary<K, V>) o;
          Dictionary<K, V> newDict = oldDict.insert(depth + 5, key, hash, value);
          return oldDict != newDict ? new Bitmap<>(bitmap, arraySet(data, idx, newDict)) : this;
        } else {
          assert o instanceof Pair;
          final Pair<K, V> kv = (Pair<K, V>) o;
          final K oldKey = kv.first();
          final V oldValue = kv.second();
          if (key.equals(oldKey)) {
            return value != oldValue ? new Bitmap(bitmap, arraySet(data, idx, pair(key, value))) : this;
          } else {
            final int newDepth = depth + 5;
            final int oldHash = oldKey.hashCode();
            if (oldHash != hash) return new Bitmap<>(bitmap, arraySet(data, idx, new Bitmap<>(newDepth, oldKey, oldHash, oldValue).insert(newDepth, key, hash, value)));
            return new Collision<>(hash, 2, new Pair[]{ kv, pair(key, value) });
          }
        }
      } else {
        final int n = Integer.bitCount(bitmap);
        if (n < 16) {
          final Object[] newData = new Object[n + 1];
          arraycopy(data, 0, newData, 0, idx);
          arraycopy(data, idx, newData, idx + 1, n - idx);
          newData[idx] = pair(key, value);
          return new Bitmap(bitmap | bit, newData);
        } else {
          final Dictionary<K, V>[] dicts = (Dictionary<K, V>[]) new Dictionary[32];
          Object o;
          int j = 0;
          for (int i = 0; i < 32; i++) {
            if (((bitmap >>> i) & 1) != 0) {
              o = data[j++];
              if (o instanceof Dictionary) {
                dicts[i] = (Dictionary<K, V>) o;
              } else {
                assert o instanceof Pair;
                final Pair<K, V> kv = (Pair<K, V>) o;
                final K k = kv.first();
                final V v = kv.second();
                dicts[i] = new Bitmap<>(depth + 5, k, k.hashCode(), v);
              }
            }
          }
          dicts[(hash >>> depth) & 0x01f] = new Bitmap<>(depth + 5, key, hash, value);
          return new Array<>(n + 1, dicts);
        }
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable V lookup(int depth, K key, int hash) {
      int bit = 1 << ((hash >>> depth) & 0x01f);
      if ((bitmap & bit) == 0) return null;
      int idx = index(bit);
      Object o = data[idx];
      if(o instanceof Dictionary) {
        return ((Dictionary<K, V>) o).lookup(depth + 5,key, hash);
      } else {
        assert o instanceof Pair;
        final Pair<K, V> kv = (Pair<K, V>) o;
        final K k = kv.first();
        final V v = kv.second();
        return key.equals(k) ? v : null;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable Dictionary<K, V> delete(int depth, K key, int hash) {
      final int bit = 1 << ((hash >>> depth) & 0x01f);
      if ((bitmap & bit) == 0) return this;
      final int idx = index(bit);
      Object o = data[idx];
      if (o instanceof Dictionary) {
        Dictionary<K, V> newDict = ((Dictionary<K, V>) o).delete(depth + 5, key, hash);
        if (o == newDict) return this;
        if (newDict != null) return new Bitmap<>(bitmap, arraySet(data, idx, newDict));
        if (bitmap == bit) return null;
        return new Bitmap<>(bitmap ^ bit, arrayRemove(data, idx));
      } else {
        assert o instanceof Pair;
        final K k = ((Pair<K, V>) o).first();
        return key.equals(k) ? new Bitmap<>(bitmap ^ bit, arrayRemove(data, idx)) : this;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nonnull<B> B fold(BiFunction<? super B, ? super Pair<K, V>, ? extends B> function, B initial) {
      B result = initial;
      for (Object o : data) {
        if (o instanceof Dictionary) {
          result = ((Dictionary<K, V>) o).fold(function, result);
        } else {
          assert o instanceof Pair;
          result = function.apply(result, (Pair<K, V>) o);
        }
      }
      return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull<U> Dictionary<K, U> map(Function<? super V, ? extends U> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      final int dataLength = data.length;
      Object[] newData = new Object[dataLength];
      Object o;
      for (int i = 0; i < dataLength; i++) {
        o = data[i];
        if (o instanceof Dictionary) {
          newData[i] = ((Dictionary<K, V>) o).map(function);
        } else {
          assert o instanceof Pair;
          newData[i] = ((Pair<K, V>) o).biMap(a -> a, function);
        }
      }
      return new Bitmap<>(bitmap, newData);
    }

    @Override
    public int size() {
      return size == -1 ? calculateSize() : size;
    }

    private int calculateSize() {
      int result = 0;
      for (Object value : data) {
        result += value instanceof Dictionary ? ((Dictionary) value).size() : 1;
      }
      size = result;
      return result;
    }

    @Override
    public @Nonnull Iterator<Pair<K, V>> iterator() {
      return new Iterator<Pair<K, V>>() {
        @Nullable Pair<K, V> next;
        @Nullable Iterator<Pair<K, V>> nextIt;
        int cursor;

        @Override
        public boolean hasNext() {
          if (nextIt != null || next != null) return true;
          return tryStep();
        }

        @Override
        public Pair<K, V> next() {
          Pair<K, V> result;
          if (next != null) {
            result = next;
            next = null;
            return result;
          } else if (nextIt != null) {
            result = nextIt.next();
            if(!nextIt.hasNext()) nextIt = null;
            return result;
          } else if (tryStep()) {
            return next();
          } else throw new NoSuchElementException();
        }

        @SuppressWarnings("unchecked")
        private boolean tryStep() {
          while (cursor < data.length){
            Object kv = data[cursor++];
            if (kv instanceof Pair) {
              next = (Pair<K, V>) kv;
              return true;
            }
            if (kv instanceof Dictionary) {
              Iterator<Pair<K, V>> it = ((Dictionary<K, V>) kv).iterator();
              if (it.hasNext()) {
                nextIt = it;
                return true;
              }
            }
          }
          return false;
        }

      };
    }
  }

  private static final class Collision<K, V> extends AsMap<K, V> {
    final int hash;
    final int n;
    final Pair<K, V>[] data;

    Collision(int hash, int n, Pair<K, V>[] data){
      this.hash = hash;
      this.n = n;
      this.data = data;
    }

    private int idxOf(K key) {
      for (int i = 0; i < n; i++) {
        final Object kv = data[i];
        if (key.equals(((Pair) kv).first())) return i;
      }
      return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nonnull Dictionary<K, V> insert(int depth, K key, int hash, V value) {
      if(hash == this.hash) {
        final int idx = idxOf(key);
        if (idx != -1) {
          if (data[idx].second() != value) {
            final Pair<K, V>[] newData = data.clone();
            newData[idx] = pair(key, value);
            return new Collision<>(hash, n, newData);
          }
          return this;
        } else {
          final Pair<K, V>[] newData = new Pair[n + 1];
          arraycopy(data, 0, newData, 0, n);
          newData[n] = pair(key, value);
          return new Collision<>(hash, n + 1, newData);
        }
      }
      return new Bitmap<>(1 << ((hash >>> depth) & 0x01f), new Object[]{ this });
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable V lookup(int depth, K key, int hash) {
      final int idx = idxOf(key);
      if (idx == -1) {
        return null;
      } else if (key.equals(data[idx].first())) {
        return data[idx].second();
      } else return null;
    }

    @Override
    @Nullable Dictionary<K, V> delete(int depth, K key, int hash) {
      final int idx = idxOf(key);
      if(idx == -1) return this;
      if(n == 1) return null;
      return new Collision<>(hash, n - 1, arrayRemove(data, idx));
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nonnull<B> B fold(BiFunction<? super B, ? super Pair<K, V>, ? extends B> function, B initial) {
      B result = initial;
      for (Object o : data) {
        if (o instanceof Dictionary) {
          result = ((Dictionary<K, V>) o).fold(function, result);
        }
        if (o instanceof Pair) {
          result = function.apply(result, (Pair<K, V>) o);
        }
      }
      return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull<U> Dictionary<K, U> map(Function<? super V, ? extends U> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      final int dataLength = data.length;
      Pair<K, U>[] newData = new Pair[dataLength];
      for (int i = 0; i < dataLength; i++) {
        newData[i] = data[i].biMap(a -> a, function);
      }
      return new Collision<>(hash, n, newData);
    }

    @Override
    public int size() {
      return n;
    }

    @Override
    public @Nonnull Iterator<Pair<K, V>> iterator() {
      return new Iterator<Pair<K, V>>() {
        @Nullable Pair<K, V> next;
        int cursor;

        @Override
        public boolean hasNext() {
          if (next != null) return true;
          return tryStep();
        }

        @Override
        public Pair<K, V> next() {
          Pair<K, V> result;
          if (next != null) {
            result = next;
            next = null;
            return result;
          } else if (tryStep()) {
            return next();
          } else throw new NoSuchElementException();
        }

        private boolean tryStep() {
          while (cursor < data.length) {
            Pair<K, V> kv = data[cursor++];
            if (kv != null) {
              next = kv;
              return true;
            }
          }
          return false;
        }
      };
    }
  }
}
