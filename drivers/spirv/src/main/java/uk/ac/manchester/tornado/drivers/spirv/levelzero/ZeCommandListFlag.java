package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import static uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeConstants.ZE_BIT;

public class ZeCommandListFlag {

    /**
     * The driver may reorder commands (e.g., kernels, copies) between barriers and
     * synchronization primitives. using this flag may increase Host overhead of
     * {@link LevelZeroCommandList#zeCommandListClose}. therefore, this flag should
     * **not** be set for low-latency usage-models.
     */
    public final static int ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING = ZE_BIT(0);

    /**
     * driver may perform additional optimizations that increase execution
     * throughput. Using this flag may increase Host overhead of
     * {@link LevelZeroCommandList#zeCommandListClose} and
     * {@link LevelZeroCommandQueue#zeCommandQueueExecuteCommandLists}
     * zeCommandQueueExecuteCommandLists. therefore, this flag should **not** be set
     * for low-latency usage-models.
     */
    public final static int ZE_COMMAND_LIST_FLAG_MAXIMIZE_THROUGHPUT = ZE_BIT(1);

    /**
     * < command list should be optimized for submission to a single command queue
     * and device engine. driver **must** disable any implicit optimizations for
     * distributing work across multiple engines. this flag should be used when
     * applications want full control over multi-engine submission and scheduling.
     */
    public final static int ZE_COMMAND_LIST_FLAG_EXPLICIT_ONLY = ZE_BIT(2);

    public final static int ZE_COMMAND_LIST_FLAG_FORCE_UINT32 = 0x7fffffff;
}
