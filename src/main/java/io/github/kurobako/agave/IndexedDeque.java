package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.github.kurobako.agave.BiFunction.flip;

abstract class IndexedDeque<M extends Measured> {
  private IndexedDeque() {}

  abstract @Nonnull IndexedDeque<M> push(M m);

  abstract @Nonnull IndexedDeque<M> inject(M m);

  abstract @Nullable M first();

  abstract @Nullable M last();

  abstract @Nullable IndexedDeque<M> removeFirst();

  abstract @Nullable IndexedDeque<M> removeLast();

  abstract int splitAt(int idx, SplitPoint<M> pt);

  abstract @Nonnull <N extends Measured> IndexedDeque<N> transform(Function<? super M, ? extends N> transform);

  abstract @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial);

  abstract @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial);

  abstract int measure();

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  abstract boolean isEmpty();

  @SuppressWarnings("unchecked")
  static @Nonnull <M extends Measured> IndexedDeque<M> empty() {
    return (IndexedDeque<M>) Shallow.EMPTY;
  }

  static @Nonnull <M extends Measured> IndexedDeque<M> catenate(IndexedDeque<M> first, IndexedDeque<M> second) {
    if (first instanceof Deep) {
      if (second instanceof Deep) {
        final Deep<M> left = (Deep<M>) first;
        final Deep<M> right = (Deep<M>) second;
        final IndexedDeque<Node<M>> forcedLeftSub = left.forceSub();
        final IndexedDeque<Node<M>> forcedRightSub = right.forceSub();
        final int newSubMeasure = left.subMeasure + left.sfx.measure() + right.pfx.measure() + right.subMeasure;
        Value<IndexedDeque<Node<M>>> newSub = () -> {
          IndexedDeque<Node<M>> leftSub = forcedLeftSub;
          for (Node<M> node : makeNodes(elementsOf(left.sfx, right.pfx))) leftSub = leftSub.inject(node);
          return catenate(leftSub, forcedRightSub);
        };
        return new Deep<>(left.pfx, newSub, newSubMeasure, right.sfx);
      } else {
        assert second instanceof Shallow;
        return second.foldLeft(IndexedDeque::inject, first);
      }
    } else {
      assert first instanceof Shallow;
      return first.foldRight(flip(IndexedDeque::push), second);
    }
  }

  @SuppressWarnings("unchecked")
  private static @Nonnull <M extends Measured> M[] elementsOf(Affix<M> left, Affix<M> right) {
    final M[] result = (M[]) new Object[left.length() + right.length()];
    final int[] i = {0};
    left.foldLeft((r, m) -> { r[i[0]++] = m; return result; }, result);
    right.foldLeft((r, m) -> { r[i[0]++] = m; return result; }, result);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static @Nonnull <M extends Measured> Node<M>[] makeNodes(M[] m) {
    assert m.length >= 2;
    assert m.length < 7;
    switch (m.length) {
      case 2: return (Node<M>[]) new Object[]{ new Two<>(m[0], m[1]) };
      case 3: return (Node<M>[]) new Object[]{ new Three<>(m[0], m[1], m[2]) };
      case 4: return (Node<M>[]) new Object[]{ new Two<>(m[0], m[1]), new Two<>(m[2], m[3]) };
      case 5: return (Node<M>[]) new Object[]{ new Two<>(m[0], m[1]), new Three<>(m[2], m[3], m[4]) };
      case 6: return (Node<M>[]) new Object[]{ new Three<>(m[0], m[1], m[2]), new Three<>(m[3], m[4], m[5]) };
      default: {
        assert false;
        return null;
      }
    }
  }

  private static @Nonnull <M extends Measured> IndexedDeque<M> simplify(IndexedDeque<Node<M>> deque) {
    if (deque instanceof Deep) {
      final IndexedDeque<Node<M>> trimmed = ((Deep<Node<M>>)deque).trim();
      return new Deep<>(deque.first(), trimmed, trimmed.measure(), deque.last());
    } else return deque.foldLeft((r, node) -> node.foldLeft(IndexedDeque::inject, r), empty());
  }

  static final class Shallow<M extends Measured> extends IndexedDeque<M> {
    static final @Nonnull Shallow<Measured> EMPTY = new Shallow<>();

    final @Nullable M value;

    Shallow() {
      value = null;
    }

    Shallow(M value) {
      this.value = value;
    }

    @Override
    @Nonnull IndexedDeque<M> push(M m) {
      return value == null ? new Shallow<>(m) : new Deep<>(m, value);
    }

    @Override
    @Nonnull IndexedDeque<M> inject(M m) {
      return value == null ? new Shallow<>(m) : new Deep<>(value, m);
    }

    @Override
    @Nullable M first() {
      return value;
    }

    @Override
    @Nullable M last() {
      return value;
    }

    @Override
    @Nullable IndexedDeque<M> removeFirst() {
      return value == null ? null : empty();
    }

    @Override
    @Nullable IndexedDeque<M> removeLast() {
      return value == null ? null : empty();
    }

    @Override
    int splitAt(int idx, SplitPoint<M> pt) {
      if (value == null) return idx;
      if (idx < value.measure()) pt.point = value;
      return idx;
    }

    @Override
    @Nonnull <N extends Measured> IndexedDeque<N> transform(Function<? super M, ? extends N> transform) {
      return value == null ? empty() : new Shallow<>(transform.apply(value));
    }

    @Override
    @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial) {
      return value == null ? initial : function.apply(initial, value);
    }

    @Override
    @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial) {
      return value == null ? initial : function.apply(value, initial);
    }

    @Override
    int measure() {
      return value == null ? 0 : value.measure();
    }

    @Override
    boolean isEmpty() {
      return value == null;
    }

  }

  static final class Deep<M extends Measured> extends IndexedDeque<M> {
    final @Nonnull Affix<M> pfx;
    volatile @Nonnull Object sub;
    final @Nonnull Affix<M> sfx;

    final int subMeasure;

    Deep(Affix<M> pfx, Object sub, int subMeasure, Affix<M> sfx) {
      this.pfx = pfx;
      this.sub = sub;
      this.sfx = sfx;
      this.subMeasure = subMeasure;
    }

    Deep(M first, M second) {
      pfx = new One<>(first);
      sub = Shallow.EMPTY;
      sfx = new One<>(second);
      subMeasure = 0;
    }

    @SuppressWarnings("unchecked")
    @Nonnull IndexedDeque<Node<M>> forceSub() {
      boolean done = (sub instanceof IndexedDeque);
      if (!done) {
        synchronized (this) {
          done = (sub instanceof IndexedDeque);
          if (!done) {
            assert sub instanceof Value;
            sub = ((Value<IndexedDeque<Node<M>>>) sub).get();
          }
        }
      }
      return (IndexedDeque<Node<M>>) sub;
    }

    @Nonnull IndexedDeque<M> trim() {
      //noinspection ConstantConditions
      return removeFirst().removeLast();
    }

    @Override
    @Nonnull IndexedDeque<M> push(M m) {
      if (!(pfx instanceof Three)) return new Deep<>(pfx.push(m), sub, subMeasure, sfx);
      final IndexedDeque<Node<M>> forcedSub = forceSub();
      final Value<IndexedDeque<Node<M>>> newSub = () -> forcedSub.push((Three<M>) pfx);
      return new Deep<>(new One<>(m), newSub, subMeasure + pfx.measure(), sfx);
    }

    @Override
    @Nonnull IndexedDeque<M> inject(M m) {
      if (!(sfx instanceof Three)) return new Deep<>(pfx, sub, subMeasure, sfx.inject(m));
      final IndexedDeque<Node<M>> forcedSub = forceSub();
      final Value<IndexedDeque<Node<M>>> newSub = () -> forcedSub.inject((Three<M>) sfx);
      return new Deep<>(pfx, newSub, subMeasure + sfx.measure(), new One<>(m));
    }

    @Override
    @Nonnull M first() {
      return pfx.head();
    }

    @Override
    @Nonnull M last() {
      return sfx.last();
    }

    @Override
    @Nonnull IndexedDeque<M> removeFirst() {
      if (pfx instanceof Node) {
        final Affix<M> newPfx = pfx.tail();
        assert newPfx != null;
        return new Deep<>(newPfx, sub, subMeasure, sfx);
      } else if (subMeasure > 0) {
        final IndexedDeque<Node<M>> forcedSub = forceSub();
        final Node<M> first = forcedSub.first();
        assert first != null;
        @SuppressWarnings("NullableProblems") final Value<IndexedDeque<Node<M>>> newSub = forcedSub::removeFirst;
        return new Deep<>(first, newSub, subMeasure - first.measure(), sfx);
      } else {
        final Affix<M> sfxTail = sfx.tail();
        return sfxTail == null ? new Shallow<>(sfx.head()) : new Deep<>(new One<>(sfx.head()), Shallow.EMPTY, 0, sfxTail);
      }
    }

    @Override
    @Nonnull IndexedDeque<M> removeLast() {
      if (sfx instanceof Node) {
        final Affix<M> newSfx = sfx.init();
        assert newSfx != null;
        return new Deep<>(pfx, sub, subMeasure, newSfx);
      } else if (subMeasure > 0) {
        final IndexedDeque<Node<M>> forcedSub = forceSub();
        final Node<M> last = forcedSub.last();
        assert last != null;
        @SuppressWarnings("NullableProblems") final Value<IndexedDeque<Node<M>>> newSub = forcedSub::removeLast;
        return new Deep<>(pfx, newSub, subMeasure - last.measure(), last);
      } else {
        final Affix<M> pfxInit = pfx.init();
        return pfxInit == null ? new Shallow<>(pfx.last()) : new Deep<>(pfxInit, Shallow.EMPTY, 0, new One<>(pfx.last()));
      }
    }

    @Override
    int splitAt(int idx, SplitPoint<M> pt) {
      final int pfxMeasure = pfx.measure();
      if (idx < pfxMeasure) {
        if (pt.right != null) pt.right = catenate(simplify(forceSub()), sfx.foldRight(flip(IndexedDeque::push), pt.right));
        return pfx.split(idx, pt);
      }
      idx -= pfxMeasure;
      if (idx < subMeasure) {
        if (pt.left != null) pt.left = pfx.foldLeft(IndexedDeque::inject, pt.left);
        if (pt.right != null) pt.right = sfx.foldRight(flip(IndexedDeque::push), pt.right);
        SplitPoint<Node<M>> deeperPt = new SplitPoint<>(pt.left != null, pt.right != null);
        idx = forceSub().splitAt(idx, deeperPt);
        if (pt.left != null) {
          assert deeperPt.left != null;
          pt.left = catenate(pt.left, simplify(deeperPt.left));
        }
        if (pt.right != null) {
          assert deeperPt.right != null;
          pt.right = catenate(simplify(deeperPt.right), pt.right);
        }
        return deeperPt.point != null ? deeperPt.point.split(idx, pt) : idx;
      }
      idx -= subMeasure;
      final int sfxMeasure = sfx.measure();
      if (idx < sfxMeasure) {
        if (pt.left != null) pt.left = catenate(pfx.foldLeft(IndexedDeque::inject, pt.left), simplify(forceSub()));
        return sfx.split(idx, pt);
      }
      idx -= sfxMeasure;
      return idx;
    }

    @Override
    @Nonnull <N extends Measured> IndexedDeque<N> transform(Function<? super M, ? extends N> function) {
      final IndexedDeque<Node<M>> forcedSub = forceSub();
      final Value<IndexedDeque<Node<N>>> newSub = () -> forcedSub.transform(node -> node.transform(function));
      return new Deep<>(pfx.transform(function), newSub, subMeasure, sfx.transform(function));
    }

    @Override
    @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial) {
      N result = initial;
      result = pfx.foldLeft(function, result);
      result = forceSub().foldLeft((n, node) -> node.foldLeft(function, n), result);
      result = sfx.foldLeft(function, result);
      return result;
    }

    @Override
    @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial) {
      N result = initial;
      result = sfx.foldRight(function, result);
      result = forceSub().foldRight((node, n) -> node.foldRight(function, n), result);
      result = pfx.foldRight(function, result);
      return result;
    }

    @Override
    int measure() {
      return pfx.measure() + subMeasure + sfx.measure();
    }

    @Override
    boolean isEmpty() {
      return false;
    }

  }

  private static abstract class Affix<M extends Measured> implements Measured {

    abstract @Nonnull Node<M> push(M m);

    abstract @Nonnull Node<M> inject(M m);

    abstract @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial);

    abstract @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial);

    abstract int split(int idx, SplitPoint<M> pt);

    abstract @Nonnull <N extends Measured> Affix<N> transform(Function<? super M, ? extends N> function);

    abstract @Nonnull M head();

    abstract @Nonnull M last();

    abstract @Nullable Affix<M> tail();

    abstract @Nullable Affix<M> init();

    abstract int length();
  }

  private static final class One<M extends Measured> extends Affix<M> {
    final @Nonnull M sole;

    One(M first) {
      this.sole = first;
    }

    @Override
    @Nonnull Node<M> push(M m) {
      return new Two<>(m, sole);
    }

    @Override
    @Nonnull Node<M> inject(M m) {
      return new Two<>(sole, m);
    }

    @Override
    @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial) {
      return function.apply(initial, sole);
    }

    @Override
    @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial) {
      return function.apply(sole, initial);
    }

    @Override
    int split(int idx, SplitPoint<M> pt) {
      final int firstMeasure = sole.measure();
      if (idx < firstMeasure) {
        pt.point = sole;
        return idx;
      }
      idx -= firstMeasure;
      return idx;
    }

    @Override
    @Nonnull <N extends Measured> Affix<N> transform(Function<? super M, ? extends N> function) {
      return new One<>(function.apply(sole));
    }

    @Override
    @Nonnull M head() {
      return sole;
    }

    @Override
    @Nonnull M last() {
      return sole;
    }

    @Override
    @Nullable Affix<M> tail() {
      return null;
    }

    @Override
    @Nullable Affix<M> init() {
      return null;
    }

    @Override
    int length() {
      return 1;
    }

    @Override
    public int measure() {
      return sole.measure();
    }
  }

  private static abstract class Node<M extends Measured> extends Affix<M> {
    @Override
    abstract @Nonnull <N extends Measured> Node<N> transform(Function<? super M, ? extends N> function);
  }

  private static final class Two<M extends Measured> extends Node<M> {
    final @Nonnull M first;
    final @Nonnull M second;

    final int firstMeasure;
    final int secondMeasure;

    Two(M first, M second) {
      this(first, first.measure(), second, second.measure());
    }

    Two(M first, int firstMeasure, M second, int secondMeasure) {
      assert firstMeasure == first.measure();
      assert secondMeasure == second.measure();
      this.first = first;
      this.second = second;
      this.firstMeasure = firstMeasure;
      this.secondMeasure = secondMeasure;
    }

    @Override
    @Nonnull Node<M> push(M m) {
      return new Three<>(m, m.measure(), first, firstMeasure, second, secondMeasure);
    }

    @Override
    @Nonnull Node<M> inject(M m) {
      return new Three<>(first, firstMeasure, second, secondMeasure, m, m.measure());
    }

    @Override
    @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial) {
      N result = initial;
      result = function.apply(result, first);
      result = function.apply(result, second);
      return result;
    }

    @Override
    @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial) {
      N result = initial;
      result = function.apply(second, result);
      result = function.apply(first, result);
      return result;
    }

    @Override
    int split(int idx, SplitPoint<M> pt) {
      if (idx < firstMeasure) {
        pt.point = first;
        if (pt.right != null) pt.right = pt.right.push(second);
        return idx;
      }
      idx -= firstMeasure;
      if (idx < secondMeasure) {
        pt.point = second;
        if (pt.left != null) pt.left = pt.left.inject(first);
        return idx;
      }
      idx -= secondMeasure;
      return idx;
    }

    @Override
    @Nonnull <N extends Measured> Two<N> transform(Function<? super M, ? extends N> function) {
      return new Two<>(function.apply(first), firstMeasure, function.apply(second), secondMeasure);
    }

    @Override
    @Nonnull M head() {
      return first;
    }

    @Override
    @Nonnull M last() {
      return second;
    }

    @Override
    @Nonnull Affix<M> tail() {
      return new One<>(second);
    }

    @Override
    @Nonnull Affix<M> init() {
      return new One<>(first);
    }

    @Override
    int length() {
      return 2;
    }

    @Override
    public int measure() {
      return firstMeasure + secondMeasure;
    }
  }

  private static final class Three<M extends Measured> extends Node<M> {
    final @Nonnull M first;
    final @Nonnull M second;
    final @Nonnull M third;

    final int firstMeasure;
    final int secondMeasure;
    final int thirdMeasure;

    Three(M first, M second, M third) {
      this(first, first.measure(), second, second.measure(), third, third.measure());
    }

    Three(M first, int firstMeasure, M second, int secondMeasure, M third, int thirdMeasure) {
      assert firstMeasure == first.measure();
      assert secondMeasure == second.measure();
      assert thirdMeasure == third.measure();
      this.first = first;
      this.second = second;
      this.third = third;
      this.firstMeasure = firstMeasure;
      this.secondMeasure = secondMeasure;
      this.thirdMeasure = thirdMeasure;
    }

    @Override
    @Nonnull Node<M> push(M m) {
      assert false;
      return null;
    }

    @Override
    @Nonnull Node<M> inject(M m) {
      assert false;
      return null;
    }

    @Override
    @Nonnull <N> N foldLeft(BiFunction<? super N, ? super M, ? extends N> function, N initial) {
      N result = initial;
      result = function.apply(result, first);
      result = function.apply(result, second);
      result = function.apply(result, third);
      return result;
    }

    @Override
    @Nonnull <N> N foldRight(BiFunction<? super M, ? super N, ? extends N> function, N initial) {
      N result = initial;
      result = function.apply(third, result);
      result = function.apply(second, result);
      result = function.apply(first, result);
      return result;
    }

    @Override
    int split(int idx, SplitPoint<M> pt) {
      if (idx < firstMeasure) {
        pt.point = first;
        if (pt.right != null) pt.right = pt.right.push(third).push(second);
        return idx;
      }
      idx -= firstMeasure;
      if (idx < secondMeasure) {
        pt.point = second;
        if (pt.left != null) pt.left = pt.left.inject(first);
        if (pt.right != null) pt.right = pt.right.push(third);
        return idx;
      }
      idx -= secondMeasure;
      if (idx < thirdMeasure) {
        pt.point = third;
        if (pt.left != null) pt.left = pt.left.inject(first).inject(second);
        return idx;
      }
      idx -= thirdMeasure;
      return idx;
    }

    @Override
    @Nonnull <N extends Measured> Three<N> transform(Function<? super M, ? extends N> function) {
      return new Three<>(function.apply(first), firstMeasure, function.apply(second), secondMeasure, function.apply(third), thirdMeasure);
    }

    @Override
    @Nonnull M head() {
      return first;
    }

    @Override
    @Nonnull M last() {
      return third;
    }

    @Override
    @Nonnull Affix<M> tail() {
      return new Two<>(second, secondMeasure, third, thirdMeasure);
    }

    @Override
    @Nonnull Two<M> init() {
      return new Two<>(first, firstMeasure, second, secondMeasure);
    }

    @Override
    int length() {
      return 3;
    }

    @Override
    public int measure() {
      return firstMeasure + secondMeasure + thirdMeasure;
    }
  }

  static final class SplitPoint<M extends Measured> {

    @Nullable IndexedDeque<M> left;
    @Nullable M point;
    @Nullable IndexedDeque<M> right;

    SplitPoint(boolean trackLeft, boolean trackRight) {
      if (trackLeft) left = IndexedDeque.empty();
      if (trackRight) right = IndexedDeque.empty();
    }

  }

}
