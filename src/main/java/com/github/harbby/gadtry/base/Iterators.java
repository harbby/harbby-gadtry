/*
 * Copyright (C) 2018 The GadTry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.harbby.gadtry.base;

import com.github.harbby.gadtry.collection.ImmutableList;
import com.github.harbby.gadtry.collection.IteratorPlus;
import com.github.harbby.gadtry.collection.iterator.LengthIterator;
import com.github.harbby.gadtry.collection.iterator.MarkIterator;
import com.github.harbby.gadtry.collection.iterator.PeekIterator;
import com.github.harbby.gadtry.collection.tuple.Tuple2;
import com.github.harbby.gadtry.function.FilterFunction;
import com.github.harbby.gadtry.function.Reducer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.github.harbby.gadtry.base.MoreObjects.checkArgument;
import static com.github.harbby.gadtry.base.MoreObjects.checkState;
import static java.util.Objects.requireNonNull;

/**
 * java Iterator++
 */
public class Iterators
{
    private Iterators() {}

    private static final IteratorPlus<?> EMPTY_ITERATOR = new IteratorPlus<Object>()
    {
        @Override
        public boolean hasNext()
        {
            return false;
        }

        @Override
        public Object next()
        {
            throw new NoSuchElementException();
        }
    };

    public static <E> IteratorPlus<E> of(E value)
    {
        return new SingleIterator<>(value);
    }

    private static class SingleIterator<V>
            implements PeekIterator<V>, MarkIterator<V>, LengthIterator<V>
    {
        private boolean hasNext = true;
        private final V value;
        private boolean mark = hasNext;

        private SingleIterator(V value)
        {
            this.value = value;
        }

        @Override
        public int length()
        {
            return 1;
        }

        @Override
        public void mark()
        {
            this.mark = hasNext;
        }

        @Override
        public void reset()
        {
            this.hasNext = mark;
        }

        @Override
        public boolean hasNext()
        {
            return hasNext;
        }

        @Override
        public V next()
        {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            hasNext = false;
            return value;
        }

        @Override
        public V peek()
        {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            return value;
        }

        @Override
        public long size()
        {
            return hasNext ? 1 : 0;
        }
    }

    @SafeVarargs
    public static <E> PeekIterator<E> of(E... values)
    {
        return of(values, 0, values.length);
    }

    public static <E> PeekIterator<E> of(final E[] values, final int offset, final int length)
    {
        requireNonNull(values, "values is null");
        checkArgument(offset >= 0, "offset >= 0");
        checkArgument(length >= 0, "length >= 0");
        checkArgument(offset + length <= values.length, "offset + length <= values.length");
        return new ImmutableArrayIterator<>(values, offset, length);
    }

    private static class ImmutableArrayIterator<V>
            implements PeekIterator<V>, MarkIterator<V>, LengthIterator<V>
    {
        private final int length;
        private final V[] values;
        private final int endIndex;

        private int index;
        private int mark;

        private ImmutableArrayIterator(V[] values, int offset, int length)
        {
            this.length = length;
            this.values = values;
            this.endIndex = offset + length;

            this.index = offset;
            this.mark = offset;
        }

        @Override
        public int length()
        {
            return length;
        }

        @Override
        public boolean hasNext()
        {
            return index < endIndex;
        }

        @Override
        public V next()
        {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return values[index++];
        }

        @Override
        public V peek()
        {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return values[index];
        }

        @Override
        public void mark()
        {
            this.mark = index;
        }

        @Override
        public void reset()
        {
            this.index = this.mark;
        }

        @Override
        public long size()
        {
            return endIndex - index;
        }
    }

    @SafeVarargs
    public static <E> IteratorPlus<E> wrap(E... values)
    {
        return of(values);
    }

    public static <E> WrapListIterator<E> wrapList(List<E> list)
    {
        return new WrapListIterator<>(list);
    }

    private static class WrapListIterator<V>
            implements PeekIterator<V>
    {
        private final List<V> list;
        private int i = 0;

        private WrapListIterator(List<V> list)
        {
            this.list = list;
        }

        @Override
        public boolean hasNext()
        {
            return i < list.size();
        }

        @Override
        public V next()
        {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return list.get(i++);
        }

        @Override
        public V peek()
        {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return list.get(i);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E> IteratorPlus<E> empty()
    {
        return (IteratorPlus<E>) EMPTY_ITERATOR;
    }

    public static <E> Iterable<E> emptyIterable()
    {
        return Iterators::empty;
    }

    public static boolean isEmpty(Iterable<?> iterable)
    {
        requireNonNull(iterable, "iterable is null");
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).isEmpty();
        }
        return isEmpty(iterable.iterator());
    }

