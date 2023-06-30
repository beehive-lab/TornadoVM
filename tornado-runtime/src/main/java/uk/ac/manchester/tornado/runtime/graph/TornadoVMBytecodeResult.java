/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

/**
 * It represents the result of a {@link uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodeBuilder}. It contains
 * the bytecode and its size.
 */
/**
 * The TornadoVMBytecodeResult class represents the result of a TornadoVM
 * bytecode compilation. It provides methods to access and manipulate the
 * bytecode.
 */
public class TornadoVMBytecodeResult {
    private final byte[] bytecode;
    private final ByteBuffer buffer;

    /**
     * Constructs a new TornadoVMBytecodeResult object with the given bytecode and
     * size.
     *
     * @param bytecode
     *            the bytecode as a byte array
     * @param size
     *            the size of the bytecode
     */
    TornadoVMBytecodeResult(byte[] bytecode, int size) {
        this.bytecode = bytecode;
        this.buffer = setupBytecodeBuffer(bytecode, size);
        TornadoInternalError.guarantee(buffer.get() == TornadoVMBytecodes.INIT.value(), "invalid code");
    }

    /**
     * Returns the bytecode as a byte array.
     *
     * @return the bytecode as a byte array
     */
    public byte[] getBytecode() {
        return bytecode;
    }

    /**
     * Sets up the bytecode buffer using the given bytecode array and size. The
     * buffer is set to little-endian byte order and its limit is set to the given
     * size.
     *
     * @param bytecode
     *            the bytecode as a byte array
     * @param size
     *            the size of the bytecode
     * @return the configured ByteBuffer
     */
    private ByteBuffer setupBytecodeBuffer(byte[] bytecode, int size) {
        return ByteBuffer.wrap(bytecode).order(ByteOrder.LITTLE_ENDIAN).limit(size);
    }

    /**
     * Retrieves the next four bytes from the bytecode buffer and interprets them as
     * an integer value.
     *
     * @return the next integer value in the bytecode buffer
     */
    public int getInt() {
        return buffer.getInt();
    }

    /**
     * Retrieves the next byte from the bytecode buffer.
     *
     * @return the next byte in the bytecode buffer
     */
    public byte get() {
        return buffer.get();
    }

    /**
     * Marks the current position in the bytecode buffer.
     *
     * @return the bytecode buffer with the current position marked
     */
    public ByteBuffer mark() {
        return buffer.mark();
    }

    /**
     * Retrieves the next eight bytes from the bytecode buffer and interprets them
     * as a long value.
     *
     * @return the next long value in the bytecode buffer
     */
    public long getLong() {
        return buffer.getLong();
    }

    /**
     * Checks if there are remaining bytes in the bytecode buffer.
     *
     * @return true if there are remaining bytes, false otherwise
     */
    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    /**
     * Resets the position of the bytecode buffer to the previously marked position.
     */
    public void reset() {
        buffer.reset();
    }

}
