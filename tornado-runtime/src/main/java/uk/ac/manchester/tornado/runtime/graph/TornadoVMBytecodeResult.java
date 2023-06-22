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

/**
 * It represents the result of a {@link TornadoVMBytecodeBuilder}. It contains
 * the bytecode and its size.
 */
public class TornadoVMBytecodeResult {
    private final byte[] bytecode;
    private final int size;

    /**
     * It constructs a new TornadoVMBytecodeResult object.
     *
     * @param bytecode
     *            the bytecode as a byte array
     * @param size
     *            the size of the bytecode
     */
    public TornadoVMBytecodeResult(byte[] bytecode, int size) {
        this.bytecode = bytecode;
        this.size = size;
    }

    /**
     * It returns the bytecode as a byte array.
     *
     * @return the bytecode as a byte array
     */
    public byte[] getBytecode() {
        return bytecode;
    }

    /**
     * It returns the size of the bytecode.
     *
     * @return the size of the bytecode
     */
    public int getBytecodeSize() {
        return size;
    }
}
