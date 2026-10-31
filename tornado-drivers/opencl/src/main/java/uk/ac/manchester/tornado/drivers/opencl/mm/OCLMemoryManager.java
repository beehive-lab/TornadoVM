/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.drivers.opencl.mm.OCLKernelStackFrame.RESERVED_SLOTS;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.api.options.RuntimeFlags;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;

public class OCLMemoryManager implements TornadoMemoryProvider {

    private static final int MAX_NUMBER_OF_ATOMICS_PER_KERNEL = 32;
    private static final int INTEGER_BYTES_SIZE = 4;
    private final OCLDeviceContext deviceContext;
    private Map<Long, OCLKernelStackFrame> oclKernelStackFrame = new ConcurrentHashMap<>();
    private long constantMemoryPointer;
    private long NON_EXISTING_ADDRESS = -1;
    private long atomicsRegionPointer = -1;

    public OCLMemoryManager(final OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public long getHeapSize() {
        return RuntimeFlags.DEVICE_MEMORY.get();
    }

    public OCLKernelStackFrame createKernelStackFrame(long executionPlanId, final int numberOfArguments) {
        if (!oclKernelStackFrame.containsKey(executionPlanId)) {
            // Create one stack frame per execution plan ID 
            long kernelStackFramePtr = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_ONLY, RESERVED_SLOTS * Long.BYTES).getBuffer();
            oclKernelStackFrame.put(executionPlanId, new OCLKernelStackFrame(kernelStackFramePtr, numberOfArguments, deviceContext));
        }
        return oclKernelStackFrame.get(executionPlanId);
    }

    public void releaseKernelStackFrame(long executionPlanId) {
        OCLKernelStackFrame stackFrame = oclKernelStackFrame.remove(executionPlanId);
        if (stackFrame != null) {
            stackFrame.invalidate();
        }
    }

    public XPUBuffer createAtomicsBuffer(final int[] array, Access access) {
        return new OCLAtomicsBuffer(array, deviceContext, access);
    }

    /**
     * Allocate regions on the device.
     */
    public void allocateDeviceMemoryRegions() {
        this.constantMemoryPointer = createBuffer(4, OCLMemFlags.CL_MEM_READ_ONLY | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR).getBuffer();
        allocateAtomicRegion();
    }

    public OCLContext.OCLBufferResult createBuffer(long size, long flags) {
        return deviceContext.getPlatformContext().createBuffer(flags, size);
    }

    public void releaseBuffer(long bufferId) {
        deviceContext.getPlatformContext().releaseBuffer(bufferId);
    }

    long toConstantAddress() {
        return constantMemoryPointer;
    }

    long toAtomicAddress() {
        return atomicsRegionPointer;
    }

    void allocateAtomicRegion() {
        if (this.atomicsRegionPointer == NON_EXISTING_ADDRESS) {
            this.atomicsRegionPointer = deviceContext.getPlatformContext().createBuffer(OCLMemFlags.CL_MEM_READ_WRITE | OCLMemFlags.CL_MEM_ALLOC_HOST_PTR, atomicRegionSize()).getBuffer();
        }
    }

    public static long atomicRegionSize() {
        return INTEGER_BYTES_SIZE * MAX_NUMBER_OF_ATOMICS_PER_KERNEL;
    }
}
