/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.cudf;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.cudf.enums.CudfAggregation;

/**
 * RAPIDS cuDF relational primitives as TornadoVM library tasks.
 *
 * <p>
 * The primitives here are the ones a generated kernel cannot express. A projection or a filter is
 * per-row work and TornadoVM compiles it well; a sort, a grouped aggregation and a join all need
 * cross-row cooperation -- multi-pass compare-exchange, atomics into a shared table, a hash build
 * side -- which no {@code @Parallel} loop states. Binding a library is not a shortcut around code
 * generation, it is the other half of it, and composing the two in one task graph is the point:
 * the intermediate between a generated projection and a cuDF group-by never leaves the device.
 *
 * <h2>Why this one needs a shim, when cuBLAS and cuSPARSE do not</h2>
 *
 * <p>
 * Every other library bound here exposes a C ABI, so {@code java.lang.foreign} can call it
 * directly and the binding is pure Java. cuDF is C++: it returns {@code std::unique_ptr<column>}
 * by value, takes {@code table_view} by value, and its symbols are mangled. None of that is
 * callable from FFM. So this module binds {@code libtornado-cudf.so}, a small {@code extern "C"}
 * shim over cuDF whose source ships in {@code src/main/native}.
 *
 * <p>
 * The shim is built by {@code tornado-drivers/cudf-jni} under the {@code cuda-backend} profile,
 * as the CUTLASS and cuDNN shims are. Building it needs libcudf, which almost nobody has, so the
 * build skips it with a warning rather than failing when it is absent -- and without it this
 * module loads, reports itself unavailable, and costs nothing, the same thing cuSPARSE does on a
 * machine with no CUDA toolkit. See the module README for how to install libcudf.
 *
 * <h2>What is bound, and what is not</h2>
 *
 * <p>
 * Eight entry points over five relational shapes -- ordering, grouping, scanning, joining and
 * filtering -- each one an operator with no other route onto a device. Four of them are the general
 * form of another: {@link #groupAggregate} is {@link #groupSum} over several columns and any
 * aggregation, {@link #sortedOrderMulti} is {@link #sortedOrder} over several keys in either
 * direction, and the narrow pair stay because they are what most callers want to read.
 *
 * <p>
 * Keys are 32-bit and values are FP64, and no column carries a validity mask, so every operand is
 * dense and non-null; widening either is a matter of more shim entry points rather than a
 * different design.
 */
public final class Cudf {

    /** The name a task graph uses to ask for this library. */
    public static final String LIBRARY_NAME = "rapids/cudf";

    private Cudf() {
    }

