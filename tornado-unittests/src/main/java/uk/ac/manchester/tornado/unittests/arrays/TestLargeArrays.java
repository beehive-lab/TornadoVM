/*
 * Copyright (c) 2025 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Assume;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V -J"-Dtornado.device.memory=3GB" uk.ac.manchester.tornado.unittests.arrays.TestLargeArrays
 * </code>
 * </p>
 */
public class TestLargeArrays extends TornadoTestBase {

    public static boolean checkDeviceMemory() {
        long mem = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getMaxGlobalMemory();

        return mem > 3L * 1024 * 1024 * 1024;
    }

    public static void addAccumulator(FloatArray a, float value) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + value);
        }
    }

    @Override
    public void before() {
        boolean hasRequiredDeviceMemory = checkDeviceMemory();

        // Skip all tests if device memory requirement not met
        Assume.assumeTrue("Skipping TestLargeArrays: requires > 3GB global memory", hasRequiredDeviceMemory);
    }

    @Test
    public void testLargeFloatArraySafe() throws TornadoExecutionPlanException {
        final int numElements = 510_000_000; // Known safe size
        testFloatArrayWithSize(numElements);
    }

    @Test(expected = TornadoOutOfMemoryException.class)
    public void testLargeFloatArrayOverflow() throws TornadoExecutionPlanException {
        final int numElements = 540_000_000; // Known to overflow
        testFloatArrayWithSize(numElements);
    }

    private void testFloatArrayWithSize(int numElements) throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
        });

        FloatArray b = FloatArray.fromSegment(a.getSegment());
        float accumulator = 1.0f;

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, a).task("t0", TestLargeArrays::addAccumulator, a, accumulator).transferToHost(
                DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(b.get(i) + accumulator, a.get(i), 0.01f);
        }
    }
}
