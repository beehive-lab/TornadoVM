/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Java bytes are signed on every device. Plain C {@code char} is signed on x86-64 but unsigned
 * on AArch64, and NVRTC follows the host ABI, so a CUDA kernel that declared Java bytes as
 * {@code char} read -1 as 255 on an AArch64 host. Quantized weights are stored as bytes, so
 * this broke every int8 model there while x86-64 hosts were unaffected.
 *
 * <p>Each test covers every byte value: loads from global and local memory, a sign-dependent
 * comparison, and the narrowing {@code (byte)} cast.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestSignedBytes}</p>
 */
public class TestSignedBytes extends TornadoTestBase {

    private static final int SIZE = 256;
    private static final int LOCAL_SIZE = 64;

    private static ByteArray allByteValues() {
        ByteArray bytes = new ByteArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            bytes.set(i, (byte) (i - 128));
        }
        return bytes;
    }

    private static GridScheduler gridScheduler(String taskName) {
        WorkerGrid1D worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        return new GridScheduler(taskName, worker);
    }

    public static void widenGlobal(KernelContext context, ByteArray input, IntArray output) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            output.set(i, input.get(i));
        }
    }

    public static void isNegative(KernelContext context, ByteArray input, IntArray output) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            output.set(i, input.get(i) < 0 ? 1 : 0);
        }
    }

    public static void narrow(KernelContext context, IntArray input, ByteArray bytes, IntArray widened) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            byte b = (byte) input.get(i);
            bytes.set(i, b);
            widened.set(i, b);
        }
    }

    public static void isNegativeLocal(KernelContext context, ByteArray input, IntArray output) {
        int i = context.globalIdx;
        int lid = context.localIdx;
        byte[] tile = context.allocateByteLocalArray(LOCAL_SIZE);
        if (i < input.getSize()) {
            tile[lid] = input.get(i);
        }
        context.localBarrier();
        if (i < input.getSize()) {
            int mirror = context.localGroupSizeX - 1 - lid;
            output.set(i, tile[mirror] < 0 ? 1 : 0);
        }
    }

    @Test
    public void test01GlobalLoad() throws TornadoExecutionPlanException {
        ByteArray input = allByteValues();
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestSignedBytes::widenGlobal, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler("s0.t0")).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("byte " + input.get(i), input.get(i), output.get(i));
        }
    }

    @Test
    public void test02Comparison() throws TornadoExecutionPlanException {
        ByteArray input = allByteValues();
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestSignedBytes::isNegative, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler("s0.t0")).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("byte " + input.get(i), input.get(i) < 0 ? 1 : 0, output.get(i));
        }
    }

    @Test
    public void test03NarrowingCast() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, i * 3 - 300);
        }
        ByteArray bytes = new ByteArray(SIZE);
        IntArray widened = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestSignedBytes::narrow, new KernelContext(), input, bytes, widened) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bytes, widened);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler("s0.t0")).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            byte expected = (byte) input.get(i);
            assertEquals("int " + input.get(i), expected, bytes.get(i));
            assertEquals("int " + input.get(i), expected, widened.get(i));
        }
    }

    @Test
    public void test04LocalArray() throws TornadoExecutionPlanException {
        ByteArray input = allByteValues();
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestSignedBytes::isNegativeLocal, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler("s0.t0")).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            int group = i / LOCAL_SIZE;
            int mirror = group * LOCAL_SIZE + (LOCAL_SIZE - 1 - i % LOCAL_SIZE);
            assertEquals("index " + i, input.get(mirror) < 0 ? 1 : 0, output.get(i));
        }
    }
}
