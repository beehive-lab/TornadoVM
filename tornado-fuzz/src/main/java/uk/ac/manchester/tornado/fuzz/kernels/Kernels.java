/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.fuzz.kernels;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;

/**
 * Shared execution plumbing for KernelContext templates: build a 1D worker grid
 * for {@code fuzz.t0} and run the graph on a specific CUDA device.
 */
public final class Kernels {

    public static final String GRAPH = "fuzz";
    public static final String TASK = "t0";
    public static final String TASK_ID = GRAPH + "." + TASK;

    private Kernels() {
    }

    /** Largest power-of-two local size that divides {@code size} and is <= cap. */
    public static int chooseLocalWork(int size, int cap) {
        int local = 1;
        for (int candidate = 2; candidate <= cap && candidate <= size; candidate <<= 1) {
            if (size % candidate == 0) {
                local = candidate;
            }
        }
        return local;
    }

    public static GridScheduler gridScheduler(int globalWork, int localWork) {
        WorkerGrid worker = new WorkerGrid1D(globalWork);
        worker.setGlobalWork(globalWork, 1, 1);
        worker.setLocalWork(localWork, 1, 1);
        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid(TASK_ID, worker);
        return scheduler;
    }

    public static void run(TaskGraph taskGraph, GridScheduler scheduler, TornadoDevice device) throws TornadoExecutionPlanException {
        ImmutableTaskGraph immutable = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutable)) {
            plan.withGridScheduler(scheduler).withDevice(device).execute();
        }
    }
}
