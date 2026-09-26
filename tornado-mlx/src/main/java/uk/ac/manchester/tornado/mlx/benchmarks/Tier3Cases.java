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
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxCreate;
import uk.ac.manchester.tornado.mlx.MlxLogic;
import uk.ac.manchester.tornado.mlx.MlxProducts;
import uk.ac.manchester.tornado.mlx.MlxRandom;
import uk.ac.manchester.tornado.mlx.MlxShape;
import uk.ac.manchester.tornado.mlx.benchmarks.MlxBenchmarks.TaskAdder;
import uk.ac.manchester.tornado.mlx.jit.JitBlas;
import uk.ac.manchester.tornado.mlx.jit.JitCreate;
import uk.ac.manchester.tornado.mlx.jit.JitLinalg;
import uk.ac.manchester.tornado.mlx.jit.JitLogic;
import uk.ac.manchester.tornado.mlx.jit.JitProducts;
import uk.ac.manchester.tornado.mlx.jit.JitQuantized;
import uk.ac.manchester.tornado.mlx.jit.JitRandom;
import uk.ac.manchester.tornado.mlx.jit.JitReduce;
import uk.ac.manchester.tornado.mlx.jit.JitShape;
import uk.ac.manchester.tornado.mlx.jit.JitSort;

/**
 * The Tier 3 cases of {@link OpEvaluation}: every Tier 3 MLX operation (comparisons and logic, array
 * creation, shape manipulation, tensor products, random sampling) against its KernelContext
 * baseline. Operations with no inputs (creation, random) list their output as an input so the graph
 * has something to transfer. Random operations draw from different generators in MLX and the JIT
 * kernels, so their outputs are only statistically alike; {@link OpEvaluation#compare} times both
 * variants and does not compare their outputs.
 */
final class Tier3Cases {

    private static final int T = 256;
    private static final int LARGE = 1 << 24;
    private static final int[] SIZES = { 4096, LARGE };
    private static final int[] SIDES = { 64, 4096 };
    private static final int VOCAB = 151936;
    private static final int SEED = 42;

    private Tier3Cases() {
    }

