package io.github.kurobako.agave.ringbuffer;

import sun.misc.Contended;

import javax.annotation.Nonnull;

import static java.lang.Math.min;

@Contended
final class ConstantMembershipCursors extends Cursor {
  private final @Nonnull Cursor primary;
  private final @Nonnull Cursor[] secondary;

  ConstantMembershipCursors(Cursor primary, Cursor[] secondary) {
    assert 0 < secondary.length;
    this.primary = primary;
    this.secondary = secondary;
  }

  @Override
  public long readVolatile() {
    long result = primary.readVolatile();
    for (Cursor c : secondary) result = min(result, c.readVolatile());
    return result;
  }

}
