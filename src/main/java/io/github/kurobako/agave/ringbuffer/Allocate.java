package io.github.kurobako.agave.ringbuffer;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface Allocate<E> {

  @Nonnull E allocate();

}
