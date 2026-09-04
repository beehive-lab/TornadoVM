/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.api.types.collections.TornadoCollectionInterface;
import uk.ac.manchester.tornado.api.types.images.TornadoImagesInterface;
import uk.ac.manchester.tornado.api.types.matrix.TornadoMatrixInterface;
import uk.ac.manchester.tornado.api.types.volumes.TornadoVolumesInterface;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodes;

/**
 * Entry point to the native TornadoVM bytecode loop provided by the
 * {@code tornado-runtime-jni} module.
 *
 * <p>
 * The native loop decodes bytecodes until it reaches one that it does not implement, at
 * which point it hands the position of that bytecode back so that
 * {@link TornadoVMInterpreter} can execute it in Java and resume. It is therefore always
 * safe to enable: the native side is an accelerator for a prefix of the bytecode stream,
 * never a replacement for the Java interpreter.
 *
 * <p>
 * The library is loaded only when {@link TornadoOptions#INTERPRETER_NATIVE} is set. If
 * {@code System.loadLibrary} fails, {@link #isAvailable()} reports {@code false} and callers
 * keep using the Java interpreter.
 */
public final class NativeBytecodeInterpreter {

    /**
     * The bytecode stream ended with an END bytecode. The returned position is just past it.
     */
    public static final int STATUS_END = 0;

    /**
     * The bytecode stream ran out of bytes before an END bytecode. The returned position is
     * the limit that was passed in.
     */
    public static final int STATUS_EOF = 1;

    /**
     * The next bytecode is not implemented natively. The returned position points at that
     * bytecode so that the Java interpreter can decode and execute it from scratch.
     */
    public static final int STATUS_BAIL = 2;

    /** A handled bytecode or backend operation failed. */
    public static final int STATUS_ERROR = 3;

    /**
     * Tells the native loop that the Java interpreter is running a warm-up pass, which
     * compiles kernels but does not execute them.
     *
     */
    public static final int FLAG_WARMUP = 1;

    /**
     * Tells the native loop that event-list tracking is on ({@code useDependencies}).
     * Without it {@code ADD_DEPENDENCY} is a no-op, matching the Java handler.
     */
    public static final int FLAG_USE_DEPENDENCIES = 2;

    /**
     * Tells the native loop that an execution graph has already been captured
     * ({@code executionGraphHandles} is non-empty). {@code ALLOC} and {@code DEALLOC} are
     * skipped, matching the Java {@code execute()} loop.
     */
    public static final int FLAG_GRAPH_INSTANTIATED = 8;

    /** Batch memory operations still use the Java handlers in this MVP. */
    public static final int FLAG_BATCHED_EXECUTION = 16;

    /** Forced physical deallocation still uses Java's whole-pool release. */
    public static final int FLAG_FORCE_DEALLOCATION = 32;

    /** Flush the backend queue when the native loop consumes END. */
    public static final int FLAG_USE_VM_FLUSH = 64;

    /**
     * Object already has device contents. {@code TRANSFER_HOST_TO_DEVICE_ONCE} skips the copy.
     */
    static final byte OBJ_HAS_CONTENT = 1;

    /**
     * Buffer is locked for reuse. {@code DEALLOC} is a no-op.
     */
    static final byte OBJ_LOCKED = 2;

    /** Object is launch metadata. Native allocation, transfer and deallocation skip it. */
    static final byte OBJ_KERNEL_CONTEXT = 4;

    static final byte OBJ_PERSISTENT = 8;
    static final byte OBJ_NATIVE_ALLOCATED = 16;
    static final byte OBJ_NATIVE_DEALLOCATED = 32;
    static final byte OBJ_NATIVE_ALLOCATION_PREPARED = 64;
    /** Host pointer addresses a reusable staging buffer, so asynchronous H2D is unsafe. */
    static final byte OBJ_STAGED_HOST_BUFFER = (byte) 128;

    static final byte OBJECT_KIND_UNSUPPORTED = 0;
    static final byte OBJECT_KIND_SEGMENT = 1;
    static final byte OBJECT_KIND_BYTE_ARRAY = 2;
    static final byte OBJECT_KIND_CHAR_ARRAY = 3;
    static final byte OBJECT_KIND_SHORT_ARRAY = 4;
    static final byte OBJECT_KIND_INT_ARRAY = 5;
    static final byte OBJECT_KIND_LONG_ARRAY = 6;
    static final byte OBJECT_KIND_FLOAT_ARRAY = 7;
    static final byte OBJECT_KIND_DOUBLE_ARRAY = 8;
    static final byte OBJECT_KIND_ATOMIC = 9;

