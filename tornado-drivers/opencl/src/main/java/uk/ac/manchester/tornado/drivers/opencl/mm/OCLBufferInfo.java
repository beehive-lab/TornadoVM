package uk.ac.manchester.tornado.drivers.opencl.mm;

import uk.ac.manchester.tornado.drivers.common.mm.BufferInfo;

public class OCLBufferInfo extends BufferInfo {

    private long bufferId;

    public OCLBufferInfo() {}

    public long getBufferId() {
        return bufferId;
    }

    public void setBufferId(long bufferId) {
        this.bufferId = bufferId;
    }
}
