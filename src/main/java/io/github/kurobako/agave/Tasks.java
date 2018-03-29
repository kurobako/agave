package io.github.kurobako.agave;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static io.github.kurobako.agave.Task.task;

public final class Tasks {
  private Tasks() {}

  public static @Nonnull Task<Lock> lock(Lock lock) {
    //noinspection ConstantConditions
    if (lock == null) throw new NullPointerException("lock");
    return task(() -> { lock.lock(); return lock; }, Lock::unlock);
  }

  public static @Nonnull Task<Lock> lockInterruptibly(Lock lock) {
    //noinspection ConstantConditions
    if (lock == null) throw new NullPointerException("lock");
    return task(() -> { lock.lockInterruptibly(); return lock; }, Lock::unlock);
  }

  public static @Nonnull Task<Lock> tryLock(Lock lock, long time, TimeUnit timeUnit) {
    //noinspection ConstantConditions
    if (lock == null) throw new NullPointerException("lock");
    //noinspection ConstantConditions
    if (timeUnit == null) throw new NullPointerException("timeUnit");
    return task(() -> { lock.tryLock(time, timeUnit); return lock; }, Lock::unlock);
  }

  public static @Nonnull <C extends AutoCloseable> Task<C> finallyClose(C closeable) {
    //noinspection ConstantConditions
    if (closeable == null) throw new NullPointerException("lock");
    return task(() -> closeable, AutoCloseable::close);
  }


}