    /**
     * Bytes per packed constant: one type tag and an 8-byte little-endian payload.
     * Must match {@code TORNADO_CONSTANT_ENTRY_BYTES} in {@code tornado_context.h}.
     */
    static final int CONSTANT_ENTRY_BYTES = 9;

    static final byte CONSTANT_NONE = 0;
    static final byte CONSTANT_BYTE = 1;
    static final byte CONSTANT_CHAR = 2;
    static final byte CONSTANT_SHORT = 3;
    static final byte CONSTANT_INT = 4;
    static final byte CONSTANT_FLOAT = 5;
    static final byte CONSTANT_LONG = 6;
    static final byte CONSTANT_DOUBLE = 7;
    static final byte CONSTANT_HALF = 8;

    /* Per-task native launch table. Must match tornado_context.h. */
    static final int LAUNCH_META_STRIDE = 18;
    static final int LAUNCH_META_FRAME_BUFFER = 0;
    static final int LAUNCH_META_CONSTANT_BUFFER = 1;
    static final int LAUNCH_META_ATOMIC_BUFFER = 2;
    static final int LAUNCH_META_LOCAL_MEMORY = 3;
    static final int LAUNCH_META_DIMENSIONS = 4;
    static final int LAUNCH_META_FLAGS = 5;
    static final int LAUNCH_META_CONTEXT = 6;
    static final int LAUNCH_META_GLOBAL_OFFSET = 9;
    static final int LAUNCH_META_GLOBAL_WORK = 12;
    static final int LAUNCH_META_LOCAL_WORK = 15;
    static final long LAUNCH_FLAG_SUPPORTED = 1;
    static final long LAUNCH_FLAG_HAS_LOCAL_WORK = 2;

    /**
     * Name of the shared library built by the {@code tornado-runtime-jni} module.
     */
    private static final String LIBRARY_NAME = "tornado-runtime";

    /**
     * The bytecodes that the native loop implements. This list must be kept in sync with the
     * cases of the switch in {@code tornado_interpreter.cpp}.
     *
     * <p>
     * {@code ADD_DEPENDENCY}, {@code ON_DEVICE} and {@code PERSIST} update native event state.
     * {@code ALLOC} and all four {@code TRANSFER_*} bytecodes call the OpenCL, CUDA, or
     * Metal backend directly. Normal {@code DEALLOC} updates the existing buffer pool
     * without a driver call. Primitive arrays are pinned once for the native-loop call.
     * Images, matrices and collections are copied through a contiguous host staging
     * buffer that matches the compound device layout on OpenCL, CUDA and Metal.
     * {@code AtomicInteger} has no per-object buffer: native ALLOC/TRANSFER/DEALLOC/LAUNCH
     * skip it the same way as {@code KernelContext}. Java writes the shared atomics
     * region before the native call and copies values back afterwards.
     * Batch memory operations, dependency-producing transfers, and forced
     * pool release also bail out because their Java lifecycle is not represented here yet.
     * {@code BARRIER} is intentionally not ported. {@code INIT}, {@code BEGIN} and
     * {@code CONTEXT} are skipped if they appear.
     */
    private static final TornadoVMBytecodes[] PORTED_BYTECODES = {
        TornadoVMBytecodes.INIT,
        TornadoVMBytecodes.BEGIN,
        TornadoVMBytecodes.CONTEXT,
        TornadoVMBytecodes.END,
        TornadoVMBytecodes.ADD_DEPENDENCY,
        TornadoVMBytecodes.ON_DEVICE,
        TornadoVMBytecodes.PERSIST,
        TornadoVMBytecodes.ALLOC,
        TornadoVMBytecodes.DEALLOC,
        TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ONCE,
        TornadoVMBytecodes.TRANSFER_HOST_TO_DEVICE_ALWAYS,
        TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS,
        TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING,
        TornadoVMBytecodes.LAUNCH
    };

