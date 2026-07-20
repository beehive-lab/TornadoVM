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
package uk.ac.manchester.tornado.examples.tile;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * jTile elementwise example (CUDA backend): SAXPY {@code c = alpha*a + b} expressed as tile
 * algebra over the {@link Tile} API.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileSaxpy
 * </code>
 */
public class TileSaxpy {

    public static void saxpy(KernelContext context, FloatArray a, FloatArray b, FloatArray c, float alpha) {
        float ta = Tile.load(context, a);
        float tb = Tile.load(context, b);
        Tile.store(context, c, Tile.add(Tile.scale(ta, alpha), tb));
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int size = 1 << 20;
        final float alpha = 2.5f;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, i);
            b.set(i, 2.0f * i);
        }

        WorkerGrid1D worker = new WorkerGrid1D(size);
        worker.setLocalWork(256, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TileSaxpy::saxpy, context, a, b, c, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        int probe = 12345;
        float expected = alpha * probe + 2.0f * probe;
        boolean ok = Math.abs(c.get(probe) - expected) < 0.001f;
        System.out.printf("jTile SAXPY size=%d: c[%d]=%.1f expected=%.1f -> %s%n", size, probe, c.get(probe), expected, ok ? "PASS" : "FAIL");
    }
}
