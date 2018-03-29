package io.github.kurobako.agave.ringbuffer;

@FunctionalInterface
public interface Consume<E> {

  boolean consume(E data, boolean more);

}
