package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import static io.github.kurobako.agave.Trampoline.delay;

@Immutable
public final class Thunk<A> implements Value<A> {
  private volatile Trampoline<A> computation;
  private A result;

  private Thunk(Trampoline<A> computation) {
    this.computation = computation;
  }

  @Override
  public @Nonnull A get() {
    if (computation != null) {
      synchronized (this) {
        if (computation != null) {
          result = computation.get();
          computation = null;
        }
      }
    }
    return result;
  }

  @Override
  public @Nonnull <B> Thunk<B> map(Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return new Thunk<>(computation.map(function));
  }

  @Override
  @SuppressWarnings("unchecked")
  public @Nonnull <B> Thunk<B> flatMap(Value.Kleisli<? super A, ? extends B> kleisli) {
    //noinspection ConstantConditions
    if (kleisli == null) throw new NullPointerException("kleisli");
    return new Thunk<>((Trampoline<B>) computation.flatMap(kleisli::apply));
  }

  public static @Nonnull <A> Value<A> thunk(Value<A> computation) {
    //noinspection ConstantConditions
    if (computation == null) throw new NullPointerException("computation");
    return computation instanceof Trampoline ? new Thunk<>((Trampoline<A>) computation) : new Thunk<>(delay(computation));
  }
}
