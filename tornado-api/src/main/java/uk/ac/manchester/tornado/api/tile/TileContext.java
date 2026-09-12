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
package uk.ac.manchester.tornado.api.tile;

import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Context for tile-parallel kernels, compiled through NVIDIA CUDA Tile.
 *
 * <p>
 * A task whose kernel method takes a {@link TileContext} as its first parameter is a tile
 * task, in the same way that a leading {@code KernelContext} selects the kernel-parallel
 * API. The kernel describes what one tile block does; the tile compiler decides how many
 * real threads back it, which tensor-core instruction to issue, and how tiles are staged
 * through shared memory. Nothing in this API names a thread, a warp, or a fragment.
 * </p>
 *
 * <p>
 * The worker grid of a tile task counts <b>tile blocks</b>, and its local work is pinned to
 * one, because CUDA Tile requires a block dimension of 1x1x1 and owns the thread mapping.
 * </p>
 *
 * <pre>
 * static void mm(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c,
 *                int m, int n, int k) {
 *     PartitionView av = tc.partition(tc.view(a, m, k), 64, 32);
 *     PartitionView bv = tc.partition(tc.view(b, k, n), 32, 64);
 *     PartitionView cv = tc.partition(tc.view(c, m, n), 64, 64);
 *     Tile acc = tc.zeros(DType.F32, 64, 64);
 *     for (int t = 0; t &lt; k / 32; t++) {
 *         acc = tc.mma(av.load(tc.bidX(), t), bv.load(t, tc.bidY()), acc);
 *     }
 *     cv.store(acc, tc.bidX(), tc.bidY());
 * }
 * </pre>
 *
 * <p>
 * Counted loops such as the one above lower to {@code ct::irange}. There is deliberately no
 * range iterator in this API: a plain counted loop is what the existing TornadoVM loop phases
 * already understand, and an {@code Iterable} would put an iterator allocation in the kernel.
 * </p>
 */
public class TileContext {

    private int blockX = 0;

    private int blockY = 0;

    private int blockZ = 0;

    private int blocksX = 1;

    private int blocksY = 1;

    private int blocksZ = 1;

    // -------------------------------------------------------------------------------------
    // Grid, counted in tile blocks. These are methods rather than public fields, unlike
    // KernelContext's thread identifiers: a method call is intrinsified by an invocation
    // plugin like every other tile operation, whereas a field read would have to be special
    // cased in TornadoTaskSpecialisation as the KernelContext fields are.
    // -------------------------------------------------------------------------------------

    /**
     * @return index of this tile block along x, lowering to {@code ct::bid().x}
     */
    public int bidX() {
        return blockX;
    }

    public int bidY() {
        return blockY;
    }

    public int bidZ() {
        return blockZ;
    }

    /**
     * @return number of tile blocks along x, lowering to {@code ct::num_blocks().x}
     */
    public int numBlocksX() {
        return blocksX;
    }

    public int numBlocksY() {
        return blocksY;
    }

    public int numBlocksZ() {
        return blocksZ;
    }

