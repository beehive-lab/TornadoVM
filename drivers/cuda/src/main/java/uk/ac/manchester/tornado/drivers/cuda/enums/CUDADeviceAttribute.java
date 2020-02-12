package uk.ac.manchester.tornado.drivers.cuda.enums;

public enum CUDADeviceAttribute {
    MAX_BLOCK_DIM_X(2),
    MAX_BLOCK_DIM_Y(3),
    MAX_BLOCK_DIM_Z(4),
    MAX_SHARED_MEMORY_PER_BLOCK(8),
    TOTAL_CONSTANT_MEMORY(9),
    CLOCK_RATE(13),
    MULTIPROCESSOR_COUNT(16);

    private final int value;

    CUDADeviceAttribute(int value) {this.value = value;}

    public int value() {return value;}
}