    public static boolean isEmpty(Iterator<?> iterator)
    {
        requireNonNull(iterator, "iterator is null");
        return !iterator.hasNext();
    }

    public static <T> Stream<T> toStream(Iterable<T> iterable)
    {
        requireNonNull(iterable, "iterable is null");
        if (iterable instanceof Collection) {
            return ((Collection<T>) iterable).stream();
        }
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static <T> Stream<T> toStream(Iterator<T> iterator)
    {
        requireNonNull(iterator, "iterator is null");
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    public static <T> T getFirst(Iterator<T> iterator, int index, T defaultValue)
    {
        return getFirst(iterator, index, (Supplier<T>) () -> defaultValue);
    }

    public static <T> T getFirst(Iterator<T> iterator, int index, Supplier<T> defaultValue)
    {
        requireNonNull(iterator, "iterator is null");
        requireNonNull(defaultValue);
        checkState(index >= 0, "must index >= 0");
        T value;
        int number = 0;
        while (iterator.hasNext()) {
            value = iterator.next();
            if (number++ == index) {
                return value;
            }
        }
        return defaultValue.get();
    }

    public static <T> T getFirst(Iterator<T> iterator, int index)
    {
        return getFirst(iterator, index, (Supplier<T>) () -> {
            throw new NoSuchElementException();
        });
    }

    public static <T> T getLast(Iterator<T> iterator)
    {
        requireNonNull(iterator, "iterator is null");
        T value = iterator.next();
        while (iterator.hasNext()) {
            value = iterator.next();
        }
        return value;
    }

    public static <T> T getLast(Iterator<T> iterator, T defaultValue)
    {
        requireNonNull(iterator, "iterator is null");
        if (!iterator.hasNext()) {
            return defaultValue;
        }
        return getLast(iterator);
    }

    public static <T> T getLast(Iterable<T> iterable)
    {
        requireNonNull(iterable, "iterable is null");
        if (iterable instanceof List) {
            List<T> list = (List<T>) iterable;
            if (list.isEmpty()) {
                throw new NoSuchElementException();
            }
            return list.get(list.size() - 1);
        }
        else {
            return getLast(iterable.iterator());
        }
    }

    public static <T> T getLast(Iterable<T> iterable, T defaultValue)
    {
        requireNonNull(iterable, "iterable is null");
        if (iterable instanceof List) {
            List<T> list = (List<T>) iterable;
            if (list.isEmpty()) {
                return defaultValue;
            }
            return list.get(list.size() - 1);
        }
        else {
            return getLast(iterable.iterator(), defaultValue);
        }
    }

    public static long size(Iterator<?> iterator)
    {
        requireNonNull(iterator, "iterator is null");
        long i;
        for (i = 0; iterator.hasNext(); i++) {
            iterator.next();
        }
        return i;
    }

    public static <F1, F2> Iterable<F2> map(Iterable<F1> iterable, Function<F1, F2> function)
    {
        requireNonNull(iterable, "iterable is null");
        requireNonNull(function, "function is null");
        return () -> map(iterable.iterator(), function);
    }

    public static <F1, F2> IteratorPlus<F2> map(Iterator<F1> iterator, Function<F1, F2> function)
    {
        requireNonNull(iterator, "iterator is null");
        requireNonNull(function, "function is null");
        return new IteratorPlus<F2>()
        {
            @Override
            public boolean hasNext()
            {
                return iterator.hasNext();
            }

            @Override
            public F2 next()
            {
                return function.apply(iterator.next());
            }

            @Override
            public void remove()
            {
                iterator.remove();
            }
        };
    }

    public static <T> Optional<T> reduce(Iterator<T> iterator, BinaryOperator<T> reducer)
    {
        requireNonNull(iterator, "iterator is null");
        requireNonNull(reducer, "reducer is null");
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        T lastValue = iterator.next();
        while (iterator.hasNext()) {
            lastValue = reducer.apply(lastValue, iterator.next());
        }
        return Optional.ofNullable(lastValue);
    }

    public static <T> IteratorPlus<T> limit(Iterator<T> iterator, int limit)
    {
        requireNonNull(iterator, "iterator is null");
        checkArgument(limit >= 0, "limit must >= 0");
        return new IteratorPlus<T>()
        {
            private int number;

            @Override
            public boolean hasNext()
            {
                return number < limit && iterator.hasNext();
            }

            @Override
            public T next()
            {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                number++;
                return iterator.next();
            }
        };
    }

    public static <T> void foreach(Iterator<T> iterator, Consumer<T> function)
    {
        requireNonNull(iterator, "iterator is null");
        iterator.forEachRemaining(function);
    }

    public static <E1, E2> IteratorPlus<E2> flatMap(Iterator<E1> iterator, Function<E1, Iterator<E2>> flatMap)
    {
        requireNonNull(iterator, "iterator is null");
        requireNonNull(flatMap, "flatMap is null");
        return new IteratorPlus<E2>()
        {
            private Iterator<E2> child = empty();

            @Override
            public boolean hasNext()
            {
                if (child.hasNext()) {
                    return true;
                }
                while (iterator.hasNext()) {
                    this.child = requireNonNull(flatMap.apply(iterator.next()), "user flatMap not return null");
                    if (child.hasNext()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public E2 next()
            {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return child.next();
            }
        };
    }

    public static <E> IteratorPlus<E> concat(Iterator<? extends Iterator<E>> iterators)
    {
        requireNonNull(iterators, "iterators is null");
        if (!iterators.hasNext()) {
            return empty();
        }
        return new IteratorPlus<E>()
        {
            private Iterator<E> child;

            @Override
            public boolean hasNext()
            {
                if (child != null && child.hasNext()) {
                    return true;
                }
                while (iterators.hasNext()) {
                    this.child = requireNonNull(iterators.next(), "user flatMap not return null");
                    if (child.hasNext()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public E next()
            {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return child.next();
            }
        };
    }

    @SafeVarargs
    public static <E> IteratorPlus<E> concat(Iterator<E>... iterators)
    {
        requireNonNull(iterators, "iterators is null");
        return concat(Iterators.of(iterators));
    }

    public static <E> IteratorPlus<E> filter(Iterator<E> iterator, Function<E, Boolean> filter)
    {
        return new FilterIterator<>(iterator, filter);
    }

    private static class FilterIterator<V>
            implements IteratorPlus<V>, PeekIterator<V>
    {
        private final Iterator<V> iterator;
        private final Function<V, Boolean> filter;
        private boolean hasNextValue;
        private V value;

        private FilterIterator(Iterator<V> iterator, Function<V, Boolean> filter)
        {
            this.iterator = requireNonNull(iterator, "iterator is null");
            this.filter = requireNonNull(filter, "filter is null");
            this.hasNextValue = false;
        }

        private boolean tryNext()
        {
            while (iterator.hasNext()) {
                V v = iterator.next();
                if (filter.apply(v)) {
                    this.value = v;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean hasNext()
        {
            if (hasNextValue) {
                return true;
            }
            else {
                boolean hasNext = tryNext();
                this.hasNextValue = hasNext;
                return hasNext;
            }
        }

        @Override
        public V next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            V v = this.value;
            this.hasNextValue = tryNext();
            return v;
        }

        @Override
        public V peek()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return this.value;
        }
    }

    /**
     * double fraction = setp / max
     *
     * @param iterator 待抽样的Iterator
     * @param setp     setp
     * @param max      max
     * @param seed     随机因子
     * @param <E>      type
     * @return 抽样后的Iterator
     */
    public static <E> IteratorPlus<E> sample(Iterator<E> iterator, int setp, int max, long seed)
    {
        return sample(iterator, setp, max, new Random(seed));
    }

    public static <E> IteratorPlus<E> sample(Iterator<E> iterator, int setp, int max, Random random)
    {
        return new SampleIterator<>(iterator, random, max, setp);
    }

    private static final class SampleIterator<V>
            implements IteratorPlus<V>
    {
        private final Iterator<V> iterator;
        private final Random random;
        private final int max;
        private final int setp;
        private V value;
        private boolean hasNextValue;

        private SampleIterator(Iterator<V> iterator, Random random, int max, int setp)
        {
            this.iterator = requireNonNull(iterator, "iterators is null");
            this.random = requireNonNull(random, "random is null");
            this.max = max;
            this.setp = setp;

            this.hasNextValue = false;
        }

        private boolean tryNext()
        {
            while (iterator.hasNext()) {
                V e = iterator.next();
                if (random.nextInt(max) < setp) {
                    this.value = e;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean hasNext()
        {
            if (hasNextValue) {
                return true;
            }
            else {
                boolean hasNext = this.tryNext();
                this.hasNextValue = hasNext;
                return hasNext;
            }
        }

        @Override
        public V next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            V v = this.value;
            this.hasNextValue = this.tryNext();
            return v;
        }
    }

    public static <E> IteratorPlus<Tuple2<E, Long>> zipIndex(Iterator<E> iterator, long startIndex)
    {
        requireNonNull(iterator, "input Iterator is null");
        return new IteratorPlus<Tuple2<E, Long>>()
        {
            private long i = startIndex;

            @Override
            public boolean hasNext()
            {
                return iterator.hasNext();
            }

            @Override
            public Tuple2<E, Long> next()
            {
                return Tuple2.of(iterator.next(), i++);
            }
        };
    }

    public static <T> Iterator<T> mergeSorted(Comparator<T> comparator, List<Iterator<T>> inputs)
    {
        requireNonNull(comparator, "comparator is null");
        requireNonNull(inputs, "inputs is null");
        if (inputs.size() == 0) {
            return Iterators.empty();
        }
        if (inputs.size() == 1) {
            return inputs.get(0);
        }
        final PriorityQueue<Tuple2<T, Iterator<T>>> priorityQueue = new PriorityQueue<>(inputs.size(), (o1, o2) -> comparator.compare(o1.f1(), o2.f1()));
        for (Iterator<T> iterator : inputs) {
            if (iterator.hasNext()) {
                priorityQueue.add(Tuple2.of(iterator.next(), iterator));
            }
        }

        return new IteratorPlus<T>()
        {
            @Override
            public boolean hasNext()
            {
                return !priorityQueue.isEmpty();
            }

            @Override
            public T next()
            {
                Tuple2<T, Iterator<T>> node = priorityQueue.poll();
                if (node == null) {
                    throw new NoSuchElementException();
                }
                T value = node.f1();
                if (node.f2().hasNext()) {
                    node.setF1(node.f2().next());
                    priorityQueue.add(node);
                }
                return value;
            }
        };
    }

    @SafeVarargs
    public static <T> Iterator<T> mergeSorted(Comparator<T> comparator, Iterator<T>... inputs)
    {
        return mergeSorted(comparator, ImmutableList.copy(inputs));
    }

    public static <K, V> IteratorPlus<Tuple2<K, V>> reduceSorted(Iterator<Tuple2<K, V>> input, Reducer<V> reducer)
    {
        requireNonNull(reducer, "reducer is null");
        requireNonNull(input, "input iterator is null");
        if (!input.hasNext()) {
            return Iterators.empty();
        }
        return new IteratorPlus<Tuple2<K, V>>()
        {
            private Tuple2<K, V> nextRow;

            @Override
            public boolean hasNext()
            {
                return nextRow != null || input.hasNext();
            }

            @Override
            public Tuple2<K, V> next()
            {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                if (nextRow == null) {
                    nextRow = input.next();
                }
                while (input.hasNext()) {
                    Tuple2<K, V> it = input.next();
                    if (!Objects.equals(it.key(), nextRow.key())) {
                        Tuple2<K, V> rs = nextRow;
                        this.nextRow = it;
                        return rs;
                    }
                    else {
                        nextRow.setValue(reducer.reduce(nextRow.value(), it.value()));
                    }
                }
                Tuple2<K, V> result = nextRow;
                nextRow = null;
                return result;
            }
        };
    }

    /**
     * We only have a partial ordering, e.g. comparing the keys by hash code, which means that
     * multiple distinct keys might be treated as equal by the ordering. To deal with this, we
     * need to read all keys considered equal by the ordering at once and compare them.
     */
    public static <K, V> IteratorPlus<Tuple2<K, V>> reduceHashSorted(Iterator<Tuple2<K, V>> input, Reducer<V> reducer, Comparator<K> comparator)
    {
        requireNonNull(reducer, "reducer is null");
        requireNonNull(input, "input iterator is null");
        if (!input.hasNext()) {
            return Iterators.empty();
        }
        Iterator<Iterator<Tuple2<K, V>>> it = new IteratorPlus<Iterator<Tuple2<K, V>>>()
        {
            private final List<Tuple2<K, V>> lastRows = new ArrayList<>();
            private final WrapListIterator<Tuple2<K, V>> listIterator = wrapList(lastRows);
            private Tuple2<K, V> nextRow;

            @Override
            public boolean hasNext()
            {
                return nextRow != null || input.hasNext();
            }

            private void tryMerge(K k1, Tuple2<K, V> tp)
            {
                for (Tuple2<K, V> it : lastRows) {
                    if (Objects.equals(it.key(), k1)) {
                        it.setValue(reducer.reduce(it.value(), tp.value()));
                        return;
                    }
                }
                lastRows.add(tp);
            }

            @Override
            public Iterator<Tuple2<K, V>> next()
            {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                if (nextRow == null) {
                    nextRow = input.next();
                    lastRows.add(nextRow);
                }
                else {
                    listIterator.i = 0;
                    lastRows.clear();
                    lastRows.add(nextRow);
                }
                K k2 = nextRow.key();
                while (input.hasNext()) {
                    Tuple2<K, V> tp = input.next();
                    K k1 = tp.key();
                    if (comparator.compare(k1, k2) == 0) {
                        tryMerge(k1, tp);
                    }
                    else {
                        this.nextRow = tp;
                        return listIterator;
                    }
                }
                nextRow = null;
                return listIterator;
            }
        };
        return concat(it);
    }

    private static class MergeJoinIterator<K, V1, V2>
            implements IteratorPlus<Tuple2<K, Tuple2<V1, V2>>>
    {
        private final Comparator<K> comparator;
        private final Iterator<Tuple2<K, V1>> leftIterator;
        private final Iterator<Tuple2<K, V2>> rightIterator;

        private final List<Tuple2<K, V1>> leftSameKeys = new ArrayList<>();
        private Tuple2<K, V1> leftNode;
        private Tuple2<K, V2> rightNode;
        private int index;

        private MergeJoinIterator(Comparator<K> comparator, Iterator<Tuple2<K, V1>> leftIterator, Iterator<Tuple2<K, V2>> rightIterator)
        {
            this.comparator = comparator;
            this.leftIterator = leftIterator;
            this.rightIterator = rightIterator;

            leftNode = leftIterator.next();
        }

        @Override
        public boolean hasNext()
        {
            if (index < leftSameKeys.size()) {
                return true;
            }
            if (!rightIterator.hasNext()) {
                return false;
            }
            this.rightNode = rightIterator.next();

            if (!leftSameKeys.isEmpty() && Objects.equals(leftSameKeys.get(0).f1(), rightNode.f1())) {
                index = 0;
                return true;
            }
            while (true) {
                int than = comparator.compare(leftNode.f1(), rightNode.f1());
                if (than == 0) {
                    leftSameKeys.clear();
                    do {
                        leftSameKeys.add(leftNode);
                        if (leftIterator.hasNext()) {
                            leftNode = leftIterator.next();
                        }
                        else {
                            break;
                        }
                    }
                    while (Objects.equals(leftNode.f1(), rightNode.f1()));
                    index = 0;
                    return true;
                }
                else if (than > 0) {
                    if (!rightIterator.hasNext()) {
                        return false;
                    }
                    this.rightNode = rightIterator.next();
                }
                else {
                    if (!leftIterator.hasNext()) {
                        return false;
                    }
                    this.leftNode = leftIterator.next();
                }
            }
        }

        @Override
        public Tuple2<K, Tuple2<V1, V2>> next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Tuple2<K, V1> x = leftSameKeys.get(index++);
            return Tuple2.of(x.f1(), Tuple2.of(x.f2(), rightNode.f2()));
        }
    }

    public static <K, V1, V2> IteratorPlus<Tuple2<K, Tuple2<V1, V2>>> mergeJoin(Comparator<K> comparator, Iterator<Tuple2<K, V1>> leftIterator, Iterator<Tuple2<K, V2>> rightIterator)
    {
        requireNonNull(comparator, "comparator is null");
        requireNonNull(leftIterator, "leftIterator is null");
        requireNonNull(rightIterator, "rightIterator is null");
        if (!leftIterator.hasNext() || !rightIterator.hasNext()) {
            return Iterators.empty();
        }
        return new MergeJoinIterator<>(comparator, leftIterator, rightIterator);
    }

    public static <V> IteratorPlus<V> autoClose(Iterator<V> iterator, Runnable autoClose)
    {
        return new AutoCloseIterator<>(iterator, autoClose);
    }

    private static class AutoCloseIterator<V>
            implements IteratorPlus<V>
    {
        private final Iterator<V> iterator;
        private final Runnable autoClose;
        private boolean hasNextValue;

        private AutoCloseIterator(Iterator<V> iterator, Runnable autoClose)
        {
            this.iterator = requireNonNull(iterator, "iterator is null");
            this.autoClose = requireNonNull(autoClose, "autoClose is null");
            this.hasNextValue = false;
        }

        @Override
        public boolean hasNext()
        {
            return hasNextValue || iterator.hasNext();
        }

        @Override
        public V next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            V v = this.iterator.next();

            if (!iterator.hasNext()) {
                this.autoClose.run();
                this.hasNextValue = false;
            }
            else {
                this.hasNextValue = true;
            }
            return v;
        }
    }

    public static <V> PeekIterator<V> stopAtFirstMatching(PeekIterator<V> iterator, FilterFunction<V> stopMatcher)
    {
        return new StopAtFirstMatchingIterator<>(iterator, stopMatcher);
    }

    private static class StopAtFirstMatchingIterator<V>
            implements PeekIterator<V>
    {
        private final PeekIterator<V> iterator;
        private final FilterFunction<V> stopMatcher;

        private V value;
        private boolean hasNextValue;
        private boolean initd;

        private StopAtFirstMatchingIterator(PeekIterator<V> iterator, FilterFunction<V> stopMatcher)
        {
            this.iterator = requireNonNull(iterator, "iterator is null");
            this.stopMatcher = requireNonNull(stopMatcher, "stopMatcher is null");
            this.initd = false;
            this.hasNextValue = true;
        }

        private void tryNext()
        {
            if (iterator.hasNext()) {
                V v1 = iterator.peek();
                if (!stopMatcher.apply(v1)) {
                    V v2 = iterator.next();
                    assert v1 == v2;
                    this.value = v1;
                    return;
                }
            }
            this.hasNextValue = false;
        }

        @Override
        public boolean hasNext()
        {
            if (!initd) {
                this.tryNext();
                this.initd = true;
            }
            return hasNextValue;
        }

        @Override
        public V next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            V v = this.value;
            this.tryNext();
            return v;
        }

        @Override
        public V peek()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return this.value;
        }
    }

    public static <V> PeekIterator<V> peekIterator(Iterator<V> iterator)
    {
        requireNonNull(iterator, "iterator is null");
        if (iterator instanceof PeekIterator) {
            return (PeekIterator<V>) iterator;
        }
        return new PeekIterator<V>()
        {
            private boolean hasDefined = false;
            private V value;

            @Override
            public boolean hasNext()
            {
                return hasDefined || iterator.hasNext();
            }

            @Override
            public V next()
            {
                if (hasDefined) {
                    hasDefined = false;
                    return value;
                }
                else {
                    return iterator.next();
                }
            }

            @Override
            public V peek()
            {
                if (!hasDefined) {
                    this.value = iterator.next();
                    hasDefined = true;
                }
                return this.value;
            }
        };
    }

    public static <K, V, O> IteratorPlus<Tuple2<K, O>> mapGroupSorted(Iterator<Tuple2<K, V>> input, BiFunction<K, Iterator<V>, O> mapGroupFunc)
    {
        requireNonNull(input, "input Iterator is null");
        requireNonNull(mapGroupFunc, "mapGroupFunc is null");
        if (!input.hasNext()) {
            return Iterators.empty();
        }
        PeekIterator<Tuple2<K, V>> iterator = peekIterator(input);
        return new IteratorPlus<Tuple2<K, O>>()
        {
            @Override
            public boolean hasNext()
            {
                return iterator.hasNext();
            }

            @Override
            public Tuple2<K, O> next()
            {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                final K cKey = iterator.peek().f1();
                Iterator<V> child = Iterators.stopAtFirstMatching(iterator, x -> !Objects.equals(x.f1(), cKey)).map(Tuple2::f2);
                return Tuple2.of(cKey, mapGroupFunc.apply(cKey, child));
            }
        };
    }
}
