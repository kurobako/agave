/*
 * Copyright (C) 2018 Fedor Gavrilov
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import static io.github.kurobako.agave.BiFunction.curry;
import static io.github.kurobako.agave.Option.none;
import static io.github.kurobako.agave.Option.some;
import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Sequence.sequence;

@Immutable
public abstract class Either<L, R> implements Foldable<R> {
  private Either() {}

  public abstract @Nonnull <X> X either(Function<? super L, ? extends X> ifLeft, Function<? super R, ? extends X> ifRight);

  public abstract @Nonnull Either<R, L> swap();

  public abstract @Nonnull <X> Either<L, X> map(Function<? super R, ? extends X> function);

  public abstract @Nonnull <X> Either<L, X> flatMap(Kleisli<? super R, L, ? extends X> kleisli);

  public abstract @Nonnull <X, Y> Either<L, Y> zip(Either<L, X> either, BiFunction<? super R, ? super X, ? extends Y> function);

  public abstract @Nonnull <X, Y> Either<X, Y> biMap(Function<? super L, ? extends X> leftFunction, Function<? super R, ? extends Y> rightFunction);

  public abstract boolean isLeft();

  public abstract boolean isRight();

  public abstract @Nonnull Option<L> asLeft();

  public abstract @Nonnull Option<R> asRight();

  public static @Nonnull <L, R> Either<L, R> left(final L value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Left<>(value);
  }

  public static @Nonnull <L, R> Either<L, R> right(final R value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Right<>(value);
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <L, R> Either<L, R> flatten(Either<L, Either<L, ? extends R>> either) {
    //noinspection ConstantConditions
    if (either == null) throw new NullPointerException("either");
    return either.flatMap(a -> (Either<L, R>) a);
  }

  public static @Nonnull <L, R> Either<L, Sequence<R>> traverse(Sequence<Either<L, R>> sequence) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    return sequence.foldRight((v, result) -> result.zip(v, Sequence::push), right(sequence()));
  }

  private static final class Left<L, R> extends Either<L, R> {
    public final @Nonnull L value;

    private Left(final L left) {
      this.value = left;
    }

    @Override
    public @Nonnull <X> X either(Function<? super L, ? extends X> ifLeft, Function<? super R, ? extends X> ifRight) {
      //noinspection ConstantConditions
      if (ifLeft == null) throw new NullPointerException("ifLeft");
      //noinspection ConstantConditions
      if (ifRight == null) throw new NullPointerException("ifRight");
      return ifLeft.apply(value);
    }

    @Override
    public @Nonnull Either<R, L> swap() {
      return right(value);
    }

    @Override
    public @Nonnull <X> Either<L, X> flatMap(Kleisli<? super R, L, ? extends X> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return self();
    }

    @Override
    public @Nonnull <X, Y> Left<L, Y> zip(Either<L, X> either, BiFunction<? super R, ? super X, ? extends Y> function) {
      //noinspection ConstantConditions
      if (either == null) throw new NullPointerException("either");
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return self();
    }


    @Override
    public @Nonnull <X> Left<L, X> map(Function<? super R, ? extends X> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return self();
    }

    @SuppressWarnings("unchecked")
    private <X> Left<L, X> self() {
      return (Left<L, X>) this;
    }

    @Override
    public @Nonnull <X, Y> Either<X, Y> biMap(Function<? super L, ? extends X> mapLeft, @Nonnull Function<? super R, ? extends Y> mapRight) {
      //noinspection ConstantConditions
      if (mapLeft == null) throw new NullPointerException("mapLeft");
      //noinspection ConstantConditions
      if (mapRight == null) throw new NullPointerException("mapRight");
      return left(mapLeft.apply(value));
    }

    @Override
    public boolean isLeft() {
      return true;
    }

    public boolean isRight() {
      return false;
    }

    @Override
    public @Nonnull Option<L> asLeft() {
      return some(value);
    }

    @Override
    public @Nonnull Option<R> asRight() {
      return none();
    }

    @Override
    public @Nonnull <B> B foldRight(BiFunction<? super R, ? super B, ? extends B> function, final B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return initial;
    }

    @Override
    public @Nonnull
    <B> B foldLeft(BiFunction<? super B, ? super R, ? extends B> function, final B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return initial;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (!(o instanceof Left)) return false;
      final Left that = (Left) o;
      return this.value.equals(that.value);
    }

    @Override
    public @Nonnull
    String toString() {
      return "Left: " + String.valueOf(value);
    }
  }

  private static final class Right<L, R> extends Either<L, R> {
    public final @Nonnull R value;

    private Right(final R right) {
      this.value = right;
    }

    @Override
    public @Nonnull <X> X either(Function<? super L, ? extends X> ifLeft, Function<? super R, ? extends X> ifRight) {
      //noinspection ConstantConditions
      if (ifLeft == null) throw new NullPointerException("ifLeft");
      //noinspection ConstantConditions
      if (ifRight == null) throw new NullPointerException("ifRight");
      return ifRight.apply(value);
    }

    @Override
    public @Nonnull Either<R, L> swap() {
      return left(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull <X> Either<L, X> flatMap(Kleisli<? super R, L, ? extends X> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return (Either<L, X>) kleisli.apply(value);
    }

    @Override
    public @Nonnull <X, Y> Either<L, Y> zip(Either<L, X> either, BiFunction<? super R, ? super X, ? extends Y> function) {
      //noinspection ConstantConditions
      if (either == null) throw new NullPointerException("either");
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return either.map(curry(function).apply(value));
    }

    @Override
    public @Nonnull <X> Either<L, X> map(Function<? super R, ? extends X> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return right(function.apply(value));
    }

    @Override
    public @Nonnull <X, Y> Either<X, Y> biMap(Function<? super L, ? extends X> mapLeft, Function<? super R, ? extends Y> mapRight) {
      //noinspection ConstantConditions
      if (mapLeft == null) throw new NullPointerException("mapLeft");
      //noinspection ConstantConditions
      if (mapRight == null) throw new NullPointerException("mapRight");
      return right(mapRight.apply(value));
    }

    @Override
    public boolean isLeft() {
      return false;
    }

    public boolean isRight() {
      return true;
    }

    @Override
    public @Nonnull Option<L> asLeft() {
      return none();
    }

    @Override
    public @Nonnull Option<R> asRight() {
      return some(value);
    }

    @Override
    public @Nonnull <B> B foldRight(BiFunction<? super R, ? super B, ? extends B> function, final B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return function.apply(value, initial);
    }

    @Override
    public @Nonnull <B> B foldLeft(BiFunction<? super B, ? super R, ? extends B> function, final B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return function.apply(initial, value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (!(o instanceof Right)) return false;
      final Right that = (Right) o;
      return this.value.equals(that.value);
    }

    @Override
    public @Nonnull
    String toString() {
      return "Right: " + String.valueOf(value);
    }
  }

  @FunctionalInterface
  interface Kleisli<A, L, R> {
    @Nonnull Either<L, R> apply(A arg);

    static @Nonnull <A, L, R, C> Kleisli<Pair<A, C>, L, Pair<R, C>> first(Kleisli<? super A, L, ? extends R> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ac -> kleisli.apply(ac.first()).map(b -> pair(b, ac.second()));
    }

    static @Nonnull <Z, A, L, R> Kleisli<Pair<Z, A>, L, Pair<Z, R>> second(Kleisli<? super A, L, ? extends R> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return za -> kleisli.apply(za.second()).map(b -> pair(za.first(), b));
    }

    static @Nonnull <A, L, R, C> Kleisli<Either<A, C>, L, Either<R, C>> left(Kleisli<? super A, L, ? extends R> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ac -> ac.either(a -> kleisli.apply(a).map(Either::left), c -> Either.right(Either.right(c)));
    }

    static @Nonnull <A, L, R, C> Kleisli<Either<C, A>, L, Either<C, R>> right(Kleisli<? super A, L, ? extends R> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ca -> ca.either(c -> Either.right(Either.left(c)), a -> kleisli.apply(a).map(Either::right));
    }

    static @Nonnull <A, L, R, C, D> Kleisli<Either<A, C>, L, Either<R, D>> sum(Kleisli<? super A, L, ? extends R> first, Kleisli<? super C, L, ? extends D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> ac.either(a -> first.apply(a).map(Either::left), c -> second.apply(c).map(Either::right));
    }

    static @Nonnull <A, L, R, C, D> Kleisli<Pair<A, C>, L, Pair<R, D>> product(Kleisli<? super A, L, ? extends R> first, Kleisli<? super C, L, ? extends D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> first.apply(ac.first()).zip(second.apply(ac.second()), Pair::pair);
    }

    @SuppressWarnings("unchecked")
    static @Nonnull <A, L, R, C> Kleisli<Either<A, C>, L, R> fanIn(Kleisli<? super A, L, ? extends R> first, Kleisli<? super C, L, ? extends R> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> (Either<L, R>) ac.either(first::apply, second::apply);
    }

    static @Nonnull <A, L, R, C> Kleisli<A, L, Pair<R, C>> fanOut(Kleisli<? super A, L, ? extends R> first, Kleisli<? super A, L, ? extends C> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).zip(second.apply(a), Pair::pair);
    }

    static @Nonnull <A, L, R, X> Kleisli<A, L, X> compose(Kleisli<? super A, L, ? extends R> first, Kleisli<? super R, L, ? extends X> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).flatMap(second);
    }

    static @Nonnull <A, L, R> Kleisli<A, L, R> pure(Function<? super A, ? extends R> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return a -> Either.right(function.apply(a));
    }
  }

}
