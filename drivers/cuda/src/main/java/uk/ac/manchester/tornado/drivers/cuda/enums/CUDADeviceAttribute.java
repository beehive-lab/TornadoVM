package uk.ac.manchester.tornado.drivers.cuda.enums;

public enum CUDADeviceAttribute {
    MAX_THREADS_PER_BLOCK(1),
    MAX_BLOCK_DIM_X(2),
    MAX_BLOCK_DIM_Y(3),
    MAX_BLOCK_DIM_Z(4),
    MAX_GRID_DIM_X(5),
    MAX_GRID_DIM_Y(6),
    MAX_GRID_DIM_Z(7),
    MAX_SHARED_MEMORY_PER_BLOCK(8),
    TOTAL_CONSTANT_MEMORY(9),
    WARP_SIZE(10),
    MAX_REGISTERS_PER_BLOCK(12),
    CLOCK_RATE(13),
    MULTIPROCESSOR_COUNT(16),
    COMPUTE_CAPABILITY_MAJOR(75),
    COMPUTE_CAPABILITY_MINOR(76)
    ;

    private final int value;

    CUDADeviceAttribute(int value) {this.value = value;}

    public int value() {return value;}
}
