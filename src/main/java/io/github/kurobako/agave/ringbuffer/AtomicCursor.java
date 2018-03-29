package io.github.kurobako.agave.ringbuffer;

import sun.misc.Contended;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

@Contended
final class AtomicCursor extends Cursor.Write {
  private static final @Nonnull AtomicLongFieldUpdater<AtomicCursor> VALUE_UPDATER = AtomicLongFieldUpdater.newUpdater(AtomicCursor.class, "value");

  @SuppressWarnings("FieldCanBeLocal")
  private volatile long value = -1;

  @Override
  public long readVolatile() {
    return value;
  }

  @Override
  public void writeOrdered(final long value) {
    VALUE_UPDATER.lazySet(this, value);
  }

  void writeVolatile(final long value) {
    VALUE_UPDATER.set(this, value);
  }

  boolean compareAndSwap(final long expected, final long updated) {
    return VALUE_UPDATER.compareAndSet(this, expected, updated);
  }

}