    static void all() {
        logic();
        create();
        shape();
        products();
        random();
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

    /** The side of a square with n elements. */
    private static int side(int n) {
        return (int) Math.round(Math.sqrt(n));
    }

    /** The side of a cube with n elements. */
    private static int cube(int n) {
        return (int) Math.round(Math.cbrt(n));
    }

    /** Values in [-5, 5) with NaNs and infinities mixed in. */
    private static FloatArray specials(int n, long seed) {
        FloatArray a = uniform(n, -5, 5, seed);
        for (int i = 3; i < n; i += 11) {
            a.set(i, Float.NaN);
        }
        for (int i = 5; i < n; i += 11) {
            a.set(i, Float.POSITIVE_INFINITY);
        }
        for (int i = 7; i < n; i += 11) {
            a.set(i, Float.NEGATIVE_INFINITY);
        }
        return a;
    }

    private static ByteArray randomBytes(int n, int ones, int outOf, long seed) {
        Random r = new Random(seed);
        ByteArray a = new ByteArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, (byte) (r.nextInt(outOf) < ones ? 1 : 0));
        }
        return a;
    }

    // ---------------------------------------------------------------- comparisons and logic

    private static void comparison(String op, LibraryTask3<FloatArray, FloatArray, ByteArray> mlx, int code, JavaTierReferences.Compare java) {
        for (int n : SIZES) {
            FloatArray a = uniform(n, -5, 5, 1);
            FloatArray b = uniform(n, -5, 5, 2);
            ByteArray out = new ByteArray(n);
            compare("logic", op, Integer.toString(n), regime(n), "f32", "JitLogic#compare", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::compare, new KernelContext(), a, b, out, n, code)), 9.0 * n, n, () -> java.run(a, b, out));
        }
    }

    private static void classification(String op, LibraryTask2<FloatArray, ByteArray> mlx, int kind, JavaTierReferences.Classify java) {
        for (int n : SIZES) {
            FloatArray a = specials(n, 3);
            ByteArray out = new ByteArray(n);
            compare("logic", op, Integer.toString(n), regime(n), "f32", "JitLogic#classify", new Object[] { a }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::classify, new KernelContext(), a, out, n, kind)), 5.0 * n, n, () -> java.run(a, out));
        }
    }

    private static void bitwise(String op, LibraryTask3<IntArray, IntArray, IntArray> mlx, int code, boolean shift, JavaTierReferences.Bitwise java) {
        for (int n : SIZES) {
            IntArray a = randomInts(n, 4);
            IntArray b = randomInts(n, 5);
            if (shift) {
                for (int i = 0; i < n; i++) {
                    b.set(i, Math.floorMod(b.get(i), 31));
                }
            }
            IntArray out = new IntArray(n);
            compare("logic", op, Integer.toString(n), regime(n), "i32", "JitLogic#bitwise", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::bitwise, new KernelContext(), a, b, out, n, code)), 12.0 * n, n, () -> java.run(a, b, out));
        }
    }

    private static void logic() {
        if (!wants("logic")) {
            return;
        }
        comparison("equal", MlxLogic::equal, JitLogic.EQUAL, JavaTierReferences::equal);
        comparison("notEqual", MlxLogic::notEqual, JitLogic.NOT_EQUAL, JavaTierReferences::notEqual);
        comparison("greater", MlxLogic::greater, JitLogic.GREATER, JavaTierReferences::greater);
        comparison("greaterEqual", MlxLogic::greaterEqual, JitLogic.GREATER_EQUAL, JavaTierReferences::greaterEqual);
        comparison("less", MlxLogic::less, JitLogic.LESS, JavaTierReferences::less);
        comparison("lessEqual", MlxLogic::lessEqual, JitLogic.LESS_EQUAL, JavaTierReferences::lessEqual);
        classification("isfinite", MlxLogic::isfinite, JitLogic.FINITE, JavaTierReferences::isfinite);
        classification("isinf", MlxLogic::isinf, JitLogic.INF, JavaTierReferences::isinf);
        classification("isnan", MlxLogic::isnan, JitLogic.NAN, JavaTierReferences::isnan);
        classification("isneginf", MlxLogic::isneginf, JitLogic.NEG_INF, JavaTierReferences::isneginf);
        classification("isposinf", MlxLogic::isposinf, JitLogic.POS_INF, JavaTierReferences::isposinf);
        bitwise("bitwiseAnd", MlxLogic::bitwiseAnd, JitLogic.AND, false, JavaTierReferences::bitwiseAnd);
        bitwise("bitwiseOr", MlxLogic::bitwiseOr, JitLogic.OR, false, JavaTierReferences::bitwiseOr);
        bitwise("bitwiseXor", MlxLogic::bitwiseXor, JitLogic.XOR, false, JavaTierReferences::bitwiseXor);
        bitwise("leftShift", MlxLogic::leftShift, JitLogic.LEFT_SHIFT, true, JavaTierReferences::leftShift);
        bitwise("rightShift", MlxLogic::rightShift, JitLogic.RIGHT_SHIFT, true, JavaTierReferences::rightShift);
        closeCases();
        logicalCases();
        valueCases();
    }

    private static void closeCases() {
        final float rtol = 1e-3f;
        final float atol = 1e-5f;
        for (int n : SIZES) {
            // b equals a element for element: allclose and arrayEqual scan the whole array.
            FloatArray a = uniform(n, -5, 5, 6);
            FloatArray b = uniform(n, -5, 5, 6);
            ByteArray mask = new ByteArray(n);
            ByteArray flag = new ByteArray(1);
            String shape = Integer.toString(n);
            compare("logic", "isclose", shape, regime(n), "f32", "JitLogic#isclose", new Object[] { a, b }, new Object[] { mask }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::isclose, a, b, mask, rtol, atol, false), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::isclose, new KernelContext(), a, b, mask, n, rtol, atol, 0)), 9.0 * n, 4.0 * n, //
                    () -> JavaTierReferences.isclose(a, b, mask, rtol, atol, false));
            compare("logic", "allclose", shape, regime(n), "f32", "JitLogic#setFlag+allcloseCheck", new Object[] { a, b }, new Object[] { flag }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::allclose, a, b, flag, rtol, atol, false), //
                    (g, gs, gn, t) -> {
                        g.task(t + "s", JitLogic::setFlag, new KernelContext(), flag, 1);
                        gs.addWorkerGrid(gn + "." + t + "s", grid1D(1));
                        g.task(t + "c", JitLogic::allcloseCheck, new KernelContext(), a, b, flag, n, rtol, atol, 0);
                        gs.addWorkerGrid(gn + "." + t + "c", grid1D(n));
                    }, 8.0 * n, 4.0 * n, () -> JavaTierReferences.allclose(a, b, flag, rtol, atol, false));
            compare("logic", "arrayEqual", shape, regime(n), "f32", "JitLogic#setFlag+arrayEqualCheck", new Object[] { a, b }, new Object[] { flag }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::arrayEqual, a, b, flag, false), //
                    (g, gs, gn, t) -> {
                        g.task(t + "s", JitLogic::setFlag, new KernelContext(), flag, 1);
                        gs.addWorkerGrid(gn + "." + t + "s", grid1D(1));
                        g.task(t + "c", JitLogic::arrayEqualCheck, new KernelContext(), a, b, flag, n, 0);
                        gs.addWorkerGrid(gn + "." + t + "c", grid1D(n));
                    }, 8.0 * n, n, () -> JavaTierReferences.arrayEqual(a, b, flag));
        }
    }

    private static void logicalCases() {
        for (int n : SIZES) {
            ByteArray a = randomBytes(n, 1, 2, 7);
            ByteArray b = randomBytes(n, 1, 2, 8);
            ByteArray out = new ByteArray(n);
            String shape = Integer.toString(n);
            compare("logic", "logicalAnd", shape, regime(n), "bool", "JitLogic#logical", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::logicalAnd, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::logical, new KernelContext(), a, b, out, n, JitLogic.AND)), 3.0 * n, n, //
                    () -> JavaTierReferences.logicalAnd(a, b, out));
            compare("logic", "logicalOr", shape, regime(n), "bool", "JitLogic#logical", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::logicalOr, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::logical, new KernelContext(), a, b, out, n, JitLogic.OR)), 3.0 * n, n, //
                    () -> JavaTierReferences.logicalOr(a, b, out));
            compare("logic", "logicalNot", shape, regime(n), "bool", "JitLogic#logicalNot", new Object[] { a }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::logicalNot, a, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::logicalNot, new KernelContext(), a, out, n)), 2.0 * n, n, //
                    () -> JavaTierReferences.logicalNot(a, out));
        }
    }

    private static void valueCases() {
        for (int n : SIZES) {
            String shape = Integer.toString(n);
            IntArray ia = randomInts(n, 9);
            IntArray io = new IntArray(n);
            compare("logic", "bitwiseInvert", shape, regime(n), "i32", "JitLogic#invert", new Object[] { ia }, new Object[] { io }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::bitwiseInvert, ia, io), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::invert, new KernelContext(), ia, io, n)), 8.0 * n, n, //
                    () -> JavaTierReferences.bitwiseInvert(ia, io));
            FloatArray x = specials(n, 10);
            FloatArray y = new FloatArray(n);
            compare("logic", "nanToNum", shape, regime(n), "f32", "JitLogic#nanToNum", new Object[] { x }, new Object[] { y }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::nanToNum, x, y, 0.5f, 1e30f, -1e30f), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::nanToNum, new KernelContext(), x, y, n, 0.5f, 1e30f, -1e30f)), 8.0 * n, n, //
                    () -> JavaTierReferences.nanToNum(x, y, 0.5f, 1e30f, -1e30f));
            // n complex values, interleaved (re, im).
            FloatArray z = uniform(2 * n, -3, 3, 11);
            FloatArray part = new FloatArray(n);
            FloatArray conj = new FloatArray(2 * n);
            String cshape = n + " complex";
            compare("logic", "real", cshape, regime(n), "c64", "JitLogic#complexPart", new Object[] { z }, new Object[] { part }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::real, z, part), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::complexPart, new KernelContext(), z, part, n, 0)), 12.0 * n, 0, //
                    () -> JavaTierReferences.complexPart(z, part, 0));
            compare("logic", "imag", cshape, regime(n), "c64", "JitLogic#complexPart", new Object[] { z }, new Object[] { part }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::imag, z, part), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::complexPart, new KernelContext(), z, part, n, 1)), 12.0 * n, 0, //
                    () -> JavaTierReferences.complexPart(z, part, 1));
            compare("logic", "conjugate", cshape, regime(n), "c64", "JitLogic#conjugate", new Object[] { z }, new Object[] { conj }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxLogic::conjugate, z, conj), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitLogic::conjugate, new KernelContext(), z, conj, n)), 16.0 * n, n, //
                    () -> JavaTierReferences.conjugate(z, conj));
        }
    }

    // ---------------------------------------------------------------- array creation

    private static void create() {
        if (!wants("create")) {
            return;
        }
        rangeCases();
        fillCases();
        eyeCases();
        windowCases();
        matrixCases();
    }

    private static void rangeCases() {
        for (int n : SIZES) {
            FloatArray out = new FloatArray(n);
            String shape = Integer.toString(n);
            compare("create", "arange", shape, regime(n), "f32", "JitCreate#arange", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::arange, out, 0.0f, (float) n, 1.0f), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::arange, new KernelContext(), out, n, 0.0f, 1.0f)), 4.0 * n, n);
            compare("create", "linspace", shape, regime(n), "f32", "JitCreate#linspace", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::linspace, out, -1.0f, 3.0f), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::linspace, new KernelContext(), out, n, -1.0f, 3.0f)), 4.0 * n, 2.0 * n);
        }
    }

    private static void fillCase(String op, int n, FloatArray in, FloatArray out, float value, TaskAdder mlx) {
        compare("create", op, Integer.toString(n), regime(n), "f32", "JitCreate#fill", new Object[] { in }, new Object[] { out }, mlx, //
                jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::fill, new KernelContext(), out, n, value)), 4.0 * n, 0);
    }

    private static void fillCases() {
        for (int n : SIZES) {
            FloatArray like = uniform(n, -1, 1, 21);
            FloatArray out = new FloatArray(n);
            fillCase("full", n, out, out, 2.5f, (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::full, out, 2.5f));
            fillCase("fullLike", n, like, out, -1.5f, (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::fullLike, like, out, -1.5f));
            fillCase("zeros", n, out, out, 0.0f, (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::zeros, out));
            fillCase("zerosLike", n, like, out, 0.0f, (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::zerosLike, like, out));
            fillCase("ones", n, out, out, 1.0f, (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::ones, out));
            fillCase("onesLike", n, like, out, 1.0f, (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::onesLike, like, out));
        }
    }

    private static void eyeCases() {
        for (int s : SIDES) {
            int n = s * s;
            FloatArray out = new FloatArray(n);
            String shape = s + "x" + s;
            compare("create", "eye", shape, regime(n), "f32", "JitCreate#eye", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::eye, out, s, s, 1), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::eye, new KernelContext(), out, s, s, 1, 0)), 4.0 * n, 0);
            compare("create", "identity", shape, regime(n), "f32", "JitCreate#eye", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::identity, out, s), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::eye, new KernelContext(), out, s, s, 0, 0)), 4.0 * n, 0);
            compare("create", "tri", shape, regime(n), "f32", "JitCreate#eye", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::tri, out, s, s, 0), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::eye, new KernelContext(), out, s, s, 0, 1)), 4.0 * n, 0);
        }
    }

    private static void window(String op, LibraryTask1<FloatArray> mlx, int kind) {
        for (int n : SIZES) {
            FloatArray out = new FloatArray(n);
            compare("create", op, Integer.toString(n), regime(n), "f32", "JitCreate#window", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::window, new KernelContext(), out, n, kind)), 4.0 * n, 6.0 * n);
        }
    }

    private static void windowCases() {
        window("bartlett", MlxCreate::bartlett, JitCreate.BARTLETT);
        window("blackman", MlxCreate::blackman, JitCreate.BLACKMAN);
        window("hamming", MlxCreate::hamming, JitCreate.HAMMING);
        window("hanning", MlxCreate::hanning, JitCreate.HANNING);
    }

    private static void matrixCases() {
        for (int s : SIDES) {
            int n = s * s;
            String shape = s + "x" + s;
            FloatArray x = uniform(s, -1, 1, 22);
            FloatArray y = uniform(s, -1, 1, 23);
            FloatArray gx = new FloatArray(n);
            FloatArray gy = new FloatArray(n);
            compare("create", "meshgrid", s + " x " + s, regime(n), "f32", "JitCreate#meshgrid", new Object[] { x, y }, new Object[] { gx, gy }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::meshgrid, x, y, gx, gy, false), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::meshgrid, new KernelContext(), x, y, gx, gy, s, s, 0)), 8.0 * n, 0);
            FloatArray a = uniform(n, -1, 1, 24);
            FloatArray out = new FloatArray(n);
            FloatArray d = new FloatArray(s);
            FloatArray tr = new FloatArray(1);
            compare("create", "diag", s + " to " + shape, regime(n), "f32", "JitCreate#diag", new Object[] { x }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::diag, x, out, 0), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::diag, new KernelContext(), x, out, s, 0)), 4.0 * n, 0);
            compare("create", "diagonal", shape, regime(n), "f32", "JitCreate#diagonal", new Object[] { a }, new Object[] { d }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::diagonal, a, d, s, s, 0), //
                    jit(() -> grid1D(s), (g, gs, gn, t) -> g.task(t, JitCreate::diagonal, new KernelContext(), a, d, s, 0, s)), 8.0 * s, 0);
            compare("create", "trace", shape, regime(n), "f32", "JitCreate#trace", new Object[] { a }, new Object[] { tr }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::trace, a, tr, s, s, 0), //
                    jit(() -> groups(1, T), (g, gs, gn, t) -> g.task(t, JitCreate::trace, new KernelContext(), a, tr, s, 0, s)), 4.0 * s, s);
            compare("create", "tril", shape, regime(n), "f32", "JitCreate#triangle", new Object[] { a }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::tril, a, out, s, s, 0), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::triangle, new KernelContext(), a, out, s, s, 0, 0)), 8.0 * n, 0);
            compare("create", "triu", shape, regime(n), "f32", "JitCreate#triangle", new Object[] { a }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxCreate::triu, a, out, s, s, 0), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitCreate::triangle, new KernelContext(), a, out, s, s, 0, 1)), 8.0 * n, 0);
        }
    }

    // ---------------------------------------------------------------- shape manipulation

    /** An MLX task over x and out, n elements each. */
    interface Shaped {
        TaskAdder of(FloatArray x, FloatArray out, int n);
    }

    /** A layout-preserving operation, which the JIT baseline performs as a copy. */
    private static void layout(String op, Shaped mlx) {
        for (int n : SIZES) {
            FloatArray x = uniform(n, -1, 1, 31);
            FloatArray out = new FloatArray(n);
            compare("shape", op, Integer.toString(n), regime(n), "f32", "JitShape#copy", new Object[] { x }, new Object[] { out }, mlx.of(x, out, n), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::copy, new KernelContext(), x, out, n)), 8.0 * n, 0, //
                    () -> JavaTierReferences.copy(x, out));
        }
    }

    private static void shape() {
        if (!wants("shape")) {
            return;
        }
        layout("reshape", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::reshape, x, o, side(n), side(n)));
        layout("flatten", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::flatten, x, o, cube(n), cube(n), cube(n)));
        layout("unflatten", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::unflatten, x, o, side(n), side(n)));
        layout("squeeze", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::squeeze, x, o, side(n), side(n)));
        layout("squeezeAxis", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::squeezeAxis, x, o, side(n), side(n)));
        layout("squeezeAxes", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::squeezeAxes, x, o, side(n), side(n)));
        layout("expandDims", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::expandDims, x, o, side(n), side(n)));
        layout("expandDimsAxes", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::expandDimsAxes, x, o, side(n), side(n)));
        layout("atleast1d", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::atleast1d, x, o));
        layout("atleast2d", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::atleast2d, x, o));
        layout("atleast3d", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::atleast3d, x, o));
        layout("contiguous", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::contiguous, x, o));
        layout("copy", (x, o, n) -> (g, gs, gn, t) -> g.libraryTask(t, MlxShape::copy, x, o));
        permuteCases();
        broadcastCases();
        typeCases();
        joinCases();
        splitCases();
        repeatCases();
        rollPadCases();
    }

    private static void permute(String op, int n, FloatArray x, FloatArray out, TaskAdder mlx, int p0, int p1, int p2) {
        int c = cube(n);
        compare("shape", op, c + "x" + c + "x" + c + " (" + p0 + "," + p1 + "," + p2 + ")", regime(n), "f32", "JitShape#permute3", new Object[] { x }, new Object[] { out }, mlx, //
                jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::permute3, new KernelContext(), x, out, c, c, c, p0, p1, p2)), 8.0 * n, 0, //
                () -> JavaTierReferences.permute3(x, out, c, c, c, p0, p1, p2));
    }

    private static void permuteCases() {
        for (int n : SIZES) {
            int c = cube(n);
            FloatArray x = uniform(n, -1, 1, 32);
            FloatArray out = new FloatArray(n);
            permute("transposeAxes", n, x, out, (g, gs, gn, t) -> g.libraryTask(t, MlxShape::transposeAxes, x, out, c, c, c, 2, 0, 1), 2, 0, 1);
            permute("swapaxes", n, x, out, (g, gs, gn, t) -> g.libraryTask(t, MlxShape::swapaxes, x, out, c, c, c, 0, 2), 2, 1, 0);
            permute("moveaxis", n, x, out, (g, gs, gn, t) -> g.libraryTask(t, MlxShape::moveaxis, x, out, c, c, c, 0, 2), 1, 2, 0);
        }
    }

    private static void broadcastCases() {
        for (int s : SIDES) {
            int n = s * s;
            String shape = s + "x" + s;
            FloatArray row = uniform(s, -1, 1, 33);
            FloatArray col = uniform(s, -1, 1, 34);
            FloatArray oa = new FloatArray(n);
            FloatArray ob = new FloatArray(n);
            compare("shape", "broadcastTo", s + " to " + shape, regime(n), "f32", "JitShape#broadcastRows", new Object[] { row }, new Object[] { oa }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::broadcastTo, row, oa, s, s), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::broadcastRows, new KernelContext(), row, oa, s, s)), 4.0 * n, 0, //
                    () -> JavaTierReferences.broadcastTo(row, oa, s, s));
            compare("shape", "broadcastArrays", s + " and " + s + " to " + shape, regime(n), "f32", "JitShape#broadcastPair", new Object[] { row, col }, new Object[] { oa, ob }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::broadcastArrays, row, col, oa, ob, s, s), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::broadcastPair, new KernelContext(), row, col, oa, ob, s, s)), 8.0 * n, 0, //
                    () -> JavaTierReferences.broadcastArrays(row, col, oa, ob, s, s));
            // Every other row and column of x [s, s].
            int h = s / 2;
            FloatArray x = uniform(n, -1, 1, 35);
            FloatArray strided = new FloatArray(h * h);
            compare("shape", "asStrided", shape + " [::2, ::2]", regime(n), "f32", "JitShape#asStrided", new Object[] { x }, new Object[] { strided }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::asStrided, x, strided, h, h, 2 * s, 2, 0), //
                    jit(() -> grid1D(h * h), (g, gs, gn, t) -> g.task(t, JitShape::asStrided, new KernelContext(), x, strided, h, h, 2 * s, 2, 0)), 8.0 * h * h, 0, //
                    () -> JavaTierReferences.asStrided(x, strided, h, h, 2 * s, 2, 0));
        }
    }

    private static void typeCases() {
        for (int n : SIZES) {
            String shape = Integer.toString(n);
            FloatArray x = uniform(n, -100, 100, 36);
            HalfFloatArray half = new HalfFloatArray(n);
            IntArray bits = new IntArray(n);
            IntArray count = new IntArray(1);
            int c = cube(n);
            compare("shape", "astype", shape + " to f16", regime(n), "f32", "JitShape#toHalf", new Object[] { x }, new Object[] { half }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::astype, x, half), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::toHalf, new KernelContext(), x, half, n)), 6.0 * n, n, //
                    () -> JavaTierReferences.astype(x, half));
            compare("shape", "view", shape + " as i32", regime(n), "f32", "JitShape#viewAsInt", new Object[] { x }, new Object[] { bits }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::view, x, bits), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::viewAsInt, new KernelContext(), x, bits, n)), 8.0 * n, 0, //
                    () -> JavaTierReferences.view(x, bits));
            compare("shape", "numberOfElements", c + "x" + c + "x" + c, regime(n), "f32", "JitShape#writeInt", new Object[] { x }, new Object[] { count }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::numberOfElements, x, count, c, c, c), //
                    jit(() -> grid1D(1), (g, gs, gn, t) -> g.task(t, JitShape::writeInt, new KernelContext(), count, c * c)), 4.0, 0);
        }
    }

    private static void joinCases() {
        for (int n : SIZES) {
            int s = side(n);
            int h = n / 2;
            FloatArray a = uniform(h, -1, 1, 37);
            FloatArray b = uniform(h, -1, 1, 38);
            FloatArray out = new FloatArray(n);
            String shape = "2 x " + h;
            compare("shape", "concatenate", shape, regime(n), "f32", "JitShape#concat", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::concatenate, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::concat, new KernelContext(), a, b, out, 1, h, h)), 8.0 * n, 0, //
                    () -> JavaTierReferences.concatenate(a, b, out));
            compare("shape", "concatenateAxis", "2 x " + s + "x" + s / 2 + " (axis 1)", regime(n), "f32", "JitShape#concat", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::concatenateAxis, a, b, out, s, s / 2, s / 2), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::concat, new KernelContext(), a, b, out, s, s / 2, s / 2)), 8.0 * n, 0, //
                    () -> JavaTierReferences.concatenateAxis(a, b, out, s, s / 2, s / 2));
            compare("shape", "stack", shape, regime(n), "f32", "JitShape#concat", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::stack, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::concat, new KernelContext(), a, b, out, 1, h, h)), 8.0 * n, 0, //
                    () -> JavaTierReferences.concatenate(a, b, out));
            compare("shape", "stackAxis", shape + " (axis 1)", regime(n), "f32", "JitShape#interleave", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::stackAxis, a, b, out), //
                    jit(() -> grid1D(h), (g, gs, gn, t) -> g.task(t, JitShape::interleave, new KernelContext(), a, b, out, h)), 8.0 * n, 0, //
                    () -> JavaTierReferences.stackAxis(a, b, out));
        }
    }

    private static void splitCases() {
        for (int n : SIZES) {
            int s = side(n);
            int q = s / 4;
            FloatArray x = uniform(n, -1, 1, 39);
            FloatArray f1 = new FloatArray(n / 2);
            FloatArray f2 = new FloatArray(n / 2);
            FloatArray s1 = new FloatArray(s * q);
            FloatArray s2 = new FloatArray(s * (s - q));
            String shape = s + "x" + s;
            compare("shape", "split", shape + " in halves", regime(n), "f32", "JitShape#split", new Object[] { x }, new Object[] { f1, f2 }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::split, x, f1, f2, s, s), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::split, new KernelContext(), x, f1, f2, s, s, s / 2)), 8.0 * n, 0, //
                    () -> JavaTierReferences.splitSections(x, f1, f2, s, s, s / 2));
            compare("shape", "splitSections", shape + " at " + q, regime(n), "f32", "JitShape#split", new Object[] { x }, new Object[] { s1, s2 }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::splitSections, x, s1, s2, s, s, q), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::split, new KernelContext(), x, s1, s2, s, s, q)), 8.0 * n, 0, //
                    () -> JavaTierReferences.splitSections(x, s1, s2, s, s, q));
        }
    }

    private static void repeatCases() {
        for (int n : SIZES) {
            int s = side(n);
            int h = s / 2;
            FloatArray xh = uniform(n / 2, -1, 1, 40);
            FloatArray xq = uniform(h * h, -1, 1, 41);
            FloatArray out = new FloatArray(n);
            compare("shape", "repeat", n / 2 + " x2", regime(n), "f32", "JitShape#repeatRows", new Object[] { xh }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::repeat, xh, out, 2), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::repeatRows, new KernelContext(), xh, out, n / 2, 1, 2)), 6.0 * n, 0, //
                    () -> JavaTierReferences.repeat(xh, out, 2));
            compare("shape", "repeatAxis", h + "x" + s + " x2 (axis 0)", regime(n), "f32", "JitShape#repeatRows", new Object[] { xh }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::repeatAxis, xh, out, h, s, 2), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::repeatRows, new KernelContext(), xh, out, h, s, 2)), 6.0 * n, 0, //
                    () -> JavaTierReferences.repeatAxis(xh, out, h, s, 2));
            compare("shape", "tile", h + "x" + h + " x(2, 2)", regime(n), "f32", "JitShape#tile", new Object[] { xq }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::tile, xq, out, h, h, 2, 2), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::tile, new KernelContext(), xq, out, h, h, 2, 2)), 5.0 * n, 0, //
                    () -> JavaTierReferences.tile(xq, out, h, h, 2, 2));
        }
    }

    private static void rollPadCases() {
        for (int n : SIZES) {
            int s = side(n);
            int shift = s + 3;
            FloatArray x = uniform(n, -1, 1, 42);
            FloatArray out = new FloatArray(n);
            FloatArray padded = new FloatArray((s + 2) * (s + 2));
            String shape = s + "x" + s;
            compare("shape", "roll", n + " by " + shift, regime(n), "f32", "JitShape#roll", new Object[] { x }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::roll, x, out, shift), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::roll, new KernelContext(), x, out, 1, n, 0, shift)), 8.0 * n, 0, //
                    () -> JavaTierReferences.roll(x, out, shift));
            compare("shape", "rollAxis", shape + " by 3 (axis 1)", regime(n), "f32", "JitShape#roll", new Object[] { x }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::rollAxis, x, out, s, s, 3), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::roll, new KernelContext(), x, out, s, s, 0, 3)), 8.0 * n, 0, //
                    () -> JavaTierReferences.rollAxes(x, out, s, s, 0, 3));
            compare("shape", "rollAxes", shape + " by (1, 3)", regime(n), "f32", "JitShape#roll", new Object[] { x }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::rollAxes, x, out, s, s, 1, 3), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitShape::roll, new KernelContext(), x, out, s, s, 1, 3)), 8.0 * n, 0, //
                    () -> JavaTierReferences.rollAxes(x, out, s, s, 1, 3));
            int p = padded.getSize();
            compare("shape", "pad", shape + " +1 each side", regime(n), "f32", "JitShape#pad", new Object[] { x }, new Object[] { padded }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::pad, x, padded, s, s, 1, 1, 1, 1, 0.0f), //
                    jit(() -> grid1D(p), (g, gs, gn, t) -> g.task(t, JitShape::pad, new KernelContext(), x, padded, s, s, 1, 1, s + 2, s + 2, 0.0f)), 4.0 * (n + p), 0, //
                    () -> JavaTierReferences.pad(x, padded, s, s, 1, 1, 1, 1, 0.0f));
            compare("shape", "padSymmetric", shape + " +1 each side", regime(n), "f32", "JitShape#pad", new Object[] { x }, new Object[] { padded }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxShape::padSymmetric, x, padded, s, s, 1, 0.0f), //
                    jit(() -> grid1D(p), (g, gs, gn, t) -> g.task(t, JitShape::pad, new KernelContext(), x, padded, s, s, 1, 1, s + 2, s + 2, 0.0f)), 4.0 * (n + p), 0, //
                    () -> JavaTierReferences.pad(x, padded, s, s, 1, 1, 1, 1, 0.0f));
        }
    }

    // ---------------------------------------------------------------- tensor products

    private static int gemmGroups(int m, int n) {
        return (m / JitBlas.GEMM_BLOCK) * (n / JitBlas.GEMM_BLOCK);
    }

    private static void products() {
        if (!wants("products")) {
            return;
        }
        einsumCases();
        tensordotCases();
        vectorCases();
        maskedCases();
        hadamardCases();
        fp8Cases();
        qqmmCases();
    }

    private static void einsumCases() {
        for (int[] s : new int[][] { { 16, 128, 128, 128 }, { 8, 512, 512, 512 } }) {
            int batch = s[0];
            int m = s[1];
            int k = s[2];
            int n = s[3];
            FloatArray a = uniform(batch * m * k, -1, 1, 51);
            FloatArray b = uniform(batch * k * n, -1, 1, 52);
            FloatArray c = new FloatArray(batch * m * n);
            compare("products", "einsum", "bij,bjk->bik " + batch + "x" + m + "x" + k + "x" + n, "batched", "f32", "JitProducts#batchedGemm", new Object[] { a, b },
                    new Object[] { c }, (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::einsumBatchedMatmul, a, b, c, batch, m, k, n), //
                    jit(() -> groups(batch * gemmGroups(m, n), JitBlas.GEMM_THREADS), (g, gs, gn, t) -> g.task(t, JitProducts::batchedGemm, new KernelContext(), a, b, c, m, n, k)),
                    4.0 * batch * (m * k + k * n + m * n), 2.0 * batch * m * n * k, () -> JavaTierReferences.batchedMatmul(a, b, c, batch, m, k, n));
        }
    }

    private static void tensordotCases() {
        for (int[] s : new int[][] { { 512, 16, 32, 512 }, { 2048, 32, 64, 2048 } }) {
            int m = s[0];
            int k1 = s[1];
            int k2 = s[2];
            int n = s[3];
            int k = k1 * k2;
            FloatArray a = uniform(m * k, -1, 1, 53);
            FloatArray b = uniform(k * n, -1, 1, 54);
            FloatArray c = new FloatArray(m * n);
            double bytes = 4.0 * (m * k + k * n + m * n);
            double flops = 2.0 * m * n * k;
            TaskAdder j = jit(() -> groups(gemmGroups(m, n), JitBlas.GEMM_THREADS), (g, gs, gn, t) -> g.task(t, JitBlas::gemm, new KernelContext(), a, b, c, m, n, k));
            compare("products", "tensordot", m + "x" + k1 + "x" + k2 + " . " + k1 + "x" + k2 + "x" + n, "prefill", "f32", "JitBlas#gemm", new Object[] { a, b }, new Object[] { c }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::tensordot, a, b, c, m, k1, k2, n), j, bytes, flops, //
                    () -> JavaTierReferences.matmul(a, b, c, m, k, n));
            compare("products", "tensordotAxis", m + "x" + k + "x" + n, "prefill", "f32", "JitBlas#gemm", new Object[] { a, b }, new Object[] { c }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::tensordotAxis, a, b, c, m, k, n), j, bytes, flops, //
                    () -> JavaTierReferences.matmul(a, b, c, m, k, n));
        }
    }

    private static void vectorCases() {
        for (int n : SIZES) {
            FloatArray a = uniform(n, -1, 1, 55);
            FloatArray b = uniform(n, -1, 1, 56);
            FloatArray dot = new FloatArray(1);
            compare("products", "inner", Integer.toString(n), regime(n), "f32", "JitProducts#dotPartial+JitReduce#reduceRows", new Object[] { a, b }, new Object[] { dot }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::inner, a, b, dot), //
                    (g, gs, gn, t) -> {
                        FloatArray partials = new FloatArray(JitReduce.PARTIAL_GROUPS);
                        g.task(t + "p", JitProducts::dotPartial, new KernelContext(), a, b, partials, n);
                        gs.addWorkerGrid(gn + "." + t + "p", groups(JitReduce.PARTIAL_GROUPS, T));
                        g.task(t + "m", JitReduce::reduceRows, new KernelContext(), partials, dot, JitReduce.PARTIAL_GROUPS, JitReduce.SUM, 1.0f);
                        gs.addWorkerGrid(gn + "." + t + "m", groups(1, T));
                    }, 8.0 * n, 2.0 * n, () -> JavaTierReferences.inner(a, b, dot));
        }
        for (int s : SIDES) {
            int n = s * s;
            FloatArray a = uniform(s, -1, 1, 57);
            FloatArray b = uniform(s, -1, 1, 58);
            FloatArray out = new FloatArray(n);
            compare("products", "outer", s + " x " + s, regime(n), "f32", "JitProducts#outer", new Object[] { a, b }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::outer, a, b, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitProducts::outer, new KernelContext(), a, b, out, s, s)), 4.0 * n, n, //
                    () -> JavaTierReferences.outer(a, b, out));
            // kron of two r x r matrices, r * r = s, into [s, s].
            int r = (int) Math.round(Math.sqrt(s));
            FloatArray ka = uniform(r * r, -1, 1, 59);
            FloatArray kb = uniform(r * r, -1, 1, 60);
            compare("products", "kron", r + "x" + r + " (x) " + r + "x" + r, regime(n), "f32", "JitProducts#kron", new Object[] { ka, kb }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::kron, ka, kb, out, r, r, r, r), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitProducts::kron, new KernelContext(), ka, kb, out, r, r, r, r)), 4.0 * n, n, //
                    () -> JavaTierReferences.kron(ka, kb, out, r, r, r, r));
        }
    }

    private static void maskedCases() {
        final int bs = 32;
        for (int d : new int[] { 512, 2048 }) {
            int blocks = d / bs;
            FloatArray a = uniform(d * d, -1, 1, 61);
            FloatArray b = uniform(d * d, -1, 1, 62);
            ByteArray maskOut = randomBytes(blocks * blocks, 3, 4, 63);
            ByteArray maskLhs = randomBytes(blocks * blocks, 3, 4, 64);
            ByteArray maskRhs = randomBytes(blocks * blocks, 3, 4, 65);
            FloatArray c = new FloatArray(d * d);
            compare("products", "blockMaskedMm", d + "x" + d + "x" + d + " bs" + bs, "prefill", "f32", "JitProducts#blockMaskedGemm", new Object[] { a, b, maskOut, maskLhs,
                    maskRhs }, new Object[] { c }, (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::blockMaskedMm, a, b, maskOut, maskLhs, maskRhs, c, d, d, d, bs), //
                    jit(() -> groups(gemmGroups(d, d), JitBlas.GEMM_THREADS), (g, gs, gn, t) -> g.task(t, JitProducts::blockMaskedGemm, new KernelContext(), a, b, maskOut, maskLhs,
                            maskRhs, c, d, d, d)), 12.0 * d * d, 2.0 * d * d * d, () -> JavaTierReferences.blockMaskedMm(a, b, maskOut, maskLhs, maskRhs, c, d, d, d, bs));
        }
        for (int[] s : new int[][] { { 128, 1024, 128, 4 }, { 512, 4096, 512, 8 } }) {
            int m = s[0];
            int k = s[1];
            int n = s[2];
            int count = s[3];
            IntArray segments = new IntArray(2 * count);
            for (int i = 0; i < count; i++) {
                segments.set(2 * i, i * k / count);
                segments.set(2 * i + 1, (i + 1) * k / count);
            }
            FloatArray a = uniform(m * k, -1, 1, 66);
            FloatArray b = uniform(k * n, -1, 1, 67);
            FloatArray out = new FloatArray(count * m * n);
            compare("products", "segmentedMm", m + "x" + k + "x" + n + ", " + count + " segments", "prefill", "f32", "JitProducts#segmentedGemm", new Object[] { a, b, segments },
                    new Object[] { out }, (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::segmentedMm, a, b, segments, out, m, k, n), //
                    jit(() -> grid1D(count * m * n), (g, gs, gn, t) -> g.task(t, JitProducts::segmentedGemm, new KernelContext(), a, b, segments, out, count, m, n, k)),
                    4.0 * (m * k + k * n + count * m * n), 2.0 * m * n * k, () -> JavaTierReferences.segmentedMm(a, b, segments, out, m, k, n));
        }
    }

    private static void hadamardCases() {
        final int n = JitProducts.HADAMARD_MAX;
        final float scale = (float) (1.0 / Math.sqrt(n));
        for (int rows : new int[] { 1, 4096 }) {
            FloatArray x = uniform(rows * n, -1, 1, 68);
            FloatArray out = new FloatArray(rows * n);
            double log2 = 31 - Integer.numberOfLeadingZeros(n);
            compare("products", "hadamardTransform", rows + "x" + n, rows == 1 ? "decode" : "large", "f32", "JitProducts#hadamard", new Object[] { x }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::hadamardTransform, x, out, rows, n, scale), //
                    jit(() -> groups(rows, T), (g, gs, gn, t) -> g.task(t, JitProducts::hadamard, new KernelContext(), x, out, n, scale)), 8.0 * rows * n, rows * n * log2);
        }
    }

    private static void fp8Cases() {
        for (int n : SIZES) {
            String shape = Integer.toString(n);
            FloatArray x = uniform(n, -400, 400, 69);
            ByteArray codes = new ByteArray(n);
            Random r = new Random(70);
            for (int i = 0; i < n; i++) {
                int v = r.nextInt(256);
                // Skip the NaN codes (all exponent and mantissa bits set).
                codes.set(i, (byte) ((v & 0x7f) == 0x7f ? v ^ 1 : v));
            }
            ByteArray bytes = new ByteArray(n);
            FloatArray back = new FloatArray(n);
            compare("products", "toFp8", shape, regime(n), "f32", "JitProducts#toFp8", new Object[] { x }, new Object[] { bytes }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::toFp8, x, bytes), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitProducts::toFp8, new KernelContext(), x, bytes, n)), 5.0 * n, n);
            compare("products", "fromFp8", shape, regime(n), "fp8", "JitProducts#fromFp8", new Object[] { codes }, new Object[] { back }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::fromFp8, codes, back), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitProducts::fromFp8, new KernelContext(), codes, back, n)), 5.0 * n, n);
        }
    }

    private static void qqmmCases() {
        final int mode = 0;
        for (int[] s : new int[][] { { 1, 4096, 4096 }, { 16, 4096, 4096 } }) {
            int m = s[0];
            int k = s[1];
            int n = s[2];
            FloatArray x = uniform(m * k, -1, 1, 71);
            IntArray wq = randomInts(n * k / 4, 72);
            for (int i = 0; i < wq.getSize(); i++) {
                // Moderate mxfp8 codes: no NaNs, no huge magnitudes.
                wq.set(i, wq.get(i) & 0x3f3f3f3f);
            }
            ByteArray scales = new ByteArray(n * k / 32);
            scales.init((byte) 127);
            FloatArray y = new FloatArray(m * n);
            compare("products", "qqmm", m + "x" + k + "x" + n + " mxfp8", m == 1 ? "decode" : "prefill", "f32", "JitProducts#qqmm", new Object[] { x, wq, scales },
                    new Object[] { y }, (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::qqmm, x, wq, scales, y, m, k, n, mode), //
                    jit(() -> groups(m * n, 32), (g, gs, gn, t) -> g.task(t, JitProducts::qqmm, new KernelContext(), x, wq, scales, y, n, k)), (double) n * k + n * k / 32.0
                            + 4.0 * (m * k + m * n), 2.0 * m * n * k);
        }
        // mxfp8 quantization; the JIT baseline is the affine 8-bit quantization over groups of 32.
        final int rows = 4096;
        final int cols = 4096;
        final int groupSize = 32;
        int groups = rows * cols / groupSize;
        FloatArray w = uniform(rows * cols, -1, 1, 73);
        IntArray wq = new IntArray(rows * cols / 4);
        ByteArray e8m0 = new ByteArray(groups);
        FloatArray jitScales = new FloatArray(groups);
        FloatArray jitBiases = new FloatArray(groups);
        compare("products", "quantizeMx", rows + "x" + cols + " mxfp8 g" + groupSize, "load", "f32", "JitQuantized#quantize", new Object[] { w }, new Object[] { wq, e8m0,
                jitScales, jitBiases }, (g, gs, gn, t) -> g.libraryTask(t, MlxProducts::quantizeMx, w, wq, e8m0, rows, cols, mode), //
                jit(() -> grid1D(groups), (g, gs, gn, t) -> g.task(t, JitQuantized::quantize, new KernelContext(), w, wq, jitScales, jitBiases, groups, groupSize, 8)),
                5.0 * rows * cols + groups, 3.0 * rows * cols);
    }

    // ---------------------------------------------------------------- random sampling

    private static void random() {
        if (!wants("random")) {
            return;
        }
        samplerCases();
        distributionCases();
        categoricalCases();
        multivariateCases();
        permutationCases();
    }

    private static void samplerCases() {
        for (int n : SIZES) {
            String shape = Integer.toString(n);
            IntArray words = new IntArray(n);
            IntArray ints = new IntArray(n);
            FloatArray out = new FloatArray(n);
            compare("random", "bits", shape, regime(n), "u32", "JitRandom#bits", new Object[] { words }, new Object[] { words }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::bits, words, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::bits, new KernelContext(), words, n, SEED)), 4.0 * n, n);
            compare("random", "uniform", shape, regime(n), "f32", "JitRandom#uniformRange", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::uniform, out, -2.0f, 3.0f, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::uniformRange, new KernelContext(), out, n, -2.0f, 3.0f, SEED)), 4.0 * n, n);
            compare("random", "randint", shape, regime(n), "i32", "JitRandom#randint", new Object[] { ints }, new Object[] { ints }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::randint, ints, -3, 5, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::randint, new KernelContext(), ints, n, -3, 5, SEED)), 4.0 * n, n);
            FloatArray p = uniform(n, 0, 1, 81);
            ByteArray coins = new ByteArray(n);
            compare("random", "bernoulli", shape, regime(n), "f32", "JitRandom#bernoulli", new Object[] { p }, new Object[] { coins }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::bernoulli, p, coins, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::bernoulli, new KernelContext(), p, coins, n, SEED)), 5.0 * n, n);
        }
    }

    private static void distributionCases() {
        for (int n : SIZES) {
            String shape = Integer.toString(n);
            FloatArray out = new FloatArray(n);
            FloatArray unused = new FloatArray(1);
            compare("random", "normal", shape, regime(n), "f32", "JitRandom#normal", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::normal, out, 1.0f, 2.0f, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::normal, new KernelContext(), out, unused, unused, n, 1.0f, 2.0f, 0, SEED)), 4.0 * n, 4.0 * n);
            FloatArray loc = uniform(n, -1, 1, 82);
            FloatArray scale = uniform(n, 0.5f, 2, 83);
            compare("random", "normalBroadcast", shape, regime(n), "f32", "JitRandom#normal", new Object[] { loc, scale }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::normalBroadcast, loc, scale, out, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::normal, new KernelContext(), out, loc, scale, n, 0.0f, 1.0f, 1, SEED)), 12.0 * n, 4.0 * n);
            compare("random", "truncatedNormal", shape, regime(n), "f32", "JitRandom#truncatedNormal", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::truncatedNormal, out, -1.0f, 2.0f, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::truncatedNormal, new KernelContext(), out, n, -1.0f, 2.0f, SEED)), 4.0 * n, 4.0 * n);
            compare("random", "gumbel", shape, regime(n), "f32", "JitRandom#gumbel", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::gumbel, out, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::gumbel, new KernelContext(), out, n, SEED)), 4.0 * n, 3.0 * n);
            compare("random", "laplace", shape, regime(n), "f32", "JitRandom#laplace", new Object[] { out }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::laplace, out, 1.0f, 0.5f, SEED), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, JitRandom::laplace, new KernelContext(), out, n, 1.0f, 0.5f, SEED)), 4.0 * n, 3.0 * n);
        }
    }

    /** Split each row across threadgroups when there are too few draws to fill the GPU. */
    private static final int CATEGORICAL_GROUPS = 64;

    /**
     * The JIT categorical baseline for {@code count = rows * samples} draws: one threadgroup per draw
     * when there are at least {@link #CATEGORICAL_GROUPS} of them, otherwise each row split into
     * slices across threadgroups, then merged.
     */
    private static TaskAdder categoricalJit(FloatArray logits, IntArray out, int rows, int classes, int samples) {
        int count = rows * samples;
        if (count >= CATEGORICAL_GROUPS) {
            return jit(() -> groups(count, JitRandom.THREADS), (g, gs, gn, t) -> g.task(t, JitRandom::categorical, new KernelContext(), logits, out, rows, classes, samples, SEED));
        }
        int chunks = CATEGORICAL_GROUPS / count;
        FloatArray partialValue = new FloatArray(count * chunks);
        IntArray partialClass = new IntArray(count * chunks);
        TaskAdder partial = jit(() -> groups(count * chunks, JitRandom.THREADS), (g, gs, gn, t) -> g.task(t, JitRandom::categoricalChunked, new KernelContext(), logits, partialValue,
                partialClass, rows, classes, chunks, SEED));
        TaskAdder merge = jit(() -> grid1D(count), (g, gs, gn, t) -> g.task(t, JitRandom::categoricalMerge, new KernelContext(), partialValue, partialClass, out, count, chunks, classes));
        return (g, gs, gn, t) -> {
            partial.add(g, gs, gn, t + "p");
            merge.add(g, gs, gn, t + "m");
        };
    }

    private static void categoricalCases() {
        // {rows, classes, samples}: one decode step over a full vocabulary, and a batch.
        for (int[] s : new int[][] { { 1, VOCAB, 8 }, { 64, 32000, 16 } }) {
            int rows = s[0];
            int classes = s[1];
            int samples = s[2];
            FloatArray logits = uniform(rows * classes, -5, 5, 84);
            IntArray one = new IntArray(rows);
            IntArray many = new IntArray(rows * samples);
            String shape = rows + "x" + classes;
            String regime = rows == 1 ? "decode" : "batch";
            double bytes = 4.0 * rows * classes;
            compare("random", "categorical", shape, regime, "f32", "JitRandom#categorical", new Object[] { logits }, new Object[] { one }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::categorical, logits, one, rows, classes, SEED), //
                    categoricalJit(logits, one, rows, classes, 1), bytes, bytes);
            compare("random", "categoricalSamples", shape + " x" + samples, regime, "f32", "JitRandom#categorical", new Object[] { logits }, new Object[] { many }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::categoricalSamples, logits, many, rows, classes, samples, SEED), //
                    categoricalJit(logits, many, rows, classes, samples), bytes,
                    bytes * samples);
            compare("random", "categoricalShape", samples + " x " + shape, regime, "f32", "JitRandom#categorical", new Object[] { logits }, new Object[] { many }, //
                    (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::categoricalShape, logits, many, rows, classes, samples, SEED), //
                    categoricalJit(logits, many, rows, classes, samples), bytes,
                    bytes * samples);
        }
    }

    private static void multivariateCases() {
        final int d = 16;
        FloatArray mean = uniform(d, -1, 1, 85);
        // Symmetric and diagonally dominant, hence positive definite.
        FloatArray cov = uniform(d * d, -0.5f, 0.5f, 86);
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < i; j++) {
                cov.set(i * d + j, cov.get(j * d + i));
            }
            cov.set(i * d + i, d);
        }
        for (int count : new int[] { 4096, 1 << 20 }) {
            FloatArray out = new FloatArray(count * d);
            compare("random", "multivariateNormal", count + " x " + d, regime(count), "f32", "JitLinalg#cholesky+JitRandom#multivariateNormal", new Object[] { mean, cov },
                    new Object[] { out }, (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::multivariateNormal, mean, cov, out, count, d, SEED), //
                    (g, gs, gn, t) -> {
                        FloatArray chol = new FloatArray(d * d);
                        g.task(t + "c", JitLinalg::cholesky, new KernelContext(), cov, chol, d, 0);
                        gs.addWorkerGrid(gn + "." + t + "c", groups(1, JitLinalg.THREADS));
                        g.task(t + "s", JitRandom::multivariateNormal, new KernelContext(), mean, chol, out, count, d, SEED);
                        gs.addWorkerGrid(gn + "." + t + "s", grid1D(count));
                    }, 4.0 * count * d, (double) count * d * (d + 4));
        }
    }

    /** JIT tasks for a random permutation of n (at most one sort block): random keys, then their sorting indices. */
    private static TaskAdder jitPermutation(IntArray indices, int n) {
        return (g, gs, gn, t) -> {
            FloatArray keys = new FloatArray(n);
            FloatArray dummyValues = new FloatArray(1);
            g.task(t + "k", JitRandom::sortKeys, new KernelContext(), keys, n, SEED);
            gs.addWorkerGrid(gn + "." + t + "k", grid1D(n));
            g.task(t + "s", JitSort::sortSlices, new KernelContext(), keys, dummyValues, indices, n, 1, nextPowerOfTwo(n), 0, 1);
            gs.addWorkerGrid(gn + "." + t + "s", groups(1, JitSort.THREADS));
        };
    }

    private static void permutationCases() {
        // The JIT sort handles one block in local memory: a permutation of up to JitSort.BLOCK elements.
        final int n = JitSort.BLOCK;
        FloatArray x = uniform(n, -1, 1, 87);
        FloatArray permuted = new FloatArray(n);
        IntArray indices = new IntArray(n);
        IntArray arange = new IntArray(n);
        String shape = Integer.toString(n);
        compare("random", "permutation", shape, regime(n), "f32", "JitRandom#sortKeys+JitSort#sortSlices", new Object[] { x }, new Object[] { permuted, indices }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::permutation, x, permuted, SEED), jitPermutation(indices, n), 8.0 * n, n);
        compare("random", "permutationArange", shape, regime(n), "i32", "JitRandom#sortKeys+JitSort#sortSlices", new Object[] { arange }, new Object[] { arange }, //
                (g, gs, gn, t) -> g.libraryTask(t, MlxRandom::permutationArange, arange, SEED), jitPermutation(arange, n), 4.0 * n, n);
    }

}
