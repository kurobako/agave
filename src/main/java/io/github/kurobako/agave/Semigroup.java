package io.github.kurobako.agave;

import javax.annotation.Nonnull;


public interface Semigroup<E extends Semigroup<E>> {

  @Nonnull E append(E element);

}
