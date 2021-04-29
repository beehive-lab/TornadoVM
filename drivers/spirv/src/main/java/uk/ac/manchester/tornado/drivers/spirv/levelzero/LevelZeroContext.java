package uk.ac.manchester.tornado.drivers.spirv.levelzero;

// A context has a driver-handler associated with it
public class LevelZeroContext {

    ZeDriverHandle driver;
    ZeContextHandle contextHandle;
    ZeContextDesc contextDescription;

    native int zeContextCreate(long driverHandler, ZeContextDesc contextDescriptionPtr, long[] contextPtr);

    public int zeContextCreate(long driverHandler, int indexDriverHandler) {
        long[] contextPointers = new long[1];
        int status = zeContextCreate(driverHandler, contextDescription, contextPointers);
        this.contextHandle = new ZeContextHandle(contextPointers);
        return status;
    }

    public LevelZeroContext(ZeDriverHandle driver, ZeContextDesc contextDescription) {
        this.driver = driver;
        this.contextDescription = contextDescription;
    }

    public ZeDriverHandle getDriver() {
        return driver;
    }

    public ZeContextHandle getContextHandle() {
        return contextHandle;
    }

    native int zeCommandQueueCreate_native(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandQueueHandle commandQueue);

    public int zeCommandQueueCreate(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandQueueHandle commandQueue) {
        return zeCommandQueueCreate_native(contextPtr, deviceHandlerPtr, commandQueueDescription, commandQueue);
    }

    native int zeCommandListCreate_native(long contextPtr, long deviceHandlerPtr, ZeCommandListDescription commandListDescription, ZeCommandListHandle commandList);

    public int zeCommandListCreate(long contextPtr, long deviceHandlerPtr, ZeCommandListDescription commandListDescription, ZeCommandListHandle commandList) {
        return zeCommandListCreate_native(contextPtr, deviceHandlerPtr, commandListDescription, commandList);
    }

    public native int zeCommandListCreateImmediate_native(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandListHandle commandList);

    public int zeCommandListCreateImmediate(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandListHandle commandList) {
        return zeCommandListCreateImmediate_native(contextPtr, deviceHandlerPtr, commandQueueDescription, commandList);
    }

    native int zeMemAllocShared_native(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, ZeHostMemAllocDesc hostMemAllocDesc, int bufferSize, int alignment, long deviceHandlerPtr,
            LevelZeroBufferInteger buffer);

    native int zeMemAllocShared_nativeByte(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, ZeHostMemAllocDesc hostMemAllocDesc, int bufferSize, int alignment, long deviceHandlerPtr,
            LevelZeroByteBuffer buffer);

    public int zeMemAllocShared(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, ZeHostMemAllocDesc hostMemAllocDesc, int bufferSize, int alignment, long deviceHandlerPtr,
            LevelZeroBufferInteger buffer) {
        return zeMemAllocShared_native(contextPtr, deviceMemAllocDesc, hostMemAllocDesc, bufferSize, alignment, deviceHandlerPtr, buffer);
    }

    public int zeMemAllocShared(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, ZeHostMemAllocDesc hostMemAllocDesc, int bufferSize, int alignment, long deviceHandlerPtr,
            LevelZeroByteBuffer buffer) {
        return zeMemAllocShared_nativeByte(contextPtr, deviceMemAllocDesc, hostMemAllocDesc, bufferSize, alignment, deviceHandlerPtr, buffer);
    }

    native int zeMemAllocDevice_native(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, int allocSize, int alignment, long deviceHandlerPtr, LevelZeroByteBuffer deviceBuffer);

    public int zeMemAllocDevice(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, int allocSize, int alignment, long deviceHandlerPtr, LevelZeroByteBuffer deviceBuffer) {
        return zeMemAllocDevice_native(contextPtr, deviceMemAllocDesc, allocSize, alignment, deviceHandlerPtr, deviceBuffer);
    }

    private native int zeMemAllocDevice_nativeLong(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, int allocSize, int alignment, long deviceHandlerPtr, LevelZeroBufferLong deviceBufferLong);

    public int zeMemAllocDevice(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, int allocSize, int alignment, long deviceHandlerPtr, LevelZeroBufferLong deviceBufferLong) {
        return zeMemAllocDevice_nativeLong(contextPtr, deviceMemAllocDesc, allocSize, alignment, deviceHandlerPtr, deviceBufferLong);
    }

    native int zeModuleCreate_nativeWithPath(long contextPtr, long deviceHandlerPtr, ZeModuleDesc moduleDesc, ZeModuleHandle module, ZeBuildLogHandle buildLog, String pathToBinary);

    public int zeModuleCreate(long contextPtr, long deviceHandlerPtr, ZeModuleDesc moduleDesc, ZeModuleHandle module, ZeBuildLogHandle buildLog, String pathToBinary) {
        return zeModuleCreate_nativeWithPath(contextPtr, deviceHandlerPtr, moduleDesc, module, buildLog, pathToBinary);
    }

    public long getDefaultContextPtr() {
        return getContextHandle().getContextPtr()[0];
    }

    native int zeMemFree_native(long contextHandlePtr, long bufferA);

    /**
     * Frees allocated host memory, device memory, or shared memory on the context.
     *
     * <ul>
     * <li>The application must ensure the device is not currently referencing the
     * memory before it is freed</li>
     * <li>The implementation of this function may immediately free all Host and
     * Device allocations associated with this memory</li>
     * <li>The application must not call this function from simultaneous threads
     * with the same pointer.</li>
     * <li>The implementation of this function must be thread-safe.</li>
     * </ul>
     *
     * @param contextHandlePtr
     *            [in] handle pointer of the context object
     * @param buffer
     *            [in][release] pointer to memory to free
     * @return ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == contextHandlePtr
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: null == buffer
     */
    public int zeMemFree(long contextHandlePtr, LevelZeroBufferInteger buffer) {
        int result = zeMemFree_native(contextHandlePtr, buffer.getPtrBuffer());
        buffer.initPtr();
        return result;
    }

