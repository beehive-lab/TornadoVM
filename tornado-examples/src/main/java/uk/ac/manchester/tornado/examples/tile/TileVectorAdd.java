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
package uk.ac.manchester.tornado.examples.tile;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Smallest useful CUDA Tile kernel: one tile block adds one tile of each input.
 *
 * <p>
 * Run with {@code tornado --printKernel} to see the generated CUDA Tile C++. The kernel below
 * names no thread and no warp; the tile compiler decides how many threads back each tile block.
 * </p>
 *
 * <pre>
 * tornado --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileVectorAdd
 * </pre>
 */
public class TileVectorAdd {

    private static final int SIZE = 4096;

    /** Elements per tile block. A power of two, and a compile-time constant, as CUDA Tile requires. */
    private static final int TILE = 256;

    public static void vectorAdd(TileContext tc, FloatArray a, FloatArray b, FloatArray c, int n) {
        PartitionView av = tc.partition(tc.view(a, n), TILE);
        PartitionView bv = tc.partition(tc.view(b, n), TILE);
        PartitionView cv = tc.partition(tc.view(c, n), TILE);

        int block = tc.bidX();
        cv.store(tc.add(av.load(block), bv.load(block)), block);
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(SIZE);
        FloatArray b = new FloatArray(SIZE);
        FloatArray c = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            a.set(i, i);
            b.set(i, 2.0f * i);
        }

        TileContext context = new TileContext();

        // The worker grid of a tile task counts TILE BLOCKS, and local work is pinned to one:
        // CUDA Tile owns the thread mapping and requires a block dimension of 1x1x1.
        WorkerGrid1D worker = new WorkerGrid1D(SIZE / TILE);
        worker.setLocalWork(1, 1, 1);
        GridScheduler grid = new GridScheduler("tile.add", worker);

        TaskGraph graph = new TaskGraph("tile") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("add", TileVectorAdd::vectorAdd, context, a, b, c, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        boolean correct = true;
        for (int i = 0; i < SIZE; i++) {
            if (Math.abs(c.get(i) - 3.0f * i) > 1e-3f) {
                System.out.println("mismatch at " + i + ": " + c.get(i) + " != " + (3.0f * i));
                correct = false;
                break;
            }
        }
        System.out.println(correct ? "TileVectorAdd: correct" : "TileVectorAdd: WRONG");
    }
}
