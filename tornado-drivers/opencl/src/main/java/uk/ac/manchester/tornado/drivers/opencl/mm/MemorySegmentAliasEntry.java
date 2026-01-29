package uk.ac.manchester.tornado.drivers.opencl.mm;

import java.util.ArrayList;

public class MemorySegmentAliasEntry {
    private long offset;
    private final long size;
    private final ArrayList<OCLMemorySegmentWrapper> buffers;

    public MemorySegmentAliasEntry(OCLMemorySegmentWrapper buffer) {
        this.offset = buffer.getBufferOffset();
        this.size = buffer.size();
        this.buffers = new ArrayList<>();
        this.buffers.add(buffer);
    }

    public void updateOffset(long newOffset) {
        offset = newOffset;
        for (OCLMemorySegmentWrapper buffer : buffers) {
            buffer.updateBufferOffset(newOffset);
        }
    }

    public void addBuffer(OCLMemorySegmentWrapper buffer) {
        buffers.add(buffer);
        buffer.updateBufferOffset(offset);
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }
}
