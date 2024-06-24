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

import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.TensorByte;
import uk.ac.manchester.tornado.api.types.tensors.TensorFP16;
import uk.ac.manchester.tornado.api.types.tensors.TensorFP32;
import uk.ac.manchester.tornado.api.types.tensors.TensorFP64;
import uk.ac.manchester.tornado.api.types.tensors.TensorInt16;
import uk.ac.manchester.tornado.api.types.tensors.TensorInt32;
import uk.ac.manchester.tornado.api.types.tensors.TensorInt64;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tensors.TestTensorTypes
 * </code>
 */
public class TestTensorTypes extends TornadoTestBase {

    public static void tensorAdditionFloat16(TensorFP16 tensorA, TensorFP16 tensorB, TensorFP16 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, HalfFloat.add(tensorA.get(i), tensorB.get(i)));
        }
    }

    public static void tensorAdditionFloat32(TensorFP32 tensorA, TensorFP32 tensorB, TensorFP32 tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, tensorA.get(i) + tensorB.get(i));
        }
    }

    public static void tensorAdditionFloat64(TensorFP64 tensorA, TensorFP64 tensorB, TensorFP64 tensorC) {
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

    public static void tensorAdditionByte(TensorByte tensorA, TensorByte tensorB, TensorByte tensorC) {
        for (@Parallel int i = 0; i < tensorC.getSize(); i++) {
            tensorC.set(i, (byte) (tensorA.get(i) + tensorB.get(i)));
        }
    }

    @Test
    public void testHelloTensorAPI() {
        Shape shape = new Shape(64, 64, 64);

        TensorFP16 tensorA = new TensorFP16(shape);

        tensorA.init(new HalfFloat(1f));

        Assert.assertEquals("Expected shape does not match", "Shape{dimensions=[64, 64, 64]}", tensorA.getShape().toString());
        Assert.assertEquals("Expected data type does not match", "HALF_FLOAT", tensorA.getDTypeAsString());
        Assert.assertEquals("Expected TensorFlow shape string does not match", "[64,64,64]", tensorA.getShape().toTensorFlowShapeString());
        Assert.assertEquals("Expected ONNX shape string does not match", "{dim_0: 64, dim_1: 64, dim_2: 64}", tensorA.getShape().toONNXShapeString());
    }

    @Test
    public void testTensorFloat16Add() throws TornadoExecutionPlanException {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorFP16 tensorA = new TensorFP16(shape);
        TensorFP16 tensorB = new TensorFP16(shape);

        // Create a tensor to store the result of addition
        TensorFP16 tensorC = new TensorFP16(shape);

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(HalfFloat.add(tensorA.get(i), tensorB.get(i)).getFloat32(), tensorC.get(i).getFloat32(), 0.01f);
        }

    }

    @Test
    public void testTensorFloat32Add() throws TornadoExecutionPlanException {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorFP32 tensorA = new TensorFP32(shape);
        TensorFP32 tensorB = new TensorFP32(shape);

        // Create a tensor to store the result of addition
        TensorFP32 tensorC = new TensorFP32(shape);

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorA.get(i) + tensorB.get(i), tensorC.get(i), 0.01f);
        }

    }

    @Test
    public void testTensorFloat64Add() throws TornadoExecutionPlanException {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorFP64 tensorA = new TensorFP64(shape);
        TensorFP64 tensorB = new TensorFP64(shape);

        // Create a tensor to store the result of addition
        TensorFP64 tensorC = new TensorFP64(shape);

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorA.get(i) + tensorB.get(i), tensorC.get(i), 0.01f);
        }

    }

    @Test
    public void testTensorInt16Add() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorA.get(i) + tensorB.get(i), tensorC.get(i), 0.01f);
        }

    }

    @Test
    public void testTensorInt32Add() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorA.get(i) + tensorB.get(i), tensorC.get(i), 0.01f);
        }
    }

    @Test
    public void testTensorInt64Add() throws TornadoExecutionPlanException {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorInt64 tensorA = new TensorInt64(shape);
        TensorInt64 tensorB = new TensorInt64(shape);

        // Create a tensor to store the result of addition
        TensorInt64 tensorC = new TensorInt64(shape);

        tensorA.init(20L);
        tensorB.init(3000L);
        tensorA.init(0L);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionInt64, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorA.get(i) + tensorB.get(i), tensorC.get(i), 0.01f);
        }
    }

    @Test
    public void testTensorByte() throws TornadoExecutionPlanException {
        // Define the shape for the tensors
        Shape shape = new Shape(64, 64, 64);

        // Create two tensors and initialize their values
        TensorByte tensorA = new TensorByte(shape);
        TensorByte tensorB = new TensorByte(shape);

        // Create a tensor to store the result of addition
        TensorByte tensorC = new TensorByte(shape);

        tensorA.init((byte) 20);
        tensorB.init((byte) 300);
        tensorA.init((byte) 0);

        // Define the task graph
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tensorA, tensorB) //
                .task("t0", TestTensorTypes::tensorAdditionByte, tensorA, tensorB, tensorC) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tensorC); //

        // Take a snapshot of the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan and execute it
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < tensorC.getSize(); i++) {
            Assert.assertEquals(tensorA.get(i) + tensorB.get(i), tensorC.get(i), 0.01f);
        }
    }

}
