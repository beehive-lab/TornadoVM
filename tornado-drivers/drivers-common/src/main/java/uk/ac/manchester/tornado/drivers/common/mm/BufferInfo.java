package uk.ac.manchester.tornado.drivers.common.mm;

public abstract class BufferInfo {

    private long hostBufferPointer;
    private long devicePointer;
    private long bufferSize;

    public BufferInfo() {
    }

    public long getHostBufferPointer() {
        return hostBufferPointer;
    }

    public void setHostBufferPointer(long hostBufferPointer) {
        this.hostBufferPointer = hostBufferPointer;
    }

    public long getDevicePointer() {
        return devicePointer;
    }

    public void setDevicePointer(long devicePointer) {
        this.devicePointer = devicePointer;
    }

    public long getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(long bufferSize) {
        this.bufferSize = bufferSize;
    }

}
