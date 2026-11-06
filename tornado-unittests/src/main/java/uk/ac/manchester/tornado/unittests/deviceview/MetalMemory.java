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

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * {@code MTLBuffer}s allocated outside TornadoVM, which is what a {@code deviceView} binds to on Metal.
 *
 * <p>
 * It sends the Objective-C messages directly, with none of TornadoVM's Metal driver in between. In
 * particular nothing here waits for TornadoVM's command queues, which a plan gives an owner nothing to
 * wait on for. That is why a read straight after {@code execute()} shows whether {@code execute()} has
 * kept its promise.
 * </p>
 */
final class MetalMemory implements ForeignDeviceMemory {

    /** {@code MTLResourceStorageModeShared}: the CPU and the GPU both reach the memory. */
    private static final long OPTIONS_SHARED = 0L;
    /** {@code MTLResourceStorageModePrivate}: only the GPU reaches the memory. */
    private static final long OPTIONS_PRIVATE = 2L << 4;
    /** {@code MTLStorageModePrivate}, what a buffer allocated with {@link #OPTIONS_PRIVATE} reports. */
    private static final long STORAGE_MODE_PRIVATE = 2L;

    private static MethodHandle msgSendReturningLong;
    private static MethodHandle msgSendTwoArguments;
    private static MethodHandle msgSendReturningVoid;
    private static long device;
    private static long selNewBuffer;
    private static long selContents;
    private static long selStorageMode;
    private static long selRelease;
    private static Throwable unavailable;

    static {
        try {
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
            SymbolLookup metal = SymbolLookup.libraryLookup("/System/Library/Frameworks/Metal.framework/Metal", Arena.global());
            msgSendReturningLong = handle(objc, "objc_msgSend", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
            msgSendTwoArguments = handle(objc, "objc_msgSend", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
            msgSendReturningVoid = handle(objc, "objc_msgSend", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
            MethodHandle registerName = handle(objc, "sel_registerName", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            selNewBuffer = selector(registerName, "newBufferWithLength:options:");
            selContents = selector(registerName, "contents");
            selStorageMode = selector(registerName, "storageMode");
            selRelease = selector(registerName, "release");
            MethodHandle createDevice = handle(metal, "MTLCreateSystemDefaultDevice", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
            device = (long) createDevice.invokeExact();
            if (device == 0) {
                throw new IllegalStateException("MTLCreateSystemDefaultDevice returned no device");
            }
        } catch (Throwable t) {
            unavailable = t;
        }
    }

    private static MethodHandle handle(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)), descriptor);
    }

    private static long selector(MethodHandle registerName, String name) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            return (long) registerName.invokeExact(FFMSupport.allocateCString(arena, name));
        }
    }

    static boolean isAvailable() {
        return unavailable == null;
    }

    /** Why Metal could not be reached, so a skipped run says what is missing. */
    static String unavailableCause() {
        return unavailable == null ? "available" : unavailable.toString();
    }

    @Override
    public long allocate(long bytes) {
        return newBuffer(bytes, OPTIONS_SHARED);
    }

    /** A buffer whose contents only the GPU reaches, so none of the copies below work on it. */
    long allocatePrivate(long bytes) {
        return newBuffer(bytes, OPTIONS_PRIVATE);
    }

    private static long newBuffer(long bytes, long options) {
        try {
            long buffer = (long) msgSendTwoArguments.invokeExact(device, selNewBuffer, bytes, options);
            if (buffer == 0) {
                throw new IllegalStateException("The device refused to allocate an MTLBuffer of " + bytes + " bytes");
            }
            return buffer;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void free(long buffer) {
        try {
            msgSendReturningVoid.invokeExact(buffer, selRelease);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static MemorySegment hostView(long buffer, long byteOffset, long bytes) {
        try {
            // Asked of the storage mode: contents can be non-zero for a private buffer too.
            long storageMode = (long) msgSendReturningLong.invokeExact(buffer, selStorageMode);
            if (storageMode == STORAGE_MODE_PRIVATE) {
                throw new IllegalStateException("This MTLBuffer has private storage: the host may not use it");
            }
            long address = (long) msgSendReturningLong.invokeExact(buffer, selContents);
            if (address == 0) {
                throw new IllegalStateException("This MTLBuffer reports no CPU address");
            }
            return FFMSupport.asSegment(address + byteOffset, bytes);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @Override
    public void copyToDevice(long buffer, long byteOffset, float[] values) {
        MemorySegment.copy(values, 0, hostView(buffer, byteOffset, (long) values.length * Float.BYTES), ValueLayout.JAVA_FLOAT, 0, values.length);
    }

    @Override
    public void copyToDevice(long buffer, long byteOffset, int[] values) {
        MemorySegment.copy(values, 0, hostView(buffer, byteOffset, (long) values.length * Integer.BYTES), ValueLayout.JAVA_INT, 0, values.length);
    }

    @Override
    public float[] copyFloatsToHost(long buffer, long byteOffset, int count) {
        float[] values = new float[count];
        MemorySegment.copy(hostView(buffer, byteOffset, (long) count * Float.BYTES), ValueLayout.JAVA_FLOAT, 0, values, 0, count);
        return values;
    }

    /** Nothing to do: {@code execute()} has already waited, which is what the tests rely on here. */
    @Override
    public void synchronize() {
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
