/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.fields;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DataRange;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fields.TestFields
 * </code>
 */
public class TestFields extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void setField(A a, float value) {
        a.someOtherField = value;
    }

    public static void setNestedField(A a, float value) {
        a.b.someField = value;
    }

    public static void setNestedArray(A a, IntArray indexes) {
        for (@Parallel int i = 0; i < indexes.getSize(); i++) {
            a.b.someArray.set(i, a.b.someArray.get(i) + indexes.get(i) + 3);
        }
    }

    @Test
    public void testFields01() throws TornadoExecutionPlanException {
        final int N = 1024;
        Foo foo = new Foo(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", foo::computeInit);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            executionResult.transferToHost(foo.output);
        }

        for (int i = 0; i < N; i++) {
            assertEquals(100, foo.output.get(i));
        }
    }

    @Test
    public void testFields02() throws TornadoExecutionPlanException {
        final int N = 1024;
        Foo foo = new Foo(N);
        foo.initRandom();

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", foo::computeAdd);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            executionResult.transferToHost(foo.output);
        }
        for (int i = 0; i < N; i++) {
            assertEquals(foo.a.get(i) + foo.b.get(i), foo.output.get(i));
        }
    }

    @Test
    public void testFields03() throws TornadoExecutionPlanException {
        final int N = 1024;
        Bar bar = new Bar(N, 15);

        TaskGraph taskGraph = new TaskGraph("Bar");
        taskGraph.task("init", bar::computeInit);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            executionResult.transferToHost(bar.output);
        }

        for (int i = 0; i < N; i++) {
            assertEquals(15, bar.output.get(i));
        }
    }

    @Test
    public void testFieldsPartialCopyout() throws TornadoExecutionPlanException {
        final int N = 1024;
        Foo foo = new Foo(N);
        foo.initRandom();

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.task("t0", foo::computeAdd);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            DataRange dataRange = new DataRange(foo.output);
            executionResult.transferToHost(dataRange.withSize(N / 2));
            executionResult.transferToHost(dataRange.withOffset(N / 2).withSize(N / 2));
        }
        for (int i = 0; i < N; i++) {
            assertEquals(foo.a.get(i) + foo.b.get(i), foo.output.get(i));
        }
    }

    @Test
    public void testFieldsLazyCopyout() throws TornadoExecutionPlanException {
        final int N = 1024;
        Foo foo = new Foo(N);
        foo.initRandom();

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, foo.a, foo.b).task("t0", foo::computeAdd, foo.a, foo.b, foo.output).transferToHost(
                DataTransferMode.UNDER_DEMAND, foo.output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            executionResult.transferToHost(foo.output);
        }

        for (int i = 0; i < N; i++) {
            assertEquals(foo.a.get(i) + foo.b.get(i), foo.output.get(i));
        }
    }

    @Test
    public void testFieldsPartialLazyCopyout() throws TornadoExecutionPlanException {
        final int N = 1024;
        Foo foo = new Foo(N);
        foo.initRandom();

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, foo.a, foo.b).task("t0", foo::computeAdd, foo.a, foo.b, foo.output).transferToHost(
                DataTransferMode.UNDER_DEMAND, foo.output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            DataRange dataRange = new DataRange(foo.output);
            executionResult.transferToHost(dataRange.withSize(N / 2));
            executionResult.transferToHost(dataRange.withOffset(N / 2).withSize(N / 2));
        }

        for (int i = 0; i < N; i++) {
            assertEquals(foo.a.get(i) + foo.b.get(i), foo.output.get(i));
        }
    }

    @Test
    public void testSetField() throws TornadoExecutionPlanException {
        // The reason this is not supported for SPIR-V is that the object fields are
        // deserialized
        // before flushing the command list. Check SPIRVObjectWrapper::deserialise and
        // SPIRVTornadoDevice::flush.
        assertNotBackend(TornadoVMBackendType.SPIRV);

        B b = new B();
        final A a = new A(b);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a);
        taskGraph.task("t0", TestFields::setField, a, 77f);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        assertEquals(77, a.someOtherField, 0.01f);
        assertEquals(-1, a.b.someField, 0.01f);
        for (int i = 0; i < b.someArray.getSize(); i++) {
            assertEquals(-1, a.b.someArray.get(i));
        }
    }

    @Test
    public void testSetNestedField() throws TornadoExecutionPlanException {
        // The reason this is not supported for SPIR-V is that the object fields are
        // deserialized
        // before flushing the command list. Check SPIRVObjectWrapper::deserialise and
        // SPIRVTornadoDevice::flush.
        assertNotBackend(TornadoVMBackendType.SPIRV);

        B b = new B();
        final A a = new A(b);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a);
        taskGraph.task("t0", TestFields::setNestedField, a, 77f);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        assertEquals(77, a.b.someField, 0.01f);
        assertEquals(-1, a.someOtherField, 0.01f);
        for (int i = 0; i < b.someArray.getSize(); i++) {
            assertEquals(-1, a.b.someArray.get(i));
        }
    }

    @Test
    public void testSetNestedArray() throws TornadoExecutionPlanException {
        B b = new B();
        final A a = new A(b);
        final IntArray indexes = new IntArray(b.someArray.getSize());
        indexes.init(1);
        b.someArray.init(2);

        TaskGraph taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a);
        taskGraph.task("t0", TestFields::setNestedArray, a, indexes);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < b.someArray.getSize(); i++) {
            assertEquals(6, a.b.someArray.get(i));
        }
        assertEquals(-1, a.someOtherField, 0.01f);
        assertEquals(-1, a.b.someField, 0.01f);
    }

    private static class Foo {
        final IntArray output;
        final IntArray a;
        final IntArray b;

        Foo(int elements) {
            output = new IntArray(elements);
            a = new IntArray(elements);
            b = new IntArray(elements);
        }

        public void initRandom() {
            Random r = new Random();
            IntStream.range(0, a.getSize()).forEach(idx -> {
                a.set(idx, r.nextInt(100));
                b.set(idx, r.nextInt(100));
            });
        }

        public void computeInit() {
            for (@Parallel int i = 0; i < output.getSize(); i++) {
                output.set(i, 100);
            }
        }

        public void computeAdd() {
            for (@Parallel int i = 0; i < output.getSize(); i++) {
                output.set(i, a.get(i) + b.get(i));
            }
        }

        public void computeAdd(IntArray a, IntArray b, IntArray output) {
            for (@Parallel int i = 0; i < output.getSize(); i++) {
                output.set(i, a.get(i) + b.get(i));
            }
        }
    }

    private static class Bar {
        final IntArray output;
        final int initValue;

        Bar(int elements, int initValue) {
            output = new IntArray(elements);
            this.initValue = initValue;
        }

        public void computeInit() {
            for (@Parallel int i = 0; i < output.getSize(); i++) {
                output.set(i, initValue);
            }
        }
    }

    private static class B {
        final IntArray someArray;
        double someField;

        B() {
            this.someField = -1;
            this.someArray = new IntArray(100);
            someArray.init(-1);
        }
    }

    private static class A {
        private final B b;
        float someOtherField;

        A(B b) {
            this.b = b;
            someOtherField = -1;
        }
    }
    // CHECKSTYLE:ON

}
