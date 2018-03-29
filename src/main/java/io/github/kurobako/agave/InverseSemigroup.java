package io.github.kurobako.agave;

import javax.annotation.Nonnull;

public interface InverseSemigroup<E extends InverseSemigroup<E>> extends Semigroup<E> {

  @Nonnull E inverse();

}
