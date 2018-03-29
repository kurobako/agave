package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

import static io.github.kurobako.agave.BiFunction.flip;
import static io.github.kurobako.agave.Pair.pair;

@Immutable
public abstract class Sequence<A> implements Foldable<A>, Iterable<A>, Semigroup<Sequence<A>> {
  private Sequence() {}

  public abstract @Nonnull Sequence<A> push(A value);

  public abstract @Nonnull Sequence<A> inject(A value);

  public final @Nonnull Pair<A, Sequence<A>> pop() {
    return pair(first(), deleteFirst());
  }

  public final @Nonnull Pair<Sequence<A>, A> eject() {
    return pair(deleteLast(), last());
  }

  public abstract @Nonnull A first();

  public abstract @Nonnull A last();

  public abstract @Nonnull Sequence<A> deleteFirst();

  public abstract @Nonnull Sequence<A> deleteLast();

  public abstract @Nonnull Sequence<A> replaceFirst(A value);

  public abstract @Nonnull Sequence<A> replaceLast(A value);

  public abstract @Nonnull A get(int idx);

  public abstract @Nonnull Sequence<A> delete(int idx);

  public abstract @Nonnull Sequence<A> replace(int idx, A value);

  public abstract @Nonnull Sequence<A> insert(int idx, A value);

  public abstract @Nonnull Pair<Sequence<A>, Sequence<A>> split(int idx);

  public abstract @Nonnull Sequence<A> take(int n);

  public abstract @Nonnull Sequence<A> drop(int n);

  public final @Nonnull Sequence<A> reverse() {
    return foldLeft(Sequence::push, sequence());
  }

