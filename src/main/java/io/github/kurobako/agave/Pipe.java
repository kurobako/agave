package io.github.kurobako.agave;

import io.github.kurobako.agave.ringbuffer.Allocate;
import io.github.kurobako.agave.ringbuffer.Consumer;
import io.github.kurobako.agave.ringbuffer.RingBuffer;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static io.github.kurobako.agave.Dictionary.dictionary;
import static io.github.kurobako.agave.Option.none;
import static io.github.kurobako.agave.Option.some;
import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Sequence.sequence;

public interface Pipe<I, S, O> {

  @Nonnull S init();

  @Nonnull Pair<S, O> handle(S state, I input);

  static @Nonnull <I, S, O> Ref<I, S, O> ref(Pipe<I, S, O> pipe, int minBufferSize) {
    //noinspection ConstantConditions
    if (pipe == null) throw new NullPointerException("pipe");
    if (minBufferSize < 1) throw new IllegalArgumentException("minBufferSize");
    return new Ref.Async<I, S, O>(minBufferSize) {
      final @Nonnull Consumer<Payload<I, S, O>> consumer = buffer.subscribe();
      volatile @Nonnull S state = pipe.init();

      @Override
      public @Nonnull S deref() {
        return state;
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return dictionary(id(), sequence(() -> consumer.consume((payload, more) -> {
          Pair<S, O> so = null;
          try {
            so = pipe.handle(state, payload.data);
            state = so.first();
          } catch (RuntimeException e) {
            boolean handled = false;
            Sequence<Function<? super RuntimeException, Supervision>> stack = payload.supervisors;
            outside: while (!stack.isEmpty()) {
              switch (stack.first().apply(e)) {
                case ESCALATE: {
                  stack = stack.deleteFirst();
                  break;
                }
                case RESTART: {
                  state = pipe.init();
                  handled = true;
                  break outside;
                }
                case RESUME: {
                  handled = true;
                  break outside;
                }
              }
            }
            if (!handled) payload.onFailure.run(e);
          }
          if (so != null) payload.onSuccess.run(so);
          return true;
        })));
      }
    };
  }

  static @Nonnull <I, S, O> Ref<I, S, O> ref(Pipe<I, S, O> pipe, BiFunction<? super S, ? super S, ? extends S> merge, int minBufferSize, int nThreads) {
    //noinspection ConstantConditions
    if (pipe == null) throw new NullPointerException("pipe");
    //noinspection ConstantConditions
    if (merge == null) throw new NullPointerException("merge");
    if (minBufferSize < 1) throw new IllegalArgumentException("minBufferSize");
    if (0 >= nThreads || nThreads > 64) throw new IllegalArgumentException("nThreads");
    return new Ref.Async<I, S, O>(minBufferSize) {
      final @Nonnull Consumer<Payload<I, S, O>>[] consumers = buffer.subscribe(nThreads);
      final @Nonnull AtomicReference<S> state = new AtomicReference<>(pipe.init());

      @Override
      public @Nonnull S deref() {
        return state.get();
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        Sequence<Supplier<Consumer.State>> seq = sequence();
        for (Consumer<Payload<I, S, O>> consumer : consumers) {
          seq = seq.push(() -> consumer.consume((payload, more) -> {
            Pair<S, O> so = null;
            try {
              S old = state.get();
              so = pipe.handle(old, payload.data);
              while (true) {
                if (state.compareAndSet(old, merge.apply(old, so.first()))) break;
                else old = state.get();
              }
            } catch (RuntimeException e) {
              boolean handled = false;
              Sequence<Function<? super RuntimeException, Supervision>> stack = payload.supervisors;
              outside: while (!stack.isEmpty()) {
                switch (stack.first().apply(e)) {
                  case ESCALATE: {
                    stack = stack.deleteFirst();
                    break;
                  }
                  case RESTART: {
                    state.set(pipe.init());
                    handled = true;
                    break outside;
                  }
                  case RESUME: {
                    handled = true;
                    break outside;
                  }
                }
              }
              if (!handled) payload.onFailure.run(e);
            }
            if (so != null) payload.onSuccess.run(so);
            return true;
          }));
        }
        return dictionary(id(), seq);
      }
    };
  }


