package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroCommandQueue {

    private LevelZeroContext context;
    private ZeCommandQueueHandle zeCommandQueueHandle;

    public LevelZeroCommandQueue(LevelZeroContext context, ZeCommandQueueHandle zeCommandQueueHandle) {
        this.context = context;
        this.zeCommandQueueHandle = zeCommandQueueHandle;
    }

    public LevelZeroContext getContext() {
        return this.context;
    }

    public ZeCommandQueueHandle getCommandQueueHandle() {
        return this.zeCommandQueueHandle;
    }

    native int zeCommandQueueExecuteCommandLists_native(long commandQueueHandlerPointer, int numCommandLists, ZeCommandListHandle commandListHandler, Object hFence);

    /**
     * Executes a command list in a command queue.
     * 
     * <ul>
     * <li>The command lists are submitted to the device in the order they are
     * received, whether from multiple calls (on the same or different threads) or a
     * single call with multiple command lists.</li>
     * <li>The application must ensure the command lists are accessible by the
     * device on which the command queue was created.</li>
     * <li>The application must ensure the command lists are not currently
     * referencing the command list since the implementation is allowed to modify
     * the contents of the command list for submission.</li>
     * <li>The application must only execute command lists created with an identical
     * command queue group ordinal to the command queue.</li>
     * <li>The application must use a fence created using the same command
     * queue.</li>
     * <li>The application must ensure the command queue, command list and fence
     * were created on the same context.</li>
     * <li>The application may call this function from simultaneous threads.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * @param commandQueueHandlerPointer
     *            [in] Pointer handle of the command queue
     * @param numCommandLists
     *            [in] number of command lists to execute
     * @param commandListHandler
     *            [in][range(0, numCommandLists)] list of handles of the command
     *            lists to execute ({@link ZeCommandListHandle}).
     * @param hFence
     *            [in][optional] handle of the fence to signal on completion (Not
     *            YET IMPLEMENTED)
     * @return
     * 
     *         ZE_RESULT_SUCCESS
     *
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: nullptr == hCommandQueue
     *
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: nullptr == phCommandLists
     *
     *         ZE_RESULT_ERROR_INVALID_SIZE: 0 == numCommandLists
     *
     *         ZE_RESULT_ERROR_INVALID_COMMAND_LIST_TYPE
     *
     *         ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     */
    public int zeCommandQueueExecuteCommandLists(long commandQueueHandlerPointer, int numCommandLists, ZeCommandListHandle commandListHandler, Object hFence) {
        return zeCommandQueueExecuteCommandLists_native(commandQueueHandlerPointer, numCommandLists, commandListHandler, hFence);
    }

    native int zeCommandQueueSynchronize_native(long commandQueueHandlerPointer, long timeOut);

    /**
     * Synchronizes a command queue by waiting on the host.
     * 
     * <ul>
     * <li>The application may call this function from simultaneous threads.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * @param commandQueueHandlerPointer
     *            [in] handle pointer of the command queue
     * @param timeOut
     *            [in] if non-zero, then indicates the maximum time (in nanoseconds)
     *            to yield before returning ZE_RESULT_SUCCESS or
     *            ZE_RESULT_NOT_READY; if zero, then immediately returns the status
     *            of the command queue; if UINT64_MAX, then function will not return
     *            until complete or device is lost. Due to external dependencies,
     *            timeout may be rounded to the closest value allowed by the
     *            accuracy of those dependencies.
     * 
     * @return
     * 
     *         ZE_RESULT_SUCCESS
     *
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == hCommandQueue
     *
     *         ZE_RESULT_NOT_READY: timeout expired
     */
    public int zeCommandQueueSynchronize(long commandQueueHandlerPointer, long timeOut) {
        return zeCommandQueueSynchronize_native(commandQueueHandlerPointer, timeOut);
    }

    public long getCommandQueueHandlerPtr() {
        return this.zeCommandQueueHandle.getCommandQueueHandlerPointer();
    }
}
