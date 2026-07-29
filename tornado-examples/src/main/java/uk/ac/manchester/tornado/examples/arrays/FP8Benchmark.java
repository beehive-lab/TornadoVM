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
package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Dequantization microbenchmark: FP8 (1 byte/element, {@link ByteArray} + {@link FP8} decode)
 * versus FP16 ({@link HalfFloatArray}). Both kernels stream N weights and expand them to
 * {@code float}; the read traffic is the dominant cost, and FP8 moves half the bytes, so a
 * memory-bound dequant should be about twice as fast.
 *
 * <p>Run (CUDA backend):</p>
 * <pre>
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.FP8Benchmark [N] [iters]
 *   # profiled:  tornado --enableProfiler console -m ... FP8Benchmark
 *   # nsys:      nsys profile -o fp8 tornado -m ... FP8Benchmark
 * </pre>
 */
public class FP8Benchmark {

    /**
     * Dequantize-and-sum an E4M3 (one byte/element) array. Each of {@code partial.getSize()} threads
     * strides over the input, so the whole array is read but only a small partial-sum vector is
     * written - the timed cost is the input read traffic (plus the FP8 decode arithmetic), not the
     * output transfer.
     */
    public static void sumFP8(ByteArray in, FloatArray partial) {
        for (@Parallel int t = 0; t < partial.getSize(); t++) {
            float s = 0.0f;
            for (int i = t; i < in.getSize(); i += partial.getSize()) {
                s += FP8.e4m3ToFloat(in.get(i));
            }
            partial.set(t, s);
        }
    }

    /** Expand-and-sum an FP16 (two bytes/element) array (hardware {@code __half2float}). */
    public static void sumFP16(HalfFloatArray in, FloatArray partial) {
        for (@Parallel int t = 0; t < partial.getSize(); t++) {
            float s = 0.0f;
            for (int i = t; i < in.getSize(); i += partial.getSize()) {
                s += in.get(i).getFloat32();
            }
            partial.set(t, s);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 32 * 1024 * 1024;
        int iters = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        int warmup = 10;

        // Deterministic weight-like values in the FP8 range.
        int threads = 65536; // partial-sum lanes; the output vector is tiny (256 KB)
        java.util.Random rng = new java.util.Random(42);
        ByteArray fp8 = new ByteArray(n);
        HalfFloatArray fp16 = new HalfFloatArray(n);
        FloatArray out = new FloatArray(threads);
        for (int i = 0; i < n; i++) {
            float v = (rng.nextFloat() - 0.5f) * 4.0f;
            fp8.set(i, FP8.e4m3FromFloat(v));
            fp16.set(i, new HalfFloat(v));
        }

        TaskGraph gFp8 = new TaskGraph("fp8") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, fp8) //
                .task("sum", FP8Benchmark::sumFP8, fp8, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        TaskGraph gFp16 = new TaskGraph("fp16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, fp16) //
                .task("sum", FP8Benchmark::sumFP16, fp16, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        double fp8Ms;
        double fp16Ms;
        try (TornadoExecutionPlan p8 = new TornadoExecutionPlan(gFp8.snapshot()); //
                TornadoExecutionPlan p16 = new TornadoExecutionPlan(gFp16.snapshot())) {
            fp8Ms = timeKernel(p8, warmup, iters);
            fp16Ms = timeKernel(p16, warmup, iters);
        }

        // Effective read bandwidth: element bytes streamed per second (weights are the read traffic).
        double fp8GBs = (double) n * 1 / (fp8Ms * 1e-3) / 1e9;
        double fp16GBs = (double) n * 2 / (fp16Ms * 1e-3) / 1e9;

        System.out.printf("%nFP8 vs FP16 dequant  (N=%,d elements, %d iters)%n", n, iters);
        System.out.println("-------------------------------------------------------------");
        System.out.printf("  FP8  (1 B/elem): %7.3f ms/iter   %6.1f GB/s   weights %,d B%n", fp8Ms, fp8GBs, (long) n);
        System.out.printf("  FP16 (2 B/elem): %7.3f ms/iter   %6.1f GB/s   weights %,d B%n", fp16Ms, fp16GBs, (long) n * 2);
        System.out.printf("  speedup (FP16/FP8): %.2fx   memory saved: %.0f%%%n", fp16Ms / fp8Ms, 50.0);
        System.exit(0);
    }

    private static double timeKernel(TornadoExecutionPlan plan, int warmup, int iters) {
        for (int i = 0; i < warmup; i++) {
            plan.execute();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            plan.execute();
        }
        long t1 = System.nanoTime();
        return (t1 - t0) / 1e6 / iters;
    }
}
