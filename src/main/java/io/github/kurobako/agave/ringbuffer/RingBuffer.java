package io.github.kurobako.agave.ringbuffer;

import sun.misc.Contended;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Integer.numberOfLeadingZeros;
import static java.lang.Math.min;
import static java.lang.Math.pow;

public abstract class RingBuffer<E> {
  private RingBuffer() {}

  public final long tryClaim() {
    return tryClaim(1);
  }

  public abstract long tryClaim(int amount);

  public final long claim() {
    return claim(1);
  }

  public final long claim(int amount) {
    if (amount < 1) throw new IllegalArgumentException();
    long result = tryClaim(amount);
    while (result == -1L) {
      result = tryClaim(amount);
      LockSupport.parkNanos(1L);
    }
    return result;
  }

  public abstract E read(long token);

  public abstract void write(long token, E data);

  public void publish(long token) {
    publish(token, token);
  }

  public abstract void publish(long from, long to);

  public abstract @Nonnull Consumer<E> subscribe(@Nonnull Cursor... after);

  public abstract @Nonnull Consumer<E>[] subscribe(int amount, @Nonnull Cursor... after);

  public abstract boolean unsubscribe(@Nonnull Consumer<E> consumer);

  public abstract long lastPublished(long from, long to);

  public abstract @Nonnull Cursor cursor();

