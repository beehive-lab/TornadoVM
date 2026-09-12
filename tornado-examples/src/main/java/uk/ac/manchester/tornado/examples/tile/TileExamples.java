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
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Shared plumbing for the tile examples: the backend check, the timing loop, and the result
 * table they all print.
 *
 * <p>
 * <b>About the timings.</b> These are wall-clock milliseconds per iteration, measured after a
 * warm-up execution, and they include JVM-side dispatch of the task graph. At small problem
 * sizes that dispatch dominates and the numbers say more about submission than about the
 * kernels. For kernel time alone, profile with Nsight Systems:
 * </p>
 *
 * <pre>
 * nsys profile --trace=cuda $JAVA_HOME/bin/java @$TORNADOVM_HOME/tornado-argfile \
 *     -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileMatrixMultiply 1024 20
 * nsys stats --report cuda_gpu_kern_sum report1.nsys-rep
 * </pre>
 */
public final class TileExamples {

    private TileExamples() {
    }

    /**
     * CUDA Tile is a CUDA-backend feature. On any other backend the tile task cannot compile,
     * so say why and stop rather than producing a confusing failure.
     */
    static void requireCuda() {
        TornadoVMBackendType backend = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(0);
        if (backend != TornadoVMBackendType.CUDA) {
            System.out.println("This example needs the CUDA backend; the default device reports " + backend + ".");
            System.out.println("It additionally needs CUDA Toolkit 13.3 or newer and driver R580 or newer.");
            System.exit(0);
        }
    }

    /**
     * Runs the graph once to warm up (which is where compilation and the first transfer land),
     * then times {@code iterations} executions.
     *
     * @return milliseconds per iteration, or NaN if the run failed
     */
    static double time(TaskGraph graph, GridScheduler scheduler, int iterations) {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            if (scheduler != null) {
                plan.withGridScheduler(scheduler);
            }
            plan.execute();
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                plan.execute();
            }
            return (System.nanoTime() - start) / 1e6 / iterations;
        } catch (Exception e) {
            System.out.println("  [failed] " + e.getMessage());
            return Double.NaN;
        }
    }

    static double maxError(FloatArray actual, float[] expected) {
        double worst = 0.0;
        for (int i = 0; i < expected.length; i++) {
            worst = Math.max(worst, Math.abs(actual.get(i) - expected[i]));
        }
        return worst;
    }

    static void header() {
        System.out.printf("%-38s %14s %10s %10s %12s   %s%n", "implementation", "ms/iteration", "speedup", "GFLOP/s", "max error", "result");
        System.out.println("-".repeat(100));
    }

    /**
     * One row of the table. {@code gigaFlop} may be zero for kernels whose work is not usefully
     * counted in flops, in which case the column is left blank.
     */
    static void report(String label, double milliseconds, double baseline, double gigaFlop, double error, double tolerance) {
        if (Double.isNaN(milliseconds)) {
            System.out.printf("%-38s %14s%n", label, "did not run");
            return;
        }
        String flops = gigaFlop > 0.0 ? String.format("%10.1f", gigaFlop / (milliseconds / 1e3)) : "         -";
        System.out.printf("%-38s %14.3f %9.1fx %s %12.5f   %s%n", label, milliseconds, baseline / milliseconds, flops, error, error <= tolerance ? "correct" : "WRONG");
    }

    static void footer() {
        System.out.println();
        System.out.println("Speedups are against sequential Java and include JVM-side dispatch, which dominates");
        System.out.println("at small sizes. See the class comment of TileExamples for the nsys recipe that");
        System.out.println("measures kernel time alone. Correctness is checked against the sequential result.");
    }
}