    /**
     * Frees allocated host memory, device memory, or shared memory on the context.
     *
     * <ul>
     * <li>The application must ensure the device is not currently referencing the
     * memory before it is freed</li>
     * <li>The implementation of this function may immediately free all Host and
     * Device allocations associated with this memory</li>
     * <li>The application must not call this function from simultaneous threads
     * with the same pointer.</li>
     * <li>The implementation of this function must be thread-safe.</li>
     * </ul>
     *
     * @param contextHandlePtr
     *            [in] handle pointer of the context object
     * @param buffer
     *            [in][release] pointer to memory to free
     * @return ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == contextHandlePtr
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: null == buffer
     */
    public int zeMemFree(long contextHandlePtr, LevelZeroByteBuffer buffer) {
        int result = zeMemFree_native(contextHandlePtr, buffer.getPtrBuffer());
        buffer.initPtr();
        return result;
    }

    private native int zeCommandListDestroy_native(long ptrZeCommandListHandle);

    /**
     * Destroys a command list.
     *
     * <ul>
     * <li>The application must ensure the device is not currently referencing the
     * command list before it is deleted.</li>
     * <li>The implementation of this function may immediately free all Host and
     * Device allocations associated with this command list.</li>
     * <li>The application must not call this function from simultaneous threads
     * with the same command list handle.</li>
     * <li>The implementation of this function must be thread-safe.</li>
     * </ul>
     *
     * @param ptrZeCommandListHandle
     *            [in][release] handle of command list object to destroy
     * @return ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: nullptr ==
     *         ptrZeCommandListHandle
     *         <p>
     *         ZE_RESULT_ERROR_HANDLE_OBJECT_IN_USE
     */
    public int zeCommandListDestroy(ZeCommandListHandle ptrZeCommandListHandle) {
        int result = zeCommandListDestroy_native(ptrZeCommandListHandle.getPtrZeCommandListHandle());
        ptrZeCommandListHandle.initPtr();
        return result;
    }

    private native int zeCommandQueueDestroy_native(long ptrZeCommandListHandle);

    /**
     * Destroys a command queue.
     *
     * <ul>
     * <li>The application must destroy all fence handles created from the command
     * queue before destroying the command queue itself</li>
     * <li>The application must ensure the device is not currently referencing the
     * command queue before it is deleted</li>
     * <li>The implementation of this function may immediately free all Host and
     * Device allocations associated with this command queue</li>
     * <li>The application must not call this function from simultaneous threads
     * with the same command queue handle.</li>
     * <li>The implementation of this function must be thread-safe.</li>
     * </ul>
     *
     * @param commandQueueHandle
     *            [in][release] handle of command queue object to destroy
     * @return ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: nullptr == commandQueueHandle
     *         <p>
     *         ZE_RESULT_ERROR_HANDLE_OBJECT_IN_USE
     */
    public int zeCommandQueueDestroy(ZeCommandQueueHandle commandQueueHandle) {
        int result = zeCommandQueueDestroy_native(commandQueueHandle.getCommandQueueHandlerPointer());
        commandQueueHandle.initPtr();
        return result;
    }

    public void initPtr() {
        this.contextHandle.initPtr();
    }

    private native int zeModuleBuildLogGetString_native(ZeBuildLogHandle buildLog, int[] sizeLog, String[] errorMessage);

    public int zeModuleBuildLogGetString(ZeBuildLogHandle buildLog, int[] sizeLog, String[] errorMessage) {
        return zeModuleBuildLogGetString_native(buildLog, sizeLog, errorMessage);
    }

    private native int zeEventPoolCreate_native(long defaultContextPtr, ZeEventPoolDescription eventPoolDesc, int numDevices, long deviceHandlerPtr, ZeEventPoolHandle eventPool);

    public int zeEventPoolCreate(long defaultContextPtr, ZeEventPoolDescription eventPoolDesc, int numDevices, long deviceHandlerPtr, ZeEventPoolHandle eventPool) {
        return zeEventPoolCreate_native(defaultContextPtr, eventPoolDesc, numDevices, deviceHandlerPtr, eventPool);
    }

    private native int zeEventCreate_native(ZeEventPoolHandle eventPool, ZeEventDescription eventDescription, ZeEventHandle event);

    public int zeEventCreate(ZeEventPoolHandle eventPool, ZeEventDescription eventDescription, ZeEventHandle event) {
        return zeEventCreate_native(eventPool, eventDescription, event);
    }

    private native int zeEventPoolDestroy_native(long eventPool);

    public int zeEventPoolDestroy(ZeEventPoolHandle event) {
        int result = zeEventPoolDestroy_native(event.getPtrZeEventPoolHandle());
        event.initPtr();
        return result;
    }

    private native int zeEventDestroy_native(long event);

    public int zeEventDestroy(ZeEventHandle event) {
        int result = zeEventDestroy_native(event.getPtrZeEventHandle());
        event.initPtr();
        return result;
    }

    private native int zeMemAllocHost_native(long contextPtr, ZeMemAllocHostDesc hostMemAllocDesc, int allocSize, int alignment, LevelZeroByteBuffer hostBuffer);

    public int zeMemAllocHost(long contextPtr, ZeMemAllocHostDesc hostMemAllocDesc, int allocSize, int alignment, LevelZeroByteBuffer hostBuffer) {
        return zeMemAllocHost_native(contextPtr, hostMemAllocDesc, allocSize, alignment, hostBuffer);
    }

}
