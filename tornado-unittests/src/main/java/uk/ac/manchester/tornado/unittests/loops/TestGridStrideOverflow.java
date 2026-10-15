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
package uk.ac.manchester.tornado.unittests.loops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A @Parallel loop whose grid-stride step leaves the int range: thread {@code g} computes
 * {@code g + globalSize}, which exceeds {@code Integer.MAX_VALUE} once the loop has more than 2^30
 * iterations. Wrapped, the index is negative, passes {@code i < n}, and the thread runs iterations
 * a second time.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.loops.TestGridStrideOverflow
 * </code>
 */
public class TestGridStrideOverflow extends TornadoTestBase {

    /** Iterations past 2^30, rounded to the work-group size so the grid is exactly n threads. */
    private static final int N = (1 << 30) + 1024;
    private static final int LOW = 8192;

    /** Counts the iterations that touch the first {@code low} indices. */
    public static void countLow(int n, int low, AtomicInteger hits) {
        for (@Parallel int i = 0; i < n; i++) {
            if (i < low) {
                hits.incrementAndGet();
            }
        }
    }

    @Test
    public void testEveryIterationRunsOnce() throws TornadoExecutionPlanException {
        AtomicInteger hits = new AtomicInteger(0);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, hits) //
                .task("t0", TestGridStrideOverflow::countLow, N, LOW, hits) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hits);

        WorkerGrid1D grid = new WorkerGrid1D(N);
        grid.setLocalWork(256, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", grid);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }
        assertEquals(LOW, hits.get());
    }
}
