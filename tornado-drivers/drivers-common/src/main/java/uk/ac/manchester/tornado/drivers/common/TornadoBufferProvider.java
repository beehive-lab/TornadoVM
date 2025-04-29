/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, 2024, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.common;

import static uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray.ARRAY_HEADER;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

import java.util.ArrayList;
import java.util.HashMap;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * This class implements a cache of allocated buffers on the device and also
 * handles the logic to allocate and free buffers. This class is extended for
 * each backend. The logic is as follows: it maintains a list of used buffers
 * and another list of free buffers. When performing an allocation, it first
 * checks if memory is available on the device. If it is not, then it will try
 * to reuse a buffer from the free list of buffers.
 */
public abstract class TornadoBufferProvider {

    protected final TornadoDeviceContext deviceContext;
    protected final HashMap<Access, ArrayList<BufferContainer>> freeBuffers;
    protected final HashMap<Access, ArrayList<BufferContainer>> usedBuffers;
    protected long currentMemoryAvailable;
    private TornadoLogger logger = new TornadoLogger(this.getClass());

    private static final String RESET = "\u001B[0m";
    public static final String YELLOW = "\u001B[33m";
    private static final String OUT_OF_MEMORY_MESSAGE = YELLOW + "\n\tTo increase the maximum device memory, use -Dtornado.device.memory=<X>GB\n" + RESET;

    protected TornadoBufferProvider(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.usedBuffers = initializeBufferHashMap();
        this.freeBuffers = initializeBufferHashMap();
        currentMemoryAvailable = TornadoOptions.DEVICE_AVAILABLE_MEMORY;
    }

    private HashMap<Access, ArrayList<BufferContainer>> initializeBufferHashMap() {
        HashMap<Access, ArrayList<BufferContainer>> bufferAccesses = new HashMap<>();
        for (Access access : Access.values()) {
            ArrayList<BufferContainer> bufferList = new ArrayList<>();
            bufferAccesses.put(access, bufferList);
        }
        return bufferAccesses;
    }

    /**
     * This function is invoked before the allocation with batch processing takes place.
     * It iterates over the list of used buffers, and if a buffer with the same
     * access type and size has already been allocated, it returns true to signify that
     * this buffer can be reused for this batch. Otherwise, it returns false.
     *
     * @param batchSize The size of the current batch
     * @param access The access type of the object (READ-ONLY, WRITE-ONLY, READ-WRITE)
     * @param numberOfBuffersForAccessType The number of buffers that are required to be allocated for this access type
     *
     * @return True if a buffer to reuse is available, or false otherwise.
     */
    public boolean reuseBufferForBatchProcessing(long batchSize, Access access, int numberOfBuffersForAccessType) {
        boolean matchFound = false;
        if (!usedBuffers.get(access).isEmpty()) {
            for (BufferContainer bufferContainer : usedBuffers.get(access)) {
                if (usedBuffers.get(access).size() < numberOfBuffersForAccessType) {
                    return false;
                }
                long size = bufferContainer.size;
                matchFound = (size == batchSize + ARRAY_HEADER);
                if (matchFound) {
                    logger.debug("Reuse buffer %s from the used-list for batch processing. Batch Size = %s, Access = %s %n", bufferContainer, batchSize, access);
                }
            }
        }
        return matchFound;
    }

    protected abstract long allocateBuffer(long size, Access access);

    protected abstract void releaseBuffer(long buffer);

    private synchronized long allocate(long size, Access access) {
        long buffer = allocateBuffer(size, access);
        currentMemoryAvailable -= size;
        BufferContainer bufferInfo = new BufferContainer(buffer, size, access);
        usedBuffers.get(access).add(bufferInfo);
        logger.debug("Buffer %s has been allocated and included in the usedBuffers list with access: %s", bufferInfo, access);
        return bufferInfo.buffer;
    }

    private synchronized void freeBuffers(long size, Access access) {
        // Attempts to free buffers of given size.
        long remainingSize = size;
        while (!freeBuffers.get(access).isEmpty() && remainingSize > 0) {
            BufferContainer bufferInfo = freeBuffers.get(access).removeFirst();
            TornadoInternalError.guarantee(!usedBuffers.get(access).contains(bufferInfo), "This buffer should not be used");
            remainingSize -= bufferInfo.size;
            currentMemoryAvailable += bufferInfo.size;
            releaseBuffer(bufferInfo.buffer);
        }
    }

    public synchronized long deallocate(Access access) {
        // Attempts to free buffers of given size.
        long spaceDeallocated = 0;
        while (!freeBuffers.get(access).isEmpty()) {
            BufferContainer bufferInfo = freeBuffers.get(access).removeFirst();
            TornadoInternalError.guarantee(!usedBuffers.get(access).contains(bufferInfo), "This buffer should not be used");
            currentMemoryAvailable += bufferInfo.size;
            spaceDeallocated += bufferInfo.size;
            releaseBuffer(bufferInfo.buffer);
        }
        return spaceDeallocated;
    }

