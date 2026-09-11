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
 * An immutable, compile-time shaped, dense array of scalars: the unit of work of the tile
 * programming model, and the direct analogue of {@code ct::tile<E, ct::shape<...>>}.
 *
 * <p>
 * On an accelerator a {@link Tile} never exists as a Java object. Every {@link TileContext}
 * call that produces one is intrinsified by the backend, and the value lives in whatever
 * registers, shared memory or tensor-core fragments the tile compiler chooses. The fields
 * below exist so that the same kernel method also runs on the JVM, which keeps kernels
 * debuggable and gives unit tests a reference implementation.
 * </p>
 *
 * <p>
 * Every dimension must be a power of two and known at compile time. The code generator
 * rejects a shape that does not constant-fold rather than bailing out of the sketch.
 * </p>
 */
public final class Tile {

    private final DType dtype;
    private final int[] shape;
    private final double[] data;

    Tile(DType dtype, int[] shape, double[] data) {
        this.dtype = dtype;
        this.shape = shape;
        this.data = data;
    }

    static Tile allocate(DType dtype, int[] shape) {
        int elements = 1;
        for (int dimension : shape) {
            elements *= dimension;
        }
        return new Tile(dtype, shape, new double[elements]);
    }

    public DType getDType() {
        return dtype;
    }

    public int getRank() {
        return shape.length;
    }

    public int getDimension(int axis) {
        return shape[axis];
    }

    int[] getShape() {
        return shape;
    }

    double[] getData() {
        return data;
    }

    /**
     * Reads one element of the JVM-side representation, for tests and host-side debugging. There
     * is no device meaning: on an accelerator a tile has no host-visible elements.
     */
    public double getElement(int index) {
        return data[index];
    }

    int getElementCount() {
        return data.length;
    }

    /**
     * @return the CUDA Tile C++ declaration of this tile, for example
     *     {@code ct::tile<float, ct::shape<64, 64>>}. Used by the code generator and by
     *     diagnostics; on the JVM path it is only ever informational.
     */
    public String toCppType() {
        StringBuilder builder = new StringBuilder("ct::tile<");
        builder.append(dtype.getCppType()).append(", ct::shape<");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(shape[i]);
        }
        return builder.append(">>").toString();
    }
}
