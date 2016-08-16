/**
 * Copyright 2016 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.*;

import io.reactivex.Observer;
import io.reactivex.annotations.*;
import io.reactivex.disposables.*;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.flowables.*;
import io.reactivex.functions.*;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.functions.Objects;
import io.reactivex.internal.fuseable.*;
import io.reactivex.internal.operators.completable.CompletableFromObservable;
import io.reactivex.internal.operators.flowable.*;
import io.reactivex.internal.operators.single.SingleFromObservable;
import io.reactivex.internal.operators.observable.*;
import io.reactivex.internal.subscribers.observable.*;
import io.reactivex.internal.util.ArrayListSupplier;
import io.reactivex.internal.util.HashMapSupplier;
import io.reactivex.observables.*;
import io.reactivex.observers.*;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.*;

/**
 * The Observable class that is designed similar to the Reactive-Streams Pattern, minus the backpressure,
 *  and offers factory methods, intermediate operators and the ability to consume reactive dataflows.
 * <p>
 * Reactive-Streams operates with {@code ObservableSource}s which {@code Observable} extends. Many operators
 * therefore accept general {@code ObservableSource}s directly and allow direct interoperation with other
 * Reactive-Streams implementations.
 * <p>
 * The Observable's operators, by default, run with a buffer size of 128 elements (see {@link Flowable#bufferSize()},
 * that can be overridden globally via the system parameter {@code rx2.buffer-size}. Most operators, however, have
 * overloads that allow setting their internal buffer size explicitly.
 * <p>
 * The documentation for this class makes use of marble diagrams. The following legend explains these diagrams:
 * <p>
 * <img width="640" height="301" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/legend.png" alt="">
 * <p>
 * For more information see the <a href="http://reactivex.io/documentation/ObservableSource.html">ReactiveX
 * documentation</a>.
 * 
 * @param <T>
 *            the type of the items emitted by the Observable
 */
public abstract class Observable<T> implements ObservableSource<T> {

