package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.ExecutionException;

import static io.github.kurobako.agave.Pair.pair;
import static io.github.kurobako.agave.Task.Kleisli.compose;
import static io.github.kurobako.agave.Task.Kleisli.pure;

@ThreadSafe
public abstract class Task<A> {
  private Task() {}

  public final @Nonnull A perform() throws ExecutionException {
    try {
      return execute(arg -> arg, Task::rethrow);
    } catch (Exception e) {
      if (e instanceof RuntimeException) throw (RuntimeException) e;
      throw new ExecutionException(e);
    }
  }

  public final @Nonnull A performUnchecked() {
    try {
      return execute(Task::id, Task::rethrow);
    } catch (Exception e) {
      throw e instanceof RuntimeException ? (RuntimeException) e : new UncheckedExecutionException(e);
    }
  }

  abstract @Nonnull <R> R execute(Step<? super A, ? extends R> onSuccess, ExceptionHandler<? extends R> onFailure) throws Exception;

  public @Nonnull <B> Task<B> map(Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return new Transform<>(this, compose(pure(function), Task::task));
  }

  public @Nonnull <B> Task<B> mapAndRelease(Function<? super A, ? extends B> function) {
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return new Resource<>(() -> this.execute(function::apply, Task::rethrow), Task::nop);
  }

  public @Nonnull <B> Task<B> flatMap(Kleisli<? super A, ? extends B> kleisli) {
    //noinspection ConstantConditions
    if (kleisli == null) throw new NullPointerException("kleisli");
    return new Transform<>(this, kleisli);
  }

  public @Nonnull <B> Task<B> then(Task<B> task) {
    //noinspection ConstantConditions
    if (task == null) throw new NullPointerException("task");
    return new Transform<>(this, ignored -> task);
  }

  public @Nonnull Task<A> then(Procedure<? super A> procedure) {
    //noinspection ConstantConditions
    if (procedure == null) throw new NullPointerException("procedure");
    return new Transform<>(this, a -> { procedure.run(a); return this; });
  }

  public @Nonnull <B, C> Task<C> zip(Task<B> task, BiFunction<? super A, ? super B, ? extends C> function) {
    //noinspection ConstantConditions
    if (task == null) throw new NullPointerException("task");
    //noinspection ConstantConditions
    if (function == null) throw new NullPointerException("function");
    return flatMap(a -> task.flatMap(b -> task(function.apply(a, b))));
  }

  public @Nonnull <B> Task<Pair<A, B>> zip(Task<B> task) {
    //noinspection ConstantConditions
    if (task == null) throw new NullPointerException("task");
    return flatMap(a -> task.flatMap(b -> task(pair(a, b))));
  }

  public static @Nonnull <A> Task<A> throwing(Exception e) {
    //noinspection ConstantConditions
    if (e == null) throw new NullPointerException("e");
    return new Resource<>(() -> { throw e; }, Task::nop);
  }

  public static @Nonnull <A> Task<A> task(A initial) {
    //noinspection ConstantConditions
    if (initial == null) throw new NullPointerException("initial");
    return new Resource<>(() -> initial, Task::nop);
  }

  public static @Nonnull <A> Task<A> task(Acquire<? extends A> acquire, final Release<? super A> release) {
    //noinspection ConstantConditions
    if (acquire == null) throw new NullPointerException("acquire");
    //noinspection ConstantConditions
    if (release == null) throw new NullPointerException("release");
    return new Resource<>(acquire, release);
  }

  @SuppressWarnings("unchecked")
  public static @Nonnull <A, B> Task<B> task(Acquire<? extends A> acquire, Step<? super A, ? extends B> effect, Release<? super A> release) {
    //noinspection ConstantConditions
    if (acquire == null) throw new NullPointerException("acquire");
    //noinspection ConstantConditions
    if (effect == null) throw new NullPointerException("effect");
    //noinspection ConstantConditions
    if (release == null) throw new NullPointerException("release");
    return new Transform<>(new Resource<>(acquire, release), a -> new Resource<>(() -> effect.step(a), Task::nop));
  }

  public static @Nonnull <A> Task<A> unwrap(Task<? extends Task<A>> task) {
    //noinspection ConstantConditions
    if (task == null) throw new NullPointerException("task");
    return task.flatMap(a -> a);
  }

  private static @Nonnull <R> R rethrow(final Exception e) throws Exception {
    throw e;
  }

  private static @Nonnull<R> R id(final R value) {
    return value;
  }

  private static <R> void nop(final R value) {}

  private static final class Resource<A> extends Task<A> {
    final Acquire<? extends A> acquire;
    final Release<? super A> release;

