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
package uk.ac.manchester.tornado.examples.llm;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cutensor.Cutensor;

/**
 * Multi-head attention output projection as an einsum
 * ({@code "s h d, h d e -> s e"}): merge the h*d head outputs straight into the
 * hidden dimension without a reshape/copy. Default s=512, h=32, d=64, e=2048
 * (Llama-3.2-1B). A genuine two-shared-mode tensor contraction - the kind of
 * call cuBLAS cannot express directly but cuTENSOR can
 * ({@code cutensorContraction2}).
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.llm.LlmEinsum [s h d e]
 * </pre>
 */
public class LlmEinsum {

    private static final String BENCH = "llmeinsum";

    /** C[i,j] = sum_{k,l} A[i,k,l] * B[k,l,j] - JIT loop nest. */
    public static void einsumJit(FloatArray a, FloatArray b, FloatArray c, int s, int h, int d, int e) {
        for (@Parallel int i = 0; i < s; i++) {
            for (@Parallel int j = 0; j < e; j++) {
                float sum = 0.0f;
                for (int k = 0; k < h; k++) {
                    for (int l = 0; l < d; l++) {
                        sum += a.get((i * h + k) * d + l) * b.get((k * d + l) * e + j);
                    }
                }
                c.set(i * e + j, sum);
            }
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        if (!LlmBench.isCudaBackend()) {
            System.out.println("LlmEinsum requires the CUDA backend.");
            return;
        }
        final int s = args.length > 0 ? Integer.parseInt(args[0]) : 512;
        final int h = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        final int d = args.length > 2 ? Integer.parseInt(args[2]) : 64;
        final int e = args.length > 3 ? Integer.parseInt(args[3]) : 2048;
        final String shape = s + "x" + h + "x" + d + "x" + e;
        final double gflop = 2.0 * s * (double) h * d * e * 1e-9;
        System.out.printf("%n%s: einsum 's h d, h d e -> s e'  s=%d h=%d d=%d e=%d%n", BENCH, s, h, d, e);

        FloatArray a = LlmBench.randomFp32(s * h * d, 1, -0.5f, 0.5f);
        FloatArray b = LlmBench.randomFp32(h * d * e, 2, -0.5f, 0.5f);
        FloatArray cJit = new FloatArray(s * e);
        FloatArray cCutensor = new FloatArray(s * e);
        FloatArray cCutensor2 = new FloatArray(s * e);

        // 1. JIT loop nest (reference).
        TaskGraph gJit = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("einsum", LlmEinsum::einsumJit, a, b, cJit, s, h, d, e) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cJit);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gJit.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            LlmBench.report(BENCH, "jit-loop-fp32", "fp32", shape, ms, gflop / (ms * 1e-3), "reference");
        }

        // 2. cuTENSOR flat matmul contraction (m=s, n=e, k=h*d).
        TaskGraph gCt = new TaskGraph("ct") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("contract", Cutensor::cutensorContraction, s, e, h * d, a, b, cCutensor) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cCutensor);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gCt.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cutensor-matmul", LlmBench.maxRelError(cCutensor, cJit), 1e-3f);
            LlmBench.report(BENCH, "cutensor-matmul", "fp32", shape, ms, gflop / (ms * 1e-3), v);
        }

        // 3. cuTENSOR two-shared-mode contraction C[i,j] = sum_{k,l} A[i,k,l] B[k,l,j].
        TaskGraph gCt2 = new TaskGraph("ct2") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("contract2", Cutensor::cutensorContraction2, s, e, h, d, a, b, cCutensor2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cCutensor2);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(gCt2.snapshot())) {
            double ms = LlmBench.timeMs(plan, LlmBench.DEFAULT_WARMUP, LlmBench.DEFAULT_ITERATIONS);
            String v = LlmBench.checkTol("cutensor-einsum2", LlmBench.maxRelError(cCutensor2, cJit), 1e-3f);
            LlmBench.report(BENCH, "cutensor-einsum2", "fp32", shape, ms, gflop / (ms * 1e-3), v);
        }

        System.exit(0);
    }
}