  static @Nonnull <A, B, C, S, T> Ref<A, T, C> compose(Ref<A, S, B> first, Ref<B, T, C> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Ref<A, T, C>() {
      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<T, C>> onSuccess, Procedure<? super RuntimeException> onFailure, A a) {
        first.push(supervisors, sb -> second.push(supervisors, onSuccess, onFailure, sb.second()), onFailure, a);
      }

      @Override
      public @Nonnull T deref() {
        return second.deref();
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return first.steps().fold((dict, entry) -> dict.insert(entry.first(), entry.second()), second.steps());
      }
    };
  }

  static @Nonnull <A, B, C, S> Ref<Pair<A, C>, S, Pair<B, C>> first(Ref<? super A, S, ? extends B> ref) {
    //noinspection ConstantConditions
    if (ref == null) throw new NullPointerException("ref");
    return new Ref<Pair<A, C>, S, Pair<B, C>>() {
      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S, Pair<B, C>>> onSuccess, Procedure<? super RuntimeException> onFailure, Pair<A, C> ac) {
        ref.push(supervisors, sb -> onSuccess.run(pair(sb.first(), pair(sb.second(), ac.second()))), onFailure, ac.first());
      }

      @Override
      public @Nonnull S deref() {
        return ref.deref();
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return ref.steps();
      }
    };
  }

  static @Nonnull <Z, A, B, S> Ref<Pair<Z, A>, S, Pair<Z, B>> second(Ref<? super A, S, ? extends B> ref) {
    //noinspection ConstantConditions
    if (ref == null) throw new NullPointerException("ref");
    return new Ref<Pair<Z, A>, S, Pair<Z, B>>() {
      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S, Pair<Z, B>>> onSuccess, Procedure<? super RuntimeException> onFailure, Pair<Z, A> za) {
        ref.push(supervisors, sb -> onSuccess.run(pair(sb.first(), pair(za.first(), sb.second()))), onFailure, za.second());
      }

      @Override
      public @Nonnull S deref() {
        return ref.deref();
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return ref.steps();
      }
    };
  }

  static @Nonnull <A, B, C, S> Ref<Either<A, C>, S, Either<B, C>> left(Ref<? super A, S, ? extends B> ref) {
    //noinspection ConstantConditions
    if (ref == null) throw new NullPointerException("ref");
    return new Ref<Either<A, C>, S, Either<B, C>>() {
      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S, Either<B, C>>> onSuccess, Procedure<? super RuntimeException> onFailure, Either<A, C> ac) {
        for (A a : ac.asLeft()) ref.push(supervisors, sb -> onSuccess.run(pair(sb.first(), Either.left(sb.second()))), onFailure, a);
        for (C c : ac.asRight()) onSuccess.run(pair(ref.deref(), Either.right(c)));
      }

      @Override
      public @Nonnull S deref() {
        return ref.deref();
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return ref.steps();
      }
    };
  }

  static @Nonnull <A, B, C, S> Ref<Either<C, A>, S, Either<C, B>> right(Ref<? super A, S, ? extends B> ref) {
    //noinspection ConstantConditions
    if (ref == null) throw new NullPointerException("ref");
    return new Ref<Either<C, A>, S, Either<C, B>>() {
      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S, Either<C, B>>> onSuccess, Procedure<? super RuntimeException> onFailure, Either<C, A> ca) {
        for (C c : ca.asLeft()) onSuccess.run(pair(ref.deref(), Either.left(c)));
        for (A a : ca.asRight()) ref.push(supervisors, sb -> onSuccess.run(pair(sb.first(), Either.right(sb.second()))), onFailure, a);
      }

      @Override
      public @Nonnull S deref() {
        return ref.deref();
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return ref.steps();
      }
    };
  }

  static @Nonnull <A, B, C, D, S, T> Ref<Either<A, C>, Either<S, T>, Either<B, D>> sum(Ref<? super A, S, ? extends B> first, Ref<? super C, T, ? extends D> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Ref<Either<A, C>, Either<S, T>, Either<B, D>>() {
      volatile @Nonnull Either<S, T> state = Either.right(second.deref());

      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<Either<S, T>, Either<B, D>>> onSuccess, Procedure<? super RuntimeException> onFailure, Either<A, C> ac) {
        for (A a : ac.asLeft()) first.push(supervisors, sb -> {
          state = Either.left(sb.first());
          onSuccess.run(pair(state, Either.left(sb.second())));
        }, onFailure, a);
        for (C c : ac.asRight()) second.push(supervisors, td -> {
          state = Either.right(td.first());
          onSuccess.run(pair(state, Either.right(td.second())));
        }, onFailure, c);
      }

      @Override
      public @Nonnull Either<S, T> deref() {
        return state;
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return first.steps().fold((dict, entry) -> dict.insert(entry.first(), entry.second()), second.steps());
      }
    };
  }

  static @Nonnull <A, B, C, D, S, T> Ref<Pair<A, C>, Pair<S, T>, Pair<B, D>> product(Ref<? super A, S, ? extends B> first, Ref<? super C, T, ? extends D> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Ref<Pair<A, C>, Pair<S, T>, Pair<B, D>>() {
      volatile @Nonnull Pair<S, T> state = pair(first.deref(), second.deref());

      @Override
      @SuppressWarnings("unchecked")
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<Pair<S, T>, Pair<B, D>>> onSuccess, Procedure<? super RuntimeException> onFailure, Pair<A, C> ac) {
        final AtomicReference<Pair<Option<Pair<S, B>>, Option<Pair<T, D>>>> sbtd = new AtomicReference<>(pair(none(), none()));
        first.push(supervisors, sb -> {
          Pair<Option<Pair<S, B>>, Option<Pair<T, D>>> old = sbtd.get();
          while (!sbtd.compareAndSet(old, pair(some((Pair<S, B>) sb), old.second()))) {
            old = sbtd.get();
          }
          for (Pair<T, D> td : old.second()) onSuccess.run(pair(pair(sb.first(), td.first()), pair(sb.second(), td.second())));
        }, onFailure, ac.first());
        second.push(supervisors, td -> {
          Pair<Option<Pair<S, B>>, Option<Pair<T, D>>> old = sbtd.get();
          while (!sbtd.compareAndSet(old, pair(old.first(), some((Pair<T, D>) td)))) {
            old = sbtd.get();
          }
          for (Pair<S, B> sb : old.first()) onSuccess.run(pair(pair(sb.first(), td.first()), pair(sb.second(), td.second())));
        }, onFailure, ac.second());
      }

      @Override
      public @Nonnull Pair<S, T> deref() {
        return state;
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return first.steps().fold((dict, entry) -> dict.insert(entry.first(), entry.second()), second.steps());
      }
    };
  }

  static @Nonnull <A, B, C, S, T> Ref<Either<A, C>, Either<S, T>, B> fanIn(Ref<? super A, S, ? extends B> first, Ref<? super C, T, ? extends B> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Ref<Either<A, C>, Either<S, T>, B>() {
      volatile @Nonnull Either<S, T> state = Either.right(second.deref());

      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<Either<S, T>, B>> onSuccess, Procedure<? super RuntimeException> onFailure, Either<A, C> ac) {
        for (A a : ac.asLeft()) first.push(supervisors, sb -> {
          state = Either.left(sb.first());
          onSuccess.run(pair(state, sb.second()));
        }, onFailure, a);
        for (C c : ac.asRight()) second.push(supervisors, tb -> {
          state = Either.right(tb.first());
          onSuccess.run(pair(state, tb.second()));
        }, onFailure, c);
      }

      @Override
      public @Nonnull Either<S, T> deref() {
        return state;
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return first.steps().fold((dict, entry) -> dict.insert(entry.first(), entry.second()), second.steps());
      }
    };
  }

  static @Nonnull <A, B, C, S, T> Ref<A, Pair<S, T>, Pair<B, C>> fanOut(Ref<? super A, S, ? extends B> first, Ref<? super A, T, ? extends C> second) {
    //noinspection ConstantConditions
    if (first == null) throw new NullPointerException("first");
    //noinspection ConstantConditions
    if (second == null) throw new NullPointerException("second");
    return new Ref<A, Pair<S, T>, Pair<B, C>>() {
      volatile @Nonnull Pair<S, T> state = pair(first.deref(), second.deref());

      @Override
      @SuppressWarnings("unchecked")
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<Pair<S, T>, Pair<B, C>>> onSuccess, Procedure<? super RuntimeException> onFailure, A a) {
        final AtomicReference<Pair<Option<Pair<S, B>>, Option<Pair<T, C>>>> sbtc = new AtomicReference<>(pair(none(), none()));
        first.push(supervisors, sb -> {
          Pair<Option<Pair<S, B>>, Option<Pair<T, C>>> old = sbtc.get();
          while (!sbtc.compareAndSet(old, pair(some((Pair<S, B>) sb), old.second()))) {
            old = sbtc.get();
          }
          for (Pair<T, C> tc : old.second()) {
            state = pair(sb.first(), tc.first());
            onSuccess.run(pair(state, pair(sb.second(), tc.second())));
          }
        }, onFailure, a);
        second.push(supervisors, tc -> {
          Pair<Option<Pair<S, B>>, Option<Pair<T, C>>> old = sbtc.get();
          while (!sbtc.compareAndSet(old, pair(old.first(), some((Pair<T, C>) tc)))) {
            old = sbtc.get();
          }
          for (Pair<S, B> sb : old.first()) {
            state = pair(sb.first(), tc.first());
            onSuccess.run(pair(state, pair(sb.second(), tc.second())));
          }
        }, onFailure, a);
      }

      @Override
      public @Nonnull Pair<S, T> deref() {
        return state;
      }

      @Override
      public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
        return first.steps().fold((dict, entry) -> dict.insert(entry.first(), entry.second()), second.steps());
      }
    };
  }

  abstract class Ref<I, S, O> {
    private final @Nonnull UUID uuid = UUID.randomUUID();

    private Ref() {}

    public final @Nonnull String id() {
      return uuid.toString();
    }

    public final void push(Procedure<Pair<S, O>> onSuccess, Procedure<? super RuntimeException> onFailure, I data) {
      //noinspection ConstantConditions
      if (onSuccess == null) throw new NullPointerException("onSuccess");
      //noinspection ConstantConditions
      if (onFailure == null) throw new NullPointerException("onFailure");
      //noinspection ConstantConditions
      if (data == null) throw new NullPointerException("data");
      push(sequence(), onSuccess, onFailure, data);
    }

    abstract void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S, O>> onSuccess, Procedure<? super RuntimeException> onFailure, I data);

    public abstract @Nonnull S deref();

    public abstract @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps();

    public final @Nonnull Ref<I, S, O> supervise(Function<? super RuntimeException, Supervision> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return new Ref<I, S, O>() {
        @Override
        void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S, O>> onSuccess, Procedure<? super RuntimeException> onFailure, I data) {
          Ref.this.push(supervisors.push(function), onSuccess, onFailure, data);
        }

        @Override
        public @Nonnull S deref() {
          return Ref.this.deref();
        }

        @Override
        public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
          return Ref.this.steps();
        }
      };
    }

    public final @Nonnull <T> Ref<I, T, O> map(Function<? super S, ? extends T> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return new Ref<I, T, O>() {
        @Override
        void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<T, O>> onSuccess, Procedure<? super RuntimeException> onFailure, I data) {
          Ref.this.push(supervisors, so -> onSuccess.run(pair(function.apply(so.first()), so.second())), onFailure, data);
        }

        @Override
        public @Nonnull T deref() {
          return function.apply(Ref.this.deref());
        }

        @Override
        public @Nonnull Dictionary<String, Sequence<Supplier<Consumer.State>>> steps() {
          return Ref.this.steps();
        }
      };
    }

    private static abstract class Async<I, S, O> extends Ref<I, S, O> {
      final @Nonnull RingBuffer<Payload<I, S, O>> buffer;

      Async(int minBufferSize) {
        super();
        this.buffer = RingBuffer.multiProducer(minBufferSize, (Allocate<Payload<I, S, O>>) Payload::new);
      }

      @Override
      void push(Sequence<Function<? super RuntimeException, Supervision>> supervisors, Procedure<Pair<S,O>> onSuccess, Procedure<? super RuntimeException> onFailure, I data) {
        final long token = buffer.claim();
        try {
          final Payload<I, S, O> payload = buffer.read(token);
          payload.supervisors = supervisors;
          payload.onSuccess = onSuccess;
          payload.onFailure = onFailure;
          payload.data = data;
        } finally {
          buffer.publish(token);
        }
      }

    }

    static final class Payload<I, S, O> {
      I data;
      Procedure<Pair<S, O>> onSuccess;
      Procedure<? super RuntimeException> onFailure;
      Sequence<Function<? super RuntimeException, Supervision>> supervisors;
    }

  }

  enum Supervision {
    RESUME, RESTART, ESCALATE
  }

}
