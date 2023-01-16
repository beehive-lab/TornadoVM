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
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.drivers.opencl.mm.OCLKernelArgs.RESERVED_SLOTS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

import uk.ac.manchester.tornado.api.memory.ObjectBuffer;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class OCLMemoryManager extends TornadoLogger implements TornadoMemoryProvider {

    private final OCLDeviceContext deviceContext;
    private long constantPointer;
    private long atomicsRegion = -1;

    private static final int MAX_NUMBER_OF_ATOMICS_PER_KERNEL = 128;
    private static final int INTEGER_BYTES_SIZE = 4;

    public OCLKernelArgs oclKernelCallWrapper = null;

    public OCLMemoryManager(final OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public long getHeapSize() {
        return DEVICE_AVAILABLE_MEMORY;
    }

    private static long align(final long address, final long alignment) {
        return (address % alignment == 0) ? address : address + (alignment - address % alignment);
    }

    public OCLKernelArgs createCallWrapper(final int maxArgs) {
        if (this.oclKernelCallWrapper == null) {
            long kernelCallBuffer = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_ONLY, RESERVED_SLOTS * Long.BYTES).getBuffer();
            this.oclKernelCallWrapper = new OCLKernelArgs(kernelCallBuffer, maxArgs, deviceContext);
        }
        return this.oclKernelCallWrapper;
    }

    public ObjectBuffer createAtomicsBuffer(final int[] arr) {
        return new AtomicsBuffer(arr, deviceContext);
    }

    /**
     * Allocate regions on the device.
     */
    public void allocateDeviceMemoryRegions() {
        this.constantPointer = createBuffer(OCLMemFlags.CL_MEM_READ_ONLY | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, 4).getBuffer();
        allocateAtomicRegion();
    }

    public OCLContext.OCLBufferResult createBuffer(long size, long flags) {
        return deviceContext.getPlatformContext().createBuffer(flags, size);
    }

    public void releaseBuffer(long bufferId) {
        deviceContext.getPlatformContext().releaseBuffer(bufferId);
    }

    long toConstantAddress() {
        return constantPointer;
    }

    long toAtomicAddress() {
        return atomicsRegion;
    }

    void allocateAtomicRegion() {
        if (this.atomicsRegion == -1) {
            this.atomicsRegion = deviceContext.getPlatformContext()
                    .createBuffer(OCLMemFlags.CL_MEM_READ_WRITE | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, INTEGER_BYTES_SIZE * MAX_NUMBER_OF_ATOMICS_PER_KERNEL).getBuffer();
        }
    }

    void deallocateAtomicRegion() {
        if (this.atomicsRegion != -1) {
            deviceContext.getPlatformContext().releaseBuffer(this.atomicsRegion);
            this.atomicsRegion = -1;
        }
    }
}
