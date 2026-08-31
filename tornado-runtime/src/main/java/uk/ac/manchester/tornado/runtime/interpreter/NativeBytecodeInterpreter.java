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

    /**
     * Tells the native loop that the Java interpreter is running a warm-up pass, which
     * compiles kernels but does not execute them.
     *
     */
    public static final int FLAG_WARMUP = 1;

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

    /**
     * Name of the shared library built by the {@code tornado-runtime-jni} module.
     */
    private static final String LIBRARY_NAME = "tornado-runtime";

    /**
     * The bytecodes that the native loop implements. This list must be kept in sync with the
     * cases of the switch in {@code tornado_interpreter.cpp}.
     */
    private static final TornadoVMBytecodes[] PORTED_BYTECODES = {
        // Only END is ported now
        TornadoVMBytecodes.END
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
     * dereference the pointer: on-heap arrays have to be pinned per transfer, later.
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
     * duration of the call and does not write them.
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
     * @return a {@code STATUS_*} value in the high 32 bits and the resulting position, which
     *     is always a valid bytecode boundary, in the low 32 bits.
     */
    static native long execute(byte[] bytecode, int position, int limit, int flags, long[] bufferHandles, long[] bufferOffsets, long[] bufferSizes, long[] hostPointers, long[] kernelHandles,
            long[] programHandles, byte[] constants, long commandQueue, long deviceContext, int backend, int deviceIndex, int platformIndex, long executionPlanId);

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
