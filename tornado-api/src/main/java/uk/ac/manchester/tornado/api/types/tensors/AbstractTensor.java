package uk.ac.manchester.tornado.api.types.tensors;

public interface AbstractTensor {
    Shape getShape();

    String getDTypeAsString();

    DType getDType();

}
