/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.codegen;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertArrayEquals;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.codegen.TestSignedComparisonsCodegen
 * </code>
 */
public class TestSignedComparisonsCodegen extends TornadoTestBase {

    private static void testKernel01(KernelContext context, IntArray array) {
        int idx = context.globalIdx;

        if (0 < idx && idx < array.getSize()) {
            array.set(idx, 1);
        }
    }

    private static void testKernel02(KernelContext context, IntArray array) {
        int idx = context.globalIdx;

        if (idx > 2 && (idx % 2 == 0)) {
            array.set(idx, 1);
        }
    }

    private static void testKernel03(KernelContext context, IntArray array) {
        int idx = context.globalIdx;

        if (idx > 2 || (idx % 2 == 0)) {
            array.set(idx, 1);
        }
    }

    private static void testKernel04(KernelContext context, IntArray array) {
        int idx = context.globalIdx;
        int i = 5 - idx;

        boolean isInBounds = 0 < idx && idx < array.getSize() && 0 < i && i < array.getSize();
        if (isInBounds) {
            array.set(idx, 1);
        }
    }

    @Test
    public void testSigned01() {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        int size = 8;
        IntArray testArr = new IntArray(size);
        testArr.init(0);

        WorkerGrid workerGrid = new WorkerGrid1D(size / 2);
        GridScheduler gridScheduler = new GridScheduler("testSigned.testKernel01", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("testSigned") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, testArr) //
                .task("testKernel01", TestSignedComparisonsCodegen::testKernel01, context, testArr) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArr);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }

        assertArrayEquals(new int[] { 0, 1, 1, 1, 0, 0, 0, 0 }, testArr.toHeapArray());
    }

    @Test
    public void testSigned02() {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        int size = 8;
        IntArray testArr = new IntArray(size);
        testArr.init(0);

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("testSigned.testKernel02", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("testSigned") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, testArr) //
                .task("testKernel02", TestSignedComparisonsCodegen::testKernel02, context, testArr) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArr);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }

        assertArrayEquals(new int[] { 0, 0, 0, 0, 1, 0, 1, 0 }, testArr.toHeapArray());
    }

    @Test
    public void testSigned03() {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        int size = 8;
        IntArray testArr = new IntArray(size);
        testArr.init(0);

        WorkerGrid workerGrid = new WorkerGrid1D(size / 2);
        GridScheduler gridScheduler = new GridScheduler("testSigned.testKernel03", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("testSigned") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, testArr) //
                .task("testKernel03", TestSignedComparisonsCodegen::testKernel03, context, testArr) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArr);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }

        assertArrayEquals(new int[] { 1, 0, 1, 1, 0, 0, 0, 0 }, testArr.toHeapArray());
    }

    @Test
    public void testSigned04() {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        int size = 8;
        IntArray testArr = new IntArray(size);

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("testSigned.testKernel04", workerGrid);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("testSigned").transferToDevice(DataTransferMode.FIRST_EXECUTION, testArr) //
                .task("testKernel04", TestSignedComparisonsCodegen::testKernel04, context, testArr)   //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testArr);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }

        assertArrayEquals(new int[] { 0, 1, 1, 1, 1, 0, 0, 0 }, testArr.toHeapArray());
    }

}