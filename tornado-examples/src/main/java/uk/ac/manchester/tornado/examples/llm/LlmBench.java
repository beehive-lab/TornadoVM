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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Shared helpers for the LLM-inference benchmark suite ({@code llm*} mains).
 * Each benchmark self-times its variants (wall-clock around
 * {@code plan.execute()}, after warmup) and appends one CSV row per variant to
 * {@code $TORNADO_LLM_RESULTS/<benchmark>.csv} (default {@code ./results}).
 */
public final class LlmBench {

    public static final int DEFAULT_WARMUP = 10;
    public static final int DEFAULT_ITERATIONS = 30;

    private LlmBench() {
    }

    public static boolean isCudaBackend() {
        int driverIndex = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex();
        TornadoVMBackendType backend = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(driverIndex);
        return backend == TornadoVMBackendType.CUDA;
    }

    /** Wall-clock ms per iteration after warmup. */
    public static double timeMs(TornadoExecutionPlan plan, int warmup, int iters) {
        for (int i = 0; i < warmup; i++) {
            plan.execute();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            plan.execute();
        }
        return (System.nanoTime() - t0) / 1e6 / iters;
    }

    public static double gflopsGemm(int m, int n, int k, double ms) {
        return (2.0 * m * n * k * 1e-9) / (ms * 1e-3);
    }

    /** Appends one result row; creates the file with a header on first write. */
    public static void csv(String benchmark, String variant, String dtype, String shape, double ms, double gflops, String note) {
        Path dir = Paths.get(System.getenv().getOrDefault("TORNADO_LLM_RESULTS", "results"));
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(benchmark + ".csv");
            boolean fresh = !Files.exists(file);
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                if (fresh) {
                    out.println("benchmark,variant,dtype,shape,ms_per_iter,gflops,note");
                }
                out.printf("%s,%s,%s,%s,%.4f,%.1f,%s%n", benchmark, variant, dtype, shape, ms, gflops, note == null ? "" : note);
            }
        } catch (IOException e) {
            System.err.println("[LlmBench] could not write CSV: " + e.getMessage());
        }
    }

    public static void report(String benchmark, String variant, String dtype, String shape, double ms, double gflops, String note) {
        System.out.printf("  %-28s %-6s %10.3f ms/iter %10.1f GFLOP/s  %s%n", variant, dtype, ms, gflops, note == null ? "" : note);
        csv(benchmark, variant, dtype, shape, ms, gflops, note);
    }

    public static HalfFloatArray randomFp16(int size, long seed, float lo, float hi) {
        Random rng = new Random(seed);
        HalfFloatArray arr = new HalfFloatArray(size);
        float range = hi - lo;
        for (int i = 0; i < size; i++) {
            arr.set(i, new HalfFloat(lo + rng.nextFloat() * range));
        }
        return arr;
    }

    public static FloatArray randomFp32(int size, long seed, float lo, float hi) {
        Random rng = new Random(seed);
        FloatArray arr = new FloatArray(size);
        float range = hi - lo;
        for (int i = 0; i < size; i++) {
            arr.set(i, lo + rng.nextFloat() * range);
        }
        return arr;
    }

    public static FloatArray toFp32(HalfFloatArray src) {
        FloatArray out = new FloatArray(src.getSize());
        for (int i = 0; i < src.getSize(); i++) {
            out.set(i, src.get(i).getFloat32());
        }
        return out;
    }

    /** Max relative error against a reference FloatArray (denominator clamped to 1). */
    public static float maxRelError(FloatArray got, FloatArray ref) {
        float maxRel = 0.0f;
        for (int i = 0; i < ref.getSize(); i++) {
            float denom = Math.max(Math.abs(ref.get(i)), 1.0f);
            maxRel = Math.max(maxRel, Math.abs(ref.get(i) - got.get(i)) / denom);
        }
        return maxRel;
    }

    public static float maxRelError(HalfFloatArray got, FloatArray ref) {
        float maxRel = 0.0f;
        for (int i = 0; i < ref.getSize(); i++) {
            float denom = Math.max(Math.abs(ref.get(i)), 1.0f);
            maxRel = Math.max(maxRel, Math.abs(ref.get(i) - got.get(i).getFloat32()) / denom);
        }
        return maxRel;
    }

    public static String checkTol(String name, float maxRel, float tol) {
        String verdict = maxRel <= tol ? "OK" : "FAILED";
        System.out.printf("  [%s] validation %s (max rel err %.4f, tol %.2g)%n", name, verdict, maxRel, tol);
        return verdict + " maxRel=" + String.format("%.4f", maxRel);
    }
}