    /**
     * The permutation that sorts {@code keys} ascending, as row positions rather than sorted data.
     *
     * <p>What a SQL {@code ORDER BY} needs. A sort that reorders the key column with one carried
     * payload column serves only a caller whose rows are exactly that shape; a query's rows have
     * arbitrary columns of arbitrary types, and most of them are of no interest to a device.
     * Handing back the permutation lets the caller reorder whatever it is holding, so nothing but
     * the key crosses the interconnect and no payload column has to be expressible on a device at
     * all.
     *
     * <p>{@code outOrder} holds {@code n} positions. Stable over equal keys, because SQL's
     * {@code ORDER BY} is and a caller that sorts twice on different columns relies on it.
     *
     * <p>No null ordering is offered: columns reaching this binding carry no validity mask, so no
     * key can be absent and a parameter choosing where absent keys go would be one the data can
     * never exercise. It belongs with null support, not before it.
     */
    public static LibraryTaskDescriptor sortedOrder(int n, IntArray keys, IntArray outOrder) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("sortedOrder") //
                .withParameters(new Object[] { n, keys, outOrder }) //
                .withAccess(access);
    }

    /**
     * Sums {@code values} per distinct key, writing one row per group.
     *
     * <p>
     * {@code outKeys} and {@code outSums} must hold at least as many rows as there are distinct
     * keys; sizing them to {@code n} is always safe. The number of groups actually produced is
     * written to element 0 of {@code outGroups}, because the caller cannot know it in advance and
     * a device-side count is the only honest answer.
     */
    public static LibraryTaskDescriptor groupSum(int n, IntArray keys, DoubleArray values, IntArray outKeys, DoubleArray outSums, IntArray outGroups) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY, Access.WRITE_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("groupSum") //
                .withParameters(new Object[] { n, keys, values, outKeys, outSums, outGroups }) //
                .withAccess(access);
    }

    /**
     * Running total of {@code values}, inclusive -- the shape of {@code SUM(x) OVER (ORDER BY ...
     * ROWS UNBOUNDED PRECEDING)}.
     */
    public static LibraryTaskDescriptor runningSum(int n, DoubleArray values, DoubleArray out) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("runningSum") //
                .withParameters(new Object[] { n, values, out }) //
                .withAccess(access);
    }

    /**
     * Inner-joins two key columns, writing the matched row positions as index pairs.
     *
     * <p>
     * Indices rather than joined rows: the caller already has both sides on the device, and
     * gathering the payload columns is a per-row map that a generated kernel does well. Splitting
     * it that way keeps the shim small and lets the two halves share one task graph.
     *
     * <p>
     * {@code capacity} bounds the output. A join whose result exceeds it fails rather than
     * truncating, because a silently short join is a wrong answer; element 0 of {@code outCount}
     * receives the number of pairs written.
     */
    public static LibraryTaskDescriptor innerJoin(int leftCount, IntArray leftKeys, int rightCount, IntArray rightKeys, int capacity, IntArray outLeft, IntArray outRight, IntArray outCount) {
        Access[] access = new Access[8];
        Arrays.fill(access, Access.READ_ONLY);
        access[5] = Access.WRITE_ONLY; // outLeft
        access[6] = Access.WRITE_ONLY; // outRight
        access[7] = Access.WRITE_ONLY; // outCount
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("innerJoin") //
                .withParameters(new Object[] { leftCount, leftKeys, rightCount, rightKeys, capacity, outLeft, outRight, outCount }) //
                .withAccess(access);
    }

    /**
     * {@link #groupSum} generalised: one aggregation over several value columns, in one pass.
     *
     * <p>
     * Doing it column by column re-hashes the same keys once per column, which for a k-means
     * iteration summing <em>d</em> coordinates per cluster is <em>d</em> times the grouping work for
     * no reason. cuDF takes a vector of aggregation requests and this hands it one per column.
     *
     * <p>
     * Columns are packed with a stride of {@code n}: column <em>c</em> of {@code values} starts at
     * element {@code c * n}, and its results at {@code outResults[c * n]}, of which the first
     * {@code outGroups[0]} entries are live. The stride is {@code n} and not the group count because
     * the caller has to size the buffer before the device can say how many groups there are.
     *
     * <p>
     * {@code outKeys} holds one key per group. {@link CudfAggregation#COUNT} arrives as a
     * {@code double} like the rest, so one output buffer serves every aggregation.
     *
     * @param n
     *     rows in each column.
     * @param columns
     *     how many value columns {@code values} holds.
     * @param aggregation
     *     the code from {@link CudfAggregation#code()}.
     */
    public static LibraryTaskDescriptor groupAggregate(int n, int columns, int aggregation, IntArray keys, DoubleArray values, IntArray outKeys, DoubleArray outResults,
            IntArray outGroups) {
        Access[] access = new Access[8];
        Arrays.fill(access, Access.READ_ONLY);
        access[5] = Access.WRITE_ONLY; // outKeys
        access[6] = Access.WRITE_ONLY; // outResults
        access[7] = Access.WRITE_ONLY; // outGroups
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("groupAggregate") //
                .withParameters(new Object[] { n, columns, aggregation, keys, values, outKeys, outResults, outGroups }) //
                .withAccess(access);
    }

    /**
     * One aggregation over a whole column, written to {@code out[0]}.
     *
     * <p>
     * The shape that is not a group-by and cannot be faked as one without inventing a key: scaling
     * a feature needs the column's own minimum and maximum, and "has any centroid moved further
     * than epsilon" is a maximum over a column of deltas.
     *
     * <p>
     * {@link CudfAggregation#COUNT} is refused, because ungrouped it is {@code n} and the caller
     * already has that. An empty column is refused too, rather than reporting a value it does not
     * have.
     *
     * @param operation
     *     the code from {@link CudfAggregation#code()}, excluding {@code COUNT}.
     */
    public static LibraryTaskDescriptor reduce(int n, int operation, DoubleArray values, DoubleArray out) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("reduce") //
                .withParameters(new Object[] { n, operation, values, out }) //
                .withAccess(access);
    }

    /**
     * The positions where {@code mask} is non-zero, in order -- a stream compaction.
     *
     * <p>
     * The filter, and the one operation here that a generated kernel genuinely cannot do. Computing
     * a predicate per row is a map and TornadoVM compiles it well; moving the survivors together is
     * cross-row, and no {@code @Parallel} loop states it.
     *
     * <p>
     * Positions rather than compacted rows, for the same reason {@link #sortedOrder} returns a
     * permutation: the caller gathers whatever columns it holds, and none of them has to be
     * expressible on a device. {@code outCount[0]} receives how many survived, and a result larger
     * than {@code capacity} fails rather than truncating -- a short filter is a wrong answer that no
     * row count catches, because the caller reads the count the filter wrote.
     *
     * @param mask
     *     one byte a row, zero to drop and non-zero to keep.
     * @param capacity
     *     rows {@code outIndices} can hold; sizing it to {@code n} is always safe.
     */
    public static LibraryTaskDescriptor selectedIndices(int n, int capacity, ByteArray mask, IntArray outIndices, IntArray outCount) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("selectedIndices") //
                .withParameters(new Object[] { n, capacity, mask, outIndices, outCount }) //
                .withAccess(access);
    }

    /**
     * {@link #sortedOrder} generalised: several key columns, each ascending or descending.
     *
     * <p>
     * Key columns are packed with a stride of {@code n}, so column <em>c</em> starts at element
     * {@code c * n}, and bit <em>c</em> of {@code descendingMask} makes that column descending.
     * Still stable, so a caller can rely on ties keeping the order they arrived in.
     *
     * @param keyColumns
     *     1 to 31, since the mask carries one bit each.
     * @param descendingMask
     *     bit <em>c</em> set means column <em>c</em> sorts descending; 0 is all ascending and is
     *     what {@link #sortedOrder} does.
     */
    public static LibraryTaskDescriptor sortedOrderMulti(int n, int keyColumns, int descendingMask, IntArray keys, IntArray outOrder) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("sortedOrderMulti") //
                .withParameters(new Object[] { n, keyColumns, descendingMask, keys, outOrder }) //
                .withAccess(access);
    }
}
