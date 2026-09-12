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

/**
 * A tiling of a {@link TensorView} into non-overlapping, fixed-size tiles, the analogue of
 * {@code ct::partition_view}. Block indices address a tile rather than an element, so a
 * kernel performs no address arithmetic of its own.
 *
 * <p>
 * The unmasked {@code load} and {@code store} forms require the tile to lie entirely inside
 * the tensor; CUDA Tile C++ does not bounds-check them. The masked forms zero-pad reads and
 * discard out-of-bounds writes, and are what the code generator selects whenever the extents
 * are not provably divisible by the tile shape.
 * </p>
 *
 * <p>
 * Every accessor is fixed-arity by rank rather than varargs. A varargs index list would force
 * the invocation plugin to constant-fold an allocation, which is exactly the fragility the MMA
 * intrinsics already have to work around.
 * </p>
 */
public final class PartitionView {

    private final TensorView view;
    private final int[] tileShape;

    PartitionView(TensorView view, int[] tileShape) {
        this.view = view;
        this.tileShape = tileShape;
    }

    public DType getDType() {
        return view.getDType();
    }

    public int getRank() {
        return tileShape.length;
    }

    public int getTileDimension(int axis) {
        return tileShape[axis];
    }

    /**
     * @return the number of whole and partial tiles along {@code axis}.
     */
    public int getBlockCount(int axis) {
        return (view.getExtent(axis) + tileShape[axis] - 1) / tileShape[axis];
    }

    public Tile load(int blockX) {
        return load1D(blockX, false);
    }

    public Tile loadMasked(int blockX) {
        return load1D(blockX, true);
    }

    public Tile load(int blockX, int blockY) {
        return load2D(blockX, blockY, false);
    }

    public Tile loadMasked(int blockX, int blockY) {
        return load2D(blockX, blockY, true);
    }

    public void store(Tile tile, int blockX) {
        store1D(tile, blockX, false);
    }

    public void storeMasked(Tile tile, int blockX) {
        store1D(tile, blockX, true);
    }

    public void store(Tile tile, int blockX, int blockY) {
        store2D(tile, blockX, blockY, false);
    }

    public void storeMasked(Tile tile, int blockX, int blockY) {
        store2D(tile, blockX, blockY, true);
    }

    private Tile load1D(int blockX, boolean masked) {
        checkRank(1);
        Tile tile = Tile.allocate(view.getDType(), tileShape);
        int base = blockX * tileShape[0];
        int extent = view.getExtent(0);
        for (int i = 0; i < tileShape[0]; i++) {
            int index = base + i;
            if (index < extent) {
                tile.getData()[i] = view.readLinear(index);
            } else if (!masked) {
                throw outOfBounds(index, extent);
            }
        }
        return tile;
    }

    private void store1D(Tile tile, int blockX, boolean masked) {
        checkRank(1);
        int base = blockX * tileShape[0];
        int extent = view.getExtent(0);
        for (int i = 0; i < tileShape[0]; i++) {
            int index = base + i;
            if (index < extent) {
                view.writeLinear(index, tile.getData()[i]);
            } else if (!masked) {
                throw outOfBounds(index, extent);
            }
        }
    }

    /**
     * Loads one tile from a rank-3 view.
     *
     * <p>
     * Rank 3 exists for addressing, not for arithmetic: a batch or head dimension stops having
     * to be folded into the row index. The loaded tile is usually reshaped to rank 2 before it
     * meets {@code mma} or a reduction, which is how NVIDIA's own kernels do it - they load a
     * rank-4 tile and reshape it.
     * </p>
     */
    public Tile load(int blockX, int blockY, int blockZ) {
        return load3D(blockX, blockY, blockZ, false);
    }

    public Tile loadMasked(int blockX, int blockY, int blockZ) {
        return load3D(blockX, blockY, blockZ, true);
    }

    public void store(Tile tile, int blockX, int blockY, int blockZ) {
        store3D(tile, blockX, blockY, blockZ, false);
    }

    public void storeMasked(Tile tile, int blockX, int blockY, int blockZ) {
        store3D(tile, blockX, blockY, blockZ, true);
    }

    private Tile load3D(int blockX, int blockY, int blockZ, boolean masked) {
        checkRank(3);
        Tile tile = Tile.allocate(view.getDType(), tileShape);
        int[] blocks = { blockX, blockY, blockZ };
        for (int i = 0; i < tileShape[0]; i++) {
            for (int j = 0; j < tileShape[1]; j++) {
                for (int k = 0; k < tileShape[2]; k++) {
                    int[] within = { i, j, k };
                    int linear = linearIndex(blocks, within, masked);
                    if (linear >= 0) {
                        tile.getData()[(i * tileShape[1] + j) * tileShape[2] + k] = view.readLinear(linear);
                    }
                }
            }
        }
        return tile;
    }

