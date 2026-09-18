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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Regression tests for consuming a {@code new HalfFloat(float)} as a value in the same kernel
 * rather than storing it: {@code getHalfFloatValue()} for the raw f16 bits, and
 * {@code getFloat32()} for the value rounded through half precision.
 *
 * <p>Both used to abort compilation on the CUDA backend. {@code HalfFloat.getHalfFloatValue()} was
 * inlined into a read of the object's {@code halfFloatValue} field, and the HalfFloat replacement
 * phase then dissolved the allocation into the value it wraps - leaving that value as the base of
 * a memory address, which address lowering refuses:
 * {@code address origin unimplemented: CUDAConvertFloatToHalf} (or, with any arithmetic in front of
 * the conversion, {@code ...calc.MulNode}). Storing the same expression into a HalfFloatArray
 * always worked, which is what made float-to-half reachable only as a store.
 *
 * <p>How to run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestHalfFloatValueConversion}</p>
 */
public class TestHalfFloatValueConversion extends TornadoTestBase {

    private static final int SIZE = 64;

    /**
     * The fix is applied to the CUDA backend only; the OpenCL/Metal HalfFloat replacement phases
     * share the pattern and are not changed here, so skip them rather than assert against
     * behaviour this change does not touch.
     */
    private void assumeCudaBackend() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "This HalfFloat value-conversion fix targets the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
    }

    /** The f16 bits of a computed float: the shape that reported {@code address origin ... MulNode}. */
    public static void bitsOfComputed(FloatArray a, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(a.get(i) * 2.0f).getHalfFloatValue() & 0xFFFF);
        }
    }

    /**
     * The f16 bits of a value read straight from memory. With no arithmetic in front of it, the
     * conversion node itself was named as the rejected address origin.
     */
    public static void bitsOfLoad(FloatArray a, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(a.get(i)).getHalfFloatValue() & 0xFFFF);
        }
    }

    /** The same conversion consumed as a float: a round trip through half precision. */
    public static void float32OfNewHalfFloat(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, new HalfFloat(a.get(i) * 2.0f).getFloat32());
        }
    }

    /**
     * Two converted halves packed into one int - the tensor-core weight-staging shape from the
     * quantised GEMM in the second report, which had to fall back to a ~40-operation software
     * float-to-half in the inner K loop because this did not compile.
     */
    public static void packHalfPair(FloatArray a, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            int lo = new HalfFloat(a.get(i) * 2.0f).getHalfFloatValue() & 0xFFFF;
            int hi = new HalfFloat(a.get(i) * 4.0f).getHalfFloatValue() & 0xFFFF;
            out.set(i, lo | (hi << 16));
        }
    }

    private static FloatArray input() {
        FloatArray a = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            a.set(i, i * 0.5f - 16.0f);
        }
        return a;
    }

    @Test
    public void testBitsOfComputed() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        FloatArray a = input();
        IntArray out = new IntArray(SIZE);
        runTask("bc", TestHalfFloatValueConversion::bitsOfComputed, a, out);
        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(a.get(i) * 2.0f) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testBitsOfLoad() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        FloatArray a = input();
        IntArray out = new IntArray(SIZE);
        runTask("bl", TestHalfFloatValueConversion::bitsOfLoad, a, out);
        for (int i = 0; i < SIZE; i++) {
            assertEquals("bits[" + i + "]", Float.floatToFloat16(a.get(i)) & 0xFFFF, out.get(i));
        }
    }

    @Test
    public void testFloat32OfNewHalfFloat() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        FloatArray a = input();
        FloatArray out = new FloatArray(SIZE);
        // The device rounds through half precision, so the oracle does too and the comparison is exact.
        TaskGraph tg = new TaskGraph("f32").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestHalfFloatValueConversion::float32OfNewHalfFloat, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < SIZE; i++) {
            float expected = Float.float16ToFloat(Float.floatToFloat16(a.get(i) * 2.0f));
            assertEquals("out[" + i + "]", expected, out.get(i), 0.0f);
        }
    }

    @Test
    public void testPackHalfPair() throws TornadoExecutionPlanException {
        assumeCudaBackend();
        FloatArray a = input();
        IntArray out = new IntArray(SIZE);
        runTask("pp", TestHalfFloatValueConversion::packHalfPair, a, out);
        for (int i = 0; i < SIZE; i++) {
            int expected = (Float.floatToFloat16(a.get(i) * 2.0f) & 0xFFFF) //
                    | ((Float.floatToFloat16(a.get(i) * 4.0f) & 0xFFFF) << 16);
            assertEquals("packed[" + i + "]", expected, out.get(i));
        }
    }

    private void runTask(String name, TornadoFunctions.Task2<FloatArray, IntArray> kernel, FloatArray a, IntArray out) throws TornadoExecutionPlanException {
        TaskGraph tg = new TaskGraph(name).transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", kernel, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.execute();
        }
    }
}
