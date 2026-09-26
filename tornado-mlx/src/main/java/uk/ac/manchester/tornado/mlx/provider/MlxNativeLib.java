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
package uk.ac.manchester.tornado.mlx.provider;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * The parts of the MLX interop that the generated {@link MlxC} bindings cannot provide: the
 * deleter upcall that tells whether MLX adopted or copied a buffer, Objective-C retain/release for
 * TornadoVM's MTLBuffers, and small helpers over {@link MlxC}. Restricted FFM operations go through
 * {@link FFMSupport}, which runs them inside {@code tornado.runtime}.
 */
final class MlxNativeLib {

    /** {@code mlx_dtype} values (mlx/c/array.h). */
    static final int MLX_BOOL = 0;
    static final int MLX_UINT8 = 1;
    static final int MLX_UINT16 = 2;
    static final int MLX_UINT32 = 3;
    static final int MLX_INT8 = 5;
    static final int MLX_INT16 = 6;
    static final int MLX_INT32 = 7;
    static final int MLX_INT64 = 8;
    static final int MLX_FLOAT16 = 9;
    static final int MLX_FLOAT32 = 10;
    static final int MLX_FLOAT64 = 11;
    static final int MLX_BFLOAT16 = 12;
    static final int MLX_COMPLEX64 = 13;

    private static final SymbolLookup LIBOBJC = FFMSupport.loadLibrary("/usr/lib/libobjc.A.dylib");
    private static final MethodHandle OBJC_RETAIN = LIBOBJC == null ? null : FFMSupport.downcall(LIBOBJC, FunctionDescriptor.of(C_LONG, C_LONG), "objc_retain");
    private static final MethodHandle OBJC_RELEASE = LIBOBJC == null ? null : FFMSupport.downcall(LIBOBJC, FunctionDescriptor.ofVoid(C_LONG), "objc_release");

    /**
     * Deleter handed to {@code mlx_array_new_data_managed}. MLX calls it when it no longer needs
     * the memory: right away if it had to copy the data, otherwise when the array is freed. The
     * count therefore tells the provider whether a wrap adopted the memory or copied it.
     */
    private static final AtomicLong RELEASES = new AtomicLong();
    private static final MemorySegment COUNTING_DELETER;

    /**
     * The last MLX error on this thread. mlx-c's default error handler prints the message and calls {@code exit}, which
     * would end the JVM on any MLX error (an operation MLX does not implement on the CPU, a shape it rejects); the
     * handler installed here records the message instead, so the call returns its failure status and {@link #check}
     * turns it into an exception.
     */
    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

    static {
        MemorySegment deleter = null;
        if (MlxC.isLoaded()) {
            try {
                MethodHandle target = MethodHandles.lookup().findStatic(MlxNativeLib.class, "onRelease", MethodType.methodType(void.class, MemorySegment.class));
                deleter = FFMSupport.upcallStub(target, FunctionDescriptor.ofVoid(C_POINTER), Arena.global());
                SymbolLookup mlxc = FFMSupport.loadLibrary("libmlxc.dylib", "/opt/homebrew/opt/mlx-c/lib/libmlxc.dylib", "/usr/local/opt/mlx-c/lib/libmlxc.dylib", "libmlxc.so");
                MethodHandle setHandler = FFMSupport.downcall(mlxc, FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER), "mlx_set_error_handler");
                MethodHandle onError = MethodHandles.lookup().findStatic(MlxNativeLib.class, "onError", MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
                setHandler.invokeExact(FFMSupport.upcallStub(onError, FunctionDescriptor.ofVoid(C_POINTER, C_POINTER), Arena.global()), MemorySegment.NULL, MemorySegment.NULL);
            } catch (Throwable e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        COUNTING_DELETER = deleter;
    }

    private MlxNativeLib() {
    }

    private static void onRelease(MemorySegment data) {
        RELEASES.incrementAndGet();
    }

    private static void onError(MemorySegment message, MemorySegment data) {
        LAST_ERROR.set(FFMSupport.readCString(message));
    }

    /** Where MLX installs its compiled kernel library, next to {@code libmlx.dylib}; null if not found. */
    static String metallibPath() {
        for (String candidate : new String[] { "/opt/homebrew/opt/mlx/lib/mlx.metallib", "/usr/local/opt/mlx/lib/mlx.metallib" }) {
            if (Files.isReadable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /** Throws if libmlxc (or the Objective-C runtime) could not be loaded. */
    static void load() {
        if (!MlxC.isLoaded() || OBJC_RETAIN == null) {
            throw new TornadoRuntimeException("[ERROR] Unable to load mlx-c. Install it (e.g. `brew install mlx-c`) and make sure libmlxc is on the library path.");
        }
    }

    static void check(int status, String call) {
        if (status != 0) {
            String error = LAST_ERROR.get();
            LAST_ERROR.remove();
            throw new TornadoRuntimeException("[ERROR] " + call + " failed with status " + status + (error == null ? "" : ": " + error));
        }
    }

    static String version() {
        load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment slot = arena.allocate(C_POINTER);
            slot.set(C_POINTER, 0, MlxC.mlx_string_new());
            check(MlxC.mlx_version(slot), "mlx_version");
            MemorySegment str = slot.get(C_POINTER, 0);
            String version = FFMSupport.readCString(MlxC.mlx_string_data(str));
            MlxC.mlx_string_free(str);
            return version;
        }
    }

    /** Number of times MLX has released memory it was handed; see {@link #COUNTING_DELETER}. */
    static long releases() {
        return RELEASES.get();
    }

    /** Wraps {@code shape} elements at {@code address} as an MLX array without asking MLX to copy them. */
    static MemorySegment wrap(long address, int[] shape, int dtype) {
        try (Arena arena = Arena.ofConfined()) {
            // allocate + copy rather than allocateArray/allocateFrom, whose names differ between
            // the JDK 21 preview FFM API and JDK 22+.
            MemorySegment shapeSegment = FFMSupport.allocateArray(arena, C_INT, shape.length);
            MemorySegment.copy(shape, 0, shapeSegment, C_INT, 0, shape.length);
            return MlxC.mlx_array_new_data_managed(MemorySegment.ofAddress(address), shapeSegment, shape.length, dtype, COUNTING_DELETER);
        }
    }

    static void eval(MemorySegment array) {
        check(MlxC.mlx_array_eval(array), "mlx_array_eval");
    }

    /** Address of an evaluated array's first element, whatever its dtype. */
    static long dataAddress(MemorySegment array) {
        return MlxC.mlx_array_data_uint8(array).address();
    }

    static void free(MemorySegment array) {
        MlxC.mlx_array_free(array);
    }

    static void retain(long object) {
        try {
            long ignored = (long) OBJC_RETAIN.invokeExact(object);
        } catch (Throwable t) {
            throw new TornadoRuntimeException("[ERROR] objc_retain failed: " + t);
        }
    }

    static void release(long object) {
        try {
            OBJC_RELEASE.invokeExact(object);
        } catch (Throwable t) {
            throw new TornadoRuntimeException("[ERROR] objc_release failed: " + t);
        }
    }
}
