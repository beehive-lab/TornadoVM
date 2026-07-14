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
package uk.ac.manchester.tornado.cutensor.tests;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cutensor.Cutensor;

/**
 * Benchmark: FP32 tensor contractions via cuTENSOR vs TornadoVM JIT kernels.
 * Two workloads - matmul {@code C[m,n] = A[m,k] B[k,n]} against a naive
 * {@code @Parallel} kernel, and a two-mode contraction
 * {@code C[i,j] = A[i,k,l] B[k,l,j]} (which cuBLAS cannot express in one call)
 * against a JIT four-nested-loop kernel.
 *
 * <p>How to run?</p>
 * <code>
 * tornado -m tornado.cutensor/uk.ac.manchester.tornado.cutensor.tests.BenchmarkCutensor --params="1024 100"
 * </code>
 */
public class BenchmarkCutensor {

    private static final int WARMUP = 20;

    public static void matmul(FloatArray a, FloatArray b, FloatArray c, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += a.get(i * size + k) * b.get(k * size + j);
                }
                c.set(i * size + j, sum);
            }
        }
    }

    /** C[i,j] = sum_{k,l} A[i,k,l] * B[k,l,j], with i=j=size and k=l=cSize. */
    public static void contraction2(FloatArray a, FloatArray b, FloatArray c, int size, int cSize) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < cSize; k++) {
                    for (int l = 0; l < cSize; l++) {
                        sum += a.get((i * cSize + k) * cSize + l) * b.get((k * cSize + l) * size + j);
                    }
                }
                c.set(i * size + j, sum);
            }
        }
    }

    private static double bench(TornadoExecutionPlan plan, int iterations) {
        for (int i = 0; i < WARMUP; i++) {
            plan.execute();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            plan.execute();
        }
        return (System.nanoTime() - start) / (double) iterations;
    }

    private static void report(String name, double nanos, double gflop) {
        System.out.printf("  %-28s %8.3f ms   %8.2f GFLOP/s%n", name, nanos * 1e-6, gflop / (nanos * 1e-9));
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 1024;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 100;
        Random random = new Random(42);

        // ---- matmul as a contraction ----
        FloatArray a = new FloatArray(size * size);
        FloatArray b = new FloatArray(size * size);
        FloatArray cJit = new FloatArray(size * size);
        FloatArray cCutensor = new FloatArray(size * size);
        for (int i = 0; i < size * size; i++) {
            a.set(i, random.nextFloat());
            b.set(i, random.nextFloat());
        }
        double gflopMatmul = 2.0 * size * size * size * 1e-9;

        TaskGraph jit = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("mxm", BenchmarkCutensor::matmul, a, b, cJit, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, cJit);
        TaskGraph ct = new TaskGraph("cutensor") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("contract", Cutensor::cutensorContraction, size, size, size, a, b, cCutensor) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, cCutensor);

        System.out.println("cuTENSOR benchmark: matmul " + size + "x" + size + ", " + iterations + " iterations (+" + WARMUP + " warm-up)");
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(jit.snapshot())) {
            report("JIT @Parallel matmul", bench(plan, iterations), gflopMatmul);
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(ct.snapshot())) {
            report("cuTENSOR contraction", bench(plan, iterations), gflopMatmul);
        }

        // ---- two-mode contraction C[i,j] = A[i,k,l] B[k,l,j] ----
        final int outer = Math.min(size, 256);
        final int contracted = 32;
        FloatArray ta = new FloatArray(outer * contracted * contracted);
        FloatArray tb = new FloatArray(contracted * contracted * outer);
        FloatArray tcJit = new FloatArray(outer * outer);
        FloatArray tcCutensor = new FloatArray(outer * outer);
        for (int i = 0; i < ta.getSize(); i++) {
            ta.set(i, random.nextFloat());
        }
        for (int i = 0; i < tb.getSize(); i++) {
            tb.set(i, random.nextFloat());
        }
        double gflop2 = 2.0 * outer * outer * contracted * contracted * 1e-9;

        TaskGraph jit2 = new TaskGraph("jit2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, ta, tb) //
                .task("c2", BenchmarkCutensor::contraction2, ta, tb, tcJit, outer, contracted) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, tcJit);
        TaskGraph ct2 = new TaskGraph("cutensor2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, ta, tb) //
                .libraryTask("contract2", Cutensor::cutensorContraction2, outer, outer, contracted, contracted, ta, tb, tcCutensor) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, tcCutensor);

        System.out.println("Two-mode contraction C[i,j]=A[i,k,l]B[k,l,j] (i=j=" + outer + ", k=l=" + contracted + "):");
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(jit2.snapshot())) {
            report("JIT @Parallel 4-loop", bench(plan, iterations), gflop2);
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(ct2.snapshot())) {
            report("cuTENSOR contraction2", bench(plan, iterations), gflop2);
        }
    }
}
