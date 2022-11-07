/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import static org.junit.Assert.assertNotNull;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestIO extends TornadoTestBase {

    private float[] createAndInitializeArray(int size) {
        float[] array = new float[size];
        IntStream.range(0, size).parallel().forEach(idx -> {
            array[idx] = idx;
        });

        return array;
    }

    /**
     * This test case uses the forceCopyIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * This method is used to copy data once and reuse it in the next invocations.
     */
    @Test
    public void testForceCopyIn() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        TaskGraph s0 = new TaskGraph("s0");
        assertNotNull(s0);

        s0.transferToDevice(DataTransferMode.FIRST_EXECUTION, arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.transferToHost(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * This method is used to stream data every time a method is launched for
     * execution.
     */
    @Test
    public void testStreamIn() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        TaskGraph s0 = new TaskGraph("s0");
        assertNotNull(s0);

        s0.transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.transferToHost(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * Additionally, the lockObjectsInMemory method is used to pin buffers used for
     * streaming data to a device. Buffers used for locked arguments will be created
     * and allocated once and will be reused in the next invocations. The pinned
     * buffers are released by the unlockObjectsFromMemory method.
     */
    @Test
    public void testLockObjectsInMemory() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        TaskGraph s0 = new TaskGraph("s0");
        assertNotNull(s0);

        s0.lockObjectsInMemory(arrayA, arrayB, arrayC);
        s0.transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.transferToHost(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.execute();
        }

        s0.unlockObjectsFromMemory(arrayA, arrayB, arrayC);

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * Additionally, the lockObjectsInMemory method is used to pin buffers used for
     * streaming data to a device. Buffers used for locked arguments will be created
     * and allocated once and will be reused in the next invocations. The pinned
     * buffers are released by the unlockObjectsFromMemory method.
     *
     * In this test case, arrayB2 is used to update the reference of the arrayB
     * parameter of the vectorAddFloat task. As arrayB is created once and reused by
     * the updateReference method, the buffer for this object is created and
     * allocated once, and it is reused in the next invocations.
     */
    @Test
    public void testLockObjectsInMemoryWithUpdateReference01() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayB2 = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            arrayB[idx] = 2 * idx;
        });

        TaskGraph s0 = new TaskGraph("s0");
        assertNotNull(s0);

        s0.lockObjectsInMemory(arrayA, arrayB, arrayB2, arrayC);
        s0.transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.transferToHost(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.replaceParameter(arrayB, arrayB2);
            s0.execute();
            s0.replaceParameter(arrayB2, arrayB);
        }

        s0.unlockObjectsFromMemory(arrayA, arrayB, arrayC);

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    /**
     * This test case uses the streamIn method of the
     * {@link uk.ac.manchester.tornado.api.TaskGraph} API to pass input data to a
     * targeted device.
     *
     * Additionally, the lockObjectsInMemory method is used to pin buffers used for
     * streaming data to a device. Buffers used for locked arguments will be created
     * and allocated once and will be reused in the next invocations. The pinned
     * buffers are released by the unlockObjectsFromMemory method.
     *
     * In this test case, arrayB2 is used to update the reference of the arrayB
     * parameter of the vectorAddFloat task. As arrayB is created every time that
     * the TaskSchedule is executed, a new buffer is created and allocated every
     * time.
     */
    @Test
    public void testLockObjectsInMemoryWithUpdateReference02() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            arrayB[idx] = 2 * idx;
        });

        TaskGraph s0 = new TaskGraph("s0");
        assertNotNull(s0);

        s0.lockObjectsInMemory(arrayA, arrayB, arrayC);
        s0.transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.transferToHost(arrayC);

        for (int i = 0; i < 4; i++) {
            float[] arrayB2 = createAndInitializeArray(N);
            s0.replaceParameter(arrayB, arrayB2);
            s0.execute();
            s0.replaceParameter(arrayB2, arrayB);
        }

        s0.unlockObjectsFromMemory(arrayA, arrayB, arrayC);

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }
}
