package uk.ac.manchester.tornado.unittests.tensors;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.tensors.DType;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.Tensor;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestTensorTypes extends TornadoTestBase {

    // Method to perform tensor addition
    public static void tensorAdditionHalfFloat(Tensor tensorA, Tensor tensorB, Tensor tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, HalfFloat.add(tensorA.get(i), tensorB.get(i)));
        }
    }

    public static void tensorAdditionFloat(Tensor tensorA, Tensor tensorB, Tensor tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.getFloatValue(i) + tensorB.getFloatValue(i));
        }
    }

    @Test
    public void testHelloTensorAPI() {
        Shape shape = new Shape(64, 64, 64);

        Tensor tensorA = new Tensor(shape, DType.HALF_FLOAT);

        tensorA.init(new HalfFloat(1f));

        Tensor tensorB = new Tensor(shape, DType.HALF_FLOAT);

        tensorB.init(new HalfFloat(1f));

        System.out.println("Half-precision tensor:");
        System.out.println(STR."Shape: \{tensorA.getShape()}");
        System.out.println(STR."Data type: \{tensorA.getDTypeAsString()}");
        System.out.println(STR."Shape as TF: \{tensorA.getShape().toTensorFlowShapeString()}");
        System.out.println(STR."Shape as ONNX: \{tensorA.getShape().toONNXShapeString()}");
    }

    @Test
    public void testTensorAdditionHalfFloat() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        Tensor tensorA = new Tensor(shape, DType.HALF_FLOAT);

        tensorA.init(new HalfFloat(1f));

        Tensor tensorB = new Tensor(shape, DType.HALF_FLOAT);
        tensorB.init(new HalfFloat(1f));

        // Create a tensor to store the result of addition
        Tensor tensorC = new Tensor(shape, DType.HALF_FLOAT);
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

    }

    @Test
    public void testTensorAdditionFloat() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        Tensor tensorA = new Tensor(shape, DType.FLOAT);

        tensorA.init(1f);

        Tensor tensorB = new Tensor(shape, DType.FLOAT);
        tensorB.init(1f);

        // Create a tensor to store the result of addition
        Tensor tensorC = new Tensor(shape, DType.HALF_FLOAT);
        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionFloat, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

    }

}
