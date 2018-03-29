/*
 * Copyright (C) 2017 Fedor Gavrilov
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

import static io.github.kurobako.agave.Pair.pair;

@FunctionalInterface
public interface State<S, A> {

  @Nonnull Pair<S, A> run(S initial);

  default @Nonnull S exec(final S initial) {
    return run(initial).first();
  }

  default @Nonnull A eval(final S initial) {
    return run(initial).second();
  }

  default @Nonnull State<S, A> with(final @Nonnull Function<? super S, ? extends S> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return s -> {
      final Pair<S, A> sa = run(s);
      return pair(function.apply(sa.first()), sa.second());
    };
  }

  default @Nonnull <B> State<S, B> map(final @Nonnull Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return s -> {
      final Pair<S, A> sa = run(s);
      return pair(sa.first(), function.apply(sa.second()));
    };
  }

  default @Nonnull <B> State<S, B> apply(final @Nonnull State<S, ? extends Function<? super A, ? extends B>> state) {
    //noinspection ConstantConditions
    if (state == null) throw new NullPointerException("state");
    return flatMap(a -> state.flatMap(f -> state(f.apply(a))));
  }

  default @Nonnull <B> State<S, B> flatMap(final @Nonnull Function<? super A, ? extends State<S, B>> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return s -> {
      final Pair<S, A> sa = State.this.run(s);
      return function.apply(sa.second()).run(sa.first());
    };
  }

  static @Nonnull <S> State<S, S> get() {
    return s -> pair(s, s);
  }

  static @Nonnull <S> State<S, Unit> put(final S s) {
    //noinspection ConstantConditions
    if (s == null) throw new NullPointerException("s");
    final Pair<S, Unit> su = pair(s, Unit.INSTANCE);
    return whatever -> su;
  }

  static @Nonnull <S> State<S, Unit> modify(final @Nonnull Function<? super S, ? extends S> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return s -> pair(function.apply(s), Unit.INSTANCE);
  }

  static @Nonnull
  <S, A> State<S, A> gets(final @Nonnull Function<? super S, ? extends A> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return s -> pair(s, function.apply(s));
  }

  static @Nonnull <S extends InverseSemigroup<S>> State<S, S> delta(final @Nonnull S current) {
    //noinspection ConstantConditions
    if (current == null) throw new NullPointerException("current");
    return s -> pair(current.inverse().append(s), current);
  }

  static @Nonnull <S, A> State<S, A> unwrap(final @Nonnull State<S, ? extends State<S, A>> state) {
    //noinspection ConstantConditions
    if (state == null) throw new NullPointerException("state");
    return state.flatMap(a -> a);
  }

  static @Nonnull <S, A> State<S, A> state(final A value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return s -> pair(s, value);
  }
}
