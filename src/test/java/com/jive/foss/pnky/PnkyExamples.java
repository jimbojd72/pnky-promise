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

import java.util.concurrent.Executor;

import com.jive.foss.commons.function.ThrowingFunction;
import com.jive.foss.commons.function.ThrowingRunnable;
import com.jive.foss.commons.function.ThrowingSupplier;

/**
 * Example that shows different usage scenarios for a {@link PnkyPromise}.
 *
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class PnkyExamples
{
  private Executor executor;

  /**
   * Shows how to use {@link PnkyPromise} to execute a step that throws an exception that can be
   * used to complete the future exceptionally.
   * <p>
   * This approach is the most verbose.
   */
  public PnkyPromise<Void> newExceptionalProcessExampleMethod()
  {
    final Pnky<Void> pnky = Pnky.create();

    executor.execute(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          doSomethingExceptional();
          pnky.resolve(null);
        }
        catch (final Exception e)
        {
          pnky.reject(e);
        }
      }
    });

    return pnky;
  }

  /**
   * Shows an alternate, and more concise, example of how to use {@link PnkyPromise} to execute a
   * step that throws an exception that can be used to complete the future exceptionally.
   */
  public PnkyPromise<Void> newExceptionalProcessExampleAlternateMethod()
  {
    return Pnky.runAsync(new ThrowingRunnable()
    {
      @Override
      public void run() throws Exception
      {
        doSomethingExceptional();
      }
    }, executor);
  }

  /**
   * Shows how to use {@link PnkyPromise} to execute a step, in-line mid process, that throws an
   * exception that should be handled manually to complete the future.
   */
  public PnkyPromise<Void> inlineExceptionalProcessCompleteDirectlyExampleMethod()
  {
    return doSomethingUsingPromise()
        .thenCompose(new ThrowingFunction<Integer, PnkyPromise<Void>>()
        {
          @Override
          public PnkyPromise<Void> apply(final Integer result) throws Exception
          {
            final Pnky<Void> future = Pnky.create();

            if (result == 1)
            {
              try
              {
                doSomethingExceptional();
                future.resolve(null);
              }
              catch (final Exception e)
              {
                future.reject(e);
              }
            }

            return future;
          }
        });
  }

  /**
   * Shoes how to use {@link PnkyPromise} to execute transformational and compositional steps,
   * in-line mid process, with a fallback step to handle exceptional completion.
   */
  public PnkyPromise<String> inlineProcessExampleTransformMethod()
  {
    return doSomethingUsingPromise()
        .thenTransform(new ThrowingFunction<Integer, Integer>()
        {
          @Override
          public Integer apply(final Integer result) throws Exception
          {
            if (result == 1)
            {
              doSomethingExceptional();

              return 0;
            }
            else
            {
              return result;
            }
          }
        })
        .thenCompose(new ThrowingFunction<Integer, PnkyPromise<String>>()
        {
          @Override
          public PnkyPromise<String> apply(final Integer value) throws Exception
          {
            return doSomethingElseUsingPromise(value, 2);
          }
        })
        .composeFallback(new ThrowingFunction<Throwable, PnkyPromise<String>>()
        {
          @Override
          public PnkyPromise<String> apply(final Throwable cause) throws Exception
          {
            return Pnky.immediatelyFailed(new IllegalStateException(cause));
          }
        });
  }

  /**
   * Represents some action that throws an exception.
   */
  public void doSomethingExceptional() throws Exception
  {
    // No-op
  }

  public PnkyPromise<Integer> doSomethingUsingPromise()
  {
    return Pnky.supplyAsync(new ThrowingSupplier<Integer>()
    {
      @Override
      public Integer get() throws Exception
      {
        return 1;
      }
    }, executor);
  }

  public PnkyPromise<String> doSomethingElseUsingPromise(final Integer value, final int radix)
  {
    return Pnky.supplyAsync(new ThrowingSupplier<String>()
    {
      @Override
      public String get() throws Exception
      {
        return Integer.toString(value, radix);
      }
    }, executor);
  }
}
