/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.mm;

import static uk.ac.manchester.tornado.drivers.metal.mm.MetalKernelStackFrame.RESERVED_SLOTS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.metal.MetalContext;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalMemFlags;

public class MetalMemoryManager implements TornadoMemoryProvider {

    private static final int MAX_NUMBER_OF_ATOMICS_PER_KERNEL = 32;
    private static final int INTEGER_BYTES_SIZE = 4;
    private final MetalDeviceContext deviceContext;
    private Map<Long, MetalKernelStackFrame> metalKernelStackFrame = new ConcurrentHashMap<>();
    private long constantMemoryPointer;
    private long NON_EXISTING_ADDRESS = -1;
    private long atomicsRegionPointer = -1;

    public MetalMemoryManager(final MetalDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public long getHeapSize() {
        return DEVICE_AVAILABLE_MEMORY;
    }

    public MetalKernelStackFrame createKernelStackFrame(long executionPlanId, final int numberOfArguments) {
        if (!metalKernelStackFrame.containsKey(executionPlanId)) {
            // Create one stack frame per execution plan ID 
            long kernelStackFramePtr = deviceContext.getPlatformContext().createBuffer(MetalMemFlags.METAL_MEM_READ_ONLY, RESERVED_SLOTS * Long.BYTES).getBuffer();
            metalKernelStackFrame.put(executionPlanId, new MetalKernelStackFrame(kernelStackFramePtr, numberOfArguments, deviceContext));
        }
        return metalKernelStackFrame.get(executionPlanId);
    }

    public void releaseKernelStackFrame(long executionPlanId) {
        MetalKernelStackFrame stackFrame = metalKernelStackFrame.remove(executionPlanId);
        if (stackFrame != null) {
            stackFrame.invalidate();
        }
    }

    public XPUBuffer createAtomicsBuffer(final int[] array, Access access) {
        return new MetalAtomicsBuffer(array, deviceContext, access);
    }

    /**
     * Allocate regions on the device.
     */
    public void allocateDeviceMemoryRegions() {
        this.constantMemoryPointer = createBuffer(4, MetalMemFlags.METAL_MEM_READ_ONLY | MetalMemFlags.METAL_MEM_ALLOC_HOST_PTR).getBuffer();
        allocateAtomicRegion();
    }

    public MetalContext.MetalBufferResult createBuffer(long size, long flags) {
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
            this.atomicsRegionPointer = deviceContext.getPlatformContext().createBuffer(MetalMemFlags.METAL_MEM_READ_WRITE | MetalMemFlags.METAL_MEM_ALLOC_HOST_PTR, atomicRegionSize()).getBuffer();
        }
    }

    void deallocateAtomicRegion() {
        if (this.atomicsRegionPointer != NON_EXISTING_ADDRESS) {
            deviceContext.getPlatformContext().releaseBuffer(this.atomicsRegionPointer);
            this.atomicsRegionPointer = NON_EXISTING_ADDRESS;
        }
    }

    public static long atomicRegionSize() {
        return INTEGER_BYTES_SIZE * MAX_NUMBER_OF_ATOMICS_PER_KERNEL;
    }
}
