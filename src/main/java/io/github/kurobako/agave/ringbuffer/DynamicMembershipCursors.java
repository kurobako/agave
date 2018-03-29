package io.github.kurobako.agave.ringbuffer;

import sun.misc.Contended;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.lang.Math.min;
import static java.util.Arrays.copyOf;

@Contended
final class DynamicMembershipCursors extends Cursor {
  private static final AtomicReferenceFieldUpdater<DynamicMembershipCursors, Cursor[]> MEMBERS_UPDATER = AtomicReferenceFieldUpdater.newUpdater(DynamicMembershipCursors.class, Cursor[].class, "members");

  private volatile @Nonnull Cursor[] members = new Cursor[0];

  @Override
  public long readVolatile() {
    long result = Long.MAX_VALUE;
    for (Cursor cursor : members) {
      result = min(result, cursor.readVolatile());
    }
    return result;
  }

  void invite(Cursor resetTo, Cursor.Write... newMembers) {
    Cursor[] current;
    Cursor[] updated;
    long resetValue;
    int index;
    do {
      current = members;
      updated = copyOf(current, current.length + newMembers.length);
      index = current.length;
      resetValue = resetTo.readVolatile();
      for (Cursor.Write c : newMembers) {
        c.writeOrdered(resetValue);
        updated[index++] = c;
      }
    } while (!MEMBERS_UPDATER.compareAndSet(this, current, updated));
    resetValue = resetTo.readVolatile();
    for (Cursor.Write c : newMembers) {
      c.writeOrdered(resetValue);
    }
  }

  boolean expel(Cursor member) {
    Cursor[] current;
    Cursor[] updated;
    int deleteAt = -1;
    do {
      current = members;
      for (Cursor c : current) {
        if (c == member) {
          deleteAt++;
        }
      }
      if (deleteAt == -1) return false;
      updated = new Cursor[current.length - deleteAt + 1];
      int updatedIndex = 0;
      for (final Cursor c : current) {
        if (c != member) {
          updated[updatedIndex++] = c;
        }
      }
    } while (!MEMBERS_UPDATER.compareAndSet(this, current, updated));
    return true;
  }

}
