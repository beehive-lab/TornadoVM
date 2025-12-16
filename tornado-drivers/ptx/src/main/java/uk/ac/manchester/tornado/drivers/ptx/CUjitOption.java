package uk.ac.manchester.tornado.drivers.ptx;

public enum CUjitOption {
    CU_JIT_MAX_REGISTERS(0),
    CU_JIT_OPTIMIZATION_LEVEL(7),
    CU_JIT_TARGET(9),
    CU_JIT_GENERATE_DEBUG_INFO(11),
    CU_JIT_LOG_VERBOSE(12),
    CU_JIT_GENERATE_LINE_INFO(13),
    CU_JIT_CACHE_MODE(14);

    private final int value;

    CUjitOption(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
