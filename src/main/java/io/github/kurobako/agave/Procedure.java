package io.github.kurobako.agave;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface Procedure<A> {

  void run(A arg);

  static @Nonnull <A> Procedure<A> sequence(Procedure<A> first, Procedure<A> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return arg -> { first.run(arg); second.run(arg); };
  }
}
