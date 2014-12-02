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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.jive.foss.commons.function.ThrowingBiConsumer;
import com.jive.foss.commons.function.ThrowingBiFunction;
import com.jive.foss.commons.function.ThrowingConsumer;
import com.jive.foss.commons.function.ThrowingFunction;
import com.jive.foss.commons.function.ThrowingRunnable;

//@formatter:off
/**
 * {@code PnkyPromise} is an enhancement of {@link ListenableFuture} that provides methods to handle
 * most scenarios needed in an asynchronous environment. While much more verbose, and a different
 * API, a {@code PnkyPromise} will work similarly to JavaScript
 * <a href="http://people.mozilla.org/~jorendorff/es6-draft.html#sec-promise-objects">promises</a>.
 *
 * <p>
 *   Experimentation with Guava's {@link ListenableFuture} and Java 8's {@link CompletableFuture},
 *   uncovered shortcomings with both existing solutions. Efforts uncovered 3 cross-cutting concerns
 *   to be addressed through a different APIs.
 *   <ol>
 *     <li>When to perform an action (always, on success only, or on failure only).</li>
 *     <li>Whether or not to propagate the successful result or error on completion or transform or
 *     recover the result.</li>
 *     <li>Handle a transformed result that returns the result of another asynchronous action.</li>
 *   </ol>
 * </p>
 *
 * This set of operations led to the following results:
 *   <table summary="">
 *     <tr>
 *       <th colspan="4">Listenable Future</th>
 *     </tr>
 *     <tr>
 *       <td></td>
 *       <td>Always invoke</td>
 *       <td>Invoke on success only</td>
 *       <td>Invoke on failure only</td>
 *     </tr>
 *     <tr>
 *       <td>Propagate result</td>
 *       <td>No</td>
 *       <td>No</td>
 *       <td>No</td>
 *     </tr>
 *     <tr>
 *       <td>Transform/recover result</td>
 *       <td>No</td>
 *       <td>Yes - transform</td>
 *       <td>Yes - withFallback</td>
 *     </tr>
 *     <tr>
 *       <td>Transform/recover with new future</td>
 *       <td>No</td>
 *       <td>No</td>
 *       <td>No</td>
 *     </tr>
 *     <tr>
 *       <th colspan="4">Completable Future</th>
 *     </tr>
 *     <tr>
 *       <td></td>
 *       <td>Always invoke</td>
 *       <td>Invoke on success only</td>
 *       <td>Invoke on failure only</td>
 *     </tr>
 *     <tr>
 *       <td>Propagate result</td>
 *       <td>Yes - whenComplete</td>
 *       <td>Yes - thenAccept</td>
 *       <td>No</td>
 *     </tr>
 *     <tr>
 *       <td>Transform/recover result</td>
 *       <td>Yes - handle</td>
 *       <td>Yes - thenApply</td>
 *       <td>Yes - exceptionally</td>
 *     </tr>
 *     <tr>
 *       <td>Transform/recover with new future</td>
 *       <td>No</td>
 *       <td>Yes - thenCompose</td>
 *       <td>No</td>
 *     </tr>
 *   </table>
 *
 * <p>
 *   {@link ListenableFuture} is missing a lot of the desired operations and also does not provide
 *   the ability to chain actions in a fluent pattern.  Chaining must be performed in a verbose
 *   manner via the extension class {@link Futures}.
 * </p>
 *
 * <p>
 *   {@link CompletableFuture} meets many of the identified concerns but still did not provide all
 *   of the desired  capabilities.  There are also issues with the functional interfaces in Java 8
 *   not allowing for checked exceptions. While it is generally not the best idea to throw checked
 *   exceptions within an asynchronous chain, it is easy to acknowledge that these types of errors
 *   can happen and a library should be prepared to handle the exceptional result.
 * </p>
 *
 * <p>
 *   Since neither of these existing tools provides the full set of desired capabilities, this
 *   interface was developed to fill the gap. While {@link ListenableFuture} is not nearly as close
 *   to the desired set of capabilities, it is the easiest and cleanest base upon which to build the
 *   desired capabilities.
 * </p>
 *
 * This interface provides the following capabilities.
 * <table summary="">
 *     <tr>
 *       <th colspan="4">Pnky Promise</th>
 *     </tr>
 *     <tr>
 *       <td></td>
 *       <td>Always invoke</td>
 *       <td>Invoke on success only</td>
 *       <td>Invoke on failure only</td>
 *     </tr>
 *     <tr>
 *       <td>Propagate result</td>
 *       <td>Yes - alwaysAccept</td>
 *       <td>Yes - thenAccept</td>
 *       <td>Yes - onFailure</td>
 *     </tr>
 *     <tr>
 *       <td>Transform/recover result</td>
 *       <td>Yes - alwaysTransform</td>
 *       <td>Yes - thenTransform</td>
 *       <td>Yes - withFallback</td>
 *     </tr>
 *     <tr>
 *       <td>Transform/recover with new future</td>
 *       <td>Yes - alwaysCompose</td>
 *       <td>Yes - thenCompose</td>
 *       <td>Yes - composeFallback</td>
 *     </tr>
 *   </table>
 *
 * @author Brandon Pedersen
 */