    /**
     * Drives the JVM fallback over a grid of tile blocks. Has no meaning on an accelerator,
     * where the block index comes from {@code ct::bid()}.
     *
     * <p>
     * Host side only. Calling this from inside a kernel is rejected at compile time, because
     * there is no device operation to intrinsify it to.
     * </p>
     */
    public void setBlockIndex(int x, int y, int z) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
    }

    /**
     * Declares the grid the JVM fallback iterates over. Has no meaning on an accelerator,
     * where the grid comes from the worker grid of the task.
     */
    public void setBlockCount(int x, int y, int z) {
        this.blocksX = x;
        this.blocksY = y;
        this.blocksZ = z;
    }

    // -------------------------------------------------------------------------------------
    // Views: buffer -> tensor_span -> partition_view
    // -------------------------------------------------------------------------------------

    public TensorView view(FloatArray array, int extent) {
        return new TensorView(array, DType.F32, new int[] { extent });
    }

    public TensorView view(FloatArray array, int rows, int columns) {
        return new TensorView(array, DType.F32, new int[] { rows, columns });
    }

    public TensorView view(HalfFloatArray array, int extent) {
        return new TensorView(array, DType.F16, new int[] { extent });
    }

    public TensorView view(HalfFloatArray array, int rows, int columns) {
        return new TensorView(array, DType.F16, new int[] { rows, columns });
    }

    public TensorView view(BFloat16Array array, int extent) {
        return new TensorView(array, DType.BF16, new int[] { extent });
    }

    public TensorView view(BFloat16Array array, int rows, int columns) {
        return new TensorView(array, DType.BF16, new int[] { rows, columns });
    }

    public TensorView view(IntArray array, int extent) {
        return new TensorView(array, DType.S32, new int[] { extent });
    }

    public TensorView view(IntArray array, int rows, int columns) {
        return new TensorView(array, DType.S32, new int[] { rows, columns });
    }

    /**
     * A view of an 8-bit integer buffer, the operand type of an integer matmul. An
     * {@code mma} over these accumulates into {@link DType#S32} and lowers to {@code IMMA} on
     * Ada and newer.
     */
    public TensorView view(Int8Array array, int extent) {
        return new TensorView(array, DType.S8, new int[] { extent });
    }

    public TensorView view(Int8Array array, int rows, int columns) {
        return new TensorView(array, DType.S8, new int[] { rows, columns });
    }

    public TensorView view(DoubleArray array, int extent) {
        return new TensorView(array, DType.F64, new int[] { extent });
    }

    public TensorView view(DoubleArray array, int rows, int columns) {
        return new TensorView(array, DType.F64, new int[] { rows, columns });
    }

    /**
     * A view of an 8-bit float buffer. The format is a parameter because {@link FP8Array} is a
     * byte buffer with both an e4m3 and an e5m2 accessor - the buffer does not carry which one
     * it holds, so the kernel has to say.
     *
     * <p>
     * Requires compute capability 9.0 or newer. {@code tileiras} rejects an fp8 tile on Ada
     * (sm_89) with "unsupported type 'f8E4M3FN'", so the compiler gate refuses it early with a
     * message that names the requirement instead.
     * </p>
     */
    public TensorView view(FP8Array array, DType format, int extent) {
        return new TensorView(array, requireFp8(format), new int[] { extent });
    }

    public TensorView view(FP8Array array, DType format, int rows, int columns) {
        return new TensorView(array, requireFp8(format), new int[] { rows, columns });
    }

    private static DType requireFp8(DType format) {
        if (format != DType.FP8_E4M3 && format != DType.FP8_E5M2) {
            throw new IllegalArgumentException("[TileContext] A view of an FP8Array needs FP8_E4M3 or FP8_E5M2 as its format, got " + format + ".");
        }
        return format;
    }

    /**
     * A rank-3 view, for a batch or head dimension that would otherwise be folded into the row
     * index. CUDA Tile views go to higher rank still; three is what the kernels in the test
     * suite need.
     */
    public TensorView view(FloatArray array, int extent0, int extent1, int extent2) {
        return new TensorView(array, DType.F32, new int[] { extent0, extent1, extent2 });
    }

    public TensorView view(HalfFloatArray array, int extent0, int extent1, int extent2) {
        return new TensorView(array, DType.F16, new int[] { extent0, extent1, extent2 });
    }

    public TensorView view(BFloat16Array array, int extent0, int extent1, int extent2) {
        return new TensorView(array, DType.BF16, new int[] { extent0, extent1, extent2 });
    }

    public TensorView view(IntArray array, int extent0, int extent1, int extent2) {
        return new TensorView(array, DType.S32, new int[] { extent0, extent1, extent2 });
    }

    public TensorView view(Int8Array array, int extent0, int extent1, int extent2) {
        return new TensorView(array, DType.S8, new int[] { extent0, extent1, extent2 });
    }

    public TensorView view(DoubleArray array, int extent0, int extent1, int extent2) {
        return new TensorView(array, DType.F64, new int[] { extent0, extent1, extent2 });
    }

    public PartitionView partition(TensorView view, int tileExtent) {
        checkShape(tileExtent);
        return new PartitionView(view, new int[] { tileExtent });
    }

    public PartitionView partition(TensorView view, int tileRows, int tileColumns) {
        checkShape(tileRows);
        checkShape(tileColumns);
        return new PartitionView(view, new int[] { tileRows, tileColumns });
    }

    public PartitionView partition(TensorView view, int tile0, int tile1, int tile2) {
        checkShape(tile0);
        checkShape(tile1);
        checkShape(tile2);
        return new PartitionView(view, new int[] { tile0, tile1, tile2 });
    }

    // -------------------------------------------------------------------------------------
    // Tile creation
    // -------------------------------------------------------------------------------------

    public Tile zeros(DType dtype, int extent) {
        checkShape(extent);
        return Tile.allocate(dtype, new int[] { extent });
    }

    public Tile zeros(DType dtype, int rows, int columns) {
        checkShape(rows);
        checkShape(columns);
        return Tile.allocate(dtype, new int[] { rows, columns });
    }

    public Tile full(DType dtype, double value, int extent) {
        Tile tile = zeros(dtype, extent);
        java.util.Arrays.fill(tile.getData(), value);
        return tile;
    }

    public Tile full(DType dtype, double value, int rows, int columns) {
        Tile tile = zeros(dtype, rows, columns);
        java.util.Arrays.fill(tile.getData(), value);
        return tile;
    }

    /** A tile of ones, {@code ct::ones}. */
    public Tile ones(DType dtype, int extent) {
        return full(dtype, 1.0, extent);
    }

    public Tile ones(DType dtype, int rows, int columns) {
        return full(dtype, 1.0, rows, columns);
    }

    public Tile iota(DType dtype, int extent) {
        Tile tile = zeros(dtype, extent);
        for (int i = 0; i < extent; i++) {
            tile.getData()[i] = i;
        }
        return tile;
    }

    /**
     * Rank-2 {@code iota}, numbering elements in row-major order. The useful shape is
     * {@code (1, n)}: a row of indices that broadcasts against tiles loaded from a partition
     * view of the same width, which is what a position-dependent schedule such as RoPE needs.
     */
    public Tile iota(DType dtype, int rows, int columns) {
        Tile tile = zeros(dtype, rows, columns);
        for (int i = 0; i < rows * columns; i++) {
            tile.getData()[i] = i % columns;
        }
        return tile;
    }

    // -------------------------------------------------------------------------------------
    // Elementwise
    // -------------------------------------------------------------------------------------

    public Tile add(Tile a, Tile b) {
        return zip(a, b, '+');
    }

    public Tile sub(Tile a, Tile b) {
        return zip(a, b, '-');
    }

    public Tile mul(Tile a, Tile b) {
        return zip(a, b, '*');
    }

    /**
     * Elementwise division, with the same broadcasting as the other operators. Lowers to the
     * {@code /} operator, which is how CUDA Tile spells it; there is no named divide.
     */
    public Tile div(Tile a, Tile b) {
        return zip(a, b, '/');
    }

    /**
     * Elementwise maximum of two tiles, with broadcasting. Lowers to {@code ct::max}, which in
     * CUDA Tile is the two-operand form; the reduction is {@link #max(Tile, int)} and lowers to
     * {@code ct::reduce_max}. Keeping both is what allows a running maximum across chunks of a
     * row wider than one tile.
     */
    public Tile maximum(Tile a, Tile b) {
        return zip(a, b, 'M');
    }

    /**
     * Elementwise minimum, {@code ct::min}. The counterpart of {@link #maximum(Tile, Tile)};
     * both are the two-operand form, not a reduction.
     */
    public Tile minimum(Tile a, Tile b) {
        return zip(a, b, (left, right) -> Math.min(left, right));
    }

    // -------------------------------------------------------------------------------------
    // Comparisons and select
    // -------------------------------------------------------------------------------------

    /**
     * Elementwise {@code a < b}, yielding a predicate tile.
     *
     * <p>
     * A comparison is the only way to produce a {@link DType#PRED} tile, and
     * {@link #select(Tile, Tile, Tile)} is the only thing that consumes one. Together they are
     * what makes a data-dependent kernel expressible: causal masking, a ragged sequence tail,
     * a clamp, a ReLU. Without them a tile kernel can only compute the same arithmetic for
     * every element.
     * </p>
     */
    public Tile lessThan(Tile a, Tile b) {
        return compare(a, b, "<");
    }

    /** Elementwise {@code a < scalar}. */
    public Tile lessThan(Tile a, double scalar) {
        return compareScalar(a, scalar, "<");
    }

    public Tile lessOrEqual(Tile a, Tile b) {
        return compare(a, b, "<=");
    }

    public Tile lessOrEqual(Tile a, double scalar) {
        return compareScalar(a, scalar, "<=");
    }

    public Tile greaterThan(Tile a, Tile b) {
        return compare(a, b, ">");
    }

    public Tile greaterThan(Tile a, double scalar) {
        return compareScalar(a, scalar, ">");
    }

    public Tile greaterOrEqual(Tile a, Tile b) {
        return compare(a, b, ">=");
    }

    public Tile greaterOrEqual(Tile a, double scalar) {
        return compareScalar(a, scalar, ">=");
    }

    public Tile equalTo(Tile a, Tile b) {
        return compare(a, b, "==");
    }

    public Tile equalTo(Tile a, double scalar) {
        return compareScalar(a, scalar, "==");
    }

    public Tile notEqualTo(Tile a, Tile b) {
        return compare(a, b, "!=");
    }

    public Tile notEqualTo(Tile a, double scalar) {
        return compareScalar(a, scalar, "!=");
    }

    /**
     * Elementwise conjunction of two predicate tiles, {@code a & b}. Spelled with the bitwise
     * operator rather than {@code &&} because that is what composes two masks in CUDA Tile.
     */
    public Tile logicalAnd(Tile a, Tile b) {
        return predicateOf(zip(a, b, (left, right) -> left != 0.0 && right != 0.0 ? 1.0 : 0.0));
    }

    /** Elementwise disjunction of two predicate tiles, {@code a | b}. */
    public Tile logicalOr(Tile a, Tile b) {
        return predicateOf(zip(a, b, (left, right) -> left != 0.0 || right != 0.0 ? 1.0 : 0.0));
    }

    /**
     * Elementwise choice, {@code ct::select}: the element of {@code whenTrue} where the
     * predicate holds, of {@code whenFalse} where it does not.
     */
    public Tile select(Tile predicate, Tile whenTrue, Tile whenFalse) {
        if (predicate.getDType() != DType.PRED) {
            throw new IllegalArgumentException("[TileContext] The first argument of select must be a comparison result, not a "
                    + predicate.getDType() + " tile.");
        }
        Tile result = Tile.allocate(whenTrue.getDType(), whenTrue.getShape());
        for (int i = 0; i < result.getElementCount(); i++) {
            result.getData()[i] = predicate.getData()[i] != 0.0 ? whenTrue.getData()[i] : whenFalse.getData()[i];
        }
        return result;
    }

    private Tile compare(Tile a, Tile b, String operator) {
        return predicateOf(zip(a, b, (left, right) -> holds(left, right, operator) ? 1.0 : 0.0));
    }

    private Tile compareScalar(Tile a, double scalar, String operator) {
        Tile result = Tile.allocate(DType.PRED, a.getShape());
        for (int i = 0; i < a.getElementCount(); i++) {
            result.getData()[i] = holds(a.getData()[i], scalar, operator) ? 1.0 : 0.0;
        }
        return result;
    }

    private static boolean holds(double left, double right, String operator) {
        return switch (operator) {
            case "<" -> left < right;
            case "<=" -> left <= right;
            case ">" -> left > right;
            case ">=" -> left >= right;
            case "==" -> left == right;
            default -> left != right;
        };
    }

    /** Retypes a zip result as a predicate, since {@link #zip} keeps the operand type. */
    private static Tile predicateOf(Tile computed) {
        return new Tile(DType.PRED, computed.getShape(), computed.getData());
    }

    public Tile scale(Tile a, double scalar) {
        Tile result = Tile.allocate(a.getDType(), a.getShape());
        for (int i = 0; i < a.getElementCount(); i++) {
            result.getData()[i] = a.getData()[i] * scalar;
        }
        return result;
    }

    // -------------------------------------------------------------------------------------
    // Matrix multiply accumulate: tensor cores, instruction chosen by the tile compiler
    // -------------------------------------------------------------------------------------

    /**
     * Computes {@code a * b + acc} over whole tiles. Lowers to {@code ct::mma}.
     *
     * @param a
     *     left operand, shape MxK
     * @param b
     *     right operand, shape KxN
     * @param acc
     *     accumulator, shape MxN; its element type must be a legal accumulator for the
     *     operand type
     * @return a new accumulator tile of shape MxN
     */
    public Tile mma(Tile a, Tile b, Tile acc) {
        if (a.getRank() != 2 || b.getRank() != 2 || acc.getRank() != 2) {
            throw new IllegalArgumentException("[TileContext] mma operates on rank-2 tiles.");
        }
        int m = a.getDimension(0);
        int k = a.getDimension(1);
        int n = b.getDimension(1);
        if (b.getDimension(0) != k || acc.getDimension(0) != m || acc.getDimension(1) != n) {
            throw new IllegalArgumentException("[TileContext] mma shape mismatch: " + m + "x" + k + " times "
                    + b.getDimension(0) + "x" + n + " into " + acc.getDimension(0) + "x" + acc.getDimension(1) + ".");
        }
        if (!a.getDType().canAccumulateInto(acc.getDType())) {
            throw new IllegalArgumentException("[TileContext] CUDA Tile does not accumulate " + a.getDType()
                    + " operands into a " + acc.getDType() + " tile.");
        }
        Tile result = Tile.allocate(acc.getDType(), acc.getShape());
        for (int row = 0; row < m; row++) {
            for (int column = 0; column < n; column++) {
                double sum = acc.getData()[row * n + column];
                for (int inner = 0; inner < k; inner++) {
                    sum += a.getData()[row * k + inner] * b.getData()[inner * n + column];
                }
                result.getData()[row * n + column] = sum;
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------------------
    // Reductions and reshapes
    // -------------------------------------------------------------------------------------

    /**
     * Sums along {@code axis}, keeping the reduced dimension, as {@code ct::sum} does in
     * CUDA Tile C++.
     */
    public Tile sum(Tile a, int axis) {
        return reduce(a, axis, Reduction.SUM);
    }

    private enum Reduction {
        SUM,
        MAXIMUM,
        MINIMUM,
        PRODUCT,
        ALL,
        ANY,
        BIT_AND,
        BIT_OR,
        BIT_XOR
    }

    private Tile reduce(Tile a, int axis, Reduction kind) {
        if (a.getRank() != 2) {
            throw new IllegalArgumentException("[TileContext] Reductions currently support rank-2 tiles.");
        }
        if (axis != 0 && axis != 1) {
            throw new IllegalArgumentException("[TileContext] Reduction axis must be 0 or 1, got " + axis + ".");
        }
        int rows = a.getDimension(0);
        int columns = a.getDimension(1);
        int[] shape = axis == 0 ? new int[] { 1, columns } : new int[] { rows, 1 };
        Tile result = Tile.allocate(a.getDType(), shape);
        double initial = switch (kind) {
            case SUM, ANY, BIT_OR, BIT_XOR -> 0.0;
            case MAXIMUM -> Double.NEGATIVE_INFINITY;
            case MINIMUM -> Double.POSITIVE_INFINITY;
            case PRODUCT, ALL -> 1.0;
            case BIT_AND -> -1.0;
        };
        java.util.Arrays.fill(result.getData(), initial);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int target = axis == 0 ? column : row;
                double value = a.getData()[row * columns + column];
                double accumulated = result.getData()[target];
                result.getData()[target] = switch (kind) {
                    case SUM -> accumulated + value;
                    case MAXIMUM -> Math.max(accumulated, value);
                    case MINIMUM -> Math.min(accumulated, value);
                    case PRODUCT -> accumulated * value;
                    case ALL -> accumulated != 0.0 && value != 0.0 ? 1.0 : 0.0;
                    case ANY -> accumulated != 0.0 || value != 0.0 ? 1.0 : 0.0;
                    case BIT_AND -> (int) accumulated & (int) value;
                    case BIT_OR -> (int) accumulated | (int) value;
                    case BIT_XOR -> (int) accumulated ^ (int) value;
                };
            }
        }
        return result;
    }

    /**
     * Maximum along {@code axis}, keeping the reduced dimension. Lowers to
     * {@code ct::reduce_max}: in CUDA Tile {@code ct::max} is the elementwise two-operand form,
     * and the reduction is a differently named function.
     */
    public Tile max(Tile a, int axis) {
        return reduce(a, axis, Reduction.MAXIMUM);
    }

    /**
     * Minimum along {@code axis}, keeping the reduced dimension. Lowers to
     * {@code ct::reduce_min}.
     */
    public Tile min(Tile a, int axis) {
        return reduce(a, axis, Reduction.MINIMUM);
    }

    /**
     * Product along {@code axis}, keeping the reduced dimension. Lowers to {@code ct::prod}.
     */
    public Tile prod(Tile a, int axis) {
        return reduce(a, axis, Reduction.PRODUCT);
    }

    /**
     * True where every element along {@code axis} holds, {@code ct::all_of}. Takes a predicate
     * tile and produces one, so it composes with {@link #select(Tile, Tile, Tile)}.
     */
    public Tile allOf(Tile a, int axis) {
        return predicateOf(reduce(a, axis, Reduction.ALL));
    }

    /** True where any element along {@code axis} holds, {@code ct::any_of}. */
    public Tile anyOf(Tile a, int axis) {
        return predicateOf(reduce(a, axis, Reduction.ANY));
    }

    /** Bitwise and along {@code axis}, {@code ct::reduce_bitand}. */
    public Tile reduceBitAnd(Tile a, int axis) {
        return reduce(a, axis, Reduction.BIT_AND);
    }

    /** Bitwise or along {@code axis}, {@code ct::reduce_bitor}. */
    public Tile reduceBitOr(Tile a, int axis) {
        return reduce(a, axis, Reduction.BIT_OR);
    }

    /** Bitwise exclusive or along {@code axis}, {@code ct::reduce_bitxor}. */
    public Tile reduceBitXor(Tile a, int axis) {
        return reduce(a, axis, Reduction.BIT_XOR);
    }

    /**
     * Fused multiply-add, {@code ct::fma(a, b, c)} elementwise. Distinct from
     * {@link #mma(Tile, Tile, Tile)}, which is a matrix product: this one multiplies
     * elementwise and never touches a tensor core.
     */
    public Tile fma(Tile a, Tile b, Tile c) {
        Tile product = mul(a, b);
        return add(product, c);
    }

    public Tile exp(Tile a) {
        return mapUnary(a, Math::exp);
    }

    public Tile sin(Tile a) {
        return mapUnary(a, Math::sin);
    }

    public Tile cos(Tile a) {
        return mapUnary(a, Math::cos);
    }

    public Tile sqrt(Tile a) {
        return mapUnary(a, Math::sqrt);
    }

    /**
     * Natural logarithm, {@code ct::log}. Needed to fold a running maximum and a running
     * denominator into one log-sum-exp value, which is how a split-KV attention kernel hands
     * its partial result to the reduction that combines the splits.
     */
    public Tile log(Tile a) {
        return mapUnary(a, Math::log);
    }

    /**
     * Base-two logarithm, {@code ct::log2}, the counterpart of {@link #exp2(Tile)}.
     */
    public Tile log2(Tile a) {
        return mapUnary(a, value -> Math.log(value) / Math.log(2.0));
    }

    /**
     * Base-two exponential, {@code ct::exp2}. This is the cheaper instruction, and it is what
     * NVIDIA's own tile kernels use for softmax: they fold {@code 1/ln 2} into the score scale
     * and exponentiate base two instead.
     */
    public Tile exp2(Tile a) {
        return mapUnary(a, value -> Math.pow(2.0, value));
    }

    /**
     * Hyperbolic tangent, {@code ct::tanh}. Used for logit soft-capping and for the tanh
     * approximation of GELU.
     */
    public Tile tanh(Tile a) {
        return mapUnary(a, Math::tanh);
    }

    /**
     * Absolute value, {@code ct::abs}.
     */
    public Tile abs(Tile a) {
        return mapUnary(a, Math::abs);
    }

    /**
     * Round towards negative infinity, {@code ct::floor}.
     */
    /** Round towards positive infinity, {@code ct::ceil}. */
    public Tile ceil(Tile a) {
        return mapUnary(a, Math::ceil);
    }

    public Tile tan(Tile a) {
        return mapUnary(a, Math::tan);
    }

    public Tile sinh(Tile a) {
        return mapUnary(a, Math::sinh);
    }

    public Tile cosh(Tile a) {
        return mapUnary(a, Math::cosh);
    }

    /** Two-argument arc tangent, {@code ct::atan2(y, x)}. */
    public Tile atan2(Tile y, Tile x) {
        return zip(y, x, (left, right) -> Math.atan2(left, right));
    }

    /** {@code ct::pow}. */
    public Tile pow(Tile base, Tile exponent) {
        return zip(base, exponent, (left, right) -> Math.pow(left, right));
    }

    /** Remainder, {@code ct::remainder}. */
    public Tile remainder(Tile a, Tile b) {
        return zip(a, b, (left, right) -> left % right);
    }

    /** Division rounding towards negative infinity, {@code ct::floordiv}. */
    public Tile floorDiv(Tile a, Tile b) {
        return zip(a, b, (left, right) -> Math.floor(left / right));
    }

    /** Division rounding towards positive infinity, {@code ct::ceildiv}. */
    public Tile ceilDiv(Tile a, Tile b) {
        return zip(a, b, (left, right) -> Math.ceil(left / right));
    }

    /** High half of an integer multiply, {@code ct::mulhi}. */
    public Tile mulhi(Tile a, Tile b) {
        return zip(a, b, (left, right) -> (double) (int) (((long) (int) left * (long) (int) right) >> 32));
    }

    /**
     * True where the element is NaN, {@code ct::isnan}. The result is a predicate tile, so it
     * feeds {@link #select(Tile, Tile, Tile)} like any comparison.
     */
    public Tile isNaN(Tile a) {
        return predicateOf(mapUnary(a, value -> Double.isNaN(value) ? 1.0 : 0.0));
    }

    /** True where the element is an infinity, {@code ct::isinf}. */
    public Tile isInfinite(Tile a) {
        return predicateOf(mapUnary(a, value -> Double.isInfinite(value) ? 1.0 : 0.0));
    }

    /** Negates a predicate tile, {@code !mask}. */
    public Tile logicalNot(Tile a) {
        return predicateOf(mapUnary(a, value -> value != 0.0 ? 0.0 : 1.0));
    }

    public Tile bitwiseAnd(Tile a, Tile b) {
        return zip(a, b, (left, right) -> (int) left & (int) right);
    }

    public Tile bitwiseOr(Tile a, Tile b) {
        return zip(a, b, (left, right) -> (int) left | (int) right);
    }

    public Tile bitwiseXor(Tile a, Tile b) {
        return zip(a, b, (left, right) -> (int) left ^ (int) right);
    }

    /** Bitwise complement, {@code ~tile}. */
    public Tile bitwiseNot(Tile a) {
        return mapUnary(a, value -> ~(int) value);
    }

    public Tile floor(Tile a) {
        return mapUnary(a, Math::floor);
    }

    public Tile rsqrt(Tile a) {
        return mapUnary(a, value -> 1.0 / Math.sqrt(value));
    }

    private Tile mapUnary(Tile a, java.util.function.DoubleUnaryOperator function) {
        Tile result = Tile.allocate(a.getDType(), a.getShape());
        for (int i = 0; i < a.getElementCount(); i++) {
            result.getData()[i] = function.applyAsDouble(a.getData()[i]);
        }
        return result;
    }

    /**
     * Stretches a tile to a larger shape, {@code ct::broadcast}. Only a dimension of extent one
     * may stretch, which is the same rule the elementwise operations apply implicitly; this is
     * the explicit form, for when a row or column vector has to become a full tile before it is
     * used more than once.
     */
    public Tile broadcast(Tile a, int extent) {
        return broadcastTo(a, new int[] { extent });
    }

    public Tile broadcast(Tile a, int rows, int columns) {
        return broadcastTo(a, new int[] { rows, columns });
    }

    private Tile broadcastTo(Tile a, int[] target) {
        if (a.getRank() != target.length) {
            throw new IllegalArgumentException("[TileContext] broadcast cannot change rank: a rank-" + a.getRank()
                    + " tile cannot become rank-" + target.length + ". Use reshape for that.");
        }
        for (int axis = 0; axis < target.length; axis++) {
            int from = a.getDimension(axis);
            if (from != target[axis] && from != 1) {
                throw new IllegalArgumentException("[TileContext] broadcast can only stretch a dimension of extent 1, but axis "
                        + axis + " is " + from + " and the target is " + target[axis] + ".");
            }
        }
        Tile result = Tile.allocate(a.getDType(), target);
        int rows = target.length == 2 ? target[0] : 1;
        int columns = target.length == 2 ? target[1] : target[0];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                result.getData()[row * columns + column] = broadcastRead(a, row, column);
            }
        }
        return result;
    }

    /**
     * Reinterprets a tile with a different shape of the same size, {@code ct::reshape}, in
     * row-major order. This is how a reduction result changes orientation: a {@code sum} along
     * axis 1 gives {@code [rows, 1]}, and a kernel that needs it as {@code [1, rows]} reshapes
     * rather than transposing, which would be a data movement.
     */
    public Tile reshape(Tile a, int extent) {
        return reshapeTo(a, new int[] { extent });
    }

    public Tile reshape(Tile a, int rows, int columns) {
        return reshapeTo(a, new int[] { rows, columns });
    }

    public Tile reshape(Tile a, int extent0, int extent1, int extent2) {
        return reshapeTo(a, new int[] { extent0, extent1, extent2 });
    }

    private Tile reshapeTo(Tile a, int[] target) {
        int size = 1;
        for (int dimension : target) {
            checkShape(dimension);
            size *= dimension;
        }
        if (size != a.getElementCount()) {
            throw new IllegalArgumentException("[TileContext] reshape must keep the element count: " + a.getElementCount()
                    + " elements cannot become " + size + ".");
        }
        return new Tile(a.getDType(), target, a.getData().clone());
    }

    /**
     * Takes one sub-tile out of a tile, {@code ct::extract}.
     *
     * <p>
     * The indices are in units of the sub-tile, not elements, the same way a partition view
     * addresses blocks: {@code extract(row, 1, 32, 0, 1)} of a {@code [1, 64]} tile is elements
     * 32 to 63. Verified against the toolkit rather than assumed.
     * </p>
     *
     * <p>
     * This is what splits a packed row without loading it twice, as a SiLU-and-multiply or a
     * GEGLU has to: one load of the whole row, then two extracts.
     * </p>
     */
    public Tile extract(Tile a, int extent, int block) {
        return extractFrom(a, new int[] { extent }, new int[] { block });
    }

    public Tile extract(Tile a, int rows, int columns, int blockRow, int blockColumn) {
        return extractFrom(a, new int[] { rows, columns }, new int[] { blockRow, blockColumn });
    }

    private Tile extractFrom(Tile a, int[] shape, int[] blockIndices) {
        if (a.getRank() != shape.length) {
            throw new IllegalArgumentException("[TileContext] extract cannot change rank: a rank-" + a.getRank()
                    + " tile cannot yield a rank-" + shape.length + " sub-tile.");
        }
        for (int axis = 0; axis < shape.length; axis++) {
            checkShape(shape[axis]);
            if (a.getDimension(axis) % shape[axis] != 0) {
                throw new IllegalArgumentException("[TileContext] extract needs the sub-tile to divide the tile, but axis "
                        + axis + " is " + a.getDimension(axis) + " and the sub-tile is " + shape[axis] + ".");
            }
        }
        Tile result = Tile.allocate(a.getDType(), shape);
        if (shape.length == 1) {
            int base = blockIndices[0] * shape[0];
            for (int i = 0; i < shape[0]; i++) {
                result.getData()[i] = a.getData()[base + i];
            }
            return result;
        }
        int columns = a.getDimension(1);
        for (int row = 0; row < shape[0]; row++) {
            for (int column = 0; column < shape[1]; column++) {
                int sourceRow = blockIndices[0] * shape[0] + row;
                int sourceColumn = blockIndices[1] * shape[1] + column;
                result.getData()[row * shape[1] + column] = a.getData()[sourceRow * columns + sourceColumn];
            }
        }
        return result;
    }

    public Tile transpose(Tile a) {
        if (a.getRank() != 2) {
            throw new IllegalArgumentException("[TileContext] transpose currently supports rank-2 tiles.");
        }
        int rows = a.getDimension(0);
        int columns = a.getDimension(1);
        Tile result = Tile.allocate(a.getDType(), new int[] { columns, rows });
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                result.getData()[column * rows + row] = a.getData()[row * columns + column];
            }
        }
        return result;
    }

    /**
     * Elementwise conversion, lowering to {@code ct::element_cast}. Any pair of scalar element
     * types is accepted, narrowing included; a narrowing conversion loses precision, and that is
     * the caller's decision rather than an error.
     */
    public Tile cast(Tile a, DType target) {
        double[] values = a.getData().clone();
        if (target == DType.F16) {
            // Mirror half-precision rounding so the JVM fallback agrees with the device.
            for (int i = 0; i < values.length; i++) {
                values[i] = new uk.ac.manchester.tornado.api.types.HalfFloat((float) values[i]).getFloat32();
            }
        }
        return new Tile(target, a.getShape(), values);
    }

    // -------------------------------------------------------------------------------------

    /**
     * Elementwise op with the broadcasting CUDA Tile applies to its operators: a singleton
     * dimension is stretched to match. That is what lets a row reduction, which keeps its
     * reduced dimension as 1, combine with the tile it came from - the shape softmax and
     * RMS norm both rely on.
     */
    private Tile zip(Tile a, Tile b, char operation) {
        // Written as a statement switch over plain lambdas on purpose: a method reference
        // inside a cast in a switch expression crashes javac 21 (DeferredAttr assertion).
        java.util.function.DoubleBinaryOperator function;
        switch (operation) {
            case '+':
                function = (left, right) -> left + right;
                break;
            case '-':
                function = (left, right) -> left - right;
                break;
            case '/':
                function = (left, right) -> left / right;
                break;
            case 'M':
                function = (left, right) -> Math.max(left, right);
                break;
            default:
                function = (left, right) -> left * right;
                break;
        }
        return zip(a, b, function);
    }

    private Tile zip(Tile a, Tile b, java.util.function.DoubleBinaryOperator operation) {
        int[] shape = broadcastShape(a, b);
        Tile result = Tile.allocate(a.getDType(), shape);
        int rows = shape.length == 2 ? shape[0] : 1;
        int columns = shape.length == 2 ? shape[1] : shape[0];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double left = broadcastRead(a, row, column);
                double right = broadcastRead(b, row, column);
                result.getData()[row * columns + column] = operation.applyAsDouble(left, right);
            }
        }
        return result;
    }

    private static int[] broadcastShape(Tile a, Tile b) {
        if (a.getRank() != b.getRank()) {
            throw new IllegalArgumentException("[TileContext] Elementwise operands must have the same rank, got "
                    + a.getRank() + " and " + b.getRank() + ".");
        }
        int[] shape = new int[a.getRank()];
        for (int i = 0; i < shape.length; i++) {
            int left = a.getDimension(i);
            int right = b.getDimension(i);
            if (left != right && left != 1 && right != 1) {
                throw new IllegalArgumentException("[TileContext] Dimension " + i + " does not broadcast: " + left
                        + " against " + right + ". CUDA Tile stretches a dimension only when it is 1.");
            }
            shape[i] = Math.max(left, right);
        }
        return shape;
    }

    private static double broadcastRead(Tile tile, int row, int column) {
        if (tile.getRank() == 1) {
            return tile.getData()[tile.getDimension(0) == 1 ? 0 : column];
        }
        int r = tile.getDimension(0) == 1 ? 0 : row;
        int c = tile.getDimension(1) == 1 ? 0 : column;
        return tile.getData()[r * tile.getDimension(1) + c];
    }

    private static void checkShape(int dimension) {
        if (dimension <= 0 || (dimension & (dimension - 1)) != 0) {
            throw new IllegalArgumentException("[TileContext] Every tile dimension must be a positive power of two, got "
                    + dimension + ". CUDA Tile requires power-of-two, compile-time tile shapes.");
        }
    }
}
