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
package uk.ac.manchester.tornado.clblast.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP16NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;

/**
 * FFM bindings for clblast_c.h. CLBlast takes cl_mem objects and element offsets,
 * scalars by value, and a pointer to the command queue handle. No JNI shim or
 * host copy of device data is needed.
 */
final class CLBlastNativeLib {

    private final Map<String, MethodHandle> functions = new HashMap<>();

    static CLBlastNativeLib load() {
        SymbolLookup lookup = FFMSupport.loadBundledLibrary("libclblast.so.1", "libclblast.so", "clblast.dll", "CLBlast.dll", "libclblast.dylib");
        return lookup == null ? null : new CLBlastNativeLib(lookup);
    }

    CLBlastNativeLib(SymbolLookup lookup) {
        // The OpenCL backend uses 64-bit size_t throughout its FFM bindings.
        if (C_POINTER.byteSize() != Long.BYTES) {
            throw new TornadoRuntimeException("CLBlast bindings require a 64-bit platform");
        }
        bindPrecision(lookup, "S", FFMSupport.C_FLOAT);
        bindPrecision(lookup, "D", FFMSupport.C_DOUBLE);
    }

    boolean isAvailable() {
        return functions.size() == 8;
    }

    private void bindPrecision(SymbolLookup lookup, String precision, ValueLayout scalar) {
        bind(lookup, "CLBlast" + precision + "gemm",
                FunctionDescriptor.of(C_INT, C_INT, C_INT, C_INT, C_LONG, C_LONG, C_LONG, scalar,
                        C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, scalar, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER));
        bind(lookup, "CLBlast" + precision + "gemv",
                FunctionDescriptor.of(C_INT, C_INT, C_INT, C_LONG, C_LONG, scalar, C_POINTER, C_LONG, C_LONG,
                        C_POINTER, C_LONG, C_LONG, scalar, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER));
        bind(lookup, "CLBlast" + precision + "axpy",
                FunctionDescriptor.of(C_INT, C_LONG, scalar, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER));
        bind(lookup, "CLBlast" + precision + "dot",
                FunctionDescriptor.of(C_INT, C_LONG, C_POINTER, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER));
    }

    private void bind(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        MethodHandle function = FFMSupport.downcall(lookup, descriptor, name);
        if (function != null) {
            functions.put(name, function);
        }
    }

    void dispatch(String name, LibraryInvocation invocation, long queueHandle) {
        MethodHandle function = functions.get(name);
        if (function == null) {
            throw new TornadoRuntimeException("CLBlast function unavailable or unsupported: " + name);
        }
        if (queueHandle == 0) {
            throw new TornadoRuntimeException("CLBlast requires a valid OpenCL command queue");
        }
        boolean fp64 = name.charAt(7) == 'D';
        int elementBytes = fp64 ? Double.BYTES : Float.BYTES;
        // The queue pointer is host-side input only; CLBlast consumes it during the call.
        // Passing a null event avoids creating an event that the provider would have to release.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment queue = FFMSupport.allocatePointer(arena);
            queue.set(C_POINTER, 0, MemorySegment.ofAddress(queueHandle));
            int status = switch (name) {
                case "CLBlastSgemm", "CLBlastDgemm" -> gemm(function, invocation, queue, fp64, elementBytes);
                case "CLBlastSgemv", "CLBlastDgemv" -> gemv(function, invocation, queue, fp64, elementBytes);
                case "CLBlastSaxpy", "CLBlastDaxpy" -> axpy(function, invocation, queue, fp64, elementBytes);
                case "CLBlastSdot", "CLBlastDdot" -> dot(function, invocation, queue, elementBytes);
                default -> throw new TornadoRuntimeException("CLBlast function unsupported: " + name);
            };
            if (status == -2044) {
                // CLBlastNoDoublePrecision: the device has no cl_khr_fp64.
                throw new TornadoDeviceFP64NotSupported(name + " needs FP64, which this OpenCL device does not support");
            } else if (status == -2045) {
                // CLBlastNoDoublePrecision: the device has no cl_khr_fp64.
                throw new TornadoDeviceFP16NotSupported(name + " needs FP16, which this OpenCL device does not support");
            } else if (status != 0) {
                throw new TornadoRuntimeException(name + " failed with CLBlast status " + status);
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new TornadoRuntimeException("Unable to invoke " + name + ": " + t);
        }
    }

    private static int integer(LibraryInvocation invocation, int index) {
        return (int) invocation.getArg(index);
    }

    /** Dimensions, leading dimensions and increments are unsigned size_t in the C API. */
    private static long size(LibraryInvocation invocation, int index) {
        int value = integer(invocation, index);
        if (value <= 0) {
            throw new IllegalArgumentException("CLBlast argument " + index + " must be positive: " + value);
        }
        return value;
    }

