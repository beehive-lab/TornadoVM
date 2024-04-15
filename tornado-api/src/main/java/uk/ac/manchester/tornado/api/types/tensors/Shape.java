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

public record Shape(long... dimensions) {

    /**
     * Returns the rank of the shape, which is the number of dimensions.
     *
     * @return the number of dimensions of the shape
     */
    public int getRank() {
        return dimensions.length;
    }

    /**
     * Returns of the dimensions of the shape.
     *
     * @return an array of long values representing the dimensions of the shape
     */
    public long[] getDimensions() {
        return dimensions;
    }

    /**
     * Calculates and returns the size of the shape, which is the product of all its dimensions.
     *
     * @return the total size of the shape as an int
     */
    public int getSize() {
        return (int) Arrays.stream(dimensions).reduce(1, (a, b) -> a * b);
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

    /**
     * Generates a string representation of the shape compatible with TensorFlow's shape format.
     *
     * @return a string representing the shape in TensorFlow's format
     */

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

    /**
     * Generates a string representation of the shape compatible with ONNX's shape format.
     *
     * @return a string representing the shape in ONNX's format
     */
    public String toONNXShapeString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < dimensions.length; i++) {
            sb.append("dim_").append(i).append(": ").append(dimensions[i]);
            if (i < dimensions.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

}
