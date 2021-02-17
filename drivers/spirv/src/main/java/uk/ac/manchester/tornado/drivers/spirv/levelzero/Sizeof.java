package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public enum Sizeof {

    POINTER(8),
    BYTE(1),
    BOOLEAN(1),
    CHAR(1),
    SHORT(2),
    INT(4),
    FLOAT(4),
    DOUBLE(8),
    LONG(8);

    private int numBytes;

    Sizeof(int numBytes) {
        this.numBytes = numBytes;
    };

    public int getNumBytes() {
        return numBytes;
    }
}
