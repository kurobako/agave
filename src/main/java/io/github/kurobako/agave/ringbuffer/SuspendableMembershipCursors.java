package io.github.kurobako.agave.ringbuffer;

import sun.misc.Contended;

import javax.annotation.Nonnull;

import static java.lang.Math.min;

@Contended
final class SuspendableMembershipCursors extends Cursor.Write {
  private final @Nonnull Cursor.Write primary;
  private final @Nonnull AtomicCursor[] secondary;

  SuspendableMembershipCursors(Cursor.Write primary, AtomicCursor[] secondary) {
    this.primary = primary;
    this.secondary = secondary;
  }

  @Override
  public long readVolatile() {
    long result = primary.readVolatile();
    long val;
    for (AtomicCursor c : secondary) {
      val = c.readVolatile();
      result = min(result, val);
    }
    return result;
  }

  @Override
  public void writeOrdered(long value) {
    primary.writeOrdered(value);
    long old;
    boolean done;
    for (AtomicCursor c : secondary) {
      do {
        old = c.readVolatile();
        if (old == Long.MAX_VALUE) break;
        done = c.compareAndSwap(old, value);
      } while (!done);
    }
  }

  void suspendMembership(int index) {
    assert 0 < index && index < secondary.length;
    secondary[index].writeOrdered(Long.MAX_VALUE);
  }

  void resumeMembership(final int index) {
    assert 0 < index && index < secondary.length;
    secondary[index].writeOrdered(primary.readVolatile());
  }
}