  public static @Nonnull <E> RingBuffer<E> singleProducer(int minSize) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new SingleProducerBuffer<>(minSize, null);
  }

  public static @Nonnull <E> RingBuffer<E> singleProducer(int minSize, Allocate<E> allocator) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new SingleProducerBuffer<>(minSize, allocator, null);
  }

  public static @Nonnull <E> RingBuffer<E> singleProducer(int minSize, Runnable onPublish) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new SingleProducerBuffer<>(minSize, onPublish);
  }

  public static @Nonnull <E> RingBuffer<E> singleProducer(int minSize, Allocate<E> allocator, Runnable onPublish) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new SingleProducerBuffer<>(minSize, allocator, onPublish);
  }

  public static @Nonnull <E> RingBuffer<E> multiProducer(int minSize) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new MultiProducerBuffer<>(minSize, null);
  }

  public static @Nonnull <E> RingBuffer<E> multiProducer(int minSize, Allocate<E> allocator) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new MultiProducerBuffer<>(minSize, allocator, null);
  }

  public static @Nonnull <E> RingBuffer<E> multiProducer(int minSize, Runnable onPublish) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new MultiProducerBuffer<>(minSize, onPublish);
  }

  public static @Nonnull <E> RingBuffer<E> multiProducer(int minSize, Allocate<E> allocator, Runnable onPublish) {
    if (minSize < 1) throw new IllegalArgumentException();
    minSize = (int) pow(2, 32 - Integer.numberOfLeadingZeros(minSize - 1));
    return new MultiProducerBuffer<>(minSize, allocator, onPublish);
  }

  @Contended
  private static final class SingleProducerBuffer<E> extends RingBuffer<E> {
    private final @Nonnull E[] entries;
    private final int mask;
    private final @Nullable Runnable onPublish;
    private final @Nonnull AtomicCursor cursor = new AtomicCursor();
    private final @Nonnull DynamicMembershipCursors gate = new DynamicMembershipCursors();
    private long next = -1L;
    private long cachedMin = -1L;

    @SuppressWarnings("unchecked")
    SingleProducerBuffer(int size, @Nullable Runnable onPublish) {
      assert 0 < size && Integer.bitCount(size) == 1;
      entries = (E[]) new Object[size];
      mask = entries.length - 1;
      this.onPublish = onPublish;
    }

    @SuppressWarnings("unchecked")
    SingleProducerBuffer(int size, Allocate<E> allocator, @Nullable Runnable onPublish) {
      assert 0 < size && Integer.bitCount(size) == 1;
      entries = (E[]) new Object[size];
      for (int i = 0; i < entries.length; i++) entries[i] = allocator.allocate();
      mask = entries.length - 1;
      this.onPublish = onPublish;
    }

    @Override
    public long tryClaim(int amount) {
      if (amount < 1) throw new IllegalArgumentException();
      final long wrapsAt = next + amount - entries.length;
      if (next < cachedMin || cachedMin < wrapsAt) {
        cursor.writeVolatile(next);
        final long min = min(next, gate.readVolatile());
        cachedMin = min;
        if (wrapsAt > min) return -1L;
      }
      return next += amount;
    }

    @Override
    public E read(long token) {
      if (token < 0) throw new IllegalArgumentException();
      return entries[(int)(token & mask)];
    }

    @Override
    public void write(long token, E data) {
      if (token < 0) throw new IllegalArgumentException();
      entries[(int)(token & mask)] = data;
    }

    @Override
    public void publish(long from, long to) {
      if (to < from) throw new IllegalArgumentException();
      cursor.writeOrdered(to);
      if (onPublish != null) onPublish.run();
    }

    public final @Nonnull Consumer<E> subscribe(Cursor... after) {
      final Consumer.BatchingConsumer<E> result = Consumer.batching(this, after);
      gate.invite(cursor, result.cursor());
      return result;
    }

    @Override
    public final @Nonnull Consumer<E>[] subscribe(int amount, Cursor... after) {
      if (amount < 1) throw new IllegalArgumentException();
      final Consumer.ParallelConsumer<E>[] result = Consumer.parallel(this, amount, after);
      gate.invite(cursor, result[0].cursor());
      return result;
    }

    @Override
    public boolean unsubscribe(Consumer<E> consumer) {
      return gate.expel(consumer.cursor());
    }

    @Override
    public long lastPublished(long from, long to) {
      return to;
    }

    @Override
    public @Nonnull Cursor cursor() {
      return cursor;
    }
  }

  @Contended
  private static final class MultiProducerBuffer<E> extends RingBuffer<E> {
    private final @Nonnull E[] entries;
    private final int mask;
    private final @Nullable Runnable onPublish;
    private final @Nonnull AtomicCursor cursor = new AtomicCursor();
    private final @Nonnull
    DynamicMembershipCursors gate = new DynamicMembershipCursors();
    private final @Nonnull AtomicCursor cachedGating = new AtomicCursor();
    private final @Nonnull AtomicIntegerArray availability;
    private final int availabilityShift;

    @SuppressWarnings("unchecked")
    MultiProducerBuffer(int size, @Nullable Runnable onPublish) {
      assert 0 < size && Integer.bitCount(size) == 1;
      entries = (E[]) new Object[size];
      mask = size - 1;
      this.onPublish = onPublish;
      availability = new AtomicIntegerArray(size);
      for (int i = 0; i < size; i++) availability.lazySet(i, -1);
      availabilityShift = 31 - numberOfLeadingZeros(size);
    }

    @SuppressWarnings("unchecked")
    MultiProducerBuffer(int size, Allocate<E> allocator, @Nullable Runnable onPublish) {
      assert 0 < size && Integer.bitCount(size) == 1;
      entries = (E[]) new Object[size];
      for (int i = 0; i < entries.length; i++) entries[i] = allocator.allocate();
      mask = size - 1;
      this.onPublish = onPublish;
      availability = new AtomicIntegerArray(size);
      for (int i = 0; i < size; i++) availability.lazySet(i, -1);
      availabilityShift = 31 - numberOfLeadingZeros(size);
    }

    @Override
    public long tryClaim(int amount) {
      if (amount < 1) throw new IllegalArgumentException();
      long cursorValue;
      long result;
      do {
        cursorValue = cursor.readVolatile();
        result = cursorValue + amount;
        final long wrapsAt = cursorValue + amount - entries.length;
        final long cachedGatingValue = cachedGating.readVolatile();
        if (cursorValue < cachedGatingValue || cachedGatingValue < wrapsAt) {
          final long min = min(cursorValue, gate.readVolatile());
          cachedGating.writeOrdered(min);
          if (min < wrapsAt) return -1L;
        }
      } while (!cursor.compareAndSwap(cursorValue, result));
      return result;
    }

    @Override
    public E read(long token) {
      if (token < 0) throw new IllegalArgumentException();
      return entries[(int)(token & mask)];
    }

    @Override
    public void write(long token, E data) {
      if (token < 0) throw new IllegalArgumentException();
      entries[(int)(token & mask)] = data;
    }

    @Override
    public void publish(long from, long to) {
      if (to < from) throw new IllegalArgumentException();
      for (long i = from; i <= to; i++) availability.lazySet(((int) i) & mask, (int) (i >>> availabilityShift));
      if (onPublish != null) onPublish.run();
    }

    public @Nonnull Consumer<E> subscribe(Cursor... after) {
      final Consumer.BatchingConsumer<E> result = Consumer.batching(this, after);
      gate.invite(cursor, result.cursor());
      return result;
    }

    @Override
    public @Nonnull Consumer<E>[] subscribe(int amount, Cursor... after) {
      if (amount < 1) throw new IllegalArgumentException();
      final Consumer.ParallelConsumer<E>[] result = Consumer.parallel(this, amount, after);
      gate.invite(cursor, result[0].cursor());
      return result;
    }

    @Override
    public boolean unsubscribe(Consumer<E> consumer) {
      return gate.expel(consumer.cursor());
    }

    @Override
    public long lastPublished(long from, long to) {
      for (long i = from; i <= to; i++) {
        if (availability.get(((int) i) & mask) != (int) (i >>> availabilityShift)) return i - 1;
      }
      return to;
    }

    @Override
    public @Nonnull Cursor cursor() {
      return cursor;
    }
  }

}
