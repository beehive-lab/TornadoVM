/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestAPI extends TornadoTestBase {

    @Test
    public void testSyncObject() {
        final int N = 1024;
        int size = 20;
        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.lockObjectInMemory(data);

        s0.task("t0", TestArrays::addAccumulator, data, 1).execute();
        s0.syncObject(data);

        s0.unlockObjectFromMemory(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

    @Test
    public void testSyncObjects() {
        final int N = 128;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.lockObjectInMemory(data);

        s0.task("t0", TestArrays::addAccumulator, data, 1);
        s0.execute();
        s0.syncObjects(data);

        s0.unlockObjectFromMemory(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

    @Test
    public void testWarmUp() {
        final int N = 128;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.lockObjectInMemory(data);

        s0.task("t0", TestArrays::addAccumulator, data, 1);
        s0.streamOut(data);
        s0.warmup();
        s0.execute();

        s0.unlockObjectFromMemory(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

}
