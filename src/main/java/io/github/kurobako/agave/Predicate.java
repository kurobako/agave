package io.github.kurobako.agave;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface Predicate<A> extends Function<A, Boolean> {

  boolean test(A arg);

  @Override
  default @Nonnull Boolean apply(A arg) {
    return test(arg);
  }

  static @Nonnull <A> Predicate<A> TRUE() {
    return any -> true;
  }

  static @Nonnull <A> Predicate<A> FALSE() {
    return any -> false;
  }

  static @Nonnull <A> Predicate<A> not(Predicate<A> predicate) {
    //noinspection ConstantConditions
    if (predicate == null) throw new NullPointerException("predicate");
    return arg -> !predicate.test(arg);
  }

  static @Nonnull <A> Predicate<A> and(Predicate<A> first, Predicate<A> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return arg -> first.test(arg) && second.test(arg);
  }

  static @Nonnull <A> Predicate<A> or(Predicate<A> first, Predicate<A> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return arg -> first.test(arg) || second.test(arg);
  }

  static @Nonnull <A> Predicate<A> xor(Predicate<A> first, Predicate<A> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return arg -> first.test(arg) ^ second.test(arg);
  }
}
