package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.util.stream.IntStream;

public class ZeContextHandle {

    private long[] contextPtrs;

    public ZeContextHandle(long[] contextPtr) {
        this.contextPtrs = contextPtr;
    }

    public long[] getContextPtr() {
        return contextPtrs;
    }

    @Override
    public String toString() {
        return String.format( "Context Pointer: 0x%02x\n", contextPtrs[0]);
    }

    public void initPtr() {
        IntStream.range(0, contextPtrs.length).forEach(i-> contextPtrs[i] = -1);
    }
}
