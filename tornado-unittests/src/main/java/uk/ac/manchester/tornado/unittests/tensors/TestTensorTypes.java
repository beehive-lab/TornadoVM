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
package uk.ac.manchester.tornado.unittests.tensors;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.tensors.DType;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.TensorFloat16;
import uk.ac.manchester.tornado.api.types.tensors.TensorFloat32;
import uk.ac.manchester.tornado.api.types.tensors.TensorFloat64;
import uk.ac.manchester.tornado.api.types.tensors.TensorInt16;
import uk.ac.manchester.tornado.api.types.tensors.TensorInt32;
import uk.ac.manchester.tornado.api.types.tensors.TensorInt64;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestTensorTypes extends TornadoTestBase {

    public static void tensorAdditionFloat16(TensorFloat16 tensorA, TensorFloat16 tensorB, TensorFloat16 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, HalfFloat.add(tensorA.get(i), tensorB.get(i)));
        }
    }

    public static void tensorAdditionFloat32(TensorFloat32 tensorA, TensorFloat32 tensorB, TensorFloat32 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.get(i) + tensorB.get(i));
        }
    }

    public static void tensorAdditionFloat64(TensorFloat64 tensorA, TensorFloat64 tensorB, TensorFloat64 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.get(i) + tensorB.get(i));
        }
    }

    public static void tensorAdditionInt16(TensorInt16 tensorA, TensorInt16 tensorB, TensorInt16 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, (short) (tensorA.get(i) + tensorB.get(i)));
        }
    }

    public static void tensorAdditionInt32(TensorInt32 tensorA, TensorInt32 tensorB, TensorInt32 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.get(i) + tensorB.get(i));
        }
    }

    public static void tensorAdditionInt64(TensorInt64 tensorA, TensorInt64 tensorB, TensorInt64 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.get(i) + tensorB.get(i));
        }
    }

    @Test
    public void testHelloTensorAPI() {
        Shape shape = new Shape(64, 64, 64);

        TensorFloat16 tensorA = new TensorFloat16(shape);

        tensorA.init(new HalfFloat(1f));

        Assert.assertEquals("Expected shape does not match", "Shape{dimensions=[64, 64, 64]}", tensorA.getShape().toString());
        Assert.assertEquals("Expected data type does not match", "HALF_FLOAT", tensorA.getDTypeAsString());
        Assert.assertEquals("Expected TensorFlow shape string does not match", "[64,64,64]", tensorA.getShape().toTensorFlowShapeString());
        Assert.assertEquals("Expected ONNX shape string does not match", "{dim_0: 64, dim_1: 64, dim_2: 64}", tensorA.getShape().toONNXShapeString());
    }

    @Test
    public void testTensorFloat16Add() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorFloat16 tensorA = new TensorFloat16(shape);
        TensorFloat16 tensorB = new TensorFloat16(shape);

        // Create a tensor to store the result of addition
        TensorFloat16 tensorC = new TensorFloat16(shape);

        tensorA.init(new HalfFloat(2));
        tensorB.init(new HalfFloat(5));
        tensorA.init(new HalfFloat(0));

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionFloat16, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < tensorC.getSize(); i++) {
            //            Assert.assertEquals(tensorC.get(i), (float) HalfFloat.add(tensorA.get(i), tensorB.get(i)), 0.00f);
        }

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

        tensorA.init(20f);
        tensorB.init(3000f);
        tensorA.init(0f);

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

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorC.get(i), tensorA.get(i) + tensorB.get(i), 0.00f);
        }

    }

    @Test
    public void testTensorFloat64Add() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorFloat64 tensorA = new TensorFloat64(shape);
        TensorFloat64 tensorB = new TensorFloat64(shape);

        // Create a tensor to store the result of addition
        TensorFloat64 tensorC = new TensorFloat64(shape);

        tensorA.init(20d);
        tensorB.init(3000d);
        tensorA.init(0d);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionFloat64, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorC.get(i), tensorA.get(i) + tensorB.get(i), 0.00f);
        }

    }

    @Test
    public void testTensorInt16Add() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorInt16 tensorA = new TensorInt16(shape);
        TensorInt16 tensorB = new TensorInt16(shape);

        // Create a tensor to store the result of addition
        TensorInt16 tensorC = new TensorInt16(shape);

        tensorA.init((short) 20);
        tensorB.init((short) 300);
        tensorA.init((short) 0);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionInt16, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorC.get(i), tensorA.get(i) + tensorB.get(i), 0.00f);
        }

    }

    @Test
    public void testTensorInt32Add() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorInt32 tensorA = new TensorInt32(shape);
        TensorInt32 tensorB = new TensorInt32(shape);

        // Create a tensor to store the result of addition
        TensorInt32 tensorC = new TensorInt32(shape);

        tensorA.init(20);
        tensorB.init(300);
        tensorA.init(0);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionInt32, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorC.get(i), tensorA.get(i) + tensorB.get(i), 0.00f);
        }
    }

    @Test
    public void testTensorInt64Add() {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorInt64 tensorA = new TensorInt64(shape);
        TensorInt64 tensorB = new TensorInt64(shape);

        // Create a tensor to store the result of addition
        TensorInt64 tensorC = new TensorInt64(shape);

        tensorA.init(20l);
        tensorB.init(3000l);
        tensorA.init(0l);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionInt64, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorC.get(i), tensorA.get(i) + tensorB.get(i), 0.00f);
        }
    }

}
