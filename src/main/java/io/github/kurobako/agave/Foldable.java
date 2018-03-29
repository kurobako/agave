package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import static io.github.kurobako.agave.BiFunction.flip;
import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Reducer.stateless;

public interface Foldable<A> {

  @Nonnull <B> B foldLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial);

  @Nonnull <B> B foldRight(BiFunction<? super A, ? super B, ? extends B> function, B initial);

  default @Nonnull <B> Sequence<B> scanLeft(BiFunction<? super B, ? super A, ? extends B> function, B initial) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return foldLeft((pair, a) -> {
      final B b = function.apply(pair.first(), a);
      return pair(b, pair.second().inject(b));
    }, pair(initial, Sequence.<B>sequence())).second();
  }

  default @Nonnull <B> Sequence<B> scanRight(BiFunction<? super A, ? super B, ? extends B> function, B initial) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return foldRight((a, pair) -> {
      final B b = function.apply(a, pair.first());
      return pair(b, pair.second().inject(b));
    }, pair(initial, Sequence.<B>sequence())).second();
  }

  @SuppressWarnings("unchecked")
  default @Nonnull <B, S> B reduceLeft(Reducer<? super A, B, S> reducer, B initial) {
    //noinspection ConstantConditions
    if (reducer == null) throw new NullPointerException("reducer");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    final S[] state = (S[]) new Object[]{ reducer.init() };
    final B[] result = (B[]) new Object[]{ initial };
    try {
      forEachLeft(value -> {
        final Pair<S, B> sb = reducer.step(state[0], result[0], value);
        state[0] = sb.first();
        result[0] = sb.second();
      });
    } catch (Reducer.Done ignored) { }
    return reducer.complete(state[0], result[0]);
  }

  @SuppressWarnings("unchecked")
  default @Nonnull <B, S> B reduceRight(Reducer<? super A, B, S> reducer, B initial) {
    //noinspection ConstantConditions
    if (reducer == null) throw new NullPointerException("reducer");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    final S[] state = (S[]) new Object[]{ reducer.init() };
    final B[] result = (B[]) new Object[]{ initial };
    try {
      forEachRight(value -> {
        final Pair<S, B> sb = reducer.step(state[0], result[0], value);
        state[0] = sb.first();
        result[0] = sb.second();
      });
    } catch (Reducer.Done ignored) { }
    return reducer.complete(state[0], result[0]);
  }

  default @Nonnull <B, C, D, S> B transduceLeft(Function<Reducer<C, D, Unit>, Reducer<A, B, S>> transducer, BiFunction<? super D, ? super C, ? extends D> function, B initial) {
    //noinspection ConstantConditions
    if (transducer == null) throw new NullPointerException("transducer");
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return reduceLeft(transducer.apply(stateless(function)), initial);
  }

  default @Nonnull <B, C, D, S> B transduceRight(Function<Reducer<C, D, Unit>, Reducer<A, B, S>> transducer, BiFunction<? super C, ? super D, ? extends D> function, B initial) {
    //noinspection ConstantConditions
    if (transducer == null) throw new NullPointerException("transducer");
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return reduceRight(transducer.apply(stateless(flip(function))), initial);
  }

  default void forEachLeft(Procedure<? super A> action) {
    //noinspection ConstantConditions
    if (action == null) throw new NullPointerException("action");
    foldLeft((unit, a) -> { action.run(a); return unit; }, Unit.INSTANCE);
  }

  default void forEachRight(Procedure<? super A> action) {
    //noinspection ConstantConditions
    if (action == null) throw new NullPointerException("action");
    foldRight((a, unit) -> { action.run(a); return unit; }, Unit.INSTANCE);
  }
}
