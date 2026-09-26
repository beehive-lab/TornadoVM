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
package uk.ac.manchester.tornado.mlx.benchmarks;

import static uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation.compare;
import static uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation.grid1D;
import static uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation.groups;
import static uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation.jit;
import static uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation.randomInts;
import static uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation.wants;

import java.util.Random;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxConv;
import uk.ac.manchester.tornado.mlx.MlxFft;
import uk.ac.manchester.tornado.mlx.MlxIndex;
import uk.ac.manchester.tornado.mlx.MlxLinalg;
import uk.ac.manchester.tornado.mlx.MlxMath;
import uk.ac.manchester.tornado.mlx.MlxReduce;
import uk.ac.manchester.tornado.mlx.MlxSort;
import uk.ac.manchester.tornado.mlx.benchmarks.MlxBenchmarks.TaskAdder;
import uk.ac.manchester.tornado.mlx.jit.JitConv;
import uk.ac.manchester.tornado.mlx.jit.JitFft;
import uk.ac.manchester.tornado.mlx.jit.JitIndex;
import uk.ac.manchester.tornado.mlx.jit.JitLinalg;
import uk.ac.manchester.tornado.mlx.jit.JitMath;
import uk.ac.manchester.tornado.mlx.jit.JitReduce;
import uk.ac.manchester.tornado.mlx.jit.JitScan;
import uk.ac.manchester.tornado.mlx.jit.JitSort;

/**
 * The Tier 2 cases of {@link OpEvaluation}: every Tier 2 MLX operation against its KernelContext
 * baseline, at shapes typical of its use. Multi-kernel JIT baselines (two-pass reductions, the
 * global sort, axis-by-axis FFTs, copy-then-scatter updates) add all their tasks for each
 * benchmarked operation.
 */
final class Tier2Cases {

    private static final int T = 256;
    private static final int LARGE = 1 << 24;

    private Tier2Cases() {
    }

    static void all() {
        math();
        reduce();
        scan();
        sort();
        index();
        conv();
        linalg();
        fft();
    }

    private static String regime(int n) {
        return n <= 8192 ? "decode" : "large";
    }

