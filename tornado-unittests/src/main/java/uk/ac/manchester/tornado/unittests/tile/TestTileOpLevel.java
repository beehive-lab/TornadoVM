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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Operator-level tests for the tile API: one case per operator, each in isolation.
 *
 * <p>
 * The other tile suites test kernels - attention, norms, GEMM variants - where a single
 * operator's mistake shows up as a wrong number somewhere downstream. This suite is the
 * complement: every case exercises exactly one operator against a host reference, so a failure
 * names the operator and nothing else. It is the suite to run first when a toolkit or driver
 * changes under the branch.
 * </p>
 *
 * <p>
 * Part one covers each operator at fp32 (or int32, where the builtin takes integral tiles only -
 * {@code floordiv}, {@code ceildiv}, {@code mulhi}, the bitwise family and the bit reductions).
 * Part two repeats the type-generic operators at {@code F16}, {@code BF16} and {@code FP8_E4M3},
 * which is where element-type handling actually breaks: each narrow-type case computes in the
 * narrow type and casts to fp32 only to store, so the tolerance is the element type's own.
 * </p>
 *
 * <p>
 * fp8 needs compute capability 9.0. Below that the compiler gate refuses the kernel and the fp8
 * cases report UNSUPPORTED - which is the correct outcome on sm_89, not a skipped test.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileOpLevel
 * </pre>
 */
public class TestTileOpLevel extends TornadoTestBase {

    /** Tile rows. Small, because every case checks every element against a host reference. */
    private static final int ROWS = 8;

    /** Tile columns. */
    private static final int COLUMNS = 32;

    /** Elements in one tile. */
    private static final int ELEMENTS = ROWS * COLUMNS;

    /** Half the tile width, for {@code extract} and {@code cat}. */
    private static final int HALF = COLUMNS / 2;

    /** The square side {@code reshape} maps the tile onto: 16 x 16 is the same 256 elements. */
    private static final int RESHAPED = 16;

    /** Square side for {@code mma}, which wants equal contracting dimensions here. */
    private static final int MMA = 32;

    /** Starting element of the strided view. */
    private static final int OFFSET = 5;

    /** Row pitch of the strided view: wider than the tile, so rows are not contiguous. */
    private static final int PITCH = COLUMNS + 7;

    /** Relative tolerance for fp32 operators. */
    private static final double TOL = 1e-4;

    /** Relative tolerance where the hardware is allowed an approximation, as for {@code rsqrt}. */
    private static final double LOOSE = 1e-2;

    /** Relative tolerance for fp16: ten mantissa bits. */
    private static final double HALF_TOL = 5e-3;

    /** Relative tolerance for bf16: seven mantissa bits, so an order of magnitude coarser. */
    private static final double BF16_TOL = 4e-2;

    /** Relative tolerance for fp8 e4m3: three mantissa bits. */
    private static final double FP8_TOL = 1.5e-1;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /** Input value ranges, chosen per operator so the operator is defined over the whole tile. */
    private enum Domain {
        /** Both signs, moderate magnitude. */
        SYMMETRIC,
        /** Strictly positive, for {@code log}, {@code sqrt} and {@code pow}'s base. */
        POSITIVE,
        /** Inside the unit interval, for {@code tan} and small exponents. */
        UNIT,
        /** Away from zero in both directions, for divisors. */
        NONZERO,
        /** Close to one, so a product over 32 terms stays in range. */
        NEAR_ONE,
        /** A small discrete set, so comparisons have equal and unequal cases. */
        STEPS,
        /** Full 32-bit patterns, for the bitwise operators. */
        INT_BITS,
        /** Small signed integers. */
        INT_SMALL,
        /** Shift counts inside the word width. */
        INT_SHIFT,
        /** Positive divisors. */
        INT_DIVISOR
    }

    // -------------------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------------------

    /** One tile block. The block itself is always 1x1x1 - the tile compiler owns the threads. */
    private static void execute(TaskGraph graph) throws TornadoExecutionPlanException {
        WorkerGrid2D worker = new WorkerGrid2D(1, 1);
        worker.setLocalWork(1, 1, 1);
        executeWith(new GridScheduler("op.k", worker), graph);
    }

