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
package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Checks what a library provider receives on the Metal backend. Metal buffers use shared
 * (CPU + GPU) storage, so a provider can read and write TornadoVM's buffers directly through
 * {@link LibraryInvocation#getDevicePointer(int)}. The test provider below does exactly that,
 * between two JIT kernels, so a wrong address, a missing header offset or a lost ordering shows
 * up as wrong results. It also checks the native buffer, offset and queue handles.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestLibraryNativeBuffers
 * </code>
 */
public class TestLibraryNativeBuffers extends TornadoTestBase {

    static final String LIBRARY = "test/native-buffers";

    /**
     * Host-side test provider: {@code scale(in, out, n, factor)} computes out = in * factor by
     * reading and writing the buffers through the addresses the interpreter hands it.
     */
    public static final class NativeBufferTestProvider implements TornadoLibraryProvider {

        static volatile boolean handlesChecked;

        @Override
        public String libraryName() {
            return LIBRARY;
        }

        @Override
        public boolean canHandle(TornadoXPUDevice device) {
            // Needs CPU-visible device memory: only Metal's shared-storage buffers qualify.
            return device.getTornadoVMBackend() == TornadoVMBackendType.METAL;
        }

        @Override
        public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
            return new LibraryContext() {
            };
        }

        @Override
        public void dispatch(String functionName, LibraryInvocation invocation) {
            if (!functionName.equals("scale")) {
                throw new IllegalArgumentException("unknown function " + functionName);
            }
            int n = (Integer) invocation.getArg(2);
            float factor = (Float) invocation.getArg(3);
            for (int i = 0; i < 2; i++) {
                if (invocation.getNativeBuffer(i) == 0 || invocation.getNativeOffset(i) != TornadoNativeArray.ARRAY_HEADER) {
                    throw new IllegalStateException("argument " + i + ": native buffer " + invocation.getNativeBuffer(i) + ", offset " + invocation.getNativeOffset(i));
                }
            }
            TornadoNativeStreamSupport stream = (TornadoNativeStreamSupport) invocation.getDevice();
            if (stream.getNativeStream(invocation.getExecutionPlanId()) == 0 || stream.getNativeContext(invocation.getExecutionPlanId()) == 0) {
                throw new IllegalStateException("Metal device returned a null command queue or device");
            }
            handlesChecked = true;

            // FFMSupport performs the restricted reinterpret inside tornado.runtime, which has native access.
            MemorySegment in = FFMSupport.asSegment(invocation.getDevicePointer(0), (long) n * Float.BYTES);
            MemorySegment out = FFMSupport.asSegment(invocation.getDevicePointer(1), (long) n * Float.BYTES);
            for (int i = 0; i < n; i++) {
                out.setAtIndex(ValueLayout.JAVA_FLOAT, i, in.getAtIndex(ValueLayout.JAVA_FLOAT, i) * factor);
            }
        }

        @Override
        public void destroyContext(LibraryContext context) {
        }
    }

    public static LibraryTaskDescriptor scale(FloatArray in, FloatArray out, int n, float factor) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY) //
                .withFunction("scale") //
                .withParameters(new Object[] { in, out, n, factor }) //
                .withAccess(new Access[] { Access.READ_ONLY, Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY });
    }

    public static void iota(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, i);
        }
    }

    public static void addOne(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + 1.0f);
        }
    }

    private void metalOnly() {
        TornadoVMBackendType backend = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backend != TornadoVMBackendType.METAL) {
            assertNotBackend(backend, "Host-side library access to device buffers needs Metal's shared storage (default device is " + backend + ")");
        }
    }

    /**
     * JIT -> library -> JIT with no host transfers in between: the library reads what the first
     * kernel wrote and the second kernel reads what the library wrote.
     */
    private void jitLibraryJit(int n) throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(n);
        FloatArray b = new FloatArray(n);
        FloatArray c = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("nativeBuffers") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("iota", TestLibraryNativeBuffers::iota, a) //
                .libraryTask("scale", TestLibraryNativeBuffers::scale, a, b, n, 3.0f) //
                .task("addOne", TestLibraryNativeBuffers::addOne, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        NativeBufferTestProvider.handlesChecked = false;
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        assertEquals("provider did not run", true, NativeBufferTestProvider.handlesChecked);
        for (int i = 0; i < n; i++) {
            assertEquals(3.0f * i + 1.0f, c.get(i), 0.0f);
        }
    }

    @Test
    public void testJitLibraryJitSmall() throws TornadoExecutionPlanException {
        metalOnly();
        jitLibraryJit(1);
    }

    @Test
    public void testJitLibraryJit() throws TornadoExecutionPlanException {
        metalOnly();
        jitLibraryJit(4096);
    }

    @Test
    public void testJitLibraryJitLarge() throws TornadoExecutionPlanException {
        metalOnly();
        jitLibraryJit(1 << 20);
    }
}
