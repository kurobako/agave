package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import static io.github.kurobako.agave.Either.left;
import static io.github.kurobako.agave.Either.right;
import static io.github.kurobako.agave.Sequence.sequence;

@Immutable
public abstract class Trampoline<A> implements Value<A> {
  private Trampoline() {}

  public abstract @Nonnull Either<Trampoline<A>, A> resume();

  @Override
  public @Nonnull <B> Trampoline<B> map(Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return flatMap(a -> new More<>(More.UNIT, u -> new Done<>(function.apply(a))));
  }

  @Override
  public @Nonnull <B> Trampoline<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
    //noinspection ConstantConditions
    if (kleisli == null) throw new NullPointerException("kleisli");
    return new More<>(this, kleisli);
  }

  public abstract @Nonnull <B, C> Trampoline<C> zip(final Trampoline<B> trampoline, final BiFunction<? super A, ? super B, ? extends C> function);

  abstract @Nonnull <X> X match(Function<? super Done<A>, ? extends X> done, Function<? super More<?, A>, ? extends X> right);

  abstract boolean isDone();

  public static @Nonnull <A> Trampoline<A> done(final A value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new Done<>(value);
  }

  public static @Nonnull <A> Trampoline<A> delay(final Value<A> value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new More<>(More.UNIT, unit -> new Done<>(value.get()));
  }

  public static @Nonnull <A> Trampoline<A> suspend(final Value<Trampoline<A>> value) {
    //noinspection ConstantConditions
    if (value == null) throw new NullPointerException("value");
    return new More<>(More.UNIT, unit -> value.get());
  }

  private static final class Done<A> extends Trampoline<A> {
    private final @Nonnull A value;

    Done(final A value) {
      this.value = value;
    }

    @Override
    public @Nonnull
    A get() {
      return value;
    }

    @Override
    public @Nonnull Either<Trampoline<A>, A> resume() {
      return right(value);
    }

    @Override
    public @Nonnull <B, C> Trampoline<C> zip(final Trampoline<B> trampoline, final BiFunction<? super A, ? super B, ? extends C> function) {
      //noinspection ConstantConditions
      if (trampoline == null) throw new NullPointerException("trampoline");
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return new More<>(trampoline, b -> done(function.apply(get(), b)));
    }

    @Override
    @Nonnull <X> X match(Function<? super Done<A>, ? extends X> done, Function<? super More<?, A>, ? extends X> right) {
      return done.apply(this);
    }

    @Override
    public boolean isDone() {
      return true;
    }
  }

  private static final class More<O, A> extends Trampoline<A> {
    private static final @Nonnull
    Done<Unit> UNIT = new Done<>(Unit.INSTANCE);

    private final @Nonnull Trampoline<O> previous;
    private final @Nonnull Value.Kleisli<? super O, ? extends A> transform;

    More(final Trampoline<O> previous, final Value.Kleisli<? super O, ? extends A> transform) {
      this.previous = previous;
      this.transform = transform;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull A get() {
      Value<?> current = this;
      Sequence<Kleisli<Object, Object>> stack = sequence();
      A result = null;
      while (result == null) {
        if (!(current instanceof Trampoline) || ((Trampoline<?>) current).isDone() ) {
          final A value = (A) current.get();
          if (stack.isEmpty()) {
            result = value;
          } else {
            current = stack.first().apply(value);
            stack = stack.deleteFirst();
          }
        } else {
          final More more = (More<?, ?>) current;
          current = more.previous;
          stack = stack.push(more.transform);
        }
      }
      return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull Either<Trampoline<A>, A> resume() {
      return previous.match(done -> left((Trampoline<A>) transform.apply(done.value)), more -> left(more.flatMap(transform)));
    }

    @Override
    public @Nonnull <B, C> Trampoline<C> zip(final Trampoline<B> trampoline, final BiFunction<? super A, ? super B, ? extends C> function) {
      //noinspection ConstantConditions
      if (trampoline == null) throw new NullPointerException("trampoline");
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return new More<>(trampoline, b -> this.map(a -> function.apply(a, b)));
    }

    @Override
    @Nonnull <X> X match(Function<? super Done<A>, ? extends X> done, Function<? super More<?, A>, ? extends X> right) {
      return right.apply(this);
    }

    @Override
    public boolean isDone() {
      return false;
    }
  }

}
