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

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
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

    public TensorView view(IntArray array, int extent) {
        return new TensorView(array, DType.S32, new int[] { extent });
    }

    public TensorView view(IntArray array, int rows, int columns) {
        return new TensorView(array, DType.S32, new int[] { rows, columns });
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

    public Tile iota(DType dtype, int extent) {
        Tile tile = zeros(dtype, extent);
        for (int i = 0; i < extent; i++) {
            tile.getData()[i] = i;
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
        return reduce(a, axis, false);
    }

    private Tile reduce(Tile a, int axis, boolean maximum) {
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
        if (maximum) {
            java.util.Arrays.fill(result.getData(), Double.NEGATIVE_INFINITY);
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int target = axis == 0 ? column : row;
                double value = a.getData()[row * columns + column];
                result.getData()[target] = maximum ? Math.max(result.getData()[target], value) : result.getData()[target] + value;
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
        return reduce(a, axis, true);
    }

    public Tile exp(Tile a) {
        return mapUnary(a, Math::exp);
    }

    public Tile sqrt(Tile a) {
        return mapUnary(a, Math::sqrt);
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
     * Elementwise conversion, lowering to {@code ct::element_cast}. Only widening conversions
     * are legal: CUDA Tile rejects narrowing, so f16 to f32 is fine and f32 to f16 is not.
     */
    public Tile cast(Tile a, DType target) {
        if (!a.getDType().canConvertTo(target)) {
            throw new IllegalArgumentException("[TileContext] CUDA Tile rejects the narrowing conversion "
                    + a.getDType() + " to " + target + ". Only widening element conversions are allowed.");
        }
        return new Tile(target, a.getShape(), a.getData().clone());
    }

    // -------------------------------------------------------------------------------------

    /**
     * Elementwise op with the broadcasting CUDA Tile applies to its operators: a singleton
     * dimension is stretched to match. That is what lets a row reduction, which keeps its
     * reduced dimension as 1, combine with the tile it came from - the shape softmax and
     * RMS norm both rely on.
     */
    private Tile zip(Tile a, Tile b, char operation) {
        int[] shape = broadcastShape(a, b);
        Tile result = Tile.allocate(a.getDType(), shape);
        int rows = shape.length == 2 ? shape[0] : 1;
        int columns = shape.length == 2 ? shape[1] : shape[0];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double left = broadcastRead(a, row, column);
                double right = broadcastRead(b, row, column);
                double value = switch (operation) {
                    case '+' -> left + right;
                    case '-' -> left - right;
                    case '/' -> left / right;
                    default -> left * right;
                };
                result.getData()[row * columns + column] = value;
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
