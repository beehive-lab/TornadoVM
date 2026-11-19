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
package uk.ac.manchester.tornado.unittests.deviceview;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Device memory allocated outside TornadoVM, which is what a {@code deviceView} exists to bind to.
 *
 * <p>
 * It calls the CUDA driver API directly and retains the device's <b>primary</b> context, which is
 * the context TornadoVM's CUDA backend retains too ({@code CUDAContext.getContextId}) and the one
 * the CUDA runtime API creates for libraries such as cuDF and RMM. That is why the addresses handed
 * out here are valid inside TornadoVM's kernels: not because device addresses are universal, but
 * because both sides are in the same context.
 * </p>
 */
final class CudaMemory implements ForeignDeviceMemory {

    private static final Linker LINKER = Linker.nativeLinker();

    private static MethodHandle cuInit;
    private static MethodHandle cuDeviceGet;
    private static MethodHandle cuDevicePrimaryCtxRetain;
    private static MethodHandle cuCtxSetCurrent;
    private static MethodHandle cuCtxSynchronize;
    private static MethodHandle cuMemAlloc;
    private static MethodHandle cuMemFree;
    private static MethodHandle cuMemcpyHtoD;
    private static MethodHandle cuMemcpyDtoH;
    private static Throwable unavailable;

    static {
        try {
            SymbolLookup cuda = SymbolLookup.libraryLookup("libcuda.so.1", Arena.global());
            cuInit = handle(cuda, "cuInit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            cuDeviceGet = handle(cuda, "cuDeviceGet", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            cuDevicePrimaryCtxRetain = handle(cuda, "cuDevicePrimaryCtxRetain", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            cuCtxSetCurrent = handle(cuda, "cuCtxSetCurrent", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
            cuCtxSynchronize = handle(cuda, "cuCtxSynchronize", FunctionDescriptor.of(ValueLayout.JAVA_INT));
            cuMemAlloc = handle(cuda, "cuMemAlloc_v2", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            cuMemFree = handle(cuda, "cuMemFree_v2", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
            cuMemcpyHtoD = handle(cuda, "cuMemcpyHtoD_v2", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            cuMemcpyDtoH = handle(cuda, "cuMemcpyDtoH_v2", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        } catch (Throwable t) {
            unavailable = t;
        }
    }

    private static MethodHandle handle(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)), descriptor);
    }

    static boolean isAvailable() {
        return unavailable == null;
    }

    /** Why the driver library could not be loaded, so a skipped run says which library is missing. */
    static String unavailableCause() {
        return unavailable == null ? "available" : unavailable.toString();
    }

    /** Makes device 0's primary context current on this thread, so the allocations below land on it. */
    static void enterPrimaryContext() {
        try (Arena arena = Arena.ofConfined()) {
            check((int) cuInit.invokeExact(0), "cuInit");
            MemorySegment device = arena.allocate(Integer.BYTES, Integer.BYTES);
            check((int) cuDeviceGet.invokeExact(device, 0), "cuDeviceGet");
            int ordinal = device.get(ValueLayout.JAVA_INT, 0);
            MemorySegment context = arena.allocate(Long.BYTES, Long.BYTES);
            check((int) cuDevicePrimaryCtxRetain.invokeExact(context, ordinal), "cuDevicePrimaryCtxRetain");
            long handle = context.get(ValueLayout.JAVA_LONG, 0);
            check((int) cuCtxSetCurrent.invokeExact(handle), "cuCtxSetCurrent");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public long allocate(long bytes) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Long.BYTES, Long.BYTES);
            check((int) cuMemAlloc.invokeExact(out, bytes), "cuMemAlloc");
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void free(long devicePointer) {
        try {
            check((int) cuMemFree.invokeExact(devicePointer), "cuMemFree");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void copyToDevice(long devicePointer, long byteOffset, float[] values) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate((long) values.length * Float.BYTES, Float.BYTES);
            for (int i = 0; i < values.length; i++) {
                host.setAtIndex(ValueLayout.JAVA_FLOAT, i, values[i]);
            }
            check((int) cuMemcpyHtoD.invokeExact(devicePointer + byteOffset, host, (long) values.length * Float.BYTES), "cuMemcpyHtoD");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void copyToDevice(long devicePointer, long byteOffset, int[] values) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate((long) values.length * Integer.BYTES, Integer.BYTES);
            for (int i = 0; i < values.length; i++) {
                host.setAtIndex(ValueLayout.JAVA_INT, i, values[i]);
            }
            check((int) cuMemcpyHtoD.invokeExact(devicePointer + byteOffset, host, (long) values.length * Integer.BYTES), "cuMemcpyHtoD");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public float[] copyFloatsToHost(long devicePointer, long byteOffset, int count) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate((long) count * Float.BYTES, Float.BYTES);
            check((int) cuMemcpyDtoH.invokeExact(host, devicePointer + byteOffset, (long) count * Float.BYTES), "cuMemcpyDtoH");
            float[] values = new float[count];
            for (int i = 0; i < count; i++) {
                values[i] = host.getAtIndex(ValueLayout.JAVA_FLOAT, i);
            }
            return values;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /**
     * Waits for everything in the context, including the stream TornadoVM launched on. The copies
     * above run on the legacy default stream, which a non-blocking stream does not order against.
     */
    @Override
    public void synchronize() {
        try {
            check((int) cuCtxSynchronize.invokeExact(), "cuCtxSynchronize");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void check(int status, String call) {
        if (status != 0) {
            throw new IllegalStateException(call + " failed with CUDA status " + status);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException runtime) {
            return runtime;
        }
        if (t instanceof Error error) {
            throw error;
        }
        return new RuntimeException(t);
    }
}