    private void store3D(Tile tile, int blockX, int blockY, int blockZ, boolean masked) {
        checkRank(3);
        int[] blocks = { blockX, blockY, blockZ };
        for (int i = 0; i < tileShape[0]; i++) {
            for (int j = 0; j < tileShape[1]; j++) {
                for (int k = 0; k < tileShape[2]; k++) {
                    int[] within = { i, j, k };
                    int linear = linearIndex(blocks, within, masked);
                    if (linear >= 0) {
                        view.writeLinear(linear, tile.getData()[(i * tileShape[1] + j) * tileShape[2] + k]);
                    }
                }
            }
        }
    }

    /**
     * Row-major linear index of one element of the addressed block, or -1 when it falls outside
     * the view and the access is masked.
     */
    private int linearIndex(int[] blocks, int[] within, boolean masked) {
        int linear = 0;
        int total = 1;
        for (int axis = 0; axis < tileShape.length; axis++) {
            int index = blocks[axis] * tileShape[axis] + within[axis];
            int extent = view.getExtent(axis);
            if (index >= extent) {
                if (masked) {
                    return -1;
                }
                throw outOfBounds(index, extent);
            }
            linear = linear * extent + index;
            total *= extent;
        }
        if (linear >= total) {
            throw outOfBounds(linear, total);
        }
        return linear;
    }

    private Tile load2D(int blockX, int blockY, boolean masked) {
        checkRank(2);
        Tile tile = Tile.allocate(view.getDType(), tileShape);
        int rows = tileShape[0];
        int columns = tileShape[1];
        int extentRows = view.getExtent(0);
        int extentColumns = view.getExtent(1);
        for (int r = 0; r < rows; r++) {
            int row = blockX * rows + r;
            for (int c = 0; c < columns; c++) {
                int column = blockY * columns + c;
                if (row < extentRows && column < extentColumns) {
                    tile.getData()[r * columns + c] = view.readLinear(row * extentColumns + column);
                } else if (!masked) {
                    throw outOfBounds(row * extentColumns + column, extentRows * extentColumns);
                }
            }
        }
        return tile;
    }

    private void store2D(Tile tile, int blockX, int blockY, boolean masked) {
        checkRank(2);
        int rows = tileShape[0];
        int columns = tileShape[1];
        int extentRows = view.getExtent(0);
        int extentColumns = view.getExtent(1);
        for (int r = 0; r < rows; r++) {
            int row = blockX * rows + r;
            for (int c = 0; c < columns; c++) {
                int column = blockY * columns + c;
                if (row < extentRows && column < extentColumns) {
                    view.writeLinear(row * extentColumns + column, tile.getData()[r * columns + c]);
                } else if (!masked) {
                    throw outOfBounds(row * extentColumns + column, extentRows * extentColumns);
                }
            }
        }
    }

    private void checkRank(int expected) {
        if (tileShape.length != expected) {
            throw new IllegalArgumentException("[TileContext] This partition view has rank " + tileShape.length
                    + ", but was addressed with " + expected + " block index(es).");
        }
    }

    private IndexOutOfBoundsException outOfBounds(int index, int extent) {
        return new IndexOutOfBoundsException("[TileContext] Unmasked tile access reached element " + index
                + " of a tensor with " + extent + " elements. Use loadMasked/storeMasked for ragged edges.");
    }

    /**
     * Accumulates a tile into the view atomically: {@code view[block] += tile}, element by
     * element, with every concurrent tile block's contribution kept.
     *
     * <p>
     * This is how several tile blocks combine into one output without a second kernel - a
     * split-K matmul accumulating partial products, or a scatter-add where blocks share rows.
     * </p>
     *
     * <p>
     * CUDA Tile has no atomic read-modify-write on a {@code partition_view}: the view offers
     * only {@code atomic_load} and {@code atomic_store}, while {@code ct::atomic_add} takes a
     * <em>tile of pointers</em>. The code generator therefore computes the pointer tile for the
     * addressed block from the view's base pointer and extents. The ordering is relaxed and the
     * scope is the device, which is what an accumulator in device memory wants; a stricter
     * order would cost more and buy nothing here, since correctness comes from the atomicity of
     * each element's update rather than from ordering between them.
     * </p>
     */
    public void atomicAdd(Tile tile, int blockX) {
        accumulate(tile, new int[] { blockX });
    }

    public void atomicAdd(Tile tile, int blockX, int blockY) {
        accumulate(tile, new int[] { blockX, blockY });
    }

    private void accumulate(Tile tile, int[] blockIndices) {
        // JVM fallback: single threaded, so a plain read-modify-write is the same thing.
        int[] shape = tileShape;
        if (shape.length == 1) {
            int base = blockIndices[0] * shape[0];
            for (int i = 0; i < shape[0]; i++) {
                view.writeLinear(base + i, view.readLinear(base + i) + tile.getElement(i));
            }
            return;
        }
        int columns = view.getExtent(1);
        for (int row = 0; row < shape[0]; row++) {
            for (int column = 0; column < shape[1]; column++) {
                int index = (blockIndices[0] * shape[0] + row) * columns + blockIndices[1] * shape[1] + column;
                view.writeLinear(index, view.readLinear(index) + tile.getElement(row * shape[1] + column));
            }
        }
    }
}