//@formatter:on
public interface PnkyPromise<V> extends ListenableFuture<V>
{
  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes,
   * executing the operation on the thread that completes this future. The returned future completes
   * after executing the operation with the same completion state as this future, except when the
   * operation throws an exception. In this case, the returned future completes exceptionally with
   * the thrown exception.
   *
   * @param onSuccess
   *          operation to supply with the result of this future when this future completes
   *          successfully
   * @param onFailure
   *          operation to supply with the failure cause when this future completes exceptionally
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> alwaysAccept(final ThrowingConsumer<? super V> onSuccess,
      final ThrowingConsumer<Throwable> onFailure);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes,
   * executing the operation on the provided executor. The returned future completes after executing
   * the operation with the same completion state as this future, except when the operation throws
   * an exception. In this case, the returned future completes exceptionally with the thrown
   * exception.
   *
   * @param onSuccess
   *          operation to supply with the result of this future when this future completes
   *          successfully
   * @param onFailure
   *          operation to supply with the failure cause when this future completes exceptionally
   * @param executor
   *          the executor used to execute the supplied operation and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> alwaysAccept(final ThrowingConsumer<? super V> onSuccess,
      final ThrowingConsumer<Throwable> onFailure, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function after this
   * future completes, executing the function on the thread that completes this future. The returned
   * future completes with the result of executing the supplied function, except when the function
   * throws an exception. In this case, the returned future completes exceptionally with the thrown
   * exception.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future when this future completes
   *          successfully
   * @param onFailure
   *          the function to supply with the failure cause when this future completes exceptionally
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysTransform(final ThrowingFunction<? super V, O> onSuccess,
      final ThrowingFunction<Throwable, O> onFailure);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function after this
   * future completes, executing the function on the provided executor. The returned future
   * completes with the result of executing the supplied function, except when the function throws
   * an exception. In this case, the returned future completes exceptionally with the thrown
   * exception.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future when this future completes
   *          successfully
   * @param onFailure
   *          the function to supply with the failure cause when this future completes exceptionally
   * @param executor
   *          the executor used to execute the supplied function and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysTransform(final ThrowingFunction<? super V, O> onSuccess,
      final ThrowingFunction<Throwable, O> onFailure, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes, executing the function on the thread that completes this
   * future. The returned future completes with the result of the future returned by the supplied
   * function, except when the function throws an exception. In this case, the returned future
   * completes exceptionally with the thrown exception. The completion of the returned future is
   * performed on the thread that completes the future returned by the supplied function.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future when this future completes
   *          successfully
   * @param onFailure
   *          the function to supply with the failure cause when this future completes exceptionally
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysCompose(final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess,
      final ThrowingFunction<Throwable, PnkyPromise<O>> onFailure);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes, executing the function on the supplied executor. The
   * returned future completes with the result of the future returned by the supplied function,
   * except when the function throws an exception. In this case, the returned future completes
   * exceptionally with the thrown exception. The completion of the returned future is performed on
   * the thread that completes the future returned by the supplied function.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future when this future completes
   *          successfully
   * @param onFailure
   *          the function to supply with the failure cause when this future completes exceptionally
   * @param executor
   *          the executor used to execute the supplied function
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysCompose(final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess,
      final ThrowingFunction<Throwable, PnkyPromise<O>> onFailure, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes,
   * executing the operation on the thread that completes this future. The returned future completes
   * after executing the operation with the same completion state as this future, except when the
   * operation throws an exception. In this case, the returned future completes exceptionally with
   * the thrown exception.
   *
   * @param operation
   *          operation to supply with the result of this future when this future completes
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> alwaysAccept(final ThrowingBiConsumer<? super V, Throwable> operation);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes,
   * executing the operation on the supplied executor. The returned future completes after executing
   * the operation with the same completion state as this future, except when the operation throws
   * an exception. In this case, the returned future completes exceptionally with the thrown
   * exception.
   *
   * @param operation
   *          operation to supply with the result of this future when this future completes
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> alwaysAccept(final ThrowingBiConsumer<? super V, Throwable> operation,
      final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function after this
   * future completes, executing the function on the thread that completes this future. The returned
   * future completes with the result of executing the supplied function, except when the function
   * throws an exception. In this case, the returned future completes exceptionally with the thrown
   * exception.
   *
   * @param <O>
   *          type of the result
   * @param function
   *          the function to supply with the result of this future
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysTransform(final ThrowingBiFunction<? super V, Throwable, O> function);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function after this
   * future completes, executing the function on the thread that completes this future. The returned
   * future completes with the result of executing the supplied function, except when the function
   * throws an exception. In this case, the returned future completes exceptionally with the thrown
   * exception.
   *
   * @param <O>
   *          type of the result
   * @param function
   *          the function to supply with the result of this future
   * @param executor
   *          the executor used to execute the supplied function and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysTransform(final ThrowingBiFunction<? super V, Throwable, O> function,
      final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes, executing the function on the thread that completes this
   * future. The returned future completes with the result of the future returned by the supplied
   * function, except when the function throws an exception. In this case, the returned future
   * completes exceptionally with the thrown exception. The completion of the returned future is
   * performed on the thread that completes the future returned by the supplied function.
   *
   * @param <O>
   *          type of the result
   * @param function
   *          the function to supply with the result of this future
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysCompose(
      final ThrowingBiFunction<? super V, Throwable, PnkyPromise<O>> function);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes, executing the function on the supplied executor. The
   * returned future completes with the result of the future returned by the supplied function,
   * except when the function throws an exception. In this case, the returned future completes
   * exceptionally with the thrown exception. The completion of the returned future is performed on
   * the thread that completes the future returned by the supplied function.
   *
   * @param <O>
   *          type of the result
   * @param function
   *          the function to supply with the result of this future
   * @param executor
   *          the executor used to execute the supplied function
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> alwaysCompose(
      final ThrowingBiFunction<? super V, Throwable, PnkyPromise<O>> function,
      final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * successfully, executing the operation on the thread that completes this future. The returned
   * future completes, after executing the operation in the case of a successful completion, with
   * the same completion state as this future, except when the operation throws an exception. In
   * this case, the returned future completes exceptionally with the thrown exception.
   *
   * @param operation
   *          the operation to execute
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> alwaysRun(final ThrowingRunnable operation);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * successfully, executing the operation on the supplied executor. The returned future completes,
   * after executing the operation in the case of a successful completion of this future, with the
   * same completion state as this future, except when the operation throws an exception. In this
   * case, the returned future completes exceptionally with the thrown exception.
   *
   * @param operation
   *          the operation to execute
   * @param executor
   *          the executor used to execute the supplied operation and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> alwaysRun(final ThrowingRunnable operation, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * successfully, executing the operation on the thread that completes this future. The returned
   * future completes, after executing the operation in the case of a successful completion, with
   * the same completion state as this future, except when the operation throws an exception. In
   * this case, the returned future completes exceptionally with the thrown exception.
   *
   * @param onSuccess
   *          operation to supply with the result of this future when this future completes
   *          successfully
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> thenAccept(final ThrowingConsumer<? super V> onSuccess);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * successfully, executing the operation on the supplied executor. The returned future completes,
   * after executing the operation in the case of a successful completion of this future, with the
   * same completion state as this future, except when the operation throws an exception. In this
   * case, the returned future completes exceptionally with the thrown exception.
   *
   * @param onSuccess
   *          operation to supply with the result of this future when this future completes
   *          successfully
   * @param executor
   *          the executor used to execute the supplied operation and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> thenAccept(final ThrowingConsumer<? super V> onSuccess, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function when this
   * future completes successfully, executing the function on the thread that completes this future.
   * The returned future completes with the result of executing the supplied function in the case of
   * a successful completion of this future, except when the function throws an exception. In this
   * case, the returned future completes exceptionally with the thrown exception. If this future
   * completes exceptionally, the returned future completes exceptionally with the same cause as
   * this future.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future when this future completes
   *          successfully
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> thenTransform(final ThrowingFunction<? super V, O> onSuccess);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function when this
   * future completes successfully, executing the function on the supplied executor. The returned
   * future completes with the result of executing the supplied function in the case of a successful
   * completion of this future, except when the function throws an exception. In this case, the
   * returned future completes exceptionally with the thrown exception. If this future completes
   * exceptionally, the returned future completes exceptionally with the same cause as this future.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future when this future completes
   *          successfully
   * @param executor
   *          the executor used to execute the supplied operation and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> thenTransform(final ThrowingFunction<? super V, O> onSuccess,
      final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes successfully, executing the function on the thread that
   * completes this future. The returned future completes with the result of the future returned by
   * the supplied function in the case of a successful completion of this future, except when the
   * function throws an exception. In this case, the returned future completes exceptionally with
   * the thrown exception. The completion of the returned future is performed on the thread that
   * completes the future returned by the supplied function. If this future completes exceptionally,
   * the returned future completes exceptionally with the same cause as this future.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> thenCompose(final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes successfully, executing the function on the supplied
   * executor. The returned future completes with the result of the future returned by the supplied
   * function in the case of a successful completion of this future, except when the function throws
   * an exception. In this case, the returned future completes exceptionally with the thrown
   * exception. The completion of the returned future is performed on the thread that completes the
   * future returned by the supplied function. If this future completes exceptionally, the returned
   * future completes exceptionally with the same cause as this future.
   *
   * @param <O>
   *          type of the result
   * @param onSuccess
   *          the function to supply with the result of this future
   * @param executor
   *          the executor used to execute the supplied function
   *
   * @return a new {@link PnkyPromise future}
   */
  <O> PnkyPromise<O> thenCompose(final ThrowingFunction<? super V, PnkyPromise<O>> onSuccess,
      final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * successfully, executing the operation on the thread that completes this future. The returned
   * future completes, after executing the operation in the case of a successful completion, with
   * the same completion state as this future, except when the operation throws an exception. In
   * this case, the returned future completes exceptionally with the thrown exception.
   *
   * @param onSuccess
   *          operation to execute when this future completes successfully
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> thenRun(final ThrowingRunnable onSuccess);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * successfully, executing the operation on the supplied executor. The returned future completes,
   * after executing the operation in the case of a successful completion of this future, with the
   * same completion state as this future, except when the operation throws an exception. In this
   * case, the returned future completes exceptionally with the thrown exception.
   *
   * @param runnable
   *          operation to execute when this future completes successfully
   * @param executor
   *          the executor used to execute the supplied operation and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> thenRun(final ThrowingRunnable runnable, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * exceptionally, executing the operation on the thread that completes this future. The returned
   * future completes, after executing the operation in the case of an exceptional completion of
   * this future, with the same completion state as this future, except when the operation throws an
   * exception. In this case, the returned future completes exceptionally with the thrown exception.
   *
   * @param onFailure
   *          operation to supply with the cause of the completion of this future when this future
   *          completes exceptionally
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> onFailure(final ThrowingConsumer<Throwable> onFailure);

  /**
   * Creates a new {@link PnkyPromise future} that performs an operation when this future completes
   * exceptionally, executing the operation on the supplied executor. The returned future completes,
   * after executing the operation in the case of an exceptional completion of this future, with the
   * same completion state as this future, except when the operation throws an exception. In this
   * case, the returned future completes exceptionally with the thrown exception.
   *
   * @param onFailure
   *          operation to supply with the cause of the completion of this future when this future
   *          completes exceptionally
   * @param executor
   *          the executor used to execute the supplied operation and complete the returned future
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> onFailure(final ThrowingConsumer<Throwable> onFailure, final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function when this
   * future completes exceptionally, executing the function on the thread that completes this
   * future. The returned future completes with the result of executing the supplied function in the
   * case of an exceptional completion of this future, except when the function throws an exception.
   * In this case, the returned future completes exceptionally with the thrown exception. If this
   * future completes successfully, the returned future completes successfully with the same result
   * as this future.
   * <p>
   * This operation provides a mechanism to recover from a failure by supplying an alternate valid
   * result.
   *
   * @param onFailure
   *          the function to supply with the result of this future when this future completes
   *          successfully
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> withFallback(final ThrowingFunction<Throwable, V> onFailure);

  /**
   * Creates a new {@link PnkyPromise future} that completes via a supplied function when this
   * future completes exceptionally, executing the function on the supplied executor. The returned
   * future completes with the result of executing the supplied function in the case of an
   * exceptional completion of this future, except when the function throws an exception. In this
   * case, the returned future completes exceptionally with the thrown exception. If this future
   * completes successfully, the returned future completes successfully with the same result as this
   * future.
   * <p>
   * This operation provides a mechanism to recover from a failure by supplying an alternate valid
   * result.
   *
   * @param onFailure
   *          the function to supply with the result of this future when this future completes
   *          successfully
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> withFallback(final ThrowingFunction<Throwable, V> onFailure,
      final Executor executor);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes exceptionally, executing the function on the thread that
   * completes this future. The returned future completes with the result of the future returned by
   * the supplied function in the case of an exceptional completion of this future, except when the
   * function throws an exception. In this case, the returned future completes exceptionally with
   * the thrown exception. The completion of the returned future is performed on the thread that
   * completes the future returned by the supplied function. If this future completes successfully,
   * the returned future completes successfully with the same result as this future.
   * <p>
   * This operation provides a mechanism to recover from a failure by supplying an alternate valid
   * result.
   *
   * @param onFailure
   *          the function to supply with the result of this future
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> composeFallback(final ThrowingFunction<Throwable, PnkyPromise<V>> onFailure);

  /**
   * Creates a new {@link PnkyPromise future} that completes via the future returned by a supplied
   * function after this future completes exceptionally, executing the function on the supplied
   * executor. The returned future completes with the result of the future returned by the supplied
   * function in the case of an exceptional completion of this future, except when the function
   * throws an exception. In this case, the returned future completes exceptionally with the thrown
   * exception. The completion of the returned future is performed on the thread that completes the
   * future returned by the supplied function. If this future completes successfully, the returned
   * future completes successfully with the same result as this future.
   * <p>
   * This operation provides a mechanism to recover from a failure by supplying an alternate valid
   * result.
   *
   * @param onFailure
   *          the function to supply with the result of this future
   * @param executor
   *          the executor used to execute the supplied function
   *
   * @return a new {@link PnkyPromise future}
   */
  PnkyPromise<V> composeFallback(
      final ThrowingFunction<Throwable, PnkyPromise<V>> onFailure,
      final Executor executor);

  /**
   * Attempts to cancel execution of this task. The {@link PnkyPromise future} may only be cancelled
   * if the execution of the task has not yet started. Subsequent {@link PnkyPromise futures} will
   * fail with a {@link CancellationException} that is triggered from this cancellation.
   *
   * This method works the same as calling {@link #cancel(boolean) cancel(false)}.
   *
   * @return {@code false} if the task could not be cancelled, typically because it has already
   *         completed normally; {@code true} otherwise
   */
  boolean cancel();

  /**
   * Attempts to cancel execution of this task. The {@link PnkyPromise future} may only be cancelled
   * if the execution of the task has not yet started. Subsequent {@link PnkyPromise futures} will
   * fail with a {@link CancellationException} that is triggered from this cancellation.
   *
   * @param mayInterruptIfRunning
   *          {@code true} this argument has no effect on the action taken by this method as the
   *          action has no ability to be interrupted
   *
   * @return {@code false} if the task could not be cancelled, typically because it has already
   *         completed normally; {@code true} otherwise
   */
  @Override
  boolean cancel(boolean mayInterruptIfRunning);
}
