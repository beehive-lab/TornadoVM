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
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

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
 * The shim is <em>not</em> built by {@code make}, because building it needs libcudf and almost
 * nobody has it. Without it this module loads, reports itself unavailable, and costs nothing --
 * the same thing cuSPARSE does on a machine with no CUDA toolkit. See the module README for how
 * to build it.
 *
 * <h2>What is bound, and what is not</h2>
 *
 * <p>
 * Four primitives, chosen because each is a Flink batch operator with a measured headroom and no
 * other route onto a device. Keys are 32-bit and values are FP64, which is what the SQL side
 * produces; widening that is a matter of more shim entry points rather than a different design.
 */
public final class Cudf {

    /** The name a task graph uses to ask for this library. */
    public static final String LIBRARY_NAME = "rapids/cudf";

    private Cudf() {
    }

    /**
     * Sorts {@code n} key/value pairs by key, ascending, writing the result to {@code outKeys} and
     * {@code outValues}.
     *
     * <p>
     * A stable sort, because SQL's {@code ORDER BY} is stable over equal keys and a caller that
     * sorts twice on different columns is relying on it.
     */
    public static LibraryTaskDescriptor sortPairs(int n, IntArray keys, DoubleArray values, IntArray outKeys, DoubleArray outValues) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("sortPairs") //
                .withParameters(new Object[] { n, keys, values, outKeys, outValues }) //
                .withAccess(access);
    }

    /**
     * The permutation that sorts {@code keys} ascending, as row positions rather than sorted data.
     *
     * <p>What a SQL {@code ORDER BY} needs, and not what {@link #sortPairs} gives it: sorting
     * key/value pairs reorders one carried column, where a query's rows have arbitrary columns of
     * arbitrary types and most of them are of no interest to a device. Handing back the
     * permutation lets the caller reorder whatever it is holding, so nothing but the key crosses
     * the interconnect and no payload column has to be expressible on a device at all.
     *
     * <p>{@code outOrder} holds {@code n} positions. Stable over equal keys, like {@link
     * #sortPairs}. {@code nullsFirst} chooses where absent keys go, which SQL lets a query state.
     */
    public static LibraryTaskDescriptor sortedOrder(int n, IntArray keys, int nullsFirst, IntArray outOrder) {
        Access[] access = new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("sortedOrder") //
                .withParameters(new Object[] { n, keys, nullsFirst, outOrder }) //
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
}
