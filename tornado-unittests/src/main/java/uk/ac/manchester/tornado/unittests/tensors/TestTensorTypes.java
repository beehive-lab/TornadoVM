package uk.ac.manchester.tornado.unittests.tensors;

import org.junit.Test;

import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestTensorTypes extends TornadoTestBase {

    @Test
    public void testTemporaryValues01() {
        // Define a sample shape
        Shape shape = new Shape(2, 3);

        //        Tensor tensor1 = new Tensor(shape, new HalfFloat());

        shape.getSize();

        // Set values in the data array
        //        for (int i = 0; i < halfDataArray.getSize(); i++) {
        //            halfDataArray.set(i, new HalfFloat((float) (i * 0.1))); // Set element value (assuming set method takes float)
        //        }

        //        // Print the tensor information
        //        System.out.println("Half-precision tensor:");
        //        System.out.println("Shape: " + tensor1.getShape());
        //        System.out.println("Data type: " + tensor1.getDtype().getLayout());
    }

}
