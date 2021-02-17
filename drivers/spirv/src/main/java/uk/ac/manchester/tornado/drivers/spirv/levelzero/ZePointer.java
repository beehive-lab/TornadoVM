package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZePointer {
    private long valuePointer;

    public ZePointer() {
    }

    public ZePointer(long valuePointer) {
        this.valuePointer = valuePointer;
    }

    public void setValuePointer(long valuePointer) {
        this.valuePointer = valuePointer;
    }

    public long getValuePointer() {
        return this.valuePointer;
    }
}
