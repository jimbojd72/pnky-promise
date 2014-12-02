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

import static com.jayway.awaitility.Awaitility.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Cleanup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.jive.foss.commons.concurrent.ImmediateExecutor;
import com.jive.foss.commons.function.CombinedException;
import com.jive.foss.commons.function.ThrowingBiConsumer;
import com.jive.foss.commons.function.ThrowingBiFunction;
import com.jive.foss.commons.function.ThrowingConsumer;
import com.jive.foss.commons.function.ThrowingFunction;
import com.jive.foss.commons.function.ThrowingRunnable;
import com.jive.foss.commons.function.ThrowingSupplier;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class PnkyTest
{
  private ExecutorService executor;

  @Before
  public void setup()
  {
    // TODO should use a test executor here that can validate that the executor was used.

    executor = Executors.newSingleThreadExecutor();

  }

  @After
  public void teardown()
  {
    executor.shutdownNow();
  }

  @Test
  public void testAlwaysPropagateBiConsumerSuccess() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();

    // Only on this test just to verify same thread behavior
    final AtomicBoolean invoked = new AtomicBoolean();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertEquals(1, (int) result);
            assertNull(error);
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            assertEquals(1, (int) result);
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            invoked.set(true);
          }
        });

    assertTrue(invoked.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testAlwaysPropagateError() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            throw new NumberFormatException();
          }
        }, ImmediateExecutor.getInstance())
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertNull(result);
            assertThat(error, instanceOf(NumberFormatException.class));
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        });

    assertEquals(0, badThings.get());
  }

  @Test
  public void testAlwaysTransformSuccess() throws Exception
  {
    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .alwaysTransform(new ThrowingBiFunction<Integer, Throwable, Integer>()
        {
          @Override
          public Integer apply(final Integer result, final Throwable error) throws Exception
          {
            assertEquals(1, (int) result);
            assertNull(error);
            return result + 1;
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertEquals(2, (int) result);
            assertNull(error);
          }
        });
  }

  @Test
  public void testAlwaysTransformFailure() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            throw new NumberFormatException();
          }
        }, ImmediateExecutor.getInstance())
        .alwaysTransform(new ThrowingBiFunction<Integer, Throwable, Integer>()
        {
          @Override
          public Integer apply(final Integer result, final Throwable error) throws Exception
          {
            throw new IllegalStateException();
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertNull(result);
            assertThat(error, instanceOf(IllegalStateException.class));
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .alwaysTransform(new ThrowingBiFunction<Integer, Throwable, Integer>()
        {
          @Override
          public Integer apply(final Integer result, final Throwable error) throws Exception
          {
            return 2;
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertNull(error);
            assertEquals(2, (int) result);
          }
        });

    assertEquals(0, badThings.get());
  }

  @Test
  public void testAlwaysComposeOnSuccess() throws Exception
  {
    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .alwaysCompose(new ThrowingBiFunction<Integer, Throwable, PnkyPromise<Integer>>()
        {
          @Override
          public PnkyPromise<Integer> apply(final Integer result, final Throwable error)
              throws Exception
          {
            assertEquals(1, (int) result);
            assertNull(error);
            return Pnky.immediatelyComplete(result + 1);
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertEquals(2, (int) result);
            assertNull(error);
          }
        });
  }

  @Test
  public void testAlwaysComposeFailure() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            throw new NumberFormatException();
          }
        }, ImmediateExecutor.getInstance())
        .alwaysCompose(new ThrowingBiFunction<Integer, Throwable, PnkyPromise<Integer>>()
        {
          @Override
          public PnkyPromise<Integer> apply(final Integer result, final Throwable error)
              throws Exception
          {
            throw new IllegalStateException();
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertNull(result);
            assertThat(error, instanceOf(IllegalStateException.class));
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .alwaysCompose(new ThrowingBiFunction<Integer, Throwable, PnkyPromise<Integer>>()
        {
          @Override
          public PnkyPromise<Integer> apply(final Integer result, final Throwable error)
              throws Exception
          {
            return Pnky.immediatelyComplete(2);
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertNull(error);
            assertEquals(2, (int) result);
          }
        })
        .alwaysCompose(new ThrowingBiFunction<Integer, Throwable, PnkyPromise<Integer>>()
        {
          @Override
          public PnkyPromise<Integer> apply(final Integer result, final Throwable error)
              throws Exception
          {
            return Pnky.immediatelyFailed(
                new IllegalArgumentException());
          }
        })
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            assertNull(result);
            assertThat(error, instanceOf(IllegalArgumentException.class));
          }
        });

    assertEquals(0, badThings.get());
  }

  @Test
  public void testPropagateIndividually() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            throw new NumberFormatException();
          }
        }, ImmediateExecutor.getInstance())
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            assertThat(error, instanceOf(NumberFormatException.class));
          }
        })
        .withFallback(new ThrowingFunction<Throwable, Integer>()
        {
          @Override
          public Integer apply(final Throwable error) throws Exception
          {
            return 1;
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            assertEquals(1, (int) result);
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            throw new IllegalStateException();
          }
        })
        .withFallback(new ThrowingFunction<Throwable, Integer>()
        {
          @Override
          public Integer apply(final Throwable error) throws Exception
          {
            assertThat(error, instanceOf(IllegalStateException.class));
            return 5;
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            assertEquals(5, (int) result);
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            badThings.incrementAndGet();
          }
        });

    assertEquals(0, badThings.get());
  }

  @Test
  public void testAcceptExceptionally() throws Exception
  {
    final PnkyPromise<Integer> future = Pnky.immediatelyComplete(-1);

    final AtomicInteger value = new AtomicInteger();

    future.thenAccept(new ThrowingConsumer<Integer>()
    {
      @Override
      public void accept(final Integer newValue) throws Exception
      {
        value.set(newValue);
      }
    }).get();

    assertEquals(-1, value.get());

    value.set(0);

    future.thenAccept(new ThrowingConsumer<Integer>()
    {
      @Override
      public void accept(final Integer newValue) throws Exception
      {
        value.set(newValue);
      }
    }).get();

    assertEquals(-1, value.get());

    value.set(0);

    future.thenAccept(new ThrowingConsumer<Integer>()
    {
      @Override
      public void accept(final Integer newValue) throws Exception
      {
        value.set(newValue);
      }
    }, executor).get();

    assertEquals(-1, value.get());

    value.set(0);

    try
    {
      future
          .thenAccept(new ThrowingConsumer<Integer>()
          {
            @Override
            public void accept(final Integer result) throws Exception
            {
              throw new TestException();
            }
          })
          .get();

      fail();
    }
    catch (final ExecutionException e)
    {
      assertEquals(TestException.class, e.getCause().getClass());
    }

    try
    {
      future
          .thenAccept(new ThrowingConsumer<Integer>()
          {
            @Override
            public void accept(final Integer result) throws Exception
            {
              throw new TestException();
            }
          }, ForkJoinPool.commonPool())
          .get();

      fail();
    }
    catch (final ExecutionException e)
    {
      assertEquals(TestException.class, e.getCause().getClass());
    }

    try
    {
      future
          .thenAccept(new ThrowingConsumer<Integer>()
          {
            @Override
            public void accept(final Integer result) throws Exception
            {
              throw new TestException();
            }
          }, executor)
          .get();

      fail();
    }
    catch (final ExecutionException e)
    {
      assertEquals(TestException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testAccept() throws Exception
  {
    final PnkyPromise<Integer> future = Pnky.immediatelyComplete(-1);

    final AtomicInteger value = new AtomicInteger();

    future
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer newValue) throws Exception
          {
            value.set(newValue);
          }
        })
        .get();

    assertEquals(-1, value.get());

    value.set(0);

    future
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer newValue) throws Exception
          {
            value.set(newValue);
          }
        })
        .get();

    assertEquals(-1, value.get());

    value.set(0);

    future
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer newValue) throws Exception
          {
            value.set(newValue);
          }
        }, executor)
        .get();

    assertEquals(-1, value.get());
  }

  @Test
  public void testAllWaitsForAll() throws Exception
  {
    final Pnky<Integer> toFinish = Pnky.create();
    final List<PnkyPromise<Integer>> promises = Arrays.asList(
        Pnky.<Integer> immediatelyFailed(new NumberFormatException()), toFinish);

    final AtomicReference<Throwable> throwable = new AtomicReference<>();
    Pnky.all(promises).alwaysAccept(new ThrowingBiConsumer<List<Integer>, Throwable>()
    {
      @Override
      public void accept(final List<Integer> result, final Throwable error) throws Exception
      {
        assertNull(result);
        throwable.set(error);
      }
    });

    assertNull(throwable.get());

    toFinish.resolve(1);

    assertThat(throwable.get(), instanceOf(CombinedException.class));
    assertThat(((CombinedException) throwable.get()).getCauses().get(0),
        instanceOf(NumberFormatException.class));
  }

  @Test
  public void testAllWithEmptySetOfPromises() throws Exception
  {
    final CountDownLatch invoked = new CountDownLatch(1);
    Pnky.all(Collections.<PnkyPromise<Void>> emptyList())
        .alwaysAccept(new ThrowingBiConsumer<List<Void>, Throwable>()
        {
          @Override
          public void accept(final List<Void> result, final Throwable error) throws Exception
          {
            invoked.countDown();
          }
        });

    assertTrue(invoked.await(10, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testAllInOrder() throws Exception
  {
    final Pnky<Integer> first = Pnky.create();
    final Pnky<Integer> second = Pnky.create();
    final CountDownLatch finished = new CountDownLatch(1);
    final AtomicReference<List<Integer>> results = new AtomicReference<>();
    Pnky.all(Arrays.asList(first, second))
        .thenAccept(new ThrowingConsumer<List<Integer>>()
        {
          @Override
          public void accept(final List<Integer> result) throws Exception
          {
            results.set(result);
            finished.countDown();
          }
        });

    second.resolve(2);

    assertNull(results.get());

    first.resolve(1);

    assertTrue(finished.await(10, TimeUnit.MILLISECONDS));

    assertNotNull(results.get());
    assertEquals(2, results.get().size());
    assertEquals(1, (int) results.get().get(0));
    assertEquals(2, (int) results.get().get(1));
  }

  @Test
  public void testAllFailingFast() throws Exception
  {
    final AtomicReference<Throwable> result = new AtomicReference<>();
    Pnky.allFailingFast(
        Arrays.asList(Pnky.immediatelyFailed(new NumberFormatException()), Pnky.create()))
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable newValue) throws Exception
          {
            result.set(newValue);
          }
        });

    assertThat(result.get(), instanceOf(NumberFormatException.class));
  }

  @Test
  public void testWrapListenableFutureSuccess() throws Exception
  {
    @Cleanup("shutdownNow")
    final ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    final AtomicBoolean started = new AtomicBoolean();
    final CountDownLatch latch = new CountDownLatch(1);

    final ListenableFuture<Boolean> future = executorService.submit(new Callable<Boolean>()
    {
      @Override
      public Boolean call() throws Exception
      {
        started.set(true);
        return latch.await(5, TimeUnit.SECONDS);
      }
    });

    final PnkyPromise<Boolean> pnky = Pnky.from(future);

    assertFalse(pnky.isDone());

    await().untilTrue(started);

    latch.countDown();

    await().until(new Callable<Boolean>()
    {
      @Override
      public Boolean call() throws Exception
      {
        return pnky.isDone();
      }
    });
    assertTrue(pnky.get());
  }

  @Test
  public void testWrapListenableFutureFailure() throws Exception
  {
    @Cleanup("shutdownNow")
    final
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    final AtomicBoolean started = new AtomicBoolean();
    final CountDownLatch latch = new CountDownLatch(1);

    final ListenableFuture<Boolean> future = executorService
        .submit(new Callable<Boolean>()
        {
          @Override
          public Boolean call() throws Exception
          {
            started.set(true);
            if (latch.await(5, TimeUnit.SECONDS))
            {
              throw new NumberFormatException();
            }
            else
            {
              throw new Exception();
            }
          }
        });

    final PnkyPromise<Boolean> pnky = Pnky.from(future);

    assertFalse(pnky.isDone());

    await().untilTrue(started);

    latch.countDown();

    await().until(new Callable<Boolean>()
    {
      @Override
      public Boolean call() throws Exception
      {
        return pnky.isDone();
      }
    });

    try
    {
      pnky.get(5, TimeUnit.SECONDS);
      fail();
    }
    catch (final ExecutionException e)
    {
      assertThat(e.getCause(), instanceOf(NumberFormatException.class));
    }
  }

  @Test
  public void testAlwaysRunSuccess() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invoked = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .alwaysRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            invoked.set(true);
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            assertEquals(1, (int) result);
            invokedDownstream.set(true);
          }
        });

    assertTrue(invoked.get());
    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testAlwaysRunFailure() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invoked = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .composeAsync(new ThrowingSupplier<PnkyPromise<Integer>>()
            {
              @Override
              public PnkyPromise<Integer> get() throws Exception
              {
                return Pnky.immediatelyFailed(new RuntimeException("Initial exception"));
              }
            },
            ImmediateExecutor.getInstance())
        .alwaysRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            invoked.set(true);
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            invokedDownstream.set(true);
            assertEquals("Initial exception", error.getMessage());
          }
        });

    assertTrue(invoked.get());
    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testAlwaysRunFailureReplacesSuccess() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invoked = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .alwaysRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            invoked.set(true);
            throw new RuntimeException("exception");
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            invokedDownstream.set(true);
            assertEquals("exception", error.getMessage());
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        });

    assertTrue(invoked.get());
    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testAlwaysRunFailureReplacesFailure() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invoked = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .composeAsync(new ThrowingSupplier<PnkyPromise<Integer>>()
        {
          @Override
          public PnkyPromise<Integer> get() throws Exception
          {
            return Pnky.immediatelyFailed(new RuntimeException("Initial exception"));
          }
        }, ImmediateExecutor.getInstance())
        .alwaysRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            invoked.set(true);
            throw new RuntimeException("Second exception");
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            invokedDownstream.set(true);
            assertEquals("Second exception", error.getMessage());
          }
        });

    assertTrue(invoked.get());
    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testThenRunSuccess() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invoked = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .thenRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            invoked.set(true);
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            assertEquals(1, (int) result);
            invokedDownstream.set(true);
          }
        });

    assertTrue(invoked.get());
    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testThenRunFailure() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .composeAsync(new ThrowingSupplier<PnkyPromise<Integer>>()
        {
          @Override
          public PnkyPromise<Integer> get() throws Exception
          {
            return Pnky.immediatelyFailed(new RuntimeException("Initial exception"));
          }
        }, ImmediateExecutor.getInstance())
        .thenRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            badThings.incrementAndGet();
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            invokedDownstream.set(true);
            assertEquals("Initial exception", error.getMessage());
          }
        });

    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testThenRunFailureReplacesSuccess() throws Exception
  {
    final AtomicInteger badThings = new AtomicInteger();
    final AtomicBoolean invoked = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();

    Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            return 1;
          }
        }, ImmediateExecutor.getInstance())
        .thenRun(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {
            invoked.set(true);
            throw new RuntimeException("exception");
          }
        })
        .onFailure(new ThrowingConsumer<Throwable>()
        {
          @Override
          public void accept(final Throwable error) throws Exception
          {
            invokedDownstream.set(true);
            assertEquals("exception", error.getMessage());
          }
        })
        .thenAccept(new ThrowingConsumer<Integer>()
        {
          @Override
          public void accept(final Integer result) throws Exception
          {
            badThings.incrementAndGet();
          }
        });

    assertTrue(invoked.get());
    assertTrue(invokedDownstream.get());
    assertEquals(0, badThings.get());
  }

  @Test
  public void testCancellationException() throws Exception
  {
    final AtomicBoolean isCancellationException = new AtomicBoolean();

    final Pnky<Integer> pnky = Pnky.create();

    pnky
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            isCancellationException.set(error instanceof CancellationException);
          }
        });

    pnky.cancel();

    assertTrue(isCancellationException.get());
  }

  @Test
  public void testCancelBeforeRunningTask() throws Exception
  {
    final AtomicBoolean invokedTask = new AtomicBoolean();
    final AtomicBoolean invokedDownstream = new AtomicBoolean();
    final AtomicBoolean isCancellationException = new AtomicBoolean();

    final CountDownLatch countDown = new CountDownLatch(1);

    final PnkyPromise<Integer> originalPromise = Pnky
        .supplyAsync(new ThrowingSupplier<Integer>()
        {
          @Override
          public Integer get() throws Exception
          {
            countDown.await();
            return 1;
          }
        }, executor);

    final PnkyPromise<Integer> cancelPromise = originalPromise
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            invokedTask.set(true);
          }
        });

    final PnkyPromise<Integer> finalResult = cancelPromise
        .alwaysAccept(new ThrowingBiConsumer<Integer, Throwable>()
        {
          @Override
          public void accept(final Integer result, final Throwable error) throws Exception
          {
            invokedDownstream.set(true);
            isCancellationException.set(error instanceof CancellationException);
          }
        });

    assertTrue(cancelPromise.cancel());
    countDown.countDown();

    try
    {
      finalResult.get(5, TimeUnit.SECONDS);
      fail("Final promise should not be successful");
    }
    catch (final ExecutionException e)
    {
      assertTrue(e.getCause() instanceof CancellationException);
    }

    try
    {
      cancelPromise.get(5, TimeUnit.SECONDS);
      fail("Cancel promise should not be successful");
    }
    catch (final CancellationException e)
    {
      // expected
    }

    assertEquals(Integer.valueOf(1), originalPromise.get(5, TimeUnit.SECONDS));

    assertFalse(invokedTask.get());
    assertTrue(invokedDownstream.get());
    assertTrue(isCancellationException.get());
  }

  @Test
  public void testCannotCancelWhileTaskIsRunning() throws Exception
  {
    final CountDownLatch countDown = new CountDownLatch(1);
    final CountDownLatch arrived = new CountDownLatch(1);

    final PnkyPromise<Void> initialPromise = Pnky.runAsync(new ThrowingRunnable()
    {
      @Override
      public void run() throws Exception
      {
        arrived.countDown();
        countDown.await();
      }
    }, executor);

    assertTrue(arrived.await(5, TimeUnit.SECONDS));

    assertFalse(initialPromise.cancel());

    countDown.countDown();

    assertNull(initialPromise.get());
  }

  @Test
  public void testCannotCancelAfterCompleted() throws Exception
  {
    final CountDownLatch arrived = new CountDownLatch(1);

    final PnkyPromise<Void> promise = Pnky
        .runAsync(new ThrowingRunnable()
        {
          @Override
          public void run() throws Exception
          {/**/}
        }, executor)
        .thenAccept(new ThrowingConsumer<Void>()
        {
          @Override
          public void accept(final Void r) throws Exception
          {
            arrived.countDown();
          }
        });

    assertTrue(arrived.await(5, TimeUnit.SECONDS));

    assertFalse(promise.cancel());

    assertNull(promise.get());
  }
}