    private static FloatArray uniform(int n, float lo, float hi, long seed) {
        Random r = new Random(seed);
        FloatArray a = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, lo + (hi - lo) * r.nextFloat());
        }
        return a;
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    // ---------------------------------------------------------------- element-wise math

    private static void unary(String op, float lo, float hi, LibraryTask2<FloatArray, FloatArray> mlx, Task4<KernelContext, FloatArray, FloatArray, Integer> jit,
            JavaTierReferences.Unary java) {
        for (int n : new int[] { 4096, LARGE }) {
            FloatArray a = uniform(n, lo, hi, 11);
            FloatArray out = new FloatArray(n);
            compare("math", op, Integer.toString(n), regime(n), "f32", "JitMath#" + op, new Object[] { a }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, jit, new KernelContext(), a, out, n)), 8.0 * n, n, () -> java.run(a, out));
        }
    }

    private static void binary(String op, float lo, float hi, float bLo, float bHi, LibraryTask3<FloatArray, FloatArray, FloatArray> mlx,
            Task5<KernelContext, FloatArray, FloatArray, FloatArray, Integer> jit, JavaTierReferences.Binary java) {
        for (int n : new int[] { 4096, LARGE }) {
            FloatArray a = uniform(n, lo, hi, 12);
            FloatArray b = uniform(n, bLo, bHi, 13);
            FloatArray c = new FloatArray(n);
            compare("math", op, Integer.toString(n), regime(n), "f32", "JitMath#" + op, new Object[] { a, b }, new Object[] { c }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, b, c), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, jit, new KernelContext(), a, b, c, n)), 12.0 * n, n, () -> java.run(a, b, c));
        }
    }

    private static void math() {
        if (!wants("math")) {
            return;
        }
        unary("abs", -10, 10, MlxMath::abs, JitMath::abs, JavaTierReferences::abs);
        unary("arccos", -1, 1, MlxMath::arccos, JitMath::arccos, JavaTierReferences::arccos);
        unary("arccosh", 1, 10, MlxMath::arccosh, JitMath::arccosh, JavaTierReferences::arccosh);
        unary("arcsin", -1, 1, MlxMath::arcsin, JitMath::arcsin, JavaTierReferences::arcsin);
        unary("arcsinh", -10, 10, MlxMath::arcsinh, JitMath::arcsinh, JavaTierReferences::arcsinh);
        unary("arctan", -10, 10, MlxMath::arctan, JitMath::arctan, JavaTierReferences::arctan);
        unary("arctanh", -0.99f, 0.99f, MlxMath::arctanh, JitMath::arctanh, JavaTierReferences::arctanh);
        unary("ceil", -10, 10, MlxMath::ceil, JitMath::ceil, JavaTierReferences::ceil);
        unary("cos", -10, 10, MlxMath::cos, JitMath::cos, JavaTierReferences::cos);
        unary("cosh", -5, 5, MlxMath::cosh, JitMath::cosh, JavaTierReferences::cosh);
        unary("degrees", -10, 10, MlxMath::degrees, JitMath::degrees, JavaTierReferences::degrees);
        unary("erfinv", -0.99f, 0.99f, MlxMath::erfinv, JitMath::erfinv, JavaTierReferences::erfinv);
        unary("expm1", -5, 5, MlxMath::expm1, JitMath::expm1, JavaTierReferences::expm1);
        unary("floor", -10, 10, MlxMath::floor, JitMath::floor, JavaTierReferences::floor);
        unary("log", 0.01f, 10, MlxMath::log, JitMath::log, JavaTierReferences::log);
        unary("log10", 0.01f, 10, MlxMath::log10, JitMath::log10, JavaTierReferences::log10);
        unary("log1p", -0.9f, 10, MlxMath::log1p, JitMath::log1p, JavaTierReferences::log1p);
        unary("log2", 0.01f, 10, MlxMath::log2, JitMath::log2, JavaTierReferences::log2);
        unary("radians", -10, 10, MlxMath::radians, JitMath::radians, JavaTierReferences::radians);
        unary("reciprocal", 0.5f, 10, MlxMath::reciprocal, JitMath::reciprocal, JavaTierReferences::reciprocal);
        unary("sign", -10, 10, MlxMath::sign, JitMath::sign, JavaTierReferences::sign);
        unary("sin", -10, 10, MlxMath::sin, JitMath::sin, JavaTierReferences::sin);
        unary("sinh", -5, 5, MlxMath::sinh, JitMath::sinh, JavaTierReferences::sinh);
        unary("tan", -1.5f, 1.5f, MlxMath::tan, JitMath::tan, JavaTierReferences::tan);
        binary("arctan2", -10, 10, -10, 10, MlxMath::arctan2, JitMath::arctan2, JavaTierReferences::arctan2);
        binary("floor_divide", -10, 10, 0.5f, 10, MlxMath::floorDivide, JitMath::floorDivide, JavaTierReferences::floorDivide);
        binary("logaddexp", -10, 10, -10, 10, MlxMath::logaddexp, JitMath::logaddexp, JavaTierReferences::logaddexp);
        binary("power", 0.1f, 5, -3, 3, MlxMath::power, JitMath::power, JavaTierReferences::power);
        binary("remainder", -10, 10, 0.5f, 10, MlxMath::remainder, JitMath::remainder, JavaTierReferences::remainder);
        for (int n : new int[] { 4096, LARGE }) {
            FloatArray a = uniform(n, -100, 100, 14);
            FloatArray b = uniform(n, 0.5f, 10, 15);
            FloatArray o1 = new FloatArray(n);
            FloatArray o2 = new FloatArray(n);
            ByteArray cond = new ByteArray(n);
            for (int i = 0; i < n; i++) {
                cond.set(i, (byte) (i % 3 == 0 ? 1 : 0));
            }
            compare("math", "round", Integer.toString(n), regime(n), "f32", "JitMath#round", new Object[] { a }, new Object[] { o1 }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxMath::round, a, o1, 2), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitMath::round, new KernelContext(), a, o1, n, 2)), 8.0 * n, 3.0 * n, //
                    () -> JavaTierReferences.round(a, o1, 2));
            compare("math", "divmod", Integer.toString(n), regime(n), "f32", "JitMath#divmod", new Object[] { a, b }, new Object[] { o1, o2 }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxMath::divmod, a, b, o1, o2), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitMath::divmod, new KernelContext(), a, b, o1, o2, n)), 16.0 * n, 3.0 * n, //
                    () -> JavaTierReferences.divmod(a, b, o1, o2));
            compare("math", "clip", Integer.toString(n), regime(n), "f32", "JitMath#clip", new Object[] { a }, new Object[] { o1 }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxMath::clip, a, o1, -20f, 30f), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitMath::clip, new KernelContext(), a, o1, n, -20f, 30f)), 8.0 * n, 2.0 * n, //
                    () -> JavaTierReferences.clip(a, o1, -20f, 30f));
            compare("math", "where", Integer.toString(n), regime(n), "f32", "JitMath#where", new Object[] { cond, a, b }, new Object[] { o1 }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxMath::where, cond, a, b, o1), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitMath::where, new KernelContext(), cond, a, b, o1, n)), 13.0 * n, n, //
                    () -> JavaTierReferences.where(cond, a, b, o1));
        }
    }

    // ---------------------------------------------------------------- reductions

    /** {form, outer, len1, len2, inner}: whole, rows, strided columns. */
    private static final int[][] REDUCE_SHAPES = { { 0, 1, LARGE, 1, 1 }, { 1, 512, 4096, 1, 1 }, { 2, 64, 64, 64, 64 } };

    private static String reduceShape(int[] s) {
        return switch (s[0]) {
            case 0 -> Integer.toString(s[2]);
            case 1 -> s[1] + "x" + s[2];
            default -> s[1] + "x" + s[2] + "x" + s[3] + "x" + s[4] + " (axes 1,2)";
        };
    }

    private static String reduceRegime(int[] s) {
        return switch (s[0]) {
            case 0 -> "whole";
            case 1 -> "rows";
            default -> "strided";
        };
    }

    /** JIT tasks for sum-family reductions of x viewed as [outer, len, inner]. */
    private static TaskAdder jitReduce(FloatArray x, FloatArray out, int outer, int len, int inner, int op, boolean mean) {
        int outputs = outer * inner;
        float scale = mean ? 1.0f / len : 1.0f;
        if (outputs == 1 && len > 16 * T) {
            return (g, gs, gn, t) -> {
                FloatArray partials = new FloatArray(JitReduce.PARTIAL_GROUPS);
                g.task(t + "p", JitReduce::reducePartial, new KernelContext(), x, partials, len, op);
                g.task(t + "m", JitReduce::reduceRows, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS, op, scale);
                gs.addWorkerGrid(gn + "." + t + "p", groups(JitReduce.PARTIAL_GROUPS, T));
                gs.addWorkerGrid(gn + "." + t + "m", groups(1, T));
            };
        } else if (inner == 1) {
            return jit(() -> groups(outer, T), (g, gs, gn, t) -> g.task(t, JitReduce::reduceRows, new KernelContext(), x, out, len, op, scale));
        }
        return jit(() -> grid1D(outputs), (g, gs, gn, t) -> g.task(t, JitReduce::reduceColumns, new KernelContext(), x, out, outputs, len, inner, op, scale));
    }

    private static TaskAdder jitLogsumexp(FloatArray x, FloatArray out, int outer, int len, int inner) {
        int outputs = outer * inner;
        if (outputs == 1 && len > 16 * T) {
            return (g, gs, gn, t) -> {
                FloatArray partials = new FloatArray(2 * JitReduce.PARTIAL_GROUPS);
                g.task(t + "p", JitReduce::logsumexpPartial, new KernelContext(), x, partials, len);
                g.task(t + "m", JitReduce::logsumexpMerge, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS);
                gs.addWorkerGrid(gn + "." + t + "p", groups(JitReduce.PARTIAL_GROUPS, T));
                gs.addWorkerGrid(gn + "." + t + "m", groups(1, T));
            };
        }
        return jit(() -> groups(outputs, T), (g, gs, gn, t) -> g.task(t, JitReduce::logsumexp, new KernelContext(), x, out, len, inner));
    }

    private static TaskAdder jitVariance(FloatArray x, FloatArray out, int outer, int len, int inner, boolean std) {
        int outputs = outer * inner;
        int sq = std ? 1 : 0;
        if (outputs == 1 && len > 16 * T) {
            return (g, gs, gn, t) -> {
                FloatArray partials = new FloatArray(3 * JitReduce.PARTIAL_GROUPS);
                g.task(t + "p", JitReduce::variancePartial, new KernelContext(), x, partials, len);
                g.task(t + "m", JitReduce::varianceMerge, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS, len, 0, sq);
                gs.addWorkerGrid(gn + "." + t + "p", groups(JitReduce.PARTIAL_GROUPS, T));
                gs.addWorkerGrid(gn + "." + t + "m", groups(1, T));
            };
        }
        return jit(() -> groups(outputs, T), (g, gs, gn, t) -> g.task(t, JitReduce::variance, new KernelContext(), x, out, len, inner, 0, sq));
    }

    private static TaskAdder jitAllAny(FloatArray x, ByteArray out, int outer, int len, int inner, int op) {
        int outputs = outer * inner;
        if (outputs == 1 && len > 16 * T) {
            return (g, gs, gn, t) -> {
                FloatArray partials = new FloatArray(JitReduce.PARTIAL_GROUPS);
                g.task(t + "p", JitReduce::reducePartial, new KernelContext(), x, partials, len, op);
                g.task(t + "m", JitReduce::allAny, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS, 1, op);
                gs.addWorkerGrid(gn + "." + t + "p", groups(JitReduce.PARTIAL_GROUPS, T));
                gs.addWorkerGrid(gn + "." + t + "m", groups(1, T));
            };
        }
        return jit(() -> groups(outputs, T), (g, gs, gn, t) -> g.task(t, JitReduce::allAny, new KernelContext(), x, out, len, inner, op));
    }

    private static void reduceCase(String op, String kernel, int[] s, FloatArray x, Object out, TaskAdder mlx, TaskAdder jit, Runnable java) {
        int n = s[1] * s[2] * s[3] * s[4];
        compare("reduce", op, reduceShape(s), reduceRegime(s), "f32", kernel, new Object[] { x }, new Object[] { out }, mlx, jit, 4.0 * n, n, java);
    }

    private static void reduce() {
        if (!wants("reduce")) {
            return;
        }
        String[] names = { "sum", "prod", "max", "min", "mean" };
        int[] codes = { JitReduce.SUM, JitReduce.PROD, JitReduce.MAX, JitReduce.MIN, JitReduce.SUM };
        for (int[] s : REDUCE_SHAPES) {
            int outer = s[1];
            int len = s[2] * s[3];
            int inner = s[4];
            FloatArray x = uniform(outer * len * inner, 0.9999f, 1.0001f, 21);
            FloatArray out = new FloatArray(outer * inner);
            ByteArray bytes = new ByteArray(outer * inner);
            IntArray idx = new IntArray(outer * inner);
            for (int k = 0; k < names.length; k++) {
                String op = names[k];
                int code = codes[k];
                boolean mean = op.equals("mean");
                TaskAdder mlx = switch (op) {
                    case "sum" -> s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::sum, x, out)
                            : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::sumAxis, x, out, outer, len, inner)
                                    : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::sumAxes, x, out, s[1], s[2], s[3], s[4]);
                    case "prod" -> s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::prod, x, out)
                            : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::prodAxis, x, out, outer, len, inner)
                                    : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::prodAxes, x, out, s[1], s[2], s[3], s[4]);
                    case "max" -> s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::max, x, out)
                            : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::maxAxis, x, out, outer, len, inner)
                                    : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::maxAxes, x, out, s[1], s[2], s[3], s[4]);
                    case "min" -> s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::min, x, out)
                            : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::minAxis, x, out, outer, len, inner)
                                    : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::minAxes, x, out, s[1], s[2], s[3], s[4]);
                    default -> s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::mean, x, out)
                            : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::meanAxis, x, out, outer, len, inner)
                                    : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::meanAxes, x, out, s[1], s[2], s[3], s[4]);
                };
                Runnable java = switch (op) {
                    case "sum" -> () -> JavaTierReferences.sum(x, out, outer, len, inner);
                    case "prod" -> () -> JavaTierReferences.prod(x, out, outer, len, inner);
                    case "max" -> () -> JavaTierReferences.max(x, out, outer, len, inner);
                    case "min" -> () -> JavaTierReferences.min(x, out, outer, len, inner);
                    default -> () -> JavaTierReferences.mean(x, out, outer, len, inner);
                };
                reduceCase(op, "JitReduce#reduce", s, x, out, mlx, jitReduce(x, out, outer, len, inner, code, mean), java);
            }
            TaskAdder lse = s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::logsumexp, x, out)
                    : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::logsumexpAxis, x, out, outer, len, inner)
                            : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::logsumexpAxes, x, out, s[1], s[2], s[3], s[4]);
            reduceCase("logsumexp", "JitReduce#logsumexp", s, x, out, lse, jitLogsumexp(x, out, outer, len, inner), //
                    () -> JavaTierReferences.logsumexp(x, out, outer, len, inner));
            TaskAdder var = s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::var, x, out, 0)
                    : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::varAxis, x, out, outer, len, inner, 0)
                            : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::varAxes, x, out, s[1], s[2], s[3], s[4], 0);
            reduceCase("var", "JitReduce#variance", s, x, out, var, jitVariance(x, out, outer, len, inner, false), //
                    () -> JavaTierReferences.var(x, out, outer, len, inner, 0));
            TaskAdder std = s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::std, x, out, 0)
                    : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::stdAxis, x, out, outer, len, inner, 0)
                            : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::stdAxes, x, out, s[1], s[2], s[3], s[4], 0);
            reduceCase("std", "JitReduce#variance", s, x, out, std, jitVariance(x, out, outer, len, inner, true), //
                    () -> JavaTierReferences.std(x, out, outer, len, inner, 0));
            TaskAdder all = s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::all, x, bytes)
                    : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::allAxis, x, bytes, outer, len, inner)
                            : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::allAxes, x, bytes, s[1], s[2], s[3], s[4]);
            reduceCase("all", "JitReduce#allAny", s, x, bytes, all, jitAllAny(x, bytes, outer, len, inner, JitReduce.ALL), //
                    () -> JavaTierReferences.all(x, bytes, outer, len, inner));
            TaskAdder any = s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::any, x, bytes)
                    : s[0] == 1 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::anyAxis, x, bytes, outer, len, inner)
                            : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::anyAxes, x, bytes, s[1], s[2], s[3], s[4]);
            reduceCase("any", "JitReduce#allAny", s, x, bytes, any, jitAllAny(x, bytes, outer, len, inner, JitReduce.ANY), //
                    () -> JavaTierReferences.any(x, bytes, outer, len, inner));
            if (s[0] != 2) {
                // argmin has no axes form; the JIT baseline reduces each output in one threadgroup.
                int aLen = s[0] == 0 ? 151936 : len;
                FloatArray xa = s[0] == 0 ? uniform(aLen, -1, 1, 22) : x;
                int[] as = s[0] == 0 ? new int[] { 0, 1, aLen, 1, 1 } : s;
                TaskAdder am = s[0] == 0 ? (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::argmin, xa, idx)
                        : (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::argminAxis, xa, idx, outer, len, inner);
                reduceCase("argmin", "JitReduce#argmin", as, xa, idx, am, jit(() -> groups(s[0] == 0 ? 1 : outer * inner, T), (g, gs, gn, t) -> g.task(t, JitReduce::argmin,
                        new KernelContext(), xa, idx, aLen, s[0] == 0 ? 1 : inner)), () -> JavaTierReferences.argmin(xa, idx, s[0] == 0 ? 1 : outer, aLen, s[0] == 0 ? 1 : inner));
            }
        }
        for (int[] s : new int[][] { { 1, 512, 1024, 1, 1 }, { 2, 64, 256, 1, 64 } }) {
            int outer = s[1];
            int len = s[2];
            int inner = s[4];
            FloatArray x = uniform(outer * len * inner, -1, 1, 23);
            FloatArray out = new FloatArray(outer * inner);
            compare("reduce", "median", s[0] == 1 ? outer + "x" + len : outer + "x" + len + "x" + inner, s[0] == 1 ? "rows" : "strided", "f32", "JitReduce#median", new Object[] { x },
                    new Object[] { out }, (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::median, x, out, outer, len, inner), //
                    jit(() -> groups(outer * inner, T), (g, gs, gn, t) -> g.task(t, JitReduce::median, new KernelContext(), x, out, len, inner, nextPowerOfTwo(len))), 4.0 * x.getSize(),
                    x.getSize(), () -> JavaTierReferences.median(x, out, outer, len, inner));
        }
    }

    // ---------------------------------------------------------------- scans

    private static void scan() {
        if (!wants("scan")) {
            return;
        }
        String[] names = { "cumsum", "cumprod", "cummax", "cummin", "logcumsumexp" };
        int[] codes = { JitScan.SUM, JitScan.PROD, JitScan.MAX, JitScan.MIN, JitScan.LOGADDEXP };
        for (int[] s : new int[][] { { 512, 4096, 1 }, { 1, 1 << 20, 1 }, { 16, 4096, 64 } }) {
            int outer = s[0];
            int len = s[1];
            int inner = s[2];
            FloatArray x = uniform(outer * len * inner, 0.9999f, 1.0001f, 31);
            FloatArray out = new FloatArray(x.getSize());
            String shape = outer + "x" + len + (inner > 1 ? "x" + inner : "");
            String regime = inner > 1 ? "strided" : outer == 1 ? "one row" : "rows";
            for (int k = 0; k < names.length; k++) {
                String op = names[k];
                int code = codes[k];
                TaskAdder mlx = switch (op) {
                    case "cumsum" -> (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::cumsum, x, out, outer, len, inner, false, true);
                    case "cumprod" -> (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::cumprod, x, out, outer, len, inner, false, true);
                    case "cummax" -> (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::cummax, x, out, outer, len, inner, false, true);
                    case "cummin" -> (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::cummin, x, out, outer, len, inner, false, true);
                    default -> (g, gs, gn, t) -> g.libraryTask(t, MlxReduce::logcumsumexp, x, out, outer, len, inner, false, true);
                };
                TaskAdder j = inner == 1 //
                        ? jit(() -> groups(outer, JitScan.THREADS), (g, gs, gn, t) -> g.task(t, JitScan::scanRows, new KernelContext(), x, out, len, code, 0, 1)) //
                        : jit(() -> grid1D(outer * inner), (g, gs, gn, t) -> g.task(t, JitScan::scanColumns, new KernelContext(), x, out, outer * inner, len, inner, code, 0, 1));
                Runnable java = switch (op) {
                    case "cumsum" -> () -> JavaTierReferences.cumsum(x, out, outer, len, inner);
                    case "cumprod" -> () -> JavaTierReferences.cumprod(x, out, outer, len, inner);
                    case "cummax" -> () -> JavaTierReferences.cummax(x, out, outer, len, inner);
                    case "cummin" -> () -> JavaTierReferences.cummin(x, out, outer, len, inner);
                    default -> () -> JavaTierReferences.logcumsumexp(x, out, outer, len, inner);
                };
                compare("scan", op, shape, regime, "f32", inner == 1 ? "JitScan#scanRows" : "JitScan#scanColumns", new Object[] { x }, new Object[] { out }, mlx, j, 8.0 * x.getSize(),
                        x.getSize(), java);
            }
        }
    }

    // ---------------------------------------------------------------- sort and partition

    /** JIT tasks sorting a whole array with the global bitonic sort. */
    private static TaskAdder jitGlobalSort(FloatArray x, FloatArray values, IntArray indices, int n, boolean writeValues, boolean writeIndices) {
        int padded = nextPowerOfTwo(n);
        int wv = writeValues ? 1 : 0;
        int wi = writeIndices ? 1 : 0;
        return (g, gs, gn, t) -> {
            FloatArray keys = new FloatArray(padded);
            IntArray idx = new IntArray(padded);
            g.task(t + "pad", JitSort::pad, new KernelContext(), x, keys, idx, n, padded);
            gs.addWorkerGrid(gn + "." + t + "pad", grid1D(padded));
            g.task(t + "blk", JitSort::sortBlocks, new KernelContext(), keys, idx);
            gs.addWorkerGrid(gn + "." + t + "blk", groups(padded / JitSort.BLOCK, JitSort.THREADS));
            for (int k = 2 * JitSort.BLOCK; k <= padded; k <<= 1) {
                for (int j = k >> 1; j >= JitSort.BLOCK; j >>= 1) {
                    String name = t + "g" + k + "_" + j;
                    g.task(name, JitSort::mergeGlobal, new KernelContext(), keys, idx, padded, k, j);
                    gs.addWorkerGrid(gn + "." + name, grid1D(padded / 2));
                }
                String name = t + "b" + k;
                g.task(name, JitSort::mergeBlocks, new KernelContext(), keys, idx, k);
                gs.addWorkerGrid(gn + "." + name, groups(padded / JitSort.BLOCK, JitSort.THREADS));
            }
            g.task(t + "unpad", JitSort::unpad, new KernelContext(), keys, idx, values, indices, n, wv, wi);
            gs.addWorkerGrid(gn + "." + t + "unpad", grid1D(n));
        };
    }

    private static void sort() {
        if (!wants("sort")) {
            return;
        }
        sortAxisCases();
        sortCases();
    }

    private static void sortAxisCases() {
        final int rows = 512;
        final int len = 2048;
        FloatArray x = uniform(rows * len, -1, 1, 41);
        FloatArray vals = new FloatArray(x.getSize());
        IntArray idx = new IntArray(x.getSize());
        FloatArray dummyValues = new FloatArray(1);
        IntArray dummyIndices = new IntArray(1);
        String shape = rows + "x" + len;
        TaskAdder jv = jit(() -> groups(rows, JitSort.THREADS), (g, gs, gn, t) -> g.task(t, JitSort::sortSlices, new KernelContext(), x, vals, dummyIndices, len, 1, len, 1, 0));
        TaskAdder ji = jit(() -> groups(rows, JitSort.THREADS), (g, gs, gn, t) -> g.task(t, JitSort::sortSlices, new KernelContext(), x, dummyValues, idx, len, 1, len, 0, 1));
        double bytes = 8.0 * x.getSize();
        compare("sort", "sortAxis", shape, "rows", "f32", "JitSort#sortSlices", new Object[] { x }, new Object[] { vals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::sortAxis, x, vals, rows, len, 1), jv, bytes, x.getSize(), //
                () -> JavaTierReferences.sortAxis(x, vals, rows, len, 1));
        compare("sort", "argsortAxis", shape, "rows", "f32", "JitSort#sortSlices", new Object[] { x }, new Object[] { idx }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::argsortAxis, x, idx, rows, len, 1), ji, bytes, x.getSize(), //
                () -> JavaTierReferences.argsortAxis(x, idx, rows, len, 1));
        compare("sort", "partitionAxis", shape, "rows", "f32", "JitSort#sortSlices", new Object[] { x }, new Object[] { vals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::partitionAxis, x, vals, rows, len, 1, len / 2), jv, bytes, x.getSize(), //
                () -> JavaTierReferences.partitionAxis(x, vals, rows, len, 1, len / 2));
        compare("sort", "argpartitionAxis", shape, "rows", "f32", "JitSort#sortSlices", new Object[] { x }, new Object[] { idx }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::argpartitionAxis, x, idx, rows, len, 1, len / 2), ji, bytes, x.getSize(), //
                () -> JavaTierReferences.argpartitionAxis(x, idx, rows, len, 1, len / 2));
    }

    private static void sortCases() {
        final int n = 1 << 16;
        FloatArray x = uniform(n, -1, 1, 42);
        FloatArray vals = new FloatArray(n);
        IntArray idx = new IntArray(n);
        FloatArray dummyValues = new FloatArray(1);
        IntArray dummyIndices = new IntArray(1);
        String shape = Integer.toString(n);
        compare("sort", "sort", shape, "whole", "f32", "JitSort#global", new Object[] { x }, new Object[] { vals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::sort, x, vals), jitGlobalSort(x, vals, dummyIndices, n, true, false), 8.0 * n, n, 2, //
                () -> JavaTierReferences.sort(x, vals));
        compare("sort", "argsort", shape, "whole", "f32", "JitSort#global", new Object[] { x }, new Object[] { idx }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::argsort, x, idx), jitGlobalSort(x, dummyValues, idx, n, false, true), 8.0 * n, n, 2, //
                () -> JavaTierReferences.argsort(x, idx));
        compare("sort", "partition", shape, "whole", "f32", "JitSort#global", new Object[] { x }, new Object[] { vals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::partition, x, vals, n / 2), jitGlobalSort(x, vals, dummyIndices, n, true, false), 8.0 * n, n, 2, //
                () -> JavaTierReferences.partition(x, vals, n / 2));
        compare("sort", "argpartition", shape, "whole", "f32", "JitSort#global", new Object[] { x }, new Object[] { idx }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxSort::argpartition, x, idx, n / 2), jitGlobalSort(x, dummyValues, idx, n, false, true), 8.0 * n, n, 2, //
                () -> JavaTierReferences.argpartition(x, idx, n / 2));
    }

    // ---------------------------------------------------------------- indexing

    /** A copy of x into out followed by an update task: the JIT form of every copy-and-modify operation. */
    private static TaskAdder copyThen(FloatArray x, FloatArray out, int updateThreads, TaskAdder update) {
        return (g, gs, gn, t) -> {
            g.task(t + "c", JitIndex::copy, new KernelContext(), x, out, x.getSize());
            gs.addWorkerGrid(gn + "." + t + "c", grid1D(x.getSize()));
            update.add(g, gs, gn, t + "u");
            gs.addWorkerGrid(gn + "." + t + "u", grid1D(updateThreads));
        };
    }

    private static void index() {
        if (!wants("index")) {
            return;
        }
        final int rows = 4096;
        final int cols = 4096;
        FloatArray x = uniform(rows * cols, 1, 2, 51);
        FloatArray out = new FloatArray(rows * cols);
        int n = rows * cols;
        takeCases(x, n);
        // Embedding lookup: rows of an 8192 x 4096 table.
        final int vocab = 8192;
        final int dim = 4096;
        FloatArray table = uniform(vocab * dim, -1, 1, 53);
        for (int tokens : new int[] { 1, 512 }) {
            IntArray ids = new IntArray(tokens);
            for (int i = 0; i < tokens; i++) {
                ids.set(i, (i * 7919) % vocab);
            }
            FloatArray emb = new FloatArray(tokens * dim);
            compare("index", "takeAxis", tokens + " of " + vocab + "x" + dim, tokens == 1 ? "decode" : "prefill", "f32", "JitIndex#takeAxis", new Object[] { table, ids },
                    new Object[] { emb }, (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::takeAxis, table, ids, emb, 1, vocab, dim), //
                    jit(() -> grid1D(tokens * dim), (g, gs, gn, t) -> g.task(t, JitIndex::takeAxis, new KernelContext(), table, ids, emb, 1, vocab, tokens, dim)), 8.0 * tokens * dim,
                    0);
        }
        takeAlongAxisCases(rows, x);
        gatherCases(rows, cols, x, out, n);
        gatherRowsCases(rows, cols, x, out, n);
        sliceCases(rows, cols, x, out, n);
        maskedScatterCases(x, out, n);
        gatherMmCases(x);
    }

    private static void takeCases(FloatArray x, int n) {
        final int count = 1 << 20;
        IntArray idx = randomInts(count, 52);
        for (int i = 0; i < count; i++) {
            idx.set(i, Math.floorMod(idx.get(i), n));
        }
        FloatArray o = new FloatArray(count);
        compare("index", "take", count + " of " + n, "large", "f32", "JitIndex#take", new Object[] { x, idx }, new Object[] { o }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::take, x, idx, o), //
                jit(() -> grid1D(count), (g, gs, gn, t) -> g.task(t, JitIndex::take, new KernelContext(), x, idx, o, count)), 12.0 * count, 0);
    }

    private static void takeAlongAxisCases(int rows, FloatArray x) {
        final int outer = 512;
        final int len = 4096;
        final int m = 64;
        FloatArray xs = uniform(outer * len, 1, 2, 54);
        IntArray along = new IntArray(outer * m);
        IntArray distinct = new IntArray(outer * m);
        for (int o = 0; o < outer; o++) {
            for (int i = 0; i < m; i++) {
                along.set(o * m + i, (o * 131 + i * 977) % len);
                distinct.set(o * m + i, (o + i * 61) % len);
            }
        }
        FloatArray vals = uniform(outer * m, -1, 1, 55);
        FloatArray o1 = new FloatArray(outer * m);
        FloatArray o2 = new FloatArray(outer * len);
        String shape = outer + "x" + len + ", " + m + " per row";
        compare("index", "takeAlongAxis", shape, "rows", "f32", "JitIndex#takeAlongAxis", new Object[] { xs, along }, new Object[] { o1 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::takeAlongAxis, xs, along, o1, outer, len, m, 1), //
                jit(() -> grid1D(outer * m), (g, gs, gn, t) -> g.task(t, JitIndex::takeAlongAxis, new KernelContext(), xs, along, o1, outer, len, m, 1)), 12.0 * outer * m, 0);
        compare("index", "putAlongAxis", shape, "rows", "f32", "JitIndex#copy+putAlongAxis", new Object[] { xs, distinct, vals }, new Object[] { o2 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::putAlongAxis, xs, distinct, vals, o2, outer, len, m, 1), //
                copyThen(xs, o2, outer * m, (g, gs, gn, t) -> g.task(t, JitIndex::putAlongAxis, new KernelContext(), distinct, vals, o2, outer, len, m, 1, JitIndex.SET)),
                8.0 * outer * len, 0);
        compare("index", "scatterAddAxis", shape, "rows", "f32", "JitIndex#copy+putAlongAxis", new Object[] { xs, along, vals }, new Object[] { o2 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterAddAxis, xs, along, vals, o2, outer, len, m, 1), //
                copyThen(xs, o2, outer * m, (g, gs, gn, t) -> g.task(t, JitIndex::putAlongAxis, new KernelContext(), along, vals, o2, outer, len, m, 1, JitIndex.ADD)),
                8.0 * outer * len, 0);
    }

    private static void gatherCases(int rows, int cols, FloatArray x, FloatArray out, int n) {
        final int count = 1 << 20;
        IntArray ri = new IntArray(count);
        IntArray ci = new IntArray(count);
        IntArray rd = new IntArray(count);
        IntArray cd = new IntArray(count);
        Random r = new Random(56);
        for (int i = 0; i < count; i++) {
            ri.set(i, r.nextInt(rows));
            ci.set(i, r.nextInt(cols));
            int flat = (int) ((long) i * 15 % n);
            rd.set(i, flat / cols);
            cd.set(i, flat % cols);
        }
        FloatArray pts = new FloatArray(count);
        FloatArray ups = uniform(count, 0.5f, 1.5f, 57);
        compare("index", "gather", count + " points of " + rows + "x" + cols, "large", "f32", "JitIndex#gatherPoints", new Object[] { x, ri, ci }, new Object[] { pts }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::gather, x, ri, ci, pts, rows, cols), //
                jit(() -> grid1D(count), (g, gs, gn, t) -> g.task(t, JitIndex::gatherPoints, new KernelContext(), x, ri, ci, pts, cols, count)), 16.0 * count, 0);
        String[] ops = { "scatter", "scatterAdd", "scatterMax", "scatterMin", "scatterProd" };
        int[] codes = { JitIndex.SET, JitIndex.ADD, JitIndex.MAX, JitIndex.MIN, JitIndex.PROD };
        for (int k = 0; k < ops.length; k++) {
            int code = codes[k];
            IntArray sr = code == JitIndex.ADD ? ri : rd;
            IntArray sc = code == JitIndex.ADD ? ci : cd;
            TaskAdder mlx = switch (code) {
                case JitIndex.SET -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatter, x, sr, sc, ups, out, rows, cols);
                case JitIndex.ADD -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterAdd, x, sr, sc, ups, out, rows, cols);
                case JitIndex.MAX -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterMax, x, sr, sc, ups, out, rows, cols);
                case JitIndex.MIN -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterMin, x, sr, sc, ups, out, rows, cols);
                default -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterProd, x, sr, sc, ups, out, rows, cols);
            };
            compare("index", ops[k], count + " points into " + rows + "x" + cols, "large", "f32", "JitIndex#copy+scatterPoints", new Object[] { x, sr, sc, ups },
                    new Object[] { out }, mlx, copyThen(x, out, count, (g, gs, gn, t) -> g.task(t, JitIndex::scatterPoints, new KernelContext(), sr, sc, ups, out, cols, count,
                            code)), 8.0 * n + 16.0 * count, 0);
        }
    }

    private static void gatherRowsCases(int rows, int cols, FloatArray x, FloatArray out, int n) {
        final int windows = 64;
        final int sliceRows = 16;
        IntArray starts = new IntArray(windows);
        for (int i = 0; i < windows; i++) {
            starts.set(i, (i * 61) % (rows - sliceRows));
        }
        FloatArray w = new FloatArray(windows * sliceRows * cols);
        compare("index", "gatherRows", windows + " windows of " + sliceRows + " rows", "prefill", "f32", "JitIndex#gatherRows", new Object[] { x, starts }, new Object[] { w }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::gatherRows, x, starts, w, rows, cols, sliceRows), //
                jit(() -> grid1D(w.getSize()), (g, gs, gn, t) -> g.task(t, JitIndex::gatherRows, new KernelContext(), x, starts, w, cols, windows, sliceRows)), 8.0 * w.getSize(),
                0);
        final int count = 1024;
        IntArray rowIdx = new IntArray(count);
        IntArray rowAdd = new IntArray(count);
        for (int i = 0; i < count; i++) {
            rowIdx.set(i, (i * 7) % rows);
            rowAdd.set(i, (i * 13) % 256);
        }
        FloatArray rowUps = uniform(count * cols, 0.5f, 1.5f, 58);
        String[] ops = { "scatterRows", "scatterAddRows", "scatterMaxRows", "scatterMinRows", "scatterProdRows" };
        int[] codes = { JitIndex.SET, JitIndex.ADD, JitIndex.MAX, JitIndex.MIN, JitIndex.PROD };
        for (int k = 0; k < ops.length; k++) {
            int code = codes[k];
            IntArray ii = code == JitIndex.ADD ? rowAdd : rowIdx;
            TaskAdder mlx = switch (code) {
                case JitIndex.SET -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterRows, x, ii, rowUps, out, rows, cols);
                case JitIndex.ADD -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterAddRows, x, ii, rowUps, out, rows, cols);
                case JitIndex.MAX -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterMaxRows, x, ii, rowUps, out, rows, cols);
                case JitIndex.MIN -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterMinRows, x, ii, rowUps, out, rows, cols);
                default -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::scatterProdRows, x, ii, rowUps, out, rows, cols);
            };
            compare("index", ops[k], count + " rows into " + rows + "x" + cols, "large", "f32", "JitIndex#copy+scatterRows", new Object[] { x, ii, rowUps }, new Object[] { out },
                    mlx, copyThen(x, out, count * cols, (g, gs, gn, t) -> g.task(t, JitIndex::scatterRows, new KernelContext(), ii, rowUps, out, cols, count, code)), 8.0 * n
                            + 8.0 * count * cols, 0);
        }
    }

    private static void sliceCases(int rows, int cols, FloatArray x, FloatArray out, int n) {
        final int outRows = rows / 2;
        final int outCols = cols / 2;
        FloatArray s = new FloatArray(outRows * outCols);
        compare("index", "slice", rows + "x" + cols + " [::2, ::2]", "large", "f32", "JitIndex#slice", new Object[] { x }, new Object[] { s }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::slice, x, s, rows, cols, 0, rows, 2, 0, cols, 2), //
                jit(() -> grid1D(outRows * outCols), (g, gs, gn, t) -> g.task(t, JitIndex::slice, new KernelContext(), x, s, cols, 0, 2, 0, 2, outRows, outCols)), 8.0 * s.getSize(),
                0, () -> JavaTierReferences.slice(x, s, cols, 0, rows, 2, 0, cols, 2));
        // KV cache read and write at a position held on the device.
        final int kvRows = 1024;
        IntArray pos = IntArray.fromElements(2000);
        FloatArray kv = new FloatArray(kvRows * cols);
        compare("index", "sliceRowsAt", kvRows + " rows of " + rows + "x" + cols, "decode", "f32", "JitIndex#sliceRowsAt", new Object[] { x, pos }, new Object[] { kv }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceRowsAt, x, pos, kv, rows, cols, kvRows), //
                jit(() -> grid1D(kvRows * cols), (g, gs, gn, t) -> g.task(t, JitIndex::sliceRowsAt, new KernelContext(), x, pos, kv, cols, kvRows)), 8.0 * kv.getSize(), 0, //
                () -> JavaTierReferences.sliceRowsAt(x, pos, kv, cols, kvRows));
        FloatArray row = uniform(cols, -1, 1, 59);
        compare("index", "sliceUpdateRowsAt", "1 row into " + rows + "x" + cols, "decode", "f32", "JitIndex#copy+sliceUpdateRowsAt", new Object[] { x, row, pos },
                new Object[] { out }, (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceUpdateRowsAt, x, row, pos, out, rows, cols, 1), //
                copyThen(x, out, cols, (g, gs, gn, t) -> g.task(t, JitIndex::sliceUpdateRowsAt, new KernelContext(), row, pos, out, cols, 1)), 8.0 * n, 0);
        final int ub = 1024;
        FloatArray block = uniform(ub * ub, 0.5f, 1.5f, 60);
        String[] ops = { "sliceUpdate", "sliceUpdateAdd", "sliceUpdateMax", "sliceUpdateMin", "sliceUpdateProd" };
        int[] codes = { JitIndex.SET, JitIndex.ADD, JitIndex.MAX, JitIndex.MIN, JitIndex.PROD };
        for (int k = 0; k < ops.length; k++) {
            int code = codes[k];
            TaskAdder mlx = switch (code) {
                case JitIndex.SET -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceUpdate, x, block, out, rows, cols, 512, 512, ub, ub);
                case JitIndex.ADD -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceUpdateAdd, x, block, out, rows, cols, 512, 512, ub, ub);
                case JitIndex.MAX -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceUpdateMax, x, block, out, rows, cols, 512, 512, ub, ub);
                case JitIndex.MIN -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceUpdateMin, x, block, out, rows, cols, 512, 512, ub, ub);
                default -> (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::sliceUpdateProd, x, block, out, rows, cols, 512, 512, ub, ub);
            };
            compare("index", ops[k], ub + "x" + ub + " into " + rows + "x" + cols, "large", "f32", "JitIndex#copy+sliceUpdate", new Object[] { x, block }, new Object[] { out }, mlx,
                    copyThen(x, out, ub * ub, (g, gs, gn, t) -> g.task(t, JitIndex::sliceUpdate, new KernelContext(), block, out, cols, 512, 512, ub, ub, code)), 8.0 * n, 0);
        }
    }

    private static void maskedScatterCases(FloatArray x, FloatArray out, int n) {
        ByteArray mask = new ByteArray(n);
        for (int i = 0; i < n; i++) {
            mask.set(i, (byte) (i % 10 < 3 ? 1 : 0));
        }
        FloatArray src = uniform(n, -1, 1, 61);
        compare("index", "maskedScatter", n + ", 30% masked", "large", "f32", "JitIndex#maskPositions+maskedScatter", new Object[] { x, mask, src }, new Object[] { out }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::maskedScatter, x, mask, src, out), //
                (g, gs, gn, t) -> {
                    IntArray positions = new IntArray(n);
                    g.task(t + "p", JitIndex::maskPositions, new KernelContext(), mask, positions, n);
                    gs.addWorkerGrid(gn + "." + t + "p", groups(1, JitScan.THREADS));
                    g.task(t + "s", JitIndex::maskedScatter, new KernelContext(), x, mask, src, positions, out, n);
                    gs.addWorkerGrid(gn + "." + t + "s", grid1D(n));
                }, 13.0 * n, 0);
    }

    private static void gatherMmCases(FloatArray x) {
        // Mixture-of-experts prefill: 8 routed token blocks against 16 experts.
        final int batches = 8;
        final int experts = 16;
        final int m = 128;
        final int k = 1024;
        final int nn = 1024;
        FloatArray a = uniform(batches * m * k, -1, 1, 62);
        FloatArray b = uniform(experts * k * nn, -1, 1, 63);
        IntArray lhs = IntArray.fromElements(0, 1, 2, 3, 4, 5, 6, 7);
        IntArray rhs = IntArray.fromElements(3, 14, 0, 7, 9, 2, 11, 5);
        FloatArray c = new FloatArray(batches * m * nn);
        compare("index", "gatherMm", batches + "x" + m + "x" + k + " @ " + experts + " experts " + k + "x" + nn, "prefill", "f32", "JitIndex#gatherMm", new Object[] { a, b, lhs,
                rhs }, new Object[] { c }, (g, gs, gn, t) -> g.libraryTask(t, MlxIndex::gatherMm, a, b, lhs, rhs, c, batches, experts, m, k, nn), //
                jit(() -> groups(batches * (m / 32) * (nn / 32), 128), (g, gs, gn, t) -> g.task(t, JitIndex::gatherMm, new KernelContext(), a, b, lhs, rhs, c, m, nn, k)), 4.0
                        * (batches * m * k + batches * k * nn + batches * m * nn), 2.0 * batches * m * nn * k);
    }

    // ---------------------------------------------------------------- convolutions

    private static int[] same(int v) {
        return new int[] { v, v, v };
    }

    private static int[] forward(int n, int[] in, int cin, int[] k, int cout, int stride, int padding, int dims) {
        int[] s = same(1);
        int[] p = same(0);
        for (int a = 3 - dims; a < 3; a++) {
            s[a] = stride;
            p[a] = padding;
        }
        return JitConv.geometry(n, in, cin, k, cout, s, p, p, same(1), same(1), 1, false);
    }

    private static int[] transposed(int n, int[] in, int cin, int[] k, int cout, int stride, int padding, int dims) {
        int[] lo = same(0);
        int[] inDil = same(1);
        for (int a = 3 - dims; a < 3; a++) {
            lo[a] = k[a] - 1 - padding;
            inDil[a] = stride;
        }
        return JitConv.geometry(n, in, cin, k, cout, same(1), lo, lo, same(1), inDil, 1, true);
    }

    interface ConvTask {
        void add(uk.ac.manchester.tornado.api.TaskGraph g, String t, FloatArray x, FloatArray w, FloatArray out);
    }

    private static void convCase(String op, String shape, int[] geometry, ConvTask mlx) {
        int inputs = geometry[0] * geometry[1] * geometry[2] * geometry[3] * geometry[4];
        int weights = geometry[8] * geometry[9] * geometry[10] * geometry[11] * geometry[4];
        int outputs = JitConv.outputs(geometry);
        FloatArray x = uniform(inputs, -1, 1, 71);
        FloatArray w = uniform(weights, -1, 1, 72);
        FloatArray out = new FloatArray(outputs);
        IntArray g = IntArray.fromArray(geometry);
        double flops = 2.0 * outputs * geometry[9] * geometry[10] * geometry[11] * geometry[4];
        compare("conv", op, shape, "cnn", "f32", "JitConv#conv", new Object[] { x, w, g }, new Object[] { out }, (gr, gs, gn, t) -> mlx.add(gr, t, x, w, out), //
                jit(() -> grid1D(outputs), (gr, gs, gn, t) -> gr.task(t, JitConv::conv, new KernelContext(), x, w, out, g, outputs)), 4.0 * (inputs + weights + outputs), flops);
    }

    private static void conv() {
        if (!wants("conv")) {
            return;
        }
        convCase("conv1d", "8x4096x64, k5, 64 out", forward(8, new int[] { 1, 1, 4096 }, 64, new int[] { 1, 1, 5 }, 64, 1, 2, 1), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::conv1d, x, w, o, 8, 4096, 64, 64, 5, 1, 2, 1, 1));
        convCase("conv2d", "1x128x128x64, k3, 64 out", forward(1, new int[] { 1, 128, 128 }, 64, new int[] { 1, 3, 3 }, 64, 1, 1, 2), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::conv2d, x, w, o, 1, 128, 128, 64, 64, 3, 3, 1, 1, 1, 1));
        convCase("conv3d", "1x32x32x32x16, k3, 16 out", forward(1, new int[] { 32, 32, 32 }, 16, new int[] { 3, 3, 3 }, 16, 1, 1, 3), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::conv3d, x, w, o, 1, 32, 32, 32, 16, 16, 3, 3, 3, 1, 1, 1, 1));
        convCase("convTranspose1d", "8x2048x64, k4 s2, 64 out", transposed(8, new int[] { 1, 1, 2048 }, 64, new int[] { 1, 1, 4 }, 64, 2, 1, 1), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::convTranspose1d, x, w, o, 8, 2048, 64, 64, 4, 2, 1, 1, 0, 1));
        convCase("convTranspose2d", "1x64x64x64, k4 s2, 64 out", transposed(1, new int[] { 1, 64, 64 }, 64, new int[] { 1, 4, 4 }, 64, 2, 1, 2), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::convTranspose2d, x, w, o, 1, 64, 64, 64, 64, 4, 4, 2, 1, 1, 0, 1));
        convCase("convTranspose3d", "1x16x16x16x16, k4 s2, 16 out", transposed(1, new int[] { 16, 16, 16 }, 16, new int[] { 4, 4, 4 }, 16, 2, 1, 3), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::convTranspose3d, x, w, o, 1, 16, 16, 16, 16, 16, 4, 4, 4, 2, 1, 1, 0, 1));
        convCase("convGeneral2d", "1x128x128x64, k3, input dilation 2", JitConv.geometry(1, new int[] { 1, 128, 128 }, 64, new int[] { 1, 3, 3 }, 64, same(1), new int[] { 0, 1, 1 },
                new int[] { 0, 1, 1 }, same(1), new int[] { 1, 2, 2 }, 1, false), //
                (g, t, x, w, o) -> g.libraryTask(t, MlxConv::convGeneral2d, x, w, o, 1, 128, 128, 64, 64, 3, 3, 1, 1, 1, 1, 2, 1, false));
    }

    // ---------------------------------------------------------------- linear algebra

    private static FloatArray matrices(int batch, int n, int kind, long seed) {
        // kind 0: diagonally dominant, 1: symmetric positive definite, 2: upper triangular, 3: symmetric.
        Random r = new Random(seed);
        FloatArray a = new FloatArray(batch * n * n);
        for (int b = 0; b < batch; b++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    float v = 2 * r.nextFloat() - 1;
                    if (kind == 2 && j < i) {
                        v = 0;
                    }
                    if (i == j) {
                        v += kind == 0 || kind == 1 ? n : kind == 2 ? 1.5f : 0;
                    }
                    a.set((b * n + i) * n + j, v);
                }
            }
            if (kind == 1 || kind == 3) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < i; j++) {
                        a.set((b * n + i) * n + j, a.get((b * n + j) * n + i));
                    }
                }
            }
        }
        return a;
    }

    private static void linalgCase(String op, String kernel, int batch, int n, Object[] in, Object[] out, TaskAdder mlx, TaskAdder jit, double flops) {
        compare("linalg", op, batch + " x " + n + "x" + n, "batched", "f32", kernel, in, out, mlx, jit, 8.0 * batch * n * n, flops);
    }

    private static void linalg() {
        if (!wants("linalg")) {
            return;
        }
        crossCases();
        final int batch = 256;
        final int n = 32;
        final int nrhs = 8;
        FloatArray general = matrices(batch, n, 0, 84);
        FloatArray spd = matrices(batch, n, 1, 85);
        FloatArray upper = matrices(batch, n, 2, 86);
        FloatArray sym = matrices(batch, n, 3, 87);
        FloatArray rhs = uniform(batch * n * nrhs, -1, 1, 88);
        FloatArray o1 = new FloatArray(batch * n * n);
        FloatArray o2 = new FloatArray(batch * n * n);
        FloatArray o3 = new FloatArray(batch * n * n);
        FloatArray sol = new FloatArray(batch * n * nrhs);
        FloatArray vals = new FloatArray(batch * n);
        FloatArray cvals = new FloatArray(2 * batch * n);
        FloatArray cvecs = new FloatArray(2 * batch * n * n);
        IntArray piv = new IntArray(batch * n);
        IntArray dummyInts = new IntArray(1);
        FloatArray dummyA = new FloatArray(1);
        FloatArray dummyB = new FloatArray(1);
        double cube = (double) batch * n * n * n;
        linalgCase("cholesky", "JitLinalg#cholesky", batch, n, new Object[] { spd }, new Object[] { o1 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::cholesky, spd, o1, batch, n, false), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::cholesky, new KernelContext(), spd, o1, n, 0)), cube / 3);
        linalgCase("choleskyInv", "JitLinalg#choleskyInv", batch, n, new Object[] { upper }, new Object[] { o1 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::choleskyInv, upper, o1, batch, n, true), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::choleskyInv, new KernelContext(), upper, o1, n, 1)), cube);
        linalgCase("triInv", "JitLinalg#triInv", batch, n, new Object[] { upper }, new Object[] { o1 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::triInv, upper, o1, batch, n, true), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::triInv, new KernelContext(), upper, o1, n, 1)), cube / 3);
        linalgCase("inv", "JitLinalg#inv", batch, n, new Object[] { general }, new Object[] { o1 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::inv, general, o1, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::inv, new KernelContext(), general, o1, n)), 2 * cube);
        linalgCase("solve", "JitLinalg#solve", batch, n, new Object[] { general, rhs }, new Object[] { sol }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::solve, general, rhs, sol, batch, n, nrhs), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::solve, new KernelContext(), general, rhs, sol, n, nrhs)), cube);
        linalgCase("solveTriangular", "JitLinalg#solveTriangular", batch, n, new Object[] { upper, rhs }, new Object[] { sol }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::solveTriangular, upper, rhs, sol, batch, n, nrhs, true), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::solveTriangular, new KernelContext(), upper, rhs, sol, n, nrhs, 1)), (double) batch
                        * n * n * nrhs);
        linalgCase("luFactor", "JitLinalg#lu", batch, n, new Object[] { general }, new Object[] { o1, piv }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::luFactor, general, o1, piv, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::lu, new KernelContext(), general, o1, piv, dummyInts, n, 1, 0)), 2 * cube / 3);
        linalgCase("lu", "JitLinalg#lu+splitLu", batch, n, new Object[] { general }, new Object[] { piv, o2, o3 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::lu, general, piv, o2, o3, batch, n), //
                (g, gs, gn, t) -> {
                    FloatArray packed = new FloatArray(batch * n * n);
                    g.task(t + "f", JitLinalg::lu, new KernelContext(), general, packed, dummyInts, piv, n, 0, 1);
                    gs.addWorkerGrid(gn + "." + t + "f", groups(batch, JitLinalg.THREADS));
                    g.task(t + "s", JitLinalg::splitLu, new KernelContext(), packed, o2, o3, batch * n * n, n);
                    gs.addWorkerGrid(gn + "." + t + "s", grid1D(batch * n * n));
                }, 2 * cube / 3);
        linalgCase("qr", "JitLinalg#qr", batch, n, new Object[] { general }, new Object[] { o1, o2 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::qr, general, o1, o2, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::qr, new KernelContext(), general, o1, o2, n)), 8 * cube / 3);
        linalgCase("eigh", "JitLinalg#eigh", batch, n, new Object[] { sym }, new Object[] { vals, o1 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::eigh, sym, vals, o1, batch, n, false), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::eigh, new KernelContext(), sym, vals, o1, n, 0, 1)), 9 * cube);
        linalgCase("eigvalsh", "JitLinalg#eigh", batch, n, new Object[] { sym }, new Object[] { vals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::eigvalsh, sym, vals, batch, n, false), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::eigh, new KernelContext(), sym, vals, dummyA, n, 0, 0)), 4 * cube);
        linalgCase("svd", "JitLinalg#svd", batch, n, new Object[] { general }, new Object[] { o1, vals, o2 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::svd, general, o1, vals, o2, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::svd, new KernelContext(), general, o1, vals, o2, n, 1)), 12 * cube);
        linalgCase("singularValues", "JitLinalg#svd", batch, n, new Object[] { general }, new Object[] { vals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::singularValues, general, vals, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::svd, new KernelContext(), general, dummyA, vals, dummyB, n, 0)), 12 * cube);
        linalgCase("pinv", "JitLinalg#svd+pinvFromSvd", batch, n, new Object[] { general }, new Object[] { o3 }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::pinv, general, o3, batch, n), //
                (g, gs, gn, t) -> {
                    FloatArray u = new FloatArray(batch * n * n);
                    FloatArray s = new FloatArray(batch * n);
                    FloatArray vt = new FloatArray(batch * n * n);
                    g.task(t + "s", JitLinalg::svd, new KernelContext(), general, u, s, vt, n, 1);
                    gs.addWorkerGrid(gn + "." + t + "s", groups(batch, JitLinalg.THREADS));
                    g.task(t + "p", JitLinalg::pinvFromSvd, new KernelContext(), u, s, vt, o3, batch * n * n, n, 1e-6f);
                    gs.addWorkerGrid(gn + "." + t + "p", grid1D(batch * n * n));
                }, 14 * cube);
        linalgCase("eig", "JitLinalg#eig", batch, n, new Object[] { general }, new Object[] { cvals, cvecs }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::eig, general, cvals, cvecs, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::eig, new KernelContext(), general, cvals, cvecs, n, 1)), 25 * cube);
        linalgCase("eigvals", "JitLinalg#eig", batch, n, new Object[] { general }, new Object[] { cvals }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::eigvals, general, cvals, batch, n), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::eig, new KernelContext(), general, cvals, dummyA, n, 0)), 10 * cube);
    }

    private static void crossCases() {
        final int count = 1 << 20;
        FloatArray a = uniform(3 * count, -1, 1, 81);
        FloatArray b = uniform(3 * count, -1, 1, 82);
        FloatArray c = new FloatArray(3 * count);
        compare("linalg", "cross", count + " vectors", "large", "f32", "JitLinalg#crossProduct", new Object[] { a, b }, new Object[] { c }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::cross, a, b, c, count), //
                jit(() -> grid1D(count), (g, gs, gn, t) -> g.task(t, JitLinalg::crossProduct, new KernelContext(), a, b, c, count)), 36.0 * count, 6.0 * count);
        final int rows = 4096;
        final int cols = 4096;
        FloatArray x = uniform(rows * cols, -1, 1, 83);
        FloatArray norms = new FloatArray(rows);
        for (float ord : new float[] { 2f, 3f }) {
            compare("linalg", "norm (ord " + (int) ord + ")", rows + "x" + cols, "rows", "f32", "JitLinalg#normRows", new Object[] { x }, new Object[] { norms }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::norm, x, norms, rows, cols, ord), //
                    jit(() -> groups(rows, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::normRows, new KernelContext(), x, norms, cols, ord)), 4.0 * rows * cols,
                    2.0 * rows * cols);
        }
        compare("linalg", "l2Norm", rows + "x" + cols, "rows", "f32", "JitLinalg#normRows", new Object[] { x }, new Object[] { norms }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::l2Norm, x, norms, rows, cols), //
                jit(() -> groups(rows, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::normRows, new KernelContext(), x, norms, cols, 2f)), 4.0 * rows * cols,
                2.0 * rows * cols);
        final int batch = 256;
        FloatArray fro = new FloatArray(batch);
        compare("linalg", "frobeniusNorm", batch + " x 256x256", "batched", "f32", "JitLinalg#normRows", new Object[] { x }, new Object[] { fro }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxLinalg::frobeniusNorm, x, fro, batch, 256, 256), //
                jit(() -> groups(batch, JitLinalg.THREADS), (g, gs, gn, t) -> g.task(t, JitLinalg::normRows, new KernelContext(), x, fro, 256 * 256, 2f)), 4.0 * rows * cols,
                2.0 * rows * cols);
    }

    // ---------------------------------------------------------------- FFT

    /** One JIT FFT pass over the middle axis of in viewed as [lines / inner, inLen, inner]. */
    private static void fftPass(uk.ac.manchester.tornado.api.TaskGraph g, uk.ac.manchester.tornado.api.GridScheduler gs, String gn, String t, FloatArray in, FloatArray out, int lines,
            int n, int inLen, int outLen, int inner, int inKind, boolean realOut, boolean inverse, float scale) {
        int ro = realOut ? 1 : 0;
        int inv = inverse ? 1 : 0;
        if (n <= JitFft.MAX_N && (n & (n - 1)) == 0) {
            g.task(t, JitFft::fft, new KernelContext(), in, out, n, 31 - Integer.numberOfLeadingZeros(n), inLen, outLen, inner, inKind, ro, inv, scale);
            gs.addWorkerGrid(gn + "." + t, groups(lines, JitFft.THREADS));
        } else {
            g.task(t, JitFft::dft, new KernelContext(), in, out, lines, n, inLen, outLen, inner, inKind, ro, inv, scale);
            gs.addWorkerGrid(gn + "." + t, grid1D(lines * outLen));
        }
    }

    private static void fftCase(String op, String shape, int complexIn, Object[] in, Object[] out, TaskAdder mlx, TaskAdder jit, int points, int lines) {
        double flops = 5.0 * points * (Math.log(points / (double) lines) / Math.log(2));
        compare("fft", op, shape, "signal", "f32", "JitFft#fft", in, out, mlx, jit, 8.0 * points * (complexIn + 1), flops);
    }

    private static void fft() {
        if (!wants("fft")) {
            return;
        }
        final int norm = MlxFft.BACKWARD;
        fftCases(norm);
        fftCases2(norm);
        fftCases22(norm);
        fftshiftCases();
    }

    private static void fftCases(int norm) {
        final int rows = 1024;
        for (int len : new int[] { 1024, 1000 }) {
            FloatArray x = uniform(2 * rows * len, -1, 1, 91);
            FloatArray y = new FloatArray(2 * rows * len);
            String shape = rows + "x" + len + (len == 1000 ? " (not a power of two)" : "");
            fftCase("fft", shape, 1, new Object[] { x }, new Object[] { y }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::fft, x, y, rows, len, len, norm), //
                    (g, gs, gn, t) -> fftPass(g, gs, gn, t, x, y, rows, len, len, len, 1, JitFft.COMPLEX, false, false, 1f), rows * len, rows);
            fftCase("ifft", shape, 1, new Object[] { x }, new Object[] { y }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::ifft, x, y, rows, len, len, norm), //
                    (g, gs, gn, t) -> fftPass(g, gs, gn, t, x, y, rows, len, len, len, 1, JitFft.COMPLEX, false, true, 1f / len), rows * len, rows);
        }
        final int len = 1024;
        final int half = len / 2 + 1;
        FloatArray xr = uniform(rows * len, -1, 1, 92);
        FloatArray spec = uniform(2 * rows * half, -1, 1, 93);
        FloatArray ys = new FloatArray(2 * rows * half);
        FloatArray yr = new FloatArray(rows * len);
        fftCase("rfft", rows + "x" + len, 0, new Object[] { xr }, new Object[] { ys }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::rfft, xr, ys, rows, len, len, norm), //
                (g, gs, gn, t) -> fftPass(g, gs, gn, t, xr, ys, rows, len, len, half, 1, JitFft.REAL, false, false, 1f), rows * len, rows);
        fftCase("irfft", rows + "x" + len, 1, new Object[] { spec }, new Object[] { yr }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::irfft, spec, yr, rows, half, len, norm), //
                (g, gs, gn, t) -> fftPass(g, gs, gn, t, spec, yr, rows, len, half, len, 1, JitFft.HERMITIAN, true, true, 1f / len), rows * len, rows);
    }

    private static void fftCases2(int norm) {
        final int h = 1024;
        final int w = 1024;
        final int wh = w / 2 + 1;
        FloatArray x = uniform(2 * h * w, -1, 1, 94);
        FloatArray y = new FloatArray(2 * h * w);
        FloatArray xr = uniform(h * w, -1, 1, 95);
        FloatArray spec = uniform(2 * h * wh, -1, 1, 96);
        FloatArray ys = new FloatArray(2 * h * wh);
        FloatArray yr = new FloatArray(h * w);
        String shape = "1x" + h + "x" + w;
        for (boolean inverse : new boolean[] { false, true }) {
            fftCase(inverse ? "ifft2" : "fft2", shape, 1, new Object[] { x }, new Object[] { y }, //
                    inverse ? (g, gs, gn, t) -> g.libraryTask(t, MlxFft::ifft2, x, y, 1, h, w, norm) : (g, gs, gn, t) -> g.libraryTask(t, MlxFft::fft2, x, y, 1, h, w, norm), //
                    (g, gs, gn, t) -> {
                        FloatArray tmp = new FloatArray(2 * h * w);
                        fftPass(g, gs, gn, t + "w", x, tmp, h, w, w, w, 1, JitFft.COMPLEX, false, inverse, inverse ? 1f / w : 1f);
                        fftPass(g, gs, gn, t + "h", tmp, y, w, h, h, h, w, JitFft.COMPLEX, false, inverse, inverse ? 1f / h : 1f);
                    }, h * w, 1);
        }
        fftCase("rfft2", shape, 0, new Object[] { xr }, new Object[] { ys }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::rfft2, xr, ys, 1, h, w, norm), //
                (g, gs, gn, t) -> {
                    FloatArray tmp = new FloatArray(2 * h * wh);
                    fftPass(g, gs, gn, t + "w", xr, tmp, h, w, w, wh, 1, JitFft.REAL, false, false, 1f);
                    fftPass(g, gs, gn, t + "h", tmp, ys, wh, h, h, h, wh, JitFft.COMPLEX, false, false, 1f);
                }, h * w, 1);
        fftCase("irfft2", shape, 1, new Object[] { spec }, new Object[] { yr }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::irfft2, spec, yr, 1, h, w, norm), //
                (g, gs, gn, t) -> {
                    FloatArray tmp = new FloatArray(2 * h * wh);
                    fftPass(g, gs, gn, t + "h", spec, tmp, wh, h, h, h, wh, JitFft.COMPLEX, false, true, 1f / h);
                    fftPass(g, gs, gn, t + "w", tmp, yr, h, w, wh, w, 1, JitFft.HERMITIAN, true, true, 1f / w);
                }, h * w, 1);
    }

    private static void fftCases22(int norm) {
        final int d = 64;
        final int h = 64;
        final int w = 64;
        final int wh = w / 2 + 1;
        int points = d * h * w;
        FloatArray x = uniform(2 * points, -1, 1, 97);
        FloatArray y = new FloatArray(2 * points);
        FloatArray xr = uniform(points, -1, 1, 98);
        FloatArray spec = uniform(2 * d * h * wh, -1, 1, 99);
        FloatArray ys = new FloatArray(2 * d * h * wh);
        FloatArray yr = new FloatArray(points);
        String shape = "1x" + d + "x" + h + "x" + w;
        for (boolean inverse : new boolean[] { false, true }) {
            fftCase(inverse ? "ifftn" : "fftn", shape, 1, new Object[] { x }, new Object[] { y }, //
                    inverse ? (g, gs, gn, t) -> g.libraryTask(t, MlxFft::ifftn, x, y, 1, d, h, w, norm) : (g, gs, gn, t) -> g.libraryTask(t, MlxFft::fftn, x, y, 1, d, h, w, norm), //
                    (g, gs, gn, t) -> {
                        FloatArray t1 = new FloatArray(2 * points);
                        FloatArray t2 = new FloatArray(2 * points);
                        fftPass(g, gs, gn, t + "w", x, t1, d * h, w, w, w, 1, JitFft.COMPLEX, false, inverse, inverse ? 1f / w : 1f);
                        fftPass(g, gs, gn, t + "h", t1, t2, d * w, h, h, h, w, JitFft.COMPLEX, false, inverse, inverse ? 1f / h : 1f);
                        fftPass(g, gs, gn, t + "d", t2, y, h * w, d, d, d, h * w, JitFft.COMPLEX, false, inverse, inverse ? 1f / d : 1f);
                    }, points, 1);
        }
        fftCase("rfftn", shape, 0, new Object[] { xr }, new Object[] { ys }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::rfftn, xr, ys, 1, d, h, w, norm), //
                (g, gs, gn, t) -> {
                    FloatArray t1 = new FloatArray(2 * d * h * wh);
                    FloatArray t2 = new FloatArray(2 * d * h * wh);
                    fftPass(g, gs, gn, t + "w", xr, t1, d * h, w, w, wh, 1, JitFft.REAL, false, false, 1f);
                    fftPass(g, gs, gn, t + "h", t1, t2, d * wh, h, h, h, wh, JitFft.COMPLEX, false, false, 1f);
                    fftPass(g, gs, gn, t + "d", t2, ys, h * wh, d, d, d, h * wh, JitFft.COMPLEX, false, false, 1f);
                }, points, 1);
        fftCase("irfftn", shape, 1, new Object[] { spec }, new Object[] { yr }, (g, gs, gn, t) -> g.libraryTask(t, MlxFft::irfftn, spec, yr, 1, d, h, w, norm), //
                (g, gs, gn, t) -> {
                    FloatArray t1 = new FloatArray(2 * d * h * wh);
                    FloatArray t2 = new FloatArray(2 * d * h * wh);
                    fftPass(g, gs, gn, t + "d", spec, t1, h * wh, d, d, d, h * wh, JitFft.COMPLEX, false, true, 1f / d);
                    fftPass(g, gs, gn, t + "h", t1, t2, d * wh, h, h, h, wh, JitFft.COMPLEX, false, true, 1f / h);
                    fftPass(g, gs, gn, t + "w", t2, yr, d * h, w, wh, w, 1, JitFft.HERMITIAN, true, true, 1f / w);
                }, points, 1);
    }

    private static void fftshiftCases() {
        final int rows = 4096;
        final int len = 4096;
        FloatArray x = uniform(rows * len, -1, 1, 100);
        FloatArray y = new FloatArray(rows * len);
        compare("fft", "fftshift", rows + "x" + len, "large", "f32", "JitFft#roll", new Object[] { x }, new Object[] { y }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxFft::fftshift, x, y, rows, len), //
                jit(() -> grid1D(rows * len), (g, gs, gn, t) -> g.task(t, JitFft::roll, new KernelContext(), x, y, rows * len, len, len / 2)), 8.0 * rows * len, 0);
        compare("fft", "ifftshift", rows + "x" + len, "large", "f32", "JitFft#roll", new Object[] { x }, new Object[] { y }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxFft::ifftshift, x, y, rows, len), //
                jit(() -> grid1D(rows * len), (g, gs, gn, t) -> g.task(t, JitFft::roll, new KernelContext(), x, y, rows * len, len, -(len / 2))), 8.0 * rows * len, 0);
        final int n = 1 << 20;
        FloatArray f = new FloatArray(n);
        FloatArray rf = new FloatArray(n / 2 + 1);
        compare("fft", "fftfreq", Integer.toString(n), "large", "f32", "JitFft#frequencies", new Object[] { f }, new Object[] { f }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxFft::fftfreq, f, n, 0.5f), //
                jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitFft::frequencies, new KernelContext(), f, n, n, 0.5f, 0)), 4.0 * n, n);
        compare("fft", "rfftfreq", Integer.toString(n), "large", "f32", "JitFft#frequencies", new Object[] { rf }, new Object[] { rf }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxFft::rfftfreq, rf, n, 0.5f), //
                jit(() -> grid1D(n / 2 + 1), (g, gs, gn, t) -> g.task(t, JitFft::frequencies, new KernelContext(), rf, n / 2 + 1, n, 0.5f, 1)), 2.0 * n, n / 2.0);
    }

}
