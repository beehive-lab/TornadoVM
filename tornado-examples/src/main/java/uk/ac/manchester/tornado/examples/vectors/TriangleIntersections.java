/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.vectors;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble3;
import uk.ac.manchester.tornado.api.types.vectors.Double3;

import static uk.ac.manchester.tornado.api.types.vectors.Double3.cross;
import static uk.ac.manchester.tornado.api.types.vectors.Double3.dot;
import static uk.ac.manchester.tornado.api.types.vectors.Double3.sub;

/**
 * This example computes the triangle intersections for 100k triangle pairs.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.TriangleIntersections
 * </code>
 *
 */
public class TriangleIntersections {

    public static void computeRayTriangleIntersections(KernelContext context, VectorDouble3 v10s, VectorDouble3 v11s, VectorDouble3 v12s, // Triangle 1 vertices
            VectorDouble3 v20s, VectorDouble3 v21s, VectorDouble3 v22s, // Triangle 2 vertices
            IntArray output) {
        int i = context.globalIdx;

        Double3 v10 = v10s.get(i);
        Double3 v11 = v11s.get(i);
        Double3 v12 = v12s.get(i);
        Double3 v20 = v20s.get(i);
        Double3 v21 = v21s.get(i);
        Double3 v22 = v22s.get(i);

        final double tol = 1e-6;

        Double3 n = Double3.normalise(cross(sub(v11, v10), sub(v12, v10)));
        double d = -dot(v10, n);

        double nTov0 = dot(n, v20) + d;
        double nTov1 = dot(n, v21) + d;
        double nTov2 = dot(n, v22) + d;

        if (TornadoMath.abs(nTov0) < tol && TornadoMath.abs(nTov1) < tol && TornadoMath.abs(nTov2) < tol) {
            output.set(i, 0);
        } else {
            if ((nTov0 > 0) == (nTov1 > 0) && (nTov0 > 0) == (nTov2 > 0)) {
                output.set(i, 0);
                return;
            } else {
                n = Double3.normalise(cross(sub(v21, v20), sub(v22, v20)));
                d = -dot(v20, n);

                nTov0 = dot(n, v10) + d;
                nTov1 = dot(n, v11) + d;
                nTov2 = dot(n, v12) + d;

                if ((nTov0 > 0) == (nTov1 > 0) && (nTov0 > 0) == (nTov2 > 0)) {
                    output.set(i, 0);
                    return;
                }
            }
        }

        output.set(i, 1);
    }

    private static Double3 d(double x, double y, double z) {
        return new Double3(x, y, z);
    }

    public static void main(String[] args) {
        int size = 100_000; // Stress-test with 100k triangle pairs

        VectorDouble3 v10s = new VectorDouble3(size);
        VectorDouble3 v11s = new VectorDouble3(size);
        VectorDouble3 v12s = new VectorDouble3(size);
        VectorDouble3 v20s = new VectorDouble3(size);
        VectorDouble3 v21s = new VectorDouble3(size);
        VectorDouble3 v22s = new VectorDouble3(size);
        IntArray output = new IntArray(size);

        java.util.Random rng = new java.util.Random(42);

        for (int i = 0; i < size; i++) {
            int bucket = i & 3;

            switch (bucket) {
                case 0 -> {
                    // Coplanar in z=0
                    v10s.set(i, d(rng.nextDouble(), rng.nextDouble(), 0));
                    v11s.set(i, d(rng.nextDouble(), rng.nextDouble(), 0));
                    v12s.set(i, d(rng.nextDouble(), rng.nextDouble(), 0));
                    v20s.set(i, d(rng.nextDouble(), rng.nextDouble(), 0));
                    v21s.set(i, d(rng.nextDouble(), rng.nextDouble(), 0));
                    v22s.set(i, d(rng.nextDouble(), rng.nextDouble(), 0));
                }
                case 1 -> {
                    // Triangle 2 above triangle 1’s plane
                    v10s.set(i, d(-1, -1, 0));
                    v11s.set(i, d(1, -1, 0));
                    v12s.set(i, d(0, 1, 0));
                    v20s.set(i, d(-1, -1, 1));
                    v21s.set(i, d(1, -1, 1));
                    v22s.set(i, d(0, 1, 1));
                }
                case 2 -> {
                    // Triangle 1 one side of vertical plane
                    v10s.set(i, d(0.6 + rng.nextDouble(), 0.1, 0));
                    v11s.set(i, d(1.5 + rng.nextDouble(), 0.3, 0));
                    v12s.set(i, d(0.8 + rng.nextDouble(), 1.2, 0));
                    v20s.set(i, d(-1, 0, -1));
                    v21s.set(i, d(-1, 1, 1));
                    v22s.set(i, d(-1, -1, 1));
                }
                case 3 -> {
                    // Genuine intersection
                    v10s.set(i, d(2, 2, 0));
                    v11s.set(i, d(-2, 2, 0));
                    v12s.set(i, d(0, -2, 0));
                    v20s.set(i, d(0, 0, -2));
                    v21s.set(i, d(0, 3, 2));
                    v22s.set(i, d(0, -3, 2));
                }
            }
        }

        // Set up TornadoVM execution
        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.computeRayTriangleIntersections", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, v10s, v11s, v12s, v20s, v21s, v22s) //
                .task("computeRayTriangleIntersections", TriangleIntersections::computeRayTriangleIntersections, context, v10s, v11s, v12s, v20s, v21s, v22s, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException("TornadoVM execution failed", e);
        }

        // Verify and summarize
        int intersecting = 0;
        for (int i = 0; i < size; i++) {
            int result = output.get(i);
            if (result == 1) {
                intersecting++;
            }
        }

        if (size / 4 != intersecting) {
            System.out.printf("[FAIL] Validation failed because intersecting: %d differs from size/4: %d\n", intersecting, size / 4);
        } else {
            System.out.printf("[SUCCESS] Validation succeeded\n");
        }
    }
}
