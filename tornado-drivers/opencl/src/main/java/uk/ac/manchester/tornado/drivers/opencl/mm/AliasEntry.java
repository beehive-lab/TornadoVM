package uk.ac.manchester.tornado.drivers.opencl.mm;

import java.util.ArrayList;

public class AliasEntry {
    private long offset;
    private final long size;
    private final ArrayList<OCLArrayWrapper<?>> buffers;

    public AliasEntry(OCLArrayWrapper<?> buffer) {
        this.offset = buffer.getBufferOffset();
        this.size = buffer.size();
        this.buffers = new ArrayList<>();
        this.buffers.add(buffer);
    }

    public void updateOffset(long newOffset) {
        offset = newOffset;
        for (OCLArrayWrapper<?> buffer : buffers) {
            buffer.updateBufferOffset(newOffset);
            
        }
    }

    public void addBuffer(OCLArrayWrapper<?> buffer) {
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