    private static final boolean AVAILABLE = loadNativeLibrary();

    private NativeBytecodeInterpreter() {
    }

    /**
     * It reports whether the native loop implements a bytecode.
     *
     * <p>
     * The interpreter loop calls this before crossing into the native loop.
     *
     * @param op
     *     the opcode to test, as read from the bytecode buffer.
     * @return true when crossing into the native loop is worthwhile for this bytecode.
     */
    static boolean isPorted(byte op) {
        for (TornadoVMBytecodes bytecode : PORTED_BYTECODES) {
            if (bytecode.value() == op) {
                return true;
            }
        }
        return false;
    }

    private static boolean loadNativeLibrary() {
        if (!TornadoOptions.INTERPRETER_NATIVE) {
            return false;
        }
        try {
            System.loadLibrary(LIBRARY_NAME);
            return true;
        } catch (UnsatisfiedLinkError e) {
            new TornadoLogger(NativeBytecodeInterpreter.class).warn("could not use lib%s, falling back to the Java bytecode interpreter: %s", LIBRARY_NAME, e.getMessage());
            return false;
        }
    }

    /**
     * @return true when the native bytecode loop was requested and its library is loaded.
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Packs the interpreter's constant list into the tagged blob the native loop reads.
     *
     * <p>
     * Each constant is one type byte followed by an 8-byte little-endian payload. Unused high
     * bytes of the payload are zero. An empty list becomes a zero-length array, never
     * {@code null}.
     */
    static byte[] packConstants(List<Object> constants) {
        if (constants == null || constants.isEmpty()) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(constants.size() * CONSTANT_ENTRY_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (Object value : constants) {
            final int start = buffer.position();
            buffer.put(tagOf(value));
            writePayload(buffer, value);
            while (buffer.position() < start + CONSTANT_ENTRY_BYTES) {
                buffer.put((byte) 0);
            }
        }
        return buffer.array();
    }

    private static byte tagOf(Object value) {
        return switch (value) {
            case Byte b -> CONSTANT_BYTE;
            case Character c -> CONSTANT_CHAR;
            case Short s -> CONSTANT_SHORT;
            case Integer i -> CONSTANT_INT;
            case Float f -> CONSTANT_FLOAT;
            case Long l -> CONSTANT_LONG;
            case Double d -> CONSTANT_DOUBLE;
            case HalfFloat h -> CONSTANT_HALF;
            case Boolean b -> CONSTANT_BYTE;
            case null, default -> CONSTANT_NONE;
        };
    }

    private static void writePayload(ByteBuffer buffer, Object value) {
        switch (value) {
            case Byte b -> buffer.put(b);
            case Character c -> buffer.putChar(c);
            case Short s -> buffer.putShort(s);
            case Integer i -> buffer.putInt(i);
            case Float f -> buffer.putFloat(f);
            case Long l -> buffer.putLong(l);
            case Double d -> buffer.putDouble(d);
            case HalfFloat h -> buffer.putShort(h.getHalfFloatValue());
            case Boolean b -> buffer.put(b ? (byte) 1 : (byte) 0);
            case null, default -> {
            }
        }
    }

    /**
     * Stable host address of an off-heap Tornado object, or {@code 0} when the object is a
     * Java array (or anything else without a Panama segment). A zero means C++ must not
     * dereference the pointer; on-heap primitive arrays are pinned once for the native call.
     *
     * <p>
     * Images, matrices and collections return the payload segment only. That is not the
     * compound device layout. Field buffers copy those objects through
     * {@code OBJ_STAGED_HOST_BUFFER} instead of this pointer.
     */
    static long hostPointerOf(Object object) {
        if (object == null) {
            return 0L;
        }
        return switch (object) {
            case TornadoNativeArray array -> array.getSegmentWithHeader().address();
            case TornadoCollectionInterface<?> collection -> collection.getSegmentWithHeader().address();
            case TornadoImagesInterface<?> image -> image.getSegmentWithHeader().address();
            case TornadoMatrixInterface<?> matrix -> matrix.getSegmentWithHeader().address();
            case TornadoVolumesInterface<?> volume -> volume.getSegmentWithHeader().address();
            default -> 0L;
        };
    }

    /**
     * Runs the native bytecode loop over {@code bytecode}, starting at {@code position} and
     * stopping at the first bytecode that is not implemented natively.
     *
     * <p>
     * The table arguments may be {@code null} when the caller has nothing to publish. Parallel
     * {@code long[]} tables that are non-null must share a length. C++ pins the arrays for the
     * duration of the call. Buffer handles, object flags, {@code lastEvent},
     * {@code eventsIndexes} and {@code events} can be updated.
     *
     * @param bytecode
     *     the bytecode buffer backing the current {@code TornadoVMBytecodeResult}.
     * @param position
     *     the index to start decoding from. Must satisfy {@code 0 <= position <= limit}.
     * @param limit
     *     the number of valid bytes in {@code bytecode}. Must not exceed its length.
     * @param flags
     *     a bitwise OR of the {@code FLAG_*} constants of this class.
     * @param bufferHandles
     *     device buffer handles indexed by object index, or {@code null}.
     * @param bufferOffsets
     *     device buffer offsets indexed by object index, or {@code null}.
     * @param bufferSizes
     *     device buffer sizes indexed by object index, or {@code null}.
     * @param hostPointers
     *     off-heap host addresses indexed by object index, or {@code null}. {@code 0} marks
     *     an on-heap object.
     * @param kernelHandles
     *     kernel handles indexed by local task index, or {@code null}.
     * @param programHandles
     *     program handles indexed by local task index, or {@code null}.
     * @param constants
     *     packed constant blob from {@link #packConstants(List)}, or {@code null}.
     * @param commandQueue
     *     backend command-queue / stream handle ({@code cl_command_queue}, {@code CUstream},
     *     {@code MTLCommandQueue}), or {@code 0} if the device does not expose one.
     * @param deviceContext
     *     backend context handle ({@code cl_context}, {@code CUcontext}, Metal context), or
     *     {@code 0} if the device does not expose one.
     * @param backend
     *     {@link uk.ac.manchester.tornado.api.enums.TornadoVMBackendType#ordinal()} of this
     *     interpreter's device. C++ {@code TornadoBackend} must stay in the same order as that
     *     enum.
     * @param deviceIndex
     *     index of this interpreter's device within its platform.
     * @param platformIndex
     *     index of this interpreter's platform.
     * @param executionPlanId
     *     execution-plan id this interpreter is running for.
     * @param lastEvent
     *     length-1 in/out slot for the loop-carried event-pool id (Java {@code lastEvent}).
     * @param eventsIndexes
     *     write cursors for each event list, or {@code null} when there are no lists.
     * @param events
     *     event-list rows (Java {@code events}), or {@code null}. Null rows are allocated
     *     on first {@code ADD_DEPENDENCY}, matching {@code waitListForWrite}.
     * @param eventRowLength
     *     length of each event-list row ({@code TornadoOptions.MAX_EVENTS}).
     * @param objectFlags
     *     per-object bits ({@code OBJ_HAS_CONTENT}, {@code OBJ_LOCKED},
     *     {@code OBJ_KERNEL_CONTEXT}). {@code HAS_CONTENT} is written on a successful
     *     {@code TRANSFER_HOST_TO_DEVICE_ONCE}.
     * @return a {@code STATUS_*} value in the high 32 bits and the resulting position, which
     *     is always a valid bytecode boundary, in the low 32 bits.
     */
    static native long execute(byte[] bytecode, int position, int limit, int flags, long[] bufferHandles, long[] bufferOffsets, long[] bufferSizes, long[] hostPointers, long[] kernelHandles,
            long[] programHandles, long[] launchMetadata, byte[] constants, long commandQueue, long deviceContext, int backend, int deviceIndex, int platformIndex, long executionPlanId, int[] lastEvent,
            int[] eventsIndexes, int[][] events, int eventRowLength, byte[] objectFlags, byte[] objectAccesses, Object[] objects, byte[] objectKinds, long[] dataOffsets, long[] partialCopySizes);

    /**
     * Extracts the {@code STATUS_*} value from a result returned by {@link #execute}.
     */
    static int statusOf(long result) {
        return (int) (result >>> 32);
    }

    /**
     * Extracts the bytecode position from a result returned by {@link #execute}.
     */
    static int positionOf(long result) {
        return (int) result;
    }
}
