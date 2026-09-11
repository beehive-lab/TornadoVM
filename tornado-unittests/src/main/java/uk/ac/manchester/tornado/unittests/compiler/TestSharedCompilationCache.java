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

package uk.ac.manchester.tornado.unittests.compiler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The result of the Graal front end is cached per device and shared by every execution plan, so
 * that creating a new {@link TornadoExecutionPlan} does not re-run the whole JIT pipeline. These
 * tests pin the cases where a cached entry must NOT be reused: shape analysis folds concrete
 * loop bounds into the parallel domain, so anything that changes the iteration space has to miss
 * the cache.
 *
 * <p>
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.compiler.TestSharedCompilationCache
 * </code>
 * </p>
 */
public class TestSharedCompilationCache extends TornadoTestBase {

    public static void twice(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2.0f);
        }
    }

    public static void fill(IntArray out, int limit) {
        for (@Parallel int i = 0; i < limit; i++) {
            out.set(i, i + 1);
        }
    }

    /**
     * Runs the same task graph and task name in a fresh execution plan each time, so every call
     * after the first is served from the shared compilation cache.
     */
    private static void runTwice(int numElements) throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(numElements);
        FloatArray out = new FloatArray(numElements);
        in.init(1.0f);
        out.init(-1.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestSharedCompilationCache::twice, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals("element " + i + " of " + numElements, 2.0f, out.get(i), 0.0f);
        }
    }

    /**
     * A second plan reusing the first plan's compiled code must still produce correct results.
     */
    @Test
    public void testRepeatedExecutionPlansSameSize() throws TornadoExecutionPlanException {
        runTwice(1024);
        runTwice(1024);
        runTwice(1024);
    }

    /**
     * The parallel domain is derived from the array length at compile time, so a larger array in
     * a later plan must not inherit the smaller iteration space. Growing the size is the case
     * that silently under-computes if the cache ignores argument shape.
     */
    @Test
    public void testExecutionPlansWithDifferentSizes() throws TornadoExecutionPlanException {
        runTwice(1024);
        runTwice(4096);
        runTwice(256);
        runTwice(4096);
    }

    /**
     * Same here for a scalar that bounds the parallel loop: it is folded into the domain, so two
     * plans differing only in that value must not share compiled code.
     */
    @Test
    public void testExecutionPlansWithDifferentScalarBounds() throws TornadoExecutionPlanException {
        for (int limit : new int[] { 128, 512, 64, 512 }) {
            IntArray out = new IntArray(512);
            out.init(0);

            TaskGraph taskGraph = new TaskGraph("s1") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, out) //
                    .task("t0", TestSharedCompilationCache::fill, out, limit) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.execute();
            }

            for (int i = 0; i < limit; i++) {
                assertEquals("limit " + limit + ", element " + i, i + 1, out.get(i));
            }
            for (int i = limit; i < 512; i++) {
                assertEquals("limit " + limit + ", element " + i + " must be untouched", 0, out.get(i));
            }
        }
    }
}
