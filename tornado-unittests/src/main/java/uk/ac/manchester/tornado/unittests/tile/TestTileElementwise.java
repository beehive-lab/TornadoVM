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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * jTile milestone M1 - elementwise tile operations over the {@link Tile} API.
 *
 * <p>
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileElementwise
 * </code>
 * </p>
 */
public class TestTileElementwise extends TornadoTestBase {

    // c = alpha * a + b, expressed as tile algebra.
    public static void saxpyTile(KernelContext context, FloatArray a, FloatArray b, FloatArray c, float alpha) {
        float ta = Tile.load(context, a);
        float tb = Tile.load(context, b);
        Tile.store(context, c, Tile.add(Tile.scale(ta, alpha), tb));
    }

    // c = a * b, elementwise, expressed as tile algebra.
    public static void mulTile(KernelContext context, FloatArray a, FloatArray b, FloatArray c) {
        float ta = Tile.load(context, a);
        float tb = Tile.load(context, b);
        Tile.store(context, c, Tile.mul(ta, tb));
    }

    @Test
    public void testTileSaxpy() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int tileSize = 256;
        final float alpha = 3.0f;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        IntStream.range(0, size).forEach(i -> {
            a.set(i, i);
            b.set(i, 2.0f * i);
        });

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(tileSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestTileElementwise::saxpyTile, context, a, b, c, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(alpha * i + 2.0f * i, c.get(i), 0.001f);
        }
    }

    @Test
    public void testTileMul() throws TornadoExecutionPlanException {
        final int size = 512;
        final int tileSize = 128;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        IntStream.range(0, size).forEach(i -> {
            a.set(i, i + 1);
            b.set(i, 2.0f);
        });

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(tileSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestTileElementwise::mulTile, context, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals((i + 1) * 2.0f, c.get(i), 0.001f);
        }
    }
}
