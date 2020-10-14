/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.DeviceBuffer;

public class AtomicsBuffer extends OCLByteBuffer implements DeviceBuffer {

    private int numAtomics;
    private boolean onDevice;
    private int[] atomicsList;
    private final static int OFFSET = 0;

    AtomicsBuffer(long offset, int[] arr, OCLDeviceContext device) {
        super(device, offset, arr.length * 4);
        buffer.clear();
        numAtomics = 0;
        onDevice = false;
        this.atomicsList = arr;
        allocateAtomicRegion();
    }

    @Override
    public boolean isOnDevice() {
        return onDevice;
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
    public void write() {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public int enqueueWrite() {
        onDevice = true;
        // Non-blocking write
        return enqueueWrite(toAtomicAddress(), atomicsList, OFFSET, null);
    }

    @Override
    public int enqueueRead() {
        onDevice = true;
        // Blocking read
        return read(toAtomicAddress(), atomicsList);
    }

    @Override
    public void set(int[] arr) {
        this.atomicsList = arr;
    }

    @Override
    public int[] getBuffer() {
        return this.atomicsList;
    }

    @Override
    public int enqueueWrite(int[] events) {
        onDevice = true;
        return enqueueWrite(toAtomicAddress(), atomicsList, OFFSET, events);
    }

    @Override
    public void reset() {
        buffer.mark();
        buffer.reset();
        onDevice = false;
    }

    @Override
    public int getNumberOfAtomics() {
        return numAtomics;
    }

    @Override
    public void dump() {
        throw new TornadoRuntimeException("Not implemented");
    }

    @Override
    public void push(int value) {
        atomicsList[numAtomics] = value;
        numAtomics++;
    }

}
