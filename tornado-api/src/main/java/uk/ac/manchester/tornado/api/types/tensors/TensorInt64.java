package uk.ac.manchester.tornado.api.types.tensors;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.MemorySegment;

public final class TensorInt64 extends TornadoNativeArray implements AbstractTensor {
    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public MemorySegment getSegment() {
        return null;
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return null;
    }

    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return 0;
    }

    @Override
    public long getNumBytesOfSegment() {
        return 0;
    }

    @Override
    protected void clear() {

    }

    @Override
    public int getElementSize() {
        return 0;
    }

    @Override
    public Shape getShape() {
        return null;
    }

    @Override
    public String getDTypeAsString() {
        return null;
    }

    @Override
    public DType getDType() {
        return null;
    }
}
