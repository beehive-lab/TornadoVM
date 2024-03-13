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
package uk.ac.manchester.tornado.api.types.tensors;

import java.util.Arrays;

public record Shape(int... dimensions) {

    public Shape(int... dimensions) {
        this.dimensions = dimensions.clone();
    }

    public int[] dimensions() {
        return dimensions.clone();
    }

    public int getRank() {
        return dimensions.length;
    }

    public int getSize() {
        int size = 1;
        for (int dim : dimensions) {
            size *= dim;
        }
        return size;
    }

    public Shape reshape(int... newDimensions) {
        int newSize = 1;
        for (int dim : newDimensions) {
            if (dim < 0)
                throw new IllegalArgumentException("Dimensions must be non-negative");
            newSize *= dim;
        }
        if (newSize != getSize()) {
            throw new IllegalArgumentException("Total size must remain constant");
        }
        return new Shape(newDimensions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Shape shape = (Shape) o;
        return Arrays.equals(dimensions, shape.dimensions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(dimensions);
    }

    @Override
    public String toString() {
        return STR."Shape{dimensions=\{Arrays.toString(dimensions)}}";
    }

    public String toTensorFlowShapeString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < dimensions.length; i++) {
            sb.append(dimensions[i]);
            if (i < dimensions.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public String toONNXShapeString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < dimensions.length; i++) {
            sb.append("dim_" + i + ": " + dimensions[i]);
            if (i < dimensions.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

}
