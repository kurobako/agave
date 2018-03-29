package io.github.kurobako.agave;

import javax.annotation.Nonnull;

import static io.github.kurobako.agave.Pair.pair;

@FunctionalInterface
public interface Writer<O extends Semigroup<O>, A> {

  @Nonnull Pair<O, A> run();

  default @Nonnull O exec() {
    return run().first();
  }

  default @Nonnull Writer<O, Pair<O, A>> listen() {
    final Pair<O, A> initial = run();
    final Pair<O, Pair<O, A>> result = pair(initial.first(), initial);
    return () -> result;
  }

  default @Nonnull <P> Writer<O, Pair<P, A>> listens(final Function<? super O, ? extends P> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<O, A> initial = run();
    return () -> pair(initial.first(), pair(function.apply(initial.first()), initial.second()));
  }

  default @Nonnull Writer<O, A> censor(final Function<? super O, ? extends O> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<O, A> initial = run();
    return () -> pair(function.apply(initial.first()), initial.second());
  }

  default @Nonnull <B> Writer<O, B> map(final Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<O, A> initial = run();
    return () -> pair(initial.first(), function.apply(initial.second()));
  }

  default @Nonnull <B> Writer<O, B> apply(final Writer<O, ? extends Function<? super A, ? extends B>> writer) {
    //noinspection ConstantConditions
    if (writer == null) throw new NullPointerException("writer");
    final Pair<O, A> initial = run();
    final Pair<O, ? extends Function<? super A, ? extends B>> application = writer.run();
    return () -> pair(application.first().append(initial.first()), application.second().apply(initial.second()));
  }

  default @Nonnull <B> Writer<O, B> flatMap(final Function<? super A, ? extends Writer<O, B>> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    final Pair<O, A> initial = run();
    final Pair<O, B> returned = function.apply(initial.second()).run();
    final Pair<O, B> result = pair(initial.first().append(returned.first()), returned.second());
    return () -> result;
  }

  static @Nonnull <O extends Semigroup<O>, A> Writer<O, A> pass(final Writer<O, Pair<A, ? extends Function<? super O, ? extends O>>> writer) {
    //noinspection ConstantConditions
    if (writer == null) throw new NullPointerException("writer");
    final Pair<O, Pair<A, ? extends Function<? super O, ? extends O>>> initial = writer.run();
    final Pair<O, A> result = pair(initial.second().second().apply(initial.first()), initial.second().first());
    return () -> result;
  }

  static @Nonnull <O extends Semigroup<O>> Writer<O, Unit> tell(final O output) {
    //noinspection ConstantConditions
    if (output == null) throw new NullPointerException("output");
    return writer(output, Unit.INSTANCE);
  }

  static @Nonnull <O extends Semigroup<O>, A> Writer<O, A> writer(final O output, final A value) {
    //noinspection ConstantConditions
    if (output == null) throw new NullPointerException("output");
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    final Pair<O, A> result = pair(output, value);
    return () -> result;
  }

  static @Nonnull <O extends Semigroup<O>, A> Writer<O, A> unwrap(final Writer<O, ? extends Writer<O, A>> writer) {
    //noinspection ConstantConditions
    if (writer == null) throw new NullPointerException("writer");
    return writer.flatMap(arg -> arg);
  }

}