    private static MemorySegment buffer(LibraryInvocation invocation, int index) {
        long handle = invocation.getNativeBuffer(index);
        if (!invocation.isReference(index) || handle == 0 || handle == -1) {
            throw new IllegalArgumentException("CLBlast argument " + index + " requires an allocated OpenCL buffer");
        }
        return MemorySegment.ofAddress(handle);
    }

    static long elementOffset(LibraryInvocation invocation, int index, int elementBytes) {
        long bytes = invocation.getNativeOffset(index);
        if (bytes < 0 || bytes % elementBytes != 0) {
            throw new IllegalArgumentException("CLBlast buffer offset must be non-negative and aligned to " + elementBytes + " bytes: " + bytes);
        }
        return bytes / elementBytes;
    }

    private static int gemm(MethodHandle function, LibraryInvocation invocation, MemorySegment queue, boolean fp64, int elementBytes) throws Throwable {
        if (fp64) {
            return (int) function.invokeExact(
                integer(invocation, 0),
                integer(invocation, 1),
                integer(invocation, 2),
                size(invocation, 3),
                size(invocation, 4),
                size(invocation, 5),
                (double) invocation.getArg(6),
                buffer(invocation, 7),
                elementOffset(invocation, 7, elementBytes),
                size(invocation, 8),
                buffer(invocation, 9),
                elementOffset(invocation, 9, elementBytes),
                size(invocation, 10),
                (double) invocation.getArg(11),
                buffer(invocation, 12),
                elementOffset(invocation, 12, elementBytes),
                size(invocation, 13), queue, MemorySegment.NULL);
        }
        return (int) function.invokeExact(
                integer(invocation, 0),
                integer(invocation, 1),
                integer(invocation, 2),
                size(invocation, 3),
                size(invocation, 4),
                size(invocation, 5),
                (float) invocation.getArg(6),
                buffer(invocation, 7),
                elementOffset(invocation, 7, elementBytes),
                size(invocation, 8),
                buffer(invocation, 9),
                elementOffset(invocation, 9, elementBytes),
                size(invocation, 10),
                (float) invocation.getArg(11),
                buffer(invocation, 12),
                elementOffset(invocation, 12, elementBytes),
                size(invocation, 13), queue, MemorySegment.NULL);
    }

    private static int gemv(MethodHandle function, LibraryInvocation invocation, MemorySegment queue, boolean fp64, int elementBytes) throws Throwable {
        if (fp64) {
            return (int) function.invokeExact(
                integer(invocation, 0),
                integer(invocation, 1),
                size(invocation, 2),
                size(invocation, 3),
                (double) invocation.getArg(4),
                buffer(invocation, 5),
                elementOffset(invocation, 5, elementBytes),
                size(invocation, 6),
                buffer(invocation, 7),
                elementOffset(invocation, 7, elementBytes),
                size(invocation, 8),
                (double) invocation.getArg(9),
                buffer(invocation, 10),
                elementOffset(invocation, 10, elementBytes),
                size(invocation, 11), queue, MemorySegment.NULL);
        }
        return (int) function.invokeExact(
                integer(invocation, 0),
                integer(invocation, 1),
                size(invocation, 2),
                size(invocation, 3),
                (float) invocation.getArg(4),
                buffer(invocation, 5),
                elementOffset(invocation, 5, elementBytes),
                size(invocation, 6),
                buffer(invocation, 7),
                elementOffset(invocation, 7, elementBytes),
                size(invocation, 8),
                (float) invocation.getArg(9),
                buffer(invocation, 10),
                elementOffset(invocation, 10, elementBytes),
                size(invocation, 11), queue, MemorySegment.NULL);
    }

    private static int axpy(MethodHandle function, LibraryInvocation invocation, MemorySegment queue, boolean fp64, int elementBytes) throws Throwable {
        if (fp64) {
            return (int) function.invokeExact(
                size(invocation, 0),
                (double) invocation.getArg(1),
                buffer(invocation, 2),
                elementOffset(invocation, 2, elementBytes),
                size(invocation, 3),
                buffer(invocation, 4),
                elementOffset(invocation, 4, elementBytes),
                size(invocation, 5), queue, MemorySegment.NULL);
        }
        return (int) function.invokeExact(
                size(invocation, 0),
                (float) invocation.getArg(1),
                buffer(invocation, 2),
                elementOffset(invocation, 2, elementBytes),
                size(invocation, 3),
                buffer(invocation, 4),
                elementOffset(invocation, 4, elementBytes),
                size(invocation, 5), queue, MemorySegment.NULL);
    }

    private static int dot(MethodHandle function, LibraryInvocation invocation, MemorySegment queue, int elementBytes) throws Throwable {
        return (int) function.invokeExact(
                size(invocation, 0),
                buffer(invocation, 5),
                elementOffset(invocation, 5, elementBytes),
                buffer(invocation, 1),
                elementOffset(invocation, 1, elementBytes),
                size(invocation, 2),
                buffer(invocation, 3),
                elementOffset(invocation, 3, elementBytes),
                size(invocation, 4), queue, MemorySegment.NULL);
    }
}
