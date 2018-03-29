package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Sequence.sequence;

@Immutable
public abstract class Option<A> implements Foldable<A>, Iterable<A> {
  private Option() {}

  public abstract @Nonnull <B> Option<B> map(Function<? super A, ? extends B> function);

  public abstract @Nonnull <B> Option<B> flatMap(Kleisli<? super A, ? extends B> kleisli);

  public abstract @Nonnull <B, C> Option<C> zip(Option<B> option, BiFunction<? super A, ? super B, ? extends C> function);

  public final @Nonnull <B> Option<Pair<A, B>> zip(Option<B> option) {
    //noinspection ConstantConditions
    if (option == null) throw new NullPointerException("option");
    return zip(option, Pair::pair);
  }

  public abstract @Nonnull Option<A> filter(Predicate<? super A> predicate);

  public abstract @Nonnull <B> B option(Function<? super A, ? extends B> ifSome, Value<B> ifNone);

  public abstract @Nullable A asNullable();

  public abstract boolean isEmpty();

  public static @Nonnull <A> Option<A> fromNullable(@Nullable A value) {
    return value == null ? none() : new Some<>(value);
  }

  public static @Nonnull <A> Option<A> some(A value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Some<>(value);
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <A> Option<A> none() {
    return (Option<A>) None.INSTANCE;
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <A> Option<A> flatten(Option<Option<? extends A>> option) {
    //noinspection ConstantConditions
    if (option == null) throw new NullPointerException();
    return option.flatMap(a -> (Option<A>) a);
  }

  public static @Nonnull <A> Option<Sequence<A>> traverse(Sequence<Option<A>> sequence) {
    //noinspection ConstantConditions
    if (sequence == null) throw new NullPointerException("sequence");
    return sequence.foldRight((v, result) -> result.zip(v, Sequence::push), some(sequence()));
  }

  private static final class None<A> extends Option<A> {
    static final @Nonnull None<Object> INSTANCE = new None<>();

    @Override
    public @Nonnull <B> Option<B> map(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return none();
    }

    @Override
    public @Nonnull <B> Option<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return none();
    }

    @Override
    public @Nonnull <B, C> Option<C> zip(Option<B> option, BiFunction<? super A, ? super B, ? extends C> function) {
      //noinspection ConstantConditions
      if (option == null) throw new NullPointerException("option");
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return none();
    }

    @Override
    public @Nonnull Option<A> filter(Predicate<? super A> predicate) {
      //noinspection ConstantConditions
      if (predicate == null) throw new NullPointerException("predicate");
      return none();
    }

    @Override
    public @Nonnull<B> B option(Function<? super A, ? extends B> ifSome, Value<B> ifNone) {
      //noinspection ConstantConditions
      if (ifSome == null) throw new NullPointerException("ifSome");
      //noinspection ConstantConditions
      if (ifNone == null) throw new NullPointerException("ifNone");
      return ifNone.get();
    }

    @Override
    public @Nullable A asNullable() {
      return null;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return initial;
    }

    @Override
    public @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return initial;
    }

    @Override
    public @Nonnull Iterator<A> iterator() {
      return Collections.emptyIterator();
    }


    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public String toString() {
      return "()";
    }
  }

  private static final class Some<A> extends Option<A> {
    final @Nonnull A value;

    private Some(A value) {
      this.value = value;
    }

    @Override
    public @Nonnull <B> Option<B> map(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return new Some<>(function.apply(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull <B> Option<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return (Option<B>) kleisli.apply(value);
    }

    @Override
    public @Nonnull <B, C> Option<C> zip(Option<B> option, BiFunction<? super A, ? super B, ? extends C> function) {
      //noinspection ConstantConditions
      if (option == null) throw new NullPointerException("option");
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return option.map(b -> function.apply(value, b));
    }

    @Override
    public @Nonnull Option<A> filter(Predicate<? super A> predicate) {
      //noinspection ConstantConditions
      if (predicate == null) throw new NullPointerException("predicate");
      return predicate.test(value) ? this : none();
    }

    @Override
    public @Nonnull<B> B option(Function<? super A, ? extends B> ifSome, Value<B> ifNone) {
      //noinspection ConstantConditions
      if (ifSome == null) throw new NullPointerException("ifSome");
      //noinspection ConstantConditions
      if (ifNone == null) throw new NullPointerException("ifNone");
      return ifSome.apply(value);
    }

    @Override
    public @Nonnull A asNullable() {
      return value;
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
      return function.apply(initial, value);
    }

    @Override
    public @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      //noinspection ConstantConditions
      if (initial == null) throw new NullPointerException("initial");
      return function.apply(value, initial);
    }

    @Override
    public @Nonnull Iterator<A> iterator() {
      return new Iterator<A>() {
        boolean consumed;

        @Override
        public boolean hasNext() {
          return !consumed;
        }

        @Override
        public A next() {
          if (consumed) throw new NoSuchElementException();
          consumed = true;
          return value;
        }
      };
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (!(o instanceof Some)) return false;
      Some that = (Some) o;
      return this.value.equals(that.value);
    }

    @Override
    public @Nonnull String toString() {
      return "(" + value.toString() + ")";
    }
  }

  @FunctionalInterface
  interface Kleisli<A, B> {
    @Nonnull Option<B> apply(A arg);

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
      return ac -> ac.either(a -> kleisli.apply(a).map(Either::left), c -> some(Either.right(c)));
    }

    static @Nonnull <A, B, C> Kleisli<Either<C, A>, Either<C, B>> right(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ca -> ca.either(c -> some(Either.left(c)), a -> kleisli.apply(a).map(Either::right));
    }

    static @Nonnull <A, B, C, D> Kleisli<Either<A, C>, Either<B, D>> sum(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> ac.either(a -> first.apply(a).map(Either::left), c -> second.apply(c).map(Either::right));
    }

    static @Nonnull <A, B, C, D> Kleisli<Pair<A, C>, Pair<B, D>> product(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends D> second) {
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
      return ac -> (Option<B>) ac.either(first::apply, second::apply);
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
      return a -> some(function.apply(a));
    }
  }

}