  @Override
  public @Nonnull Sequence<A> append(Sequence<A> sequence) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    return catenate(this, sequence);
  }

  public abstract @Nonnull <B> Sequence<B> map(Function<? super A, ? extends B> function);

  public abstract @Nonnull <B> Sequence<B> flatMap(Kleisli<? super A, ? extends B> kleisli);

  public final @Nonnull <B, C> Sequence<C> zip(Sequence<B> sequence, BiFunction<? super A, ? super B, ? extends C> function) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final java.util.Iterator<A> firstIt = this.iterator();
    final java.util.Iterator<B> secondIt = sequence.iterator();
    Sequence<C> result = sequence();
    while (firstIt.hasNext() && secondIt.hasNext()) result = result.inject(function.apply(firstIt.next(), secondIt.next()));
    return result;
  }

  public final @Nonnull <B> Sequence<Pair<A, B>> zip(Sequence<B> sequence) {
    return zip(sequence, Pair::pair);
  }

  public abstract @Nonnull Sequence<A> filter(Predicate<? super A> predicate);

  public abstract int length();

  public abstract boolean isEmpty();

  public abstract @Nonnull List<A> asJavaUtilList();

  @Override
  public final int hashCode() {
    return foldLeft((i, kv) -> 31 * i + kv.hashCode(), 0);
  }

  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof Sequence)) return false;
    final Sequence that = (Sequence) o;
    final java.util.Iterator it = that.iterator();
    for (A v : this) {
      if (!it.hasNext()) return false;
      if (!v.equals(it.next())) return false;
    }
    return true;
  }

  @Override
  public final String toString() {
    final StringJoiner result = new StringJoiner(", ", "[", "]");
    forEachLeft(v -> result.add(v.toString()));
    return result.toString();
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <A> Sequence<A> sequence() {
    return (Sequence<A>) Shallow.EMPTY;
  }

  public static @Nonnull <A> Sequence<A> sequence(A sole) {
    //noinspection ConstantConditions
    if (sole == null) throw new NullPointerException("sole");
    return new Shallow<>(sole);
  }

  public static @Nonnull <A> Sequence<A> sequence(A first, A second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Deep<>(first, second);
  }

  public static @Nonnull <A> Sequence<A> sequence(A first, A second, A third) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    //noinspection ConstantConditions
    if (third == null) throw new NullPointerException("third");
    return new Deep<>(new One<>(first), IndexedDeque.empty(), new Two<>(second, third));
  }

  public static @Nonnull <A> Sequence<A> sequence(A first, A second, A third, A fourth) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    //noinspection ConstantConditions
    if (third == null) throw new NullPointerException("third");
    //noinspection ConstantConditions
    if (fourth == null) throw new NullPointerException("fourth");
    return new Deep<>(new Two<>(first, second), IndexedDeque.empty(), new Two<>(third, fourth));
  }

  public static @Nonnull <A> Sequence<A> sequence(A first, A second, A third, A fourth, A fifth) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    //noinspection ConstantConditions
    if (third == null) throw new NullPointerException("third");
    //noinspection ConstantConditions
    if (fourth == null) throw new NullPointerException("fourth");
    //noinspection ConstantConditions
    if (fifth == null) throw new NullPointerException("fifth");
    return new Deep<>(new Two<>(first, second), IndexedDeque.empty(), new Three<>(third, fourth, fifth));
  }

  public static @Nonnull <A> Sequence<A> sequence(A first, A second, A third, A fourth, A fifth, A sixth) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    //noinspection ConstantConditions
    if (third == null) throw new NullPointerException("third");
    //noinspection ConstantConditions
    if (fourth == null) throw new NullPointerException("fourth");
    //noinspection ConstantConditions
    if (fifth == null) throw new NullPointerException("fifth");
    //noinspection ConstantConditions
    if (sixth == null) throw new NullPointerException("sixth");
    return new Deep<>(new Three<>(first, second, third), IndexedDeque.empty(), new Three<>(fourth, fifth, sixth));
  }

  @SafeVarargs
  public static @Nonnull <A> Sequence<A> sequence(A... values) {
    //noinspection ConstantConditions
    if (values == null) throw new NullPointerException("values");
    Sequence<A> result = sequence();
    for (A value : values) result = result.inject(value);
    return result;
  }

  public static @Nonnull <A> Sequence<A> catenate(Sequence<A> first, Sequence<A> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    if (first instanceof Deep) {
      if (second instanceof Deep) {
        final Deep<A> left = (Deep<A>) first;
        final Deep<A> right = (Deep<A>) second;
        IndexedDeque<Node<A>> leftSub = left.sub;
        for (Node<A> node : makeNodes(elementsOf(left.sfx, right.pfx))) leftSub = leftSub.inject(node);
        return new Deep<>(left.pfx, IndexedDeque.catenate(leftSub, right.sub), right.sfx);
      } else {
        assert second instanceof Shallow;
        return second.foldLeft(Sequence::inject, first);
      }
    } else {
      assert first instanceof Shallow;
      return first.foldRight(flip(Sequence::push), second);
    }
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <A> Sequence<A> flatten(Sequence<Sequence<? extends A>> sequence) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    return sequence.foldLeft((fst, snd) -> catenate(fst, (Sequence<A>) snd), sequence());
  }

  public static @Nonnull <A> Sequence<Sequence<A>> traverse(Sequence<Sequence<A>> sequence) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    return sequence.foldRight((v, result) -> result.zip(v, Sequence::push), sequence());
  }

  @SuppressWarnings("unchecked")
  private static @Nonnull <A> A[] elementsOf(Affix<A> left, Affix<A> right) {
    final A[] result = (A[]) new Object[left.measure() + right.measure()];
    final int[] i = {0};
    left.foldLeft((r, m) -> { r[i[0]++] = m; return result; }, result);
    right.foldLeft((r, m) -> { r[i[0]++] = m; return result; }, result);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static @Nonnull <A> Node<A>[] makeNodes(A[] m) {
    assert m.length >= 2;
    assert m.length < 7;
    switch (m.length) {
      case 2: return (Node<A>[]) new Node[]{ new Two<>(m[0], m[1]) };
      case 3: return (Node<A>[]) new Node[]{ new Three<>(m[0], m[1], m[2]) };
      case 4: return (Node<A>[]) new Node[]{ new Two<>(m[0], m[1]), new Two<>(m[2], m[3]) };
      case 5: return (Node<A>[]) new Node[]{ new Two<>(m[0], m[1]), new Three<>(m[2], m[3], m[4]) };
      case 6: return (Node<A>[]) new Node[]{ new Three<>(m[0], m[1], m[2]), new Three<>(m[3], m[4], m[5]) };
      default: {
        assert false;
        return null;
      }
    }
  }

  private static @Nonnull <A> Sequence<A> simplify(IndexedDeque<Node<A>> deque) {
    if (deque instanceof IndexedDeque.Deep) {
      final IndexedDeque<Node<A>> trimmed = ((IndexedDeque.Deep<Node<A>>)deque).trim();
      return new Deep<>(deque.first(), trimmed, deque.last());
    } else return deque.foldLeft((seq, node) -> node.foldLeft(Sequence::inject, seq), sequence());
  }

  private static abstract class AsList<A> extends Sequence<A> implements List<A> {

    @Override
    public final @Nonnull List<A> asJavaUtilList() {
      return this;
    }

    @Override
    public final int size() {
      return length();
    }

    @Override
    public final boolean add(A a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean containsAll(Collection<?> collection) {
      for (Object o : collection) if (!contains(o)) return false;
      return true;
    }

    @Override
    public final boolean addAll(Collection<? extends A> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean addAll(int i, Collection<? extends A> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean removeAll(Collection<?> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean retainAll(Collection<?> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public final A set(int i, A a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final void add(int i, A a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final A remove(int i) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final @Nonnull java.util.Iterator<A> iterator() {
      return listIterator(0);
    }

    @Override
    public final @Nonnull ListIterator<A> listIterator() {
      return listIterator(0);
    }

  }

  private static final class Shallow<A> extends AsList<A> {
    static final @Nonnull Shallow<Object> EMPTY = new Shallow<>();
    static final @Nonnull Object[] EMPTY_ARRAY = new Object[0];

    final @Nullable A a;

    Shallow() {
      a = null;
    }

    Shallow(A a) {
      this.a = a;
    }

    @Override
    public @Nonnull Sequence<A> push(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      return a == null ? new Shallow<>(value) : new Deep<>(value, a);
    }

    @Override
    public @Nonnull Sequence<A> inject(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      return a == null ? new Shallow<>(value) : new Deep<>(a, value);
    }

    @Override
    public @Nonnull A first() {
      if (a == null) throw new NoSuchElementException();
      return a;
    }

    @Override
    public @Nonnull A last() {
      if (a == null) throw new NoSuchElementException();
      return a;
    }

    @Override
    public @Nonnull Sequence<A> deleteFirst() {
      if (a == null) throw new NoSuchElementException();
      return sequence();
    }

    @Override
    public @Nonnull Sequence<A> deleteLast() {
      if (a == null) throw new NoSuchElementException();
      return sequence();
    }

    @Override
    public @Nonnull Sequence<A> replaceFirst(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      if (a == null) throw new NoSuchElementException();
      return new Shallow<>(value);
    }

    @Override
    public @Nonnull Sequence<A> replaceLast(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      if (a == null) throw new NoSuchElementException();
      return new Shallow<>(value);
    }

    @Override
    public @Nonnull A get(int idx) {
      if (a == null || idx != 0) throw new IndexOutOfBoundsException();
      return a;
    }

    @Override
    public @Nonnull Sequence<A> delete(int idx) {
      if (a == null || idx != 0) throw new IndexOutOfBoundsException();
      return sequence();
    }

    @Override
    public @Nonnull Sequence<A> replace(int idx, A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      if (a == null || idx != 0) throw new IndexOutOfBoundsException();
      return new Shallow<>(value);
    }

    @Override
    public @Nonnull Sequence<A> insert(int idx, A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      if (a == null) {
        if (idx != 0) throw new IndexOutOfBoundsException();
        return new Shallow<>(value);
      } else {
        switch (idx) {
          case 0: return new Deep<>(value, a);
          case 1: return new Deep<>(a, value);
          default: throw new IndexOutOfBoundsException();
        }
      }
    }

    @Override
    public @Nonnull Pair<Sequence<A>, Sequence<A>> split(int idx) {
      if (a == null) {
        if (idx != 0) throw new IndexOutOfBoundsException();
        return pair(this, this);
      } else {
        switch (idx) {
          case 0: return pair(sequence(), this);
          case 1: return pair(this, sequence());
          default: throw new IndexOutOfBoundsException();
        }
      }
    }

    @Override
    public @Nonnull Sequence<A> take(int n) {
      if (a == null) {
        if (n != 0) throw new IndexOutOfBoundsException();
        return this;
      } else {
        switch (n) {
          case 0: return sequence();
          case 1: return this;
          default: throw new IndexOutOfBoundsException();
        }
      }
    }

    @Override
    public @Nonnull Sequence<A> drop(int n) {
      if (a == null) {
        if (n != 0) throw new IndexOutOfBoundsException();
        return this;
      } else {
        switch (n) {
          case 0: return this;
          case 1: return sequence();
          default: throw new IndexOutOfBoundsException();
        }
      }
    }

    @Override
    public @Nonnull <B> Sequence<B> map(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return a == null ? sequence() : new Shallow<>(function.apply(a));
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull <B> Sequence<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return a == null ? sequence() : (Sequence<B>) kleisli.apply(a);
    }

    @Override
    public @Nonnull Sequence<A> filter(Predicate<? super A> predicate) {
      //noinspection ConstantConditions
      if (predicate == null) throw new NullPointerException("predicate");
      if (a != null && !predicate.test(a)) return sequence();
      return this;
    }

    @Override
    public int length() {
      return a == null ? 0 : 1;
    }

    @Override
    public boolean isEmpty() {
      return a == null;
    }

    @Override
    public @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return a == null ? initial : function.apply(initial, a);
    }

    @Override
    public @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return a == null ? initial : function.apply(a, initial);
    }

    @Override
    public boolean contains(Object o) {
      return a != null && a.equals(o);
    }

    @Override
    public @Nonnull Object[] toArray() {
      return a == null ? EMPTY_ARRAY : new Object[]{ a };
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T> T[] toArray(T[] ts) {
      if (a == null) {
        if (ts.length > 0) ts[0] = null;
        return ts;
      } else {
        Object[] result = ts.length < 1 ? (Object[]) Array.newInstance(ts.getClass().getComponentType(), 1) : ts;
        result[0] = a;
        if (result.length > 1) result[1] = null;
        return (T[]) result;
      }
    }

    @Override
    public int indexOf(Object o) {
      return a != null && a.equals(o) ? 0 : -1;
    }

    @Override
    public int lastIndexOf(Object o) {
      return a != null && a.equals(o) ? 0 : -1;
    }

    @Override
    public ListIterator<A> listIterator(int i) {
      if (i != 0) throw new IndexOutOfBoundsException();
      return a == null ? Collections.emptyListIterator() : new Iterator<>(this, i);
    }

    @Override
    public @Nonnull List<A> subList(int fromIndex, int toIndex) {
      if (a == null) {
        if (fromIndex != 0 || toIndex != 0) throw new IndexOutOfBoundsException();
        return asJavaUtilList();
      } else {
        if (fromIndex < 0 || toIndex > 1 || fromIndex > toIndex) throw new IndexOutOfBoundsException();
        if (fromIndex == toIndex) return Sequence.<A>sequence().asJavaUtilList();
        else return this.asJavaUtilList();
      }
    }

  }

  private static final class Deep<A> extends AsList<A> {
    final @Nonnull Affix<A> pfx;
    final @Nonnull IndexedDeque<Node<A>> sub;
    final @Nonnull Affix<A> sfx;

    Deep(Affix<A> pfx, IndexedDeque<Node<A>> sub, Affix<A> sfx) {
      this.pfx = pfx;
      this.sub = sub;
      this.sfx = sfx;
    }

    Deep(A first, A second) {
      pfx = new One<>(first);
      sub = IndexedDeque.empty();
      sfx = new One<>(second);
    }

    @Override
    public @Nonnull Sequence<A> push(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      if (!(pfx instanceof Three)) return new Deep<>(pfx.push(value), sub, sfx);
      else return new Deep<>(new One<>(value), sub.push((Three<A>) pfx), sfx);
    }

    @Override
    public @Nonnull Sequence<A> inject(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      if (!(sfx instanceof Three)) return new Deep<>(pfx, sub, sfx.inject(value));
      else return new Deep<>(pfx, sub.inject((Three<A>) sfx), new One<>(value));
    }

    @Override
    public @Nonnull A first() {
      return pfx.head();
    }

    @Override
    public @Nonnull A last() {
      return sfx.last();
    }

    @Override
    public @Nonnull Sequence<A> deleteFirst() {
      if (pfx instanceof Node) {
        final Affix<A> newPfx = pfx.tail();
        assert newPfx != null;
        return new Deep<>(newPfx, sub, sfx);
      } else if (!sub.isEmpty()) {
        final Node<A> newPfx = sub.first();
        final IndexedDeque<Node<A>> newSub = sub.removeFirst();
        assert newPfx != null;
        assert newSub != null;
        return new Deep<>(newPfx, newSub, sfx);
      } else {
        final Affix<A> sfxTail = sfx.tail();
        return sfxTail == null ? new Shallow<>(sfx.head()) : new Deep<>(new One<>(sfx.head()), IndexedDeque.empty(), sfxTail);
      }
    }

    @Override
    public @Nonnull Sequence<A> deleteLast() {
      if (sfx instanceof Node) {
        final Affix<A> newSfx = sfx.init();
        assert newSfx != null;
        return new Deep<>(pfx, sub, newSfx);
      } else if (!sub.isEmpty()) {
        final Node<A> newSfx = sub.last();
        final IndexedDeque<Node<A>> newSub = sub.removeLast();
        assert newSfx != null;
        assert newSub != null;
        return new Deep<>(pfx, newSub, newSfx);
      } else {
        final Affix<A> pfxInit = pfx.init();
        return pfxInit == null ? new Shallow<>(pfx.last()) : new Deep<>(pfxInit, IndexedDeque.empty(), new One<>(pfx.last()));
      }
    }

    @Override
    public @Nonnull Sequence<A> replaceFirst(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      return new Deep<>(pfx.replaceHead(value), sub, sfx);
    }

    @Override
    public @Nonnull Sequence<A> replaceLast(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      return new Deep<>(pfx, sub, sfx.replaceLast(value));
    }

    @Override
    public @Nonnull A get(int idx) {
      if (idx < 0) throw new IndexOutOfBoundsException();
      final int pfxMeasure = pfx.measure();
      if (idx < pfxMeasure) return pfx.get(idx);
      idx -= pfxMeasure;
      final int subMeasure = sub.measure();
      if (idx < subMeasure) {
        IndexedDeque.SplitPoint<Node<A>> sp = new IndexedDeque.SplitPoint<>(false, false);
        idx = sub.splitAt(idx, sp);
        assert sp.point != null;
        return sp.point.get(idx);
      }
      idx -= subMeasure;
      final int sfxMeasure = sfx.measure();
      if (idx < sfxMeasure) return sfx.get(idx);
      throw new IndexOutOfBoundsException();
    }

    private @Nullable Split<A> splitAt(int idx, boolean trackLeft, boolean trackRight) {
      if (idx < 0) return null;
      final int pfxMeasure = pfx.measure();
      if (idx < pfxMeasure) {
        final Split<A> innermost = pfx.splitAt(idx);
        final Sequence<A> left = trackLeft ? innermost.left : sequence();
        final Sequence<A> right = trackRight ? catenate(innermost.right, sfx.foldLeft(Sequence::inject, simplify(sub))) : sequence();
        return new Split<>(left, innermost.point, right);
      }
      idx -= pfxMeasure;
      final int subMeasure = sub.measure();
      if (idx < subMeasure) {
        final IndexedDeque.SplitPoint<Node<A>> sp = new IndexedDeque.SplitPoint<>(trackLeft, trackRight);
        idx = sub.splitAt(idx, sp);
        assert sp.left != null;
        assert sp.point != null;
        assert sp.right != null;
        final Split<A> innermost = sp.point.splitAt(idx);
        final Sequence<A> left = trackLeft ? catenate(pfx.foldRight(flip(Sequence::push), simplify(sp.left)), innermost.left) : sequence();
        final Sequence<A> right = trackRight ? catenate(innermost.right, sfx.foldLeft(Sequence::inject, simplify(sp.right))) : sequence();
        return new Split<>(left, innermost.point, right);
      }
      idx -= subMeasure;
      final int sfxMeasure = sfx.measure();
      if (idx < sfxMeasure) {
        final Split<A> innermost = sfx.splitAt(idx);
        final Sequence<A> left = trackLeft ? catenate(pfx.foldRight(flip(Sequence::push), simplify(sub)), innermost.left) : sequence();
        final Sequence<A> right = trackRight ? innermost.right : sequence();
        return new Split<>(left, innermost.point, right);
      }
      return null;
    }

    @Override
    public @Nonnull Sequence<A> delete(int idx) {
      final Split<A> split = splitAt(idx, true, true);
      if (split == null) throw new IndexOutOfBoundsException();
      return catenate(split.left, split.right);
    }

    @Override
    public @Nonnull Sequence<A> replace(int idx, A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      final Split<A> split = splitAt(idx, true, true);
      if (split == null) throw new IndexOutOfBoundsException();
      return catenate(split.left, split.right.push(value));
    }

    @Override
    public @Nonnull Sequence<A> insert(int idx, A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      final Split<A> split = splitAt(idx, true, true);
      if (split == null) throw new IndexOutOfBoundsException();
      return catenate(split.left.inject(value), split.right.push(value));
    }

    @Override
    public @Nonnull Pair<Sequence<A>, Sequence<A>> split(int idx) {
      final Split<A> split = splitAt(idx, true, true);
      if (split == null) throw new IndexOutOfBoundsException();
      return pair(split.left, split.right.push(split.point));
    }

    @Override
    public @Nonnull Sequence<A> take(int n) {
      final Split<A> split = splitAt(n, true, false);
      if (split == null) throw new NoSuchElementException();
      return split.left;
    }

    @Override
    public @Nonnull Sequence<A> drop(int n) {
      final Split<A> split = splitAt(n, false, true);
      if (split == null) throw new NoSuchElementException();
      return split.right.push(split.point);
    }

    @Override
    public @Nonnull <B> Sequence<B> map(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return new Deep<>(pfx.transform(function), sub.transform(node -> node.transform(function)), sfx.transform(function));
    }

    @Override
    public @Nonnull <B> Sequence<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      Sequence<Sequence<? extends B>> ss = map(kleisli::apply);
      return flatten(ss);
    }

    @Override
    public @Nonnull Sequence<A> filter(Predicate<? super A> predicate) {
      //noinspection ConstantConditions
      if (predicate == null) throw new NullPointerException("predicate");
      return foldRight((a, seq) -> predicate.test(a) ? seq.push(a) : seq, sequence());
    }

    @Override
    public int length() {
      return pfx.measure() + sub.measure() + sfx.measure();
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      B result = initial;
      result = pfx.foldLeft(function, result);
      result = sub.foldLeft((r, node) -> node.foldLeft(function, r), result);
      result = sfx.foldLeft(function, result);
      return result;
    }

    @Override
    public @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      B result = initial;
      result = sfx.foldRight(function, result);
      result = sub.foldRight((node, r) -> node.foldRight(function, r), result);
      result = pfx.foldRight(function, result);
      return result;
    }

    @Override
    public boolean contains(Object o) {
      for (A a : this) {
        if (a.equals(o)) return true;
      }
      return false;
    }

    @Override
    public @Nonnull Object[] toArray() {
      return toArray(Shallow.EMPTY_ARRAY);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T> T[] toArray(T[] ts) {
      final int len = length();
      Object[] result = ts.length < len ? (Object[]) Array.newInstance(ts.getClass().getComponentType(), len) : ts;
      if (result.length > len) result[len] = null;
      final int[] idx = new int[]{ 0 };
      forEachLeft(v -> result[idx[0]++] = v);
      return (T[]) result;
    }

    @Override
    public int indexOf(Object o) {
      for (int i = 0; i < length(); i++) {
        if (get(i).equals(o)) return i;
      }
      return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
      for (int i = length() - 1; i >= 0; i--) {
        if (get(i).equals(o)) return i;
      }
      return -1;
    }

    @Override
    public @Nonnull ListIterator<A> listIterator(int i) {
      if (i < 0 || i > length()) throw new IndexOutOfBoundsException();
      return new Iterator<>(this, i);
    }

    @Override
    public @Nonnull List<A> subList(int fromIndex, int toIndex) {
      if (fromIndex < 0 || toIndex > length() || fromIndex > toIndex) throw new IndexOutOfBoundsException();
      return drop(fromIndex).take(toIndex - fromIndex).asJavaUtilList();
    }

  }

  private static abstract class Affix<A> implements Measured {

    abstract @Nonnull Node<A> push(A a);

    abstract @Nonnull Node<A> inject(A a);

    abstract @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial);

    abstract @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial);

    abstract @Nonnull <B> Affix<B> transform(Function<? super A, ? extends B> function);

    abstract @Nonnull A head();

    abstract @Nonnull A last();

    abstract @Nullable Affix<A> tail();

    abstract @Nullable Affix<A> init();

    abstract @Nonnull Affix<A> replaceHead(A a);

    abstract @Nonnull Affix<A> replaceLast(A a);

    abstract @Nonnull A get(int idx);

    abstract @Nonnull Split<A> splitAt(int idx);

  }

  private static final class One<A> extends Affix<A> {
    final @Nonnull A sole;

    One(A sole) {
      this.sole = sole;
    }

    @Override
    @Nonnull Node<A> push(A a) {
      return new Two<>(a, sole);
    }

    @Override
    @Nonnull Node<A> inject(A a) {
      return new Two<>(sole, a);
    }

    @Override
    @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
      return function.apply(initial, sole);
    }

    @Override
    @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      return function.apply(sole, initial);
    }

    @Override
    @Nonnull <B> Affix<B> transform(Function<? super A, ? extends B> function) {
      return new One<>(function.apply(sole));
    }

    @Override
    @Nonnull A head() {
      return sole;
    }

    @Override
    @Nonnull A last() {
      return sole;
    }

    @Override
    @Nullable Affix<A> tail() {
      return null;
    }

    @Override
    @Nullable Affix<A> init() {
      return null;
    }

    @Override
    @Nonnull Affix<A> replaceHead(A a) {
      return new One<>(a);
    }

    @Override @Nonnull Affix<A> replaceLast(A a) {
      return new One<>(a);
    }

    @Override
    public int measure() {
      return 1;
    }

    @Override
    @Nonnull A get(int idx) {
      if (idx == 0) return sole;
      assert false;
      return null;
    }

    @Override
    @Nonnull Split<A> splitAt(int idx) {
      if (idx == 0) return new Split<>(sequence(), sole, sequence());
      assert false;
      return null;
    }

  }

  private static abstract class Node<A> extends Affix<A> {
    @Override
    abstract @Nonnull <B> Node<B> transform(Function<? super A, ? extends B> function);
  }

  private static final class Two<A> extends Node<A> {
    final @Nonnull A first;
    final @Nonnull A second;

    Two(A first, A second) {
      this.first = first;
      this.second = second;
    }

    @Override
    @Nonnull Node<A> push(A a) {
      return new Three<>(a, first, second);
    }

    @Override
    @Nonnull Node<A> inject(A a) {
      return new Three<>(first, second, a);
    }

    @Override
    @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
      B result = initial;
      result = function.apply(result, first);
      result = function.apply(result, second);
      return result;
    }

    @Override
    @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      B result = initial;
      result = function.apply(second, result);
      result = function.apply(first, result);
      return result;
    }

    @Override
    @Nonnull <B> Node<B> transform(Function<? super A, ? extends B> function) {
      return new Two<>(function.apply(first), function.apply(second));
    }

    @Override
    @Nonnull A head() {
      return first;
    }

    @Override
    @Nonnull A last() {
      return second;
    }

    @Override
    @Nonnull Affix<A> tail() {
      return new One<>(second);
    }

    @Override
    @Nonnull Affix<A> init() {
      return new One<>(first);
    }

    @Override
    @Nonnull Affix<A> replaceHead(A a) {
      return new Two<>(a, second);
    }

    @Override @Nonnull Affix<A> replaceLast(A a) {
      return new Two<>(first, a);
    }

    @Override
    public int measure() {
      return 2;
    }

    @Override
    @Nonnull A get(int idx) {
      switch (idx) {
        case 0: return first;
        case 1: return second;
        default: {
          assert false;
          return null;
        }
      }
    }

    @Override
    @Nonnull Split<A> splitAt(int idx) {
      switch (idx) {
        case 0: return new Split<>(sequence(), first, new Shallow<>(second));
        case 1: return new Split<>(new Shallow<>(first), second, sequence());
        default: {
          assert false;
          return null;
        }
      }
    }

  }

  private static final class Three<A> extends Node<A> {
    final @Nonnull A first;
    final @Nonnull A second;
    final @Nonnull A third;

    Three(A first, A second, A third) {
      this.first = first;
      this.second = second;
      this.third = third;
    }

    @Override
    @Nonnull Node<A> push(A a) {
      assert false;
      return null;
    }

    @Override
    @Nonnull Node<A> inject(A a) {
      assert false;
      return null;
    }

    @Override
    @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
      B result = initial;
      result = function.apply(result, first);
      result = function.apply(result, second);
      result = function.apply(result, third);
      return result;
    }

    @Override
    @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      B result = initial;
      result = function.apply(third, result);
      result = function.apply(second, result);
      result = function.apply(first, result);
      return result;
    }

    @Override
    @Nonnull <B> Node<B> transform(Function<? super A, ? extends B> function) {
      return new Three<>(function.apply(first), function.apply(second), function.apply(third));
    }

    @Override
    @Nonnull A head() {
      return first;
    }

    @Override
    @Nonnull A last() {
      return third;
    }

    @Override
    @Nonnull Affix<A> tail() {
      return new Two<>(second, third);
    }

    @Override
    @Nonnull Affix<A> init() {
      return new Two<>(first, second);
    }

    @Override
    @Nonnull Affix<A> replaceHead(A a) {
      return new Three<>(a, second, third);
    }

    @Override @Nonnull Affix<A> replaceLast(A a) {
      return new Three<>(first, second, a);
    }

    @Override
    public int measure() {
      return 3;
    }

    @Override
    @Nonnull A get(int idx) {
      switch (idx) {
        case 0: return first;
        case 1: return second;
        case 2: return third;
        default: {
          assert false;
          return null;
        }
      }
    }

    @Override
    @Nonnull Split<A> splitAt(int idx) {
      switch (idx) {
        case 0: return new Split<>(sequence(), first, new Deep<>(second, third));
        case 1: return new Split<>(new Shallow<>(first), second, new Shallow<>(third));
        case 2: return new Split<>(new Deep<>(first, second), third, sequence());
        default: {
          assert false;
          return null;
        }
      }
    }

  }

  private static final class Iterator<A> implements ListIterator<A> {
    int prevIdx;
    final Sequence<A> parent;

    Iterator(Sequence<A> parent, int nextIdx) {
      assert nextIdx >= 0;
      prevIdx = nextIdx - 1;
      this.parent = parent;
    }

    @Override
    public boolean hasNext() {
      final int nextIdx = prevIdx + 1;
      return nextIdx >= 0 && nextIdx < parent.length();
    }

    @Override
    public A next() {
      if (!hasNext()) throw new NoSuchElementException();
      return parent.get(++prevIdx);
    }

    @Override
    public boolean hasPrevious() {
      return prevIdx >= 0 && prevIdx < parent.length();
    }

    @Override
    public A previous() {
      if (!hasPrevious()) throw new NoSuchElementException();
      return parent.get(prevIdx++);
    }

    @Override
    public int nextIndex() {
      return prevIdx + 1;
    }

    @Override
    public int previousIndex() {
      return prevIdx;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void set(A a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(A a) {
      throw new UnsupportedOperationException();
    }

  }

  private static final class Split<A> {
    final @Nonnull Sequence<A> left;
    final @Nonnull A point;
    final @Nonnull Sequence<A> right;

    Split(Sequence<A> left,  A point, Sequence<A> right) {
      this.left = left;
      this.point = point;
      this.right = right;
    }

  }

  @FunctionalInterface
  interface Kleisli<A, B> {
    @Nonnull Sequence<B> apply(A arg);

    static @Nonnull <A, B, C> Kleisli<Pair<A, C>, Pair<B, C>> first(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ac -> kleisli.apply(ac.first()).map(b -> pair(b, ac.second()));
    }

    static @Nonnull <Z, A, B> Kleisli<Pair<Z, A>, Pair<Z, B>> second(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return za -> kleisli.apply(za.second()).map(b -> pair(za.first(), b));
    }

    static @Nonnull <A, B, C> Kleisli<Either<A, C>, Either<B, C>> left(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ac -> ac.either(a -> kleisli.apply(a).map(Either::left), c -> sequence(Either.right(c)));
    }

    static @Nonnull <A, B, C> Kleisli<Either<C, A>, Either<C, B>> right(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ca -> ca.either(c -> sequence(Either.left(c)), a -> kleisli.apply(a).map(Either::right));
    }

    static @Nonnull <A, B, C, D> Kleisli<Either<A, C>, Either<B, D>> sum(Kleisli<? super A, ? extends B> first, Kleisli<? super C, D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> ac.either(a -> first.apply(a).map(Either::left), c -> second.apply(c).map(Either::right));
    }

    static @Nonnull <A, B, C, D> Kleisli<Pair<A, C>, Pair<B, D>> product(Kleisli<? super A, ? extends B> first, Kleisli<? super C, D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> first.apply(ac.first()).zip(second.apply(ac.second()), Pair::pair);
    }

    @SuppressWarnings("unchecked")
    static @Nonnull <A, B, C> Kleisli<Either<A, C>, B> fanIn(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends B> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> (Sequence<B>) ac.either(first::apply, second::apply);
    }

    static @Nonnull <A, B, C> Kleisli<A, Pair<B, C>> fanOut(Kleisli<? super A, ? extends B> first, Kleisli<? super A, ? extends C> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).zip(second.apply(a), Pair::pair);
    }

    static @Nonnull <A, B, C> Kleisli<A, C> compose(Kleisli<? super A, ? extends B> first, Kleisli<? super B, ? extends C> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).flatMap(second);
    }

    static @Nonnull <A, B> Kleisli<A, B> pure(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return a -> sequence(function.apply(a));
    }
  }
}
