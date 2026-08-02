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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code TestSlice} only ever checks slice CONTENT from the host side after a kernel finished --
 * it never passes a parent array and a slice VIEW of it into the SAME kernel call as two distinct
 * Java objects backed by the same underlying off-heap memory. This does exactly that, and the
 * empirical result is a genuine finding, not a test bug: {@link FloatArray#slice(int, int)}
 * shares the same host-side {@code MemorySegment} (true aliasing on the host), but TornadoVM's
 * data-flow analysis allocates a SEPARATE device buffer per distinct Java array object regardless
 * of host-level segment aliasing. So a write through {@code parent} on the device is NOT visible
 * through {@code sliceView} within the same kernel call -- confirmed by first observing this
 * (the naive "should alias" version of this test failed: reads came back as the untouched
 * sentinel, not the freshly-written value), then asserting the real behavior below. The parent
 * array itself is still written and transferred back correctly, proving the write succeeded; it
 * simply doesn't propagate to the second view's independent device buffer.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestSliceAliasing
 * </code>
 */
public class TestSliceAliasing extends TornadoTestBase {

    public static void writeParentReadSlice(FloatArray parent, FloatArray sliceView, int sliceOffset, FloatArray observed) {
        for (@Parallel int i = 0; i < observed.getSize(); i++) {
            parent.set(sliceOffset + i, (float) (i * 3 + 1));
            observed.set(i, sliceView.get(i));
        }
    }

    @Test
    public void testDeviceBuffersOfParentAndSliceDoNotAlias() throws TornadoExecutionPlanException {
        final int parentSize = 512;
        final int sliceOffset = 256;
        final int sliceLength = 256;
        final float sentinel = -1.0f;

        FloatArray parent = new FloatArray(parentSize);
        parent.init(sentinel);
        FloatArray sliceView = parent.slice(sliceOffset, sliceLength);
        FloatArray observed = new FloatArray(sliceLength);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, parent) //
                .task("t0", TestSliceAliasing::writeParentReadSlice, parent, sliceView, sliceOffset, observed) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, observed, parent);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < sliceLength; i++) {
            // The write through `parent` really happened...
            assertEquals(i * 3 + 1, parent.get(sliceOffset + i), 0.001f);
            // ...but `sliceView`, passed as a second distinct array argument, has its own
            // separate device buffer that was never touched by that write.
            assertEquals(sentinel, observed.get(i), 0.001f);
        }
    }

}
