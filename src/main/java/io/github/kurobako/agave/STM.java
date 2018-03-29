package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static io.github.kurobako.agave.Sequence.sequence;

public final class STM {
  private final @Nonnull AtomicLong nextId = new AtomicLong();
  private final @Nonnull AtomicLong nextStamp = new AtomicLong();
  private final @Nonnull ThreadLocal<Transaction> transaction = new ThreadLocal<>();
  private final long lockWaitNanos;
  private final long terminateWaitNanos;
  private final long maxRetries;
  private final @Nullable Timer timer;
  private boolean fairByDefault;

  public STM() {
    lockWaitNanos = TimeUnit.MILLISECONDS.toNanos(10);
    terminateWaitNanos = TimeUnit.MILLISECONDS.toNanos(5);
    maxRetries = 50000;
    timer = null;
    fairByDefault = false;
  }

  @SuppressWarnings("ConstantConditions")
  public STM(long lockWait, TimeUnit lockWaitTimeUnit, long terminateWait, TimeUnit terminateWaitTimeUnit, long maxRetries, boolean fairByDefault, Timer timer) {
    if (lockWait < 0) throw new IllegalArgumentException();
    if (lockWaitTimeUnit == null) throw new NullPointerException("lockWaitTimeUnit");
    if (terminateWait < 0) throw new IllegalArgumentException();
    if (terminateWaitTimeUnit == null) throw new NullPointerException("terminateWaitTimeUnit");
    if (maxRetries < 0) throw new IllegalArgumentException();
    if (timer == null) throw new NullPointerException("timer");
    lockWaitNanos = lockWaitTimeUnit.toNanos(lockWait);
    terminateWaitNanos = terminateWaitTimeUnit.toNanos(terminateWait);
    this.maxRetries = maxRetries;
    this.fairByDefault = fairByDefault;
    this.timer = timer;
  }

  @SuppressWarnings("ConstantConditions")
  public STM(long lockWait, TimeUnit lockWaitTimeUnit, long terminateWait, TimeUnit terminateWaitTimeUnit, long maxRetries, boolean fairByDefault) {
    if (lockWait < 0) throw new IllegalArgumentException();
    if (lockWaitTimeUnit == null) throw new NullPointerException("lockWaitTimeUnit");
    if (terminateWait < 0) throw new IllegalArgumentException();
    if (terminateWaitTimeUnit == null) throw new NullPointerException("terminateWaitTimeUnit");
    if (maxRetries < 0) throw new IllegalArgumentException();
    lockWaitNanos = lockWaitTimeUnit.toNanos(lockWait);
    terminateWaitNanos = terminateWaitTimeUnit.toNanos(terminateWait);
    this.maxRetries = maxRetries;
    this.fairByDefault = fairByDefault;
    timer = null;
  }

  public @Nonnull <A> Ref<A> ref(A value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Ref<>(value, fairByDefault);
  }

