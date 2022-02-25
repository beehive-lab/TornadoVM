package uk.ac.manchester.tornado.drivers.opencl.runtime;

import java.util.Comparator;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext.OCLBufferResult;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class OCLBufferProvider implements TornadoBufferProvider {

    private static class BufferInfo {
        private final long buffer;
        private final long size;

        public BufferInfo(long buffer, long size) {
            this.buffer = buffer;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BufferInfo)) return false;
            BufferInfo that = (BufferInfo) o;
            return buffer == that.buffer && size == that.size;
        }

        @Override
        public int hashCode() {
            return (int) buffer;
        }
    }

    private final OCLDeviceContext deviceContext;
    private final SortedSet<BufferInfo> freeBuffers;
    private final HashSet<BufferInfo> usedBuffers;
    private long availableMemory;

    public static final long DEVICE_AVAILABLE_MEMORY = RuntimeUtilities.parseSize(System.getProperty("tornado.device.memory", "1GB"));

    public OCLBufferProvider(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.usedBuffers = new HashSet<>();
        Comparator<BufferInfo> comparator = (o1, o2) -> {
            if (o1.size > o2.size) {
                return 1;
            } else if (o1.size < o2.size) {
                return -1;
            }
            if (o1.buffer != o2.buffer) {
                return -1;
            }
            return 0;
        };
        this.freeBuffers = new TreeSet<>(comparator);

        // There is no way of querying the available memory on the device. For the moment, I use a user defined value.
        // availableMemory = deviceContext.getDevice().getDeviceGlobalMemorySize();
        availableMemory = DEVICE_AVAILABLE_MEMORY;
    }

    private long allocate(long size) {
        OCLBufferResult buffer = deviceContext.getMemoryManager().createBuffer(size, OCLMemFlags.CL_MEM_READ_WRITE);
        availableMemory -= size;
        BufferInfo bufferInfo = new BufferInfo(buffer.getBuffer(), size);
        usedBuffers.add(bufferInfo);
        return bufferInfo.buffer;
    }

    private void freeBuffers(long size) {
        // Attempts to free buffers of given size.
        long remainingSize = size;
        while (!freeBuffers.isEmpty() && remainingSize > 0) {
            BufferInfo bufferInfo = freeBuffers.first();
            TornadoInternalError.guarantee(!usedBuffers.contains(bufferInfo), "This buffer should not be used");
            freeBuffers.remove(bufferInfo);
            remainingSize -= bufferInfo.size;
            availableMemory += bufferInfo.size;
            deviceContext.getMemoryManager().releaseBuffer(bufferInfo.buffer);
        }
    }

    @Override
    public boolean canAllocate(int allocationsRequired) {
        return freeBuffers.size() >= allocationsRequired;
    }

    @Override
    public void resetBuffers() {
        freeBuffers(Long.MAX_VALUE);
    }

    @Override
    public long getBuffer(long size) {
        OCLTargetDevice targetDevice = deviceContext.getDevice();
        if (size <= availableMemory && size < targetDevice.getDeviceMaxAllocationSize()) {
            // Allocate if there is enough device memory.
            return allocate(size);
        } else if (size < targetDevice.getDeviceMaxAllocationSize()) {
            // First check if there is an available buffer of given size.
            BufferInfo minBuffer = null;
            for (BufferInfo bufferInfo : freeBuffers) {
                if (bufferInfo.size >= size && (minBuffer == null || bufferInfo.size < minBuffer.size)) {
                    minBuffer = bufferInfo;
                }
            }
            if (minBuffer != null) {
                usedBuffers.add(minBuffer);
                freeBuffers.remove(minBuffer);
                return minBuffer.buffer;
            }

            // There is no available buffer. Start freeing unused buffers and allocate.
            freeBuffers(size);
            if (size <= availableMemory) {
                return allocate(size);
            } else {
                throw new TornadoOutOfMemoryException("Unable to allocate " + size + " bytes of memory.");
            }
        } else {
            // Throw OOM exception.
            throw new TornadoOutOfMemoryException("Unable to allocate " + size + " bytes of memory.");
        }
    }

    @Override
    public void markBufferReleased(long buffer, long size) {
        BufferInfo bufferInfo = new BufferInfo(buffer, size);
        boolean contained = usedBuffers.remove(bufferInfo);
        TornadoInternalError.guarantee(contained, "Expected the buffer to be allocated and used at this point.");
        freeBuffers.add(bufferInfo);
    }

}
