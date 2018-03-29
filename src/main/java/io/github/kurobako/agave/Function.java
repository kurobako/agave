package io.github.kurobako.agave;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface Function<A, B> {

  @Nonnull B apply(A arg);

  static @Nonnull <A, B, C> Function<A, C> compose(Function<? super A, ? extends B> first, Function<? super B, ? extends C> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return a -> second.apply(first.apply(a));
  }

}