    private static void executeWith(GridScheduler grid, TaskGraph graph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    /**
     * Runs a kernel whose element type the device may not support, turning the compiler gate's
     * refusal into the exception the runner counts as UNSUPPORTED. Anything else propagates.
     */
    private static void executeGated(TaskGraph graph) throws TornadoExecutionPlanException {
        try {
            execute(graph);
        } catch (TornadoExecutionPlanException | RuntimeException e) {
            String message = String.valueOf(e.getMessage()) + String.valueOf(e.getCause());
            if (message.contains("FP8") && message.contains("9.0")) {
                throw new TornadoVMCUDANotSupported("fp8 tiles need compute capability 9.0: " + message);
            }
            throw e;
        }
    }

    /**
     * Compares with a tolerance relative to the magnitude of the expected value, so one
     * tolerance per element type works across operators whose outputs differ in scale.
     */
    private static void assertClose(String what, double expected, double actual, double tolerance) {
        double magnitude = Math.max(1.0, Math.abs(expected));
        assertEquals(what, expected, actual, tolerance * magnitude);
    }

    // -------------------------------------------------------------------------------------
    // Inputs
    // -------------------------------------------------------------------------------------

    private static float sample(Domain domain, Random random) {
        switch (domain) {
            case SYMMETRIC:
                return 4.0f * random.nextFloat() - 2.0f;
            case POSITIVE:
                return 0.25f + 3.75f * random.nextFloat();
            case UNIT:
                return 0.05f + 0.9f * random.nextFloat();
            case NONZERO:
                return (random.nextBoolean() ? 1.0f : -1.0f) * (0.5f + 2.0f * random.nextFloat());
            case NEAR_ONE:
                return 0.9f + 0.2f * random.nextFloat();
            case STEPS:
                // Five distinct values including the 0.5 the scalar comparisons test against, so
                // every comparison has both a true and a false case.
                return random.nextInt(5) * 0.5f - 1.0f;
            default:
                throw new IllegalArgumentException("not a floating point domain: " + domain);
        }
    }

    private static int sampleInt(Domain domain, Random random) {
        switch (domain) {
            case INT_BITS:
                return random.nextInt();
            case INT_SMALL:
                return random.nextInt(2001) - 1000;
            case INT_SHIFT:
                return random.nextInt(8);
            case INT_DIVISOR:
                return 1 + random.nextInt(16);
            default:
                throw new IllegalArgumentException("not an integer domain: " + domain);
        }
    }

    private static FloatArray floats(Domain domain, int seed) {
        return floats(ELEMENTS, domain, seed);
    }

    private static FloatArray floats(int length, Domain domain, int seed) {
        Random random = new Random(seed);
        FloatArray array = new FloatArray(length);
        for (int i = 0; i < length; i++) {
            array.set(i, sample(domain, random));
        }
        return array;
    }

    private static IntArray ints(Domain domain, int seed) {
        Random random = new Random(seed);
        IntArray array = new IntArray(ELEMENTS);
        for (int i = 0; i < ELEMENTS; i++) {
            array.set(i, sampleInt(domain, random));
        }
        return array;
    }

    /**
     * Narrow-type inputs stay in [0.5, 2.5]: fp8 e4m3 carries three mantissa bits, so a wide
     * input range would put the rounding of the inputs, not the operator, under test.
     */
    private static HalfFloatArray halves(int length, int seed) {
        Random random = new Random(seed);
        HalfFloatArray array = new HalfFloatArray(length);
        for (int i = 0; i < length; i++) {
            array.set(i, new HalfFloat(0.5f + 2.0f * random.nextFloat()));
        }
        return array;
    }

    private static BFloat16Array bfloats(int length, int seed) {
        Random random = new Random(seed);
        BFloat16Array array = new BFloat16Array(length);
        for (int i = 0; i < length; i++) {
            array.setFloat(i, 0.5f + 2.0f * random.nextFloat());
        }
        return array;
    }

    private static FP8Array fp8s(int length, int seed) {
        Random random = new Random(seed);
        FP8Array array = new FP8Array(length);
        for (int i = 0; i < length; i++) {
            array.setE4M3(i, 0.5f + 2.0f * random.nextFloat());
        }
        return array;
    }

    // =====================================================================================
    // Kernels
    // =====================================================================================

    // -------------------------------------------------------------------------------------
    // Elementwise arithmetic
    // -------------------------------------------------------------------------------------

    /** {@code ct::operator+}. */
    public static void opAdd(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.add(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator-}. */
    public static void opSub(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.sub(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator*}. */
    public static void opMul(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.mul(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator/}. */
    public static void opDiv(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.div(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::max}, the elementwise form. */
    public static void opMaximum(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.maximum(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::min}, the elementwise form. */
    public static void opMinimum(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.minimum(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Math, one operand
    // -------------------------------------------------------------------------------------

    /** {@code ct::exp}. */
    public static void opExp(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.exp(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::exp2}. */
    public static void opExp2(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.exp2(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::log}. */
    public static void opLog(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.log(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::log2}. */
    public static void opLog2(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.log2(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::sqrt}. */
    public static void opSqrt(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.sqrt(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::rsqrt}, an approximate reciprocal square root. */
    public static void opRsqrt(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.rsqrt(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::sin}. */
    public static void opSin(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.sin(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::cos}. */
    public static void opCos(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cos(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::tan}. The domain stays away from the pole at pi/2. */
    public static void opTan(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.tan(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::sinh}. */
    public static void opSinh(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.sinh(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::cosh}. */
    public static void opCosh(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cosh(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::tanh}. */
    public static void opTanh(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.tanh(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::abs}. */
    public static void opAbs(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.abs(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::floor}. */
    public static void opFloor(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.floor(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::ceil}. */
    public static void opCeil(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.ceil(iv.load(0, 0)), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Math, two operands
    // -------------------------------------------------------------------------------------

    /** {@code ct::atan2}. */
    public static void opAtan2(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.atan2(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::pow}. */
    public static void opPow(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.pow(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::remainder}. Verified truncating, matching Java's {@code %}. */
    public static void opRemainder(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.remainder(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Integer arithmetic and bitwise
    // -------------------------------------------------------------------------------------

    /** {@code ct::operator&}. */
    public static void opBitwiseAnd(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.bitwiseAnd(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator|}. */
    public static void opBitwiseOr(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.bitwiseOr(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator^}. */
    public static void opBitwiseXor(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.bitwiseXor(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator~}. */
    public static void opBitwiseNot(TileContext tc, IntArray in, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.bitwiseNot(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator<<}. */
    public static void opShiftLeft(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.shiftLeft(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::operator>>}. */
    public static void opShiftRight(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.shiftRight(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /**
     * {@code ct::mulhi}, the high half of a 32x32 multiply.
     *
     * <p>
     * The high half is unsigned even though the tile is signed {@code S32}. This is CUDA Tile's
     * behaviour and not an artefact of this lowering: a hand-written tile kernel calling
     * {@code ct::mulhi} on an {@code int} tile agrees, returning -2022270762 for
     * -1171259281 * -1170105035 where the signed high half is 319093554. The reference below is
     * therefore the unsigned one.
     * </p>
     */
    public static void opMulhi(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.mulhi(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::floordiv}. Integer only: the builtin takes integral tiles. */
    public static void opFloorDiv(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.floorDiv(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    /** {@code ct::ceildiv}. Integer only, like {@code floordiv}. */
    public static void opCeilDiv(TileContext tc, IntArray a, IntArray b, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.ceilDiv(iv.load(0, 0), jv.load(0, 0)), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Comparisons
    // -------------------------------------------------------------------------------------

    /** lessThan, tile against tile: a predicate tile, made visible through select. */
    public static void opLessThan(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.lessThan(iv.load(0, 0), jv.load(0, 0));
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** lessOrEqual, tile against tile: a predicate tile, made visible through select. */
    public static void opLessOrEqual(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.lessOrEqual(iv.load(0, 0), jv.load(0, 0));
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** greaterThan, tile against tile: a predicate tile, made visible through select. */
    public static void opGreaterThan(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.greaterThan(iv.load(0, 0), jv.load(0, 0));
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** greaterOrEqual, tile against tile: a predicate tile, made visible through select. */
    public static void opGreaterOrEqual(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.greaterOrEqual(iv.load(0, 0), jv.load(0, 0));
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** equalTo, tile against tile: a predicate tile, made visible through select. */
    public static void opEqualTo(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.equalTo(iv.load(0, 0), jv.load(0, 0));
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** notEqualTo, tile against tile: a predicate tile, made visible through select. */
    public static void opNotEqualTo(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.notEqualTo(iv.load(0, 0), jv.load(0, 0));
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** lessThan against a scalar, the form a mask against a runtime bound uses. */
    public static void opLessThanScalar(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.lessThan(iv.load(0, 0), 0.5);
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** lessOrEqual against a scalar, the form a mask against a runtime bound uses. */
    public static void opLessOrEqualScalar(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.lessOrEqual(iv.load(0, 0), 0.5);
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** greaterThan against a scalar, the form a mask against a runtime bound uses. */
    public static void opGreaterThanScalar(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.greaterThan(iv.load(0, 0), 0.5);
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** greaterOrEqual against a scalar, the form a mask against a runtime bound uses. */
    public static void opGreaterOrEqualScalar(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.greaterOrEqual(iv.load(0, 0), 0.5);
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** equalTo against a scalar, the form a mask against a runtime bound uses. */
    public static void opEqualToScalar(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.equalTo(iv.load(0, 0), 0.5);
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    /** notEqualTo against a scalar, the form a mask against a runtime bound uses. */
    public static void opNotEqualToScalar(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile mask = tc.notEqualTo(iv.load(0, 0), 0.5);
        ov.store(tc.select(mask, tc.ones(DType.F32, ROWS, COLUMNS), tc.zeros(DType.F32, ROWS, COLUMNS)), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Reductions
    // -------------------------------------------------------------------------------------

    /** {@code ct::sum} along axis 1. */
    public static void opSum(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.sum(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::reduce_max} along axis 1. Note the spelling: {@code ct::max} is elementwise. */
    public static void opMax(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.max(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::reduce_min} along axis 1. */
    public static void opMin(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.min(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::prod} along axis 1. Inputs stay near one: a product of 32 leaves any range fast. */
    public static void opProd(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.prod(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::reduce_bitand} along axis 1. */
    public static void opReduceBitAnd(TileContext tc, IntArray in, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.reduceBitAnd(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::reduce_bitor} along axis 1. */
    public static void opReduceBitOr(TileContext tc, IntArray in, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.reduceBitOr(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::reduce_bitxor} along axis 1. */
    public static void opReduceBitXor(TileContext tc, IntArray in, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.reduceBitXor(iv.load(0, 0), 1), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Scans
    // -------------------------------------------------------------------------------------

    /** {@code ct::partial_sum} along axis 1, inclusive. */
    public static void opPrefixSum(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.prefixSum(iv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::partial_prod} along axis 1, inclusive. */
    public static void opPrefixProduct(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.prefixProduct(iv.load(0, 0), 1), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tile creation
    // -------------------------------------------------------------------------------------

    /** {@code ct::zeros}, {@code ct::full}, {@code ct::ones} - one output view each. */
    public static void opCreation(TileContext tc, FloatArray zero, FloatArray filled, FloatArray one, int rows, int columns) {
        PartitionView zv = tc.partition(tc.view(zero, rows, columns), ROWS, COLUMNS);
        PartitionView fv = tc.partition(tc.view(filled, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(one, rows, columns), ROWS, COLUMNS);
        zv.store(tc.zeros(DType.F32, ROWS, COLUMNS), 0, 0);
        fv.store(tc.full(DType.F32, 2.5, ROWS, COLUMNS), 0, 0);
        ov.store(tc.ones(DType.F32, ROWS, COLUMNS), 0, 0);
    }

    /**
     * {@code ct::iota}, as a column of row indices and a row of column indices - the two forms a
     * mask kernel builds coordinates from.
     */
    public static void opIota(TileContext tc, IntArray rowIndex, IntArray columnIndex, int rows, int columns) {
        PartitionView rv = tc.partition(tc.view(rowIndex, rows, 1), ROWS, 1);
        PartitionView cv = tc.partition(tc.view(columnIndex, 1, columns), 1, COLUMNS);
        rv.store(tc.iota(DType.S32, ROWS, 1), 0, 0);
        cv.store(tc.iota(DType.S32, 1, COLUMNS), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Fused multiply-add, select and the predicate operators
    // -------------------------------------------------------------------------------------

    /** {@code ct::fma}: {@code a * b + c} in one operation. */
    public static void opFma(TileContext tc, FloatArray a, FloatArray b, FloatArray c, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView kv = tc.partition(tc.view(c, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.fma(iv.load(0, 0), jv.load(0, 0), kv.load(0, 0)), 0, 0);
    }

    /** {@code ct::scale}: a tile times a compile-time constant. */
    public static void opScale(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.scale(iv.load(0, 0), 1.5), 0, 0);
    }

    /** {@code ct::select}, the only consumer of a predicate tile. */
    public static void opSelect(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView jv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        Tile left = iv.load(0, 0);
        Tile right = jv.load(0, 0);
        ov.store(tc.select(tc.greaterThan(left, right), left, right), 0, 0);
    }

    /** {@code &}, {@code |} and {@code !} on predicate tiles. */
    public static void opPredicateLogic(TileContext tc, FloatArray in, FloatArray both, FloatArray either, FloatArray negated, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(both, rows, columns), ROWS, COLUMNS);
        PartitionView ev = tc.partition(tc.view(either, rows, columns), ROWS, COLUMNS);
        PartitionView nv = tc.partition(tc.view(negated, rows, columns), ROWS, COLUMNS);

        Tile x = iv.load(0, 0);
        Tile positive = tc.greaterThan(x, 0.0);
        Tile small = tc.lessThan(x, 1.0);
        Tile ones = tc.ones(DType.F32, ROWS, COLUMNS);
        Tile zeros = tc.zeros(DType.F32, ROWS, COLUMNS);

        bv.store(tc.select(tc.logicalAnd(positive, small), ones, zeros), 0, 0);
        ev.store(tc.select(tc.logicalOr(positive, small), ones, zeros), 0, 0);
        nv.store(tc.select(tc.logicalNot(positive), ones, zeros), 0, 0);
    }

    /** {@code ct::isnan} and {@code ct::isinf}. */
    public static void opNanAndInfinite(TileContext tc, FloatArray in, FloatArray nans, FloatArray infinites, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView nv = tc.partition(tc.view(nans, rows, columns), ROWS, COLUMNS);
        PartitionView fv = tc.partition(tc.view(infinites, rows, columns), ROWS, COLUMNS);
        Tile x = iv.load(0, 0);
        Tile ones = tc.ones(DType.F32, ROWS, COLUMNS);
        Tile zeros = tc.zeros(DType.F32, ROWS, COLUMNS);
        nv.store(tc.select(tc.isNaN(x), ones, zeros), 0, 0);
        fv.store(tc.select(tc.isInfinite(x), ones, zeros), 0, 0);
    }

    /** {@code ct::all_of} and {@code ct::any_of}, reducing a predicate tile along axis 1. */
    public static void opPredicateReductions(TileContext tc, FloatArray in, FloatArray all, FloatArray any, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView av = tc.partition(tc.view(all, rows, 1), ROWS, 1);
        PartitionView yv = tc.partition(tc.view(any, rows, 1), ROWS, 1);
        Tile positive = tc.greaterThan(iv.load(0, 0), 0.0);
        Tile ones = tc.ones(DType.F32, ROWS, 1);
        Tile zeros = tc.zeros(DType.F32, ROWS, 1);
        av.store(tc.select(tc.allOf(positive, 1), ones, zeros), 0, 0);
        yv.store(tc.select(tc.anyOf(positive, 1), ones, zeros), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Matrix multiply
    // -------------------------------------------------------------------------------------

    /**
     * {@code ct::mma}: {@code a * b + acc} on tensor cores. Square fp16 operands into an fp32
     * accumulator, which is the pairing the {@code mmaf} table is built around.
     */
    public static void opMma(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int size) {
        PartitionView av = tc.partition(tc.view(a, size, size), MMA, MMA);
        PartitionView bv = tc.partition(tc.view(b, size, size), MMA, MMA);
        PartitionView ov = tc.partition(tc.view(out, size, size), MMA, MMA);
        ov.store(tc.mma(av.load(0, 0), bv.load(0, 0), tc.zeros(DType.F32, MMA, MMA)), 0, 0);
    }

    /**
     * {@code ct::matmul}: the same product with no accumulator. The result's element type comes
     * from the operands, so fp16 operands give an fp16 result - the cast to fp32 below is only
     * so the test can read it, and the case checks that what comes back really was fp16.
     *
     * <p>
     * From {@code tornado-test --printKernel -V ...TestTileOpLevel#testMatmulHalf} (reserved ABI
     * parameters elided, long lines wrapped):
     * </p>
     *
     * <pre>{@code
     * extern "C" __tile_global__ void opMatmulHalf(<4 reserved>, unsigned char *arg1,
     *                                              unsigned char *arg2, unsigned char *arg3, int arg4)
     * {
     *   unsigned long long ul_0, ul_1, ul_2;
     *
     *   // BLOCK 0
     *   ul_0  =  (unsigned long long) arg1;
     *   ul_1  =  (unsigned long long) arg2;
     *   ul_2  =  (unsigned long long) arg3;
     *   auto tview_3 = ct::partition_view{ct::tensor_span{ct::assume_aligned(
     *       reinterpret_cast<__half *>(ul_0 + 16), 16_ic), ct::extents{32, 32}}, ct::shape{32_ic, 32_ic}};
     *   auto tview_4 = ct::partition_view{ct::tensor_span{ct::assume_aligned(
     *       reinterpret_cast<__half *>(ul_1 + 16), 16_ic), ct::extents{32, 32}}, ct::shape{32_ic, 32_ic}};
     *   auto tview_5 = ct::partition_view{ct::tensor_span{ct::assume_aligned(
     *       reinterpret_cast<float *>(ul_2 + 16), 16_ic), ct::extents{32, 32}}, ct::shape{32_ic, 32_ic}};
     *   auto tile_6 = tview_3.load(0, 0);
     *   auto tile_7 = tview_4.load(0, 0);
     *   auto tile_8 = ct::matmul(tile_6, tile_7);
     *   auto tile_9 = ct::element_cast<float>(tile_8);
     *   tview_5.store(tile_9, 0, 0);
     *   return;
     * }
     * }</pre>
     *
     * <p>
     * Contrast {@code opMma} above, whose two corresponding lines are:
     * </p>
     *
     * <pre>{@code
     * ct::tile<float, ct::shape<32, 32>> tile_8 = ct::full<ct::tile<float, ct::shape<32, 32>>>(0.0f);
     * auto tile_9 = ct::mma(tile_6, tile_7, tile_8);
     * }</pre>
     *
     * <p>
     * The accumulator is a declared fp32 tile, and it is what fixes the result type. Here there
     * is no accumulator at all, and the {@code ct::element_cast<float>} is this kernel's own
     * {@code cast} - which is the proof in the emitted source that the matmul's result was the
     * narrower fp16 type and had to be widened on the way out.
     * </p>
     */
    public static void opMatmulHalf(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int size) {
        PartitionView av = tc.partition(tc.view(a, size, size), MMA, MMA);
        PartitionView bv = tc.partition(tc.view(b, size, size), MMA, MMA);
        PartitionView ov = tc.partition(tc.view(out, size, size), MMA, MMA);
        ov.store(tc.cast(tc.matmul(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /** {@code ct::matmul} with fp32 operands, where the inferred result type is fp32. */
    public static void opMatmulFloat(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int size) {
        PartitionView av = tc.partition(tc.view(a, size, size), MMA, MMA);
        PartitionView bv = tc.partition(tc.view(b, size, size), MMA, MMA);
        PartitionView ov = tc.partition(tc.view(out, size, size), MMA, MMA);
        ov.store(tc.matmul(av.load(0, 0), bv.load(0, 0)), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Shape and type
    // -------------------------------------------------------------------------------------

    /** {@code ct::transpose}: a {@code [ROWS, COLUMNS]} tile into a {@code [COLUMNS, ROWS]} view. */
    public static void opTranspose(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, columns, rows), COLUMNS, ROWS);
        ov.store(tc.transpose(iv.load(0, 0)), 0, 0);
    }

    /** {@code ct::broadcast}: a single row stretched down the tile. */
    public static void opBroadcast(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, 1, columns), 1, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.broadcast(iv.load(0, 0), ROWS, COLUMNS), 0, 0);
    }

    /** {@code ct::reshape}: the same elements, row-major, at a different rank-2 shape. */
    public static void opReshape(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, RESHAPED, RESHAPED), RESHAPED, RESHAPED);
        ov.store(tc.reshape(iv.load(0, 0), RESHAPED, RESHAPED), 0, 0);
    }

    /**
     * {@code ct::extract}: the second half of each row. The block index is in sub-tile units, so
     * asking for a {@code [ROWS, HALF]} tile at block 1 gives columns HALF..COLUMNS-1.
     */
    public static void opExtract(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, HALF), ROWS, HALF);
        ov.store(tc.extract(iv.load(0, 0), ROWS, HALF, 0, 1), 0, 0);
    }

    /** {@code ct::cat}: two half-width tiles joined along axis 1. */
    public static void opConcat(TileContext tc, FloatArray a, FloatArray b, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(a, rows, HALF), ROWS, HALF);
        PartitionView jv = tc.partition(tc.view(b, rows, HALF), ROWS, HALF);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.concat(iv.load(0, 0), jv.load(0, 0), 1), 0, 0);
    }

    /** {@code ct::element_cast}: float to int, converting the value. */
    public static void opCast(TileContext tc, FloatArray in, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(iv.load(0, 0), DType.S32), 0, 0);
    }

    /** {@code ct::element_bitcast}: float to int, keeping the bits. */
    public static void opBitcast(TileContext tc, FloatArray in, IntArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.bitcast(iv.load(0, 0), DType.S32), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Views, partitions, loads and stores
    // -------------------------------------------------------------------------------------

    /** A rank-1 view and partition: the smallest thing the API can express. */
    public static void opViewRank1(TileContext tc, FloatArray in, FloatArray out, int extent) {
        PartitionView iv = tc.partition(tc.view(in, extent), COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, extent), COLUMNS);
        ov.store(iv.load(tc.bidX()), tc.bidX());
    }

    /** A rank-2 view and partition, plus the unmasked {@code load} and {@code store}. */
    public static void opViewRank2(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(iv.load(0, 0), 0, 0);
    }

    /** A rank-3 view and partition: a batch dimension in front of a rank-2 tile. */
    public static void opViewRank3(TileContext tc, FloatArray in, FloatArray out, int batches, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, batches, rows, columns), 1, ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, batches, rows, columns), 1, ROWS, COLUMNS);
        ov.store(iv.load(tc.bidX(), 0, 0), tc.bidX(), 0, 0);
    }

    /**
     * {@code viewStrided}: a row pitch wider than the tile, and a starting offset. This is the
     * shape an interleaved buffer needs - every row starts one PITCH apart, and the first row
     * starts OFFSET elements in.
     */
    public static void opViewStrided(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.viewStrided(in, OFFSET, rows, columns, PITCH), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(iv.load(0, 0), 0, 0);
    }

    /**
     * {@code load_masked} and {@code store_masked}: the view is narrower than the tile, so the
     * load zero-pads the tail and the store drops it.
     */
    public static void opMaskedLoadStore(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.storeMasked(tc.scale(iv.loadMasked(0, 0), 2.0), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Atomics through a view
    // -------------------------------------------------------------------------------------

    /**
     * The two float atomics: add and sub.
     *
     * <p>
     * min and max are absent here on purpose. CUDA Tile constrains {@code atomic_min} and
     * {@code atomic_max} to 32- and 64-bit integral elements
     * ({@code is_32_or_64_bit_integral_scalar_v}), so they belong to the integer case below; the
     * API's own validation refuses them on an F32 view with a message naming the accepted type.
     * </p>
     */
    public static void opFloatAtomics(TileContext tc, FloatArray in, FloatArray sums, FloatArray differences, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView av = tc.partition(tc.view(sums, rows, columns), ROWS, COLUMNS);
        PartitionView sv = tc.partition(tc.view(differences, rows, columns), ROWS, COLUMNS);
        Tile x = iv.load(0, 0);
        av.atomicAdd(x, 0, 0);
        sv.atomicSub(x, 0, 0);
    }

    /** The integer atomics: min, max, and, or, xor, exchange. */
    public static void opIntegerAtomics(TileContext tc, IntArray in, IntArray minima, IntArray maxima, IntArray masked, IntArray merged, IntArray flipped, IntArray swapped, int rows,
            int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView nv = tc.partition(tc.view(minima, rows, columns), ROWS, COLUMNS);
        PartitionView mv = tc.partition(tc.view(maxima, rows, columns), ROWS, COLUMNS);
        PartitionView av = tc.partition(tc.view(masked, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(merged, rows, columns), ROWS, COLUMNS);
        PartitionView xv = tc.partition(tc.view(flipped, rows, columns), ROWS, COLUMNS);
        PartitionView ev = tc.partition(tc.view(swapped, rows, columns), ROWS, COLUMNS);
        Tile x = iv.load(0, 0);
        nv.atomicMin(x, 0, 0);
        mv.atomicMax(x, 0, 0);
        av.atomicAnd(x, 0, 0);
        ov.atomicOr(x, 0, 0);
        xv.atomicXor(x, 0, 0);
        ev.atomicExchange(x, 0, 0);
    }

    /** The view's own {@code atomic_load} and {@code atomic_store}. */
    public static void opAtomicLoadStore(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.atomicStore(iv.atomicLoad(0, 0), 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Block indices
    // -------------------------------------------------------------------------------------

    /**
     * {@code ct::bid}: each block copies its own tile. If the block index were wrong, two blocks
     * would write the same tile and the rest of the output would stay zero.
     */
    public static void opBlockId(TileContext tc, FloatArray in, FloatArray out, int rows, int columns) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(iv.load(tc.bidX(), tc.bidY()), tc.bidX(), tc.bidY());
    }

    /**
     * {@code ct::num_blocks}: a persistent kernel striding over more tiles than there are
     * blocks. With the grid size wrong, some tiles would be visited twice and others never.
     */
    public static void opNumBlocks(TileContext tc, FloatArray in, FloatArray out, int rows, int columns, int tiles) {
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        for (int tile = tc.bidX(); tile < tiles; tile += tc.numBlocksX()) {
            ov.store(tc.scale(iv.load(tile, 0), 2.0), tile, 0);
        }
    }

    // -------------------------------------------------------------------------------------
    // Element type: FP16
    // -------------------------------------------------------------------------------------

    /**
     * add on fp16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void fp16Add(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.add(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * sub on fp16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void fp16Sub(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.sub(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * mul on fp16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void fp16Mul(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.mul(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * div on fp16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void fp16Div(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.div(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * maximum on fp16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void fp16Maximum(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.maximum(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /** {@code ct::exp} on fp16 tiles. */
    public static void fp16Exp(TileContext tc, HalfFloatArray in, FloatArray out, int rows, int columns) {{
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.exp(iv.load(0, 0)), DType.F32), 0, 0);
    }}

    /** {@code ct::sum} along axis 1 on fp16 tiles. */
    public static void fp16Sum(TileContext tc, HalfFloatArray in, FloatArray out, int rows, int columns) {{
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.cast(tc.sum(iv.load(0, 0), 1), DType.F32), 0, 0);
    }}

    /** {@code ct::mma} with fp16 operands into an fp32 accumulator. */
    public static void fp16Mma(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray out, int size) {{
        PartitionView av = tc.partition(tc.view(a, size, size), MMA, MMA);
        PartitionView bv = tc.partition(tc.view(b, size, size), MMA, MMA);
        PartitionView ov = tc.partition(tc.view(out, size, size), MMA, MMA);
        ov.store(tc.mma(av.load(0, 0), bv.load(0, 0), tc.zeros(DType.F32, MMA, MMA)), 0, 0);
    }}

    // -------------------------------------------------------------------------------------
    // Element type: BF16
    // -------------------------------------------------------------------------------------

    /**
     * add on bf16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void bf16Add(TileContext tc, BFloat16Array a, BFloat16Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.add(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * sub on bf16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void bf16Sub(TileContext tc, BFloat16Array a, BFloat16Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.sub(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * mul on bf16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void bf16Mul(TileContext tc, BFloat16Array a, BFloat16Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.mul(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * div on bf16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void bf16Div(TileContext tc, BFloat16Array a, BFloat16Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.div(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * maximum on bf16 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     */
    public static void bf16Maximum(TileContext tc, BFloat16Array a, BFloat16Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.maximum(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /** {@code ct::exp} on bf16 tiles. */
    public static void bf16Exp(TileContext tc, BFloat16Array in, FloatArray out, int rows, int columns) {{
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.exp(iv.load(0, 0)), DType.F32), 0, 0);
    }}

    /** {@code ct::sum} along axis 1 on bf16 tiles. */
    public static void bf16Sum(TileContext tc, BFloat16Array in, FloatArray out, int rows, int columns) {{
        PartitionView iv = tc.partition(tc.view(in, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.cast(tc.sum(iv.load(0, 0), 1), DType.F32), 0, 0);
    }}

    /** {@code ct::mma} with bf16 operands into an fp32 accumulator. */
    public static void bf16Mma(TileContext tc, BFloat16Array a, BFloat16Array b, FloatArray out, int size) {{
        PartitionView av = tc.partition(tc.view(a, size, size), MMA, MMA);
        PartitionView bv = tc.partition(tc.view(b, size, size), MMA, MMA);
        PartitionView ov = tc.partition(tc.view(out, size, size), MMA, MMA);
        ov.store(tc.mma(av.load(0, 0), bv.load(0, 0), tc.zeros(DType.F32, MMA, MMA)), 0, 0);
    }}

    // -------------------------------------------------------------------------------------
    // Element type: FP8
    // -------------------------------------------------------------------------------------

    /**
     * add on fp8 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p>
     */
    public static void fp8Add(TileContext tc, FP8Array a, FP8Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.add(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * sub on fp8 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p>
     */
    public static void fp8Sub(TileContext tc, FP8Array a, FP8Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.sub(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * mul on fp8 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p>
     */
    public static void fp8Mul(TileContext tc, FP8Array a, FP8Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.mul(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * div on fp8 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p>
     */
    public static void fp8Div(TileContext tc, FP8Array a, FP8Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.div(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /**
     * maximum on fp8 tiles, cast to fp32 on the way out so the result is readable without a
     * narrow-type encoder in the test.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p>
     */
    public static void fp8Maximum(TileContext tc, FP8Array a, FP8Array b, FloatArray out, int rows, int columns) {
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.maximum(av.load(0, 0), bv.load(0, 0)), DType.F32), 0, 0);
    }

    /** {@code ct::exp} on fp8 tiles.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p> */
    public static void fp8Exp(TileContext tc, FP8Array in, FloatArray out, int rows, int columns) {{
        PartitionView iv = tc.partition(tc.view(in, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, columns), ROWS, COLUMNS);
        ov.store(tc.cast(tc.exp(iv.load(0, 0)), DType.F32), 0, 0);
    }}

    /** {@code ct::sum} along axis 1 on fp8 tiles.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p> */
    public static void fp8Sum(TileContext tc, FP8Array in, FloatArray out, int rows, int columns) {{
        PartitionView iv = tc.partition(tc.view(in, DType.FP8_E4M3, rows, columns), ROWS, COLUMNS);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), ROWS, 1);
        ov.store(tc.cast(tc.sum(iv.load(0, 0), 1), DType.F32), 0, 0);
    }}

    /** {@code ct::mma} with fp8 operands into an fp32 accumulator.
     * <p>
     * fp8 tiles need compute capability 9.0. Below that the compiler gate refuses the kernel
     * and this case reports UNSUPPORTED rather than failing.
     * </p> */
    public static void fp8Mma(TileContext tc, FP8Array a, FP8Array b, FloatArray out, int size) {{
        PartitionView av = tc.partition(tc.view(a, DType.FP8_E4M3, size, size), MMA, MMA);
        PartitionView bv = tc.partition(tc.view(b, DType.FP8_E4M3, size, size), MMA, MMA);
        PartitionView ov = tc.partition(tc.view(out, size, size), MMA, MMA);
        ov.store(tc.mma(av.load(0, 0), bv.load(0, 0), tc.zeros(DType.F32, MMA, MMA)), 0, 0);
    }}

    // =====================================================================================
    // Cases
    // =====================================================================================

    @Test
    public void testAdd() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.SYMMETRIC, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opAdd, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("add at " + i, x + y, out.get(i), TOL);
        }
    }

    @Test
    public void testSub() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.SYMMETRIC, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opSub, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("sub at " + i, x - y, out.get(i), TOL);
        }
    }

    @Test
    public void testMul() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.SYMMETRIC, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMul, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("mul at " + i, x * y, out.get(i), TOL);
        }
    }

    @Test
    public void testDiv() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.NONZERO, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opDiv, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("div at " + i, x / y, out.get(i), TOL);
        }
    }

    @Test
    public void testMaximum() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.SYMMETRIC, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMaximum, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("maximum at " + i, Math.max(x, y), out.get(i), TOL);
        }
    }

    @Test
    public void testMinimum() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.SYMMETRIC, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMinimum, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("minimum at " + i, Math.min(x, y), out.get(i), TOL);
        }
    }

    @Test
    public void testExp() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opExp, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("exp at " + i, Math.exp(x), out.get(i), TOL);
        }
    }

    @Test
    public void testExp2() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opExp2, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("exp2 at " + i, Math.pow(2.0, x), out.get(i), TOL);
        }
    }

    @Test
    public void testLog() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.POSITIVE, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opLog, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("log at " + i, Math.log(x), out.get(i), TOL);
        }
    }

    @Test
    public void testLog2() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.POSITIVE, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opLog2, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("log2 at " + i, Math.log(x) / Math.log(2.0), out.get(i), TOL);
        }
    }

    @Test
    public void testSqrt() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.POSITIVE, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opSqrt, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("sqrt at " + i, Math.sqrt(x), out.get(i), TOL);
        }
    }

    @Test
    public void testRsqrt() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.POSITIVE, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opRsqrt, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("rsqrt at " + i, 1.0 / Math.sqrt(x), out.get(i), LOOSE);
        }
    }

    @Test
    public void testSin() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opSin, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("sin at " + i, Math.sin(x), out.get(i), TOL);
        }
    }

    @Test
    public void testCos() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opCos, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("cos at " + i, Math.cos(x), out.get(i), TOL);
        }
    }

    @Test
    public void testTan() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.UNIT, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opTan, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("tan at " + i, Math.tan(x), out.get(i), TOL);
        }
    }

    @Test
    public void testSinh() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opSinh, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("sinh at " + i, Math.sinh(x), out.get(i), TOL);
        }
    }

    @Test
    public void testCosh() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opCosh, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("cosh at " + i, Math.cosh(x), out.get(i), TOL);
        }
    }

    @Test
    public void testTanh() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opTanh, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("tanh at " + i, Math.tanh(x), out.get(i), TOL);
        }
    }

    @Test
    public void testAbs() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opAbs, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("abs at " + i, Math.abs(x), out.get(i), TOL);
        }
    }

    @Test
    public void testFloor() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opFloor, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("floor at " + i, Math.floor(x), out.get(i), TOL);
        }
    }

    @Test
    public void testCeil() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 11);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opCeil, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = in.get(i);
            assertClose("ceil at " + i, Math.ceil(x), out.get(i), TOL);
        }
    }

    @Test
    public void testAtan2() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.NONZERO, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opAtan2, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("atan2 at " + i, Math.atan2(x, y), out.get(i), TOL);
        }
    }

    @Test
    public void testPow() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.POSITIVE, 21);
        FloatArray b = floats(Domain.UNIT, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opPow, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("pow at " + i, Math.pow(x, y), out.get(i), LOOSE);
        }
    }

    @Test
    public void testRemainder() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 21);
        FloatArray b = floats(Domain.NONZERO, 22);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opRemainder, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i);
            double y = b.get(i);
            assertClose("remainder at " + i, x % y, out.get(i), TOL);
        }
    }

    @Test
    public void testBitwiseAnd() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_BITS, 41);
        IntArray b = ints(Domain.INT_SMALL, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opBitwiseAnd, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("bitwiseAnd at " + i, x & y, out.get(i));
        }
    }

    @Test
    public void testBitwiseOr() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_BITS, 41);
        IntArray b = ints(Domain.INT_SMALL, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opBitwiseOr, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("bitwiseOr at " + i, x | y, out.get(i));
        }
    }

    @Test
    public void testBitwiseXor() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_BITS, 41);
        IntArray b = ints(Domain.INT_SMALL, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opBitwiseXor, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("bitwiseXor at " + i, x ^ y, out.get(i));
        }
    }

    @Test
    public void testBitwiseNot() throws TornadoExecutionPlanException {
        IntArray in = ints(Domain.INT_BITS, 31);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opBitwiseNot, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = in.get(i);
            assertEquals("bitwiseNot at " + i, ~x, out.get(i));
        }
    }

    @Test
    public void testShiftLeft() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_SMALL, 41);
        IntArray b = ints(Domain.INT_SHIFT, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opShiftLeft, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("shiftLeft at " + i, x << y, out.get(i));
        }
    }

    @Test
    public void testShiftRight() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_BITS, 41);
        IntArray b = ints(Domain.INT_SHIFT, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opShiftRight, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("shiftRight at " + i, x >> y, out.get(i));
        }
    }

