/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to execute:
 * 
 * <code>
 *     tornado-test.py --threadInfo --printKernel --fast -V uk.ac.manchester.tornado.unittests.kernelcontext.api.KernelPluginsTests
 * </code>
 */
public class KernelPluginsTests extends TornadoTestBase {

    private static void apiTest(KernelContext context, int[] data) {
        data[0] = context.getGlobalGroupSize(0);
    }

    @Test
    public void test01() {

        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid1D(1024);
        grid.setWorkerGrid("s0.t0", worker);

        int[] data = new int[1024];
        TaskSchedule ts = new TaskSchedule("s0").task("t0", KernelPluginsTests::apiTest, context, data).streamOut(data);
        ts.execute(grid);
        assertEquals(1024, data[0]);
    }
}
