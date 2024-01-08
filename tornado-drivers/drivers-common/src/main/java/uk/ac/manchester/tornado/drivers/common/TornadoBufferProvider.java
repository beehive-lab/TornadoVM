/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.common;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.ArrayList;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

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
    protected final ArrayList<BufferInfo> freeBuffers;
    protected final ArrayList<BufferInfo> usedBuffers;
    protected long currentMemoryAvailable;

    protected TornadoBufferProvider(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.usedBuffers = new ArrayList<>();
        this.freeBuffers = new ArrayList<>();

        // There is no way of querying the available memory on the device.
        // Instead, use a flag similar to -Xmx.
        currentMemoryAvailable = TornadoOptions.DEVICE_AVAILABLE_MEMORY;
    }



    protected abstract long allocateBuffer(long size);

    protected abstract void releaseBuffer(long buffer);

    private long allocate(long size) {
        long buffer = allocateBuffer(size);
        currentMemoryAvailable -= size;
        BufferInfo bufferInfo = new BufferInfo(buffer, size);
        usedBuffers.add(bufferInfo);
        return bufferInfo.buffer;
    }

    private void freeBuffers(long size) {
        // Attempts to free buffers of given size.
        long remainingSize = size;
        while (!freeBuffers.isEmpty() && remainingSize > 0) {
            BufferInfo bufferInfo = freeBuffers.remove(0);
            TornadoInternalError.guarantee(!usedBuffers.contains(bufferInfo), "This buffer should not be used");
            remainingSize -= bufferInfo.size;
            currentMemoryAvailable += bufferInfo.size;
            releaseBuffer(bufferInfo.buffer);
        }
    }

    private BufferInfo markBufferUsed(int freeBufferIndex) {
        BufferInfo buffer = freeBuffers.get(freeBufferIndex);
        usedBuffers.add(buffer);
        freeBuffers.remove(buffer);
        return buffer;
    }

    /**
     * First check if there is an available buffer of a given size. Perform a
     * sequential search through the freeBuffers to get the buffer with the smaller
     * size than can fulfill the allocation. The number of allocated buffers is
     * usually low, so searching sequentially should not take a lot of time.
     *
     * @param sizeInBytes
     *            Size in bytes for the requested buffer.
     * @return returns the index position of a free buffer within the free buffer
     *         list. It returns -1 if a free buffer slot is not found.
     */
    private int bufferIndexOfAFreeSpace(long sizeInBytes) {
        int minBufferIndex = -1;
        for (int i = 0; i < freeBuffers.size(); i++) {
            BufferInfo bufferInfo = freeBuffers.get(i);
            if (bufferInfo.size >= sizeInBytes && (minBufferIndex == -1 || bufferInfo.size < freeBuffers.get(minBufferIndex).size)) {
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
     *            Size in bytes for the requested buffer.
     * @return It returns a buffer native pointer.
     */
    private long freeUnusedNativeBufferAndAssignRegion(long sizeInBytes) {
        freeBuffers(sizeInBytes);
        if (sizeInBytes <= currentMemoryAvailable) {
            return allocate(sizeInBytes);
        } else {
            throw new TornadoOutOfMemoryException("Unable to allocate " + sizeInBytes + " bytes of memory.");
        }
    }

    /**
     * Method that finds a suitable buffer for a requested buffer size. If a free
     * memory buffer is found, it performs the native buffer allocation on the
     * target device. Otherwise, it throws an exception.
     *
     * @param sizeInBytes
     *            Size in bytes for the requested buffer.
     * @return Returns a pointer to the native buffer (JNI).
     *
     * @throws {@link
     *             TornadoOutOfMemoryException}
     */
    public long getBufferWithSize(long sizeInBytes) {
        TornadoTargetDevice targetDevice = deviceContext.getDevice();
        if (sizeInBytes <= currentMemoryAvailable && sizeInBytes < targetDevice.getDeviceMaxAllocationSize()) {
            // Allocate if there is enough device memory.
            return allocate(sizeInBytes);
        } else if (sizeInBytes < targetDevice.getDeviceMaxAllocationSize()) {
            int minBufferIndex = bufferIndexOfAFreeSpace(sizeInBytes);
            // If a buffer was found, mark it as used and return it.
            if (minBufferIndex != -1) {
                return markBufferUsed(minBufferIndex).buffer;
            } else {
                return freeUnusedNativeBufferAndAssignRegion(sizeInBytes);
            }
        } else {
            throw new TornadoOutOfMemoryException("Unable to allocate " + sizeInBytes + " bytes of memory.");
        }
    }

    /**
     * Removes the buffer from the {@link #usedBuffers} list and add it to
     * the @{@link #freeBuffers} list.
     */
    public void markBufferReleased(long buffer, long size) {
        int foundIndex = -1;
        for (int i = 0; i < usedBuffers.size(); i++) {
            if (usedBuffers.get(i).buffer == buffer) {
                foundIndex = i;
                break;
            }
        }
        TornadoInternalError.guarantee(foundIndex != -1, "Expected the buffer to be allocated and used at this point.");
        BufferInfo removedBuffer = usedBuffers.remove(foundIndex);
        freeBuffers.add(removedBuffer);
    }

    public boolean checkBufferAvailability(int numBuffersRequired) {
        return freeBuffers.size() >= numBuffersRequired;
    }

    public void resetBuffers() {
        freeBuffers(DEVICE_AVAILABLE_MEMORY);
    }

    public static class BufferInfo {
        public final long buffer;
        public final long size;

        public BufferInfo(long buffer, long size) {
            this.buffer = buffer;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BufferInfo)) {
                return false;
            }
            BufferInfo that = (BufferInfo) o;
            return buffer == that.buffer && size == that.size;
        }

        @Override
        public int hashCode() {
            return (int) buffer;
        }
    }
}
