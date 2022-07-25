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

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestIO extends TornadoTestBase {

    private float[] createAndInitializeArray(int size) {
        float[] array = new float[size];
        IntStream.range(0, size).parallel().forEach(idx -> {
            array[idx] = idx;
        });

        return array;
    }

    @Test
    public void testCopyIn() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.forceCopyIn(arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.streamOut(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    @Test
    public void testStreamIn() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.streamIn(arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.streamOut(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    @Test
    public void testLockObjectsInMemory() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.lockObjectsInMemory(arrayA, arrayB, arrayC);
        s0.streamIn(arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.streamOut(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.execute();
        }

        s0.unlockObjectsFromMemory(arrayA, arrayB, arrayC);

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

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

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.lockObjectsInMemory(arrayA, arrayB, arrayB2, arrayC);
        s0.streamIn(arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.streamOut(arrayC);

        for (int i = 0; i < 4; i++) {
            s0.updateReference(arrayB, arrayB2);
            s0.execute();
            s0.updateReference(arrayB2, arrayB);
        }

        s0.unlockObjectsFromMemory(arrayA, arrayB, arrayC);

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }

    @Test
    public void testLockObjectsInMemoryWithUpdateReference02() {
        final int N = 128;

        float[] arrayA = createAndInitializeArray(N);
        float[] arrayB = createAndInitializeArray(N);
        float[] arrayC = new float[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            arrayB[idx] = 2 * idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.lockObjectsInMemory(arrayA, arrayB, arrayC);
        s0.streamIn(arrayA, arrayB);
        s0.task("t0", TestArrays::vectorAddFloat, arrayA, arrayB, arrayC);
        s0.streamOut(arrayC);

        for (int i = 0; i < 4; i++) {
            float[] arrayB2 = createAndInitializeArray(N);
            s0.updateReference(arrayB, arrayB2);
            s0.execute();
            s0.updateReference(arrayB2, arrayB);
        }

        s0.unlockObjectsFromMemory(arrayA, arrayB, arrayC);

        for (int i = 0; i < N; i++) {
            assertEquals(2 * i, arrayC[i], 0.0f);
        }
    }
}
