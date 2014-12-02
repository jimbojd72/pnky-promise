/*
 * Licensed to Jive Communications Inc. under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * Jive Communications Inc. licenses this file to You under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jive.foss.pnky;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.jive.foss.commons.concurrent.ImmediateExecutor;
import com.jive.foss.commons.function.CombinedException;
import com.jive.foss.commons.function.ThrowingBiConsumer;
import com.jive.foss.commons.function.ThrowingBiFunction;
import com.jive.foss.commons.function.ThrowingConsumer;
import com.jive.foss.commons.function.ThrowingFunction;
import com.jive.foss.commons.function.ThrowingRunnable;
import com.jive.foss.commons.function.ThrowingSupplier;

/**
 * Default implementation of a {@link PnkyPromise} that also provides some useful methods for
 * initiating a chain of asynchronous actions.
 *
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pnky<V> extends AbstractFuture<V> implements PnkyPromise<V>
{
  private static final int WAITING = 0;
  private static final int RUNNING = 1;
  private static final int CANCELLING = 2;

  private final AtomicInteger state = new AtomicInteger(WAITING);

  /**
   * Completes the promise successfully with {@code value}.
   *
   * @param value
   *          the value used to complete the promise
   *
   * @return true if this call completed the promise
   */
  public boolean resolve(final V value)
  {
    return super.set(value);
  }

  /**
   * Completes the promise successfully with a {@code null} value.
   *
   * @return true if this call completed the promise
   */
  public boolean resolve()
  {
    return super.set(null);
  }

  /**
   * Completes the promise exceptionally with {@code error}.
   *
   * @param error
   *          the error used to exceptionally complete the promise
   *
   * @return true if this call completed the promise
   */
  public boolean reject(@Nonnull final Throwable error)
  {
    return super.setException(error);
  }

  @Override
  public PnkyPromise<V> alwaysAccept(final ThrowingConsumer<? super V> onSuccess,
      final ThrowingConsumer<Throwable> onFailure)
  {
    return alwaysAccept(onSuccess, onFailure, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> alwaysAccept(final ThrowingConsumer<? super V> onSuccess,
      final ThrowingConsumer<Throwable> onFailure, final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        notifyOnSuccess(pnky, onSuccess),
        notifyOnFailure(pnky, onFailure),
        executor);

    return pnky;
  }

  @Override
  public <O> PnkyPromise<O> alwaysTransform(final ThrowingFunction<? super V, O> onSuccess,
      final ThrowingFunction<Throwable, O> onFailure)
  {
    return alwaysTransform(onSuccess, onFailure, ImmediateExecutor.getInstance());
  }

  @Override
  public <O> PnkyPromise<O> alwaysTransform(final ThrowingFunction<? super V, O> onSuccess,
      final ThrowingFunction<Throwable, O> onFailure, final Executor executor)
  {
    final Pnky<O> pnky = create();

    addCallback(
        pnky,
        transformResult(pnky, onSuccess),
        transformFailure(pnky, onFailure),
        executor);

    return pnky;
  }

  @Override
  public <O> PnkyPromise<O> alwaysCompose(
      final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess,
      final ThrowingFunction<Throwable, PnkyPromise<O>> onFailure)
  {
    return alwaysCompose(onSuccess, onFailure, ImmediateExecutor.getInstance());
  }

  @Override
  public <O> PnkyPromise<O> alwaysCompose(
      final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess,
      final ThrowingFunction<Throwable, PnkyPromise<O>> onFailure, final Executor executor)
  {
    final Pnky<O> pnky = create();

    addCallback(
        pnky,
        composeResult(pnky, onSuccess),
        composeFailure(pnky, onFailure),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> alwaysAccept(final ThrowingBiConsumer<? super V, Throwable> handler)
  {
    return alwaysAccept(handler, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> alwaysAccept(final ThrowingBiConsumer<? super V, Throwable> handler,
      final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        notifyOnSuccess(pnky, new ThrowingConsumer<V>()
        {
          @Override
          public void accept(final V result) throws Throwable
          {
            handler.accept(result, null);
          }
        }),
        notifyOnFailure(pnky, new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Throwable
          {
            handler.accept(null, error);
          }
        }),
        executor);

    return pnky;
  }

  @Override
  public <O> PnkyPromise<O> alwaysTransform(
      final ThrowingBiFunction<? super V, Throwable, O> handler)
  {
    return alwaysTransform(handler, ImmediateExecutor.getInstance());
  }

  @Override
  public <O> PnkyPromise<O> alwaysTransform(
      final ThrowingBiFunction<? super V, Throwable, O> handler,
      final Executor executor)
  {
    final Pnky<O> pnky = create();

    addCallback(
        pnky,
        transformResult(pnky, new ThrowingFunction<V, O>()
        {
          @Override
          public O apply(final V result) throws Throwable
          {
            return handler.apply(result, null);
          }
        }),
        transformFailure(pnky, new ThrowingFunction<Throwable, O>()
        {
          @Override
          public O apply(final Throwable error) throws Throwable
          {
            return handler.apply(null, error);
          }
        }),
        executor);

    return pnky;
  }

  @Override
  public <O> PnkyPromise<O> alwaysCompose(
      final ThrowingBiFunction<? super V, Throwable, PnkyPromise<O>> handler)
  {
    return alwaysCompose(handler, ImmediateExecutor.getInstance());
  }

  @Override
  public <O> PnkyPromise<O> alwaysCompose(
      final ThrowingBiFunction<? super V, Throwable, PnkyPromise<O>> handler,
      final Executor executor)
  {
    final Pnky<O> pnky = create();

    addCallback(
        pnky,
        composeResult(pnky, new ThrowingFunction<V, PnkyPromise<O>>()
        {
          @Override
          public PnkyPromise<O> apply(final V result) throws Throwable
          {
            return handler.apply(result, null);
          }
        }),
        composeFailure(pnky, new ThrowingFunction<Throwable, PnkyPromise<O>>()
        {
          @Override
          public PnkyPromise<O> apply(final Throwable error) throws Throwable
          {
            return handler.apply(null, error);
          }
        }),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> alwaysRun(final ThrowingRunnable runnable)
  {
    return alwaysRun(runnable, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> alwaysRun(final ThrowingRunnable runnable, final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        runAndPassThroughResult(pnky, runnable),
        runAndPassThroughFailure(runnable, pnky),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> thenAccept(final ThrowingConsumer<? super V> onSuccess)
  {
    return thenAccept(onSuccess, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> thenAccept(final ThrowingConsumer<? super V> onSuccess,
      final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        notifyOnSuccess(pnky, onSuccess),
        passThroughException(pnky),
        executor);

    return pnky;
  }

  @Override
  public <O> PnkyPromise<O> thenTransform(final ThrowingFunction<? super V, O> onSuccess)
  {
    return thenTransform(onSuccess, ImmediateExecutor.getInstance());
  }

  @Override
  public <O> PnkyPromise<O> thenTransform(final ThrowingFunction<? super V, O> onSuccess,
      final Executor executor)
  {
    final Pnky<O> pnky = create();

    addCallback(
        pnky,
        transformResult(pnky, onSuccess),
        passThroughException(pnky),
        executor);

    return pnky;
  }

  @Override
  public <O> PnkyPromise<O> thenCompose(
      final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess)
  {
    return thenCompose(onSuccess, ImmediateExecutor.getInstance());
  }

  @Override
  public <O> PnkyPromise<O> thenCompose(
      final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess,
      final Executor executor)
  {
    final Pnky<O> pnky = create();

    addCallback(
        pnky,
        composeResult(pnky, onSuccess),
        passThroughException(pnky),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> thenRun(final ThrowingRunnable runnable)
  {
    return thenRun(runnable, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> thenRun(final ThrowingRunnable runnable, final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        runAndPassThroughResult(pnky, runnable),
        passThroughException(pnky),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> onFailure(final ThrowingConsumer<Throwable> onFailure)
  {
    return onFailure(onFailure, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> onFailure(final ThrowingConsumer<Throwable> onFailure,
      final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        passThroughResult(pnky),
        notifyOnFailure(pnky, onFailure),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> withFallback(final ThrowingFunction<Throwable, V> onFailure)
  {
    return withFallback(onFailure, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> withFallback(final ThrowingFunction<Throwable, V> onFailure,
      final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        passThroughResult(pnky),
        transformFailure(pnky, onFailure),
        executor);

    return pnky;
  }

  @Override
  public PnkyPromise<V> composeFallback(
      final ThrowingFunction<Throwable, PnkyPromise<V>> onFailure)
  {
    return composeFallback(onFailure, ImmediateExecutor.getInstance());
  }

  @Override
  public PnkyPromise<V> composeFallback(
      final ThrowingFunction<Throwable, PnkyPromise<V>> onFailure, final Executor executor)
  {
    final Pnky<V> pnky = create();

    addCallback(
        pnky,
        passThroughResult(pnky),
        composeFailure(pnky, onFailure),
        executor);

    return pnky;
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning)
  {
    // Shield any other action from occurring while we are cancelling
    if (state.compareAndSet(WAITING, CANCELLING))
    {
      if (!super.cancel(false))
      {
        // This should not ever happen
        throw new IllegalStateException(
            "Unable to mark promise as cancelled after changing Pnky state");
      }

      return true;
    }
    return false;
  }

  @Override
  public boolean cancel()
  {
    return cancel(false);
  }

  private void addCallback(final Pnky<?> pnky, final ThrowingConsumer<V> onSuccess,
      final ThrowingConsumer<Throwable> onFailure, final Executor executor)
  {
    Futures.addCallback(this, new FutureCallback<V>()
    {
      @Override
      public void onSuccess(@Nullable final V result)
      {
        if (pnky.state.compareAndSet(Pnky.WAITING, Pnky.RUNNING))
        {
          try
          {
            onSuccess.accept(result);
          }
          catch (final Throwable e)
          {
            pnky.reject(e);
          }
        }
      }

      @Override
      public void onFailure(@Nonnull final Throwable t)
      {
        if (pnky.state.compareAndSet(Pnky.WAITING, Pnky.RUNNING))
        {
          try
          {
            onFailure.accept(t);
          }
          catch (final Throwable e)
          {
            pnky.reject(e);
          }
        }
      }
    }, executor);
  }

  private ThrowingConsumer<V> passThroughResult(final Pnky<V> pnky)
  {
    return new ThrowingConsumer<V>()
    {
      @Override
      public void accept(final V input) throws Throwable
      {
        pnky.resolve(input);
      }
    };
  }

  private ThrowingConsumer<Throwable> passThroughException(final Pnky<?> pnky)
  {
    return new ThrowingConsumer<Throwable>()
    {
      @Override
      public void accept(final Throwable input) throws Throwable
      {
        pnky.reject(input);
      }
    };
  }

  private <O> ThrowingConsumer<Throwable> composeFailure(final Pnky<O> pnky,
      final ThrowingFunction<Throwable, PnkyPromise<O>> onFailure)
  {
    return new ThrowingConsumer<Throwable>()
    {
      @Override
      public void accept(final Throwable input) throws Throwable
      {
        onFailure.apply(input)
            .alwaysAccept(new ThrowingBiConsumer<O, Throwable>()
            {
              @Override
              public void accept(final O newValue, final Throwable error) throws Throwable
              {
                if (error != null)
                {
                  pnky.reject(error);
                }
                else
                {
                  pnky.resolve(newValue);
                }
              }
            });
      }
    };
  }

  private <O> ThrowingConsumer<Throwable> transformFailure(final Pnky<O> pnky,
      final ThrowingFunction<Throwable, O> onFailure)
  {
    return new ThrowingConsumer<Throwable>()
    {
      @Override
      public void accept(final Throwable input) throws Throwable
      {
        final O newValue = onFailure.apply(input);
        pnky.resolve(newValue);
      }
    };
  }

  private <O> ThrowingConsumer<V> composeResult(final Pnky<O> pnky,
      final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess)
  {
    return new ThrowingConsumer<V>()
    {
      @Override
      public void accept(final V input) throws Throwable
      {
        onSuccess.apply(input)
            .alwaysAccept(new ThrowingBiConsumer<O, Throwable>()
            {
              @Override
              public void accept(final O newValue, final Throwable error) throws Throwable
              {
                if (error != null)
                {
                  pnky.reject(error);
                }
                else
                {
                  pnky.resolve(newValue);
                }
              }
            });
      }
    };
  }

  private <O> ThrowingConsumer<V> transformResult(final Pnky<O> pnky,
      final ThrowingFunction<? super V, O> onSuccess)
  {
    return new ThrowingConsumer<V>()
    {
      @Override
      public void accept(final V input) throws Throwable
      {
        final O newValue = onSuccess.apply(input);
        pnky.resolve(newValue);
      }
    };
  }

  private ThrowingConsumer<V> notifyOnSuccess(final Pnky<V> pnky,
      final ThrowingConsumer<? super V> onSuccess)
  {
    return new ThrowingConsumer<V>()
    {
      @Override
      public void accept(final V input) throws Throwable
      {
        onSuccess.accept(input);
        pnky.resolve(input);
      }
    };
  }

  private ThrowingConsumer<Throwable> notifyOnFailure(final Pnky<V> pnky,
      final ThrowingConsumer<Throwable> onFailure)
  {
    return new ThrowingConsumer<Throwable>()
    {
      @Override
      public void accept(final Throwable input) throws Throwable
      {
        onFailure.accept(input);
        pnky.reject(input);
      }
    };
  }

  private ThrowingConsumer<V> runAndPassThroughResult(final Pnky<V> pnky,
      final ThrowingRunnable runnable)
  {
    return new ThrowingConsumer<V>()
    {
      @Override
      public void accept(final V input) throws Throwable
      {
        runnable.run();
        pnky.resolve(input);
      }
    };
  }

  private ThrowingConsumer<Throwable> runAndPassThroughFailure(
      final ThrowingRunnable runnable, final Pnky<V> pnky)
  {
    return new ThrowingConsumer<Throwable>()
    {
      @Override
      public void accept(final Throwable input) throws Throwable
      {
        runnable.run();
        pnky.reject(input);
      }
    };
  }

  // =======================
  // Public utility methods
  // =======================

  /**
   * Returns a new incomplete instance that may be completed at a later time.
   *
   * @return a new instance
   */
  public static <V> Pnky<V> create()
  {
    return new Pnky<>();
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes when the supplied operation completes,
   * executing the operation on the supplied executor. If the operation completes normally, the
   * returned future completes successfully. If the operation throws an exception, the returned
   * future completes exceptionally with the thrown exception.
   *
   * @param operation
   *          the operation to perform
   * @param executor
   *          the executor to process the action on
   *
   * @return a new {@link PnkyPromise future}
   */
  public static PnkyPromise<Void> runAsync(final ThrowingRunnable operation,
      final Executor executor)
  {
    final Pnky<Void> pnky = Pnky.create();

    executor.execute(new Runnable()
    {
      @Override
      public void run()
      {
        if (pnky.state.compareAndSet(WAITING, RUNNING))
        {
          try
          {
            operation.run();
            pnky.resolve(null);
          }
          catch (final Throwable e)
          {
            pnky.reject(e);
          }
        }
      }
    });

    return pnky;
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes when the supplied operation completes,
   * executing the operation on the supplied executor. If the operation completes normally, the
   * returned future completes successfully with the result of the operation. If the operation
   * throws an exception, the returned future completes exceptionally with the thrown exception.
   *
   * @param operation
   *          the operation to perform
   * @param executor
   *          the executor to process the operation on
   *
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> supplyAsync(final ThrowingSupplier<V> operation,
      final Executor executor)
  {
    final Pnky<V> pnky = Pnky.create();

    executor.execute(new Runnable()
    {
      @Override
      public void run()
      {
        if (pnky.state.compareAndSet(WAITING, RUNNING))
        {
          try
          {
            final V value = operation.get();
            pnky.resolve(value);
          }
          catch (final Throwable e)
          {
            pnky.reject(e);
          }
        }
      }
    });

    return pnky;
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes when the future returned by the
   * supplied operation completes, executing the operation on the supplied executor. If the
   * operation completes normally, the returned future completes with the result of the future
   * returned by the function. If the operation throws an exception, the returned future completes
   * exceptionally with the thrown exception.
   *
   * @param operation
   *          the operation to perform
   * @param executor
   *          the executor to process the operation on
   *
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> composeAsync(final ThrowingSupplier<PnkyPromise<V>> operation,
      final Executor executor)
  {
    final Pnky<V> pnky = Pnky.create();

    executor.execute(new Runnable()
    {
      @Override
      public void run()
      {
        if (pnky.state.compareAndSet(WAITING, RUNNING))
        {
          try
          {
            operation.get().alwaysAccept(new ThrowingBiConsumer<V, Throwable>()
            {
              @Override
              public void accept(final V result, final Throwable error) throws Throwable
              {
                if (error != null)
                {
                  pnky.reject(error);
                }
                else
                {
                  pnky.resolve(result);
                }
              }
            });
          }
          catch (final Throwable e)
          {
            pnky.reject(e);
          }
        }
      }
    });

    return pnky;
  }

  /**
   * Creates a new {@link PnkyPromise future} that is successfully completed with the supplied
   * value.
   *
   * @param <V>
   *          the type of future
   * @param value
   *          the value used to complete the future
   *
   * @return a new successfully completed {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> immediatelyComplete(final V value)
  {
    final Pnky<V> pnky = create();
    pnky.resolve(value);
    return pnky;
  }

  /**
   * Creates a new {@link PnkyPromise future} that is successfully completed with a {@code null}
   * value.
   *
   * @param <V>
   *          the type of future
   *
   * @return a new successfully completed {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> immediatelyComplete()
  {
    final Pnky<V> pnky = create();
    pnky.resolve(null);
    return pnky;
  }

  /**
   * Creates a new {@link PnkyPromise future} that is exceptionally completed with the supplied
   * error.
   *
   * @param <V>
   *          the type of future
   * @param e
   *          the cause used to complete the future exceptionally
   *
   * @return a new exceptionally completed {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> immediatelyFailed(@NonNull final Throwable e)
  {
    final Pnky<V> pnky = create();
    pnky.reject(e);
    return pnky;
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes successfully with the results of the
   * supplied futures that completed successfully, if and only if all of the supplied futures
   * complete successfully. The returned future completes exceptionally as soon as any of the
   * provided futures complete exceptionally.
   *
   * @param <V>
   *          the type of value for all promises
   * @param promises
   *          the promises to watch for completion
   *
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<List<V>> allFailingFast(
      final Iterable<? extends PnkyPromise<? extends V>> promises)
  {
    final Pnky<List<V>> pnky = Pnky.create();
    final ListenableFuture<List<V>> futureResults = Futures.allAsList(promises);

    Futures.addCallback(futureResults, new FutureCallback<List<V>>()
    {
      @Override
      public void onSuccess(@Nullable final List<V> result)
      {
        pnky.resolve(result);
      }

      @Override
      public void onFailure(@Nonnull final Throwable t)
      {
        pnky.reject(t);
      }
    });

    return pnky;
  }

  /**
   * See {@link #allFailingFast(Iterable)}
   */
  @SafeVarargs
  public static <V> PnkyPromise<List<V>> allFailingFast(final PnkyPromise<? extends V>... promises)
  {
    return allFailingFast(Lists.newArrayList(promises));
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes successfully with the results of the
   * supplied futures that completed successfully, if and only if all of the supplied futures
   * complete successfully. The returned future completes exceptionally with a
   * {@link CombinedException} if any of the provided futures complete exceptionally, but only when
   * all futures have been completed.
   * <p>
   * The returned future will be resolved with the values in the order that the future's were passed
   * to this method (not in completion order).
   * </p>
   * <p>
   * The {@link CombinedException} will contain a {@link CombinedException#getCauses() list} of
   * exceptions mapped to the order of the promises that were passed to this method. A {@code null}
   * value means that corresponding future was completed successfully.
   * </p>
   *
   * @param <V>
   *          the type of value for all promises
   * @param promises
   *          the set of promises to wait on
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<List<V>> all(
      final Iterable<? extends PnkyPromise<? extends V>> promises)
  {
    final Pnky<List<V>> pnky = Pnky.create();

    final int numberOfPromises = Iterables.size(promises);

    // Special case, no promises to wait for
    if (numberOfPromises == 0)
    {
      return Pnky.immediatelyComplete(Collections.<V> emptyList());
    }

    final AtomicInteger remaining = new AtomicInteger(numberOfPromises);
    @SuppressWarnings("unchecked")
    final V[] results = (V[]) new Object[numberOfPromises];
    final Throwable[] errors = new Throwable[numberOfPromises];
    final AtomicBoolean failed = new AtomicBoolean();

    int i = 0;
    for (final PnkyPromise<? extends V> promise : promises)
    {
      final int promiseNumber = i++;

      promise.alwaysAccept(new ThrowingBiConsumer<V, Throwable>()
      {
        @Override
        public void accept(final V result, final Throwable error) throws Throwable
        {
          results[promiseNumber] = result;
          errors[promiseNumber] = error;
          if (error != null)
          {
            failed.set(true);
          }

          if (remaining.decrementAndGet() == 0)
          {
            if (failed.get())
            {
              pnky.reject(new CombinedException(Arrays.asList(errors)));
            }
            else
            {
              pnky.resolve(Arrays.asList(results));
            }
          }
        }
      });
    }

    return pnky;
  }

  /**
   * See {@link #all(Iterable)}
   */
  @SafeVarargs
  public static <V> PnkyPromise<List<V>> all(final PnkyPromise<? extends V>... promises)
  {
    return all(Lists.newArrayList(promises));
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes successfully with the results of the
   * supplied futures that completed successfully if one or more of the supplied futures completes
   * successfully. The returned future completes exceptionally if all of the provided futures
   * complete exceptionally.
   *
   * @param <V>
   *          the type of value for all promises
   * @param promises
   *          the promises to watch for completion
   *
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<List<V>> any(
      final Iterable<? extends PnkyPromise<? extends V>> promises)
  {
    final Pnky<List<V>> pnky = Pnky.create();
    final ListenableFuture<List<V>> futureResults = Futures.successfulAsList(promises);

    Futures.addCallback(futureResults, new FutureCallback<List<V>>()
    {
      @Override
      public void onSuccess(@Nullable final List<V> result)
      {
        pnky.resolve(result);
      }

      @Override
      public void onFailure(@Nonnull final Throwable t)
      {
        pnky.reject(t);
      }
    });

    return pnky;
  }

  @SafeVarargs
  public static <V> PnkyPromise<List<V>> any(final PnkyPromise<? extends V>... promises)
  {
    return any(Lists.newArrayList(promises));
  }

  /**
   * Creates a new {@link PnkyPromise future} that completes successfully with the result of the
   * first supplied future that completes successfully if any of the supplied futures complete
   * successfully. The returned future completes exceptionally if all of the provided futures
   * complete exceptionally.
   *
   * @param <V>
   *          the type of value for all promises
   * @param promises
   *          the promises to watch for completion
   *
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> first(final Iterable<? extends PnkyPromise<? extends V>> promises)
  {
    final Pnky<V> pnky = Pnky.create();

    final AtomicInteger remaining = new AtomicInteger(Iterables.size(promises));

    for (final PnkyPromise<? extends V> promise : promises)
    {
      promise.alwaysAccept(new ThrowingBiConsumer<V, Throwable>()
      {
        @Override
        public void accept(final V result, final Throwable error) throws Throwable
        {
          if (error == null)
          {
            // May be called multiple times but the contract guarantees that only the first call
            // will set the value on the promise
            pnky.resolve(result);
          }
          else if (remaining.decrementAndGet() == 0)
          {
            pnky.reject(error);
          }
        }
      });
    }

    return pnky;
  }

  /**
   * See {@link #first(Iterable)}
   */
  @SafeVarargs
  public static <V> PnkyPromise<V> first(final PnkyPromise<? extends V>... promises)
  {
    return first(Lists.newArrayList(promises));
  }

  /**
   * Create a new {@link PnkyPromise future} that completes successfully with the result from the
   * provided {@code future} when it is complete. The returned future will complete exceptionally if
   * the provided {@code future} completes exceptionally.
   *
   * @param future
   *          the listenable future to wrap
   * @return a new {@link PnkyPromise future}
   */
  public static <V> PnkyPromise<V> from(final ListenableFuture<? extends V> future)
  {
    final Pnky<V> pnky = Pnky.create();

    Futures.addCallback(future, new FutureCallback<V>()
    {

      @Override
      public void onSuccess(final V result)
      {
        pnky.resolve(result);
      }

      @Override
      public void onFailure(@Nonnull final Throwable t)
      {
        pnky.reject(t);
      }
    });

    return pnky;
  }
}
