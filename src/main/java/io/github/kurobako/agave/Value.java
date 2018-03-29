package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Sequence.sequence;

@FunctionalInterface
public interface Value<A> {

  @Nonnull A get();

  default @Nonnull <B> Value<B> map(Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final A a = get();
    return () -> function.apply(a);
  }

  default @Nonnull <B> Value<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
    //noinspection ConstantConditions
    if (kleisli == null) throw new NullPointerException("kleisli");
    final A a = get();
    return () -> kleisli.apply(a).get();
  }

  static @Nonnull <A> Value<A> value(A value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return () -> value;
  }

  @SuppressWarnings("unchecked")
  static @Nonnull <A> Value<A> flatten(Value<? extends Value<? extends A>> value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return value.flatMap(a -> (Value<A>) a);
  }

  static @Nonnull <A> Value<Sequence<A>> traverse(Sequence<? extends Value<A>> sequence) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    return sequence.foldRight((v, result) -> result.map(seq -> seq.push(v.get())), value(sequence()));
  }

  @FunctionalInterface
  interface Kleisli<A, B> {
    @Nonnull Value<B> apply(A arg);

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
      return ac -> ac.either(a -> kleisli.apply(a).map(Either::left), c -> value(Either.right(c)));
    }

    static @Nonnull <A, B, C> Kleisli<Either<C, A>, Either<C, B>> right(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ca -> ca.either(c -> value(Either.left(c)), a -> kleisli.apply(a).map(Either::right));
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
      return ac -> first.apply(ac.first()).flatMap(b -> second.apply(ac.second()).flatMap(d -> value(pair(b, d))));
    }

    @SuppressWarnings("unchecked")
    static @Nonnull <A, B, C> Kleisli<Either<A, C>, B> fanIn(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends B> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> (Value<B>) ac.either(first::apply, second::apply);
    }

    static @Nonnull <A, B, C> Kleisli<A, Pair<B, C>> fanOut(Kleisli<? super A, ? extends B> first, Kleisli<? super A, ? extends C> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).flatMap(b -> second.apply(a).flatMap(c -> value(pair(b, c))));
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
      return a -> value(function.apply(a));
    }
  }
  
}