    /**
     * Mirrors the one ObservableSource in an Iterable of several ObservableSources that first either emits an item or sends
     * a termination notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element type
     * @param sources
     *            an Iterable of ObservableSources sources competing to react first
     * @return a Observable that emits the same sequence as whichever of the source ObservableSources first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public static <T> Observable<T> amb(Iterable<? extends ObservableSource<? extends T>> sources) {
        Objects.requireNonNull(sources, "sources is null");
        return new ObservableAmb<T>(null, sources);
    }
    
    /**
     * Mirrors the one ObservableSource in an array of several ObservableSources that first either emits an item or sends
     * a termination notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element type
     * @param sources
     *            an array of ObservableSource sources competing to react first
     * @return a Observable that emits the same sequence as whichever of the source ObservableSources first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> amb(ObservableSource<? extends T>... sources) {
        Objects.requireNonNull(sources, "sources is null");
        int len = sources.length;
        if (len == 0) {
            return empty();
        } else
        if (len == 1) {
            return (Observable<T>)wrap(sources[0]);
        }
        return new ObservableAmb<T>(sources, null);
    }
    
    /**
     * Returns the default 'island' size or capacity-increment hint for unbounded buffers.
     * @return the default 'island' size or capacity-increment hint
     */
    static int bufferSize() {
        return Flowable.bufferSize();
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @param bufferSize 
     *            the internal buffer size and prefetch amount applied to every source Observable
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatest(Function<? super T[], ? extends R> combiner, int bufferSize, ObservableSource<? extends T>... sources) {
        return combineLatest(sources, combiner, bufferSize);
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatest(Iterable<? extends ObservableSource<? extends T>> sources, 
            Function<? super T[], ? extends R> combiner) {
        return combineLatest(sources, combiner, bufferSize());
    }


    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @param bufferSize 
     *            the internal buffer size and prefetch amount applied to every source Observable
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatest(Iterable<? extends ObservableSource<? extends T>> sources, 
            Function<? super T[], ? extends R> combiner, int bufferSize) {
        Objects.requireNonNull(sources, "sources is null");
        Objects.requireNonNull(combiner, "combiner is null");
        validateBufferSize(bufferSize, "bufferSize");
        
        // the queue holds a pair of values so we need to double the capacity
        int s = bufferSize << 1;
        return new ObservableCombineLatest<T, R>(null, sources, combiner, s, false);
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatest(ObservableSource<? extends T>[] sources, 
            Function<? super T[], ? extends R> combiner) {
        return combineLatest(sources, combiner, bufferSize());
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @param bufferSize 
     *            the internal buffer size and prefetch amount applied to every source Observable
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatest(ObservableSource<? extends T>[] sources, 
            Function<? super T[], ? extends R> combiner, int bufferSize) {
        Objects.requireNonNull(sources, "sources is null");
        Objects.requireNonNull(combiner, "combiner is null");
        validateBufferSize(bufferSize, "bufferSize");
        
        // the queue holds a pair of values so we need to double the capacity
        int s = bufferSize << 1;
        return new ObservableCombineLatest<T, R>(sources, null, combiner, s, false);
    }

    /**
     * Combines two source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from either of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            BiFunction<? super T1, ? super T2, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2);
    }
    
    /**
     * Combines three source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3,
            Function3<? super T1, ? super T2, ? super T3, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3);
    }
    
    /**
     * Combines four source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <T4> the element type of the fourth source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param p4
     *            the fourth source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3, ObservableSource<? extends T4> p4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3, p4);
    }
    
    /**
     * Combines five source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <T4> the element type of the fourth source
     * @param <T5> the element type of the fifth source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param p4
     *            the fourth source ObservableSource
     * @param p5
     *            the fifth source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3, ObservableSource<? extends T4> p4,
            ObservableSource<? extends T5> p5,
            Function5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3, p4, p5);
    }
    
    /**
     * Combines six source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <T4> the element type of the fourth source
     * @param <T5> the element type of the fifth source
     * @param <T6> the element type of the sixth source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param p4
     *            the fourth source ObservableSource
     * @param p5
     *            the fifth source ObservableSource
     * @param p6
     *            the sixth source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3, ObservableSource<? extends T4> p4,
            ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            Function6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3, p4, p5, p6);
    }
    
    /**
     * Combines seven source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <T4> the element type of the fourth source
     * @param <T5> the element type of the fifth source
     * @param <T6> the element type of the sixth source
     * @param <T7> the element type of the seventh source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param p4
     *            the fourth source ObservableSource
     * @param p5
     *            the fifth source ObservableSource
     * @param p6
     *            the sixth source ObservableSource
     * @param p7
     *            the seventh source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3, ObservableSource<? extends T4> p4,
            ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            ObservableSource<? extends T7> p7,
            Function7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3, p4, p5, p6, p7);
    }

    /**
     * Combines eight source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <T4> the element type of the fourth source
     * @param <T5> the element type of the fifth source
     * @param <T6> the element type of the sixth source
     * @param <T7> the element type of the seventh source
     * @param <T8> the element type of the eighth source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param p4
     *            the fourth source ObservableSource
     * @param p5
     *            the fifth source ObservableSource
     * @param p6
     *            the sixth source ObservableSource
     * @param p7
     *            the seventh source ObservableSource
     * @param p8
     *            the eighth source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3, ObservableSource<? extends T4> p4,
            ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            ObservableSource<? extends T7> p7, ObservableSource<? extends T8> p8,
            Function8<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Combines nine source ObservableSources by emitting an item that aggregates the latest values of each of the
     * source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the element type of the first source
     * @param <T2> the element type of the second source
     * @param <T3> the element type of the third source
     * @param <T4> the element type of the fourth source
     * @param <T5> the element type of the fifth source
     * @param <T6> the element type of the sixth source
     * @param <T7> the element type of the seventh source
     * @param <T8> the element type of the eighth source
     * @param <T9> the element type of the ninth source
     * @param <R> the combined output type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            the second source ObservableSource
     * @param p3
     *            the third source ObservableSource
     * @param p4
     *            the fourth source ObservableSource
     * @param p5
     *            the fifth source ObservableSource
     * @param p6
     *            the sixth source ObservableSource
     * @param p7
     *            the seventh source ObservableSource
     * @param p8
     *            the eighth source ObservableSource
     * @param p9
     *            the ninth source ObservableSource
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> Observable<R> combineLatest(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            ObservableSource<? extends T3> p3, ObservableSource<? extends T4> p4,
            ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            ObservableSource<? extends T7> p7, ObservableSource<? extends T8> p8,
            ObservableSource<? extends T9> p9,
            Function9<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? super T9, ? extends R> combiner) {
        return combineLatest(Functions.toFunction(combiner), bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatestDelayError(ObservableSource<? extends T>[] sources, 
            Function<? super T[], ? extends R> combiner) {
        return combineLatestDelayError(sources, combiner, bufferSize());
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function and delays any error from the sources until
     * all source ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @param bufferSize 
     *            the internal buffer size and prefetch amount applied to every source Observable
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatestDelayError(Function<? super T[], ? extends R> combiner, 
            int bufferSize, ObservableSource<? extends T>... sources) {
        return combineLatestDelayError(sources, combiner, bufferSize);
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function and delays any error from the sources until
     * all source ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @param bufferSize 
     *            the internal buffer size and prefetch amount applied to every source Observable
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatestDelayError(ObservableSource<? extends T>[] sources, 
            Function<? super T[], ? extends R> combiner, int bufferSize) {
        validateBufferSize(bufferSize, "bufferSize");
        Objects.requireNonNull(combiner, "combiner is null");
        if (sources.length == 0) {
            return empty();
        }
        // the queue holds a pair of values so we need to double the capacity
        int s = bufferSize << 1;
        return new ObservableCombineLatest<T, R>(sources, null, combiner, s, true);
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function and delays any error from the sources until
     * all source ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatestDelayError(Iterable<? extends ObservableSource<? extends T>> sources, 
            Function<? super T[], ? extends R> combiner) {
        return combineLatestDelayError(sources, combiner, bufferSize());
    }

    /**
     * Combines a collection of source ObservableSources by emitting an item that aggregates the latest values of each of
     * the source ObservableSources each time an item is received from any of the source ObservableSources, where this
     * aggregation is defined by a specified function and delays any error from the sources until
     * all source ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the collection of source ObservableSources
     * @param combiner
     *            the aggregation function used to combine the items emitted by the source ObservableSources
     * @param bufferSize 
     *            the internal buffer size and prefetch amount applied to every source Observable
     * @return a Observable that emits items that are the result of combining the items emitted by the source
     *         ObservableSources by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> combineLatestDelayError(Iterable<? extends ObservableSource<? extends T>> sources, 
            Function<? super T[], ? extends R> combiner, int bufferSize) {
        Objects.requireNonNull(sources, "sources is null");
        Objects.requireNonNull(combiner, "combiner is null");
        validateBufferSize(bufferSize, "bufferSize");
        
        // the queue holds a pair of values so we need to double the capacity
        int s = bufferSize << 1;
        return new ObservableCombineLatest<T, R>(null, sources, combiner, s, true);
    }

    /**
     * Concatenates elements of each ObservableSource provided via an Iterable sequence into a single sequence
     * of elements without interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the common value type of the sources
     * @param sources the Iterable sequence of ObservableSources
     * @return the new Observable instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(Iterable<? extends ObservableSource<? extends T>> sources) {
        Objects.requireNonNull(sources, "sources is null");
        // TODO provide inlined implementation
        return fromIterable(sources).concatMap((Function)Functions.identity());
    }

    /**
     * Returns a Observable that emits the items emitted by each of the ObservableSources emitted by the source
     * ObservableSource, one after the other, without interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param sources
     *            a ObservableSource that emits ObservableSources
     * @return a Observable that emits items all of the items emitted by the ObservableSources emitted by
     *         {@code ObservableSources}, one after the other, without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> concat(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        return concat(sources, bufferSize());
    }

    /**
     * Returns a Observable that emits the items emitted by each of the ObservableSources emitted by the source
     * ObservableSource, one after the other, without interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param sources
     *            a ObservableSource that emits ObservableSources
     * @param prefetch 
     *            the number of ObservableSources to prefetch from the sources sequence.
     * @return a Observable that emits items all of the items emitted by the ObservableSources emitted by
     *         {@code ObservableSources}, one after the other, without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> concat(ObservableSource<? extends ObservableSource<? extends T>> sources, int prefetch) {
        Objects.requireNonNull(sources, "sources is null");
        return new ObservableConcatMap(sources, Functions.identity(), prefetch);
    }

    /**
     * Returns a Observable that emits the items emitted by two ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the two source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2) {
        return concatArray(p1, p2);
    }

    /**
     * Returns a Observable that emits the items emitted by three ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the three source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3) {
        return concatArray(p1, p2, p3);
    }

    /**
     * Returns a Observable that emits the items emitted by four ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @param p4
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the four source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4) {
        return concatArray(p1, p2, p3, p4);
    }

    /**
     * Returns a Observable that emits the items emitted by five ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @param p4
     *            a ObservableSource to be concatenated
     * @param p5
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the five source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4,
            ObservableSource<? extends T> p5
    ) {
        return concatArray(p1, p2, p3, p4, p5);
    }

    /**
     * Returns a Observable that emits the items emitted by six ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @param p4
     *            a ObservableSource to be concatenated
     * @param p5
     *            a ObservableSource to be concatenated
     * @param p6
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the six source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4,
            ObservableSource<? extends T> p5, ObservableSource<? extends T> p6
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6);
    }

    /**
     * Returns a Observable that emits the items emitted by seven ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @param p4
     *            a ObservableSource to be concatenated
     * @param p5
     *            a ObservableSource to be concatenated
     * @param p6
     *            a ObservableSource to be concatenated
     * @param p7
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the seven source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4,
            ObservableSource<? extends T> p5, ObservableSource<? extends T> p6,
            ObservableSource<? extends T> p7
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6, p7);
    }

    /**
     * Returns a Observable that emits the items emitted by eight ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @param p4
     *            a ObservableSource to be concatenated
     * @param p5
     *            a ObservableSource to be concatenated
     * @param p6
     *            a ObservableSource to be concatenated
     * @param p7
     *            a ObservableSource to be concatenated
     * @param p8
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the eight source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4,
            ObservableSource<? extends T> p5, ObservableSource<? extends T> p6,
            ObservableSource<? extends T> p7, ObservableSource<? extends T> p8
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Returns a Observable that emits the items emitted by nine ObservableSources, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be concatenated
     * @param p2
     *            a ObservableSource to be concatenated
     * @param p3
     *            a ObservableSource to be concatenated
     * @param p4
     *            a ObservableSource to be concatenated
     * @param p5
     *            a ObservableSource to be concatenated
     * @param p6
     *            a ObservableSource to be concatenated
     * @param p7
     *            a ObservableSource to be concatenated
     * @param p8
     *            a ObservableSource to be concatenated
     * @param p9
     *            a ObservableSource to be concatenated
     * @return a Observable that emits items emitted by the nine source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concat(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4,
            ObservableSource<? extends T> p5, ObservableSource<? extends T> p6,
            ObservableSource<? extends T> p7, ObservableSource<? extends T> p8,
            ObservableSource<? extends T> p9
    ) {
        return concatArray(p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    /**
     * Concatenates a variable number of ObservableSource sources.
     * <p>
     * Note: named this way because of overload conflict with concat(ObservableSource&lt;ObservableSource&gt;)
     * @param sources the array of sources
     * @param <T> the common base value type
     * @return the new NbpObservable instance
     * @throws NullPointerException if sources is null
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatArray(ObservableSource<? extends T>... sources) {
        if (sources.length == 0) {
            return empty();
        } else
        if (sources.length == 1) {
            return wrap((ObservableSource<T>)sources[0]);
        }
        return fromArray(sources).concatMap((Function)Functions.identity());
    }

    /**
     * Concatenates a variable number of ObservableSource sources and delays errors from any of them
     * till all terminate.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param sources the array of sources
     * @param <T> the common base value type
     * @return the new Observable instance
     * @throws NullPointerException if sources is null
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatArrayDelayError(ObservableSource<? extends T>... sources) {
        if (sources.length == 0) {
            return empty();
        } else
        if (sources.length == 1) {
            return (Observable<T>)wrap(sources[0]);
        }
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Concatenates a sequence of ObservableSources eagerly into a single stream of values.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them
     * in order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param sources a sequence of ObservableSources that need to be eagerly concatenated
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatArrayEager(ObservableSource<? extends T>... sources) {
        return concatArrayEager(bufferSize(), bufferSize(), sources);
    }

    /**
     * Concatenates a sequence of ObservableSources eagerly into a single stream of values.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them
     * in order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param sources a sequence of ObservableSources that need to be eagerly concatenated
     * @param maxConcurrency the maximum number of concurrent subscriptions at a time, Integer.MAX_VALUE
     *                       is interpreted as indication to subscribe to all sources at once
     * @param prefetch the number of elements to prefetch from each ObservableSource source
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatArrayEager(int maxConcurrency, int prefetch, ObservableSource<? extends T>... sources) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Concatenates the Iterable sequence of ObservableSources into a single sequence by subscribing to each ObservableSource,
     * one after the other, one at a time and delays any errors till the all inner ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources the Iterable sequence of ObservableSources
     * @return the new ObservableSource with the concatenating behavior
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatDelayError(Iterable<? extends ObservableSource<? extends T>> sources) {
        Objects.requireNonNull(sources, "sources is null");
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Concatenates the ObservableSource sequence of ObservableSources into a single sequence by subscribing to each inner ObservableSource,
     * one after the other, one at a time and delays any errors till the all inner and the outer ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources the ObservableSource sequence of ObservableSources
     * @return the new ObservableSource with the concatenating behavior
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> concatDelayError(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        return concatDelayError(sources, bufferSize(), true);
    }

    /**
     * Concatenates the ObservableSource sequence of ObservableSources into a single sequence by subscribing to each inner ObservableSource,
     * one after the other, one at a time and delays any errors till the all inner and the outer ObservableSources terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources the ObservableSource sequence of ObservableSources
     * @param prefetch the number of elements to prefetch from the outer ObservableSource
     * @param tillTheEnd if true exceptions from the outer and all inner ObservableSources are delayed to the end
     *                   if false, exception from the outer ObservableSource is delayed till the current ObservableSource terminates
     * @return the new ObservableSource with the concatenating behavior
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> concatDelayError(ObservableSource<? extends ObservableSource<? extends T>> sources, int prefetch, boolean tillTheEnd) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Concatenates a ObservableSource sequence of ObservableSources eagerly into a single stream of values.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * emitted source ObservableSources as they are observed. The operator buffers the values emitted by these
     * ObservableSources and then drains them in order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param sources a sequence of ObservableSources that need to be eagerly concatenated
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatEager(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        return concatEager(sources, bufferSize(), bufferSize());
    }

    /**
     * Concatenates a ObservableSource sequence of ObservableSources eagerly into a single stream of values.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * emitted source ObservableSources as they are observed. The operator buffers the values emitted by these
     * ObservableSources and then drains them in order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param sources a sequence of ObservableSources that need to be eagerly concatenated
     * @param maxConcurrency the maximum number of concurrently running inner ObservableSources; Integer.MAX_VALUE
     *                       is interpreted as all inner ObservableSources can be active at the same time
     * @param prefetch the number of elements to prefetch from each inner ObservableSource source
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatEager(ObservableSource<? extends ObservableSource<? extends T>> sources, int maxConcurrency, int prefetch) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Concatenates a sequence of ObservableSources eagerly into a single stream of values.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them
     * in order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param sources a sequence of ObservableSources that need to be eagerly concatenated
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatEager(Iterable<? extends ObservableSource<? extends T>> sources) {
        return concatEager(sources, bufferSize(), bufferSize());
    }

    /**
     * Concatenates a sequence of ObservableSources eagerly into a single stream of values.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them
     * in order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param sources a sequence of ObservableSources that need to be eagerly concatenated
     * @param maxConcurrency the maximum number of concurrently running inner ObservableSources; Integer.MAX_VALUE
     *                       is interpreted as all inner ObservableSources can be active at the same time
     * @param prefetch the number of elements to prefetch from each inner ObservableSource source
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> concatEager(Iterable<? extends ObservableSource<? extends T>> sources, int maxConcurrency, int prefetch) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Provides an API (via a cold Observable) that bridges the reactive world with the callback-style world.
     * <p>
     * Example:
     * <pre><code>
     * Observable.&lt;Event&gt;create(emitter -&gt; {
     *     Callback listener = new Callback() {
     *         &#64;Override
     *         public void onEvent(Event e) {
     *             emitter.onNext(e);
     *             if (e.isLast()) {
     *                 emitter.onCompleted();
     *             }
     *         }
     *         
     *         &#64;Override
     *         public void onFailure(Exception e) {
     *             emitter.onError(e);
     *         }
     *     };
     *     
     *     AutoCloseable c = api.someMethod(listener);
     *     
     *     emitter.setCancellable(c::close);
     *     
     * }, BackpressureMode.BUFFER);
     * </code></pre>
     * <p>
     * You should call the FlowableEmitter onNext, onError and onComplete methods in a serialized fashion. The
     * rest of its methods are threadsafe.
     * 
     * @param <T> the element type
     * @param source the emitter that is called when a Subscriber subscribes to the returned {@code Observable}
     * @return the new Observable instance
     * @see FlowableSource
     * @see FlowableEmitter.BackpressureMode
     * @see Cancellable
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    // FIXME update API, cancellation still needs support
    public static <T> Observable<T> create(ObservableSource<T> source) {
        Objects.requireNonNull(source, "source is null");
        return new ObservableFromSource<T>(source);
    }

    /**
     * Returns a Observable that calls a ObservableSource factory to create a ObservableSource for each new Observer
     * that subscribes. That is, for each subscriber, the actual ObservableSource that subscriber observes is
     * determined by the factory function.
     * <p>
     * <img width="640" height="340" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/defer.png" alt="">
     * <p>
     * The defer Observer allows you to defer or delay emitting items from a ObservableSource until such time as an
     * Observer subscribes to the ObservableSource. This allows an {@link Observer} to easily obtain updates or a
     * refreshed version of the sequence.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code defer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param supplier
     *            the ObservableSource factory function to invoke for each {@link Observer} that subscribes to the
     *            resulting ObservableSource
     * @param <T>
     *            the type of the items emitted by the ObservableSource
     * @return a Observable whose {@link Observer}s' subscriptions trigger an invocation of the given
     *         ObservableSource factory function
     * @see <a href="http://reactivex.io/documentation/operators/defer.html">ReactiveX operators documentation: Defer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> defer(Callable<? extends ObservableSource<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return new ObservableDefer<T>(supplier);
    }

    /**
     * Returns a Observable that emits no items to the {@link Observer} and immediately invokes its
     * {@link Subscriber#onComplete onComplete} method.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/empty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code empty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the type of the items (ostensibly) emitted by the ObservableSource
     * @return a Observable that emits no items to the {@link Observer} but immediately invokes the
     *         {@link Subscriber}'s {@link Subscriber#onComplete() onCompleted} method
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Empty</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @SuppressWarnings("unchecked")
    public static <T> Observable<T> empty() {
        return (Observable<T>) ObservableEmpty.INSTANCE;
    }

    /**
     * Returns a Observable that invokes an {@link Observer}'s {@link Observer#onError onError} method when the
     * Observer subscribes to it.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/error.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code error} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param errorSupplier
     *            a Callable factory to return a Throwable for each individual Subscriber
     * @param <T>
     *            the type of the items (ostensibly) emitted by the ObservableSource
     * @return a Observable that invokes the {@link Observer}'s {@link Observer#onError onError} method when
     *         the Observer subscribes to it
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Throw</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> error(Callable<? extends Throwable> errorSupplier) {
        Objects.requireNonNull(errorSupplier, "errorSupplier is null");
        return new ObservableError<T>(errorSupplier);
    }

    /**
     * Returns a Observable that invokes an {@link Observer}'s {@link Observer#onError onError} method when the
     * Observer subscribes to it.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/error.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code error} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param exception
     *            the particular Throwable to pass to {@link Observer#onError onError}
     * @param <T>
     *            the type of the items (ostensibly) emitted by the ObservableSource
     * @return a Observable that invokes the {@link Observer}'s {@link Observer#onError onError} method when
     *         the Observer subscribes to it
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Throw</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> error(final Throwable exception) {
        Objects.requireNonNull(exception, "e is null");
        return error(new Callable<Throwable>() {
            @Override
            public Throwable call() {
                return exception;
            }
        });
    }

    /**
     * Converts an Array into a ObservableSource that emits the items in the Array.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param values
     *            the array of elements
     * @param <T>
     *            the type of items in the Array and the type of items to be emitted by the resulting ObservableSource
     * @return a Observable that emits each item in the source Array
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> fromArray(T... values) {
        Objects.requireNonNull(values, "values is null");
        if (values.length == 0) {
            return empty();
        } else
        if (values.length == 1) {
            return just(values[0]);
        }
        return new ObservableFromArray<T>(values);
    }

    /**
     * Returns a Observable that, when an observer subscribes to it, invokes a function you specify and then
     * emits the value returned from that function.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/fromCallable.png" alt="">
     * <p>
     * This allows you to defer the execution of the function you specify until an observer subscribes to the
     * ObservableSource. That is to say, it makes the function "lazy."
     * <dl>
     *   <dt><b>Scheduler:</b></dt>
     *   <dd>{@code fromCallable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param supplier
     *         a function, the execution of which should be deferred; {@code fromCallable} will invoke this
     *         function only when an observer subscribes to the ObservableSource that {@code fromCallable} returns
     * @param <T>
     *         the type of the item emitted by the ObservableSource
     * @return a Observable whose {@link Observer}s' subscriptions trigger an invocation of the given function
     * @see #defer(Callable)
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> fromCallable(Callable<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return new ObservableFromCallable<T>(supplier);
    }

    /**
     * Converts a {@link Future} into a ObservableSource.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into a ObservableSource that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <p>
     * <em>Important note:</em> This ObservableSource is blocking; you cannot unsubscribe from it.
     * <p>
     * Unlike 1.x, cancelling the Observable won't cancel the future. If necessary, one can use composition to achieve the
     * cancellation effect: {@code futureObservableSource.doOnCancel(() -> future.cancel(true));}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting ObservableSource
     * @return a Observable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> fromFuture(Future<? extends T> future) {
        Objects.requireNonNull(future, "future is null");
        return new ObservableFromFuture<T>(future, 0L, null);
    }

    /**
     * Converts a {@link Future} into a ObservableSource, with a timeout on the Future.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into a ObservableSource that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <p>
     * Unlike 1.x, cancelling the Observable won't cancel the future. If necessary, one can use composition to achieve the
     * cancellation effect: {@code futureObservableSource.doOnCancel(() -> future.cancel(true));}.
     * <p>
     * <em>Important note:</em> This ObservableSource is blocking; you cannot unsubscribe from it.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param timeout
     *            the maximum time to wait before calling {@code get}
     * @param unit
     *            the {@link TimeUnit} of the {@code timeout} argument
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting ObservableSource
     * @return a Observable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> fromFuture(Future<? extends T> future, long timeout, TimeUnit unit) {
        Objects.requireNonNull(future, "future is null");
        Objects.requireNonNull(unit, "unit is null");
        return new ObservableFromFuture<T>(future, timeout, unit);
    }

    /**
     * Converts a {@link Future} into a ObservableSource, with a timeout on the Future.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into a ObservableSource that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <p>
     * Unlike 1.x, cancelling the Observable won't cancel the future. If necessary, one can use composition to achieve the
     * cancellation effect: {@code futureObservableSource.doOnCancel(() -> future.cancel(true));}.
     * <p>
     * <em>Important note:</em> This ObservableSource is blocking; you cannot unsubscribe from it.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param timeout
     *            the maximum time to wait before calling {@code get}
     * @param unit
     *            the {@link TimeUnit} of the {@code timeout} argument
     * @param scheduler
     *            the {@link Scheduler} to wait for the Future on. Use a Scheduler such as
     *            {@link Schedulers#io()} that can block and wait on the Future
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting ObservableSource
     * @return a Observable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> Observable<T> fromFuture(Future<? extends T> future, long timeout, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        Observable<T> o = fromFuture(future, timeout, unit); 
        return o.subscribeOn(scheduler);
    }

    /**
     * Converts a {@link Future}, operating on a specified {@link Scheduler}, into a ObservableSource.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.s.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into a ObservableSource that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <p>
     * Unlike 1.x, cancelling the Observable won't cancel the future. If necessary, one can use composition to achieve the
     * cancellation effect: {@code futureObservableSource.doOnCancel(() -> future.cancel(true));}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param scheduler
     *            the {@link Scheduler} to wait for the Future on. Use a Scheduler such as
     *            {@link Schedulers#io()} that can block and wait on the Future
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting ObservableSource
     * @return a Observable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> Observable<T> fromFuture(Future<? extends T> future, Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        Observable<T> o = fromFuture(future);
        return o.subscribeOn(scheduler);
    }

    /**
     * Converts an {@link Iterable} sequence into a ObservableSource that emits the items in the sequence.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param source
     *            the source {@link Iterable} sequence
     * @param <T>
     *            the type of items in the {@link Iterable} sequence and the type of items to be emitted by the
     *            resulting ObservableSource
     * @return a Observable that emits each item in the source {@link Iterable} sequence
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    public static <T> Observable<T> fromIterable(Iterable<? extends T> source) {
        Objects.requireNonNull(source, "source is null");
        return new ObservableFromIterable<T>(source);
    }

    /**
     * Converts an arbitrary Reactive-Streams ObservableSource into a Observable if not already a
     * Observable.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code fromObservableSource} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type of the flow
     * @param publisher the ObservableSource to convert
     * @return the new Observable instance
     * @throws NullPointerException if publisher is null
     */
    public static <T> Observable<T> fromObservableSource(final Publisher<? extends T> publisher) {
        Objects.requireNonNull(publisher, "publisher is null");
        return new ObservableFromPublisher<T>(publisher);
    }

    /**
     * Returns a cold, synchronous, stateless and backpressure-aware generator of values.
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code generator} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the generated value type
     * @param generator the Consumer called whenever a particular downstream Subscriber has
     * requested a value. The callback then should call {@code onNext}, {@code onError} or 
     * {@code onComplete} to signal a value or a terminal event. Signalling multiple {@code onNext}
     * in a call will make the operator signal {@code IllegalStateException}.
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> generate(final Consumer<Observer<T>> generator) {
        Objects.requireNonNull(generator, "generator  is null");
        return generate(Functions.<Object>nullSupplier(), 
        new BiFunction<Object, Observer<T>, Object>() {
            @Override
            public Object apply(Object s, Observer<T> o) throws Exception {
                generator.accept(o);
                return s;
            }
        }, Functions.<Object>emptyConsumer());
    }

    /**
     * Returns a cold, synchronous, stateful and backpressure-aware generator of values.
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code generate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <S> the type of the per-Subscriber state
     * @param <T> the generated value type
     * @param initialState the Callable to generate the initial state for each Subscriber
     * @param generator the Consumer called with the current state whenever a particular downstream Subscriber has
     * requested a value. The callback then should call {@code onNext}, {@code onError} or 
     * {@code onComplete} to signal a value or a terminal event. Signalling multiple {@code onNext}
     * in a call will make the operator signal {@code IllegalStateException}.
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, S> Observable<T> generate(Callable<S> initialState, final BiConsumer<S, Observer<T>> generator) {
        Objects.requireNonNull(generator, "generator  is null");
        return generate(initialState, new BiFunction<S, Observer<T>, S>() {
            @Override
            public S apply(S s, Observer<T> o) throws Exception {
                generator.accept(s, o);
                return s;
            }
        }, Functions.emptyConsumer());
    }

    /**
     * Returns a cold, synchronous, stateful and backpressure-aware generator of values.
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code generate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <S> the type of the per-Subscriber state
     * @param <T> the generated value type
     * @param initialState the Callable to generate the initial state for each Subscriber
     * @param generator the Consumer called with the current state whenever a particular downstream Subscriber has
     * requested a value. The callback then should call {@code onNext}, {@code onError} or 
     * {@code onComplete} to signal a value or a terminal event. Signalling multiple {@code onNext}
     * in a call will make the operator signal {@code IllegalStateException}.
     * @param disposeState the Consumer that is called with the current state when the generator 
     * terminates the sequence or it gets cancelled
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, S> Observable<T> generate(
            final Callable<S> initialState, 
            final BiConsumer<S, Observer<T>> generator, 
            Consumer<? super S> disposeState) {
        Objects.requireNonNull(generator, "generator  is null");
        return generate(initialState, new BiFunction<S, Observer<T>, S>() {
            @Override
            public S apply(S s, Observer<T> o) throws Exception {
                generator.accept(s, o);
                return s;
            }
        }, disposeState);
    }

    /**
     * Returns a cold, synchronous, stateful and backpressure-aware generator of values.
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code generate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <S> the type of the per-Subscriber state
     * @param <T> the generated value type
     * @param initialState the Callable to generate the initial state for each Subscriber
     * @param generator the Function called with the current state whenever a particular downstream Subscriber has
     * requested a value. The callback then should call {@code onNext}, {@code onError} or 
     * {@code onComplete} to signal a value or a terminal event and should return a (new) state for
     * the next invocation. Signalling multiple {@code onNext}
     * in a call will make the operator signal {@code IllegalStateException}.
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, S> Observable<T> generate(Callable<S> initialState, BiFunction<S, Observer<T>, S> generator) {
        return generate(initialState, generator, Functions.emptyConsumer());
    }

    /**
     * Returns a cold, synchronous, stateful and backpressure-aware generator of values.
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code generate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <S> the type of the per-Subscriber state
     * @param <T> the generated value type
     * @param initialState the Callable to generate the initial state for each Subscriber
     * @param generator the Function called with the current state whenever a particular downstream Subscriber has
     * requested a value. The callback then should call {@code onNext}, {@code onError} or 
     * {@code onComplete} to signal a value or a terminal event and should return a (new) state for
     * the next invocation. Signalling multiple {@code onNext}
     * in a call will make the operator signal {@code IllegalStateException}.
     * @param disposeState the Consumer that is called with the current state when the generator 
     * terminates the sequence or it gets cancelled
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, S> Observable<T> generate(Callable<S> initialState, BiFunction<S, Observer<T>, S> generator, 
            Consumer<? super S> disposeState) {
        Objects.requireNonNull(initialState, "initialState is null");
        Objects.requireNonNull(generator, "generator  is null");
        Objects.requireNonNull(disposeState, "diposeState is null");
        return new ObservableGenerate<T, S>(initialState, generator, disposeState);
    }

    /**
     * Returns a Observable that emits a {@code 0L} after the {@code initialDelay} and ever increasing numbers
     * after each {@code period} of time thereafter.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.p.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code interval} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param initialDelay
     *            the initial delay time to wait before emitting the first value of 0L
     * @param period
     *            the period of time between emissions of the subsequent numbers
     * @param unit
     *            the time unit for both {@code initialDelay} and {@code period}
     * @return a Observable that emits a 0L after the {@code initialDelay} and ever increasing numbers after
     *         each {@code period} of time thereafter
     * @see <a href="http://reactivex.io/documentation/operators/interval.html">ReactiveX operators documentation: Interval</a>
     * @since 1.0.12
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static Observable<Long> interval(long initialDelay, long period, TimeUnit unit) {
        return interval(initialDelay, period, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits a {@code 0L} after the {@code initialDelay} and ever increasing numbers
     * after each {@code period} of time thereafter, on a specified {@link Scheduler}.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.ps.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param initialDelay
     *            the initial delay time to wait before emitting the first value of 0L
     * @param period
     *            the period of time between emissions of the subsequent numbers
     * @param unit
     *            the time unit for both {@code initialDelay} and {@code period}
     * @param scheduler
     *            the Scheduler on which the waiting happens and items are emitted
     * @return a Observable that emits a 0L after the {@code initialDelay} and ever increasing numbers after
     *         each {@code period} of time thereafter, while running on the given Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/interval.html">ReactiveX operators documentation: Interval</a>
     * @since 1.0.12
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static Observable<Long> interval(long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        if (initialDelay < 0) {
            initialDelay = 0L;
        }
        if (period < 0) {
            period = 0L;
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return new ObservableInterval(initialDelay, period, unit, scheduler);
    }

    /**
     * Returns a Observable that emits a sequential number every specified interval of time.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/interval.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code interval} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param period
     *            the period size in time units (see below)
     * @param unit
     *            time units to use for the interval size
     * @return a Observable that emits a sequential number each time interval
     * @see <a href="http://reactivex.io/documentation/operators/interval.html">ReactiveX operators documentation: Interval</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static Observable<Long> interval(long period, TimeUnit unit) {
        return interval(period, period, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits a sequential number every specified interval of time, on a
     * specified Scheduler.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/interval.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param period
     *            the period size in time units (see below)
     * @param unit
     *            time units to use for the interval size
     * @param scheduler
     *            the Scheduler to use for scheduling the items
     * @return a Observable that emits a sequential number each time interval
     * @see <a href="http://reactivex.io/documentation/operators/interval.html">ReactiveX operators documentation: Interval</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static Observable<Long> interval(long period, TimeUnit unit, Scheduler scheduler) {
        return interval(period, period, unit, scheduler);
    }

    /**
     * Signals a range of long values, the first after some initial delay and the rest periodically after.
     * <p>
     * The sequence completes immediately after the last value (start + count - 1) has been reached.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code intervalRange} by default operates on the {@link Schedulers#computation() computation} {@link Scheduler}.</dd>
     * </dl>
     * @param start that start value of the range
     * @param count the number of values to emit in total, if zero, the operator emits an onComplete after the initial delay.
     * @param initialDelay the initial delay before signalling the first value (the start)
     * @param period the period between subsequent values
     * @param unit the unit of measure of the initialDelay and period amounts
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static Observable<Long> intervalRange(long start, long count, long initialDelay, long period, TimeUnit unit) {
        return intervalRange(start, count, initialDelay, period, unit, Schedulers.computation());
    }

    /**
     * Signals a range of long values, the first after some initial delay and the rest periodically after.
     * <p>
     * The sequence completes immediately after the last value (start + count - 1) has been reached.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you provide the {@link Scheduler}.</dd>
     * </dl>
     * @param start that start value of the range
     * @param count the number of values to emit in total, if zero, the operator emits an onComplete after the initial delay.
     * @param initialDelay the initial delay before signalling the first value (the start)
     * @param period the period between subsequent values
     * @param unit the unit of measure of the initialDelay and period amounts
     * @param scheduler the target scheduler where the values and terminal signals will be emitted
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static Observable<Long> intervalRange(long start, long count, long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {

        long end = start + (count - 1);
        if (end < 0) {
            throw new IllegalArgumentException("Overflow! start + count is bigger than Long.MAX_VALUE");
        }

        if (initialDelay < 0) {
            initialDelay = 0L;
        }
        if (period < 0) {
            period = 0L;
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return new ObservableIntervalRange(start, end, initialDelay, period, unit, scheduler);
    }

    /**
     * Returns a Observable that emits a single item and then completes.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.png" alt="">
     * <p>
     * To convert any object into a ObservableSource that emits that object, pass that object into the {@code just}
     * method.
     * <p>
     * This is similar to the {@link #fromArray(java.lang.Object[])} method, except that {@code from} will convert
     * an {@link Iterable} object into a ObservableSource that emits each of the items in the Iterable, one at a
     * time, while the {@code just} method converts an Iterable into a ObservableSource that emits the entire
     * Iterable as a single item.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param value
     *            the item to emit
     * @param <T>
     *            the type of that item
     * @return a Observable that emits {@code value} as a single item and then completes
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> just(T value) {
        Objects.requireNonNull(value, "The value is null");
        return new ObservableJust<T>(value);
    }

    /**
     * Converts two items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        
        return fromArray(v1, v2);
    }

    /**
     * Converts three items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        
        return fromArray(v1, v2, v3);
    }

    /**
     * Converts four items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        
        return fromArray(v1, v2, v3, v4);
    }

    /**
     * Converts five items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param v5
     *            fifth item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4, T v5) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        
        return fromArray(v1, v2, v3, v4, v5);
    }

    /**
     * Converts six items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param v5
     *            fifth item
     * @param v6
     *            sixth item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4, T v5, T v6) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6);
    }

    /**
     * Converts seven items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param v5
     *            fifth item
     * @param v6
     *            sixth item
     * @param v7
     *            seventh item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7);
    }

    /**
     * Converts eight items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param v5
     *            fifth item
     * @param v6
     *            sixth item
     * @param v7
     *            seventh item
     * @param v8
     *            eighth item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7, T v8) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        Objects.requireNonNull(v8, "The eighth value is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7, v8);
    }

    /**
     * Converts nine items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param v5
     *            fifth item
     * @param v6
     *            sixth item
     * @param v7
     *            seventh item
     * @param v8
     *            eighth item
     * @param v9
     *            ninth item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7, T v8, T v9) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        Objects.requireNonNull(v8, "The eighth value is null");
        Objects.requireNonNull(v9, "The ninth is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7, v8, v9);
    }

    /**
     * Converts ten items into a ObservableSource that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param v1
     *            first item
     * @param v2
     *            second item
     * @param v3
     *            third item
     * @param v4
     *            fourth item
     * @param v5
     *            fifth item
     * @param v6
     *            sixth item
     * @param v7
     *            seventh item
     * @param v8
     *            eighth item
     * @param v9
     *            ninth item
     * @param v10
     *            tenth item
     * @param <T>
     *            the type of these items
     * @return a Observable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static final <T> Observable<T> just(T v1, T v2, T v3, T v4, T v5, T v6, T v7, T v8, T v9, T v10) {
        Objects.requireNonNull(v1, "The first value is null");
        Objects.requireNonNull(v2, "The second value is null");
        Objects.requireNonNull(v3, "The third value is null");
        Objects.requireNonNull(v4, "The fourth value is null");
        Objects.requireNonNull(v5, "The fifth value is null");
        Objects.requireNonNull(v6, "The sixth value is null");
        Objects.requireNonNull(v7, "The seventh value is null");
        Objects.requireNonNull(v8, "The eighth value is null");
        Objects.requireNonNull(v9, "The ninth is null");
        Objects.requireNonNull(v10, "The tenth is null");
        
        return fromArray(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, without any transformation, while limiting the
     * number of concurrent subscriptions to these ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @param bufferSize
     *            the number of items to prefetch from each inner ObservableSource
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(Iterable<? extends ObservableSource<? extends T>> sources, int maxConcurrency, int bufferSize) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), false, maxConcurrency, bufferSize);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, without any transformation, while limiting the
     * number of concurrent subscriptions to these ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the array of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @param bufferSize
     *            the number of items to prefetch from each inner ObservableSource
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(int maxConcurrency, int bufferSize, ObservableSource<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), false, maxConcurrency, bufferSize);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, without any transformation, while limiting the
     * number of concurrent subscriptions to these ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the array of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(int maxConcurrency, ObservableSource<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), maxConcurrency);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(Iterable<? extends ObservableSource<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity());
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, without any transformation, while limiting the
     * number of concurrent subscriptions to these ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(Iterable<? extends ObservableSource<? extends T>> sources, int maxConcurrency) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), maxConcurrency);
    }

    /**
     * Flattens a ObservableSource that emits ObservableSources into a single ObservableSource that emits the items emitted by
     * those ObservableSources, without any transformation.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.oo.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T> the common element base type
     * @param sources
     *            a ObservableSource that emits ObservableSources
     * @return a Observable that emits items that are the result of flattening the ObservableSources emitted by the
     *         {@code source} ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> Observable<T> merge(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        return new ObservableFlatMap(sources, Functions.identity(), false, Integer.MAX_VALUE, bufferSize());
    }

    /**
     * Flattens a ObservableSource that emits ObservableSources into a single ObservableSource that emits the items emitted by
     * those ObservableSources, without any transformation, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.oo.png" alt="">
     * <p>
     * You can combine the items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            a ObservableSource that emits ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits items that are the result of flattening the ObservableSources emitted by the
     *         {@code source} ObservableSource
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     * @since 1.1.0
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(ObservableSource<? extends ObservableSource<? extends T>> sources, int maxConcurrency) {
        return new ObservableFlatMap(sources, Functions.identity(), false, maxConcurrency, bufferSize());
    }

    /**
     * Flattens two ObservableSources into a single ObservableSource, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be merged
     * @param p2
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items emitted by the source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        return fromArray(p1, p2).flatMap((Function)Functions.identity(), false, 2);
    }

    /**
     * Flattens three ObservableSources into a single ObservableSource, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be merged
     * @param p2
     *            a ObservableSource to be merged
     * @param p3
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items emitted by the source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2, ObservableSource<? extends T> p3) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        return fromArray(p1, p2, p3).flatMap((Function)Functions.identity(), false, 3);
    }

    /**
     * Flattens four ObservableSources into a single ObservableSource, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be merged
     * @param p2
     *            a ObservableSource to be merged
     * @param p3
     *            a ObservableSource to be merged
     * @param p4
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items emitted by the source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        Objects.requireNonNull(p4, "p4 is null");
        return fromArray(p1, p2, p3, p4).flatMap((Function)Functions.identity(), false, 4);
    }

    /**
     * Flattens an Array of ObservableSources into one ObservableSource, without any transformation.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.io.png" alt="">
     * <p>
     * You can combine items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the array of ObservableSources
     * @return a Observable that emits all of the items emitted by the ObservableSources in the Array
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> merge(ObservableSource<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), sources.length);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(Iterable<? extends ObservableSource<? extends T>> sources) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), true);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them, while limiting the number of concurrent subscriptions to these ObservableSources.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @param bufferSize
     *            the number of items to prefetch from each inner ObservableSource
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(Iterable<? extends ObservableSource<? extends T>> sources, int maxConcurrency, int bufferSize) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), true, maxConcurrency, bufferSize);
    }

    /**
     * Flattens an array of ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them, while limiting the number of concurrent subscriptions to these ObservableSources.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the array of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @param bufferSize
     *            the number of items to prefetch from each inner ObservableSource
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(int maxConcurrency, int bufferSize, ObservableSource<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), true, maxConcurrency, bufferSize);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them, while limiting the number of concurrent subscriptions to these ObservableSources.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(Iterable<? extends ObservableSource<? extends T>> sources, int maxConcurrency) {
        return fromIterable(sources).flatMap((Function)Functions.identity(), true, maxConcurrency);
    }

    /**
     * Flattens an array of ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them, while limiting the number of concurrent subscriptions to these ObservableSources.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the array of ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(int maxConcurrency, ObservableSource<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), true, maxConcurrency);
    }

    /**
     * Flattens a ObservableSource that emits ObservableSources into one ObservableSource, in a way that allows an Observer to
     * receive all successfully emitted items from all of the source ObservableSources without being interrupted by
     * an error notification from one of them.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            a ObservableSource that emits ObservableSources
     * @return a Observable that emits all of the items emitted by the ObservableSources emitted by the
     *         {@code source} ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> Observable<T> mergeDelayError(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        return new ObservableFlatMap(sources, Functions.identity(), true, Integer.MAX_VALUE, bufferSize());
    }

    /**
     * Flattens a ObservableSource that emits ObservableSources into one ObservableSource, in a way that allows an Observer to
     * receive all successfully emitted items from all of the source ObservableSources without being interrupted by
     * an error notification from one of them, while limiting the
     * number of concurrent subscriptions to these ObservableSources.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            a ObservableSource that emits ObservableSources
     * @param maxConcurrency
     *            the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits all of the items emitted by the ObservableSources emitted by the
     *         {@code source} ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(ObservableSource<? extends ObservableSource<? extends T>> sources, int maxConcurrency) {
        return new ObservableFlatMap(sources, Functions.identity(), true, maxConcurrency, bufferSize());
    }

    /**
     * Flattens two ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(ObservableSource, ObservableSource)} except that if any of the merged ObservableSources
     * notify of an error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from
     * propagating that error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if both merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be merged
     * @param p2
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items that are emitted by the two source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        return fromArray(p1, p2).flatMap((Function)Functions.identity(), true, 2);
    }

    /**
     * Flattens three ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from all of the source ObservableSources without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(ObservableSource, ObservableSource, ObservableSource)} except that if any of the merged
     * ObservableSources notify of an error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain
     * from propagating that error notification until all of the merged ObservableSources have finished emitting
     * items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be merged
     * @param p2
     *            a ObservableSource to be merged
     * @param p3
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items that are emitted by the source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2, ObservableSource<? extends T> p3) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        return fromArray(p1, p2, p3).flatMap((Function)Functions.identity(), true, 3);
    }

    /**
     * Flattens four ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from all of the source ObservableSources without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(ObservableSource, ObservableSource, ObservableSource, ObservableSource)} except that if any of
     * the merged ObservableSources notify of an error via {@link Observer#onError onError}, {@code mergeDelayError}
     * will refrain from propagating that error notification until all of the merged ObservableSources have finished
     * emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param p1
     *            a ObservableSource to be merged
     * @param p2
     *            a ObservableSource to be merged
     * @param p3
     *            a ObservableSource to be merged
     * @param p4
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items that are emitted by the source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(
            ObservableSource<? extends T> p1, ObservableSource<? extends T> p2,
            ObservableSource<? extends T> p3, ObservableSource<? extends T> p4) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(p3, "p3 is null");
        Objects.requireNonNull(p4, "p4 is null");
        return fromArray(p1, p2, p3, p4).flatMap((Function)Functions.identity(), true, 4);
    }

    /**
     * Flattens an Iterable of ObservableSources into one ObservableSource, in a way that allows an Observer to receive all
     * successfully emitted items from each of the source ObservableSources without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(ObservableSource)} except that if any of the merged ObservableSources notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged ObservableSources have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged ObservableSources send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element base type
     * @param sources
     *            the Iterable of ObservableSources
     * @return a Observable that emits items that are the result of flattening the items emitted by the
     *         ObservableSources in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> mergeDelayError(ObservableSource<? extends T>... sources) {
        return fromArray(sources).flatMap((Function)Functions.identity(), true, sources.length);
    }

    /**
     * Returns a Observable that never sends any items or notifications to an {@link Observer}.
     * <p>
     * <img width="640" height="185" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/never.png" alt="">
     * <p>
     * This ObservableSource is useful primarily for testing purposes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code never} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T>
     *            the type of items (not) emitted by the ObservableSource
     * @return a Observable that never emits any items or sends any notifications to an {@link Observer}
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Never</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @SuppressWarnings("unchecked")
    public static <T> Observable<T> never() {
        return (Observable<T>) ObservableNever.INSTANCE;
    }

    /**
     * Returns a Observable that emits a sequence of Integers within a specified range.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/range.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code range} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param start
     *            the value of the first Integer in the sequence
     * @param count
     *            the number of sequential Integers to generate
     * @return a Observable that emits a range of sequential Integers
     * @throws IllegalArgumentException
     *             if {@code count} is less than zero, or if {@code start} + {@code count} &minus; 1 exceeds
     *             {@code Integer.MAX_VALUE}
     * @see <a href="http://reactivex.io/documentation/operators/range.html">ReactiveX operators documentation: Range</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static Observable<Integer> range(final int start, final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count >= 0 required but it was " + count);
        } else
        if (count == 0) {
            return empty();
        } else
        if (count == 1) {
            return just(start);
        } else
        if ((long)start + (count - 1) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer overflow");
        }
        return new ObservableRange(start, count);
    }

    /**
     * Returns a Observable that emits a Boolean value that indicates whether two ObservableSource sequences are the
     * same by comparing the items emitted by each ObservableSource pairwise.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sequenceEqual.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sequenceEqual} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param p1
     *            the first ObservableSource to compare
     * @param p2
     *            the second ObservableSource to compare
     * @param <T>
     *            the type of items emitted by each ObservableSource
     * @return a Observable that emits a Boolean value that indicates whether the two sequences are the same
     * @see <a href="http://reactivex.io/documentation/operators/sequenceequal.html">ReactiveX operators documentation: SequenceEqual</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<Boolean> sequenceEqual(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2) {
        return sequenceEqual(p1, p2, Objects.equalsPredicate(), bufferSize());
    }

    /**
     * Returns a Observable that emits a Boolean value that indicates whether two ObservableSource sequences are the
     * same by comparing the items emitted by each ObservableSource pairwise based on the results of a specified
     * equality function.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sequenceEqual.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sequenceEqual} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param p1
     *            the first ObservableSource to compare
     * @param p2
     *            the second ObservableSource to compare
     * @param isEqual
     *            a function used to compare items emitted by each ObservableSource
     * @param <T>
     *            the type of items emitted by each ObservableSource
     * @return a Observable that emits a Boolean value that indicates whether the two ObservableSource two sequences
     *         are the same according to the specified function
     * @see <a href="http://reactivex.io/documentation/operators/sequenceequal.html">ReactiveX operators documentation: SequenceEqual</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<Boolean> sequenceEqual(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2, 
            BiPredicate<? super T, ? super T> isEqual) {
        return sequenceEqual(p1, p2, isEqual, bufferSize());
    }

    /**
     * Returns a Observable that emits a Boolean value that indicates whether two ObservableSource sequences are the
     * same by comparing the items emitted by each ObservableSource pairwise based on the results of a specified
     * equality function.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sequenceEqual.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sequenceEqual} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param p1
     *            the first ObservableSource to compare
     * @param p2
     *            the second ObservableSource to compare
     * @param isEqual
     *            a function used to compare items emitted by each ObservableSource
     * @param bufferSize
     *            the number of items to prefetch from the first and second source ObservableSource
     * @param <T>
     *            the type of items emitted by each ObservableSource
     * @return a Observable that emits a Boolean value that indicates whether the two ObservableSource two sequences
     *         are the same according to the specified function
     * @see <a href="http://reactivex.io/documentation/operators/sequenceequal.html">ReactiveX operators documentation: SequenceEqual</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<Boolean> sequenceEqual(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2, 
            BiPredicate<? super T, ? super T> isEqual, int bufferSize) {
        Objects.requireNonNull(p1, "p1 is null");
        Objects.requireNonNull(p2, "p2 is null");
        Objects.requireNonNull(isEqual, "isEqual is null");
        validateBufferSize(bufferSize, "bufferSize");
        return new ObservableSequenceEqual<T>(p1, p2, isEqual, bufferSize);
    }

    /**
     * Returns a Observable that emits a Boolean value that indicates whether two ObservableSource sequences are the
     * same by comparing the items emitted by each ObservableSource pairwise.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sequenceEqual.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sequenceEqual} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param p1
     *            the first ObservableSource to compare
     * @param p2
     *            the second ObservableSource to compare
     * @param bufferSize
     *            the number of items to prefetch from the first and second source ObservableSource
     * @param <T>
     *            the type of items emitted by each ObservableSource
     * @return a Observable that emits a Boolean value that indicates whether the two sequences are the same
     * @see <a href="http://reactivex.io/documentation/operators/sequenceequal.html">ReactiveX operators documentation: SequenceEqual</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<Boolean> sequenceEqual(ObservableSource<? extends T> p1, ObservableSource<? extends T> p2, 
            int bufferSize) {
        return sequenceEqual(p1, p2, Objects.equalsPredicate(), bufferSize);
    }

    /**
     * Converts a ObservableSource that emits ObservableSources into a ObservableSource that emits the items emitted by the
     * most recently emitted of those ObservableSources.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchDo.png" alt="">
     * <p>
     * {@code switchOnNext} subscribes to a ObservableSource that emits ObservableSources. Each time it observes one of
     * these emitted ObservableSources, the ObservableSource returned by {@code switchOnNext} begins emitting the items
     * emitted by that ObservableSource. When a new ObservableSource is emitted, {@code switchOnNext} stops emitting items
     * from the earlier-emitted ObservableSource and begins emitting items from the new one.
     * <p>
     * The resulting ObservableSource completes if both the outer ObservableSource and the last inner ObservableSource, if any, complete.
     * If the outer ObservableSource signals an onError, the inner ObservableSource is unsubscribed and the error delivered in-sequence.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the item type
     * @param sources
     *            the source ObservableSource that emits ObservableSources
     * @param bufferSize
     *            the number of items to prefetch from the inner ObservableSources
     * @return a Observable that emits the items emitted by the ObservableSource most recently emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/switch.html">ReactiveX operators documentation: Switch</a>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> switchOnNext(ObservableSource<? extends ObservableSource<? extends T>> sources, int bufferSize) {
        Objects.requireNonNull(sources, "sources is null");
        return new ObservableSwitchMap(sources, Functions.identity(), bufferSize);
    }

    /**
     * Converts a ObservableSource that emits ObservableSources into a ObservableSource that emits the items emitted by the
     * most recently emitted of those ObservableSources.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchDo.png" alt="">
     * <p>
     * {@code switchOnNext} subscribes to a ObservableSource that emits ObservableSources. Each time it observes one of
     * these emitted ObservableSources, the ObservableSource returned by {@code switchOnNext} begins emitting the items
     * emitted by that ObservableSource. When a new ObservableSource is emitted, {@code switchOnNext} stops emitting items
     * from the earlier-emitted ObservableSource and begins emitting items from the new one.
     * <p>
     * The resulting ObservableSource completes if both the outer ObservableSource and the last inner ObservableSource, if any, complete.
     * If the outer ObservableSource signals an onError, the inner ObservableSource is unsubscribed and the error delivered in-sequence.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the item type
     * @param sources
     *            the source ObservableSource that emits ObservableSources
     * @return a Observable that emits the items emitted by the ObservableSource most recently emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/switch.html">ReactiveX operators documentation: Switch</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> switchOnNext(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        return switchOnNext(sources, bufferSize());
    }

    /**
     * Converts a ObservableSource that emits ObservableSources into a ObservableSource that emits the items emitted by the
     * most recently emitted of those ObservableSources and delays any exception until all ObservableSources terminate.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchDo.png" alt="">
     * <p>
     * {@code switchOnNext} subscribes to a ObservableSource that emits ObservableSources. Each time it observes one of
     * these emitted ObservableSources, the ObservableSource returned by {@code switchOnNext} begins emitting the items
     * emitted by that ObservableSource. When a new ObservableSource is emitted, {@code switchOnNext} stops emitting items
     * from the earlier-emitted ObservableSource and begins emitting items from the new one.
     * <p>
     * The resulting ObservableSource completes if both the main ObservableSource and the last inner ObservableSource, if any, complete.
     * If the main ObservableSource signals an onError, the termination of the last inner ObservableSource will emit that error as is
     * or wrapped into a CompositeException along with the other possible errors the former inner ObservableSources signalled.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the item type
     * @param sources
     *            the source ObservableSource that emits ObservableSources
     * @return a Observable that emits the items emitted by the ObservableSource most recently emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/switch.html">ReactiveX operators documentation: Switch</a>
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> switchOnNextDelayError(ObservableSource<? extends ObservableSource<? extends T>> sources) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Converts a ObservableSource that emits ObservableSources into a ObservableSource that emits the items emitted by the
     * most recently emitted of those ObservableSources and delays any exception until all ObservableSources terminate.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchDo.png" alt="">
     * <p>
     * {@code switchOnNext} subscribes to a ObservableSource that emits ObservableSources. Each time it observes one of
     * these emitted ObservableSources, the ObservableSource returned by {@code switchOnNext} begins emitting the items
     * emitted by that ObservableSource. When a new ObservableSource is emitted, {@code switchOnNext} stops emitting items
     * from the earlier-emitted ObservableSource and begins emitting items from the new one.
     * <p>
     * The resulting ObservableSource completes if both the main ObservableSource and the last inner ObservableSource, if any, complete.
     * If the main ObservableSource signals an onError, the termination of the last inner ObservableSource will emit that error as is
     * or wrapped into a CompositeException along with the other possible errors the former inner ObservableSources signalled.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the item type
     * @param sources
     *            the source ObservableSource that emits ObservableSources
     * @param prefetch
     *            the number of items to prefetch from the inner ObservableSources
     * @return a Observable that emits the items emitted by the ObservableSource most recently emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/switch.html">ReactiveX operators documentation: Switch</a>
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> switchOnNextDelayError(ObservableSource<? extends ObservableSource<? extends T>> sources, int prefetch) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Returns a Observable that emits one item after a specified delay, and then completes.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the initial delay before emitting a single {@code 0L}
     * @param unit
     *            time units to use for {@code delay}
     * @return a Observable that emits one item after a specified delay, and then completes
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX operators documentation: Timer</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static Observable<Long> timer(long delay, TimeUnit unit) {
        return timer(delay, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits one item after a specified delay, on a specified Scheduler, and then
     * completes.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the initial delay before emitting a single 0L
     * @param unit
     *            time units to use for {@code delay}
     * @param scheduler
     *            the {@link Scheduler} to use for scheduling the item
     * @return a Observable that emits one item after a specified delay, on a specified Scheduler, and then
     *         completes
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX operators documentation: Timer</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static Observable<Long> timer(long delay, TimeUnit unit, Scheduler scheduler) {
        if (delay < 0) {
            delay = 0L;
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return new ObservableTimer(delay, unit, scheduler);
    }

    /**
     * Create a Observable by wrapping a ObservableSource <em>which has to be implemented according
     * to the Reactive-Streams specification by handling backpressure and
     * cancellation correctly; no safeguards are provided by the Observable itself</em>.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code unsafeCreate} by default doesn't operate on any particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type emitted
     * @param onSubscribe the ObservableSource instance to wrap
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> unsafeCreate(ObservableSource<T> onSubscribe) {
        Objects.requireNonNull(onSubscribe, "source is null");
        Objects.requireNonNull(onSubscribe, "onSubscribe is null");
        if (onSubscribe instanceof Observable) {
            throw new IllegalArgumentException("unsafeCreate(Observable) should be upgraded");
        }
        return new ObservableFromUnsafeSource<T>(onSubscribe);
    }

    /**
     * Constructs a ObservableSource that creates a dependent resource object which is disposed of on unsubscription.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/using.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code using} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the element type of the generated ObservableSource
     * @param <D> the type of the resource associated with the output sequence
     * @param resourceSupplier
     *            the factory function to create a resource object that depends on the ObservableSource
     * @param sourceSupplier
     *            the factory function to create a ObservableSource
     * @param disposer
     *            the function that will dispose of the resource
     * @return the ObservableSource whose lifetime controls the lifetime of the dependent resource object
     * @see <a href="http://reactivex.io/documentation/operators/using.html">ReactiveX operators documentation: Using</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, D> Observable<T> using(Callable<? extends D> resourceSupplier, Function<? super D, ? extends ObservableSource<? extends T>> sourceSupplier, Consumer<? super D> disposer) {
        return using(resourceSupplier, sourceSupplier, disposer, true);
    }

    /**
     * Constructs a ObservableSource that creates a dependent resource object which is disposed of just before 
     * termination if you have set {@code disposeEagerly} to {@code true} and unsubscription does not occur
     * before termination. Otherwise resource disposal will occur on unsubscription.  Eager disposal is
     * particularly appropriate for a synchronous ObservableSource that reuses resources. {@code disposeAction} will
     * only be called once per subscription.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/using.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code using} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the element type of the generated ObservableSource
     * @param <D> the type of the resource associated with the output sequence
     * @param resourceSupplier
     *            the factory function to create a resource object that depends on the ObservableSource
     * @param sourceSupplier
     *            the factory function to create a ObservableSource
     * @param disposer
     *            the function that will dispose of the resource
     * @param eager
     *            if {@code true} then disposal will happen either on unsubscription or just before emission of 
     *            a terminal event ({@code onComplete} or {@code onError}).
     * @return the ObservableSource whose lifetime controls the lifetime of the dependent resource object
     * @see <a href="http://reactivex.io/documentation/operators/using.html">ReactiveX operators documentation: Using</a>
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, D> Observable<T> using(Callable<? extends D> resourceSupplier, Function<? super D, ? extends ObservableSource<? extends T>> sourceSupplier, Consumer<? super D> disposer, boolean eager) {
        Objects.requireNonNull(resourceSupplier, "resourceSupplier is null");
        Objects.requireNonNull(sourceSupplier, "sourceSupplier is null");
        Objects.requireNonNull(disposer, "disposer is null");
        return new ObservableUsing<T, D>(resourceSupplier, sourceSupplier, disposer, eager);
    }

    /**
     * Validate that the given value is positive or report an IllegalArgumentException with
     * the parameter name.
     * @param bufferSize the value to validate
     * @param paramName the parameter name of the value
     * @throws IllegalArgumentException if bufferSize &lt;= 0
     */
    private static void validateBufferSize(int bufferSize, String paramName) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(paramName + " > 0 required but it was " + bufferSize);
        }
    }
    
    /**
     * Wraps an ObservableSource into an Observable if not already an Observable.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code using} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the value type
     * @param source the source ObservableSource instance
     * @return the new Observable instance or the same as the source
     * @throws NullPointerException if source is null
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> Observable<T> wrap(ObservableSource<T> source) {
        Objects.requireNonNull(source, "source is null");
        // TODO plugin wrapper?
        if (source instanceof Observable) {
            return (Observable<T>)source;
        }
        return new ObservableFromUnsafeSource<T>(source);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * items emitted, in sequence, by an Iterable of other ObservableSources.
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each of the source ObservableSources;
     * the second item emitted by the new ObservableSource will be the result of the function applied to the second
     * item emitted by each of those ObservableSources; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@code onNext} as many times as
     * the number of {@code onNext} invocations of the source ObservableSource that emits the fewest items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(Arrays.asList(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2)), (a) -&gt; a)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common value type
     * @param <R> the zipped result type
     * @param sources
     *            an Iterable of source ObservableSources
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> zip(Iterable<? extends ObservableSource<? extends T>> sources, Function<? super T[], ? extends R> zipper) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sources, "sources is null");
        return new ObservableZip<T, R>(null, sources, zipper, bufferSize(), false);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * <i>n</i> items emitted, in sequence, by the <i>n</i> ObservableSources emitted by a specified ObservableSource.
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each of the ObservableSources emitted
     * by the source ObservableSource; the second item emitted by the new ObservableSource will be the result of the
     * function applied to the second item emitted by each of those ObservableSources; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@code onNext} as many times as
     * the number of {@code onNext} invocations of the source ObservableSource that emits the fewest items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(just(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2)), (a) -&gt; a)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the value type of the inner ObservableSources
     * @param <R> the zipped result type
     * @param sources
     *            a ObservableSource of source ObservableSources
     * @param zipper
     *            a function that, when applied to an item emitted by each of the ObservableSources emitted by
     *            {@code ws}, results in an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> zip(ObservableSource<? extends ObservableSource<? extends T>> sources, final Function<T[], R> zipper) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sources, "sources is null");
        // FIXME don't want to fiddle with manual type inference, this will be inlined later anyway
        return new ObservableToList(sources, 16)
                .flatMap(new Function<List<? extends ObservableSource<? extends T>>, Observable<R>>() {
            @Override
            public Observable<R> apply(List<? extends ObservableSource<? extends T>> list) {
                return zipIterable(list, zipper, false, bufferSize());
            }
        });
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * two items emitted, in sequence, by two other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by {@code o1} and the first item
     * emitted by {@code o2}; the second item emitted by the new ObservableSource will be the result of the function
     * applied to the second item emitted by {@code o1} and the second item emitted by {@code o2}; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), (a, b) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results
     *            in an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * two items emitted, in sequence, by two other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by {@code o1} and the first item
     * emitted by {@code o2}; the second item emitted by the new ObservableSource will be the result of the function
     * applied to the second item emitted by {@code o1} and the second item emitted by {@code o2}; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), (a, b) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results
     *            in an item that will be emitted by the resulting ObservableSource
     * @param delayError delay errors from any of the source ObservableSources till the other terminates
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            BiFunction<? super T1, ? super T2, ? extends R> zipper, boolean delayError) {
        return zipArray(Functions.toFunction(zipper), delayError, bufferSize(), p1, p2);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * two items emitted, in sequence, by two other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by {@code o1} and the first item
     * emitted by {@code o2}; the second item emitted by the new ObservableSource will be the result of the function
     * applied to the second item emitted by {@code o1} and the second item emitted by {@code o2}; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), (a, b) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results
     *            in an item that will be emitted by the resulting ObservableSource
     * @param delayError delay errors from any of the source ObservableSources till the other terminates
     * @param bufferSize the number of elements to prefetch from each source ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2,
            BiFunction<? super T1, ? super T2, ? extends R> zipper, boolean delayError, int bufferSize) {
        return zipArray(Functions.toFunction(zipper), delayError, bufferSize, p1, p2);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * three items emitted, in sequence, by three other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by {@code o1}, the first item
     * emitted by {@code o2}, and the first item emitted by {@code o3}; the second item emitted by the new
     * ObservableSource will be the result of the function applied to the second item emitted by {@code o1}, the
     * second item emitted by {@code o2}, and the second item emitted by {@code o3}; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * four items emitted, in sequence, by four other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by {@code o1}, the first item
     * emitted by {@code o2}, the first item emitted by {@code o3}, and the first item emitted by {@code 04};
     * the second item emitted by the new ObservableSource will be the result of the function applied to the second
     * item emitted by each of those ObservableSources; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c, d) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <T4> the value type of the fourth source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param p4
     *            a fourth source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            ObservableSource<? extends T4> p4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * five items emitted, in sequence, by five other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by {@code o1}, the first item
     * emitted by {@code o2}, the first item emitted by {@code o3}, the first item emitted by {@code o4}, and
     * the first item emitted by {@code o5}; the second item emitted by the new ObservableSource will be the result of
     * the function applied to the second item emitted by each of those ObservableSources; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c, d, e) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <T4> the value type of the fourth source
     * @param <T5> the value type of the fifth source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param p4
     *            a fourth source ObservableSource
     * @param p5
     *            a fifth source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            ObservableSource<? extends T4> p4, ObservableSource<? extends T5> p5,
            Function5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * six items emitted, in sequence, by six other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each source ObservableSource, the
     * second item emitted by the new ObservableSource will be the result of the function applied to the second item
     * emitted by each of those ObservableSources, and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c, d, e, f) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <T4> the value type of the fourth source
     * @param <T5> the value type of the fifth source
     * @param <T6> the value type of the sixth source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param p4
     *            a fourth source ObservableSource
     * @param p5
     *            a fifth source ObservableSource
     * @param p6
     *            a sixth source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            ObservableSource<? extends T4> p4, ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            Function6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * seven items emitted, in sequence, by seven other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each source ObservableSource, the
     * second item emitted by the new ObservableSource will be the result of the function applied to the second item
     * emitted by each of those ObservableSources, and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c, d, e, f, g) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <T4> the value type of the fourth source
     * @param <T5> the value type of the fifth source
     * @param <T6> the value type of the sixth source
     * @param <T7> the value type of the seventh source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param p4
     *            a fourth source ObservableSource
     * @param p5
     *            a fifth source ObservableSource
     * @param p6
     *            a sixth source ObservableSource
     * @param p7
     *            a seventh source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            ObservableSource<? extends T4> p4, ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            ObservableSource<? extends T7> p7,
            Function7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * eight items emitted, in sequence, by eight other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each source ObservableSource, the
     * second item emitted by the new ObservableSource will be the result of the function applied to the second item
     * emitted by each of those ObservableSources, and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c, d, e, f, g, h) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <T4> the value type of the fourth source
     * @param <T5> the value type of the fifth source
     * @param <T6> the value type of the sixth source
     * @param <T7> the value type of the seventh source
     * @param <T8> the value type of the eighth source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param p4
     *            a fourth source ObservableSource
     * @param p5
     *            a fifth source ObservableSource
     * @param p6
     *            a sixth source ObservableSource
     * @param p7
     *            a seventh source ObservableSource
     * @param p8
     *            an eighth source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            ObservableSource<? extends T4> p4, ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            ObservableSource<? extends T7> p7, ObservableSource<? extends T8> p8,
            Function8<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * nine items emitted, in sequence, by nine other ObservableSources.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each source ObservableSource, the
     * second item emitted by the new ObservableSource will be the result of the function applied to the second item
     * emitted by each of those ObservableSources, and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source ObservableSource that emits the fewest
     * items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2), ..., (a, b, c, d, e, f, g, h, i) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T1> the value type of the first source
     * @param <T2> the value type of the second source
     * @param <T3> the value type of the third source
     * @param <T4> the value type of the fourth source
     * @param <T5> the value type of the fifth source
     * @param <T6> the value type of the sixth source
     * @param <T7> the value type of the seventh source
     * @param <T8> the value type of the eighth source
     * @param <T9> the value type of the ninth source
     * @param <R> the zipped result type
     * @param p1
     *            the first source ObservableSource
     * @param p2
     *            a second source ObservableSource
     * @param p3
     *            a third source ObservableSource
     * @param p4
     *            a fourth source ObservableSource
     * @param p5
     *            a fifth source ObservableSource
     * @param p6
     *            a sixth source ObservableSource
     * @param p7
     *            a seventh source ObservableSource
     * @param p8
     *            an eighth source ObservableSource
     * @param p9
     *            a ninth source ObservableSource
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> Observable<R> zip(
            ObservableSource<? extends T1> p1, ObservableSource<? extends T2> p2, ObservableSource<? extends T3> p3,
            ObservableSource<? extends T4> p4, ObservableSource<? extends T5> p5, ObservableSource<? extends T6> p6,
            ObservableSource<? extends T7> p7, ObservableSource<? extends T8> p8, ObservableSource<? extends T9> p9,
            Function9<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? super T9, ? extends R> zipper) {
        return zipArray(Functions.toFunction(zipper), false, bufferSize(), p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * items emitted, in sequence, by an array of other ObservableSources.
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each of the source ObservableSources;
     * the second item emitted by the new ObservableSource will be the result of the function applied to the second
     * item emitted by each of those ObservableSources; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@code onNext} as many times as
     * the number of {@code onNext} invocations of the source ObservableSource that emits the fewest items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it
     * is possible those other sources will never be able to run to completion (and thus not calling
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(new ObservableSource[]{range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2)}, (a) -&gt;
     * a)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion
     * or unsubscription.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the common element type
     * @param <R> the result type
     * @param sources
     *            an array of source ObservableSources
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @param delayError
     *            delay errors signalled by any of the source ObservableSource until all ObservableSources terminate
     * @param bufferSize 
     *            the number of elements to prefetch from each source ObservableSource
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> zipArray(Function<? super T[], ? extends R> zipper,
            boolean delayError, int bufferSize, ObservableSource<? extends T>... sources) {
        if (sources.length == 0) {
            return empty();
        }
        Objects.requireNonNull(zipper, "zipper is null");
        validateBufferSize(bufferSize, "bufferSize");
        return new ObservableZip<T, R>(sources, null, zipper, bufferSize, delayError);
    }

    /**
     * Returns a Observable that emits the results of a specified combiner function applied to combinations of
     * items emitted, in sequence, by an Iterable of other ObservableSources.
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new ObservableSource
     * will be the result of the function applied to the first item emitted by each of the source ObservableSources;
     * the second item emitted by the new ObservableSource will be the result of the function applied to the second
     * item emitted by each of those ObservableSources; and so forth.
     * <p>
     * The resulting {@code ObservableSource<R>} returned from {@code zip} will invoke {@code onNext} as many times as
     * the number of {@code onNext} invocations of the source ObservableSource that emits the fewest items.
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>zip(Arrays.asList(range(1, 5).doOnCompleted(action1), range(6, 5).doOnCompleted(action2)), (a) -&gt; a)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * 
     * @param sources
     *            an Iterable of source ObservableSources
     * @param zipper
     *            a function that, when applied to an item emitted by each of the source ObservableSources, results in
     *            an item that will be emitted by the resulting ObservableSource
     * @param delayError
     *            delay errors signalled by any of the source ObservableSource until all ObservableSources terminate
     * @param bufferSize 
     *            the number of elements to prefetch from each source ObservableSource
     * @param <T> the common source value type
     * @param <R> the zipped result type
     * @return a Observable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> Observable<R> zipIterable(Iterable<? extends ObservableSource<? extends T>> sources,
            Function<? super T[], ? extends R> zipper, boolean delayError, 
            int bufferSize) {
        Objects.requireNonNull(zipper, "zipper is null");
        Objects.requireNonNull(sources, "sources is null");
        validateBufferSize(bufferSize, "bufferSize");
        return new ObservableZip<T, R>(null, sources, zipper, bufferSize, delayError);
    }

    // ***************************************************************************************************
    // Instance operators
    // ***************************************************************************************************

    /**
     * Returns a Observable that emits a Boolean that indicates whether all of the items emitted by the source
     * ObservableSource satisfy a condition.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/all.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code all} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function that evaluates an item and returns a Boolean
     * @return a Observable that emits {@code true} if all items emitted by the source ObservableSource satisfy the
     *         predicate; otherwise, {@code false}
     * @see <a href="http://reactivex.io/documentation/operators/all.html">ReactiveX operators documentation: All</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Boolean> all(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new ObservableAll<T>(this, predicate);
    }

    /**
     * Mirrors the ObservableSource (current or provided) that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            a ObservableSource competing to react first
     * @return a Observable that emits the same sequence as whichever of the source ObservableSources first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> ambWith(ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return amb(this, other);
    }

    /**
     * Returns a Observable that emits {@code true} if any item emitted by the source ObservableSource satisfies a
     * specified condition, otherwise {@code false}. <em>Note:</em> this always emits {@code false} if the
     * source ObservableSource is empty.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/exists.png" alt="">
     * <p>
     * In Rx.Net this is the {@code any} Observer but we renamed it in RxJava to better match Java naming
     * idioms.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code exists} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition to test items emitted by the source ObservableSource
     * @return a Observable that emits a Boolean that indicates whether any item emitted by the source
     *         ObservableSource satisfies the {@code predicate}
     * @see <a href="http://reactivex.io/documentation/operators/contains.html">ReactiveX operators documentation: Contains</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Boolean> any(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new ObservableAny<T>(this, predicate);
    }

    /**
     * Hides the identity of this Observable and its Subscription.
     * <p>Allows hiding extra features such as {@link Processor}'s
     * {@link Subscriber} methods or preventing certain identity-based 
     * optimizations (fusion).
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code hide} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @return the new Observable instance
     * 
     * @since 2.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> hide() {
        // TODO hide the Disposable as well
        return new ObservableFromUnsafeSource<T>(this);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each containing {@code count} items. When the source
     * ObservableSource completes or encounters an error, the resulting ObservableSource emits the current buffer and
     * propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer3.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items in each buffer before it should be emitted
     * @return a Observable that emits connected, non-overlapping buffers, each containing at most
     *         {@code count} items from the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> buffer(int count) {
        return buffer(count, count);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits buffers every {@code skip} items, each containing {@code count} items. When the source
     * ObservableSource completes or encounters an error, the resulting ObservableSource emits the current buffer and
     * propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer4.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each buffer before it should be emitted
     * @param skip
     *            how many items emitted by the source ObservableSource should be skipped before starting a new
     *            buffer. Note that when {@code skip} and {@code count} are equal, this is the same operation as
     *            {@link #buffer(int)}.
     * @return a Observable that emits buffers for every {@code skip} item from the source ObservableSource and
     *         containing at most {@code count} items
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> buffer(int count, int skip) {
        return buffer(count, skip, ArrayListSupplier.<T>asCallable());
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits buffers every {@code skip} items, each containing {@code count} items. When the source
     * ObservableSource completes or encounters an error, the resulting ObservableSource emits the current buffer and
     * propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer4.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param count
     *            the maximum size of each buffer before it should be emitted
     * @param skip
     *            how many items emitted by the source ObservableSource should be skipped before starting a new
     *            buffer. Note that when {@code skip} and {@code count} are equal, this is the same operation as
     *            {@link #buffer(int)}.
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer 
     * @return a Observable that emits buffers for every {@code skip} item from the source ObservableSource and
     *         containing at most {@code count} items
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U extends Collection<? super T>> Observable<U> buffer(int count, int skip, Callable<U> bufferSupplier) {
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        if (skip <= 0) {
            throw new IllegalArgumentException("skip > 0 required but it was " + count);
        }
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return new ObservableBuffer<T, U>(this, count, skip, bufferSupplier);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each containing {@code count} items. When the source
     * ObservableSource completes or encounters an error, the resulting ObservableSource emits the current buffer and
     * propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer3.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param count
     *            the maximum number of items in each buffer before it should be emitted
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer 
     * @return a Observable that emits connected, non-overlapping buffers, each containing at most
     *         {@code count} items from the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U extends Collection<? super T>> Observable<U> buffer(int count, Callable<U> bufferSupplier) {
        return buffer(count, count, bufferSupplier);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource starts a new buffer periodically, as determined by the {@code timeshift} argument. It emits
     * each buffer after a fixed timespan, specified by the {@code timespan} argument. When the source
     * ObservableSource completes or encounters an error, the resulting ObservableSource emits the current buffer and
     * propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer7.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted
     * @param timeskip
     *            the period of time after which a new buffer will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @return a Observable that emits new buffers of items emitted by the source ObservableSource periodically after
     *         a fixed timespan has elapsed
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<List<T>> buffer(long timespan, long timeskip, TimeUnit unit) {
        return buffer(timespan, timeskip, unit, Schedulers.computation(), ArrayListSupplier.<T>asCallable());
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource starts a new buffer periodically, as determined by the {@code timeshift} argument, and on the
     * specified {@code scheduler}. It emits each buffer after a fixed timespan, specified by the
     * {@code timespan} argument. When the source ObservableSource completes or encounters an error, the resulting
     * ObservableSource emits the current buffer and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer7.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted
     * @param timeskip
     *            the period of time after which a new buffer will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return a Observable that emits new buffers of items emitted by the source ObservableSource periodically after
     *         a fixed timespan has elapsed
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<List<T>> buffer(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler) {
        return buffer(timespan, timeskip, unit, scheduler, ArrayListSupplier.<T>asCallable());
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource starts a new buffer periodically, as determined by the {@code timeshift} argument, and on the
     * specified {@code scheduler}. It emits each buffer after a fixed timespan, specified by the
     * {@code timespan} argument. When the source ObservableSource completes or encounters an error, the resulting
     * ObservableSource emits the current buffer and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer7.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param timespan
     *            the period of time each buffer collects items before it is emitted
     * @param timeskip
     *            the period of time after which a new buffer will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer 
     * @return a Observable that emits new buffers of items emitted by the source ObservableSource periodically after
     *         a fixed timespan has elapsed
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final <U extends Collection<? super T>> Observable<U> buffer(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler, Callable<U> bufferSupplier) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return new ObservableBufferTimed<T, U>(this, timespan, timeskip, unit, scheduler, bufferSupplier, Integer.MAX_VALUE, false);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument. When the source ObservableSource completes or encounters an error, the resulting
     * ObservableSource emits the current buffer and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer5.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @return a Observable that emits connected, non-overlapping buffers of items emitted by the source
     *         ObservableSource within a fixed duration
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<List<T>> buffer(long timespan, TimeUnit unit) {
        return buffer(timespan, unit, Integer.MAX_VALUE, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument or a maximum size specified by the {@code count} argument (whichever is reached
     * first). When the source ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current buffer and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer6.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each buffer before it is emitted
     * @return a Observable that emits connected, non-overlapping buffers of items emitted by the source
     *         ObservableSource, after a fixed duration or when the buffer reaches maximum capacity (whichever occurs
     *         first)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<List<T>> buffer(long timespan, TimeUnit unit, int count) {
        return buffer(timespan, unit, count, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument as measured on the specified {@code scheduler}, or a maximum size specified by
     * the {@code count} argument (whichever is reached first). When the source ObservableSource completes or
     * encounters an error, the resulting ObservableSource emits the current buffer and propagates the notification
     * from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer6.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each buffer before it is emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return a Observable that emits connected, non-overlapping buffers of items emitted by the source
     *         ObservableSource after a fixed duration or when the buffer reaches maximum capacity (whichever occurs
     *         first)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<List<T>> buffer(long timespan, TimeUnit unit, int count, Scheduler scheduler) {
        return buffer(timespan, unit, count, scheduler, ArrayListSupplier.<T>asCallable(), false);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument as measured on the specified {@code scheduler}, or a maximum size specified by
     * the {@code count} argument (whichever is reached first). When the source ObservableSource completes or
     * encounters an error, the resulting ObservableSource emits the current buffer and propagates the notification
     * from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer6.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each buffer before it is emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer
     * @param restartTimerOnMaxSize if true the time window is restarted when the max capacity of the current buffer
     *            is reached 
     * @return a Observable that emits connected, non-overlapping buffers of items emitted by the source
     *         ObservableSource after a fixed duration or when the buffer reaches maximum capacity (whichever occurs
     *         first)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final <U extends Collection<? super T>> Observable<U> buffer(
            long timespan, TimeUnit unit, 
            int count, Scheduler scheduler, 
            Callable<U> bufferSupplier, 
            boolean restartTimerOnMaxSize) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        return new ObservableBufferTimed<T, U>(this, timespan, timespan, unit, scheduler, bufferSupplier, count, restartTimerOnMaxSize);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument and on the specified {@code scheduler}. When the source ObservableSource completes or
     * encounters an error, the resulting ObservableSource emits the current buffer and propagates the notification
     * from the source ObservableSource.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer5.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return a Observable that emits connected, non-overlapping buffers of items emitted by the source
     *         ObservableSource within a fixed duration
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<List<T>> buffer(long timespan, TimeUnit unit, Scheduler scheduler) {
        return buffer(timespan, unit, Integer.MAX_VALUE, scheduler, ArrayListSupplier.<T>asCallable(), false);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits buffers that it creates when the specified {@code bufferOpenings} ObservableSource emits an
     * item, and closes when the ObservableSource returned from {@code bufferClosingSelector} emits an item.
     * <p>
     * <img width="640" height="470" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer2.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <TOpening> the element type of the buffer-opening ObservableSource
     * @param <TClosing> the element type of the individual buffer-closing ObservableSources
     * @param bufferOpenings
     *            the ObservableSource that, when it emits an item, causes a new buffer to be created
     * @param bufferClosingSelector
     *            the {@link Function} that is used to produce a ObservableSource for every buffer created. When this
     *            ObservableSource emits an item, the associated buffer is emitted.
     * @return a Observable that emits buffers, containing items from the source ObservableSource, that are created
     *         and closed when the specified ObservableSources emit items
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <TOpening, TClosing> Observable<List<T>> buffer(
            ObservableSource<? extends TOpening> bufferOpenings,
            Function<? super TOpening, ? extends ObservableSource<? extends TClosing>> bufferClosingSelector) {
        return buffer(bufferOpenings, bufferClosingSelector, ArrayListSupplier.<T>asCallable());
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits buffers that it creates when the specified {@code bufferOpenings} ObservableSource emits an
     * item, and closes when the ObservableSource returned from {@code bufferClosingSelector} emits an item.
     * <p>
     * <img width="640" height="470" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer2.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param <TOpening> the element type of the buffer-opening ObservableSource
     * @param <TClosing> the element type of the individual buffer-closing ObservableSources
     * @param bufferOpenings
     *            the ObservableSource that, when it emits an item, causes a new buffer to be created
     * @param bufferClosingSelector
     *            the {@link Function} that is used to produce a ObservableSource for every buffer created. When this
     *            ObservableSource emits an item, the associated buffer is emitted.
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer
     * @return a Observable that emits buffers, containing items from the source ObservableSource, that are created
     *         and closed when the specified ObservableSources emit items
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <TOpening, TClosing, U extends Collection<? super T>> Observable<U> buffer(
            ObservableSource<? extends TOpening> bufferOpenings,
            Function<? super TOpening, ? extends ObservableSource<? extends TClosing>> bufferClosingSelector,
            Callable<U> bufferSupplier) {
        Objects.requireNonNull(bufferOpenings, "bufferOpenings is null");
        Objects.requireNonNull(bufferClosingSelector, "bufferClosingSelector is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return new ObservableBufferBoundary<T, U, TOpening, TClosing>(this, bufferOpenings, bufferClosingSelector, bufferSupplier);
    }

    /**
     * Returns a Observable that emits non-overlapping buffered items from the source ObservableSource each time the
     * specified boundary ObservableSource emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer8.png" alt="">
     * <p>
     * Completion of either the source or the boundary ObservableSource causes the returned ObservableSource to emit the
     * latest buffer and complete.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B>
     *            the boundary value type (ignored)
     * @param boundary
     *            the boundary ObservableSource
     * @return a Observable that emits buffered items from the source ObservableSource when the boundary ObservableSource
     *         emits an item
     * @see #buffer(ObservableSource, int)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<List<T>> buffer(ObservableSource<B> boundary) {
        return buffer(boundary, ArrayListSupplier.<T>asCallable());
    }

    /**
     * Returns a Observable that emits non-overlapping buffered items from the source ObservableSource each time the
     * specified boundary ObservableSource emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer8.png" alt="">
     * <p>
     * Completion of either the source or the boundary ObservableSource causes the returned ObservableSource to emit the
     * latest buffer and complete.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B>
     *            the boundary value type (ignored)
     * @param boundary
     *            the boundary ObservableSource
     * @param initialCapacity
     *            the initial capacity of each buffer chunk
     * @return a Observable that emits buffered items from the source ObservableSource when the boundary ObservableSource
     *         emits an item
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     * @see #buffer(ObservableSource)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<List<T>> buffer(ObservableSource<B> boundary, final int initialCapacity) {
        return buffer(boundary, new Callable<List<T>>() {
            @Override
            public List<T> call() {
                return new ArrayList<T>(initialCapacity);
            }
        });
    }

    /**
     * Returns a Observable that emits non-overlapping buffered items from the source ObservableSource each time the
     * specified boundary ObservableSource emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer8.png" alt="">
     * <p>
     * Completion of either the source or the boundary ObservableSource causes the returned ObservableSource to emit the
     * latest buffer and complete.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param <B>
     *            the boundary value type (ignored)
     * @param boundary
     *            the boundary ObservableSource
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer
     * @return a Observable that emits buffered items from the source ObservableSource when the boundary ObservableSource
     *         emits an item
     * @see #buffer(ObservableSource, int)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B, U extends Collection<? super T>> Observable<U> buffer(ObservableSource<B> boundary, Callable<U> bufferSupplier) {
        Objects.requireNonNull(boundary, "boundary is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return new ObservableBufferExactBoundary<T, U, B>(this, boundary, bufferSupplier);
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers. It emits the current buffer and replaces it with a
     * new buffer whenever the ObservableSource produced by the specified {@code bufferClosingSelector} emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer1.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B> the value type of the boundary-providing ObservableSource
     * @param boundarySupplier
     *            a {@link Callable} that produces a ObservableSource that governs the boundary between buffers.
     *            Whenever the source {@code ObservableSource} emits an item, {@code buffer} emits the current buffer and
     *            begins to fill a new one
     * @return a Observable that emits a connected, non-overlapping buffer of items from the source ObservableSource
     *         each time the ObservableSource created with the {@code bufferClosingSelector} argument emits an item
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<List<T>> buffer(Callable<? extends ObservableSource<B>> boundarySupplier) {
        return buffer(boundarySupplier, ArrayListSupplier.<T>asCallable());
        
    }

    /**
     * Returns a Observable that emits buffers of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping buffers. It emits the current buffer and replaces it with a
     * new buffer whenever the ObservableSource produced by the specified {@code bufferClosingSelector} emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer1.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the collection subclass type to buffer into
     * @param <B> the value type of the boundary-providing ObservableSource
     * @param boundarySupplier
     *            a {@link Callable} that produces a ObservableSource that governs the boundary between buffers.
     *            Whenever the source {@code ObservableSource} emits an item, {@code buffer} emits the current buffer and
     *            begins to fill a new one
     * @param bufferSupplier
     *            a factory function that returns an instance of the collection subclass to be used and returned
     *            as the buffer
     * @return a Observable that emits a connected, non-overlapping buffer of items from the source ObservableSource
     *         each time the ObservableSource created with the {@code bufferClosingSelector} argument emits an item
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B, U extends Collection<? super T>> Observable<U> buffer(Callable<? extends ObservableSource<B>> boundarySupplier, Callable<U> bufferSupplier) {
        Objects.requireNonNull(boundarySupplier, "boundarySupplier is null");
        Objects.requireNonNull(bufferSupplier, "bufferSupplier is null");
        return new ObservableBufferBoundarySupplier<T, U, B>(this, boundarySupplier, bufferSupplier);
    }

    /**
     * Returns a Observable that subscribes to this ObservableSource lazily, caches all of its events 
     * and replays them, in the same order as received, to all the downstream subscribers.
     * <p>
     * <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/cache.png" alt="">
     * <p>
     * This is useful when you want a ObservableSource to cache responses and you can't control the
     * subscribe/unsubscribe behavior of all the {@link Subscriber}s.
     * <p>
     * The operator subscribes only when the first downstream subscriber subscribes and maintains
     * a single subscription towards this ObservableSource. In contrast, the operator family of {@link #replay()}
     * that return a {@link ConnectableFlowable} require an explicit call to {@link ConnectableFlowable#connect()}.  
     * <p>
     * <em>Note:</em> You sacrifice the ability to unsubscribe from the origin when you use the {@code cache}
     * Observer so be careful not to use this Observer on ObservableSources that emit an infinite or very large number
     * of items that will use up memory. 
     * A possible workaround is to apply `takeUntil` with a predicate or
     * another source before (and perhaps after) the application of cache().
     * <pre><code>
     * AtomicBoolean shouldStop = new AtomicBoolean();
     * 
     * source.takeUntil(v -&gt; shouldStop.get())
     *       .cache()
     *       .takeUntil(v -&gt; shouldStop.get())
     *       .subscribe(...);
     * </code></pre>
     * Since the operator doesn't allow clearing the cached values either, the possible workaround is
     * to forget all references to it via {@link #onTerminateDetach()} applied along with the previous
     * workaround: 
     * <pre><code>
     * AtomicBoolean shouldStop = new AtomicBoolean();
     * 
     * source.takeUntil(v -&gt; shouldStop.get())
     *       .onTerminateDetach()
     *       .cache()
     *       .takeUntil(v -&gt; shouldStop.get())
     *       .onTerminateDetach()
     *       .subscribe(...);
     * </code></pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code cache} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that, when first subscribed to, caches all of its items and notifications for the
     *         benefit of subsequent subscribers
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> cache() {
        return ObservableCache.from(this);
    }

    /**
     * Returns a Observable that subscribes to this ObservableSource lazily, caches all of its events 
     * and replays them, in the same order as received, to all the downstream subscribers.
     * <p>
     * <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/cache.png" alt="">
     * <p>
     * This is useful when you want a ObservableSource to cache responses and you can't control the
     * subscribe/unsubscribe behavior of all the {@link Subscriber}s.
     * <p>
     * The operator subscribes only when the first downstream subscriber subscribes and maintains
     * a single subscription towards this ObservableSource. In contrast, the operator family of {@link #replay()}
     * that return a {@link ConnectableFlowable} require an explicit call to {@link ConnectableFlowable#connect()}.  
     * <p>
     * <em>Note:</em> You sacrifice the ability to unsubscribe from the origin when you use the {@code cache}
     * Observer so be careful not to use this Observer on ObservableSources that emit an infinite or very large number
     * of items that will use up memory.
     * A possible workaround is to apply `takeUntil` with a predicate or
     * another source before (and perhaps after) the application of cache().
     * <pre><code>
     * AtomicBoolean shouldStop = new AtomicBoolean();
     * 
     * source.takeUntil(v -&gt; shouldStop.get())
     *       .cache()
     *       .takeUntil(v -&gt; shouldStop.get())
     *       .subscribe(...);
     * </code></pre>
     * Since the operator doesn't allow clearing the cached values either, the possible workaround is
     * to forget all references to it via {@link #onTerminateDetach()} applied along with the previous
     * workaround: 
     * <pre><code>
     * AtomicBoolean shouldStop = new AtomicBoolean();
     * 
     * source.takeUntil(v -&gt; shouldStop.get())
     *       .onTerminateDetach()
     *       .cache()
     *       .takeUntil(v -&gt; shouldStop.get())
     *       .onTerminateDetach()
     *       .subscribe(...);
     * </code></pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code cache} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * <p>
     * <em>Note:</em> The capacity hint is not an upper bound on cache size. For that, consider
     * {@link #replay(int)} in combination with {@link ConnectableFlowable#autoConnect()} or similar.
     * 
     * @param initialCapacity hint for number of items to cache (for optimizing underlying data structure)
     * @return a Observable that, when first subscribed to, caches all of its items and notifications for the
     *         benefit of subsequent subscribers
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> cacheWithInitialCapacity(int initialCapacity) {
        return ObservableCache.from(this, initialCapacity);
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource, converted to the specified
     * type.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/cast.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code cast} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the output value type cast to
     * @param clazz
     *            the target class type that {@code cast} will cast the items emitted by the source ObservableSource
     *            into before emitting them from the resulting ObservableSource
     * @return a Observable that emits each item from the source ObservableSource after converting it to the
     *         specified type
     * @see <a href="http://reactivex.io/documentation/operators/map.html">ReactiveX operators documentation: Map</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> cast(final Class<U> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        return map(new Function<T, U>() {
            @Override
            public U apply(T v) {
                return clazz.cast(v);
            }
        });
    }

    /**
     * Collects items emitted by the source ObservableSource into a single mutable data structure and returns an
     * ObservableSource that emits this structure.
     * <p>
     * <img width="640" height="330" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/collect.png" alt="">
     * <p>
     * This is a simplified version of {@code reduce} that does not need to return the state on each pass.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code collect} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the accumulator and output type
     * @param initialValueSupplier
     *           the mutable data structure that will collect the items
     * @param collector
     *           a function that accepts the {@code state} and an emitted item, and modifies {@code state}
     *           accordingly
     * @return a Observable that emits the result of collecting the values emitted by the source ObservableSource
     *         into a single mutable data structure
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> collect(Callable<? extends U> initialValueSupplier, BiConsumer<? super U, ? super T> collector) {
        Objects.requireNonNull(initialValueSupplier, "initialValueSupplier is null");
        Objects.requireNonNull(collector, "collector is null");
        return new ObservableCollect<T, U>(this, initialValueSupplier, collector);
    }

    /**
     * Collects items emitted by the source ObservableSource into a single mutable data structure and returns an
     * ObservableSource that emits this structure.
     * <p>
     * <img width="640" height="330" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/collect.png" alt="">
     * <p>
     * This is a simplified version of {@code reduce} that does not need to return the state on each pass.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code collect} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the accumulator and output type
     * @param initialValue
     *           the mutable data structure that will collect the items
     * @param collector
     *           a function that accepts the {@code state} and an emitted item, and modifies {@code state}
     *           accordingly
     * @return a Observable that emits the result of collecting the values emitted by the source ObservableSource
     *         into a single mutable data structure
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> collectInto(final U initialValue, BiConsumer<? super U, ? super T> collector) {
        Objects.requireNonNull(initialValue, "initialValue is null");
        return collect(new Callable<U>() {
            @Override
            public U call() {
                return initialValue;
            }
        }, collector);
    }

    /**
     * Transform a ObservableSource by applying a particular Transformer function to it.
     * <p>
     * This method operates on the ObservableSource itself whereas {@link #lift} operates on the ObservableSource's
     * Subscribers or Observers.
     * <p>
     * If the operator you are creating is designed to act on the individual items emitted by a source
     * ObservableSource, use {@link #lift}. If your operator is designed to transform the source ObservableSource as a whole
     * (for instance, by applying a particular set of existing RxJava operators to it) use {@code compose}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code compose} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the value type of the output ObservableSource
     * @param composer implements the function that transforms the source ObservableSource
     * @return the source ObservableSource, transformed by the transformer function
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Implementing-Your-Own-Operators">RxJava wiki: Implementing Your Own Operators</a>
     */
    public final <R> Observable<R> compose(Function<? super Observable<T>, ? extends ObservableSource<R>> composer) {
        return wrap(to(composer));
    }


    /**
     * Returns a new Observable that emits items resulting from applying a function that you supply to each item
     * emitted by the source ObservableSource, where that function returns a ObservableSource, and then emitting the items
     * that result from concatenating those resulting ObservableSources.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concatMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the type of the inner ObservableSource sources and thus the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and concatenating the ObservableSources obtained from this transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        return concatMap(mapper, 2);
    }

    /**
     * Returns a new Observable that emits items resulting from applying a function that you supply to each item
     * emitted by the source ObservableSource, where that function returns a ObservableSource, and then emitting the items
     * that result from concatenating those resulting ObservableSources.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concatMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the type of the inner ObservableSource sources and thus the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param prefetch
     *            the number of elements to prefetch from the current Observable
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and concatenating the ObservableSources obtained from this transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper, int prefetch) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (prefetch <= 0) {
            throw new IllegalArgumentException("prefetch > 0 required but it was " + prefetch);
        }
        return new ObservableConcatMap<T, R>(this, mapper, prefetch);
    }

    /**
     * Maps each of the items into a ObservableSource, subscribes to them one after the other,
     * one at a time and emits their values in order
     * while delaying any error from either this or any of the inner ObservableSources
     * till all of them terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMapDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the result value type
     * @param mapper the function that maps the items of this ObservableSource into the inner ObservableSources.
     * @return the new ObservableSource instance with the concatenation behavior
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMapDelayError(Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Maps each of the items into a ObservableSource, subscribes to them one after the other,
     * one at a time and emits their values in order
     * while delaying any error from either this or any of the inner ObservableSources
     * till all of them terminate.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMapDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the result value type
     * @param mapper the function that maps the items of this ObservableSource into the inner ObservableSources.
     * @param prefetch
     *            the number of elements to prefetch from the current Observable
     * @param tillTheEnd
     *            if true, all errors from the outer and inner ObservableSource sources are delayed until the end,
     *            if false, an error from the main source is signalled when the current ObservableSource source terminates
     * @return the new ObservableSource instance with the concatenation behavior
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMapDelayError(Function<? super T, ? extends ObservableSource<? extends R>> mapper, 
            int prefetch, boolean tillTheEnd) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Maps a sequence of values into ObservableSources and concatenates these ObservableSources eagerly into a single
     * ObservableSource.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them in
     * order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <R> the value type
     * @param mapper the function that maps a sequence of values into a sequence of ObservableSources that will be
     *               eagerly concatenated
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMapEager(Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Maps a sequence of values into ObservableSources and concatenates these ObservableSources eagerly into a single
     * ObservableSource.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them in
     * order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <R> the value type
     * @param mapper the function that maps a sequence of values into a sequence of ObservableSources that will be
     *               eagerly concatenated
     * @param maxConcurrency the maximum number of concurrent subscribed ObservableSources
     * @param prefetch hints about the number of expected source sequence values
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMapEager(Function<? super T, ? extends ObservableSource<? extends R>> mapper, 
            int maxConcurrency, int prefetch) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Maps a sequence of values into ObservableSources and concatenates these ObservableSources eagerly into a single
     * ObservableSource.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them in
     * order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <R> the value type
     * @param mapper the function that maps a sequence of values into a sequence of ObservableSources that will be
     *               eagerly concatenated
     * @param tillTheEnd
     *            if true, all errors from the outer and inner ObservableSource sources are delayed until the end,
     *            if false, an error from the main source is signalled when the current ObservableSource source terminates
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMapEagerDelayError(Function<? super T, ? extends ObservableSource<? extends R>> mapper, 
            boolean tillTheEnd) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Maps a sequence of values into ObservableSources and concatenates these ObservableSources eagerly into a single
     * ObservableSource.
     * <p>
     * Eager concatenation means that once a subscriber subscribes, this operator subscribes to all of the
     * source ObservableSources. The operator buffers the values emitted by these ObservableSources and then drains them in
     * order, each one after the previous one completes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param <R> the value type
     * @param mapper the function that maps a sequence of values into a sequence of ObservableSources that will be
     *               eagerly concatenated
     * @param maxConcurrency the maximum number of concurrent subscribed ObservableSources
     * @param prefetch
     *               the number of elements to prefetch from each source ObservableSource
     * @param tillTheEnd
     *               if true, exceptions from the current Observable and all the inner ObservableSources are delayed until
     *               all of them terminate, if false, exception from the current Observable is delayed until the
     *               currently running ObservableSource terminates
     * @return the new ObservableSource instance with the specified concatenation behavior
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> concatMapEagerDelayError(Function<? super T, ? extends ObservableSource<? extends R>> mapper, 
            int maxConcurrency, int prefetch, boolean tillTheEnd) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Returns a Observable that concatenate each item emitted by the source ObservableSource with the values in an
     * Iterable corresponding to that item that is generated by a selector.
     * <p>
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of item emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns an Iterable sequence of values for when given an item emitted by the
     *            source ObservableSource
     * @return a Observable that emits the results of concatenating the items emitted by the source ObservableSource with
     *         the values in the Iterables corresponding to those items, as generated by {@code collectionSelector}
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> concatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return concatMap(new Function<T, Observable<U>>() {
            @Override
            public Observable<U> apply(T v) throws Exception {
                return fromIterable(mapper.apply(v));
            }
        });
    }

    /**
     * Returns a Observable that concatenate each item emitted by the source ObservableSource with the values in an
     * Iterable corresponding to that item that is generated by a selector.
     * <p>
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of item emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns an Iterable sequence of values for when given an item emitted by the
     *            source ObservableSource
     * @param prefetch 
     *            the number of elements to prefetch from the current Observable
     * @return a Observable that emits the results of concatenating the items emitted by the source ObservableSource with
     *         the values in the Iterables corresponding to those items, as generated by {@code collectionSelector}
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> concatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper, int prefetch) {
        return concatMap(new Function<T, Observable<U>>() {
            @Override
            public Observable<U> apply(T v) throws Exception {
                return fromIterable(mapper.apply(v));
            }
        }, prefetch);
    }

    /**
     * Returns a Observable that emits the items emitted from the current ObservableSource, then the next, one after
     * the other, without interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            a ObservableSource to be concatenated after the current
     * @return a Observable that emits items emitted by the two source ObservableSources, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> concatWith(ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return concat(this, other);
    }

    /**
     * Returns a Observable that emits a Boolean that indicates whether the source ObservableSource emitted a
     * specified item.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/contains.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code contains} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param element
     *            the item to search for in the emissions from the source ObservableSource
     * @return a Observable that emits {@code true} if the specified item is emitted by the source ObservableSource,
     *         or {@code false} if the source ObservableSource completes without emitting that item
     * @see <a href="http://reactivex.io/documentation/operators/contains.html">ReactiveX operators documentation: Contains</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Boolean> contains(final Object element) {
        Objects.requireNonNull(element, "element is null");
        return any(new Predicate<T>() {
            @Override
            public boolean test(T v) {
                return Objects.equals(v, element);
            }
        });
    }

    /**
     * Returns a Observable that counts the total number of items emitted by the source ObservableSource and emits
     * this count as a 64-bit Long.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/longCount.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code countLong} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits a single item: the number of items emitted by the source ObservableSource as a
     *         64-bit Long item
     * @see <a href="http://reactivex.io/documentation/operators/count.html">ReactiveX operators documentation: Count</a>
     * @see #count()
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Long> count() {
        return new ObservableCount<T>(this);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, except that it drops items emitted by the
     * source ObservableSource that are followed by another item within a computed debounce duration.
     * <p>
     * <img width="640" height="425" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code debounce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the debounce value type (ignored)
     * @param debounceSelector
     *            function to retrieve a sequence that indicates the throttle duration for each item
     * @return a Observable that omits items emitted by the source ObservableSource that are followed by another item
     *         within a computed debounce duration
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<T> debounce(Function<? super T, ? extends ObservableSource<U>> debounceSelector) {
        Objects.requireNonNull(debounceSelector, "debounceSelector is null");
        return new ObservableDebounce<T, U>(this, debounceSelector);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, except that it drops items emitted by the
     * source ObservableSource that are followed by newer items before a timeout value expires. The timer resets on
     * each emission.
     * <p>
     * <em>Note:</em> If items keep being emitted by the source ObservableSource faster than the timeout then no items
     * will be emitted by the resulting ObservableSource.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code debounce} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            the time each item has to be "the most recent" of those emitted by the source ObservableSource to
     *            ensure that it's not dropped
     * @param unit
     *            the {@link TimeUnit} for the timeout
     * @return a Observable that filters out items from the source ObservableSource that are too quickly followed by
     *         newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see #throttleWithTimeout(long, TimeUnit)
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> debounce(long timeout, TimeUnit unit) {
        return debounce(timeout, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, except that it drops items emitted by the
     * source ObservableSource that are followed by newer items before a timeout value expires on a specified
     * Scheduler. The timer resets on each emission.
     * <p>
     * <em>Note:</em> If items keep being emitted by the source ObservableSource faster than the timeout then no items
     * will be emitted by the resulting ObservableSource.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.s.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            the time each item has to be "the most recent" of those emitted by the source ObservableSource to
     *            ensure that it's not dropped
     * @param unit
     *            the unit of time for the specified timeout
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle the timeout for each
     *            item
     * @return a Observable that filters out items from the source ObservableSource that are too quickly followed by
     *         newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see #throttleWithTimeout(long, TimeUnit, Scheduler)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> debounce(long timeout, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableDebounceTimed<T>(this, timeout, unit, scheduler);
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource or a specified default item
     * if the source ObservableSource is empty.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/defaultIfEmpty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code defaultIfEmpty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the item to emit if the source ObservableSource emits no items
     * @return a Observable that emits either the specified default item if the source ObservableSource emits no
     *         items, or the items emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/defaultifempty.html">ReactiveX operators documentation: DefaultIfEmpty</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> defaultIfEmpty(T defaultValue) {
        Objects.requireNonNull(defaultValue, "value is null");
        return switchIfEmpty(just(defaultValue));
    }

    /**
     * Returns a Observable that delays the emissions of the source ObservableSource via another ObservableSource on a
     * per-item basis.
     * <p>
     * <img width="640" height="450" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.o.png" alt="">
     * <p>
     * <em>Note:</em> the resulting ObservableSource will immediately propagate any {@code onError} notification
     * from the source ObservableSource.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the item delay value type (ignored)
     * @param itemDelay
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource, which is
     *            then used to delay the emission of that item by the resulting ObservableSource until the ObservableSource
     *            returned from {@code itemDelay} emits an item
     * @return a Observable that delays the emissions of the source ObservableSource via another ObservableSource on a
     *         per-item basis
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<T> delay(final Function<? super T, ? extends ObservableSource<U>> itemDelay) {
        // TODO a more efficient implementation if necessary
        Objects.requireNonNull(itemDelay, "itemDelay is null");
        return flatMap(new Function<T, Observable<T>>() {
            @Override
            public Observable<T> apply(final T v) throws Exception {
                return new ObservableTake<U>(itemDelay.apply(v), 1).map(new Function<U, T>() {
                    @Override
                    public T apply(U u) {
                        return v;
                    }
                }).defaultIfEmpty(v);
            }
        });
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource shifted forward in time by a
     * specified delay. Error notifications from the source ObservableSource are not delayed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the delay to shift the source by
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @return the source ObservableSource shifted in time by the specified delay
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> delay(long delay, TimeUnit unit) {
        return delay(delay, unit, Schedulers.computation(), false);
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource shifted forward in time by a
     * specified delay. Error notifications from the source ObservableSource are not delayed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the delay to shift the source by
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @param delayError
     *            if true, the upstream exception is signalled with the given delay, after all preceding normal elements,
     *            if false, the upstream exception is signalled immediately
     * @return the source ObservableSource shifted in time by the specified delay
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> delay(long delay, TimeUnit unit, boolean delayError) {
        return delay(delay, unit, Schedulers.computation(), delayError);
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource shifted forward in time by a
     * specified delay. Error notifications from the source ObservableSource are not delayed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the delay to shift the source by
     * @param unit
     *            the time unit of {@code delay}
     * @param scheduler
     *            the {@link Scheduler} to use for delaying
     * @return the source ObservableSource shifted in time by the specified delay
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> delay(long delay, TimeUnit unit, Scheduler scheduler) {
        return delay(delay, unit, scheduler, false);
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource shifted forward in time by a
     * specified delay. Error notifications from the source ObservableSource are not delayed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the delay to shift the source by
     * @param unit
     *            the time unit of {@code delay}
     * @param scheduler
     *            the {@link Scheduler} to use for delaying
     * @param delayError
     *            if true, the upstream exception is signalled with the given delay, after all preceding normal elements,
     *            if false, the upstream exception is signalled immediately
     * @return the source ObservableSource shifted in time by the specified delay
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> delay(long delay, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        
        return new ObservableDelay<T>(this, delay, unit, scheduler, delayError);
    }

    /**
     * Returns a Observable that delays the subscription to and emissions from the source ObservableSource via another
     * ObservableSource on a per-item basis.
     * <p>
     * <img width="640" height="450" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.oo.png" alt="">
     * <p>
     * <em>Note:</em> the resulting ObservableSource will immediately propagate any {@code onError} notification
     * from the source ObservableSource.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the subscription delay value type (ignored)
     * @param <V>
     *            the item delay value type (ignored)
     * @param subscriptionDelay
     *            a function that returns a ObservableSource that triggers the subscription to the source ObservableSource
     *            once it emits any item
     * @param itemDelay
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource, which is
     *            then used to delay the emission of that item by the resulting ObservableSource until the ObservableSource
     *            returned from {@code itemDelay} emits an item
     * @return a Observable that delays the subscription and emissions of the source ObservableSource via another
     *         ObservableSource on a per-item basis
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, V> Observable<T> delay(ObservableSource<U> subscriptionDelay,
            Function<? super T, ? extends ObservableSource<V>> itemDelay) {
        return delaySubscription(subscriptionDelay).delay(itemDelay);
    }

    /**
     * Returns an Observable that delays the subscription to this Observable
     * until the other Observable emits an element or completes normally.
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This method does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the value type of the other Observable, irrelevant
     * @param other the other Observable that should trigger the subscription
     *        to this Observable.
     * @return an Observable that delays the subscription to this Observable
     *         until the other Observable emits an element or completes normally.
     */
    @Experimental
    public final <U> Observable<T> delaySubscription(ObservableSource<U> other) {
        Objects.requireNonNull(other, "other is null");
        return new ObservableDelaySubscriptionOther<T, U>(this, other);
    }

    /**
     * Returns a Observable that delays the subscription to the source ObservableSource by a given amount of time.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delaySubscription.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the time to delay the subscription
     * @param unit
     *            the time unit of {@code delay}
     * @return a Observable that delays the subscription to the source ObservableSource by the given amount
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> delaySubscription(long delay, TimeUnit unit) {
        return delaySubscription(delay, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that delays the subscription to the source ObservableSource by a given amount of time,
     * both waiting and subscribing on a given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delaySubscription.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the time to delay the subscription
     * @param unit
     *            the time unit of {@code delay}
     * @param scheduler
     *            the Scheduler on which the waiting and subscription will happen
     * @return a Observable that delays the subscription to the source ObservableSource by a given
     *         amount, waiting and subscribing on the given Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> delaySubscription(long delay, TimeUnit unit, Scheduler scheduler) {
        return delaySubscription(timer(delay, unit, scheduler));
    }

    /**
     * Returns a Observable that reverses the effect of {@link #materialize materialize} by transforming the
     * {@link Notification} objects emitted by the source ObservableSource into the items or notifications they
     * represent.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/dematerialize.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code dematerialize} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T2> the output value type
     * @return a Observable that emits the items and notifications embedded in the {@link Notification} objects
     *         emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/materialize-dematerialize.html">ReactiveX operators documentation: Dematerialize</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <T2> Observable<T2> dematerialize() {
        @SuppressWarnings("unchecked")
        Observable<Try<Optional<T2>>> m = (Observable<Try<Optional<T2>>>)this;
        return new ObservableDematerialize<T2>(m);
    }
    
    /**
     * Returns a Observable that emits all items emitted by the source ObservableSource that are distinct.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinct.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinct} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits only those items emitted by the source ObservableSource that are distinct from
     *         each other
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> distinct() {
        return distinct((Function)Functions.identity(), new Callable<Collection<T>>() {
            @Override
            public Collection<T> call() {
                return new HashSet<T>();
            }
        });
    }

    /**
     * Returns a Observable that emits all items emitted by the source ObservableSource that are distinct according
     * to a key selector function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinct.key.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinct} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type
     * @param keySelector
     *            a function that projects an emitted item to a key value that is used to decide whether an item
     *            is distinct from another one or not
     * @return a Observable that emits those items emitted by the source ObservableSource that have distinct keys
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<T> distinct(Function<? super T, K> keySelector) {
        return distinct(keySelector, new Callable<Collection<K>>() {
            @Override
            public Collection<K> call() {
                return new HashSet<K>();
            }
        });
    }

    /**
     * Returns a Observable that emits all items emitted by the source ObservableSource that are distinct according
     * to a key selector function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinct.key.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinct} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type
     * @param keySelector
     *            a function that projects an emitted item to a key value that is used to decide whether an item
     *            is distinct from another one or not
     * @param collectionSupplier
     *            function called for each individual Subscriber to return a Collection subtype for holding the extracted
     *            keys and whose add() method's return indicates uniqueness.
     * @return a Observable that emits those items emitted by the source ObservableSource that have distinct keys
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<T> distinct(Function<? super T, K> keySelector, Callable<? extends Collection<? super K>> collectionSupplier) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(collectionSupplier, "collectionSupplier is null");
        return ObservableDistinct.withCollection(this, keySelector, collectionSupplier);
    }

    /**
     * Returns a Observable that emits all items emitted by the source ObservableSource that are distinct from their
     * immediate predecessors.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinctUntilChanged.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinctUntilChanged} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits those items from the source ObservableSource that are distinct from their
     *         immediate predecessors
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> distinctUntilChanged() {
        return ObservableDistinct.<T>untilChanged(this);
    }

    /**
     * Returns a Observable that emits all items emitted by the source ObservableSource that are distinct from their
     * immediate predecessors, according to a key selector function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinctUntilChanged.key.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinctUntilChanged} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type
     * @param keySelector
     *            a function that projects an emitted item to a key value that is used to decide whether an item
     *            is distinct from another one or not
     * @return a Observable that emits those items from the source ObservableSource whose keys are distinct from
     *         those of their immediate predecessors
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<T> distinctUntilChanged(Function<? super T, K> keySelector) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        return ObservableDistinct.untilChanged(this, keySelector);
    }

    /**
     * Returns a Observable that emits all items emitted by the source ObservableSource that are distinct from their
     * immediate predecessors when compared with each other via the provided comparator function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinctUntilChanged.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinctUntilChanged} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param comparer the function that receives the previous item and the current item and is
     *                   expected to return true if the two are equal, thus skipping the current value.
     * @return a Observable that emits those items from the source ObservableSource that are distinct from their
     *         immediate predecessors
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical
     *        with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> distinctUntilChanged(BiPredicate<? super T, ? super T> comparer) {
        Objects.requireNonNull(comparer, "comparer is null");
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Registers an {@link Action} to be called when this ObservableSource invokes either
     * {@link Subscriber#onComplete onComplete} or {@link Subscriber#onError onError}.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/finallyDo.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doAfterTerminate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param onFinally
     *            an {@link Action} to be invoked when the source ObservableSource finishes
     * @return a Observable that emits the same items as the source ObservableSource, then invokes the
     *         {@link Action}
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     * @see #doOnTerminate(Action)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doAfterTerminate(Action onFinally) {
        Objects.requireNonNull(onFinally, "onFinally is null");
        return doOnEach(Functions.emptyConsumer(), Functions.emptyConsumer(), Functions.EMPTY_ACTION, onFinally);
    }
    
    /**
     * Calls the unsubscribe {@code Action} if the downstream unsubscribes the sequence.
     * <p>
     * The action is shared between subscriptions and thus may be called concurrently from multiple
     * threads; the action must be thread safe.
     * <p>
     * If the action throws a runtime exception, that exception is rethrown by the {@code unsubscribe()} call,
     * sometimes as a {@code CompositeException} if there were multiple exceptions along the way.
     * <p>
     * Note that terminal events trigger the action unless the {@code ObservableSource} is subscribed to via {@code unsafeSubscribe()}.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnUnsubscribe.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnUnsubscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param onCancel
     *            the action that gets called when the source {@code ObservableSource}'s Subscription is cancelled
     * @return the source {@code ObservableSource} modified so as to call this Action when appropriate
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnCancel(Action onCancel) {
        return doOnLifecycle(Functions.emptyConsumer(), onCancel);
    }

    /**
     * Modifies the source ObservableSource so that it invokes an action when it calls {@code onCompleted}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnCompleted.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnCompleted} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onComplete
     *            the action to invoke when the source ObservableSource calls {@code onCompleted}
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnComplete(Action onComplete) {
        return doOnEach(Functions.emptyConsumer(), Functions.emptyConsumer(), onComplete, Functions.EMPTY_ACTION);
    }

    /**
     * Calls the appropriate onXXX consumer (shared between all subscribers) whenever a signal with the same type 
     * passes through, before forwarding them to downstream.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnEach.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    private Observable<T> doOnEach(Consumer<? super T> onNext, Consumer<? super Throwable> onError, Action onComplete, Action onAfterTerminate) {
        Objects.requireNonNull(onNext, "onNext is null");
        Objects.requireNonNull(onError, "onError is null");
        Objects.requireNonNull(onComplete, "onComplete is null");
        Objects.requireNonNull(onAfterTerminate, "onAfterTerminate is null");
        return new ObservableDoOnEach<T>(this, onNext, onError, onComplete, onAfterTerminate);
    }

    /**
     * Modifies the source ObservableSource so that it invokes an action for each item it emits.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnEach.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNotification
     *            the action to invoke for each item emitted by the source ObservableSource
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnEach(final Consumer<? super Try<Optional<T>>> onNotification) {
        Objects.requireNonNull(onNotification, "consumer is null");
        return doOnEach(
                new Consumer<T>() {
                    @Override
                    public void accept(T v) throws Exception {
                        onNotification.accept(Try.ofValue(Optional.of(v)));
                    }
                },
                new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) throws Exception {
                        onNotification.accept(Try.<Optional<T>>ofError(e));
                    }
                },
                new Action() {
                    @Override
                    public void run() throws Exception {
                        onNotification.accept(Try.ofValue(Optional.<T>empty()));
                    }
                },
                Functions.EMPTY_ACTION
                );
    }

    /**
     * Modifies the source ObservableSource so that it notifies an Observer for each item and terminal event it emits.
     * <p>
     * In case the {@code onError} of the supplied observer throws, the downstream will receive a composite
     * exception containing the original exception and the exception thrown by {@code onError}. If either the
     * {@code onNext} or the {@code onCompleted} method of the supplied observer throws, the downstream will be
     * terminated and will receive this thrown exception.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnEach.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param observer
     *            the observer to be notified about onNext, onError and onCompleted events on its
     *            respective methods before the actual downstream Subscriber gets notified.
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnEach(final Observer<? super T> observer) {
        Objects.requireNonNull(observer, "observer is null");
        return doOnEach(new Consumer<T>() {
            @Override
            public void accept(T v) {
                observer.onNext(v);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                observer.onError(e);
            }
        }, new Action() {
            @Override
            public void run() {
                observer.onComplete();
            }
        }, Functions.EMPTY_ACTION);
    }

    /**
     * Modifies the source ObservableSource so that it invokes an action if it calls {@code onError}.
     * <p>
     * In case the {@code onError} action throws, the downstream will receive a composite exception containing
     * the original exception and the exception thrown by {@code onError}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnError.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onError
     *            the action to invoke if the source ObservableSource calls {@code onError}
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnError(Consumer<? super Throwable> onError) {
        return doOnEach(Functions.emptyConsumer(), onError, Functions.EMPTY_ACTION, Functions.EMPTY_ACTION);
    }

    /**
     * Calls the appropriate onXXX method (shared between all Subscribers) for the lifecycle events of
     * the sequence (subscription, cancellation, requesting).
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnNext.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onSubscribe
     *              a Consumer called with the Subscription sent via Subscriber.onSubscribe()
     * @param onCancel
     *              called when the downstream cancels the Subscription via cancel()
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnLifecycle(final Consumer<? super Disposable> onSubscribe, final Action onCancel) {
        Objects.requireNonNull(onSubscribe, "onSubscribe is null");
        Objects.requireNonNull(onCancel, "onCancel is null");
        return new ObservableDoOnLifecycle<T>(this, onSubscribe, onCancel);
    }

    /**
     * Modifies the source ObservableSource so that it invokes an action when it calls {@code onNext}.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnNext.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            the action to invoke when the source ObservableSource calls {@code onNext}
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnNext(Consumer<? super T> onNext) {
        return doOnEach(onNext, Functions.emptyConsumer(), Functions.EMPTY_ACTION, Functions.EMPTY_ACTION);
    }

    /**
     * Modifies the source {@code ObservableSource} so that it invokes the given action when it is subscribed from
     * its subscribers. Each subscription will result in an invocation of the given action except when the
     * source {@code ObservableSource} is reference counted, in which case the source {@code ObservableSource} will invoke
     * the given action for the first subscription.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnSubscribe.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnSubscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param onSubscribe
     *            the Consumer that gets called when a Subscriber subscribes to the current {@code Observable}
     * @return the source {@code ObservableSource} modified so as to call this Consumer when appropriate
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnSubscribe(Consumer<? super Disposable> onSubscribe) {
        return doOnLifecycle(onSubscribe, Functions.EMPTY_ACTION);
    }

    /**
     * Modifies the source ObservableSource so that it invokes an action when it calls {@code onCompleted} or
     * {@code onError}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnTerminate.png" alt="">
     * <p>
     * This differs from {@code finallyDo} in that this happens <em>before</em> the {@code onCompleted} or
     * {@code onError} notification.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnTerminate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onTerminate
     *            the action to invoke when the source ObservableSource calls {@code onCompleted} or {@code onError}
     * @return the source ObservableSource with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     * @see #doAfterTerminate(Action)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> doOnTerminate(final Action onTerminate) {
        return doOnEach(Functions.emptyConsumer(), new Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) throws Exception {
                onTerminate.run();
            }
        }, onTerminate, Functions.EMPTY_ACTION);
    }

    /**
     * Returns a Observable that emits the single item at a specified index in a sequence of emissions from a
     * source ObservableSource.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/elementAt.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code elementAt} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param index
     *            the zero-based index of the item to retrieve
     * @return a Observable that emits a single item: the item at the specified position in the sequence of
     *         those emitted by the source ObservableSource
     * @throws IndexOutOfBoundsException
     *             if {@code index} is greater than or equal to the number of items emitted by the source
     *             ObservableSource, or
     *             if {@code index} is less than 0
     * @see <a href="http://reactivex.io/documentation/operators/elementat.html">ReactiveX operators documentation: ElementAt</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> elementAt(long index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index >= 0 required but it was " + index);
        }
        return new ObservableElementAt<T>(this, index, null);
    }

    /**
     * Returns a Observable that emits the item found at a specified index in a sequence of emissions from a
     * source ObservableSource, or a default item if that index is out of range.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/elementAtOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code elementAtOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param index
     *            the zero-based index of the item to retrieve
     * @param defaultValue
     *            the default item
     * @return a Observable that emits the item at the specified position in the sequence emitted by the source
     *         ObservableSource, or the default item if that index is outside the bounds of the source sequence
     * @throws IndexOutOfBoundsException
     *             if {@code index} is less than 0
     * @see <a href="http://reactivex.io/documentation/operators/elementat.html">ReactiveX operators documentation: ElementAt</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> elementAt(long index, T defaultValue) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index >= 0 required but it was " + index);
        }
        Objects.requireNonNull(defaultValue, "defaultValue is null");
        return new ObservableElementAt<T>(this, index, defaultValue);
    }

    /**
     * Filters items emitted by a ObservableSource by only emitting those that satisfy a specified predicate.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/filter.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code filter} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function that evaluates each item emitted by the source ObservableSource, returning {@code true}
     *            if it passes the filter
     * @return a Observable that emits only those items emitted by the source ObservableSource that the filter
     *         evaluates as {@code true}
     * @see <a href="http://reactivex.io/documentation/operators/filter.html">ReactiveX operators documentation: Filter</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new ObservableFilter<T>(this, predicate);
    }

    /**
     * Returns a Observable that emits only the very first item emitted by the source ObservableSource, or notifies
     * of an {@code NoSuchElementException} if the source ObservableSource is empty.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/first.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code first} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits only the very first item emitted by the source ObservableSource, or raises an
     *         {@code NoSuchElementException} if the source ObservableSource is empty
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> first() {
        return take(1).single();
    }

    /**
     * Returns a Observable that emits only the very first item emitted by the source ObservableSource, or a default
     * item if the source ObservableSource completes without emitting anything.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/firstOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code firstOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the default item to emit if the source ObservableSource doesn't emit anything
     * @return a Observable that emits only the very first item from the source, or a default item if the
     *         source ObservableSource completes without emitting any items
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> first(T defaultValue) {
        return take(1).single(defaultValue);
    }

    /**
     * Returns a Observable that emits items based on applying a function that you supply to each item emitted
     * by the source ObservableSource, where that function returns a ObservableSource, and then merging those resulting
     * ObservableSources and emitting the results of this merger.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the value type of the inner ObservableSources and the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and merging the results of the ObservableSources obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        return flatMap(mapper, false);
    }

    /**
     * Returns a Observable that emits items based on applying a function that you supply to each item emitted
     * by the source ObservableSource, where that function returns a ObservableSource, and then merging those resulting
     * ObservableSources and emitting the results of this merger.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the value type of the inner ObservableSources and the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param delayErrors
     *            if true, exceptions from the current Observable and all inner ObservableSources are delayed until all of them terminate
     *            if false, the first one signalling an exception will terminate the whole sequence immediately
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and merging the results of the ObservableSources obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper, boolean delayErrors) {
        return flatMap(mapper, delayErrors, Integer.MAX_VALUE);
    }

    /**
     * Returns a Observable that emits items based on applying a function that you supply to each item emitted
     * by the source ObservableSource, where that function returns a ObservableSource, and then merging those resulting
     * ObservableSources and emitting the results of this merger, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the value type of the inner ObservableSources and the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @param delayErrors
     *            if true, exceptions from the current Observable and all inner ObservableSources are delayed until all of them terminate
     *            if false, the first one signalling an exception will terminate the whole sequence immediately
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and merging the results of the ObservableSources obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper, boolean delayErrors, int maxConcurrency) {
        return flatMap(mapper, delayErrors, maxConcurrency, bufferSize());
    }

    /**
     * Returns a Observable that emits items based on applying a function that you supply to each item emitted
     * by the source ObservableSource, where that function returns a ObservableSource, and then merging those resulting
     * ObservableSources and emitting the results of this merger, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the value type of the inner ObservableSources and the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @param delayErrors
     *            if true, exceptions from the current Observable and all inner ObservableSources are delayed until all of them terminate
     *            if false, the first one signalling an exception will terminate the whole sequence immediately
     * @param bufferSize
     *            the number of elements to prefetch from each inner ObservableSource
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and merging the results of the ObservableSources obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper,
            boolean delayErrors, int maxConcurrency, int bufferSize) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency > 0 required but it was " + maxConcurrency);
        }
        validateBufferSize(bufferSize, "bufferSize");
        if (this instanceof ObservableJust) {
            ObservableJust<T> scalar = (ObservableJust<T>) this;
            return scalar.scalarFlatMap(mapper);
        }
        return new ObservableFlatMap<T, R>(this, mapper, delayErrors, maxConcurrency, bufferSize);
    }

    /**
     * Returns a Observable that applies a function to each item emitted or notification raised by the source
     * ObservableSource and then flattens the ObservableSources returned from these functions and emits the resulting items.
     * <p>
     * <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.nce.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the result type
     * @param onNextMapper
     *            a function that returns a ObservableSource to merge for each item emitted by the source ObservableSource
     * @param onErrorMapper
     *            a function that returns a ObservableSource to merge for an onError notification from the source
     *            ObservableSource
     * @param onCompleteSupplier
     *            a function that returns a ObservableSource to merge for an onCompleted notification from the source
     *            ObservableSource
     * @return a Observable that emits the results of merging the ObservableSources returned from applying the
     *         specified functions to the emissions and notifications of the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> flatMap(
            Function<? super T, ? extends ObservableSource<? extends R>> onNextMapper,
            Function<? super Throwable, ? extends ObservableSource<? extends R>> onErrorMapper,
            Callable<? extends ObservableSource<? extends R>> onCompleteSupplier) {
        Objects.requireNonNull(onNextMapper, "onNextMapper is null");
        Objects.requireNonNull(onErrorMapper, "onErrorMapper is null");
        Objects.requireNonNull(onCompleteSupplier, "onCompleteSupplier is null");
        return merge(new ObservableMapNotification<T, R>(this, onNextMapper, onErrorMapper, onCompleteSupplier));
    }

    /**
     * Returns a Observable that applies a function to each item emitted or notification raised by the source
     * ObservableSource and then flattens the ObservableSources returned from these functions and emits the resulting items, 
     * while limiting the maximum number of concurrent subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.nce.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the result type
     * @param onNextMapper
     *            a function that returns a ObservableSource to merge for each item emitted by the source ObservableSource
     * @param onErrorMapper
     *            a function that returns a ObservableSource to merge for an onError notification from the source
     *            ObservableSource
     * @param onCompleteSupplier
     *            a function that returns a ObservableSource to merge for an onCompleted notification from the source
     *            ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits the results of merging the ObservableSources returned from applying the
     *         specified functions to the emissions and notifications of the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> flatMap(
            Function<? super T, ? extends ObservableSource<? extends R>> onNextMapper,
            Function<Throwable, ? extends ObservableSource<? extends R>> onErrorMapper,
            Callable<? extends ObservableSource<? extends R>> onCompleteSupplier,
            int maxConcurrency) {
        Objects.requireNonNull(onNextMapper, "onNextMapper is null");
        Objects.requireNonNull(onErrorMapper, "onErrorMapper is null");
        Objects.requireNonNull(onCompleteSupplier, "onCompleteSupplier is null");
        return merge(new ObservableMapNotification<T, R>(this, onNextMapper, onErrorMapper, onCompleteSupplier), maxConcurrency);
    }

    /**
     * Returns a Observable that emits items based on applying a function that you supply to each item emitted
     * by the source ObservableSource, where that function returns a ObservableSource, and then merging those resulting
     * ObservableSources and emitting the results of this merger, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the value type of the inner ObservableSources and the output type
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits the result of applying the transformation function to each item emitted
     *         by the source ObservableSource and merging the results of the ObservableSources obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper, int maxConcurrency) {
        return flatMap(mapper, false, maxConcurrency, bufferSize());
    }

    /**
     * Returns a Observable that emits the results of a specified function to the pair of values emitted by the
     * source ObservableSource and a specified collection ObservableSource.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt="">
     * <dl>
     *  <dd>The operator honors backpressure from downstream. The outer {@code ObservableSource} is consumed
     *  in unbounded mode (i.e., no backpressure is applied to it). The inner {@code ObservableSource}s are expected to honor
     *  backpressure; if violated, the operator <em>may</em> signal {@code MissingBackpressureException}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource
     * @param resultSelector
     *            a function that combines one item emitted by each of the source and collection ObservableSources and
     *            returns an item to be emitted by the resulting ObservableSource
     * @return a Observable that emits the results of applying a function to a pair of values emitted by the
     *         source ObservableSource and the collection ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends U>> mapper, 
            BiFunction<? super T, ? super U, ? extends R> resultSelector) {
        return flatMap(mapper, resultSelector, false, bufferSize(), bufferSize());
    }

    /**
     * Returns a Observable that emits the results of a specified function to the pair of values emitted by the
     * source ObservableSource and a specified collection ObservableSource.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt="">
     * <dl>
     *  <dd>The operator honors backpressure from downstream. The outer {@code ObservableSource} is consumed
     *  in unbounded mode (i.e., no backpressure is applied to it). The inner {@code ObservableSource}s are expected to honor
     *  backpressure; if violated, the operator <em>may</em> signal {@code MissingBackpressureException}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource
     * @param combiner
     *            a function that combines one item emitted by each of the source and collection ObservableSources and
     *            returns an item to be emitted by the resulting ObservableSource
     * @param delayErrors
     *            if true, exceptions from the current Observable and all inner ObservableSources are delayed until all of them terminate
     *            if false, the first one signalling an exception will terminate the whole sequence immediately
     * @return a Observable that emits the results of applying a function to a pair of values emitted by the
     *         source ObservableSource and the collection ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends U>> mapper, 
            BiFunction<? super T, ? super U, ? extends R> combiner, boolean delayErrors) {
        return flatMap(mapper, combiner, delayErrors, bufferSize(), bufferSize());
    }

    /**
     * Returns a Observable that emits the results of a specified function to the pair of values emitted by the
     * source ObservableSource and a specified collection ObservableSource, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource
     * @param combiner
     *            a function that combines one item emitted by each of the source and collection ObservableSources and
     *            returns an item to be emitted by the resulting ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @param delayErrors
     *            if true, exceptions from the current Observable and all inner ObservableSources are delayed until all of them terminate
     *            if false, the first one signalling an exception will terminate the whole sequence immediately
     * @return a Observable that emits the results of applying a function to a pair of values emitted by the
     *         source ObservableSource and the collection ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends U>> mapper, 
            BiFunction<? super T, ? super U, ? extends R> combiner, boolean delayErrors, int maxConcurrency) {
        return flatMap(mapper, combiner, delayErrors, maxConcurrency, bufferSize());
    }

    /**
     * Returns a Observable that emits the results of a specified function to the pair of values emitted by the
     * source ObservableSource and a specified collection ObservableSource, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource
     * @param combiner
     *            a function that combines one item emitted by each of the source and collection ObservableSources and
     *            returns an item to be emitted by the resulting ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @param delayErrors
     *            if true, exceptions from the current Observable and all inner ObservableSources are delayed until all of them terminate
     *            if false, the first one signalling an exception will terminate the whole sequence immediately
     * @param bufferSize
     *            the number of elements to prefetch from the inner ObservableSources.
     * @return a Observable that emits the results of applying a function to a pair of values emitted by the
     *         source ObservableSource and the collection ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> flatMap(final Function<? super T, ? extends ObservableSource<? extends U>> mapper, 
            final BiFunction<? super T, ? super U, ? extends R> combiner, boolean delayErrors, int maxConcurrency, int bufferSize) {
        Objects.requireNonNull(mapper, "mapper is null");
        Objects.requireNonNull(combiner, "combiner is null");
        return flatMap(new Function<T, Observable<R>>() {
            @Override
            public Observable<R> apply(final T t) throws Exception {
                @SuppressWarnings("unchecked")
                Observable<U> u = (Observable<U>)mapper.apply(t);
                return u.map(new Function<U, R>() {
                    @Override
                    public R apply(U w) throws Exception{
                        return combiner.apply(t, w);
                    }
                });
            }
        }, delayErrors, maxConcurrency, bufferSize);
    }

    /**
     * Returns a Observable that emits the results of a specified function to the pair of values emitted by the
     * source ObservableSource and a specified collection ObservableSource, while limiting the maximum number of concurrent
     * subscriptions to these ObservableSources.
     * <p>
     * <!-- <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource
     * @param combiner
     *            a function that combines one item emitted by each of the source and collection ObservableSources and
     *            returns an item to be emitted by the resulting ObservableSource
     * @param maxConcurrency
     *         the maximum number of ObservableSources that may be subscribed to concurrently
     * @return a Observable that emits the results of applying a function to a pair of values emitted by the
     *         source ObservableSource and the collection ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends U>> mapper, 
            BiFunction<? super T, ? super U, ? extends R> combiner, int maxConcurrency) {
        return flatMap(mapper, combiner, false, maxConcurrency, bufferSize());
    }

    /**
     * Returns a Observable that merges each item emitted by the source ObservableSource with the values in an
     * Iterable corresponding to that item that is generated by a selector.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMapIterable.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of item emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns an Iterable sequence of values for when given an item emitted by the
     *            source ObservableSource
     * @return a Observable that emits the results of merging the items emitted by the source ObservableSource with
     *         the values in the Iterables corresponding to those items, as generated by {@code collectionSelector}
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> flatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return flatMap(new Function<T, Observable<U>>() {
            @Override
            public Observable<U> apply(T v) throws Exception {
                return fromIterable(mapper.apply(v));
            }
        });
    }

    /**
     * Returns a Observable that emits the results of applying a function to the pair of values from the source
     * ObservableSource and an Iterable corresponding to that item that is generated by a selector.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMapIterable.r.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the collection element type
     * @param <V>
     *            the type of item emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns an Iterable sequence of values for each item emitted by the source
     *            ObservableSource
     * @param resultSelector
     *            a function that returns an item based on the item emitted by the source ObservableSource and the
     *            Iterable returned for that item by the {@code collectionSelector}
     * @return a Observable that emits the items returned by {@code resultSelector} for each item in the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, V> Observable<V> flatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper, 
            BiFunction<? super T, ? super U, ? extends V> resultSelector) {
        return flatMap(new Function<T, Observable<U>>() {
            @Override
            public Observable<U> apply(T t) throws Exception {
                return fromIterable(mapper.apply(t));
            }
        }, resultSelector, false, bufferSize(), bufferSize());
    }

    /**
     * Returns a Observable that merges each item emitted by the source ObservableSource with the values in an
     * Iterable corresponding to that item that is generated by a selector.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMapIterable.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of item emitted by the resulting ObservableSource
     * @param mapper
     *            a function that returns an Iterable sequence of values for when given an item emitted by the
     *            source ObservableSource
     * @param bufferSize
     *            the number of elements to prefetch from the current Observable
     * @return a Observable that emits the results of merging the items emitted by the source ObservableSource with
     *         the values in the Iterables corresponding to those items, as generated by {@code collectionSelector}
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> flatMapIterable(final Function<? super T, ? extends Iterable<? extends U>> mapper, int bufferSize) {
        return flatMap(new Function<T, Observable<U>>() {
            @Override
            public Observable<U> apply(T v) throws Exception {
                return fromIterable(mapper.apply(v));
            }
        }, false, bufferSize);
    }

    /**
     * Subscribes to the {@link ObservableSource} and receives notifications for each element.
     * <p>
     * Alias to {@link #subscribe(Consumer)}
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Consumer} to execute for each item.
     * @return 
     *            a Disposable that allows cancelling an asynchronous sequence
     * @throws IllegalArgumentException
     *             if {@code onNext} is null
     * @throws RuntimeException
     *             if the ObservableSource calls {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable forEach(Consumer<? super T> onNext) {
        return subscribe(onNext);
    }

    /**
     * Subscribes to the {@link ObservableSource} and receives notifications for each element until the
     * onNext Predicate returns false.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Predicate} to execute for each item.
     * @return 
     *            a Disposable that allows cancelling an asynchronous sequence
     * @throws NullPointerException
     *             if {@code onNext} is null
     * @throws RuntimeException
     *             if the ObservableSource calls {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable forEachWhile(Predicate<? super T> onNext) {
        return forEachWhile(onNext, RxJavaPlugins.errorConsumer(), Functions.EMPTY_ACTION);
    }

    /**
     * Subscribes to the {@link ObservableSource} and receives notifications for each element and error events until the
     * onNext Predicate returns false.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Predicate} to execute for each item.
     * @param onError
     *            {@link Consumer} to execute when an error is emitted.
     * @return 
     *            a Disposable that allows cancelling an asynchronous sequence
     * @throws NullPointerException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable forEachWhile(Predicate<? super T> onNext, Consumer<? super Throwable> onError) {
        return forEachWhile(onNext, onError, Functions.EMPTY_ACTION);
    }

    /**
     * Subscribes to the {@link ObservableSource} and receives notifications for each element and the terminal events until the
     * onNext Predicate returns false.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Predicate} to execute for each item.
     * @param onError
     *            {@link Consumer} to execute when an error is emitted.
     * @param onComplete
     *            {@link Action} to execute when completion is signalled.
     * @return 
     *            a Disposable that allows cancelling an asynchronous sequence
     * @throws NullPointerException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable forEachWhile(final Predicate<? super T> onNext, Consumer<? super Throwable> onError,
            final Action onComplete) {
        Objects.requireNonNull(onNext, "onNext is null");
        Objects.requireNonNull(onError, "onError is null");
        Objects.requireNonNull(onComplete, "onComplete is null");

        final AtomicReference<Disposable> subscription = new AtomicReference<Disposable>();
        return subscribe(new Consumer<T>() {
            @Override
            public void accept(T v) throws Exception {
                if (!onNext.test(v)) {
                    subscription.get().dispose();
                    onComplete.run();
                }
            }
        }, onError, onComplete, new Consumer<Disposable>() {
            @Override
            public void accept(Disposable s) {
                subscription.lazySet(s);
            }
        });
    }

    /**
     * Groups the items emitted by an {@code ObservableSource} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s. The emitted {@code GroupedObservableSource} allows only a single 
     * {@link Subscriber} during its lifetime and if this {@code Subscriber} unsubscribes before the 
     * source terminates, the next emission by the source having the same key will trigger a new 
     * {@code GroupedObservableSource} emission.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedObservableSource}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #ignoreElements} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param <K>
     *            the key type
     * @return an {@code ObservableSource} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source ObservableSource that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<GroupedObservable<K, T>> groupBy(Function<? super T, ? extends K> keySelector) {
        return groupBy(keySelector, (Function)Functions.identity(), false, bufferSize());
    }

    /**
     * Groups the items emitted by an {@code ObservableSource} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s. The emitted {@code GroupedObservableSource} allows only a single 
     * {@link Subscriber} during its lifetime and if this {@code Subscriber} unsubscribes before the 
     * source terminates, the next emission by the source having the same key will trigger a new 
     * {@code GroupedObservableSource} emission.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedObservableSource}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #ignoreElements} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param <K>
     *            the key type
     * @param delayError
     *            if true, the exception from the current Observable is delayed in each group until that specific group emitted
     *            the normal values; if false, the exception bypasses values in the groups and is reported immediately.
     * @return an {@code ObservableSource} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source ObservableSource that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<GroupedObservable<K, T>> groupBy(Function<? super T, ? extends K> keySelector, boolean delayError) {
        return groupBy(keySelector, (Function)Functions.identity(), delayError, bufferSize());
    }

    /**
     * Groups the items emitted by an {@code ObservableSource} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s. The emitted {@code GroupedObservableSource} allows only a single 
     * {@link Subscriber} during its lifetime and if this {@code Subscriber} unsubscribes before the 
     * source terminates, the next emission by the source having the same key will trigger a new 
     * {@code GroupedObservableSource} emission.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedObservableSource}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #ignoreElements} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param valueSelector
     *            a function that extracts the return element for each item
     * @param <K>
     *            the key type
     * @param <V>
     *            the element type
     * @return an {@code ObservableSource} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source ObservableSource that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<GroupedObservable<K, V>> groupBy(Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector) {
        return groupBy(keySelector, valueSelector, false, bufferSize());
    }

    /**
     * Groups the items emitted by an {@code ObservableSource} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s. The emitted {@code GroupedObservableSource} allows only a single 
     * {@link Subscriber} during its lifetime and if this {@code Subscriber} unsubscribes before the 
     * source terminates, the next emission by the source having the same key will trigger a new 
     * {@code GroupedObservableSource} emission.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedObservableSource}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #ignoreElements} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param valueSelector
     *            a function that extracts the return element for each item
     * @param <K>
     *            the key type
     * @param <V>
     *            the element type
     * @param delayError
     *            if true, the exception from the current Observable is delayed in each group until that specific group emitted
     *            the normal values; if false, the exception bypasses values in the groups and is reported immediately.
     * @return an {@code ObservableSource} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source ObservableSource that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<GroupedObservable<K, V>> groupBy(Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector, boolean delayError) {
        return groupBy(keySelector, valueSelector, false, bufferSize());
    }

    /**
     * Groups the items emitted by an {@code ObservableSource} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s. The emitted {@code GroupedObservableSource} allows only a single 
     * {@link Subscriber} during its lifetime and if this {@code Subscriber} unsubscribes before the 
     * source terminates, the next emission by the source having the same key will trigger a new 
     * {@code GroupedObservableSource} emission.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedObservableSource}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #ignoreElements} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param valueSelector
     *            a function that extracts the return element for each item
     * @param delayError
     *            if true, the exception from the current Observable is delayed in each group until that specific group emitted
     *            the normal values; if false, the exception bypasses values in the groups and is reported immediately.
     * @param bufferSize
     *            the hint for how many {@link GroupedFlowable}s and element in each {@link GroupedFlowable} should be buffered
     * @param <K>
     *            the key type
     * @param <V>
     *            the element type
     * @return an {@code ObservableSource} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source ObservableSource that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<GroupedObservable<K, V>> groupBy(Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector, 
            boolean delayError, int bufferSize) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(valueSelector, "valueSelector is null");
        validateBufferSize(bufferSize, "bufferSize");

        return new ObservableGroupBy<T, K, V>(this, keySelector, valueSelector, bufferSize, delayError);
    }

    /**
     * Returns a Observable that correlates two ObservableSources when they overlap in time and groups the results.
     * <p>
     * There are no guarantees in what order the items get combined when multiple
     * items from one or both source ObservableSources overlap.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupJoin.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupJoin} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <TRight> the value type of the right ObservableSource source
     * @param <TLeftEnd> the element type of the left duration ObservableSources
     * @param <TRightEnd> the element type of the right duration ObservableSources
     * @param <R> the result type
     * @param other
     *            the other ObservableSource to correlate items from the source ObservableSource with
     * @param leftEnd
     *            a function that returns a ObservableSource whose emissions indicate the duration of the values of
     *            the source ObservableSource
     * @param rightEnd
     *            a function that returns a ObservableSource whose emissions indicate the duration of the values of
     *            the {@code right} ObservableSource
     * @param resultSelector
     *            a function that takes an item emitted by each ObservableSource and returns the value to be emitted
     *            by the resulting ObservableSource
     * @return a Observable that emits items based on combining those items emitted by the source ObservableSources
     *         whose durations overlap
     * @see <a href="http://reactivex.io/documentation/operators/join.html">ReactiveX operators documentation: Join</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <TRight, TLeftEnd, TRightEnd, R> Observable<R> groupJoin(
            ObservableSource<? extends TRight> other,
            Function<? super T, ? extends ObservableSource<TLeftEnd>> leftEnd,
            Function<? super TRight, ? extends ObservableSource<TRightEnd>> rightEnd,
            BiFunction<? super T, ? super Observable<TRight>, ? extends R> resultSelector
                    ) {
        return new ObservableGroupJoin<T, TRight, TLeftEnd, TRightEnd, R>(
                this, other, leftEnd, rightEnd, resultSelector);
    }
    
    /**
     * Ignores all items emitted by the source ObservableSource and only calls {@code onCompleted} or {@code onError}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/ignoreElements.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code ignoreElements} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an empty ObservableSource that only calls {@code onCompleted} or {@code onError}, based on which one is
     *         called by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/ignoreelements.html">ReactiveX operators documentation: IgnoreElements</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> ignoreElements() {
        return new ObservableIgnoreElements<T>(this);
    }

    /**
     * Returns a Observable that emits {@code true} if the source ObservableSource is empty, otherwise {@code false}.
     * <p>
     * In Rx.Net this is negated as the {@code any} Observer but we renamed this in RxJava to better match Java
     * naming idioms.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/isEmpty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code isEmpty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits a Boolean
     * @see <a href="http://reactivex.io/documentation/operators/contains.html">ReactiveX operators documentation: Contains</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Boolean> isEmpty() {
        return all(new Predicate<T>() {
            @Override
            public boolean test(T v) {
                return false;
            }
        });
    }

    /**
     * Correlates the items emitted by two ObservableSources based on overlapping durations.
     * <p>
     * There are no guarantees in what order the items get combined when multiple
     * items from one or both source ObservableSources overlap.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/join_.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code join} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <TRight> the value type of the right ObservableSource source
     * @param <TLeftEnd> the element type of the left duration ObservableSources
     * @param <TRightEnd> the element type of the right duration ObservableSources
     * @param <R> the result type
     * @param other
     *            the second ObservableSource to join items from
     * @param leftEnd
     *            a function to select a duration for each item emitted by the source ObservableSource, used to
     *            determine overlap
     * @param rightEnd
     *            a function to select a duration for each item emitted by the {@code right} ObservableSource, used to
     *            determine overlap
     * @param resultSelector
     *            a function that computes an item to be emitted by the resulting ObservableSource for any two
     *            overlapping items emitted by the two ObservableSources
     * @return a Observable that emits items correlating to items emitted by the source ObservableSources that have
     *         overlapping durations
     * @see <a href="http://reactivex.io/documentation/operators/join.html">ReactiveX operators documentation: Join</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <TRight, TLeftEnd, TRightEnd, R> Observable<R> join(
            ObservableSource<? extends TRight> other,
            Function<? super T, ? extends ObservableSource<TLeftEnd>> leftEnd,
            Function<? super TRight, ? extends ObservableSource<TRightEnd>> rightEnd,
            BiFunction<? super T, ? super TRight, ? extends R> resultSelector
                    ) {
        return new ObservableJoin<T, TRight, TLeftEnd, TRightEnd, R>(
                this, other, leftEnd, rightEnd, resultSelector);
    }
    
    /**
     * Returns a Observable that emits the last item emitted by the source ObservableSource or notifies observers of
     * a {@code NoSuchElementException} if the source ObservableSource is empty.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/last.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code last} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits the last item from the source ObservableSource or notifies observers of an
     *         error
     * @see <a href="http://reactivex.io/documentation/operators/last.html">ReactiveX operators documentation: Last</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> last() {
        return takeLast(1).single();
    }

    /**
     * Returns a Observable that emits only the last item emitted by the source ObservableSource, or a default item
     * if the source ObservableSource completes without emitting any items.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/lastOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code lastOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the default item to emit if the source ObservableSource is empty
     * @return a Observable that emits only the last item emitted by the source ObservableSource, or a default item
     *         if the source ObservableSource is empty
     * @see <a href="http://reactivex.io/documentation/operators/last.html">ReactiveX operators documentation: Last</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> last(T defaultValue) {
        return takeLast(1).single(defaultValue);
    }

    /**
     * <strong>This method requires advanced knowledge about building operators; please consider
     * other standard composition methods first;</strong>
     * Lifts a function to the current ObservableSource and returns a new ObservableSource that when subscribed to will pass
     * the values of the current ObservableSource through the Operator function.
     * <p>
     * In other words, this allows chaining Observers together on a ObservableSource for acting on the values within
     * the ObservableSource.
     * <p> {@code
     * ObservableSource.map(...).filter(...).take(5).lift(new OperatorA()).lift(new OperatorB(...)).subscribe()
     * }
     * <p>
     * If the operator you are creating is designed to act on the individual items emitted by a source
     * ObservableSource, use {@code lift}. If your operator is designed to transform the source ObservableSource as a whole
     * (for instance, by applying a particular set of existing RxJava operators to it) use {@link #compose}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code lift} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the output value type
     * @param lifter the Operator that implements the ObservableSource-operating function to be applied to the source
     *             ObservableSource
     * @return a Observable that is the result of applying the lifted Operator to the source ObservableSource
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Implementing-Your-Own-Operators">RxJava wiki: Implementing Your Own Operators</a>
     */
    public final <R> Observable<R> lift(ObservableOperator<? extends R, ? super T> lifter) {
        Objects.requireNonNull(lifter, "onLift is null");
        return new ObservableLift<R, T>(this, lifter);
    }

    /**
     * Returns a Observable that applies a specified function to each item emitted by the source ObservableSource and
     * emits the results of these function applications.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/map.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code map} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the output type
     * @param mapper
     *            a function to apply to each item emitted by the ObservableSource
     * @return a Observable that emits the items from the source ObservableSource, transformed by the specified
     *         function
     * @see <a href="http://reactivex.io/documentation/operators/map.html">ReactiveX operators documentation: Map</a>
     */
    public final <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return new ObservableMap<T, R>(this, mapper);
    }

    /**
     * Returns a Observable that represents all of the emissions <em>and</em> notifications from the source
     * ObservableSource into emissions marked with their original types within {@link Notification} objects.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/materialize.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code materialize} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits items that are the result of materializing the items and notifications
     *         of the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/materialize-dematerialize.html">ReactiveX operators documentation: Materialize</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Try<Optional<T>>> materialize() {
        return new ObservableMaterialize<T>(this);
    }

    /**
     * Flattens this and another ObservableSource into a single ObservableSource, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple ObservableSources so that they appear as a single ObservableSource, by
     * using the {@code mergeWith} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            a ObservableSource to be merged
     * @return a Observable that emits all of the items emitted by the source ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> mergeWith(ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return merge(this, other);
    }

    /**
     * Modifies a ObservableSource to perform its emissions and notifications on a specified {@link Scheduler},
     * asynchronously with a bounded buffer of {@link Flowable#bufferSize()} slots.
     *
     * <p>Note that onError notifications will cut ahead of onNext notifications on the emission thread if Scheduler is truly
     * asynchronous. If strict event ordering is required, consider using the {@link #observeOn(Scheduler, boolean)} overload.
     * <p>
     * <img width="640" height="308" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/observeOn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to notify {@link Observer}s on
     * @return the source ObservableSource modified so that its {@link Observer}s are notified on the specified
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/observeon.html">ReactiveX operators documentation: ObserveOn</a>
     * @see <a href="http://www.grahamlea.com/2014/07/rxjava-threading-examples/">RxJava Threading Examples</a>
     * @see #subscribeOn
     * @see #observeOn(Scheduler, boolean)
     * @see #observeOn(Scheduler, boolean, int)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> observeOn(Scheduler scheduler) {
        return observeOn(scheduler, false, bufferSize());
    }

    /**
     * Modifies a ObservableSource to perform its emissions and notifications on a specified {@link Scheduler},
     * asynchronously with a bounded buffer and optionally delays onError notifications.
     * <p>
     * <img width="640" height="308" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/observeOn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to notify {@link Observer}s on
     * @param delayError
     *            indicates if the onError notification may not cut ahead of onNext notification on the other side of the
     *            scheduling boundary. If true a sequence ending in onError will be replayed in the same order as was received
     *            from upstream
     * @return the source ObservableSource modified so that its {@link Observer}s are notified on the specified
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/observeon.html">ReactiveX operators documentation: ObserveOn</a>
     * @see <a href="http://www.grahamlea.com/2014/07/rxjava-threading-examples/">RxJava Threading Examples</a>
     * @see #subscribeOn
     * @see #observeOn(Scheduler)
     * @see #observeOn(Scheduler, boolean, int)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> observeOn(Scheduler scheduler, boolean delayError) {
        return observeOn(scheduler, delayError, bufferSize());
    }

    /**
     * Modifies a ObservableSource to perform its emissions and notifications on a specified {@link Scheduler},
     * asynchronously with a bounded buffer of configurable size and optionally delays onError notifications.
     * <p>
     * <img width="640" height="308" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/observeOn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     *
     * @param scheduler
     *            the {@link Scheduler} to notify {@link Observer}s on
     * @param delayError
     *            indicates if the onError notification may not cut ahead of onNext notification on the other side of the
     *            scheduling boundary. If true a sequence ending in onError will be replayed in the same order as was received
     *            from upstream
     * @param bufferSize the size of the buffer.
     * @return the source ObservableSource modified so that its {@link Observer}s are notified on the specified
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/observeon.html">ReactiveX operators documentation: ObserveOn</a>
     * @see <a href="http://www.grahamlea.com/2014/07/rxjava-threading-examples/">RxJava Threading Examples</a>
     * @see #subscribeOn
     * @see #observeOn(Scheduler)
     * @see #observeOn(Scheduler, boolean)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> observeOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        validateBufferSize(bufferSize, "bufferSize");
        return new ObservableObserveOn<T>(this, scheduler, delayError, bufferSize);
    }

    /**
     * Filters the items emitted by a ObservableSource, only emitting those of the specified type.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/ofClass.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code ofType} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the output type
     * @param clazz
     *            the class type to filter the items emitted by the source ObservableSource
     * @return a Observable that emits items from the source ObservableSource of type {@code clazz}
     * @see <a href="http://reactivex.io/documentation/operators/filter.html">ReactiveX operators documentation: Filter</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<U> ofType(final Class<U> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        return filter(new Predicate<T>() {
            @Override
            public boolean test(T v) {
                return clazz.isInstance(v);
            }
        }).cast(clazz);
    }

    /**
     * Instructs a ObservableSource to pass control to another ObservableSource rather than invoking
     * {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorResumeNext.png" alt="">
     * <p>
     * By default, when a ObservableSource encounters an error that prevents it from emitting the expected item to
     * its {@link Observer}, the ObservableSource invokes its Observer's {@code onError} method, and then quits
     * without invoking any more of its Observer's methods. The {@code onErrorResumeNext} method changes this
     * behavior. If you pass a function that returns a ObservableSource ({@code resumeFunction}) to
     * {@code onErrorResumeNext}, if the original ObservableSource encounters an error, instead of invoking its
     * Observer's {@code onError} method, it will instead relinquish control to the ObservableSource returned from
     * {@code resumeFunction}, which will invoke the Observer's {@link Observer#onNext onNext} method if it is
     * able to do so. In such a case, because no ObservableSource necessarily invokes {@code onError}, the Observer
     * may never know that an error happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorResumeNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param resumeFunction
     *            a function that returns a ObservableSource that will take over if the source ObservableSource encounters
     *            an error
     * @return the original ObservableSource, with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> onErrorResumeNext(Function<? super Throwable, ? extends ObservableSource<? extends T>> resumeFunction) {
        Objects.requireNonNull(resumeFunction, "resumeFunction is null");
        return new ObservableOnErrorNext<T>(this, resumeFunction, false);
    }

    /**
     * Instructs a ObservableSource to pass control to another ObservableSource rather than invoking
     * {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorResumeNext.png" alt="">
     * <p>
     * By default, when a ObservableSource encounters an error that prevents it from emitting the expected item to
     * its {@link Observer}, the ObservableSource invokes its Observer's {@code onError} method, and then quits
     * without invoking any more of its Observer's methods. The {@code onErrorResumeNext} method changes this
     * behavior. If you pass another ObservableSource ({@code resumeSequence}) to a ObservableSource's
     * {@code onErrorResumeNext} method, if the original ObservableSource encounters an error, instead of invoking its
     * Observer's {@code onError} method, it will instead relinquish control to {@code resumeSequence} which
     * will invoke the Observer's {@link Observer#onNext onNext} method if it is able to do so. In such a case,
     * because no ObservableSource necessarily invokes {@code onError}, the Observer may never know that an error
     * happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorResumeNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param next
     *            the next ObservableSource source that will take over if the source ObservableSource encounters
     *            an error
     * @return the original ObservableSource, with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> onErrorResumeNext(final ObservableSource<? extends T> next) {
        Objects.requireNonNull(next, "next is null");
        return onErrorResumeNext(new Function<Throwable, ObservableSource<? extends T>>() {
            @Override
            public ObservableSource<? extends T> apply(Throwable e) {
                return next;
            }
        });
    }

    /**
     * Instructs a ObservableSource to emit an item (returned by a specified function) rather than invoking
     * {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorReturn.png" alt="">
     * <p>
     * By default, when a ObservableSource encounters an error that prevents it from emitting the expected item to
     * its {@link Observer}, the ObservableSource invokes its Observer's {@code onError} method, and then quits
     * without invoking any more of its Observer's methods. The {@code onErrorReturn} method changes this
     * behavior. If you pass a function ({@code resumeFunction}) to a ObservableSource's {@code onErrorReturn}
     * method, if the original ObservableSource encounters an error, instead of invoking its Observer's
     * {@code onError} method, it will instead emit the return value of {@code resumeFunction}.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorReturn} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param valueSupplier
     *            a function that returns a single value that will be emitted along with a regular onComplete in case
     *            the current Observable signals an onError event
     * @return the original ObservableSource with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> onErrorReturn(Function<? super Throwable, ? extends T> valueSupplier) {
        Objects.requireNonNull(valueSupplier, "valueSupplier is null");
        return new ObservableOnErrorReturn<T>(this, valueSupplier);
    }

    /**
     * Instructs a ObservableSource to emit an item (returned by a specified function) rather than invoking
     * {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorReturn.png" alt="">
     * <p>
     * By default, when a ObservableSource encounters an error that prevents it from emitting the expected item to
     * its {@link Observer}, the ObservableSource invokes its Observer's {@code onError} method, and then quits
     * without invoking any more of its Observer's methods. The {@code onErrorReturn} method changes this
     * behavior. If you pass a function ({@code resumeFunction}) to a ObservableSource's {@code onErrorReturn}
     * method, if the original ObservableSource encounters an error, instead of invoking its Observer's
     * {@code onError} method, it will instead emit the return value of {@code resumeFunction}.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorReturn} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param value
     *            the value that is emitted along with a regular onComplete in case the current
     *            Observable signals an exception
     * @return the original ObservableSource with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> onErrorReturnValue(final T value) {
        Objects.requireNonNull(value, "value is null");
        return onErrorReturn(new Function<Throwable, T>() {
            @Override
            public T apply(Throwable e) {
                return value;
            }
        });
    }

    /**
     * Instructs a ObservableSource to pass control to another ObservableSource rather than invoking
     * {@link Observer#onError onError} if it encounters an {@link java.lang.Exception}.
     * <p>
     * This differs from {@link #onErrorResumeNext} in that this one does not handle {@link java.lang.Throwable}
     * or {@link java.lang.Error} but lets those continue through.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onExceptionResumeNextViaObservableSource.png" alt="">
     * <p>
     * By default, when a ObservableSource encounters an exception that prevents it from emitting the expected item
     * to its {@link Observer}, the ObservableSource invokes its Observer's {@code onError} method, and then quits
     * without invoking any more of its Observer's methods. The {@code onExceptionResumeNext} method changes
     * this behavior. If you pass another ObservableSource ({@code resumeSequence}) to a ObservableSource's
     * {@code onExceptionResumeNext} method, if the original ObservableSource encounters an exception, instead of
     * invoking its Observer's {@code onError} method, it will instead relinquish control to
     * {@code resumeSequence} which will invoke the Observer's {@link Observer#onNext onNext} method if it is
     * able to do so. In such a case, because no ObservableSource necessarily invokes {@code onError}, the Observer
     * may never know that an exception happened.
     * <p>
     * You can use this to prevent exceptions from propagating or to supply fallback data should exceptions be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onExceptionResumeNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param next
     *            the next ObservableSource that will take over if the source ObservableSource encounters
     *            an exception
     * @return the original ObservableSource, with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> onExceptionResumeNext(final ObservableSource<? extends T> next) {
        Objects.requireNonNull(next, "next is null");
        return new ObservableOnErrorNext<T>(this, new Function<Throwable, ObservableSource<? extends T>>() {
            @Override
            public ObservableSource<? extends T> apply(Throwable e) {
                return next;
            }
        }, true);
    }

    /**
     * Nulls out references to the upstream producer and downstream Subscriber if
     * the sequence is terminated or downstream unsubscribes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onTerminateDetach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @return a Observable which out references to the upstream producer and downstream Subscriber if
     * the sequence is terminated or downstream unsubscribes
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> onTerminateDetach() {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    
    /**
     * Returns a {@link ConnectableFlowable}, which is a variety of ObservableSource that waits until its
     * {@link ConnectableFlowable#connect connect} method is called before it begins emitting items to those
     * {@link Observer}s that have subscribed to it.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link ConnectableFlowable} that upon connection causes the source ObservableSource to emit items
     *         to its {@link Observer}s
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final ConnectableObservable<T> publish() {
        return publish(bufferSize());
    }

    /**
     * Returns a Observable that emits the results of invoking a specified selector on items emitted by a
     * {@link ConnectableFlowable} that shares a single subscription to the underlying sequence.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a function that can use the multicasted source sequence as many times as needed, without
     *            causing multiple subscriptions to the source sequence. Subscribers to the given source will
     *            receive all notifications of the source from the time of the subscription forward.
     * @return a Observable that emits the results of invoking the selector on the items emitted by a {@link ConnectableFlowable} that shares a single subscription to the underlying sequence
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> publish(Function<? super Observable<T>, ? extends ObservableSource<R>> selector) {
        return publish(selector, bufferSize());
    }

    /**
     * Returns a Observable that emits the results of invoking a specified selector on items emitted by a
     * {@link ConnectableFlowable} that shares a single subscription to the underlying sequence.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a function that can use the multicasted source sequence as many times as needed, without
     *            causing multiple subscriptions to the source sequence. Subscribers to the given source will
     *            receive all notifications of the source from the time of the subscription forward.
     * @param bufferSize
     *            the number of elements to prefetch from the current Observable
     * @return a Observable that emits the results of invoking the selector on the items emitted by a {@link ConnectableFlowable} that shares a single subscription to the underlying sequence
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> publish(Function<? super Observable<T>, ? extends ObservableSource<R>> selector, int bufferSize) {
        validateBufferSize(bufferSize, "bufferSize");
        Objects.requireNonNull(selector, "selector is null");
        return ObservablePublish.create(this, selector, bufferSize);
    }

    /**
     * Returns a {@link ConnectableFlowable}, which is a variety of ObservableSource that waits until its
     * {@link ConnectableFlowable#connect connect} method is called before it begins emitting items to those
     * {@link Observer}s that have subscribed to it.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the number of elements to prefetch from the current Observable
     * @return a {@link ConnectableFlowable} that upon connection causes the source ObservableSource to emit items
     *         to its {@link Observer}s
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final ConnectableObservable<T> publish(int bufferSize) {
        validateBufferSize(bufferSize, "bufferSize");
        return ObservablePublish.create(this, bufferSize);
    }

    /**
     * Returns a Observable that applies a specified accumulator function to the first item emitted by a source
     * ObservableSource, then feeds the result of that function along with the second item emitted by the source
     * ObservableSource into the same function, and so on until all items have been emitted by the source ObservableSource,
     * and emits the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduce.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimes called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param reducer
     *            an accumulator function to be invoked on each item emitted by the source ObservableSource, whose
     *            result will be used in the next accumulator call
     * @return a Observable that emits a single item that is the result of accumulating the items emitted by
     *         the source ObservableSource
     * @throws IllegalArgumentException
     *             if the source ObservableSource emits no items
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> reduce(BiFunction<T, T, T> reducer) {
        return scan(reducer).last();
    }

    /**
     * Returns a Observable that applies a specified accumulator function to the first item emitted by a source
     * ObservableSource and a specified seed value, then feeds the result of that function along with the second item
     * emitted by a ObservableSource into the same function, and so on until all items have been emitted by the
     * source ObservableSource, emitting the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="325" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduceSeed.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimes called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <p>
     * Note that the {@code initialValue} is shared among all subscribers to the resulting ObservableSource
     * and may cause problems if it is mutable. To make sure each subscriber gets its own value, defer
     * the application of this operator via {@link #defer(Callable)}:
     * <pre><code>
     * ObservableSource&lt;T> source = ...
     * ObservableSource.defer(() -> source.reduce(new ArrayList&lt;>(), (list, item) -> list.add(item)));
     * 
     * // alternatively, by using compose to stay fluent
     * 
     * source.compose(o ->
     *     ObservableSource.defer(() -> o.reduce(new ArrayList&lt;>(), (list, item) -> list.add(item)))
     * );
     * </code></pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the accumulator and output value type
     * @param seed
     *            the initial (seed) accumulator value
     * @param reducer
     *            an accumulator function to be invoked on each item emitted by the source ObservableSource, the
     *            result of which will be used in the next accumulator call
     * @return a Observable that emits a single item that is the result of accumulating the output from the
     *         items emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> reduce(R seed, BiFunction<R, ? super T, R> reducer) {
        return scan(seed, reducer).last();
    }

    /**
     * Returns a Observable that applies a specified accumulator function to the first item emitted by a source
     * ObservableSource and a specified seed value, then feeds the result of that function along with the second item
     * emitted by a ObservableSource into the same function, and so on until all items have been emitted by the
     * source ObservableSource, emitting the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="325" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduceSeed.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimes called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <p>
     * Note that the {@code initialValue} is shared among all subscribers to the resulting ObservableSource
     * and may cause problems if it is mutable. To make sure each subscriber gets its own value, defer
     * the application of this operator via {@link #defer(Callable)}:
     * <pre><code>
     * ObservableSource&lt;T> source = ...
     * ObservableSource.defer(() -> source.reduce(new ArrayList&lt;>(), (list, item) -> list.add(item)));
     * 
     * // alternatively, by using compose to stay fluent
     * 
     * source.compose(o ->
     *     ObservableSource.defer(() -> o.reduce(new ArrayList&lt;>(), (list, item) -> list.add(item)))
     * );
     * </code></pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the accumulator and output value type
     * @param seedSupplier
     *            the Callable that provides the initial (seed) accumulator value for each individual Subscriber
     * @param reducer
     *            an accumulator function to be invoked on each item emitted by the source ObservableSource, the
     *            result of which will be used in the next accumulator call
     * @return a Observable that emits a single item that is the result of accumulating the output from the
     *         items emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> reduceWith(Callable<R> seedSupplier, BiFunction<R, ? super T, R> reducer) {
        return scanWith(seedSupplier, reducer).last();
    }

    /**
     * Returns a Observable that repeats the sequence of items emitted by the source ObservableSource indefinitely.
     * <p>
     * <img width="640" height="309" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits the items emitted by the source ObservableSource repeatedly and in sequence
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> repeat() {
        return repeat(Long.MAX_VALUE);
    }

    /**
     * Returns a Observable that repeats the sequence of items emitted by the source ObservableSource at most
     * {@code count} times.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.on.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the number of times the source ObservableSource items are repeated, a count of 0 will yield an empty
     *            sequence
     * @return a Observable that repeats the sequence of items emitted by the source ObservableSource at most
     *         {@code count} times
     * @throws IllegalArgumentException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> repeat(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count >= 0 required but it was " + count);
        }
        if (count == 0) {
            return empty();
        }
        return new ObservableRepeat<T>(this, count);
    }

    /**
     * Returns a Observable that repeats the sequence of items emitted by the source ObservableSource until
     * the provided stop function returns true.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.on.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param stop
     *                a boolean supplier that is called when the current Observable completes and unless it returns
     *                false, the current Observable is resubscribed
     * @return the new Observable instance
     * @throws NullPointerException
     *             if {@code stop} is null
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> repeatUntil(BooleanSupplier stop) {
        Objects.requireNonNull(stop, "stop is null");
        return new ObservableRepeatUntil<T>(this, stop);
    }

    /**
     * Returns a Observable that emits the same values as the source ObservableSource with the exception of an
     * {@code onCompleted}. An {@code onCompleted} notification from the source will result in the emission of
     * a {@code void} item to the ObservableSource provided as an argument to the {@code notificationHandler}
     * function. If that ObservableSource calls {@code onComplete} or {@code onError} then {@code repeatWhen} will
     * call {@code onCompleted} or {@code onError} on the child subscription. Otherwise, this ObservableSource will
     * resubscribe to the source ObservableSource.
     * <p>
     * <img width="640" height="430" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeatWhen.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeatWhen} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param handler
     *            receives a ObservableSource of notifications with which a user can complete or error, aborting the repeat.
     * @return the source ObservableSource modified with repeat logic
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> repeatWhen(final Function<? super Observable<Object>, ? extends ObservableSource<?>> handler) {
        Objects.requireNonNull(handler, "handler is null");
        
        Function<Observable<Try<Optional<Object>>>, ObservableSource<?>> f = new Function<Observable<Try<Optional<Object>>>, ObservableSource<?>>() {
            @Override
            public ObservableSource<?> apply(Observable<Try<Optional<Object>>> no) throws Exception {
                return handler.apply(no.map(new Function<Try<Optional<Object>>, Object>() {
                    @Override
                    public Object apply(Try<Optional<Object>> v) {
                        return 0;
                    }
                }));
            }
        }
        ;
        
        return new ObservableRedo<T>(this, f);
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the underlying ObservableSource
     * that will replay all of its items and notifications to any future {@link Observer}. A Connectable
     * ObservableSource resembles an ordinary ObservableSource, except that it does not begin emitting items when it is
     * subscribed to, but only when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link ConnectableFlowable} that upon connection causes the source ObservableSource to emit its
     *         items to its {@link Observer}s
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final ConnectableObservable<T> replay() {
        return ObservableReplay.createFrom(this);
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on the items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource.
     * <p>
     * <img width="640" height="450" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            the selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @return a Observable that emits items that are the results of invoking the selector on a
     *         {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> replay(Function<? super Observable<T>, ? extends ObservableSource<R>> selector) {
        Objects.requireNonNull(selector, "selector is null");
        return ObservableReplay.multicastSelector(new Callable<ConnectableObservable<T>>() {
            @Override
            public ConnectableObservable<T> call() {
                return replay();
            }
        }, selector);
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     * replaying {@code bufferSize} notifications.
     * <p>
     * <img width="640" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            the selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable ObservableSource can replay
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource
     *         replaying no more than {@code bufferSize} items
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> replay(Function<? super Observable<T>, ? extends ObservableSource<R>> selector, final int bufferSize) {
        Objects.requireNonNull(selector, "selector is null");
        return ObservableReplay.multicastSelector(new Callable<ConnectableObservable<T>>() {
            @Override
            public ConnectableObservable<T> call() {
                return replay(bufferSize);
            }
        }, selector);
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     * replaying no more than {@code bufferSize} items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="445" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fnt.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable ObservableSource can replay
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource, and
     *         replays no more than {@code bufferSize} items that were emitted within the window defined by
     *         {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final <R> Observable<R> replay(Function<? super Observable<T>, ? extends ObservableSource<R>> selector, int bufferSize, long time, TimeUnit unit) {
        return replay(selector, bufferSize, time, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     * replaying no more than {@code bufferSize} items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="445" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fnts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable ObservableSource can replay
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that is the time source for the window
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource, and
     *         replays no more than {@code bufferSize} items that were emitted within the window defined by
     *         {@code time}
     * @throws IllegalArgumentException
     *             if {@code bufferSize} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final <R> Observable<R> replay(Function<? super Observable<T>, ? extends ObservableSource<R>> selector, final int bufferSize, final long time, final TimeUnit unit, final Scheduler scheduler) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize < 0");
        }
        Objects.requireNonNull(selector, "selector is null");
        return ObservableReplay.multicastSelector(new Callable<ConnectableObservable<T>>() {
            @Override
            public ConnectableObservable<T> call() {
                return replay(bufferSize, time, unit, scheduler);
            }
        }, selector);
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     * replaying a maximum of {@code bufferSize} items.
     * <p>
     * <img width="640" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable ObservableSource can replay
     * @param scheduler
     *            the Scheduler on which the replay is observed
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     *         replaying no more than {@code bufferSize} notifications
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final <R> Observable<R> replay(final Function<? super Observable<T>, ? extends ObservableSource<R>> selector, final int bufferSize, final Scheduler scheduler) {
        return ObservableReplay.multicastSelector(new Callable<ConnectableObservable<T>>() {
            @Override
            public ConnectableObservable<T> call() {
                return replay(bufferSize);
            }
        }, 
        new Function<Observable<T>, Observable<R>>() {
            @Override
            public Observable<R> apply(Observable<T> t) throws Exception {
                return new ObservableObserveOn<R>(selector.apply(t), scheduler, false, bufferSize());
            }
        });
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     * replaying all items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="435" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.ft.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     *         replaying all items that were emitted within the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final <R> Observable<R> replay(Function<? super Observable<T>, ? extends ObservableSource<R>> selector, long time, TimeUnit unit) {
        return replay(selector, time, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     * replaying all items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler that is the time source for the window
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     *         replaying all items that were emitted within the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final <R> Observable<R> replay(Function<? super Observable<T>, ? extends ObservableSource<R>> selector, final long time, final TimeUnit unit, final Scheduler scheduler) {
        Objects.requireNonNull(selector, "selector is null");
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return ObservableReplay.multicastSelector(new Callable<ConnectableObservable<T>>() {
            @Override
            public ConnectableObservable<T> call() {
                return replay(time, unit, scheduler);
            }
        }, selector);
    }

    /**
     * Returns a Observable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource.
     * <p>
     * <img width="640" height="445" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fs.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the ObservableSource
     * @param scheduler
     *            the Scheduler where the replay is observed
     * @return a Observable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource,
     *         replaying all items
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final <R> Observable<R> replay(final Function<? super Observable<T>, ? extends ObservableSource<R>> selector, final Scheduler scheduler) {
        Objects.requireNonNull(selector, "selector is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return ObservableReplay.multicastSelector(new Callable<ConnectableObservable<T>>() {
            @Override
            public ConnectableObservable<T> call() {
                return replay();
            }
        }, 
        new Function<Observable<T>, Observable<R>>() {
            @Override
            public Observable<R> apply(Observable<T> t) throws Exception {
                return new ObservableObserveOn<R>(selector.apply(t), scheduler, false, bufferSize());
            }
        });
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource that
     * replays at most {@code bufferSize} items emitted by that ObservableSource. A Connectable ObservableSource resembles
     * an ordinary ObservableSource, except that it does not begin emitting items when it is subscribed to, but only
     * when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.n.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     *         replays at most {@code bufferSize} items emitted by that ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final ConnectableObservable<T> replay(final int bufferSize) {
        return ObservableReplay.create(this, bufferSize);
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     * replays at most {@code bufferSize} items that were emitted during a specified time window. A Connectable
     * ObservableSource resembles an ordinary ObservableSource, except that it does not begin emitting items when it is
     * subscribed to, but only when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.nt.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     *         replays at most {@code bufferSize} items that were emitted during the window defined by
     *         {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final ConnectableObservable<T> replay(int bufferSize, long time, TimeUnit unit) {
        return replay(bufferSize, time, unit, Schedulers.computation());
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     * that replays a maximum of {@code bufferSize} items that are emitted within a specified time window. A
     * Connectable ObservableSource resembles an ordinary ObservableSource, except that it does not begin emitting items
     * when it is subscribed to, but only when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.nts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler that is used as a time source for the window
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     *         replays at most {@code bufferSize} items that were emitted during the window defined by
     *         {@code time}
     * @throws IllegalArgumentException
     *             if {@code bufferSize} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final ConnectableObservable<T> replay(final int bufferSize, final long time, final TimeUnit unit, final Scheduler scheduler) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize < 0");
        }
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return ObservableReplay.create(this, time, unit, scheduler, bufferSize);
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     * replays at most {@code bufferSize} items emitted by that ObservableSource. A Connectable ObservableSource resembles
     * an ordinary ObservableSource, except that it does not begin emitting items when it is subscribed to, but only
     * when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.ns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @param scheduler
     *            the scheduler on which the Observers will observe the emitted items
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     *         replays at most {@code bufferSize} items that were emitted by the ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final ConnectableObservable<T> replay(final int bufferSize, final Scheduler scheduler) {
        return ObservableReplay.observeOn(replay(bufferSize), scheduler);
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     * replays all items emitted by that ObservableSource within a specified time window. A Connectable ObservableSource
     * resembles an ordinary ObservableSource, except that it does not begin emitting items when it is subscribed to,
     * but only when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     *         replays the items that were emitted during the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final ConnectableObservable<T> replay(long time, TimeUnit unit) {
        return replay(time, unit, Schedulers.computation());
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     * replays all items emitted by that ObservableSource within a specified time window. A Connectable ObservableSource
     * resembles an ordinary ObservableSource, except that it does not begin emitting items when it is subscribed to,
     * but only when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that is the time source for the window
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource and
     *         replays the items that were emitted during the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final ConnectableObservable<T> replay(final long time, final TimeUnit unit, final Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return ObservableReplay.create(this, time, unit, scheduler);
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource that
     * will replay all of its items and notifications to any future {@link Observer} on the given
     * {@link Scheduler}. A Connectable ObservableSource resembles an ordinary ObservableSource, except that it does not
     * begin emitting items when it is subscribed to, but only when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the Scheduler on which the Observers will observe the emitted items
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source ObservableSource that
     *         will replay all of its items and notifications to any future {@link Observer} on the given
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final ConnectableObservable<T> replay(final Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return ObservableReplay.observeOn(replay(), scheduler);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, resubscribing to it if it calls {@code onError}
     * (infinite retry count).
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retry.png" alt="">
     * <p>
     * If the source ObservableSource calls {@link Observer#onError}, this method will resubscribe to the source
     * ObservableSource rather than propagating the {@code onError} call.
     * <p>
     * Any and all items emitted by the source ObservableSource will be emitted by the resulting ObservableSource, even
     * those emitted during failed subscriptions. For example, if a ObservableSource fails at first but emits
     * {@code [1, 2]} then succeeds the second time and emits {@code [1, 2, 3, 4, 5]} then the complete sequence
     * of emissions and notifications would be {@code [1, 2, 1, 2, 3, 4, 5, onCompleted]}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return the source ObservableSource modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retry() {
        return retry(Long.MAX_VALUE, Functions.alwaysTrue());
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, resubscribing to it if it calls {@code onError}
     * and the predicate returns true for that specific exception and retry count.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retry.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param predicate
     *            the predicate that determines if a resubscription may happen in case of a specific exception
     *            and retry count
     * @return the source ObservableSource modified with retry logic
     * @see #retry()
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retry(BiPredicate<? super Integer, ? super Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        
        return new ObservableRetryBiPredicate<T>(this, predicate);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, resubscribing to it if it calls {@code onError}
     * up to a specified number of retries.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retry.png" alt="">
     * <p>
     * If the source ObservableSource calls {@link Observer#onError}, this method will resubscribe to the source
     * ObservableSource for a maximum of {@code count} resubscriptions rather than propagating the
     * {@code onError} call.
     * <p>
     * Any and all items emitted by the source ObservableSource will be emitted by the resulting ObservableSource, even
     * those emitted during failed subscriptions. For example, if a ObservableSource fails at first but emits
     * {@code [1, 2]} then succeeds the second time and emits {@code [1, 2, 3, 4, 5]} then the complete sequence
     * of emissions and notifications would be {@code [1, 2, 1, 2, 3, 4, 5, onCompleted]}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            number of retry attempts before failing
     * @return the source ObservableSource modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retry(long count) {
        return retry(count, Functions.alwaysTrue());
    }
    
    /**
     * Retries at most times or until the predicate returns false, whichever happens first.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param times the number of times to repeat
     * @param predicate the predicate called with the failure Throwable and should return true to trigger a retry.
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retry(long times, Predicate<? super Throwable> predicate) {
        if (times < 0) {
            throw new IllegalArgumentException("times >= 0 required but it was " + times);
        }
        Objects.requireNonNull(predicate, "predicate is null");

        return new ObservableRetryPredicate<T>(this, times, predicate);
    }

    /**
     * Retries the current Observable if the predicate returns true.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate the predicate that receives the failure Throwable and should return true to trigger a retry.
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retry(Predicate<? super Throwable> predicate) {
        return retry(Long.MAX_VALUE, predicate);
    }

    /**
     * Retries until the given stop function returns true.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param stop the function that should return true to stop retrying
     * @return the new Observable instance
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retryUntil(final BooleanSupplier stop) {
        Objects.requireNonNull(stop, "stop is null");
        return retry(Long.MAX_VALUE, new Predicate<Throwable>() {
            @Override
            public boolean test(Throwable e) throws Exception {
                return !stop.getAsBoolean();
            }
        });
    }

    /**
     * Returns a Observable that emits the same values as the source ObservableSource with the exception of an
     * {@code onError}. An {@code onError} notification from the source will result in the emission of a
     * {@link Throwable} item to the ObservableSource provided as an argument to the {@code notificationHandler}
     * function. If that ObservableSource calls {@code onComplete} or {@code onError} then {@code retry} will call
     * {@code onCompleted} or {@code onError} on the child subscription. Otherwise, this ObservableSource will
     * resubscribe to the source ObservableSource.    
     * <p>
     * <img width="640" height="430" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retryWhen.f.png" alt="">
     * 
     * Example:
     * 
     * This retries 3 times, each time incrementing the number of seconds it waits.
     * 
     * <pre><code>
     *  ObservableSource.create((Subscriber<? super String> s) -> {
     *      System.out.println("subscribing");
     *      s.onError(new RuntimeException("always fails"));
     *  }).retryWhen(attempts -> {
     *      return attempts.zipWith(ObservableSource.range(1, 3), (n, i) -> i).flatMap(i -> {
     *          System.out.println("delay retry by " + i + " second(s)");
     *          return ObservableSource.timer(i, TimeUnit.SECONDS);
     *      });
     *  }).toBlocking().forEach(System.out::println);
     * </code></pre>
     * 
     * Output is:
     *
     * <pre> {@code
     * subscribing
     * delay retry by 1 second(s)
     * subscribing
     * delay retry by 2 second(s)
     * subscribing
     * delay retry by 3 second(s)
     * subscribing
     * } </pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retryWhen} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param handler
     *            receives a ObservableSource of notifications with which a user can complete or error, aborting the
     *            retry
     * @return the source ObservableSource modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> retryWhen(
            final Function<? super Observable<? extends Throwable>, ? extends ObservableSource<?>> handler) {
        Objects.requireNonNull(handler, "handler is null");
        
        Function<Observable<Try<Optional<Object>>>, ObservableSource<?>> f = new Function<Observable<Try<Optional<Object>>>, ObservableSource<?>>() {
            @Override
            public ObservableSource<?> apply(Observable<Try<Optional<Object>>> no) throws Exception {
                return handler.apply(no
                        .takeWhile(new Predicate<Try<Optional<Object>>>() {
                            @Override
                            public boolean test(Try<Optional<Object>> e) {
                                return e.hasError();
                            }
                        })
                        .map(new Function<Try<Optional<Object>>, Throwable>() {
                            @Override
                            public Throwable apply(Try<Optional<Object>> t) {
                                return t.error();
                            }
                        })
                );
            }
        }
        ;
        
        return new ObservableRedo<T>(this, f);
    }
    
    /**
     * Subscribes to the current Observable and wraps the given Subscriber into a SafeSubscriber
     * (if not already a SafeSubscriber) that
     * deals with exceptions thrown by a misbehaving Subscriber (that doesn't follow the
     * Reactive-Streams specification).
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retryWhen} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @param s the incoming Subscriber instance
     * @throws NullPointerException if s is null
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final void safeSubscribe(Observer<? super T> s) {
        Objects.requireNonNull(s, "s is null");
        if (s instanceof SafeObserver) {
            subscribe(s);
        } else {
            subscribe(new SafeObserver<T>(s));
        }
    }

    /**
     * Returns a Observable that emits the most recently emitted item (if any) emitted by the source ObservableSource
     * within periodic time intervals.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sample.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sample} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param period
     *            the sampling rate
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @return a Observable that emits the results of sampling the items emitted by the source ObservableSource at
     *         the specified time interval
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see #throttleLast(long, TimeUnit)
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> sample(long period, TimeUnit unit) {
        return sample(period, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits the most recently emitted item (if any) emitted by the source ObservableSource
     * within periodic time intervals, where the intervals are defined on a particular Scheduler.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sample.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param period
     *            the sampling rate
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @param scheduler
     *            the {@link Scheduler} to use when sampling
     * @return a Observable that emits the results of sampling the items emitted by the source ObservableSource at
     *         the specified time interval
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see #throttleLast(long, TimeUnit, Scheduler)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> sample(long period, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableSampleTimed<T>(this, period, unit, scheduler);
    }

    /**
     * Returns a Observable that, when the specified {@code sampler} ObservableSource emits an item or completes,
     * emits the most recently emitted item (if any) emitted by the source ObservableSource since the previous
     * emission from the {@code sampler} ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sample.o.png" alt="">
     * <dl>
     *      ObservableSource to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code sample} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the element type of the sampler ObservableSource
     * @param sampler
     *            the ObservableSource to use for sampling the source ObservableSource
     * @return a Observable that emits the results of sampling the items emitted by this ObservableSource whenever
     *         the {@code sampler} ObservableSource emits an item or completes
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<T> sample(ObservableSource<U> sampler) {
        Objects.requireNonNull(sampler, "sampler is null");
        return new ObservableSampleWithObservable<T>(this, sampler);
    }

    /**
     * Returns a Observable that applies a specified accumulator function to the first item emitted by a source
     * ObservableSource, then feeds the result of that function along with the second item emitted by the source
     * ObservableSource into the same function, and so on until all items have been emitted by the source ObservableSource,
     * emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scan.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source ObservableSource, whose
     *            result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the
     *            next accumulator call
     * @return a Observable that emits the results of each call to the accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> scan(BiFunction<T, T, T> accumulator) {
        Objects.requireNonNull(accumulator, "accumulator is null");
        return new ObservableScan<T>(this, accumulator);
    }

    /**
     * Returns a Observable that applies a specified accumulator function to the first item emitted by a source
     * ObservableSource and a seed value, then feeds the result of that function along with the second item emitted by
     * the source ObservableSource into the same function, and so on until all items have been emitted by the source
     * ObservableSource, emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scanSeed.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <p>
     * Note that the ObservableSource that results from this method will emit {@code initialValue} as its first
     * emitted item.
     * <p>
     * Note that the {@code initialValue} is shared among all subscribers to the resulting ObservableSource
     * and may cause problems if it is mutable. To make sure each subscriber gets its own value, defer
     * the application of this operator via {@link #defer(Callable)}:
     * <pre><code>
     * ObservableSource&lt;T> source = ...
     * ObservableSource.defer(() -> source.scan(new ArrayList&lt;>(), (list, item) -> list.add(item)));
     * 
     * // alternatively, by using compose to stay fluent
     * 
     * source.compose(o ->
     *     ObservableSource.defer(() -> o.scan(new ArrayList&lt;>(), (list, item) -> list.add(item)))
     * );
     * </code></pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the initial, accumulator and result type
     * @param initialValue
     *            the initial (seed) accumulator item
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source ObservableSource, whose
     *            result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the
     *            next accumulator call
     * @return a Observable that emits {@code initialValue} followed by the results of each call to the
     *         accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> scan(final R initialValue, BiFunction<R, ? super T, R> accumulator) {
        Objects.requireNonNull(initialValue, "seed is null");
        return scanWith(new Callable<R>() {
            @Override
            public R call() {
                return initialValue;
            }
        }, accumulator);
    }
    
    /**
     * Returns a Observable that applies a specified accumulator function to the first item emitted by a source
     * ObservableSource and a seed value, then feeds the result of that function along with the second item emitted by
     * the source ObservableSource into the same function, and so on until all items have been emitted by the source
     * ObservableSource, emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scanSeed.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <p>
     * Note that the ObservableSource that results from this method will emit {@code initialValue} as its first
     * emitted item.
     * <p>
     * Note that the {@code initialValue} is shared among all subscribers to the resulting ObservableSource
     * and may cause problems if it is mutable. To make sure each subscriber gets its own value, defer
     * the application of this operator via {@link #defer(Callable)}:
     * <pre><code>
     * ObservableSource&lt;T> source = ...
     * ObservableSource.defer(() -> source.scan(new ArrayList&lt;>(), (list, item) -> list.add(item)));
     * 
     * // alternatively, by using compose to stay fluent
     * 
     * source.compose(o ->
     *     ObservableSource.defer(() -> o.scan(new ArrayList&lt;>(), (list, item) -> list.add(item)))
     * );
     * </code></pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the initial, accumulator and result type
     * @param seedSupplier
     *            a Callable that returns the initial (seed) accumulator item for each individual Subscriber
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source ObservableSource, whose
     *            result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the
     *            next accumulator call
     * @return a Observable that emits {@code initialValue} followed by the results of each call to the
     *         accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> scanWith(Callable<R> seedSupplier, BiFunction<R, ? super T, R> accumulator) {
        Objects.requireNonNull(seedSupplier, "seedSupplier is null");
        Objects.requireNonNull(accumulator, "accumulator is null");
        return new ObservableScanSeed<T, R>(this, seedSupplier, accumulator);
    }

    /**
     * Forces a ObservableSource's emissions and notifications to be serialized and for it to obey
     * <a href="http://reactivex.io/documentation/contract.html">the ObservableSource contract</a> in other ways.
     * <p>
     * It is possible for a ObservableSource to invoke its Subscribers' methods asynchronously, perhaps from
     * different threads. This could make such a ObservableSource poorly-behaved, in that it might try to invoke
     * {@code onCompleted} or {@code onError} before one of its {@code onNext} invocations, or it might call
     * {@code onNext} from two different threads concurrently. You can force such a ObservableSource to be
     * well-behaved and sequential by applying the {@code serialize} method to it.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/synchronize.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code serialize} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return an {@link ObservableSource} that is guaranteed to be well-behaved and to make only serialized calls to
     *         its observers
     * @see <a href="http://reactivex.io/documentation/operators/serialize.html">ReactiveX operators documentation: Serialize</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> serialize() {
        return new ObservableSerialized<T>(this);
    }

    /**
     * Returns a new {@link ObservableSource} that multicasts (shares) the original {@link ObservableSource}. As long as
     * there is at least one {@link Subscriber} this {@link ObservableSource} will be subscribed and emitting data. 
     * When all subscribers have unsubscribed it will unsubscribe from the source {@link ObservableSource}. 
     * <p>
     * This is an alias for {@link #publish()}.{@link ConnectableFlowable#refCount()}.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishRefCount.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code share} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an {@code ObservableSource} that upon connection causes the source {@code ObservableSource} to emit items
     *         to its {@link Observer}s
     * @see <a href="http://reactivex.io/documentation/operators/refcount.html">ReactiveX operators documentation: RefCount</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> share() {
        return publish().refCount();
    }

    /**
     * Returns a Observable that emits the single item emitted by the source ObservableSource, if that ObservableSource
     * emits only a single item. If the source ObservableSource emits more than one item or no items, notify of an
     * {@code IllegalArgumentException} or {@code NoSuchElementException} respectively.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/single.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code single} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits the single item emitted by the source ObservableSource
     * @throws IllegalArgumentException
     *             if the source emits more than one item
     * @throws NoSuchElementException
     *             if the source emits no items
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> single() {
        return new ObservableSingle<T>(this, null);
    }

    /**
     * Returns a Observable that emits the single item emitted by the source ObservableSource, if that ObservableSource
     * emits only a single item, or a default item if the source ObservableSource emits no items. If the source
     * ObservableSource emits more than one item, throw an {@code IllegalArgumentException}.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/singleOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code singleOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            a default value to emit if the source ObservableSource emits no item
     * @return a Observable that emits the single item emitted by the source ObservableSource, or a default item if
     *         the source ObservableSource is empty
     * @throws IllegalArgumentException
     *             if the source ObservableSource emits more than one item
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> single(T defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue is null");
        return new ObservableSingle<T>(this, defaultValue);
    }

    /**
     * Returns a Observable that skips the first {@code count} items emitted by the source ObservableSource and emits
     * the remainder.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code skip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the number of items to skip
     * @return a Observable that is identical to the source ObservableSource except that it does not emit the first
     *         {@code count} items that the source ObservableSource emits
     * @see <a href="http://reactivex.io/documentation/operators/skip.html">ReactiveX operators documentation: Skip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> skip(long count) {
        if (count <= 0) {
            return this;
        }
        return new ObservableSkip<T>(this, count);
    }

    /**
     * Returns a Observable that skips values emitted by the source ObservableSource before a specified time window
     * elapses.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skip.t.png" alt="">
     * <dl>
     *  <dd>{@code skip} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window to skip
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that skips values emitted by the source ObservableSource before the time window defined
     *         by {@code time} elapses and the emits the remainder
     * @see <a href="http://reactivex.io/documentation/operators/skip.html">ReactiveX operators documentation: Skip</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> skip(long time, TimeUnit unit) {
        return skipUntil(timer(time, unit));
    }

    /**
     * Returns a Observable that skips values emitted by the source ObservableSource before a specified time window
     * on a specified {@link Scheduler} elapses.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skip.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use for the timed skipping</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window to skip
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the {@link Scheduler} on which the timed wait happens
     * @return a Observable that skips values emitted by the source ObservableSource before the time window defined
     *         by {@code time} and {@code scheduler} elapses, and then emits the remainder
     * @see <a href="http://reactivex.io/documentation/operators/skip.html">ReactiveX operators documentation: Skip</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> skip(long time, TimeUnit unit, Scheduler scheduler) {
        return skipUntil(timer(time, unit, scheduler));
    }

    /**
     * Returns a Observable that drops a specified number of items from the end of the sequence emitted by the
     * source ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.png" alt="">
     * <p>
     * This Observer accumulates a queue long enough to store the first {@code count} items. As more items are
     * received, items are taken from the front of the queue and emitted by the returned ObservableSource. This causes
     * such items to be delayed.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code skipLast} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            number of items to drop from the end of the source sequence
     * @return a Observable that emits the items emitted by the source ObservableSource except for the dropped ones
     *         at the end
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> skipLast(int count) {
        if (count < 0) {
            throw new IndexOutOfBoundsException("count >= 0 required but it was " + count);
        } else
            if (count == 0) {
                return this;
            }
        return new ObservableSkipLast<T>(this, count);
    }

    /**
     * Returns a Observable that drops items emitted by the source ObservableSource during a specified time window
     * before the source completes.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.t.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code skipLast} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that drops those items emitted by the source ObservableSource in a time window before the
     *         source completes defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<T> skipLast(long time, TimeUnit unit) {
        return skipLast(time, unit, Schedulers.trampoline(), false, bufferSize());
    }

    /**
     * Returns a Observable that drops items emitted by the source ObservableSource during a specified time window
     * before the source completes.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.t.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code skipLast} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @return a Observable that drops those items emitted by the source ObservableSource in a time window before the
     *         source completes defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<T> skipLast(long time, TimeUnit unit, boolean delayError) {
        return skipLast(time, unit, Schedulers.trampoline(), delayError, bufferSize());
    }

    /**
     * Returns a Observable that drops items emitted by the source ObservableSource during a specified time window
     * (defined on a specified scheduler) before the source completes.
     * <p>
     * <img width="640" height="340" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.ts.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use for tracking the current time</dd>
     * </dl>
     *
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler used as the time source
     * @return a Observable that drops those items emitted by the source ObservableSource in a time window before the
     *         source completes defined by {@code time} and {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler) {
        return skipLast(time, unit, scheduler, false, bufferSize());
    }

    /**
     * Returns a Observable that drops items emitted by the source ObservableSource during a specified time window
     * (defined on a specified scheduler) before the source completes.
     * <p>
     * <img width="640" height="340" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.ts.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use to track the current time</dd>
     * </dl>
     *
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler used as the time source
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @return a Observable that drops those items emitted by the source ObservableSource in a time window before the
     *         source completes defined by {@code time} and {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        return skipLast(time, unit, scheduler, delayError, bufferSize());
    }

    /**
     * Returns a Observable that drops items emitted by the source ObservableSource during a specified time window
     * (defined on a specified scheduler) before the source completes.
     * <p>
     * <img width="640" height="340" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.ts.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     *
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler used as the time source
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @param bufferSize
     *            the hint about how many elements to expect to be skipped
     * @return a Observable that drops those items emitted by the source ObservableSource in a time window before the
     *         source completes defined by {@code time} and {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        validateBufferSize(bufferSize, "bufferSize");
     // the internal buffer holds pairs of (timestamp, value) so double the default buffer size
        int s = bufferSize << 1; 
        return new ObservableSkipLastTimed<T>(this, time, unit, scheduler, s, delayError);
    }

    /**
     * Returns a Observable that skips items emitted by the source ObservableSource until a second ObservableSource emits
     * an item.
     * <p>
     * <img width="640" height="375" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipUntil.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code skipUntil} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the element type of the other ObservableSource
     * @param other
     *            the second ObservableSource that has to emit an item before the source ObservableSource's elements begin
     *            to be mirrored by the resulting ObservableSource
     * @return a Observable that skips items from the source ObservableSource until the second ObservableSource emits an
     *         item, then emits the remaining items
     * @see <a href="http://reactivex.io/documentation/operators/skipuntil.html">ReactiveX operators documentation: SkipUntil</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<T> skipUntil(ObservableSource<U> other) {
        Objects.requireNonNull(other, "other is null");
        return new ObservableSkipUntil<T, U>(this, other);
    }

    /**
     * Returns a Observable that skips all items emitted by the source ObservableSource as long as a specified
     * condition holds true, but emits all further source items as soon as the condition becomes false.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipWhile.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code skipWhile} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function to test each item emitted from the source ObservableSource
     * @return a Observable that begins emitting items emitted by the source ObservableSource when the specified
     *         predicate becomes false
     * @see <a href="http://reactivex.io/documentation/operators/skipwhile.html">ReactiveX operators documentation: SkipWhile</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> skipWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new ObservableSkipWhile<T>(this, predicate);
    }

    /**
     * Returns a Observable that emits the events emitted by source ObservableSource, in a
     * sorted order. Each item emitted by the ObservableSource must implement {@link Comparable} with respect to all
     * other items in the sequence.
     *
     * <p>Note that calling {@code sorted} with long, non-terminating or infinite sources
     * might cause {@link OutOfMemoryError}
     *
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sorted} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @throws ClassCastException
     *             if any item emitted by the ObservableSource does not implement {@link Comparable} with respect to
     *             all other items emitted by the ObservableSource
     * @return a Observable that emits the items emitted by the source ObservableSource in sorted order
     */
    public final Observable<T> sorted(){
        return toSortedList().flatMapIterable(Functions.<List<T>>identity());
    }

    /**
     * Returns a Observable that emits the events emitted by source ObservableSource, in a
     * sorted order based on a specified comparison function.
     *
     * <p>Note that calling {@code sorted} with long, non-terminating or infinite sources
     * might cause {@link OutOfMemoryError}
     *
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sorted} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param sortFunction
     *            a function that compares two items emitted by the source ObservableSource and returns an Integer
     *            that indicates their sort order
     * @return a Observable that emits the items emitted by the source ObservableSource in sorted order
     */
    public final Observable<T> sorted(Comparator<? super T> sortFunction) {
        return toSortedList(sortFunction).flatMapIterable(Functions.<List<T>>identity());
    }

    /**
     * Returns a Observable that emits the items in a specified {@link Iterable} before it begins to emit items
     * emitted by the source ObservableSource.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param values
     *            an Iterable that contains the items you want the modified ObservableSource to emit first
     * @return a Observable that emits the items in the specified {@link Iterable} and then emits the items
     *         emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> startWith(Iterable<? extends T> values) {
        return concatArray(fromIterable(values), this);
    }
    
    /**
     * Returns a Observable that emits the items in a specified {@link ObservableSource} before it begins to emit
     * items emitted by the source ObservableSource.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            a ObservableSource that contains the items you want the modified ObservableSource to emit first
     * @return a Observable that emits the items in the specified {@link ObservableSource} and then emits the items
     *         emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> startWith(ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return concatArray(other, this);
    }
    
    /**
     * Returns a Observable that emits a specified item before it begins to emit items emitted by the source
     * ObservableSource.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param value
     *            the item to emit first
     * @return a Observable that emits the specified item before it begins to emit items emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> startWith(T value) {
        Objects.requireNonNull(value, "value is null");
        return concatArray(just(value), this);
    }

    /**
     * Returns a Observable that emits the specified items before it begins to emit items emitted by the source
     * ObservableSource.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param values
     *            the array of values to emit first
     * @return a Observable that emits the specified items before it begins to emit items emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    @SuppressWarnings("unchecked")
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> startWithArray(T... values) {
        Observable<T> fromArray = fromArray(values);
        if (fromArray == empty()) {
            return this;
        }
        return concatArray(fromArray, this);
    }

    /**
     * Subscribes to a ObservableSource and ignores {@code onNext} and {@code onCompleted} emissions. 
     * <p>
     * If the Observable emits an error, it is routed to the RxJavaPlugins.onError handler. 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the ObservableSource has finished sending them
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable subscribe() {
        return subscribe(Functions.emptyConsumer(), RxJavaPlugins.errorConsumer(), Functions.EMPTY_ACTION, Functions.emptyConsumer());
    }

    /**
     * Subscribes to a ObservableSource and provides a callback to handle the items it emits.
     * <p>
     * If the Observable emits an error, it is routed to the RxJavaPlugins.onError handler. 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the ObservableSource
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the ObservableSource has finished sending them
     * @throws NullPointerException
     *             if {@code onNext} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext) {
        return subscribe(onNext, RxJavaPlugins.errorConsumer(), Functions.EMPTY_ACTION, Functions.emptyConsumer());
    }

    /**
     * Subscribes to a ObservableSource and provides callbacks to handle the items it emits and any error
     * notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the ObservableSource
     * @param onError
     *             the {@code Consumer<Throwable>} you have designed to accept any error notification from the
     *             ObservableSource
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the ObservableSource has finished sending them
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError) {
        return subscribe(onNext, onError, Functions.EMPTY_ACTION, Functions.emptyConsumer());
    }

    /**
     * Subscribes to a ObservableSource and provides callbacks to handle the items it emits and any error or
     * completion notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the ObservableSource
     * @param onError
     *             the {@code Consumer<Throwable>} you have designed to accept any error notification from the
     *             ObservableSource
     * @param onComplete
     *             the {@code Action} you have designed to accept a completion notification from the
     *             ObservableSource
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the ObservableSource has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, 
            Action onComplete) {
        return subscribe(onNext, onError, onComplete, Functions.emptyConsumer());
    }

    /**
     * Subscribes to a ObservableSource and provides callbacks to handle the items it emits and any error or
     * completion notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the ObservableSource
     * @param onError
     *             the {@code Consumer<Throwable>} you have designed to accept any error notification from the
     *             ObservableSource
     * @param onComplete
     *             the {@code Action} you have designed to accept a completion notification from the
     *             ObservableSource
     * @param onSubscribe
     *             the {@code Consumer} that receives the upstream's Subscription
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the ObservableSource has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError, 
            Action onComplete, Consumer<? super Disposable> onSubscribe) {
        Objects.requireNonNull(onNext, "onNext is null");
        Objects.requireNonNull(onError, "onError is null");
        Objects.requireNonNull(onComplete, "onComplete is null");
        Objects.requireNonNull(onSubscribe, "onSubscribe is null");

        LambdaObserver<T> ls = new LambdaObserver<T>(onNext, onError, onComplete, onSubscribe);

        subscribe(ls);

        return ls;
    }

    @Override
    public final void subscribe(Observer<? super T> observer) {
        Objects.requireNonNull(observer, "observer is null");
        
        observer = RxJavaPlugins.onSubscribe(this, observer);
        
        subscribeActual(observer);
    }
    
    /**
     * Operator implementations (both source and intermediate) should implement this method that
     * performs the necessary business logic.
     * <p>There is no need to call any of the plugin hooks on the current Observable instance or
     * the Subscriber.
     * @param observer the incoming Observer, never null
     */
    protected abstract void subscribeActual(Observer<? super T> observer);

    /**
     * Asynchronously subscribes Observers to this ObservableSource on the specified {@link Scheduler}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/subscribeOn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to perform subscription actions on
     * @return the source ObservableSource modified so that its subscriptions happen on the
     *         specified {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/subscribeon.html">ReactiveX operators documentation: SubscribeOn</a>
     * @see <a href="http://www.grahamlea.com/2014/07/rxjava-threading-examples/">RxJava Threading Examples</a>
     * @see #observeOn
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> subscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableSubscribeOn<T>(this, scheduler);
    }

    /**
     * Returns a Observable that emits the items emitted by the source ObservableSource or the items of an alternate
     * ObservableSource if the source ObservableSource is empty.
     * <p/>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchIfEmpty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param other
     *              the alternate ObservableSource to subscribe to if the source does not emit any items
     * @return  a ObservableSource that emits the items emitted by the source ObservableSource or the items of an
     *          alternate ObservableSource if the source ObservableSource is empty.
     * @since 1.1.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> switchIfEmpty(ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return new ObservableSwitchIfEmpty<T>(this, other);
    }

    /**
     * Returns a new ObservableSource by applying a function that you supply to each item emitted by the source
     * ObservableSource that returns a ObservableSource, and then emitting the items emitted by the most recently emitted
     * of these ObservableSources.
     * <p>
     * The resulting ObservableSource completes if both the upstream ObservableSource and the last inner ObservableSource, if any, complete.
     * If the upstream ObservableSource signals an onError, the inner ObservableSource is unsubscribed and the error delivered in-sequence.
     * <p>
     * <img width="640" height="350" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the element type of the inner ObservableSources and the output
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @return a Observable that emits the items emitted by the ObservableSource returned from applying {@code func} to the most recently emitted item emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> switchMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        return switchMap(mapper, bufferSize());
    }

    /**
     * Returns a new ObservableSource by applying a function that you supply to each item emitted by the source
     * ObservableSource that returns a ObservableSource, and then emitting the items emitted by the most recently emitted
     * of these ObservableSources.
     * <p>
     * The resulting ObservableSource completes if both the upstream ObservableSource and the last inner ObservableSource, if any, complete.
     * If the upstream ObservableSource signals an onError, the inner ObservableSource is unsubscribed and the error delivered in-sequence.
     * <p>
     * <img width="640" height="350" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the element type of the inner ObservableSources and the output
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param bufferSize
     *            the number of elements to prefetch from the current active inner ObservableSource
     * @return a Observable that emits the items emitted by the ObservableSource returned from applying {@code func} to the most recently emitted item emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <R> Observable<R> switchMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper, int bufferSize) {
        Objects.requireNonNull(mapper, "mapper is null");
        validateBufferSize(bufferSize, "bufferSize");
        return new ObservableSwitchMap<T, R>(this, mapper, bufferSize);
    }

    /**
     * Returns a new ObservableSource by applying a function that you supply to each item emitted by the source
     * ObservableSource that returns a ObservableSource, and then emitting the items emitted by the most recently emitted
     * of these ObservableSources and delays any error until all ObservableSources terminate.
     * <p>
     * The resulting ObservableSource completes if both the upstream ObservableSource and the last inner ObservableSource, if any, complete.
     * If the upstream ObservableSource signals an onError, the termination of the last inner ObservableSource will emit that error as is
     * or wrapped into a CompositeException along with the other possible errors the former inner ObservableSources signalled.
     * <p>
     * <img width="640" height="350" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the element type of the inner ObservableSources and the output
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @return a Observable that emits the items emitted by the ObservableSource returned from applying {@code func} to the most recently emitted item emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    public final <R> Observable<R> switchMapDelayError(Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        return switchMapDelayError(mapper, bufferSize());
    }
    
    /**
     * Returns a new ObservableSource by applying a function that you supply to each item emitted by the source
     * ObservableSource that returns a ObservableSource, and then emitting the items emitted by the most recently emitted
     * of these ObservableSources and delays any error until all ObservableSources terminate.
     * <p>
     * The resulting ObservableSource completes if both the upstream ObservableSource and the last inner ObservableSource, if any, complete.
     * If the upstream ObservableSource signals an onError, the termination of the last inner ObservableSource will emit that error as is
     * or wrapped into a CompositeException along with the other possible errors the former inner ObservableSources signalled.
     * <p>
     * <img width="640" height="350" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the element type of the inner ObservableSources and the output
     * @param mapper
     *            a function that, when applied to an item emitted by the source ObservableSource, returns an
     *            ObservableSource
     * @param bufferSize
     *            the number of elements to prefetch from the current active inner ObservableSource
     * @return a Observable that emits the items emitted by the ObservableSource returned from applying {@code func} to the most recently emitted item emitted by the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    public final <R> Observable<R> switchMapDelayError(Function<? super T, ? extends ObservableSource<? extends R>> mapper, int bufferSize) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a Observable that emits only the first {@code count} items emitted by the source ObservableSource. If the source emits fewer than 
     * {@code count} items then all of its items are emitted.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.png" alt="">
     * <p>
     * This method returns a ObservableSource that will invoke a subscribing {@link Observer}'s
     * {@link Subscriber#onNext onNext} function a maximum of {@code count} times before invoking
     * {@link Subscriber#onComplete onCompleted}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code take} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @return a Observable that emits only the first {@code count} items emitted by the source ObservableSource, or
     *         all of the items from the source ObservableSource if that ObservableSource emits fewer than {@code count} items
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> take(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count >= required but it was " + count);
        } else
        if (count == 0) {
         // FIXME may want to subscribe an cancel immediately
//            return lift(s -> CancelledSubscriber.INSTANCE);
            return empty(); 
        }
        return new ObservableTake<T>(this, count);
    }

    /**
     * Returns a Observable that emits those items emitted by source ObservableSource before a specified time runs
     * out.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code take} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits those items emitted by the source ObservableSource before the time runs out
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> take(long time, TimeUnit unit) {
        return takeUntil(timer(time, unit));
    }

    /**
     * Returns a Observable that emits those items emitted by source ObservableSource before a specified time (on a
     * specified Scheduler) runs out.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler used for time source
     * @return a Observable that emits those items emitted by the source ObservableSource before the time runs out,
     *         according to the specified Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> take(long time, TimeUnit unit, Scheduler scheduler) {
        return takeUntil(timer(time, unit, scheduler));
    }

    /**
     * Returns a Observable that emits only the very first item emitted by the source ObservableSource that satisfies
     * a specified condition.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeFirstN.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeFirst} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition any item emitted by the source ObservableSource has to satisfy
     * @return a Observable that emits only the very first item emitted by the source ObservableSource that satisfies
     *         the given condition, or that completes without emitting anything if the source ObservableSource
     *         completes without emitting a single condition-satisfying item
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> takeFirst(Predicate<? super T> predicate) {
        return filter(predicate).take(1);
    }

    /**
     * Returns a Observable that emits at most the last {@code count} items emitted by the source ObservableSource. If the source emits fewer than 
     * {@code count} items then all of its items are emitted.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.n.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLast} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit from the end of the sequence of items emitted by the source
     *            ObservableSource
     * @return a Observable that emits at most the last {@code count} items emitted by the source ObservableSource
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> takeLast(int count) {
        if (count < 0) {
            throw new IndexOutOfBoundsException("count >= required but it was " + count);
        } else
        if (count == 0) {
            return ignoreElements();
        } else
        if (count == 1) {
            return new ObservableTakeLastOne<T>(this);
        }
        return new ObservableTakeLast<T>(this, count);
    }

    /**
     * Returns a Observable that emits at most a specified number of items from the source ObservableSource that were
     * emitted in a specified window of time before the ObservableSource completed. 
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.tn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeLast} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits at most {@code count} items from the source ObservableSource that were emitted
     *         in a specified window of time before the ObservableSource completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<T> takeLast(long count, long time, TimeUnit unit) {
        return takeLast(count, time, unit, Schedulers.trampoline(), false, bufferSize());
    }

    /**
     * Returns a Observable that emits at most a specified number of items from the source ObservableSource that were
     * emitted in a specified window of time before the ObservableSource completed, where the timing information is
     * provided by a given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.tns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use for tracking the current time</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the {@link Scheduler} that provides the timestamps for the observed items
     * @return a Observable that emits at most {@code count} items from the source ObservableSource that were emitted
     *         in a specified window of time before the ObservableSource completed, where the timing information is
     *         provided by the given {@code scheduler}
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> takeLast(long count, long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(count, time, unit, scheduler, false, bufferSize());
    }

    /**
     * Returns a Observable that emits at most a specified number of items from the source ObservableSource that were
     * emitted in a specified window of time before the ObservableSource completed, where the timing information is
     * provided by a given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.tns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use for tracking the current time</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the {@link Scheduler} that provides the timestamps for the observed items
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @param bufferSize
     *            the hint about how many elements to expect to be last
     * @return a Observable that emits at most {@code count} items from the source ObservableSource that were emitted
     *         in a specified window of time before the ObservableSource completed, where the timing information is
     *         provided by the given {@code scheduler}
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> takeLast(long count, long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        validateBufferSize(bufferSize, "bufferSize");
        if (count < 0) {
            throw new IndexOutOfBoundsException("count >= 0 required but it was " + count);
        }
        return new ObservableTakeLastTimed<T>(this, count, time, unit, scheduler, bufferSize, delayError);
    }

    /**
     * Returns a Observable that emits the items from the source ObservableSource that were emitted in a specified
     * window of time before the ObservableSource completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits the items from the source ObservableSource that were emitted in the window of
     *         time before the ObservableSource completed specified by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<T> takeLast(long time, TimeUnit unit) {
        return takeLast(time, unit, Schedulers.trampoline(), false, bufferSize());
    }

    /**
     * Returns a Observable that emits the items from the source ObservableSource that were emitted in a specified
     * window of time before the ObservableSource completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @return a Observable that emits the items from the source ObservableSource that were emitted in the window of
     *         time before the ObservableSource completed specified by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<T> takeLast(long time, TimeUnit unit, boolean delayError) {
        return takeLast(time, unit, Schedulers.trampoline(), delayError, bufferSize());
    }

    /**
     * Returns a Observable that emits the items from the source ObservableSource that were emitted in a specified
     * window of time before the ObservableSource completed, where the timing information is provided by a specified
     * Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the Observed items
     * @return a Observable that emits the items from the source ObservableSource that were emitted in the window of
     *         time before the ObservableSource completed specified by {@code time}, where the timing information is
     *         provided by {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(time, unit, scheduler, false, bufferSize());
    }

    /**
     * Returns a Observable that emits the items from the source ObservableSource that were emitted in a specified
     * window of time before the ObservableSource completed, where the timing information is provided by a specified
     * Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the Observed items
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @return a Observable that emits the items from the source ObservableSource that were emitted in the window of
     *         time before the ObservableSource completed specified by {@code time}, where the timing information is
     *         provided by {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        return takeLast(time, unit, scheduler, delayError, bufferSize());
    }

    /**
     * Returns a Observable that emits the items from the source ObservableSource that were emitted in a specified
     * window of time before the ObservableSource completed, where the timing information is provided by a specified
     * Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the Observed items
     * @param delayError
     *            if true, an exception signalled by the current Observable is delayed until the regular elements are consumed
     *            by the downstream; if false, an exception is immediately signalled and all regular elements dropped
     * @param bufferSize
     *            the hint about how many elements to expect to be last
     * @return a Observable that emits the items from the source ObservableSource that were emitted in the window of
     *         time before the ObservableSource completed specified by {@code time}, where the timing information is
     *         provided by {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize) {
        return takeLast(Long.MAX_VALUE, time, unit, scheduler, delayError, bufferSize);
    }

    /**
     * Returns a Observable that emits a single List containing at most the last {@code count} elements emitted by the
     * source ObservableSource. If the source emits fewer than {@code count} items then the emitted List will contain all of the source emissions.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLastBuffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit in the list
     * @return a Observable that emits a single list containing at most the last {@code count} elements emitted by the
     *         source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> takeLastBuffer(int count) {
        return takeLast(count).toList();
    }

    /**
     * Returns a Observable that emits a single List containing at most {@code count} items from the source
     * ObservableSource that were emitted during a specified window of time before the source ObservableSource completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.tn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLastBuffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits a single List containing at most {@code count} items emitted by the
     *         source ObservableSource during the time window defined by {@code time} before the source ObservableSource
     *         completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<List<T>> takeLastBuffer(int count, long time, TimeUnit unit) {
        return takeLast(count, time, unit).toList();
    }

    /**
     * Returns a Observable that emits a single List containing at most {@code count} items from the source
     * ObservableSource that were emitted during a specified window of time (on a specified Scheduler) before the
     * source ObservableSource completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.tns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the observed items
     * @return a Observable that emits a single List containing at most {@code count} items emitted by the
     *         source ObservableSource during the time window defined by {@code time} before the source ObservableSource
     *         completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<List<T>> takeLastBuffer(int count, long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(count, time, unit, scheduler).toList();
    }

    /**
     * Returns a Observable that emits a single List containing those items from the source ObservableSource that
     * were emitted during a specified window of time before the source ObservableSource completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLastBuffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return a Observable that emits a single List containing the items emitted by the source ObservableSource
     *         during the time window defined by {@code time} before the source ObservableSource completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.TRAMPOLINE)
    public final Observable<List<T>> takeLastBuffer(long time, TimeUnit unit) {
        return takeLast(time, unit).toList();
    }

    /**
     * Returns a Observable that emits a single List containing those items from the source ObservableSource that
     * were emitted during a specified window of time before the source ObservableSource completed, where the timing
     * information is provided by the given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the observed items
     * @return a Observable that emits a single List containing the items emitted by the source ObservableSource
     *         during the time window defined by {@code time} before the source ObservableSource completed, where the
     *         timing information is provided by {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<List<T>> takeLastBuffer(long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(time, unit, scheduler).toList();
    }

    /**
     * Returns a Observable that emits the items emitted by the source Publisher until a second Publisher
     * emits an item.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeUntil.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeUntil} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            the Publisher whose first emitted item will cause {@code takeUntil} to stop emitting items
     *            from the source Publisher
     * @param <U>
     *            the type of items emitted by {@code other}
     * @return a Observable that emits the items emitted by the source Publisher until such time as {@code other} emits its first item
     * @see <a href="http://reactivex.io/documentation/operators/takeuntil.html">ReactiveX operators documentation: TakeUntil</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U> Observable<T> takeUntil(ObservableSource<U> other) {
        Objects.requireNonNull(other, "other is null");
        return new ObservableTakeUntil<T, U>(this, other);
    }

    /**
     * Returns a Observable that emits items emitted by the source Publisher, checks the specified predicate
     * for each item, and then completes when the condition is satisfied.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeUntil.p.png" alt="">
     * <p>
     * The difference between this operator and {@link #takeWhile(Predicate)} is that here, the condition is
     * evaluated <em>after</em> the item is emitted.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeUntil} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param stopPredicate 
     *            a function that evaluates an item emitted by the source Publisher and returns a Boolean
     * @return a Observable that first emits items emitted by the source Publisher, checks the specified
     *         condition after each item, and then completes when the condition is satisfied.
     * @see <a href="http://reactivex.io/documentation/operators/takeuntil.html">ReactiveX operators documentation: TakeUntil</a>
     * @see Observable#takeWhile(Predicate)
     * @since 1.1.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> takeUntil(Predicate<? super T> stopPredicate) {
        Objects.requireNonNull(stopPredicate, "predicate is null");
        return new ObservableTakeUntilPredicate<T>(this, stopPredicate);
    }

    /**
     * Returns a Observable that emits items emitted by the source ObservableSource so long as each item satisfied a
     * specified condition, and then completes as soon as this condition is not satisfied.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeWhile.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeWhile} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function that evaluates an item emitted by the source ObservableSource and returns a Boolean
     * @return a Observable that emits the items from the source ObservableSource so long as each item satisfies the
     *         condition defined by {@code predicate}, then completes
     * @see <a href="http://reactivex.io/documentation/operators/takewhile.html">ReactiveX operators documentation: TakeWhile</a>
     * @see Observable#takeUntil(Predicate)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<T> takeWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new ObservableTakeWhile<T>(this, predicate);
    }

    /**
     * Returns a Observable that emits only the first item emitted by the source ObservableSource during sequential
     * time windows of a specified duration.
     * <p>
     * This differs from {@link #throttleLast} in that this only tracks passage of time whereas
     * {@link #throttleLast} ticks at scheduled intervals.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleFirst.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code throttleFirst} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param windowDuration
     *            time to wait before emitting another item after emitting the last item
     * @param unit
     *            the unit of time of {@code windowDuration}
     * @return a Observable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> throttleFirst(long windowDuration, TimeUnit unit) {
        return throttleFirst(windowDuration, unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits only the first item emitted by the source ObservableSource during sequential
     * time windows of a specified duration, where the windows are managed by a specified Scheduler.
     * <p>
     * This differs from {@link #throttleLast} in that this only tracks passage of time whereas
     * {@link #throttleLast} ticks at scheduled intervals.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleFirst.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param skipDuration
     *            time to wait before emitting another item after emitting the last item
     * @param unit
     *            the unit of time of {@code skipDuration}
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle timeout for each
     *            event
     * @return a Observable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> throttleFirst(long skipDuration, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableThrottleFirstTimed<T>(this, skipDuration, unit, scheduler);
    }

    /**
     * Returns a Observable that emits only the last item emitted by the source ObservableSource during sequential
     * time windows of a specified duration.
     * <p>
     * This differs from {@link #throttleFirst} in that this ticks along at a scheduled interval whereas
     * {@link #throttleFirst} does not tick, it just tracks passage of time.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleLast.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code throttleLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param intervalDuration
     *            duration of windows within which the last item emitted by the source ObservableSource will be
     *            emitted
     * @param unit
     *            the unit of time of {@code intervalDuration}
     * @return a Observable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see #sample(long, TimeUnit)
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> throttleLast(long intervalDuration, TimeUnit unit) {
        return sample(intervalDuration, unit);
    }

    /**
     * Returns a Observable that emits only the last item emitted by the source ObservableSource during sequential
     * time windows of a specified duration, where the duration is governed by a specified Scheduler.
     * <p>
     * This differs from {@link #throttleFirst} in that this ticks along at a scheduled interval whereas
     * {@link #throttleFirst} does not tick, it just tracks passage of time.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleLast.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param intervalDuration
     *            duration of windows within which the last item emitted by the source ObservableSource will be
     *            emitted
     * @param unit
     *            the unit of time of {@code intervalDuration}
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle timeout for each
     *            event
     * @return a Observable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see #sample(long, TimeUnit, Scheduler)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> throttleLast(long intervalDuration, TimeUnit unit, Scheduler scheduler) {
        return sample(intervalDuration, unit, scheduler);
    }

    /**
     * Returns a Observable that only emits those items emitted by the source ObservableSource that are not followed
     * by another emitted item within a specified time window.
     * <p>
     * <em>Note:</em> If the source ObservableSource keeps emitting items more frequently than the length of the time
     * window then no items will be emitted by the resulting ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleWithTimeout.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code throttleWithTimeout} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            the length of the window of time that must pass after the emission of an item from the source
     *            ObservableSource in which that ObservableSource emits no items in order for the item to be emitted by the
     *            resulting ObservableSource
     * @param unit
     *            the {@link TimeUnit} of {@code timeout}
     * @return a Observable that filters out items that are too quickly followed by newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see #debounce(long, TimeUnit)
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> throttleWithTimeout(long timeout, TimeUnit unit) {
        return debounce(timeout, unit);
    }

    /**
     * Returns a Observable that only emits those items emitted by the source ObservableSource that are not followed
     * by another emitted item within a specified time window, where the time window is governed by a specified
     * Scheduler.
     * <p>
     * <em>Note:</em> If the source ObservableSource keeps emitting items more frequently than the length of the time
     * window then no items will be emitted by the resulting ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleWithTimeout.s.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            the length of the window of time that must pass after the emission of an item from the source
     *            ObservableSource in which that ObservableSource emits no items in order for the item to be emitted by the
     *            resulting ObservableSource
     * @param unit
     *            the {@link TimeUnit} of {@code timeout}
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle the timeout for each
     *            item
     * @return a Observable that filters out items that are too quickly followed by newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see #debounce(long, TimeUnit, Scheduler)
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> throttleWithTimeout(long timeout, TimeUnit unit, Scheduler scheduler) {
        return debounce(timeout, unit, scheduler);
    }

    /**
     * Returns a Observable that emits records of the time interval between consecutive items emitted by the
     * source ObservableSource.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeInterval.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timeInterval} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits time interval information items
     * @see <a href="http://reactivex.io/documentation/operators/timeinterval.html">ReactiveX operators documentation: TimeInterval</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Timed<T>> timeInterval() {
        return timeInterval(TimeUnit.MILLISECONDS, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits records of the time interval between consecutive items emitted by the
     * source ObservableSource, where this interval is computed on a specified Scheduler.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeInterval.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator does not operate on any particular scheduler but uses the current time
     *  from the specified {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} used to compute time intervals
     * @return a Observable that emits time interval information items
     * @see <a href="http://reactivex.io/documentation/operators/timeinterval.html">ReactiveX operators documentation: TimeInterval</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE) // Supplied scheduler is only used for creating timestamps.
    public final Observable<Timed<T>> timeInterval(Scheduler scheduler) {
        return timeInterval(TimeUnit.MILLISECONDS, scheduler);
    }

    /**
     * Returns a Observable that emits records of the time interval between consecutive items emitted by the
     * source ObservableSource.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeInterval.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timeInterval} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param unit the time unit for the current time
     * @return a Observable that emits time interval information items
     * @see <a href="http://reactivex.io/documentation/operators/timeinterval.html">ReactiveX operators documentation: TimeInterval</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE) // Trampoline scheduler is only used for creating timestamps.
    public final Observable<Timed<T>> timeInterval(TimeUnit unit) {
        return timeInterval(unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits records of the time interval between consecutive items emitted by the
     * source ObservableSource, where this interval is computed on a specified Scheduler.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeInterval.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator does not operate on any particular scheduler but uses the current time
     *  from the specified {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param unit the time unit for the current time
     * @param scheduler
     *            the {@link Scheduler} used to compute time intervals
     * @return a Observable that emits time interval information items
     * @see <a href="http://reactivex.io/documentation/operators/timeinterval.html">ReactiveX operators documentation: TimeInterval</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE) // Supplied scheduler is only used for creating timestamps.
    public final Observable<Timed<T>> timeInterval(TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableTimeInterval<T>(this, unit, scheduler);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, but notifies observers of a
     * {@code TimeoutException} if an item emitted by the source ObservableSource doesn't arrive within a window of
     * time after the emission of the previous item, where that period of time is measured by a ObservableSource that
     * is a function of the previous item.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout3.png" alt="">
     * <p>
     * Note: The arrival of the first source item is never timed out.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <V>
     *            the timeout value type (ignored)
     * @param timeoutSelector
     *            a function that returns a ObservableSource for each item emitted by the source
     *            ObservableSource and that determines the timeout window for the subsequent item
     * @return a Observable that mirrors the source ObservableSource, but notifies observers of a
     *         {@code TimeoutException} if an item emitted by the source ObservableSource takes longer to arrive than
     *         the time window defined by the selector for the previously emitted item
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <V> Observable<T> timeout(Function<? super T, ? extends ObservableSource<V>> timeoutSelector) {
        return timeout0(null, timeoutSelector, null);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, but that switches to a fallback ObservableSource if
     * an item emitted by the source ObservableSource doesn't arrive within a window of time after the emission of the
     * previous item, where that period of time is measured by a ObservableSource that is a function of the previous
     * item.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout4.png" alt="">
     * <p>
     * Note: The arrival of the first source item is never timed out.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <V>
     *            the timeout value type (ignored)
     * @param timeoutSelector
     *            a function that returns a ObservableSource, for each item emitted by the source ObservableSource, that
     *            determines the timeout window for the subsequent item
     * @param other
     *            the fallback ObservableSource to switch to if the source ObservableSource times out
     * @return a Observable that mirrors the source ObservableSource, but switches to mirroring a fallback ObservableSource
     *         if an item emitted by the source ObservableSource takes longer to arrive than the time window defined
     *         by the selector for the previously emitted item
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <V> Observable<T> timeout(Function<? super T, ? extends ObservableSource<V>> timeoutSelector, 
            ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return timeout0(null, timeoutSelector, other);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource but applies a timeout policy for each emitted
     * item. If the next item isn't emitted within the specified timeout duration starting from its predecessor,
     * the resulting ObservableSource terminates and notifies observers of a {@code TimeoutException}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.1.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between emitted items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument.
     * @return the source ObservableSource modified to notify observers of a {@code TimeoutException} in case of a
     *         timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> timeout(long timeout, TimeUnit timeUnit) {
        return timeout0(timeout, timeUnit, null, Schedulers.computation());
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource but applies a timeout policy for each emitted
     * item. If the next item isn't emitted within the specified timeout duration starting from its predecessor,
     * the resulting ObservableSource begins instead to mirror a fallback ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.2.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument
     * @param other
     *            the fallback ObservableSource to use in case of a timeout
     * @return the source ObservableSource modified to switch to the fallback ObservableSource in case of a timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<T> timeout(long timeout, TimeUnit timeUnit, ObservableSource<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return timeout0(timeout, timeUnit, other, Schedulers.computation());
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource but applies a timeout policy for each emitted
     * item using a specified Scheduler. If the next item isn't emitted within the specified timeout duration
     * starting from its predecessor, the resulting ObservableSource begins instead to mirror a fallback ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.2s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument
     * @param other
     *            the ObservableSource to use as the fallback in case of a timeout
     * @param scheduler
     *            the {@link Scheduler} to run the timeout timers on
     * @return the source ObservableSource modified so that it will switch to the fallback ObservableSource in case of a
     *         timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> timeout(long timeout, TimeUnit timeUnit, ObservableSource<? extends T> other, Scheduler scheduler) {
        Objects.requireNonNull(other, "other is null");
        return timeout0(timeout, timeUnit, other, scheduler);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource but applies a timeout policy for each emitted
     * item, where this policy is governed on a specified Scheduler. If the next item isn't emitted within the
     * specified timeout duration starting from its predecessor, the resulting ObservableSource terminates and
     * notifies observers of a {@code TimeoutException}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.1s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument
     * @param scheduler
     *            the Scheduler to run the timeout timers on
     * @return the source ObservableSource modified to notify observers of a {@code TimeoutException} in case of a
     *         timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> timeout(long timeout, TimeUnit timeUnit, Scheduler scheduler) {
        return timeout0(timeout, timeUnit, null, scheduler);
    }
    
    /**
     * Returns a Observable that mirrors the source ObservableSource, but notifies observers of a
     * {@code TimeoutException} if either the first item emitted by the source ObservableSource or any subsequent item
     * doesn't arrive within time windows defined by other ObservableSources.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout5.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the first timeout value type (ignored)
     * @param <V>
     *            the subsequent timeout value type (ignored)
     * @param firstTimeoutSelector
     *            a function that returns a ObservableSource that determines the timeout window for the first source
     *            item
     * @param timeoutSelector
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource and that
     *            determines the timeout window in which the subsequent source item must arrive in order to
     *            continue the sequence
     * @return a Observable that mirrors the source ObservableSource, but notifies observers of a
     *         {@code TimeoutException} if either the first item or any subsequent item doesn't arrive within
     *         the time windows specified by the timeout selectors
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final <U, V> Observable<T> timeout(Callable<? extends ObservableSource<U>> firstTimeoutSelector,
            Function<? super T, ? extends ObservableSource<V>> timeoutSelector) {
        Objects.requireNonNull(firstTimeoutSelector, "firstTimeoutSelector is null");
        return timeout0(firstTimeoutSelector, timeoutSelector, null);
    }

    /**
     * Returns a Observable that mirrors the source ObservableSource, but switches to a fallback ObservableSource if either
     * the first item emitted by the source ObservableSource or any subsequent item doesn't arrive within time windows
     * defined by other ObservableSources.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout6.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the first timeout value type (ignored)
     * @param <V>
     *            the subsequent timeout value type (ignored)
     * @param firstTimeoutSelector
     *            a function that returns a ObservableSource which determines the timeout window for the first source
     *            item
     * @param timeoutSelector
     *            a function that returns a ObservableSource for each item emitted by the source ObservableSource and that
     *            determines the timeout window in which the subsequent source item must arrive in order to
     *            continue the sequence
     * @param other
     *            the fallback ObservableSource to switch to if the source ObservableSource times out
     * @return a Observable that mirrors the source ObservableSource, but switches to the {@code other} ObservableSource if
     *         either the first item emitted by the source ObservableSource or any subsequent item doesn't arrive
     *         within time windows defined by the timeout selectors
     * @throws NullPointerException
     *             if {@code timeoutSelector} is null
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, V> Observable<T> timeout(
            Callable<? extends ObservableSource<U>> firstTimeoutSelector,
            Function<? super T, ? extends ObservableSource<V>> timeoutSelector,
                    ObservableSource<? extends T> other) {
        Objects.requireNonNull(firstTimeoutSelector, "firstTimeoutSelector is null");
        Objects.requireNonNull(other, "other is null");
        return timeout0(firstTimeoutSelector, timeoutSelector, other);
    }
    
    private Observable<T> timeout0(long timeout, TimeUnit timeUnit, ObservableSource<? extends T> other,
            Scheduler scheduler) {
        Objects.requireNonNull(timeUnit, "timeUnit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableTimeoutTimed<T>(this, timeout, timeUnit, scheduler, other);
    }

    private <U, V> Observable<T> timeout0(
            Callable<? extends ObservableSource<U>> firstTimeoutSelector,
            Function<? super T, ? extends ObservableSource<V>> timeoutSelector,
                    ObservableSource<? extends T> other) {
        Objects.requireNonNull(timeoutSelector, "timeoutSelector is null");
        return new ObservableTimeout<T, U, V>(this, firstTimeoutSelector, timeoutSelector, other);
    }

    /**
     * Returns a Observable that emits each item emitted by the source ObservableSource, wrapped in a
     * {@link Timed} object.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timestamp.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timestamp} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits timestamped items from the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/timestamp.html">ReactiveX operators documentation: Timestamp</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Timed<T>> timestamp() {
        return timestamp(TimeUnit.MILLISECONDS, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits each item emitted by the source ObservableSource, wrapped in a
     * {@link Timed} object whose timestamps are provided by a specified Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timestamp.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator does not operate on any particular scheduler but uses the current time
     *  from the specified {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to use as a time source
     * @return a Observable that emits timestamped items from the source ObservableSource with timestamps provided by
     *         the {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/timestamp.html">ReactiveX operators documentation: Timestamp</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE) // Supplied scheduler is only used for creating timestamps.
    public final Observable<Timed<T>> timestamp(Scheduler scheduler) {
        return timestamp(TimeUnit.MILLISECONDS, scheduler);
    }

    /**
     * Returns a Observable that emits each item emitted by the source ObservableSource, wrapped in a
     * {@link Timed} object.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timestamp.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timestamp} does not operate on any particular scheduler but uses the current time
     *  from the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param unit the time unit for the current time
     * @return a Observable that emits timestamped items from the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/timestamp.html">ReactiveX operators documentation: Timestamp</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Timed<T>> timestamp(TimeUnit unit) {
        return timestamp(unit, Schedulers.computation());
    }

    /**
     * Returns a Observable that emits each item emitted by the source ObservableSource, wrapped in a
     * {@link Timed} object whose timestamps are provided by a specified Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timestamp.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator does not operate on any particular scheduler but uses the current time
     *  from the specified {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param unit the time unit for the current time
     * @param scheduler
     *            the {@link Scheduler} to use as a time source
     * @return a Observable that emits timestamped items from the source ObservableSource with timestamps provided by
     *         the {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/timestamp.html">ReactiveX operators documentation: Timestamp</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE) // Supplied scheduler is only used for creating timestamps.
    public final Observable<Timed<T>> timestamp(final TimeUnit unit, final Scheduler scheduler) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return map(new Function<T, Timed<T>>() {
            @Override
            public Timed<T> apply(T v) {
                return new Timed<T>(v, scheduler.now(unit), unit);
            }
        });
    }

    /**
     * Calls the specified converter function during assembly time and returns its resulting value.
     * <p>
     * This allows fluent conversion to any other type.
     * @param <R> the resulting object type
     * @param converter the function that receives the current Observable instance and returns a vlau
     * @return the value returned by the function
     */
    public final <R> R to(Function<? super Observable<T>, R> converter) {
        try {
            return converter.apply(this);
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            throw Exceptions.propagate(ex);
        }
    }

    /**
     * Converts a ObservableSource into a {@link BlockingFlowable} (a ObservableSource with blocking operators).
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toBlocking} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return a {@code BlockingObservableSource} version of this ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final BlockingObservable<T> toBlocking() {
        return BlockingObservable.from(this);
    }
    
    /**
     * Returns a Completable that discards all onNext emissions (similar to
     * {@code ignoreAllElements()}) and calls onCompleted when this source ObservableSource calls
     * onCompleted. Error terminal events are propagated.
     * <p>
     * <img width="640" height="295" src=
     * "https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/Completable.toCompletable.png"
     * alt="">
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code toCompletable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return a Completable that calls onCompleted on it's subscriber when the source ObservableSource
     *         calls onCompleted
     * @see <a href="http://reactivex.io/documentation/completable.html">ReactiveX documentation:
     *      Completable</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical
     *        with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Completable toCompletable() {
        return new CompletableFromObservable<T>(this);
    }

    /**
     * Returns a Observable that emits a single item, a list composed of all the items emitted by the source
     * ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toList.png" alt="">
     * <p>
     * Normally, a ObservableSource that returns multiple items will do so by invoking its {@link Observer}'s
     * {@link Observer#onNext onNext} method for each such item. You can change this behavior, instructing the
     * ObservableSource to compose a list of all of these items and then to invoke the Observer's {@code onNext}
     * function once, passing it the entire list, by calling the ObservableSource's {@code toList} method prior to
     * calling its {@link #subscribe} method.
     * <p>
     * Be careful not to use this operator on ObservableSources that emit infinite or very large numbers of items, as
     * you do not have the option to unsubscribe.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a Observable that emits a single item: a List containing all of the items emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> toList() {
        return toList(16);
    }

    /**
     * Returns a Observable that emits a single item, a list composed of all the items emitted by the source
     * ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toList.png" alt="">
     * <p>
     * Normally, a ObservableSource that returns multiple items will do so by invoking its {@link Observer}'s
     * {@link Observer#onNext onNext} method for each such item. You can change this behavior, instructing the
     * ObservableSource to compose a list of all of these items and then to invoke the Observer's {@code onNext}
     * function once, passing it the entire list, by calling the ObservableSource's {@code toList} method prior to
     * calling its {@link #subscribe} method.
     * <p>
     * Be careful not to use this operator on ObservableSources that emit infinite or very large numbers of items, as
     * you do not have the option to unsubscribe.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param capacityHint
     *         the number of elements expected from the current Observable
     * @return a Observable that emits a single item: a List containing all of the items emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> toList(final int capacityHint) {
        if (capacityHint <= 0) {
            throw new IllegalArgumentException("capacityHint > 0 required but it was " + capacityHint);
        }
        return new ObservableToList<T, List<T>>(this, capacityHint);
    }

    /**
     * Returns a Observable that emits a single item, a list composed of all the items emitted by the source
     * ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toList.png" alt="">
     * <p>
     * Normally, a ObservableSource that returns multiple items will do so by invoking its {@link Observer}'s
     * {@link Observer#onNext onNext} method for each such item. You can change this behavior, instructing the
     * ObservableSource to compose a list of all of these items and then to invoke the Observer's {@code onNext}
     * function once, passing it the entire list, by calling the ObservableSource's {@code toList} method prior to
     * calling its {@link #subscribe} method.
     * <p>
     * Be careful not to use this operator on ObservableSources that emit infinite or very large numbers of items, as
     * you do not have the option to unsubscribe.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the subclass of a collection of Ts
     * @param collectionSupplier
     *               the Callable returning the collection (for each individual Subscriber) to be filled in
     * @return a Observable that emits a single item: a List containing all of the items emitted by the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U extends Collection<? super T>> Observable<U> toList(Callable<U> collectionSupplier) {
        Objects.requireNonNull(collectionSupplier, "collectionSupplier is null");
        return new ObservableToList<T, U>(this, collectionSupplier);
    }

    /**
     * Returns a Observable that emits a single HashMap containing all items emitted by the source ObservableSource,
     * mapped by the keys returned by a specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMap.png" alt="">
     * <p>
     * If more than one source item maps to the same key, the HashMap will contain the latest of those items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param keySelector
     *            the function that extracts the key from a source item to be used in the HashMap
     * @return a Observable that emits a single item: a HashMap containing the mapped items from the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<Map<K, T>> toMap(final Function<? super T, ? extends K> keySelector) {
        return collect(HashMapSupplier.<K, T>asCallable(), new BiConsumer<Map<K, T>, T>() {
            @Override
            public void accept(Map<K, T> m, T t) throws Exception {
                K key = keySelector.apply(t);
                m.put(key, t);
            }
        });
    }

    /**
     * Returns a Observable that emits a single HashMap containing values corresponding to items emitted by the
     * source ObservableSource, mapped by the keys returned by a specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMap.png" alt="">
     * <p>
     * If more than one source item maps to the same key, the HashMap will contain a single entry that
     * corresponds to the latest of those items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param <V> the value type of the Map
     * @param keySelector
     *            the function that extracts the key from a source item to be used in the HashMap
     * @param valueSelector
     *            the function that extracts the value from a source item to be used in the HashMap
     * @return a Observable that emits a single item: a HashMap containing the mapped items from the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<Map<K, V>> toMap(
            final Function<? super T, ? extends K> keySelector, 
            final Function<? super T, ? extends V> valueSelector) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(valueSelector, "valueSelector is null");
        return collect(HashMapSupplier.<K, V>asCallable(), new BiConsumer<Map<K, V>, T>() {
            @Override
            public void accept(Map<K, V> m, T t) throws Exception {
                K key = keySelector.apply(t);
                V value = valueSelector.apply(t);
                m.put(key, value);
            }
        });
    }

    /**
     * Returns a Observable that emits a single Map, returned by a specified {@code mapFactory} function, that
     * contains keys and values extracted from the items emitted by the source ObservableSource.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param <V> the value type of the Map
     * @param keySelector
     *            the function that extracts the key from a source item to be used in the Map
     * @param valueSelector
     *            the function that extracts the value from the source items to be used as value in the Map
     * @param mapSupplier
     *            the function that returns a Map instance to be used
     * @return a Observable that emits a single item: a Map that contains the mapped items emitted by the
     *         source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<Map<K, V>> toMap(
            final Function<? super T, ? extends K> keySelector, 
            final Function<? super T, ? extends V> valueSelector,
            Callable<? extends Map<K, V>> mapSupplier) {
        return collect(mapSupplier, new BiConsumer<Map<K, V>, T>() {
            @Override
            public void accept(Map<K, V> m, T t) throws Exception {
                K key = keySelector.apply(t);
                V value = valueSelector.apply(t);
                m.put(key, value);
            }
        });
    }

    /**
     * Returns a Observable that emits a single HashMap that contains an ArrayList of items emitted by the
     * source ObservableSource keyed by a specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param keySelector
     *            the function that extracts the key from the source items to be used as key in the HashMap
     * @return a Observable that emits a single item: a HashMap that contains an ArrayList of items mapped from
     *         the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K> Observable<Map<K, Collection<T>>> toMultimap(Function<? super T, ? extends K> keySelector) {
        @SuppressWarnings({ "rawtypes", "unchecked" })
        Function<? super T, ? extends T> valueSelector = (Function)Functions.identity();
        Callable<Map<K, Collection<T>>> mapSupplier = HashMapSupplier.asCallable();
        Function<K, List<T>> collectionFactory = ArrayListSupplier.asFunction();
        return toMultimap(keySelector, valueSelector, mapSupplier, collectionFactory);
    }

    /**
     * Returns a Observable that emits a single HashMap that contains an ArrayList of values extracted by a
     * specified {@code valueSelector} function from items emitted by the source ObservableSource, keyed by a
     * specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param <V> the value type of the Map
     * @param keySelector
     *            the function that extracts a key from the source items to be used as key in the HashMap
     * @param valueSelector
     *            the function that extracts a value from the source items to be used as value in the HashMap
     * @return a Observable that emits a single item: a HashMap that contains an ArrayList of items mapped from
     *         the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<Map<K, Collection<V>>> toMultimap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector) {
        Callable<Map<K, Collection<V>>> mapSupplier = HashMapSupplier.asCallable();
        Function<K, List<V>> collectionFactory = ArrayListSupplier.asFunction();
        return toMultimap(keySelector, valueSelector, mapSupplier, collectionFactory);
    }

    /**
     * Returns a Observable that emits a single Map, returned by a specified {@code mapFactory} function, that
     * contains a custom collection of values, extracted by a specified {@code valueSelector} function from
     * items emitted by the source ObservableSource, and keyed by the {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param <V> the value type of the Map
     * @param keySelector
     *            the function that extracts a key from the source items to be used as the key in the Map
     * @param valueSelector
     *            the function that extracts a value from the source items to be used as the value in the Map
     * @param mapSupplier
     *            the function that returns a Map instance to be used
     * @param collectionFactory
     *            the function that returns a Collection instance for a particular key to be used in the Map
     * @return a Observable that emits a single item: a Map that contains the collection of mapped items from
     *         the source ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @SuppressWarnings("unchecked")
    public final <K, V> Observable<Map<K, Collection<V>>> toMultimap(
            final Function<? super T, ? extends K> keySelector, 
            final Function<? super T, ? extends V> valueSelector, 
            final Callable<? extends Map<K, Collection<V>>> mapSupplier,
            final Function<? super K, ? extends Collection<? super V>> collectionFactory) {
        Objects.requireNonNull(keySelector, "keySelector is null");
        Objects.requireNonNull(valueSelector, "valueSelector is null");
        Objects.requireNonNull(mapSupplier, "mapSupplier is null");
        Objects.requireNonNull(collectionFactory, "collectionFactory is null");
        return collect(mapSupplier, new BiConsumer<Map<K, Collection<V>>, T>() {
            @Override
            public void accept(Map<K, Collection<V>> m, T t) throws Exception {
                K key = keySelector.apply(t);

                Collection<V> coll = m.get(key);
                if (coll == null) {
                    coll = (Collection<V>)collectionFactory.apply(key);
                    m.put(key, coll);
                }

                V value = valueSelector.apply(t);

                coll.add(value);
            }
        });
    }

    /**
     * Returns a Observable that emits a single Map, returned by a specified {@code mapFactory} function, that
     * contains an ArrayList of values, extracted by a specified {@code valueSelector} function from items
     * emitted by the source ObservableSource and keyed by the {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K> the key type of the Map
     * @param <V> the value type of the Map
     * @param keySelector
     *            the function that extracts a key from the source items to be used as the key in the Map
     * @param valueSelector
     *            the function that extracts a value from the source items to be used as the value in the Map
     * @param mapSupplier
     *            the function that returns a Map instance to be used
     * @return a Observable that emits a single item: a Map that contains a list items mapped from the source
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <K, V> Observable<Map<K, Collection<V>>> toMultimap(
            Function<? super T, ? extends K> keySelector, 
            Function<? super T, ? extends V> valueSelector,
            Callable<Map<K, Collection<V>>> mapSupplier
            ) {
        return toMultimap(keySelector, valueSelector, mapSupplier, ArrayListSupplier.<V, K>asFunction());
    }
    
    /**
     * Converts the current Observable into a Observable by applying the specified backpressure strategy.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator applies the chosen backpressure strategy of {@link BackpressureStrategy} enum.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toFlowable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param strategy the backpressure strategy to apply
     * @return the new Observable instance
     */
    public final Flowable<T> toFlowable(BackpressureStrategy strategy) {
        Flowable<T> o = new FlowableFromObservable<T>(this);
        
        switch (strategy) {
        case BUFFER:
            return o.onBackpressureBuffer();
        case DROP:
            return o.onBackpressureDrop();
        case LATEST:
            return o.onBackpressureLatest();
        default:
            return o;
        }
    }

    /**
     * Returns a Single that emits the single item emitted by the source ObservableSource, if that ObservableSource
     * emits only a single item. If the source ObservableSource emits more than one item or no items, notify of an
     * {@code IllegalArgumentException} or {@code NoSuchElementException} respectively.
     * <p>
     * <img width="640" height="295" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/Single.toSingle.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSingle} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return a Single that emits the single item emitted by the source ObservableSource
     * @throws IllegalArgumentException
     *             if the source ObservableSource emits more than one item
     * @throws NoSuchElementException
     *             if the source ObservableSource emits no items
     * @see <a href="http://reactivex.io/documentation/single.html">ReactiveX documentation: Single</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Single<T> toSingle() {
        return new SingleFromObservable<T>(this);
    }

    /**
     * Returns a Observable that emits a list that contains the items emitted by the source ObservableSource, in a
     * sorted order. Each item emitted by the ObservableSource must implement {@link Comparable} with respect to all
     * other items in the sequence.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toSortedList.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSortedList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @throws ClassCastException
     *             if any item emitted by the ObservableSource does not implement {@link Comparable} with respect to
     *             all other items emitted by the ObservableSource
     * @return a Observable that emits a list that contains the items emitted by the source ObservableSource in
     *         sorted order
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> toSortedList() {
        return toSortedList(Functions.naturalOrder());
    }

    /**
     * Returns a Observable that emits a list that contains the items emitted by the source ObservableSource, in a
     * sorted order based on a specified comparison function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toSortedList.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSortedList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param comparator
     *            a function that compares two items emitted by the source ObservableSource and returns an Integer
     *            that indicates their sort order
     * @return a Observable that emits a list that contains the items emitted by the source ObservableSource in
     *         sorted order
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> toSortedList(final Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator is null");
        return toList().map(new Function<List<T>, List<T>>() {
            @Override
            public List<T> apply(List<T> v) {
                Collections.sort(v, comparator);
                return v;
            }
        });
    }

    /**
     * Returns a Observable that emits a list that contains the items emitted by the source ObservableSource, in a
     * sorted order based on a specified comparison function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toSortedList.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSortedList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param comparator
     *            a function that compares two items emitted by the source ObservableSource and returns an Integer
     *            that indicates their sort order
     * @param capacityHint 
     *             the initial capacity of the ArrayList used to accumulate items before sorting
     * @return a Observable that emits a list that contains the items emitted by the source ObservableSource in
     *         sorted order
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> toSortedList(final Comparator<? super T> comparator, int capacityHint) {
        Objects.requireNonNull(comparator, "comparator is null");
        return toList(capacityHint).map(new Function<List<T>, List<T>>() {
            @Override
            public List<T> apply(List<T> v) {
                Collections.sort(v, comparator);
                return v;
            }
        });
    }

    /**
     * Returns a Observable that emits a list that contains the items emitted by the source ObservableSource, in a
     * sorted order. Each item emitted by the ObservableSource must implement {@link Comparable} with respect to all
     * other items in the sequence.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toSortedList.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSortedList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param capacityHint 
     *             the initial capacity of the ArrayList used to accumulate items before sorting
     * @return a Observable that emits a list that contains the items emitted by the source ObservableSource in
     *         sorted order
     * @throws ClassCastException
     *             if any item emitted by the ObservableSource does not implement {@link Comparable} with respect to
     *             all other items emitted by the ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<List<T>> toSortedList(int capacityHint) {
        return toSortedList(Functions.<T>naturalOrder(), capacityHint);
    }

    /**
     * Modifies the source ObservableSource so that subscribers will unsubscribe from it on a specified
     * {@link Scheduler}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to perform unsubscription actions on
     * @return the source ObservableSource modified so that its unsubscriptions happen on the specified
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/subscribeon.html">ReactiveX operators documentation: SubscribeOn</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<T> unsubscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return new ObservableUnsubscribeOn<T>(this, scheduler);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each containing {@code count} items. When the source
     * ObservableSource completes or encounters an error, the resulting ObservableSource emits the current window and
     * propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window3.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each window before it should be emitted
     * @return a Observable that emits connected, non-overlapping windows, each containing at most
     *         {@code count} items from the source ObservableSource
     * @throws IllegalArgumentException if either count is non-positive
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Observable<T>> window(long count) {
        return window(count, count, bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits windows every {@code skip} items, each containing no more than {@code count} items. When
     * the source ObservableSource completes or encounters an error, the resulting ObservableSource emits the current window
     * and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="365" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window4.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param skip
     *            how many items need to be skipped before starting a new window. Note that if {@code skip} and
     *            {@code count} are equal this is the same operation as {@link #window(long)}.
     * @return a Observable that emits windows every {@code skip} items containing at most {@code count} items
     *         from the source ObservableSource
     * @throws IllegalArgumentException if either count or skip is non-positive
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Observable<T>> window(long count, long skip) {
        return window(count, skip, bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits windows every {@code skip} items, each containing no more than {@code count} items. When
     * the source ObservableSource completes or encounters an error, the resulting ObservableSource emits the current window
     * and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="365" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window4.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param skip
     *            how many items need to be skipped before starting a new window. Note that if {@code skip} and
     *            {@code count} are equal this is the same operation as {@link #window(long)}.
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @return a Observable that emits windows every {@code skip} items containing at most {@code count} items
     *         from the source ObservableSource
     * @throws IllegalArgumentException if either count or skip is non-positive
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Observable<Observable<T>> window(long count, long skip, int bufferSize) {
        if (skip <= 0) {
            throw new IllegalArgumentException("skip > 0 required but it was " + skip);
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        validateBufferSize(bufferSize, "bufferSize");
        return new ObservableWindow<T>(this, count, skip, bufferSize);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource starts a new window periodically, as determined by the {@code timeshift} argument. It emits
     * each window after a fixed timespan, specified by the {@code timespan} argument. When the source
     * ObservableSource completes or ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window7.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted
     * @param timeskip
     *            the period of time after which a new window will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @return a Observable that emits new windows periodically as a fixed timespan elapses
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<Observable<T>> window(long timespan, long timeskip, TimeUnit unit) {
        return window(timespan, timeskip, unit, Schedulers.computation(), bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource starts a new window periodically, as determined by the {@code timeshift} argument. It emits
     * each window after a fixed timespan, specified by the {@code timespan} argument. When the source
     * ObservableSource completes or ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window7.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted
     * @param timeskip
     *            the period of time after which a new window will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return a Observable that emits new windows periodically as a fixed timespan elapses
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<Observable<T>> window(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler) {
        return window(timespan, timeskip, unit, scheduler, bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource starts a new window periodically, as determined by the {@code timeshift} argument. It emits
     * each window after a fixed timespan, specified by the {@code timespan} argument. When the source
     * ObservableSource completes or ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window7.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted
     * @param timeskip
     *            the period of time after which a new window will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @return a Observable that emits new windows periodically as a fixed timespan elapses
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<Observable<T>> window(long timespan, long timeskip, TimeUnit unit, Scheduler scheduler, int bufferSize) {
        validateBufferSize(bufferSize, "bufferSize");
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(unit, "unit is null");
        return new ObservableWindowTimed<T>(this, timespan, timeskip, unit, scheduler, Long.MAX_VALUE, bufferSize, false);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration specified by the
     * {@code timespan} argument. When the source ObservableSource completes or encounters an error, the resulting
     * ObservableSource emits the current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="375" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window5.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @return a Observable that emits connected, non-overlapping windows representing items emitted by the
     *         source ObservableSource during fixed, consecutive durations
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<Observable<T>> window(long timespan, TimeUnit unit) {
        return window(timespan, unit, Schedulers.computation(), Long.MAX_VALUE, false);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration as specified by the
     * {@code timespan} argument or a maximum size as specified by the {@code count} argument (whichever is
     * reached first). When the source ObservableSource completes or encounters an error, the resulting ObservableSource
     * emits the current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<Observable<T>> window(long timespan, TimeUnit unit, 
            long count) {
        return window(timespan, unit, Schedulers.computation(), count, false);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration as specified by the
     * {@code timespan} argument or a maximum size as specified by the {@code count} argument (whichever is
     * reached first). When the source ObservableSource completes or encounters an error, the resulting ObservableSource
     * emits the current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param restart
     *            if true, when a window reaches the capacity limit, the timer is restarted as well
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public final Observable<Observable<T>> window(long timespan, TimeUnit unit, 
            long count, boolean restart) {
        return window(timespan, unit, Schedulers.computation(), count, restart);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration as specified by the
     * {@code timespan} argument. When the source ObservableSource completes or encounters an error, the resulting
     * ObservableSource emits the current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="375" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window5.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return a Observable that emits connected, non-overlapping windows containing items emitted by the
     *         source ObservableSource within a fixed duration
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<Observable<T>> window(long timespan, TimeUnit unit, 
            Scheduler scheduler) {
        return window(timespan, unit, scheduler, Long.MAX_VALUE, false);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration specified by the
     * {@code timespan} argument or a maximum size specified by the {@code count} argument (whichever is reached
     * first). When the source ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<Observable<T>> window(long timespan, TimeUnit unit, 
            Scheduler scheduler, long count) {
        return window(timespan, unit, scheduler, count, false);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration specified by the
     * {@code timespan} argument or a maximum size specified by the {@code count} argument (whichever is reached
     * first). When the source ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @param restart
     *            if true, when a window reaches the capacity limit, the timer is restarted as well
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<Observable<T>> window(long timespan, TimeUnit unit, 
            Scheduler scheduler, long count, boolean restart) {
        return window(timespan, unit, scheduler, count, restart, bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows, each of a fixed duration specified by the
     * {@code timespan} argument or a maximum size specified by the {@code count} argument (whichever is reached
     * first). When the source ObservableSource completes or encounters an error, the resulting ObservableSource emits the
     * current window and propagates the notification from the source ObservableSource.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @param restart
     *            if true, when a window reaches the capacity limit, the timer is restarted as well
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Observable<Observable<T>> window(
            long timespan, TimeUnit unit, Scheduler scheduler, 
            long count, boolean restart, int bufferSize) {
        validateBufferSize(bufferSize, "bufferSize");
        Objects.requireNonNull(scheduler, "scheduler is null");
        Objects.requireNonNull(unit, "unit is null");
        if (count <= 0) {
            throw new IllegalArgumentException("count > 0 required but it was " + count);
        }
        return new ObservableWindowTimed<T>(this, timespan, timespan, unit, scheduler, count, bufferSize, restart);
    }

    /**
     * Returns a Observable that emits non-overlapping windows of items it collects from the source ObservableSource
     * where the boundary of each window is determined by the items emitted from a specified boundary-governing
     * ObservableSource.
     * <p>
     * <img width="640" height="475" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window8.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B>
     *            the window element type (ignored)
     * @param boundary
     *            a ObservableSource whose emitted items close and open windows
     * @return a Observable that emits non-overlapping windows of items it collects from the source ObservableSource
     *         where the boundary of each window is determined by the items emitted from the {@code boundary}
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<Observable<T>> window(ObservableSource<B> boundary) {
        return window(boundary, bufferSize());
    }

    /**
     * Returns a Observable that emits non-overlapping windows of items it collects from the source ObservableSource
     * where the boundary of each window is determined by the items emitted from a specified boundary-governing
     * ObservableSource.
     * <p>
     * <img width="640" height="475" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window8.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B>
     *            the window element type (ignored)
     * @param boundary
     *            a ObservableSource whose emitted items close and open windows
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @return a Observable that emits non-overlapping windows of items it collects from the source ObservableSource
     *         where the boundary of each window is determined by the items emitted from the {@code boundary}
     *         ObservableSource
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<Observable<T>> window(ObservableSource<B> boundary, int bufferSize) {
        Objects.requireNonNull(boundary, "boundary is null");
        return new ObservableWindowBoundary<T, B>(this, boundary, bufferSize);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits windows that contain those items emitted by the source ObservableSource between the time when
     * the {@code windowOpenings} ObservableSource emits an item and when the ObservableSource returned by
     * {@code closingSelector} emits an item.
     * <p>
     * <img width="640" height="550" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window2.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the element type of the window-opening ObservableSource
     * @param <V> the element type of the window-closing ObservableSources
     * @param windowOpen
     *            a ObservableSource that, when it emits an item, causes another window to be created
     * @param windowClose
     *            a {@link Function} that produces a ObservableSource for every window created. When this ObservableSource
     *            emits an item, the associated window is closed and emitted
     * @return a Observable that emits windows of items emitted by the source ObservableSource that are governed by
     *         the specified window-governing ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, V> Observable<Observable<T>> window(
            ObservableSource<U> windowOpen,
            Function<? super U, ? extends ObservableSource<V>> windowClose) {
        return window(windowOpen, windowClose, bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits windows that contain those items emitted by the source ObservableSource between the time when
     * the {@code windowOpenings} ObservableSource emits an item and when the ObservableSource returned by
     * {@code closingSelector} emits an item.
     * <p>
     * <img width="640" height="550" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window2.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the element type of the window-opening ObservableSource
     * @param <V> the element type of the window-closing ObservableSources
     * @param windowOpen
     *            a ObservableSource that, when it emits an item, causes another window to be created
     * @param windowClose
     *            a {@link Function} that produces a ObservableSource for every window created. When this ObservableSource
     *            emits an item, the associated window is closed and emitted
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @return a Observable that emits windows of items emitted by the source ObservableSource that are governed by
     *         the specified window-governing ObservableSources
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, V> Observable<Observable<T>> window(
            ObservableSource<U> windowOpen,
            Function<? super U, ? extends ObservableSource<V>> windowClose, int bufferSize) {
        Objects.requireNonNull(windowOpen, "windowOpen is null");
        Objects.requireNonNull(windowClose, "windowClose is null");
        return new ObservableWindowBoundarySelector<T, U, V>(this, windowOpen, windowClose, bufferSize);
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows. It emits the current window and opens a new one
     * whenever the ObservableSource produced by the specified {@code closingSelector} emits an item.
     * <p>
     * <img width="640" height="460" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window1.png" alt="">
     * <dl>
     *  if left unconsumed.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B> the element type of the boundary ObservableSource
     * @param boundary
     *            a {@link Callable} that returns an {@code ObservableSource} that governs the boundary between windows.
     *            When the source {@code ObservableSource} emits an item, {@code window} emits the current window and begins
     *            a new one.
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         whenever {@code closingSelector} emits an item
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<Observable<T>> window(Callable<? extends ObservableSource<B>> boundary) {
        return window(boundary, bufferSize());
    }

    /**
     * Returns a Observable that emits windows of items it collects from the source ObservableSource. The resulting
     * ObservableSource emits connected, non-overlapping windows. It emits the current window and opens a new one
     * whenever the ObservableSource produced by the specified {@code closingSelector} emits an item.
     * <p>
     * <img width="640" height="460" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window1.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B> the element type of the boundary ObservableSource
     * @param boundary
     *            a {@link Callable} that returns an {@code ObservableSource} that governs the boundary between windows.
     *            When the source {@code ObservableSource} emits an item, {@code window} emits the current window and begins
     *            a new one.
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @return a Observable that emits connected, non-overlapping windows of items from the source ObservableSource
     *         whenever {@code closingSelector} emits an item
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <B> Observable<Observable<T>> window(Callable<? extends ObservableSource<B>> boundary, int bufferSize) {
        Objects.requireNonNull(boundary, "boundary is null");
        return new ObservableWindowBoundarySupplier<T, B>(this, boundary, bufferSize);
    }

    /**
     * Merges the specified ObservableSource into this ObservableSource sequence by using the {@code resultSelector}
     * function only when the source ObservableSource (this instance) emits an item.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/withLatestFrom.png" alt="">
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator, by default, doesn't run any particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U> the element type of the other ObservableSource
     * @param <R> the result type of the combination
     * @param other
     *            the other ObservableSource
     * @param combiner
     *            the function to call when this ObservableSource emits an item and the other ObservableSource has already
     *            emitted an item, to generate the item to be emitted by the resulting ObservableSource
     * @return a Observable that merges the specified ObservableSource into this ObservableSource by using the
     *         {@code resultSelector} function only when the source ObservableSource sequence (this instance) emits an
     *         item
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> withLatestFrom(ObservableSource<? extends U> other, BiFunction<? super T, ? super U, ? extends R> combiner) {
        Objects.requireNonNull(other, "other is null");
        Objects.requireNonNull(combiner, "combiner is null");

        return new ObservableWithLatestFrom<T, U, R>(this, combiner, other);
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, R> Observable<R> withLatestFrom(ObservableSource<T1> o1, ObservableSource<T2> o2, 
            Function3<? super T, ? super T1, ? super T2, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <T3> the third other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param o3 the third other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, T3, R> Observable<R> withLatestFrom(
            ObservableSource<T1> o1, ObservableSource<T2> o2, 
            ObservableSource<T3> o3, 
            Function4<? super T, ? super T1, ? super T2, ? super T3, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <T3> the third other source's value type
     * @param <T4> the fourth other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param o3 the third other ObservableSource
     * @param o4 the fourth other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, T3, T4, R> Observable<R> withLatestFrom(
            ObservableSource<T1> o1, ObservableSource<T2> o2, 
            ObservableSource<T3> o3, ObservableSource<T4> o4, 
            Function5<? super T, ? super T1, ? super T2, ? super T3, ? super T4, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }
    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <T3> the third other source's value type
     * @param <T4> the fourth other source's value type
     * @param <T5> the fifth other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param o3 the third other ObservableSource
     * @param o4 the fourth other ObservableSource
     * @param o5 the fifth other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, T3, T4, T5, R> Observable<R> withLatestFrom(
            ObservableSource<T1> o1, ObservableSource<T2> o2, 
            ObservableSource<T1> o3, ObservableSource<T2> o4, 
            ObservableSource<T1> o5, 
            Function6<? super T, ? super T1, ? super T2, ? super T3, ? super T4, ? super T5, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <T3> the third other source's value type
     * @param <T4> the fourth other source's value type
     * @param <T5> the fifth other source's value type
     * @param <T6> the sixth other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param o3 the third other ObservableSource
     * @param o4 the fourth other ObservableSource
     * @param o5 the fifth other ObservableSource
     * @param o6 the sixth other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, T3, T4, T5, T6, R> Observable<R> withLatestFrom(
            ObservableSource<T1> o1, ObservableSource<T2> o2, 
            ObservableSource<T1> o3, ObservableSource<T2> o4, 
            ObservableSource<T1> o5, ObservableSource<T2> o6, 
            Function7<? super T, ? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <T3> the third other source's value type
     * @param <T4> the fourth other source's value type
     * @param <T5> the fifth other source's value type
     * @param <T6> the sixth other source's value type
     * @param <T7> the seventh other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param o3 the third other ObservableSource
     * @param o4 the fourth other ObservableSource
     * @param o5 the fifth other ObservableSource
     * @param o6 the sixth other ObservableSource
     * @param o7 the seventh other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, T3, T4, T5, T6, T7, R> Observable<R> withLatestFrom(
            ObservableSource<T1> o1, ObservableSource<T2> o2, 
            ObservableSource<T1> o3, ObservableSource<T2> o4, 
            ObservableSource<T1> o5, ObservableSource<T2> o6, 
            ObservableSource<T1> o7,
            Function8<? super T, ? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T1> the first other source's value type
     * @param <T2> the second other source's value type
     * @param <T3> the third other source's value type
     * @param <T4> the fourth other source's value type
     * @param <T5> the fifth other source's value type
     * @param <T6> the sixth other source's value type
     * @param <T7> the seventh other source's value type
     * @param <T8> the eighth other source's value type
     * @param <R> the result value type
     * @param o1 the first other ObservableSource
     * @param o2 the second other ObservableSource
     * @param o3 the third other ObservableSource
     * @param o4 the fourth other ObservableSource
     * @param o5 the fifth other ObservableSource
     * @param o6 the sixth other ObservableSource
     * @param o7 the seventh other ObservableSource
     * @param o8 the eighth other ObservableSource
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <T1, T2, T3, T4, T5, T6, T7, T8, R> Observable<R> withLatestFrom(
            ObservableSource<T1> o1, ObservableSource<T2> o2, 
            ObservableSource<T1> o3, ObservableSource<T2> o4, 
            ObservableSource<T1> o5, ObservableSource<T2> o6, 
            ObservableSource<T1> o7, ObservableSource<T2> o8, 
            Function9<? super T, ? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the result value type
     * @param others the array of other sources
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <R> Observable<R> withLatestFrom(ObservableSource<?>[] others, Function<Object[], R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Combines the value emission from this ObservableSource with the latest emissions from the
     * other ObservableSources via a function to produce the output item.
     * 
     * <p>Note that this operator doesn't emit anything until all other sources have produced at
     * least one value. The resulting emission only happens when this ObservableSource emits (and
     * not when any of the other sources emit, unlike combineLatest). 
     * If a source doesn't produce any value and just completes, the sequence is completed immediately.
     * 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This operator does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R> the result value type
     * @param others the iterable of other sources
     * @param combiner the function called with an array of values from each participating ObservableSource
     * @return the new ObservableSource instance
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final <R> Observable<R> withLatestFrom(Iterable<ObservableSource<?>> others, Function<Object[], R> combiner) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a Observable that emits items that are the result of applying a specified function to pairs of
     * values, one each from the source ObservableSource and a specified Iterable sequence.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.i.png" alt="">
     * <p>
     * Note that the {@code other} Iterable is evaluated as items are observed from the source ObservableSource; it is
     * not pre-consumed. This allows you to zip infinite streams on either side.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zipWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items in the {@code other} Iterable
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param other
     *            the Iterable sequence
     * @param zipper
     *            a function that combines the pairs of items from the ObservableSource and the Iterable to generate
     *            the items to be emitted by the resulting ObservableSource
     * @return a Observable that pairs up values from the source ObservableSource and the {@code other} Iterable
     *         sequence and emits the results of {@code zipFunction} applied to these pairs
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> zipWith(Iterable<U> other,  BiFunction<? super T, ? super U, ? extends R> zipper) {
        Objects.requireNonNull(other, "other is null");
        Objects.requireNonNull(zipper, "zipper is null");
        return new ObservableZipIterable<T, U, R>(this, other, zipper);
    }

    /**
     * Returns a Observable that emits items that are the result of applying a specified function to pairs of
     * values, one each from the source ObservableSource and another specified ObservableSource.
     * <p>
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>range(1, 5).doOnCompleted(action1).zipWith(range(6, 5).doOnCompleted(action2), (a, b) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * 
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zipWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the {@code other} ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param other
     *            the other ObservableSource
     * @param zipper
     *            a function that combines the pairs of items from the two ObservableSources to generate the items to
     *            be emitted by the resulting ObservableSource
     * @return a Observable that pairs up values from the source ObservableSource and the {@code other} ObservableSource
     *         and emits the results of {@code zipFunction} applied to these pairs
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> zipWith(ObservableSource<? extends U> other, 
            BiFunction<? super T, ? super U, ? extends R> zipper) {
        Objects.requireNonNull(other, "other is null");
        return zip(this, other, zipper);
    }

    /**
     * Returns a Observable that emits items that are the result of applying a specified function to pairs of
     * values, one each from the source ObservableSource and another specified ObservableSource.
     * <p>
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>range(1, 5).doOnCompleted(action1).zipWith(range(6, 5).doOnCompleted(action2), (a, b) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * 
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zipWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the {@code other} ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param other
     *            the other ObservableSource
     * @param zipper
     *            a function that combines the pairs of items from the two ObservableSources to generate the items to
     *            be emitted by the resulting ObservableSource
     * @param delayError
     *            if true, errors from the current Observable or the other ObservableSource is delayed until both terminate
     * @return a Observable that pairs up values from the source ObservableSource and the {@code other} ObservableSource
     *         and emits the results of {@code zipFunction} applied to these pairs
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> zipWith(ObservableSource<? extends U> other, 
            BiFunction<? super T, ? super U, ? extends R> zipper, boolean delayError) {
        return zip(this, other, zipper, delayError);
    }

    /**
     * Returns a Observable that emits items that are the result of applying a specified function to pairs of
     * values, one each from the source ObservableSource and another specified ObservableSource.
     * <p>
     * <p>
     * The operator subscribes to its sources in order they are specified and completes eagerly if 
     * one of the sources is shorter than the rest while unsubscribing the other sources. Therefore, it 
     * is possible those other sources will never be able to run to completion (and thus not calling 
     * {@code doOnCompleted()}). This can also happen if the sources are exactly the same length; if
     * source A completes and B has been consumed and is about to complete, the operator detects A won't
     * be sending further values and it will unsubscribe B immediately. For example:
     * <pre><code>range(1, 5).doOnCompleted(action1).zipWith(range(6, 5).doOnCompleted(action2), (a, b) -&gt; a + b)</code></pre>
     * {@code action1} will be called but {@code action2} won't.
     * <br>To work around this termination property,
     * use {@code doOnUnsubscribed()} as well or use {@code using()} to do cleanup in case of completion 
     * or unsubscription.
     * 
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zipWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the {@code other} ObservableSource
     * @param <R>
     *            the type of items emitted by the resulting ObservableSource
     * @param other
     *            the other ObservableSource
     * @param zipper
     *            a function that combines the pairs of items from the two ObservableSources to generate the items to
     *            be emitted by the resulting ObservableSource
     * @param bufferSize
     *            the capacity hint for the buffer in the inner windows
     * @param delayError
     *            if true, errors from the current Observable or the other ObservableSource is delayed until both terminate
     * @return a Observable that pairs up values from the source ObservableSource and the {@code other} ObservableSource
     *         and emits the results of {@code zipFunction} applied to these pairs
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <U, R> Observable<R> zipWith(ObservableSource<? extends U> other, 
            BiFunction<? super T, ? super U, ? extends R> zipper, boolean delayError, int bufferSize) {
        return zip(this, other, zipper, delayError, bufferSize);
    }
    
    // -------------------------------------------------------------------------
    // Fluent test support, super handy and reduces test preparation boilerplate
    // -------------------------------------------------------------------------
    /**
     * Creates a TestObserver and subscribes
     * it to this Observable.
     * @return the new TestObserver instance
     */
    public final TestObserver<T> test() { // NoPMD
        TestObserver<T> ts = new TestObserver<T>();
        subscribe(ts);
        return ts;
    }
    
    /**
     * Creates a TestObserver with the given fusion mode
     * and optionally in cancelled state, then subscribes it to this Observable.
     * @param fusionMode the requested fusion mode, see {@link QueueDisposable} constants.
     * @param cancelled if true, the TestSubscriber will be cancelled before subscribing to this
     * Observable.
     * @return the new TestObserver instance
     */
    public final TestObserver<T> test(int fusionMode, boolean cancelled) { // NoPMD
        TestObserver<T> ts = new TestObserver<T>();
        // TODO implement ts.setInitialFusionMode(fusionMode);
        if (cancelled) {
            ts.dispose();
        }
        subscribe(ts);
        return ts;
    }
}