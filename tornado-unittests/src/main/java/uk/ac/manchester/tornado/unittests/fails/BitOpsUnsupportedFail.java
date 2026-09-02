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
 * Unlike {@code Integer.numberOfLeadingZeros}/{@code bitCount} (registered {@code InvocationPlugin}s)
 * and {@code numberOfTrailingZeros}/{@code highestOneBit}/{@code lowestOneBit} (unregistered but
 * still compile fine via their portable JDK bytecode fallback -- see
 * {@code numpromotion.TestBitManipulation}), {@code Integer.reverse} genuinely fails to compile:
 * confirmed empirically by the ABSENCE of a cached {@code .cubin} for it (every other bit op in
 * this family produces one under {@code var/cuda-codecache/}), and by it consistently throwing
 * "should not reach here" (a raw {@link Error}) while the others consistently do not, across
 * repeated isolated single-method runs.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.BitOpsUnsupportedFail
 * </code>
 */
public class BitOpsUnsupportedFail extends TornadoTestBase {

    public static void reverseKernel(IntArray a, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, Integer.reverse(a.get(i)));
        }
    }

    @Test(expected = Error.class)
    public void testReverseUnsupported() {
        final int size = 32;
        IntArray a = new IntArray(size);
        IntArray out = new IntArray(size);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", BitOpsUnsupportedFail::reverseKernel, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

}
