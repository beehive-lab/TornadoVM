package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Size, in bytes, for each primitive type in level-zero.
 */
public enum Sizeof {

    // @formatter:off
    
    // Primitive Types
    POINTER(8),
    BYTE(1),
    BOOLEAN(1),
    CHAR(1),
    SHORT(2),
    INT(4),
    FLOAT(4),
    DOUBLE(8),
    LONG(8),
    
    // Native types in Level Zero
    ze_kernel_timestamp_result_t(32);
    
    // @formatter:on

    private int numBytes;

    Sizeof(int numBytes) {
        this.numBytes = numBytes;
    };

    public int getNumBytes() {
        return numBytes;
    }
}