    Resource(final Acquire<? extends A> acquire, final Release<? super A> release) {
      this.acquire = acquire;
      this.release = release;
    }

    @Override
    @Nonnull <R> R execute(Step<? super A, ? extends R> onSuccess, ExceptionHandler<? extends R> onFailure) throws Exception {
      A resource = null;
      R result = null;
      Exception failure = null;
      try {
        resource = acquire.acquire();
        result = onSuccess.step(resource);
      } catch (Exception e) {
        failure = e;
      } finally {
        if (resource != null) {
          try {
            release.release(resource);
          } catch (Exception e) {
            if (failure != null) failure.addSuppressed(e);
            else failure = e;
          }
        }
      }
      return failure != null ? onFailure.handle(failure) : result;
    }
  }

  private static final class Transform<Z, A> extends Task<A> {
    final Task<? extends Z> monad;
    final Kleisli<? super Z, ? extends A> kleisli;

    Transform(final Task<? extends Z> monad, final Kleisli<? super Z, ? extends A> kleisli) {
      this.monad = monad;
      this.kleisli = kleisli;
    }

    @Override
    @Nonnull <R> R execute(Step<? super A, ? extends R> onSuccess, ExceptionHandler<? extends R> onFailure) throws Exception {
      return monad.execute(z -> kleisli.apply(z).execute(onSuccess, Task::rethrow), onFailure);
    }
  }

  private interface ExceptionHandler<R> {
    @Nonnull R handle(Exception exception) throws Exception;
  }

  @FunctionalInterface
  public interface Acquire<A> {
    @Nonnull A acquire() throws Exception;
  }

  @FunctionalInterface
  public interface Step<A, B> {
    @Nonnull B step(A value) throws Exception;
  }

  @FunctionalInterface
  public interface Release<A> {
    void release(A resource) throws Exception;
  }

  @FunctionalInterface
  public interface Kleisli<A, B> {
    @Nonnull Task<B> apply(A arg);

    static @Nonnull <A, B, C> Kleisli<Pair<A, C>, Pair<B, C>> first(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ac -> kleisli.apply(ac.first()).map(b -> pair(b, ac.second()));
    }

    static @Nonnull <Z, A, B> Kleisli<Pair<Z, A>, Pair<Z, B>> second(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return za -> kleisli.apply(za.second()).map(b -> pair(za.first(), b));
    }

    static @Nonnull <A, B, C> Kleisli<Either<A, C>, Either<B, C>> left(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ac -> ac.either(a -> kleisli.apply(a).map(Either::left), c -> task(Either.right(c)));
    }

    static @Nonnull <A, B, C> Kleisli<Either<C, A>, Either<C, B>> right(Kleisli<? super A, ? extends B> kleisli) {
      //noinspection ConstantConditions
      if (kleisli == null) throw new NullPointerException("kleisli");
      return ca -> ca.either(c -> task(Either.left(c)), a -> kleisli.apply(a).map(Either::right));
    }

    static @Nonnull <A, B, C, D> Kleisli<Either<A, C>, Either<B, D>> sum(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> ac.either(a -> first.apply(a).map(Either::left), c -> second.apply(c).map(Either::right));
    }

    static @Nonnull <A, B, C, D> Kleisli<Pair<A, C>, Pair<B, D>> product(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends D> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> first.apply(ac.first()).zip(second.apply(ac.second()), Pair::pair);
    }

    @SuppressWarnings("unchecked")
    static @Nonnull <A, B, C> Kleisli<Either<A, C>, B> fanIn(Kleisli<? super A, ? extends B> first, Kleisli<? super C, ? extends B> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return ac -> (Task<B>) ac.either(first::apply, second::apply);
    }

    static @Nonnull <A, B, C> Kleisli<A, Pair<B, C>> fanOut(Kleisli<? super A, ? extends B> first, Kleisli<? super A, ? extends C> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).zip(second.apply(a), Pair::pair);
    }

    static @Nonnull <A, B, C> Kleisli<A, C> compose(Kleisli<? super A, ? extends B> first, Kleisli<? super B, ? extends C> second) {
      //noinspection ConstantConditions
      if (first == null) throw new NullPointerException("first");
      //noinspection ConstantConditions
      if (second == null) throw new NullPointerException("second");
      return a -> first.apply(a).flatMap(second);
    }

    static @Nonnull <A, B> Kleisli<A, B> pure(Function<? super A, ? extends B> function) {
      //noinspection ConstantConditions
      if (function == null) throw new NullPointerException("function");
      return a -> task(function.apply(a));
    }
  }
}
