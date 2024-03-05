package uk.ac.manchester.tornado.unittests.tensors;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.Tensor;
import uk.ac.manchester.tornado.api.types.tensors.dtype.HF;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestTensorTypes extends TornadoTestBase {

    // Method to perform tensor addition
    public static void tensorAdditionHalfFloat(Tensor<HF> tensorA, Tensor<HF> tensorB, Tensor<HF> tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, HalfFloat.add(tensorA.get(i), tensorB.get(i)));
        }
    }

    @Test
    public void testHelloTensorAPI() {
        // Define a sample
        Shape shape = new Shape(64, 64);

        HF halfFloat = new HF();

        Tensor<HF> tensorA = new Tensor<>(shape, halfFloat);

        tensorA.init(new HalfFloat(1f));

        Tensor<HF> tensorB = new Tensor<>(shape, halfFloat);

        tensorB.init(new HalfFloat(1f));

        System.out.println("Half-precision tensor:");
        System.out.println("Shape: " + tensorA.getShape());
        System.out.println("Data type: " + tensorA.getDTypeAsString());
    }

    @Test
    public void testTensorAdditionHalfFloat() {
        // Define the shape for the tensors
        Shape shape = new Shape(4096);

        // Create two tensors and initialize their values
        Tensor<HF> tensorA = new Tensor<>(shape, new HF());
        tensorA.init(new HalfFloat(1f));

        Tensor<HF> tensorB = new Tensor<>(shape, new HF());
        tensorB.init(new HalfFloat(1f));

        // Create a tensor to store the result of addition
        Tensor<HF> tensorC = new Tensor<>(shape, new HF());

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionHalfFloat, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        // Verify the result of addition
        //        for (int i = 0; i < tensorC.getSize(); i++) {
        //            assertEquals(2.0f, tensorC.getElement(i).toFloat(), 0.01f);
        //        }
    }

}
