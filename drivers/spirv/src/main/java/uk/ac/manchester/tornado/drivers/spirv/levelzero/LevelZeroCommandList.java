package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroCommandList {

    private LevelZeroContext context;
    private ZeCommandQueueListHandle zeCommandList;

    public LevelZeroCommandList(LevelZeroContext context, ZeCommandQueueListHandle zeCommandList) {
        this.context = context;
        this.zeCommandList = zeCommandList;
    }

    public ZeCommandQueueListHandle getCommandListHandler() {
        return this.zeCommandList;
    }

    public long getCommandListHandlerPtr() {
        return this.zeCommandList.getPtrZeCommandListHandle();
    }

    public LevelZeroContext getContext() {
        return this.context;
    }

    native int zeCommandListAppendLaunchKernel_native(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, Object signalEvent, int numWaitEvents, Object phWaitEvents);

    public int zeCommandListAppendLaunchKernel(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, Object signalEvent, int numWaitEvents, Object phWaitEvents) {
        return zeCommandListAppendLaunchKernel_native(commandListHandler, ptrZeKernelHandle, dispatch, signalEvent, numWaitEvents, phWaitEvents);
    }

    native int zeCommandListClose_native(long ptrZeCommandListHandle);

    public int zeCommandListClose(long ptrZeCommandListHandle) {
        return zeCommandListClose_native(ptrZeCommandListHandle);
    }

    private native int zeCommandListAppendMemoryCopy_native(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, char[] heapBuffer, int allocSize, ZeEventHandle hSignalEvents,
            int numWaitEvents, ZeEventHandle phWaitEvents);

    /**
     * Copies host, device, or shared memory.
     * 
     * Details:
     * 
     * <ul>
     * <li>The application must ensure the memory pointed to by dstptr and srcptr is
     * accessible by the device on which the command list was created.</li>
     * <li>The implementation must not access the memory pointed to by dstptr and
     * srcptr as they are free to be modified by either the Host or device up until
     * execution.</li>
     * <li>The application must ensure the events are accessible by the device on
     * which the command list was created.</li>
     * <li>The application must ensure the command list and events were created, and
     * the memory was allocated, on the same context.</li>
     * <li>The application must not call this function from simultaneous threads
     * with the same command list handle.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * This function is similar to: clEnqueueCopyBuffer, clEnqueueReadBuffer,
     * clEnqueueWriteBuffer, and clEnqueueSVMMemCpy.
     * 
     * @param commandListHandlerPtr
     *            [in] handle of command list
     * @param dstBuffer
     *            [in] {@link LevelZeroByteBuffer} to destination memory to copy to
     * @param srcBuffer
     *            [in] Java array to source memory to copy from
     * @param allocSize
     *            [in] size in bytes to copy
     * @param hSignalEvents
     *            [in][optional] {@link ZeEventHandle} handler of the event to
     *            signal on completion
     * @param numWaitEvents
     *            [in][optional] number of events to wait on before launching; must
     *            be 0 if `null == phWaitEvents`
     * @param phWaitEvents
     *            [in][optional][range(0, numWaitEvents)] handle of the events to
     *            wait on before launching
     * @return Status of the call:
     * 
     *         ZE_RESULT_SUCCESS
     *
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == hCommandList
     *
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: null == dstptr or null ==
     *         srcptr
     *
     *         ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     *
     *         ZE_RESULT_ERROR_INVALID_SIZE: (null == phWaitEvents) && (0 <
     *         numWaitEvents)
     * 
     */
    public int zeCommandListAppendMemoryCopy(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, char[] srcBuffer, int allocSize, ZeEventHandle hSignalEvents, int numWaitEvents,
            ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_native(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, hSignalEvents, numWaitEvents, phWaitEvents);
    }
}