    @Test
    public void testMulhi() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_BITS, 41);
        IntArray b = ints(Domain.INT_BITS, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMulhi, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("mulhi at " + i, //
                    (int) ((Integer.toUnsignedLong(x) * Integer.toUnsignedLong(y)) >>> 32), out.get(i));
        }
    }

    @Test
    public void testFloorDiv() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_SMALL, 41);
        IntArray b = ints(Domain.INT_DIVISOR, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opFloorDiv, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("floorDiv at " + i, (int) Math.floor((double) x / (double) y), out.get(i));
        }
    }

    @Test
    public void testCeilDiv() throws TornadoExecutionPlanException {
        IntArray a = ints(Domain.INT_SMALL, 41);
        IntArray b = ints(Domain.INT_DIVISOR, 42);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opCeilDiv, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = a.get(i);
            int y = b.get(i);
            assertEquals("ceilDiv at " + i, (int) Math.ceil((double) x / (double) y), out.get(i));
        }
    }

    @Test
    public void testLessThan() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.STEPS, 51);
        FloatArray b = floats(Domain.STEPS, 52);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opLessThan, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = a.get(i);
            float y = b.get(i);
            assertEquals("lessThan at " + i, (x < y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testLessOrEqual() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.STEPS, 51);
        FloatArray b = floats(Domain.STEPS, 52);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opLessOrEqual, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = a.get(i);
            float y = b.get(i);
            assertEquals("lessOrEqual at " + i, (x <= y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testGreaterThan() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.STEPS, 51);
        FloatArray b = floats(Domain.STEPS, 52);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opGreaterThan, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = a.get(i);
            float y = b.get(i);
            assertEquals("greaterThan at " + i, (x > y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testGreaterOrEqual() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.STEPS, 51);
        FloatArray b = floats(Domain.STEPS, 52);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opGreaterOrEqual, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = a.get(i);
            float y = b.get(i);
            assertEquals("greaterOrEqual at " + i, (x >= y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testEqualTo() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.STEPS, 51);
        FloatArray b = floats(Domain.STEPS, 52);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opEqualTo, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = a.get(i);
            float y = b.get(i);
            assertEquals("equalTo at " + i, (x == y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testNotEqualTo() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.STEPS, 51);
        FloatArray b = floats(Domain.STEPS, 52);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opNotEqualTo, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = a.get(i);
            float y = b.get(i);
            assertEquals("notEqualTo at " + i, (x != y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testLessThanScalar() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 61);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opLessThanScalar, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            float y = 0.5f;
            assertEquals("lessThan scalar at " + i, (x < y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testLessOrEqualScalar() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 61);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opLessOrEqualScalar, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            float y = 0.5f;
            assertEquals("lessOrEqual scalar at " + i, (x <= y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testGreaterThanScalar() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 61);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opGreaterThanScalar, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            float y = 0.5f;
            assertEquals("greaterThan scalar at " + i, (x > y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testGreaterOrEqualScalar() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 61);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opGreaterOrEqualScalar, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            float y = 0.5f;
            assertEquals("greaterOrEqual scalar at " + i, (x >= y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testEqualToScalar() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 61);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opEqualToScalar, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            float y = 0.5f;
            assertEquals("equalTo scalar at " + i, (x == y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testNotEqualToScalar() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 61);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opNotEqualToScalar, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            float y = 0.5f;
            assertEquals("notEqualTo scalar at " + i, (x != y) ? 1.0f : 0.0f, out.get(i), 0.0f);
        }
    }

    @Test
    public void testSum() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 71);
        FloatArray out = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opSum, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double acc = 0.0;
            for (int column = 0; column < COLUMNS; column++) {
                double x = in.get(row * COLUMNS + column);
                acc = acc + x;
            }
            assertClose("sum of row " + row, acc, out.get(row), TOL);
        }
    }

    @Test
    public void testMax() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 71);
        FloatArray out = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opMax, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double acc = Double.NEGATIVE_INFINITY;
            for (int column = 0; column < COLUMNS; column++) {
                double x = in.get(row * COLUMNS + column);
                acc = Math.max(acc, x);
            }
            assertClose("max of row " + row, acc, out.get(row), TOL);
        }
    }

    @Test
    public void testMin() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 71);
        FloatArray out = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opMin, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double acc = Double.POSITIVE_INFINITY;
            for (int column = 0; column < COLUMNS; column++) {
                double x = in.get(row * COLUMNS + column);
                acc = Math.min(acc, x);
            }
            assertClose("min of row " + row, acc, out.get(row), TOL);
        }
    }

    @Test
    public void testProd() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.NEAR_ONE, 71);
        FloatArray out = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opProd, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double acc = 1.0;
            for (int column = 0; column < COLUMNS; column++) {
                double x = in.get(row * COLUMNS + column);
                acc = acc * x;
            }
            assertClose("prod of row " + row, acc, out.get(row), LOOSE);
        }
    }

    @Test
    public void testReduceBitAnd() throws TornadoExecutionPlanException {
        IntArray in = ints(Domain.INT_BITS, 81);
        IntArray out = new IntArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opReduceBitAnd, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            int acc = -1;
            for (int column = 0; column < COLUMNS; column++) {
                int x = in.get(row * COLUMNS + column);
                acc = acc & x;
            }
            assertEquals("reduceBitAnd of row " + row, acc, out.get(row));
        }
    }

    @Test
    public void testReduceBitOr() throws TornadoExecutionPlanException {
        IntArray in = ints(Domain.INT_BITS, 81);
        IntArray out = new IntArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opReduceBitOr, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            int acc = 0;
            for (int column = 0; column < COLUMNS; column++) {
                int x = in.get(row * COLUMNS + column);
                acc = acc | x;
            }
            assertEquals("reduceBitOr of row " + row, acc, out.get(row));
        }
    }

    @Test
    public void testReduceBitXor() throws TornadoExecutionPlanException {
        IntArray in = ints(Domain.INT_BITS, 81);
        IntArray out = new IntArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opReduceBitXor, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            int acc = 0;
            for (int column = 0; column < COLUMNS; column++) {
                int x = in.get(row * COLUMNS + column);
                acc = acc ^ x;
            }
            assertEquals("reduceBitXor of row " + row, acc, out.get(row));
        }
    }

    @Test
    public void testPrefixSum() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 91);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opPrefixSum, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double acc = 0.0;
            for (int column = 0; column < COLUMNS; column++) {
                double x = in.get(row * COLUMNS + column);
                acc = acc + x;
                assertClose("prefixSum at (" + row + ", " + column + ")", acc, out.get(row * COLUMNS + column), TOL);
            }
        }
    }

    @Test
    public void testPrefixProduct() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.NEAR_ONE, 91);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opPrefixProduct, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double acc = 1.0;
            for (int column = 0; column < COLUMNS; column++) {
                double x = in.get(row * COLUMNS + column);
                acc = acc * x;
                assertClose("prefixProduct at (" + row + ", " + column + ")", acc, out.get(row * COLUMNS + column), LOOSE);
            }
        }
    }

    @Test
    public void testCreation() throws TornadoExecutionPlanException {
        FloatArray zero = new FloatArray(ELEMENTS);
        FloatArray filled = new FloatArray(ELEMENTS);
        FloatArray one = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .task("k", TestTileOpLevel::opCreation, new TileContext(), zero, filled, one, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, zero, filled, one));
        for (int i = 0; i < ELEMENTS; i++) {
            assertEquals("zeros at " + i, 0.0f, zero.get(i), 0.0f);
            assertEquals("full at " + i, 2.5f, filled.get(i), 0.0f);
            assertEquals("ones at " + i, 1.0f, one.get(i), 0.0f);
        }
    }

    @Test
    public void testIota() throws TornadoExecutionPlanException {
        IntArray rowIndex = new IntArray(ROWS);
        IntArray columnIndex = new IntArray(COLUMNS);
        execute(new TaskGraph("op") //
                .task("k", TestTileOpLevel::opIota, new TileContext(), rowIndex, columnIndex, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, rowIndex, columnIndex));
        for (int row = 0; row < ROWS; row++) {
            assertEquals("iota row " + row, row, rowIndex.get(row));
        }
        for (int column = 0; column < COLUMNS; column++) {
            assertEquals("iota column " + column, column, columnIndex.get(column));
        }
    }

    @Test
    public void testFma() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 101);
        FloatArray b = floats(Domain.SYMMETRIC, 102);
        FloatArray c = floats(Domain.SYMMETRIC, 103);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("k", TestTileOpLevel::opFma, new TileContext(), a, b, c, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertClose("fma at " + i, (double) a.get(i) * b.get(i) + c.get(i), out.get(i), TOL);
        }
    }

    @Test
    public void testScale() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 104);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opScale, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertClose("scale at " + i, in.get(i) * 1.5, out.get(i), TOL);
        }
    }

    @Test
    public void testSelect() throws TornadoExecutionPlanException {
        FloatArray a = floats(Domain.SYMMETRIC, 105);
        FloatArray b = floats(Domain.SYMMETRIC, 106);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opSelect, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertEquals("select at " + i, Math.max(a.get(i), b.get(i)), out.get(i), 0.0f);
        }
    }

    @Test
    public void testPredicateLogic() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.STEPS, 107);
        FloatArray both = new FloatArray(ELEMENTS);
        FloatArray either = new FloatArray(ELEMENTS);
        FloatArray negated = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opPredicateLogic, new TileContext(), in, both, either, negated, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, both, either, negated));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            assertEquals("logicalAnd at " + i, (x > 0.0f && x < 1.0f) ? 1.0f : 0.0f, both.get(i), 0.0f);
            assertEquals("logicalOr at " + i, (x > 0.0f || x < 1.0f) ? 1.0f : 0.0f, either.get(i), 0.0f);
            assertEquals("logicalNot at " + i, (x > 0.0f) ? 0.0f : 1.0f, negated.get(i), 0.0f);
        }
    }

    @Test
    public void testNanAndInfinite() throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(ELEMENTS);
        for (int i = 0; i < ELEMENTS; i++) {
            // Every third element is a NaN and every fifth an infinity, so both predicates have
            // true and false cases in every row.
            if (i % 3 == 0) {
                in.set(i, Float.NaN);
            } else if (i % 5 == 0) {
                in.set(i, (i % 10 == 0) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY);
            } else {
                in.set(i, i * 0.25f);
            }
        }
        FloatArray nans = new FloatArray(ELEMENTS);
        FloatArray infinites = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opNanAndInfinite, new TileContext(), in, nans, infinites, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, nans, infinites));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            assertEquals("isNaN at " + i, Float.isNaN(x) ? 1.0f : 0.0f, nans.get(i), 0.0f);
            assertEquals("isInfinite at " + i, Float.isInfinite(x) ? 1.0f : 0.0f, infinites.get(i), 0.0f);
        }
    }

    @Test
    public void testPredicateReductions() throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(ELEMENTS);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                // Row 0 all positive, row 1 all negative, the rest mixed: all_of and any_of have
                // to disagree somewhere or the test proves nothing.
                float value;
                if (row == 0) {
                    value = 1.0f + column;
                } else if (row == 1) {
                    value = -1.0f - column;
                } else {
                    value = (column % row == 0) ? 1.0f : -1.0f;
                }
                in.set(row * COLUMNS + column, value);
            }
        }
        FloatArray all = new FloatArray(ROWS);
        FloatArray any = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opPredicateReductions, new TileContext(), in, all, any, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, all, any));
        for (int row = 0; row < ROWS; row++) {
            boolean expectedAll = true;
            boolean expectedAny = false;
            for (int column = 0; column < COLUMNS; column++) {
                boolean positive = in.get(row * COLUMNS + column) > 0.0f;
                expectedAll = expectedAll && positive;
                expectedAny = expectedAny || positive;
            }
            assertEquals("allOf of row " + row, expectedAll ? 1.0f : 0.0f, all.get(row), 0.0f);
            assertEquals("anyOf of row " + row, expectedAny ? 1.0f : 0.0f, any.get(row), 0.0f);
        }
    }

    @Test
    public void testMma() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(MMA * MMA, 111);
        HalfFloatArray b = halves(MMA * MMA, 112);
        FloatArray out = new FloatArray(MMA * MMA);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMma, new TileContext(), a, b, out, MMA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < MMA; row++) {
            for (int column = 0; column < MMA; column++) {
                double expected = 0.0;
                for (int step = 0; step < MMA; step++) {
                    expected += a.get(row * MMA + step).getFloat32() * b.get(step * MMA + column).getFloat32();
                }
                assertClose("mma at (" + row + ", " + column + ")", expected, out.get(row * MMA + column), HALF_TOL);
            }
        }
    }

    @Test
    public void testMatmulHalf() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(MMA * MMA, 113);
        HalfFloatArray b = halves(MMA * MMA, 114);
        FloatArray out = new FloatArray(MMA * MMA);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMatmulHalf, new TileContext(), a, b, out, MMA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < MMA; row++) {
            for (int column = 0; column < MMA; column++) {
                double expected = 0.0;
                for (int step = 0; step < MMA; step++) {
                    expected += a.get(row * MMA + step).getFloat32() * b.get(step * MMA + column).getFloat32();
                }
                float actual = out.get(row * MMA + column);
                assertClose("matmul at (" + row + ", " + column + ")", expected, actual, HALF_TOL);
                // The point of the case: matmul infers fp16 from its operands, where mma would
                // have taken fp32 from the accumulator. Rounding the result to fp16 has to be a
                // no-op, or the inferred type was wider than it should be.
                assertEquals("matmul result is not fp16-representable at (" + row + ", " + column + ")", //
                        actual, new HalfFloat(actual).getFloat32(), 0.0f);
            }
        }
    }

    @Test
    public void testMatmulFloat() throws TornadoExecutionPlanException {
        FloatArray a = floats(MMA * MMA, Domain.NEAR_ONE, 115);
        FloatArray b = floats(MMA * MMA, Domain.NEAR_ONE, 116);
        FloatArray out = new FloatArray(MMA * MMA);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opMatmulFloat, new TileContext(), a, b, out, MMA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < MMA; row++) {
            for (int column = 0; column < MMA; column++) {
                double expected = 0.0;
                for (int step = 0; step < MMA; step++) {
                    expected += (double) a.get(row * MMA + step) * b.get(step * MMA + column);
                }
                assertClose("matmul at (" + row + ", " + column + ")", expected, out.get(row * MMA + column), TOL);
            }
        }
    }

    @Test
    public void testTranspose() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 121);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opTranspose, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                assertEquals("transpose at (" + row + ", " + column + ")", //
                        in.get(row * COLUMNS + column), out.get(column * ROWS + row), 0.0f);
            }
        }
    }

    @Test
    public void testBroadcast() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 122);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opBroadcast, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                assertEquals("broadcast at (" + row + ", " + column + ")", //
                        in.get(column), out.get(row * COLUMNS + column), 0.0f);
            }
        }
    }

    @Test
    public void testReshape() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 123);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opReshape, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        // Row-major in both shapes, so linear order is preserved element for element.
        for (int i = 0; i < ELEMENTS; i++) {
            assertEquals("reshape at " + i, in.get(i), out.get(i), 0.0f);
        }
    }

    @Test
    public void testExtract() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 124);
        FloatArray out = new FloatArray(ROWS * HALF);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opExtract, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < HALF; column++) {
                assertEquals("extract at (" + row + ", " + column + ")", //
                        in.get(row * COLUMNS + HALF + column), out.get(row * HALF + column), 0.0f);
            }
        }
    }

    @Test
    public void testConcat() throws TornadoExecutionPlanException {
        FloatArray a = floats(ROWS * HALF, Domain.SYMMETRIC, 125);
        FloatArray b = floats(ROWS * HALF, Domain.SYMMETRIC, 126);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::opConcat, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < HALF; column++) {
                assertEquals("concat left at (" + row + ", " + column + ")", //
                        a.get(row * HALF + column), out.get(row * COLUMNS + column), 0.0f);
                assertEquals("concat right at (" + row + ", " + column + ")", //
                        b.get(row * HALF + column), out.get(row * COLUMNS + HALF + column), 0.0f);
            }
        }
    }

    @Test
    public void testCast() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 127);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opCast, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            // element_cast truncates towards zero, like a C cast; it does not round.
            assertEquals("cast at " + i, (int) in.get(i), out.get(i));
        }
    }

    @Test
    public void testBitcast() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 128);
        IntArray out = new IntArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opBitcast, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertEquals("bitcast at " + i, Float.floatToRawIntBits(in.get(i)), out.get(i));
        }
    }

    @Test
    public void testViewRank1() throws TornadoExecutionPlanException {
        FloatArray in = floats(COLUMNS, Domain.SYMMETRIC, 131);
        FloatArray out = new FloatArray(COLUMNS);
        WorkerGrid1D worker = new WorkerGrid1D(1);
        worker.setLocalWork(1, 1, 1);
        executeWith(new GridScheduler("op.k", worker), new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opViewRank1, new TileContext(), in, out, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < COLUMNS; i++) {
            assertEquals("rank-1 copy at " + i, in.get(i), out.get(i), 0.0f);
        }
    }

    @Test
    public void testViewRank2() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 132);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opViewRank2, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertEquals("rank-2 copy at " + i, in.get(i), out.get(i), 0.0f);
        }
    }

    @Test
    public void testViewRank3() throws TornadoExecutionPlanException {
        final int batches = 2;
        FloatArray in = floats(batches * ELEMENTS, Domain.SYMMETRIC, 133);
        FloatArray out = new FloatArray(batches * ELEMENTS);
        WorkerGrid1D worker = new WorkerGrid1D(batches);
        worker.setLocalWork(1, 1, 1);
        executeWith(new GridScheduler("op.k", worker), new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opViewRank3, new TileContext(), in, out, batches, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < batches * ELEMENTS; i++) {
            assertEquals("rank-3 copy at " + i, in.get(i), out.get(i), 0.0f);
        }
    }

    @Test
    public void testViewStrided() throws TornadoExecutionPlanException {
        FloatArray in = floats(OFFSET + ROWS * PITCH, Domain.SYMMETRIC, 134);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opViewStrided, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                assertEquals("strided read at (" + row + ", " + column + ")", //
                        in.get(OFFSET + row * PITCH + column), out.get(row * COLUMNS + column), 0.0f);
            }
        }
    }

    @Test
    public void testMaskedLoadStore() throws TornadoExecutionPlanException {
        // Deliberately not a multiple of the tile width, so the tail is ragged.
        final int narrow = COLUMNS - 12;
        FloatArray in = floats(ROWS * narrow, Domain.SYMMETRIC, 135);
        FloatArray out = new FloatArray(ROWS * narrow);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opMaskedLoadStore, new TileContext(), in, out, ROWS, narrow) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ROWS * narrow; i++) {
            assertEquals("masked copy at " + i, in.get(i) * 2.0f, out.get(i), 1e-4f);
        }
    }

    @Test
    public void testFloatAtomics() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 141);
        FloatArray sums = new FloatArray(ELEMENTS);
        FloatArray differences = new FloatArray(ELEMENTS);
        for (int i = 0; i < ELEMENTS; i++) {
            sums.set(i, 1.0f);
            differences.set(i, 1.0f);
        }
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, sums, differences) //
                .task("k", TestTileOpLevel::opFloatAtomics, new TileContext(), in, sums, differences, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sums, differences));
        for (int i = 0; i < ELEMENTS; i++) {
            float x = in.get(i);
            assertClose("atomicAdd at " + i, 1.0 + x, sums.get(i), TOL);
            assertClose("atomicSub at " + i, 1.0 - x, differences.get(i), TOL);
        }
    }

    @Test
    public void testIntegerAtomics() throws TornadoExecutionPlanException {
        IntArray in = ints(Domain.INT_BITS, 142);
        IntArray minima = new IntArray(ELEMENTS);
        IntArray maxima = new IntArray(ELEMENTS);
        IntArray masked = new IntArray(ELEMENTS);
        IntArray merged = new IntArray(ELEMENTS);
        IntArray flipped = new IntArray(ELEMENTS);
        IntArray swapped = new IntArray(ELEMENTS);
        final int seed = 0x5A5A5A5A;
        for (int i = 0; i < ELEMENTS; i++) {
            // The seed sits mid-range, so min and max each have to move some elements and leave
            // others alone - a seed at an extreme would pass even if the atomic did nothing.
            minima.set(i, seed);
            maxima.set(i, seed);
            masked.set(i, seed);
            merged.set(i, seed);
            flipped.set(i, seed);
            swapped.set(i, seed);
        }
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, minima, maxima, masked, merged, flipped, swapped) //
                .task("k", TestTileOpLevel::opIntegerAtomics, new TileContext(), in, minima, maxima, masked, merged, flipped, swapped, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, minima, maxima, masked, merged, flipped, swapped));
        for (int i = 0; i < ELEMENTS; i++) {
            int x = in.get(i);
            assertEquals("atomicMin at " + i, Math.min(seed, x), minima.get(i));
            assertEquals("atomicMax at " + i, Math.max(seed, x), maxima.get(i));
            assertEquals("atomicAnd at " + i, seed & x, masked.get(i));
            assertEquals("atomicOr at " + i, seed | x, merged.get(i));
            assertEquals("atomicXor at " + i, seed ^ x, flipped.get(i));
            assertEquals("atomicExchange at " + i, x, swapped.get(i));
        }
    }

    @Test
    public void testAtomicLoadStore() throws TornadoExecutionPlanException {
        FloatArray in = floats(Domain.SYMMETRIC, 143);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opAtomicLoadStore, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertEquals("atomic load/store at " + i, in.get(i), out.get(i), 0.0f);
        }
    }

    @Test
    public void testBlockId() throws TornadoExecutionPlanException {
        final int blocks = 2;
        FloatArray in = floats(blocks * blocks * ELEMENTS, Domain.SYMMETRIC, 151);
        FloatArray out = new FloatArray(blocks * blocks * ELEMENTS);
        WorkerGrid2D worker = new WorkerGrid2D(blocks, blocks);
        worker.setLocalWork(1, 1, 1);
        executeWith(new GridScheduler("op.k", worker), new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opBlockId, new TileContext(), in, out, blocks * ROWS, blocks * COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < blocks * blocks * ELEMENTS; i++) {
            assertEquals("block-indexed copy at " + i, in.get(i), out.get(i), 0.0f);
        }
    }

    @Test
    public void testNumBlocks() throws TornadoExecutionPlanException {
        final int tiles = 4;
        final int blocks = 2;
        FloatArray in = floats(tiles * ELEMENTS, Domain.SYMMETRIC, 152);
        FloatArray out = new FloatArray(tiles * ELEMENTS);
        WorkerGrid1D worker = new WorkerGrid1D(blocks);
        worker.setLocalWork(1, 1, 1);
        executeWith(new GridScheduler("op.k", worker), new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::opNumBlocks, new TileContext(), in, out, tiles * ROWS, COLUMNS, tiles) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < tiles * ELEMENTS; i++) {
            assertEquals("persistent stride at " + i, in.get(i) * 2.0f, out.get(i), 1e-4f);
        }
    }

    @Test
    public void testFp16Add() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(ELEMENTS, 201);
        HalfFloatArray b = halves(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp16Add, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i).getFloat32();
            double y = b.get(i).getFloat32();
            assertClose("fp16 add at " + i, x + y, out.get(i), HALF_TOL);
        }
    }

    @Test
    public void testFp16Sub() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(ELEMENTS, 201);
        HalfFloatArray b = halves(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp16Sub, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i).getFloat32();
            double y = b.get(i).getFloat32();
            assertClose("fp16 sub at " + i, x - y, out.get(i), HALF_TOL);
        }
    }

    @Test
    public void testFp16Mul() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(ELEMENTS, 201);
        HalfFloatArray b = halves(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp16Mul, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i).getFloat32();
            double y = b.get(i).getFloat32();
            assertClose("fp16 mul at " + i, x * y, out.get(i), HALF_TOL);
        }
    }

    @Test
    public void testFp16Div() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(ELEMENTS, 201);
        HalfFloatArray b = halves(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp16Div, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i).getFloat32();
            double y = b.get(i).getFloat32();
            assertClose("fp16 div at " + i, x / y, out.get(i), HALF_TOL);
        }
    }

    @Test
    public void testFp16Maximum() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(ELEMENTS, 201);
        HalfFloatArray b = halves(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp16Maximum, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.get(i).getFloat32();
            double y = b.get(i).getFloat32();
            assertClose("fp16 maximum at " + i, Math.max(x, y), out.get(i), HALF_TOL);
        }
    }

    @Test
    public void testFp16Exp() throws TornadoExecutionPlanException {
        HalfFloatArray in = halves(ELEMENTS, 203);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::fp16Exp, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertClose("fp16 exp at " + i, Math.exp(in.get(i).getFloat32()), out.get(i), HALF_TOL);
        }
    }

    @Test
    public void testFp16Sum() throws TornadoExecutionPlanException {
        HalfFloatArray in = halves(ELEMENTS, 204);
        FloatArray out = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::fp16Sum, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double expected = 0.0;
            for (int column = 0; column < COLUMNS; column++) {
                expected += in.get(row * COLUMNS + column).getFloat32();
            }
            // A narrow accumulator over 32 terms, so the tolerance is the element type's, not fp32's.
            assertClose("fp16 sum of row " + row, expected, out.get(row), HALF_TOL * 4.0);
        }
    }

    @Test
    public void testFp16Mma() throws TornadoExecutionPlanException {
        HalfFloatArray a = halves(MMA * MMA, 205);
        HalfFloatArray b = halves(MMA * MMA, 206);
        FloatArray out = new FloatArray(MMA * MMA);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp16Mma, new TileContext(), a, b, out, MMA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < MMA; row++) {
            for (int column = 0; column < MMA; column++) {
                double expected = 0.0;
                for (int step = 0; step < MMA; step++) {
                    expected += a.get(row * MMA + step).getFloat32() * b.get(step * MMA + column).getFloat32();
                }
                assertClose("fp16 mma at (" + row + ", " + column + ")", expected, out.get(row * MMA + column), HALF_TOL * 4.0);
            }
        }
    }

    @Test
    public void testBf16Add() throws TornadoExecutionPlanException {
        BFloat16Array a = bfloats(ELEMENTS, 201);
        BFloat16Array b = bfloats(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::bf16Add, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getFloat(i);
            double y = b.getFloat(i);
            assertClose("bf16 add at " + i, x + y, out.get(i), BF16_TOL);
        }
    }

    @Test
    public void testBf16Sub() throws TornadoExecutionPlanException {
        BFloat16Array a = bfloats(ELEMENTS, 201);
        BFloat16Array b = bfloats(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::bf16Sub, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getFloat(i);
            double y = b.getFloat(i);
            assertClose("bf16 sub at " + i, x - y, out.get(i), BF16_TOL);
        }
    }

    @Test
    public void testBf16Mul() throws TornadoExecutionPlanException {
        BFloat16Array a = bfloats(ELEMENTS, 201);
        BFloat16Array b = bfloats(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::bf16Mul, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getFloat(i);
            double y = b.getFloat(i);
            assertClose("bf16 mul at " + i, x * y, out.get(i), BF16_TOL);
        }
    }

    @Test
    public void testBf16Div() throws TornadoExecutionPlanException {
        BFloat16Array a = bfloats(ELEMENTS, 201);
        BFloat16Array b = bfloats(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::bf16Div, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getFloat(i);
            double y = b.getFloat(i);
            assertClose("bf16 div at " + i, x / y, out.get(i), BF16_TOL);
        }
    }

    @Test
    public void testBf16Maximum() throws TornadoExecutionPlanException {
        BFloat16Array a = bfloats(ELEMENTS, 201);
        BFloat16Array b = bfloats(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::bf16Maximum, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getFloat(i);
            double y = b.getFloat(i);
            assertClose("bf16 maximum at " + i, Math.max(x, y), out.get(i), BF16_TOL);
        }
    }

    @Test
    public void testBf16Exp() throws TornadoExecutionPlanException {
        BFloat16Array in = bfloats(ELEMENTS, 203);
        FloatArray out = new FloatArray(ELEMENTS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::bf16Exp, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertClose("bf16 exp at " + i, Math.exp(in.getFloat(i)), out.get(i), BF16_TOL);
        }
    }

    @Test
    public void testBf16Sum() throws TornadoExecutionPlanException {
        BFloat16Array in = bfloats(ELEMENTS, 204);
        FloatArray out = new FloatArray(ROWS);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::bf16Sum, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double expected = 0.0;
            for (int column = 0; column < COLUMNS; column++) {
                expected += in.getFloat(row * COLUMNS + column);
            }
            // A narrow accumulator over 32 terms, so the tolerance is the element type's, not fp32's.
            assertClose("bf16 sum of row " + row, expected, out.get(row), BF16_TOL * 4.0);
        }
    }

    @Test
    public void testBf16Mma() throws TornadoExecutionPlanException {
        BFloat16Array a = bfloats(MMA * MMA, 205);
        BFloat16Array b = bfloats(MMA * MMA, 206);
        FloatArray out = new FloatArray(MMA * MMA);
        execute(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::bf16Mma, new TileContext(), a, b, out, MMA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < MMA; row++) {
            for (int column = 0; column < MMA; column++) {
                double expected = 0.0;
                for (int step = 0; step < MMA; step++) {
                    expected += a.getFloat(row * MMA + step) * b.getFloat(step * MMA + column);
                }
                assertClose("bf16 mma at (" + row + ", " + column + ")", expected, out.get(row * MMA + column), BF16_TOL * 4.0);
            }
        }
    }

    @Test
    public void testFp8Add() throws TornadoExecutionPlanException {
        FP8Array a = fp8s(ELEMENTS, 201);
        FP8Array b = fp8s(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp8Add, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getE4M3(i);
            double y = b.getE4M3(i);
            assertClose("fp8 add at " + i, x + y, out.get(i), FP8_TOL);
        }
    }

    @Test
    public void testFp8Sub() throws TornadoExecutionPlanException {
        FP8Array a = fp8s(ELEMENTS, 201);
        FP8Array b = fp8s(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp8Sub, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getE4M3(i);
            double y = b.getE4M3(i);
            assertClose("fp8 sub at " + i, x - y, out.get(i), FP8_TOL);
        }
    }

    @Test
    public void testFp8Mul() throws TornadoExecutionPlanException {
        FP8Array a = fp8s(ELEMENTS, 201);
        FP8Array b = fp8s(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp8Mul, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getE4M3(i);
            double y = b.getE4M3(i);
            assertClose("fp8 mul at " + i, x * y, out.get(i), FP8_TOL);
        }
    }

    @Test
    public void testFp8Div() throws TornadoExecutionPlanException {
        FP8Array a = fp8s(ELEMENTS, 201);
        FP8Array b = fp8s(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp8Div, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getE4M3(i);
            double y = b.getE4M3(i);
            assertClose("fp8 div at " + i, x / y, out.get(i), FP8_TOL);
        }
    }

    @Test
    public void testFp8Maximum() throws TornadoExecutionPlanException {
        FP8Array a = fp8s(ELEMENTS, 201);
        FP8Array b = fp8s(ELEMENTS, 202);
        FloatArray out = new FloatArray(ELEMENTS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp8Maximum, new TileContext(), a, b, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            double x = a.getE4M3(i);
            double y = b.getE4M3(i);
            assertClose("fp8 maximum at " + i, Math.max(x, y), out.get(i), FP8_TOL);
        }
    }

    @Test
    public void testFp8Exp() throws TornadoExecutionPlanException {
        FP8Array in = fp8s(ELEMENTS, 203);
        FloatArray out = new FloatArray(ELEMENTS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::fp8Exp, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int i = 0; i < ELEMENTS; i++) {
            assertClose("fp8 exp at " + i, Math.exp(in.getE4M3(i)), out.get(i), FP8_TOL);
        }
    }

    @Test
    public void testFp8Sum() throws TornadoExecutionPlanException {
        FP8Array in = fp8s(ELEMENTS, 204);
        FloatArray out = new FloatArray(ROWS);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileOpLevel::fp8Sum, new TileContext(), in, out, ROWS, COLUMNS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < ROWS; row++) {
            double expected = 0.0;
            for (int column = 0; column < COLUMNS; column++) {
                expected += in.getE4M3(row * COLUMNS + column);
            }
            // A narrow accumulator over 32 terms, so the tolerance is the element type's, not fp32's.
            assertClose("fp8 sum of row " + row, expected, out.get(row), FP8_TOL * 4.0);
        }
    }

    @Test
    public void testFp8Mma() throws TornadoExecutionPlanException {
        FP8Array a = fp8s(MMA * MMA, 205);
        FP8Array b = fp8s(MMA * MMA, 206);
        FloatArray out = new FloatArray(MMA * MMA);
        executeGated(new TaskGraph("op") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileOpLevel::fp8Mma, new TileContext(), a, b, out, MMA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        for (int row = 0; row < MMA; row++) {
            for (int column = 0; column < MMA; column++) {
                double expected = 0.0;
                for (int step = 0; step < MMA; step++) {
                    expected += a.getE4M3(row * MMA + step) * b.getE4M3(step * MMA + column);
                }
                assertClose("fp8 mma at (" + row + ", " + column + ")", expected, out.get(row * MMA + column), FP8_TOL * 4.0);
            }
        }
    }

}
