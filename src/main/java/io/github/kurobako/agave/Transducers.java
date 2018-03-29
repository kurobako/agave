package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import static io.github.kurobako.agave.Option.none;
import static io.github.kurobako.agave.Option.some;
import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Sequence.sequence;

public final class Transducers {
  private Transducers() {}

  public static @Nonnull <A, R, S> Function<Reducer<A, R, S>, Reducer<A, R, S>> filter(Predicate<? super A> predicate) {
    //noinspection ConstantConditions
    if (predicate == null) throw new NullPointerException("predicate");
    return original -> new Reducer<A, R, S>() {
      @Override
      public @Nonnull S init() {
        return original.init();
      }

      @Override
      public @Nonnull Pair<S, R> step(S state, R result, A value) throws Done {
        return predicate.test(value) ? original.step(state, result, value) : pair(state, result);
      }

      @Override
      public @Nonnull R complete(S state, R result) {
        return original.complete(state, result);
      }
    };
  }

  public static @Nonnull <A, B, R, S> Function<Reducer<B, R, S>, Reducer<A, R, S>> map(Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return original -> new Reducer<A, R, S>() {
      @Override
      public @Nonnull S init() {
        return original.init();
      }

      @Override
      public @Nonnull Pair<S, R> step(S state, R result, A value) throws Done {
        return original.step(state, result, function.apply(value));
      }

      @Override
      public @Nonnull R complete(S state, R result) {
        return original.complete(state, result);
      }
    };
  }

  public static @Nonnull <A, R, S> Function<Reducer<A, R, S>, Reducer<A, R, Pair<Integer, S>>> take(int n) {
    if (n < 0) throw new IllegalArgumentException();
    return original -> new Reducer<A, R, Pair<Integer, S>>() {
      @Override
      public @Nonnull Pair<Integer, S> init() {
        return pair(n, original.init());
      }

      @Override
      public @Nonnull Pair<Pair<Integer, S>, R> step(Pair<Integer, S> state, R result, A value) throws Done {
        if (state.first() == 0) throw Done.INSTANCE;
        final Pair<S, R> sr = original.step(state.second(), result, value);
        return pair(pair(state.first() - 1, sr.first()), sr.second());
      }

      @Override
      public @Nonnull R complete(Pair<Integer, S> state, R result) {
        return original.complete(state.second(), result);
      }
    };
  }

  public static @Nonnull <A, R, S> Function<Reducer<A, R, S>, Reducer<A, R, Pair<Integer, S>>> drop(int n) {
    if (n < 0) throw new IllegalArgumentException();
    return original -> new Reducer<A, R, Pair<Integer, S>>() {
      @Override
      public @Nonnull Pair<Integer, S> init() {
        return pair(n, original.init());
      }

      @Override
      public @Nonnull Pair<Pair<Integer, S>, R> step(Pair<Integer, S> state, R result, A value) throws Done {
        if (state.first() != 0) return pair(pair(state.first() - 1, state.second()), result);
        final Pair<S, R> sr = original.step(state.second(), result, value);
        return pair(pair(0, sr.first()), sr.second());
      }

      @Override
      public @Nonnull R complete(Pair<Integer, S> state, R result) {
        return original.complete(state.second(), result);
      }
    };
  }

  public static @Nonnull <A, R, S> Function<Reducer<A, R, S>, Reducer<A, R, Pair<Option<A>, S>>> dedupe() {
    return original -> new Reducer<A, R, Pair<Option<A>, S>>() {
      @Override
      public @Nonnull Pair<Option<A>, S> init() {
        return pair(none(), original.init());
      }

      @Override
      public @Nonnull Pair<Pair<Option<A>, S>, R> step(Pair<Option<A>, S> state, R result, A value) throws Done {
        if (value.equals(state.first().asNullable())) return pair(state, result);
        final Pair<S, R> sr = original.step(state.second(), result, value);
        return pair(pair(some(value), sr.first()), sr.second());
      }

      @Override
      public @Nonnull R complete(Pair<Option<A>, S> state, R result) {
        return original.complete(state.second(), result);
      }
    };
  }

  public static @Nonnull <A, R, S, X> Function<Reducer<Sequence<A>, R, S>, Reducer<A, R, Pair<Option<Pair<Sequence<A>, X>>, S>>> partition(Function<? super A, ? extends X> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return original -> new Reducer<A, R, Pair<Option<Pair<Sequence<A>, X>>, S>>() {
      @Override
      public @Nonnull Pair<Option<Pair<Sequence<A>, X>>, S> init() {
        return pair(none(), original.init());
      }

      @Override
      public @Nonnull Pair<Pair<Option<Pair<Sequence<A>, X>>, S>, R> step(Pair<Option<Pair<Sequence<A>, X>>, S> state, R result, A value) throws Done {
        final X x = function.apply(value);
        final Pair<Sequence<A>, X> sx = state.first().asNullable();
        if (sx == null) return pair(pair(some(pair(sequence(value), x)), state.second()), result);
        if (sx.second().equals(x)) return pair(pair(some(pair(sx.first().inject(value), sx.second())), state.second()), result);
        final Pair<S, R> sr = original.step(state.second(), result, sx.first());
        return pair(pair(some(pair(sequence(value), x)), sr.first()), sr.second());
      }

      @Override
      public @Nonnull R complete(Pair<Option<Pair<Sequence<A>, X>>, S> state, R result) {
        final Pair<Sequence<A>, X> sx = state.first().asNullable();
        if (sx == null) return original.complete(state.second(), result);
        try {
          return original.step(state.second(), result, sx.first()).second();
        } catch (Done done) {
          return result;
        }
      }
    };
  }
}
