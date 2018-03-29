/*
 * Copyright (C) 2018 Fedor Gavrilov
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import static io.github.kurobako.agave.Function.compose;

@FunctionalInterface
public interface Reader<E, A> {

  @Nonnull A run(E environment);

  default @Nonnull <F> Reader<F, A> local(final Function<? super F, ? extends E> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return compose(function, this::run)::apply;
  }

  default @Nonnull Reader<E, A> scope(final E environment) {
    return whatever -> run(environment);
  }

  default @Nonnull <B> Reader<E, B> map(final Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Function<E, A> run = this::run;
    return compose(run, function)::apply;
  }

  default @Nonnull <B> Reader<E, B> apply(final Reader<E, ? extends Function<? super A, ? extends B>> reader) {
    //noinspection ConstantConditions
    if (reader == null) throw new NullPointerException("reader");
    return e -> reader.run(e).apply(run(e));
  }

  default @Nonnull <B> Reader<E, B> flatMap(final Function<? super A, ? extends Reader<E, B>> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return e -> function.apply(run(e)).run(e);
  }

  static @Nonnull <E> Reader<E, E> ask() {
    return e -> e;
  }

  static @Nonnull <E, A> Reader<E, A> reader(final Function<? super E, ? extends A> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return function::apply;
  }

  static @Nonnull <E, A> Reader<E, A> dereference(final Reader<E, ? extends Reader<E, A>> reader) {
    //noinspection ConstantConditions
    if (reader == null) throw new NullPointerException("reader");
    return reader.flatMap(a -> a);
  }

  static @Nonnull <E, A> Reader<E, A> reader(final A value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return whatever -> value;
  }
}
