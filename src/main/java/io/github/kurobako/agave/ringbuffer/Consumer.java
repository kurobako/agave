package io.github.kurobako.agave.ringbuffer;

import sun.misc.Contended;

import javax.annotation.Nonnull;

public abstract class Consumer<E> {
  private Consumer() {}

  public abstract @Nonnull State consume(Consume<E> with);

  public abstract @Nonnull Cursor cursor();

  public enum State {
    IDLE, WORKING, GATING
  }

  static @Nonnull <E> BatchingConsumer<E> batching(RingBuffer<E> buffer, Cursor... gate) {
    return new BatchingConsumer<>(buffer, gate);
  }

  @SuppressWarnings("unchecked")
  static @Nonnull <E> ParallelConsumer<E>[] parallel(RingBuffer<E> buffer, int amount, Cursor... gate) {
    assert 0 < amount && amount < 65;
    final ParallelConsumer<E>[] result = new ParallelConsumer[amount];
    final AtomicCursor sharedCursor = new AtomicCursor();
    final AtomicCursor[] ownCursors = new AtomicCursor[amount];
    for (int i = 0; i < amount; i++) ownCursors[i] = new AtomicCursor();
    final SuspendableMembershipCursors groupCursor = new SuspendableMembershipCursors(sharedCursor, ownCursors);
    for (int i = 0; i < amount; i++) {
      groupCursor.suspendMembership(i);
      result[i] = new ParallelConsumer<>(buffer, groupCursor, i, sharedCursor, ownCursors[i], gate);
    }
    return result;
  }

  @Contended
  static final class BatchingConsumer<E> extends Consumer<E> {
    private final @Nonnull RingBuffer<E> buffer;
    private final @Nonnull Cursor gate;
    private final @Nonnull AtomicCursor cursor = new AtomicCursor();

    private BatchingConsumer(RingBuffer<E> buffer, Cursor... gate) {
      this.buffer = buffer;
      this.gate = (gate.length == 0) ? buffer.cursor() : new ConstantMembershipCursors(buffer.cursor(), gate);
    }

    @Override
    public @Nonnull State consume(Consume<E> with) {
      final long current = cursor.readVolatile();
      long next = current + 1;
      final long lastPublished = buffer.lastPublished(next, gate.readVolatile());
      if (next <= lastPublished) {
        long consumed = current;
        try {
          boolean shouldContinue;
          do {
            shouldContinue = with.consume(buffer.read(next), next != lastPublished);
            consumed = next;
            next++;
          } while (shouldContinue && next <= lastPublished);
        } finally {
          cursor.writeOrdered(consumed);
        }
        return State.WORKING;
      }
      if (next <= buffer.cursor().readVolatile()) return State.GATING;
      return State.IDLE;
    }

    @Override
    public @Nonnull Cursor.Write cursor() {
      return cursor;
    }
  }

  @Contended
  static final class ParallelConsumer<E> extends Consumer<E> {
    private final @Nonnull RingBuffer<E> buffer;
    private final @Nonnull Cursor gate;
    private final @Nonnull AtomicCursor sharedCursor;
    private final @Nonnull AtomicCursor ownCursor;
    private final @Nonnull SuspendableMembershipCursors groupCursor;
    private final int id;

    private ParallelConsumer(RingBuffer<E> buffer, SuspendableMembershipCursors groupCursor, int memberId, AtomicCursor sharedCursor, AtomicCursor ownCursor, Cursor... gate) {
      assert 0 <= memberId && memberId < 64;
      this.buffer = buffer;
      this.gate = (gate.length == 0) ? buffer.cursor() : new ConstantMembershipCursors(buffer.cursor(), gate);
      this.groupCursor = groupCursor;
      this.sharedCursor = sharedCursor;
      this.ownCursor = ownCursor;
      id = memberId;
    }

    @Override
    public @Nonnull State consume(Consume<E> with) {
      groupCursor.resumeMembership(id);
      try {
        boolean shouldContinue;
        long current;
        long next;
        long lastPublished;
        do {
          do {
            current = sharedCursor.readVolatile();
            next = current + 1;
            lastPublished = buffer.lastPublished(next, gate.readVolatile());
            if (lastPublished < next) return next < buffer.cursor().readVolatile() ? State.GATING : State.IDLE;
            ownCursor.writeOrdered(current);
          } while (!sharedCursor.compareAndSwap(current, next));
          shouldContinue = with.consume(buffer.read(next), false);
        } while (shouldContinue);
        return State.WORKING;
      } finally {
        groupCursor.suspendMembership(id);
      }
    }

    @Override
    public @Nonnull Cursor.Write cursor() {
      return groupCursor;
    }

  }

}
