package io.github.kurobako.agave.ringbuffer;

public abstract class Cursor {

  public abstract long readVolatile();

  static abstract class Write extends Cursor {

    abstract void writeOrdered(long value);

  }
}
