package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import static io.github.kurobako.agave.Function.compose;
import static io.github.kurobako.agave.Pair.pair;

@FunctionalInterface
public interface Store<I, A> {

  @Nonnull Pair<Function<? super I, ? extends A>, I> run();

  default @Nonnull I pos() {
    return run().second();
  }

  default @Nonnull A peek(final I index) {
    return run().first().apply(index);
  }

  default A peeks(final Function<? super I, ? extends I> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<Function<? super I, ? extends A>, I> fi = run();
    return fi.first().apply(function.apply(fi.second()));
  }

  default @Nonnull Store<I, A> seek(final I index) {
    //noinspection ConstantConditions
    if (index == null) throw new NullPointerException("index");
    final Pair<Function<? super I, ? extends A>, I> fi = run();
    return store(fi.first(), index);
  }

  default @Nonnull Store<I, A> seeks(final Function<? super I, ? extends I> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<Function<? super I, ? extends A>, I> fi = run();
    return store(fi.first(), function.apply(fi.second()));
  }

  default @Nonnull A get() {
    final Pair<Function<? super I, ? extends A>, I> fi = run();
    return fi.first().apply(fi.second());
  }

  default @Nonnull Store<I, Store<I, A>> duplicate() {
    return extend(arg -> arg);
  }

  default @Nonnull <B> Store<I, B> map(final Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<Function<? super I, ? extends A>, I> fi = run();
    return store(compose(fi.first(), function), fi.second());
  }

  default @Nonnull <B> Store<I, B> extend(final Function<? super Store<I, A>, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<Function<? super I, ? extends A>, I> fi = run();
    return store(index -> {
      Store<I, A> s = store(fi.first(), index);
      return function.apply(s);
    }, fi.second());
  }

  static @Nonnull <I, A> Store<I, A> store(final Function<? super I, ? extends A> function, final I index) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<Function<? super I, ? extends A>, I> fi = pair(function, index);
    return () -> fi;
  }

}
