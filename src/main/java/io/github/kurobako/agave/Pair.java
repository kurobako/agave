package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

@Immutable
public final class Pair<A, B> {
  private final @Nonnull A first;
  private final @Nonnull B second;

  Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  public @Nonnull A first() {
    return first;
  }

  public @Nonnull B second() {
    return second;
  }

  public @Nonnull Pair<B, A> swap() {
    return new Pair<>(second, first);
  }

  public @Nonnull <C, D> Pair<C, D> biMap(Function<? super A, ? extends C> mapFirst, Function<? super B, ? extends D> mapSecond) {
    //noinspection ConstantConditions
    if (mapFirst == null) throw new NullPointerException("mapFirst");
    //noinspection ConstantConditions
    if (mapSecond == null) throw new NullPointerException("mapSecond");
    return new Pair<>(mapFirst.apply(first), mapSecond.apply(second));
  }

  public @Nonnull <C> C squeeze(BiFunction<? super A, ? super B, ? extends C> transform) {
    //noinspection ConstantConditions
    if (transform == null) throw new NullPointerException();
    return transform.apply(this.first, this.second);
  }

  @Override
  public int hashCode() {
    final int l = first.hashCode();
    final int r = second.hashCode();
    return l ^ (((r & 0xFFFF) << 16) | (r >> 16));
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof Pair)) return false;
    final Pair that = (Pair) o;
    return this.first.equals(that.first) && this.second.equals(that.second);
  }

  @Override
  public @Nonnull String toString() {
    return "(" + first + ", " + second + ")";
  }

  public static @Nonnull <A, B> Pair<A, B> pair(A first, B second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Pair<>(first, second);
  }

}
