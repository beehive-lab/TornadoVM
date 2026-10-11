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
package uk.ac.manchester.tornado.unittests.fails;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Declaring a multi-dimensional array (e.g. {@code int[][]}) INSIDE a kernel is not supported
 * (distinct from the SUPPORTED flattened {@code Matrix2DInt}-style off-heap types passed in from the
 * host). The compiler refuses the kernel with an {@link Error} (a {@code TornadoInternalError}), so this asserts
 * {@link Error}: callers must catch broadly to recover. This pins down the restriction with a
 * regression test instead of leaving it undocumented-by-test.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.MultiDimArrayFail
 * </code>
 */
public class MultiDimArrayFail extends TornadoTestBase {

    public static void kernelWithLocalMultiDimArray(IntArray a) {
        int[][] local = new int[4][4];
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            local[0][0] = i;
            a.set(i, local[0][0]);
        }
    }

    @Test(expected = Error.class)
    public void testMultiDimArrayDeclaration() {
        final int size = 64;
        IntArray a = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", MultiDimArrayFail::kernelWithLocalMultiDimArray, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

}
