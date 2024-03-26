package uk.ac.manchester.tornado.unittests.tensors;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.tensors.DType;
import uk.ac.manchester.tornado.api.types.tensors.Tensor;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.TensorFloat32;
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
            //            tensorC.set(i, tensorA.get(i, Float.class) + tensorB.get(i, Float.class));
            tensorC.set(i, tensorA.getFloatValue(i) + tensorB.getFloatValue(i));
        }
    }

    public static void tensorAdditionFloat32(TensorFloat32 tensorA, TensorFloat32 tensorB, TensorFloat32 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.get(i) + tensorB.get(i));
        }
    }

    static void matrixVectorSimple(float[] xout, float[] x, Tensor w, int n, int d) {
        for (@Parallel int i = 0; i < d; i++) {
            float val = 0f;
            for (int j = 0; j < n; j++) {
                val += w.getFloatValue(i * n + j) * x[j];
            }
            xout[i] = val;
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

    private Tensor initRandTensor(int size) {
        Random random = new Random();
        Shape shape = new Shape(size);
        Tensor tensor = new Tensor(shape, DType.FLOAT);
        for (int i = 0; i < size; i++) {
            tensor.set(i, random.nextFloat(1f));
        }
        return tensor;
    }

    //    private TensorFloat32 initRandTensor(int size) {
    //        Random random = new Random();
    //        Shape shape = new Shape(size);
    //        Tensor tensor = new Tensor(shape, DType.FLOAT);
    //        for (int i = 0; i < size; i++) {
    //            tensor.set(i, random.nextFloat(1f));
    //        }
    //        return tensor;
    //    }

    private FloatArray toFloatArray(Tensor tensor) {
        FloatArray floatArray = new FloatArray(tensor.getSize());
        assert tensor.getDType() == DType.FLOAT;
        for (int i = 0; i < tensor.getSize(); i++) {
            floatArray.set(i, tensor.getFloatValue(i));
        }
        return floatArray;
    }

    @Test
    public void testTensorAdditionFloat() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        Tensor tensorA = initRandTensor(shape.getSize());
        Tensor tensorB = initRandTensor(shape.getSize());

        // Create a tensor to store the result of addition
        Tensor tensorC = new Tensor(shape, DType.QINT8);

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

        FloatArray fa = toFloatArray(tensorA);
        FloatArray fb = toFloatArray(tensorB);

        //        for (int i = 0; i < tensorC.getSize(); i++) {
        //            System.out.println(STR." Tensor value: \{tensorC.getFloatValue(i)} Float Array value: \{fa.get(i) + fb.get(i)}");
        //        }

    }

    @Test
    public void testTensorFloat32Add() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorFloat32 tensorA = new TensorFloat32(shape);
        TensorFloat32 tensorB = new TensorFloat32(shape);

        // Create a tensor to store the result of addition
        TensorFloat32 tensorC = new TensorFloat32(shape);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionFloat32, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        //        for (int i = 0; i < tensorC.getSize(); i++) {
        //            System.out.println(STR." Tensor value: \{tensorC.getFloatValue(i)} Float Array value: \{fa.get(i) + fb.get(i)}");
        //        }

    }

    @Test
    public void testMixedTypes() {
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        Tensor tensorA = new Tensor(shape, DType.FLOAT);

        tensorA.init(2f);

        Tensor tensorB = new Tensor(shape, DType.FLOAT);
        tensorB.init(3.5f);

        // Create a tensor to store the result of addition
        Tensor tensorC = new Tensor(shape, DType.FLOAT);
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
