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
import java.util.List;

import uk.ac.manchester.tornado.runtime.ffm.ROCmLibraries;

/**
 * Device memory allocated outside TornadoVM on the HIP backend, which is what a {@code deviceView}
 * exists to bind to.
 *
 * <p>
 * It calls the HIP runtime API directly. The runtime API works in the device's <b>primary</b>
 * context, which is the context TornadoVM's HIP backend retains too
 * ({@code hipDevicePrimaryCtxRetain}) and the one libraries built on the HIP runtime allocate in.
 * That is why the addresses handed out here are valid inside TornadoVM's kernels: both sides are in
 * the same context.
 * </p>
 */
final class HipMemory implements ForeignDeviceMemory {

    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code hipMemcpyHostToDevice} and {@code hipMemcpyDeviceToHost} from {@code hipMemcpyKind}. */
    private static final int HOST_TO_DEVICE = 1;
    private static final int DEVICE_TO_HOST = 2;

    private static MethodHandle hipInit;
    private static MethodHandle hipSetDevice;
    private static MethodHandle hipDeviceSynchronize;
    private static MethodHandle hipMalloc;
    private static MethodHandle hipFree;
    private static MethodHandle hipMemcpyHtoD;
    private static MethodHandle hipMemcpyDtoH;
    private static Throwable unavailable;

    static {
        try {
            SymbolLookup hip = ROCmLibraries.load(List.of("libamdhip64.so.7", "libamdhip64.so.6", "libamdhip64.so"), List.of("amdhip64_7.dll", "amdhip64_6.dll"));
            if (hip == null) {
                throw new UnsatisfiedLinkError("the HIP runtime library (amdhip64) is not loadable");
            }
            hipInit = handle(hip, "hipInit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            hipSetDevice = handle(hip, "hipSetDevice", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            hipDeviceSynchronize = handle(hip, "hipDeviceSynchronize", FunctionDescriptor.of(ValueLayout.JAVA_INT));
            hipMalloc = handle(hip, "hipMalloc", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            hipFree = handle(hip, "hipFree", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
            // hipMemcpy(dst, src, sizeBytes, kind), with the device side as a plain address.
            hipMemcpyHtoD = handle(hip, "hipMemcpy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
            hipMemcpyDtoH = handle(hip, "hipMemcpy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
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

    /** Why the runtime library could not be loaded, so a skipped run says which library is missing. */
    static String unavailableCause() {
        return unavailable == null ? "available" : unavailable.toString();
    }

    /** Makes device 0 current on this thread, so the allocations below land in its primary context. */
    static void enterPrimaryContext() {
        try {
            check((int) hipInit.invokeExact(0), "hipInit");
            check((int) hipSetDevice.invokeExact(0), "hipSetDevice");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public long allocate(long bytes) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Long.BYTES, Long.BYTES);
            check((int) hipMalloc.invokeExact(out, bytes), "hipMalloc");
            return out.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void free(long devicePointer) {
        try {
            check((int) hipFree.invokeExact(devicePointer), "hipFree");
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
            check((int) hipMemcpyHtoD.invokeExact(devicePointer + byteOffset, host, (long) values.length * Float.BYTES, HOST_TO_DEVICE), "hipMemcpy");
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
            check((int) hipMemcpyHtoD.invokeExact(devicePointer + byteOffset, host, (long) values.length * Integer.BYTES, HOST_TO_DEVICE), "hipMemcpy");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public float[] copyFloatsToHost(long devicePointer, long byteOffset, int count) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate((long) count * Float.BYTES, Float.BYTES);
            check((int) hipMemcpyDtoH.invokeExact(host, devicePointer + byteOffset, (long) count * Float.BYTES, DEVICE_TO_HOST), "hipMemcpy");
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
     * Waits for everything on the device, including the stream TornadoVM launched on. The copies
     * above run on the null stream, which a non-blocking stream does not order against.
     */
    @Override
    public void synchronize() {
        try {
            check((int) hipDeviceSynchronize.invokeExact(), "hipDeviceSynchronize");
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void check(int status, String call) {
        if (status != 0) {
            throw new IllegalStateException(call + " failed with HIP status " + status);
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
