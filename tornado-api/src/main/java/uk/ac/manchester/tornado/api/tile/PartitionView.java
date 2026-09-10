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
}
