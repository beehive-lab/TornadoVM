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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.drivers.opencl.mm.OCLKernelStackFrame.RESERVED_SLOTS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class OCLMemoryManager implements TornadoMemoryProvider {

    private static final int MAX_NUMBER_OF_ATOMICS_PER_KERNEL = 32;
    private static final int INTEGER_BYTES_SIZE = 4;
    private final OCLDeviceContext deviceContext;
    private Map<Long, OCLKernelStackFrame> oclKernelStackFrame = new ConcurrentHashMap<>();
    private long constantMemoryPointer;
    private long NON_EXISTING_ADDRESS = -1;
    private long atomicsRegionPointer = -1;
    private final Map<Object, SubBufferInfo> subBufferMappings = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, XPUBuffer> objectBuffers = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Long, Integer> parentSubBufferRefs = new ConcurrentHashMap<>();
    private final Map<Long, Access> parentBufferAccess = new ConcurrentHashMap<>();
    private final Set<Long> pendingParentRelease = ConcurrentHashMap.newKeySet();

    public OCLMemoryManager(final OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public long getHeapSize() {
        return DEVICE_AVAILABLE_MEMORY;
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

    public long createSubBuffer(long parentBuffer, long offset, long size, Access access) {
        long flags = getOCLMemFlagForAccess(access);
        Access parentAccess = parentBufferAccess.get(parentBuffer);
        if (parentAccess != null) {
            long parentFlags = getOCLMemFlagForAccess(parentAccess);
            if ((flags & parentFlags) != flags) {
                flags = parentFlags;
            }
        }
        long subBuffer = deviceContext.getPlatformContext().createSubBuffer(parentBuffer, flags, offset, size);
        if (subBuffer != -1) {
            parentSubBufferRefs.merge(parentBuffer, 1, Integer::sum);
        }
        return subBuffer;
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

    void deallocateAtomicRegion() {
        if (this.atomicsRegionPointer != NON_EXISTING_ADDRESS) {
            deviceContext.getPlatformContext().releaseBuffer(this.atomicsRegionPointer);
            this.atomicsRegionPointer = NON_EXISTING_ADDRESS;
        }
    }

    public static long atomicRegionSize() {
        return INTEGER_BYTES_SIZE * MAX_NUMBER_OF_ATOMICS_PER_KERNEL;
    }

    public static final class SubBufferInfo {
        final long parentBuffer;
        final long offset;
        final long size;
        final Access parentAccess;

        public SubBufferInfo(long parentBuffer, long offset, long size, Access parentAccess) {
            this.parentBuffer = parentBuffer;
            this.offset = offset;
            this.size = size;
            this.parentAccess = parentAccess;
        }
    }

    public SubBufferInfo getSubBufferInfo(Object key) {
        synchronized (subBufferMappings) {
            return subBufferMappings.get(key);
        }
    }

    public void registerSubBuffer(Object key, long parentBuffer, long offset, long size, Access access) {
        synchronized (subBufferMappings) {
            SubBufferInfo existing = subBufferMappings.get(key);
            if (existing == null || existing.parentBuffer != parentBuffer || existing.size != size || existing.offset < offset) {
                subBufferMappings.put(key, new SubBufferInfo(parentBuffer, offset, size, access));
            }
        }
        parentBufferAccess.putIfAbsent(parentBuffer, access);
        synchronized (objectBuffers) {
            XPUBuffer existingBuffer = objectBuffers.get(key);
            if (existingBuffer instanceof OCLArrayWrapper<?> arrayWrapper) {
                arrayWrapper.remapToSubBuffer(getSubBufferInfo(key), key);
            } else if (existingBuffer instanceof OCLMemorySegmentWrapper memorySegmentWrapper) {
                memorySegmentWrapper.remapToSubBuffer(getSubBufferInfo(key), key);
            }
        }
    }

    public boolean delayParentBufferRelease(long parentBuffer, Access access) {
        if (parentSubBufferRefs.getOrDefault(parentBuffer, 0) > 0) {
            pendingParentRelease.add(parentBuffer);
            parentBufferAccess.putIfAbsent(parentBuffer, access);
            return true;
        }
        return false;
    }

    public void releaseSubBuffer(long subBuffer, long parentBuffer) {
        if (subBuffer != parentBuffer) {
            releaseBuffer(subBuffer);
        }
        int remaining = parentSubBufferRefs.get(parentBuffer) - 1;
        if (remaining <= 0) {
            parentSubBufferRefs.remove(parentBuffer);
            if (pendingParentRelease.remove(parentBuffer)) {
                deviceContext.getBufferProvider().markBufferReleased(parentBuffer, parentBufferAccess.get(parentBuffer));
            }
        } else {
            parentSubBufferRefs.put(parentBuffer, remaining);
        }
    }

    private static long getOCLMemFlagForAccess(Access access) {
        return switch (access) {
            case READ_ONLY -> OCLMemFlags.CL_MEM_READ_ONLY;
            case WRITE_ONLY -> OCLMemFlags.CL_MEM_WRITE_ONLY;
            case READ_WRITE -> OCLMemFlags.CL_MEM_READ_WRITE;
            default -> OCLMemFlags.CL_MEM_READ_WRITE;
        };
    }

    public void registerObjectBuffer(Object key, XPUBuffer buffer) {
        synchronized (objectBuffers) {
            objectBuffers.put(key, buffer);
        }
    }

    public XPUBuffer getObjectBuffer(Object key) {
        synchronized (objectBuffers) {
            return objectBuffers.get(key);
        }
    }
}
