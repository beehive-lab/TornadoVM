/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
    private final boolean serialTaskGraph;

    /**
     * Constructs a new TornadoVMBytecodeResult object with the given bytecode and
     * size.
     *
     * @param bytecode
     *            the bytecode as a byte array
     * @param size
     *            the size of the bytecode
     * @param serialTaskGraph
     *            whether this (non-batched) graph's tasks form a dependency chain (max width 1),
     *            in which case intra-plan concurrency cannot overlap anything for this graph;
     *            always {@code false} for batched graphs
     */
    TornadoVMBytecodeResult(byte[] bytecode, int size, boolean serialTaskGraph) {
        this.bytecode = bytecode;
        this.serialTaskGraph = serialTaskGraph;
        this.buffer = setupBytecodeBuffer(bytecode, size);
        TornadoInternalError.guarantee(buffer.get() == TornadoVMBytecodes.INIT.value(), "invalid code");
    }

    /**
     * Whether this graph's tasks form a dependency chain (no two tasks are independent);
     * always {@code false} for batched graphs. The interpreter runs such graphs single-stream
     * even when the plan requested intra-plan concurrency, avoiding pure cross-stream event
     * overhead.
     */
    public boolean isSerialTaskGraph() {
        return serialTaskGraph;
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

    public int position() {
        return buffer.position();
    }

    public void position(int newPosition) {
        buffer.position(newPosition);
    }

}
