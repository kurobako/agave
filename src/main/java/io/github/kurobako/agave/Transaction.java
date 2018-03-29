package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.github.kurobako.agave.Dictionary.dictionary;

final class Transaction {

  @Nonnull Dictionary<STM.Ref<?>, Object> values = dictionary();
  @Nonnull Dictionary<STM.Ref<?>, Unit> assignOps = dictionary();
  @Nonnull Dictionary<STM.Ref<?>, Sequence<? extends Function<?, ?>>> commuteOps = dictionary();
  @Nonnull Dictionary<STM.Ref<?>, Unit> ensureOps = dictionary();
  long readStamp;
  long startStamp;
  long startTime;
  @Nullable Ctx ctx;

  @SuppressWarnings("unchecked")
  @Nullable <A> A valuesLookup(STM.Ref<A> key) {
    return (A) values.lookupNullable(key);
  }

  <A> void valuesInsert(STM.Ref<A> key, A value) {
    values = values.insert(key, value);
  }

  <A> boolean assignOpsContains(STM.Ref<A> key) {
    return assignOps.lookupNullable(key) != null;
  }

  <A> void assignOpsInsert(STM.Ref<A> key) {
    assignOps = assignOps.insert(key, Unit.INSTANCE);
  }

  @SuppressWarnings("unchecked")
  @Nullable <A> Sequence<Function<? super A, ? extends A>> commuteOpsLookup(STM.Ref<A> key) {
    return (Sequence<Function<? super A, ? extends A>>) commuteOps.lookupNullable(key);
  }

  <A> void commuteOpsInsert(STM.Ref<A> key, Sequence<Function<? super A, ? extends A>> value) {
    commuteOps = commuteOps.insert(key, value);
  }

  <A> boolean ensureOpsContains(STM.Ref<A> key) {
    return ensureOps.lookupNullable(key) != null;
  }

  <A> void ensureOpsInsert(STM.Ref<A> key) {
    ensureOps = ensureOps.insert(key, Unit.INSTANCE);
  }

  <A> void ensureOpsDelete(STM.Ref<A> key) {
    ensureOps = ensureOps.delete(key);
  }

  void terminate(Transaction.State state) {
    if (ctx != null) {
      synchronized(this) {
        ctx.state(state);
        ctx.countDown();
      }
      values = dictionary();
      assignOps = dictionary();
      commuteOps = dictionary();
      ctx = null;
    }
  }

  static final class Ctx extends CountDownLatch {
    static final AtomicReferenceFieldUpdater<Ctx, State> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(Ctx.class, State.class, "state");

    final long startStamp;
    private volatile @Nonnull State state;

    Ctx(long startStamp, State state) {
      super(1);
      this.startStamp = startStamp;
      this.state = state;
    }

    @Nonnull State state() {
      return state;
    }

    boolean state(State expected, State updated) {
      return STATE_UPDATER.compareAndSet(this, expected, updated);
    }

    void state(State value) {
      STATE_UPDATER.set(this, value);
    }

  }

  enum State {
    RUNNING, COMMITING, COMMITED, RETRY, TERMINATED
  }

  static final class Retry extends RuntimeException {
    static final @Nonnull Retry INSTANCE = new Retry();

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

}
