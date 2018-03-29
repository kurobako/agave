package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import java.util.Iterator;

import static io.github.kurobako.agave.Pair.pair;

public interface Reducer<A, B, S> {

  @Nonnull S init();

  @Nonnull Pair<S, B> step(S state, B result, A value) throws Done;

  @Nonnull B complete(S state, B result);

  static @Nonnull <A, B> Reducer<A, B, Unit> stateless(BiFunction<? super B, ? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return new Reducer<A, B, Unit>() {
      @Override
      public @Nonnull Unit init() {
        return Unit.INSTANCE;
      }

      @Override
      public @Nonnull Pair<Unit, B> step(Unit state, B result, A value) {
        return pair(Unit.INSTANCE, function.apply(result, value));
      }

      @Override
      public @Nonnull B complete(Unit state, B result) {
        return result;
      }
    };
  }

  static @Nonnull <A> Reducer<A, Sequence<A>, Unit> toSequence() {
    return new Reducer<A, Sequence<A>, Unit>() {
      @Override
      public @Nonnull Unit init() {
        return Unit.INSTANCE;
      }

      @Override
      public @Nonnull Pair<Unit, Sequence<A>> step(Unit state, Sequence<A> result, A value) throws Done {
        return pair(Unit.INSTANCE, result.inject(value));
      }

      @Override
      public @Nonnull Sequence<A> complete(Unit state, Sequence<A> result) {
        return result;
      }
    };
  }

  static @Nonnull <K, V> Reducer<Pair<K, V>, Dictionary<K, V>, Unit> toDictionary() {
    return new Reducer<Pair<K, V>, Dictionary<K, V>, Unit>() {
      @Override
      public @Nonnull Unit init() {
        return Unit.INSTANCE;
      }

      @Override
      public @Nonnull Pair<Unit, Dictionary<K, V>> step(Unit state, Dictionary<K, V> result, Pair<K, V> value) throws Done {
        return pair(Unit.INSTANCE, result.insert(value.first(), value.second()));
      }

      @Override
      public @Nonnull Dictionary<K, V> complete(Unit state, Dictionary<K, V> result) {
        return result;
      }
    };
  }

  static @Nonnull <A, B, S> B reduce(Iterator<A> iterator, Reducer<? super A, B, S> reducer, B initial) {
    //noinspection ConstantConditions
    if (iterator == null) throw new NullPointerException("iterator");
    //noinspection ConstantConditions
    if (reducer == null) throw new NullPointerException("reducer");
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    S state = reducer.init();
    B result = initial;
    try {
      while (iterator.hasNext()) {
        final Pair<S, B> sb = reducer.step(state, result, iterator.next());
        state = sb.first();
        result = sb.second();
      }
    } catch (Done ignored) {}
    return reducer.complete(state, result);
  }

  final class Done extends RuntimeException {
    public static @Nonnull Done INSTANCE = new Done();

    private Done() {}

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
