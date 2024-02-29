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
        return "Shape{dimensions=" + Arrays.toString(dimensions) + "}";
    }

    public Shape squeeze() {
        int[] newDimensions = Arrays.stream(dimensions).filter(dim -> dim != 1).toArray();
        return new Shape(newDimensions);
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
