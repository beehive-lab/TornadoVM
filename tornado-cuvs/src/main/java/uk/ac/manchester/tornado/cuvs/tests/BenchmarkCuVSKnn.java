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
package uk.ac.manchester.tornado.cuvs.tests;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.cuvs.CuVS;
import uk.ac.manchester.tornado.cuvs.CuVSAllNeighborsAlgo;
import uk.ac.manchester.tornado.cuvs.CuVSDistance;

/**
 * Times the all-neighbors k-NN graph of a random dataset with cuVS (exact brute force and NN-Descent), as a
 * TornadoVM library task.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cuvs/uk.ac.manchester.tornado.cuvs.tests.BenchmarkCuVSKnn [rows] [dim] [k]
 * </code>
 */
public class BenchmarkCuVSKnn {

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int rows = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        final int dim = args.length > 1 ? Integer.parseInt(args[1]) : 128;
        final int k = args.length > 2 ? Integer.parseInt(args[2]) : 32;
        FloatArray dataset = new FloatArray(rows * dim);
        Random random = new Random(42);
        for (int i = 0; i < rows * dim; i++) {
            dataset.set(i, random.nextFloat());
        }
        LongArray neighbors = new LongArray(rows * k);
        FloatArray distances = new FloatArray(rows * k);

        for (CuVSAllNeighborsAlgo algo : new CuVSAllNeighborsAlgo[] { CuVSAllNeighborsAlgo.BRUTE_FORCE, CuVSAllNeighborsAlgo.NN_DESCENT }) {
            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataset) //
                    .libraryTask("graph", CuVS::allNeighbors, dataset, rows, dim, k, algo.value(), CuVSDistance.L2_EXPANDED.value(), neighbors, distances) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute(); // warm-up
                long best = Long.MAX_VALUE;
                for (int r = 0; r < 3; r++) {
                    long t = System.nanoTime();
                    plan.execute();
                    best = Math.min(best, System.nanoTime() - t);
                }
                System.out.printf("cuVS all-neighbors %-11s %d x %d, k=%d: %.1f ms%n", algo, rows, dim, k, best / 1e6);
            }
        }
    }
}
