package uk.ac.manchester.tornado.unittests.tensors;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.tensors.DType;
import uk.ac.manchester.tornado.api.types.tensors.Tensor;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestTensorTypes extends TornadoTestBase {

    @Test
    public void testTemporaryValues01() {
        // Define a sample shape
        int[] shape = { 2, 3 };

        Tensor<HalfFloatArray> halfTensor = new Tensor<>(DType.HALF_FLOAT, shape);

        HalfFloatArray halfDataArray = halfTensor.getData();
        for (int i = 0; i < halfDataArray.getSize(); i++) {
            halfDataArray.set(i, new HalfFloat((float) (i * 0.1))); // Set element value (assuming set method takes float)
        }

        // Print the tensor information
        System.out.println("Half-precision tensor:");
        System.out.println("Shape: " + Arrays.toString(halfTensor.getShape()));
        System.out.println("Data type: " + halfTensor.getDType());

    }
}