    private synchronized BufferContainer markBufferUsed(int freeBufferIndex, Access access) {
        BufferContainer buffer = freeBuffers.get(access).get(freeBufferIndex);
        usedBuffers.get(access).add(buffer);
        freeBuffers.get(access).remove(buffer);
        return buffer;
    }

    /**
     * First check if there is an available buffer of a given size. Perform a
     * sequential search through the freeBuffers to get the buffer with the smaller
     * size than can fulfill the allocation. The number of allocated buffers is
     * usually low, so searching sequentially should not take a lot of time.
     *
     * @param sizeInBytes
     *     Size in bytes for the requested buffer.
     * @return returns the index position of a free buffer within the free buffer
     *     list. It returns -1 if a free buffer slot is not found.
     */
    private synchronized int bufferIndexOfAFreeSpace(long sizeInBytes, Access access) {
        int minBufferIndex = -1;
        for (int i = 0; i < freeBuffers.get(access).size(); i++) {
            BufferContainer bufferInfo = freeBuffers.get(access).get(i);
            if (bufferInfo.size >= sizeInBytes && (minBufferIndex == -1 || bufferInfo.size < freeBuffers.get(access).get(minBufferIndex).size)) {
                minBufferIndex = i;
            }
        }
        return minBufferIndex;
    }

    /**
     * There is no buffer to fulfill the size. Start freeing unused buffers and try
     * to allocate.
     *
     * @param sizeInBytes
     *     Size in bytes for the requested buffer.
     * @return It returns a buffer native pointer.
     */
    private synchronized long freeUnusedNativeBufferAndAssignRegion(long sizeInBytes, Access access) {
        freeBuffers(sizeInBytes, access);
        if (sizeInBytes <= currentMemoryAvailable) {
            return allocate(sizeInBytes, access);
        } else {
            throw new TornadoOutOfMemoryException("Unable to allocate " + sizeInBytes + " bytes of memory." + OUT_OF_MEMORY_MESSAGE);
        }
    }

    /**
     * Method that finds a suitable buffer for a requested buffer size. If a free
     * memory buffer is found, it performs the native buffer allocation on the
     * target device. Otherwise, it throws an exception.
     *
     * @param sizeInBytes
     *     Size in bytes for the requested buffer.
     * @return Returns a pointer to the native buffer (JNI).
     *
     * @throws {@link
     *     TornadoOutOfMemoryException}
     */
    public synchronized long getOrAllocateBufferWithSize(long sizeInBytes, Access access) {
        TornadoTargetDevice device = deviceContext.getDevice();
        if (sizeInBytes <= currentMemoryAvailable && sizeInBytes < device.getDeviceMaxAllocationSize()) {
            // Allocate if there is enough device memory.
            return allocate(sizeInBytes, access);
        } else if (sizeInBytes < device.getDeviceMaxAllocationSize()) {
            int minBufferIndex = bufferIndexOfAFreeSpace(sizeInBytes, access);
            // If a buffer was found, mark it as used and return it.
            if (minBufferIndex != -1) {
                return markBufferUsed(minBufferIndex, access).buffer;
            } else {
                return freeUnusedNativeBufferAndAssignRegion(sizeInBytes, access);
            }
        } else {
            throw new TornadoOutOfMemoryException("[ERROR] Unable to allocate " + sizeInBytes + " bytes of memory." + OUT_OF_MEMORY_MESSAGE);
        }
    }

    /**
     * Removes the buffer from the {@link #usedBuffers} list and add it to
     * the @{@link #freeBuffers} list.
     */
    public synchronized void markBufferReleased(long buffer, Access access) {
        int foundIndex = -1;
        for (int i = 0; i < usedBuffers.get(access).size(); i++) {
            // find the buffer slot to mark it as free
            if (usedBuffers.get(access).get(i) != null && usedBuffers.get(access).get(i).buffer == buffer) {
                foundIndex = i;
                break;
            }
        }

        if (foundIndex != -1) {
            // if found, we mark it as free by inserting it into the free list
            BufferContainer removedBuffer = usedBuffers.get(access).remove(foundIndex);
            freeBuffers.get(access).add(removedBuffer);
            logger.debug("Buffer %s has been released and included in the freeBuffers list for access: %s", removedBuffer, access);
        }
    }

    /**
     * Function that returns true if the there are, at least numBuffers available in the free list.
     *
     * @param numBuffers
     *     Number of free buffers.
     * @return boolean.
     */
    public boolean isNumFreeBuffersAvailable(int numBuffers, Access access) {
        return freeBuffers.get(access).size() >= numBuffers;
    }

    public synchronized void resetBuffers(Access access) {
        freeBuffers(DEVICE_AVAILABLE_MEMORY, access);
    }

    private record BufferContainer(long buffer, long size, Access access) {

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof BufferContainer that)) {
                return false;
            }
            return buffer == that.buffer && size == that.size;
        }

        @Override
        public int hashCode() {
            return (int) buffer;
        }
    }
}
