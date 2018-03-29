package io.github.kurobako.agave;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface BiFunction<A, B, C> extends Function<Pair<A, B>, C> {

  @Nonnull C apply(A fst, B snd);

  @Override
  default @Nonnull C apply(final Pair<A, B> arg) {
    //noinspection ConstantConditions
    if (arg == null) throw new NullPointerException("arg");
    return apply(arg.first(), arg.second());
  }

  static @Nonnull <A, B, C> BiFunction<B, A, C> flip(BiFunction<? super A, ? super B, ? extends C> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return (b, a) -> function.apply(a, b);
  }

  static @Nonnull <A, B, C> Function<A, Function<B, C>> curry(BiFunction<? super A, ? super B, ? extends C> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return a -> b -> function.apply(a, b);
  }

  static @Nonnull <A, B, C> BiFunction<A, B, C> uncurry(Function<? super A, ? extends Function<? super B, ? extends C>> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return (a, b) -> function.apply(a).apply(b);
  }

}
