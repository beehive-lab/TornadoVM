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
 * The library is loaded only when {@link TornadoOptions#INTERPRETER_NATIVE} is set. If it
 * cannot be loaded, {@link #isAvailable()} reports {@code false} and callers keep using the
 * Java interpreter.
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
     * compiles kernels but replays nothing.
     *
     */
    public static final int FLAG_WARMUP = 1;

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
            return selfCheck();
        } catch (UnsatisfiedLinkError e) {
            new TornadoLogger(NativeBytecodeInterpreter.class).warn("could not use lib%s, falling back to the Java bytecode interpreter: %s", LIBRARY_NAME, e.getMessage());
            return false;
        }
    }

    /**
     * It runs the native loop over a single END bytecode.
     *
     * <p>
     * The JVM binds native methods lazily, so loading the library on its own proves nothing
     * about the entry point. This forces the binding and checks that the library agrees with
     * this class on the opcode values and on how a result is packed, so that a missing or
     * out-of-date library is caught here instead of part-way through an execution.
     *
     * @return true when the library behaved as expected.
     */
    private static boolean selfCheck() {
        final long result = execute(new byte[] { TornadoVMBytecodes.END.value() }, 0, 1, 0);
        if (statusOf(result) == STATUS_END && positionOf(result) == 1) {
            return true;
        }
        new TornadoLogger(NativeBytecodeInterpreter.class).warn("lib%s failed its self check (result 0x%x), falling back to the Java bytecode interpreter", LIBRARY_NAME, result);
        return false;
    }

    /**
     * @return true when the native bytecode loop was requested and its library is loaded.
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Runs the native bytecode loop over {@code bytecode}, starting at {@code position} and
     * stopping at the first bytecode that is not implemented natively.
     *
     * @param bytecode
     *     the bytecode buffer backing the current {@code TornadoVMBytecodeResult}.
     * @param position
     *     the index to start decoding from. Must satisfy {@code 0 <= position <= limit}.
     * @param limit
     *     the number of valid bytes in {@code bytecode}. Must not exceed its length.
     * @param flags
     *     a bitwise OR of the {@code FLAG_*} constants of this class.
     * @return a {@code STATUS_*} value in the high 32 bits and the resulting position, which
     *     is always a valid bytecode boundary, in the low 32 bits.
     */
    static native long execute(byte[] bytecode, int position, int limit, int flags);

    /**
     * Extracts the {@code STATUS_*} value from a result returned by
     * {@link #execute(byte[], int, int, int)}.
     */
    static int statusOf(long result) {
        return (int) (result >>> 32);
    }

    /**
     * Extracts the bytecode position from a result returned by
     * {@link #execute(byte[], int, int, int)}.
     */
    static int positionOf(long result) {
        return (int) result;
    }
}
