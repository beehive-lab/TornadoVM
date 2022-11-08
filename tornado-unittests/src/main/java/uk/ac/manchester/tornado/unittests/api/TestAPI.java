/*
 * Copyright (c) 2013-2020, 2022 APT Group, Department of Computer Science,
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

/**
 * How to run?
 *
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.api.TestAPI
 * </code>
 */
public class TestAPI extends TornadoTestBase {

    @Test
    public void testSyncObject() {
        final int N = 1024;
        int size = 20;
        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.lockObjectInMemory(data) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .execute(); //

        // Force data transfers from D->H after the execution of a task-graph
        taskGraph.syncObject(data);

        // Mark objects associated with the task-graph for reusing memory
        taskGraph.unlockObjectFromMemory(data);

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

        TaskGraph tg = new TaskGraph("s0");
        assertNotNull(tg);

        tg.lockObjectInMemory(data);
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        tg.task("t0", TestArrays::addAccumulator, data, 1);
        tg.execute();

        tg.syncObjects(data);
        tg.unlockObjectFromMemory(data);

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

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.lockObjectInMemory(data);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        taskGraph.task("t0", TestArrays::addAccumulator, data, 1);
        taskGraph.transferToHost(data);

        taskGraph.warmup();

        taskGraph.execute();
        taskGraph.unlockObjectFromMemory(data);

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

}