  public @Nonnull <A> Ref<A> ref(A value, boolean fair) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Ref<>(value, fair);
  }

  public final class Ref<A> {

    final long id = nextId.getAndIncrement();
    final @Nonnull AtomicInteger failures  = new AtomicInteger();
    final @Nonnull ReadWriteLock lock;
    @Nonnull StampedValue<A> value;
    @Nullable Transaction.Ctx txCtx;

    Ref(A value, boolean fair) {
      lock = new ReentrantReadWriteLock(fair);
      this.value = new StampedValue<>(value, 0);
    }

    public @Nonnull A deref() {
      final Transaction tx = transaction.get();
      if (!exists(tx)) {
        final Lock readLock = lock.readLock();
        try {
          readLock.lock();
          return value.value;
        } finally {
          readLock.unlock();
        }
      } else return deref(tx);
    }

    private A deref(Transaction tx) {
      assert tx.ctx != null;
      if (!isAlive(tx.ctx.state())) throw Transaction.Retry.INSTANCE;
      final A val = tx.valuesLookup(this);
      if (val != null) return val;
      final Lock readLock = lock.readLock();
      try {
        readLock.lock();
        if (value.stamp > tx.readStamp) {
          for (StampedValue<A> v = value.prev; value != v; v = v.prev) {
            if (tx.readStamp >= v.stamp) return v.value;
          }
        } else return value.value;
      } finally {
        readLock.unlock();
      }
      failures.incrementAndGet();
      throw Transaction.Retry.INSTANCE;
    }

    private boolean exists(@Nullable Transaction tx) {
      if (tx == null) return false;
      final Transaction.Ctx ctx = tx.ctx;
      return ctx != null;
    }

    private boolean isAlive(Transaction.State state) {
      return Transaction.State.RUNNING == state || Transaction.State.COMMITING == state;
    }

    public @Nonnull A assign(A value) {
      //noinspection ConstantConditions
      if (value == null) throw new NullPointerException("value");
      final Transaction tx = transaction.get();
      if (!exists(tx)) throw new IllegalStateException();
      assert tx.ctx != null;
      if (!isAlive(tx.ctx.state())) throw Transaction.Retry.INSTANCE;
      if (tx.commuteOpsLookup(this) != null) throw new IllegalStateException();
      if (!tx.assignOpsContains(this)) {
        tx.assignOpsInsert(this);
        unlockReadsIfEnsured(tx);
        boolean locked = false;
        try {
          lockWrites();
          locked = true;
          if (tx.readStamp < this.value.stamp) throw Transaction.Retry.INSTANCE;
          if (txCtx != null && txCtx != tx.ctx && isAlive(txCtx.state())) {
            if (!tryTerminate(txCtx, tx.ctx.startStamp)) {
              lock.writeLock().unlock();
              locked = false;
              throw retry(tx);
            }
          }
          txCtx = tx.ctx;
        } finally {
          if (locked) lock.writeLock().unlock();
        }
      }
      tx.valuesInsert(this, value);
      return value;
    }

    private void unlockReadsIfEnsured(Transaction tx){
      if (tx.ensureOpsContains(this)) {
        tx.ensureOpsDelete(this);
        lock.readLock().unlock();
      }
    }

    private void lockWrites(){
      try {
        if (lock.writeLock().tryLock(lockWaitNanos, TimeUnit.NANOSECONDS)) return;
      } catch(InterruptedException ignored) {}
      throw Transaction.Retry.INSTANCE;
    }

    private boolean tryTerminate(Transaction.Ctx ctx, long startStamp) {
      boolean done = false;
      if (startStamp < ctx.startStamp && startStamp + terminateWaitNanos < (timer == null ? System.nanoTime() : timer.nanos())) {
        done = ctx.state(Transaction.State.RUNNING, Transaction.State.TERMINATED);
        if (done) ctx.countDown();
      }
      return done;
    }

    private Transaction.Retry retry(Transaction tx) {
      tx.terminate(Transaction.State.RETRY);
      try {
        assert txCtx != null;
        txCtx.await(lockWaitNanos, TimeUnit.NANOSECONDS);
      } catch(InterruptedException ignored) {}
      return Transaction.Retry.INSTANCE;
    }

    public @Nonnull A alter(Function<? super A, ? extends A> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return assign(function.apply(deref()));
    }

    public @Nonnull A commute(Function<? super A, ? extends A> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      final Transaction tx = transaction.get();
      if (!exists(tx)) throw new IllegalStateException();
      assert tx.ctx != null;
      if (!(isAlive(tx.ctx.state()))) throw Transaction.Retry.INSTANCE;
      A val = tx.valuesLookup(this);
      if (val == null) {
        final Lock readLock = lock.readLock();
        try {
          readLock.lock();
          val = value.value;
          tx.valuesInsert(this, val);
        } finally {
          readLock.unlock();
        }
      }
      final Sequence<Function<? super A, ? extends A>> commutes = tx.commuteOpsLookup(this);
      tx.commuteOpsInsert(this, commutes == null ? sequence(function) : commutes.inject(function));
      final A result = function.apply(val);
      tx.valuesInsert(this, result);
      return result;
    }

    public void ensure() {
      final Transaction tx = transaction.get();
      if (!exists(tx)) throw new IllegalStateException();
      assert tx.ctx != null;
      if (!isAlive(tx.ctx.state())) throw Transaction.Retry.INSTANCE;
      if (!tx.ensureOpsContains(this)) {
        final Lock readLock = lock.readLock();
        readLock.lock();
        if (tx.readStamp < value.stamp) {
          readLock.unlock();
          throw Transaction.Retry.INSTANCE;
        }
        if (txCtx != null) {
          final Transaction.State txCtxState = txCtx.state();
          if (Transaction.State.RUNNING == txCtxState || Transaction.State.COMMITING == txCtxState) {
            readLock.unlock();
            if (txCtx != tx.ctx) throw retry(tx);
          }
        } else {
          tx.ensureOpsInsert(this);
        }
      }
    }

    public @Nonnull <B> B transactionally(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      Transaction tx = transaction.get();
      if (tx != null) {
        return tx.ctx == null ? transactionally(tx, function) : function.apply(deref(tx));
      } else {
        tx = new Transaction();
        transaction.set(tx);
        try {
          return transactionally(tx, function);
        } finally {
          transaction.remove();
        }
      }
    }

    @SuppressWarnings("unchecked")
    private @Nonnull <B> B transactionally(Transaction tx, Function<? super A, ? extends B> function) {
      B result = null;
      boolean done = false;
      Deque<Ref<?>> locked = new ArrayDeque<>();
      for (int i = 0; i < maxRetries; i++) {
        try {
          final long stamp = nextStamp.incrementAndGet();
          tx.readStamp = stamp;
          if (i == 0) {
            tx.startStamp = stamp;
            tx.startTime = timer == null ? System.nanoTime() : timer.nanos();
          }
          tx.ctx = new Transaction.Ctx(tx.startStamp, Transaction.State.RUNNING);
          result = function.apply(deref(tx));
          if (tx.ctx.state(Transaction.State.RUNNING, Transaction.State.COMMITING)) {
            tx.commuteOps.forEachLeft(p -> {
              final Ref<?> r = p.first();
              if (!tx.assignOpsContains(r)) {
                boolean ensured = tx.ensureOpsContains(r);
                r.unlockReadsIfEnsured(tx);
                r.lockWrites();
                locked.push(r);
                if (tx.readStamp < r.value.stamp && ensured) throw Transaction.Retry.INSTANCE;
                assert r.txCtx != null;
                if (isAlive(r.txCtx.state()) && r.txCtx != tx.ctx && !tryTerminate(r.txCtx, tx.startStamp)) throw Transaction.Retry.INSTANCE;
                Object v = r.value.value;
                tx.valuesInsert((Ref<Object>)r, v);
                tx.valuesInsert((Ref<Object>)r, p.second().foldLeft((o, f) -> ((Function<Object, Object>)f).apply(v), v));
              }
            });
            tx.assignOps.forEachLeft(p -> {
              final Ref<?> r = p.first();
              r.lockWrites();
              locked.push(r);
            });
            long commitStamp = nextStamp.incrementAndGet();
            tx.values.forEachLeft(p -> {
              Ref<Object> r = (Ref<Object>) p.first();
              int history = 0;
              for(StampedValue<?> sv = r.value.next; sv != r.value; sv = sv.next) history++;
              final Object newValue = p.second();
              if (r.failures.get() > 0 && history < 10) {
                final StampedValue<Object> newV = new StampedValue<>(newValue, commitStamp);
                newV.prev = r.value;
                newV.next = r.value.next;
                newV.prev.next = newV;
                newV.next.prev = newV;
                r.value = newV;
                r.failures.set(0);
              } else {
                r.value = r.value.next;
                r.value.value = newValue;
                r.value.stamp = commitStamp;
              }
            });
            tx.ctx.state(Transaction.State.COMMITED);
            done = true;
          }
        } catch (Transaction.Retry ignored) {
        } finally {
          while (!locked.isEmpty()) locked.pop().lock.writeLock().unlock();
          tx.ensureOps.forEachLeft(p -> p.first().lock.readLock().unlock());
          if (done) tx.terminate(Transaction.State.COMMITED);
          else tx.terminate(Transaction.State.RETRY);
        }
        if (done) break;
      }
      if (done) return result;
      throw new UncheckedExecutionException("retry limit reached");
    }

    @Override
    public int hashCode() {
      return (int) (id ^ id >>> 32);
    }

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

  }

  private static final class StampedValue<A> {

    @Nonnull A value;
    long stamp;
    @Nonnull StampedValue<A> prev;
    @Nonnull StampedValue<A> next;

    StampedValue(A value, long stamp) {
      this.value = value;
      this.stamp = stamp;
      prev = this;
      next = this;
    }

  }

  public interface Timer {
    long nanos();
  }
}
