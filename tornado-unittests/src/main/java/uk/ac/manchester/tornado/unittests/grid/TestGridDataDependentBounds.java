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
package uk.ac.manchester.tornado.unittests.grid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code @Parallel} loops whose bounds are array contents, which only the device knows, run with a
 * worker grid of the user's (as in the TornadoVM ray tracer, whose image size is in an array).
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.grid.TestGridDataDependentBounds
 * </code>
 */
public class TestGridDataDependentBounds extends TornadoTestBase {

    private static void fill(IntArray pixels, IntArray dimensions) {
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        for (@Parallel int x = 0; x < width; x++) {
            for (@Parallel int y = 0; y < height; y++) {
                pixels.set(x + y * width, x * 1000 + y);
            }
        }
    }

    @Test
    public void testBoundsFromArrayWithWorkerGrid() throws TornadoExecutionPlanException {
        final int width = 200;
        final int height = 120;
        IntArray pixels = new IntArray(width * height);
        IntArray dimensions = IntArray.fromElements(width, height);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dimensions) //
                .task("t0", TestGridDataDependentBounds::fill, pixels, dimensions) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, pixels);
        // A grid smaller than the image in x: the loops stride over it.
        WorkerGrid2D worker = new WorkerGrid2D(128, height);
        worker.setLocalWork(16, 8, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(grid).execute();
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(x * 1000 + y, pixels.get(x + y * width));
            }
        }
    }
}
