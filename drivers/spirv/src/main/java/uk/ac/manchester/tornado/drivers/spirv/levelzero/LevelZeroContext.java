/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.manchester.tornado.drivers.spirv.levelzero;

// A context has a driver-handler associated with it
public class LevelZeroContext {

    ZeDriverHandle driver;
    ZeContextHandle contextHandle;
    ZeContextDesc contextDescription;

    native int zeContextCreate(long driverHandler, ZeContextDesc contextDescriptionPtr, long[] contextPtr);

    public int zeContextCreate(long driverHandler) {
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

    /**
     * Creates a pool of events on the context.
     *
     * Details:
     * <ul>
     * <li>The application must only use events within the pool for the device(s),
     * or their sub-devices, which were provided during creation.</li>
     * <li>The application may call this function from simultaneous threads.</li>
     * <li>- The implementation of this function must be thread-safe.</li>
     * </ul>
     * 
     * 
     * @return Error code:
     * 
     *         <p>
     *         ZE_RESULT_SUCCESS
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: if null is passed as a
     *         defaultContextPtr
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: if null is passed as a
     *         eventPoolDesc or poolHandler
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_ENUMERATION: if description flags contains
     *         0x7
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_OUT_OF_HOST_MEMORY
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_OUT_OF_DEVICE_MEMORY
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SIZE: if device count == 0 or (numDevices ==
     *         0) && (null == devices)
     *         </p>
     */
    public int zeEventPoolCreate(long defaultContextPtr, ZeEventPoolDescription eventPoolDesc, int numDevices, long deviceHandlerPtr, ZeEventPoolHandle eventPool) {
        return zeEventPoolCreate_native(defaultContextPtr, eventPoolDesc, numDevices, deviceHandlerPtr, eventPool);
    }

    private native int zeEventCreate_native(ZeEventPoolHandle eventPool, ZeEventDescription eventDescription, ZeEventHandle event);

    /**
     * Creates an event from the pool.
     * 
     * Details:
     * <ul>
     * <li>An event is used to communicate fine-grain host-to-device, device-to-host
     * or device-to-device dependencies have completed.</li>
     * <li>The application must ensure the location in the pool is not being used,by
     * another event.</li>
     * <li>The application must **not** call this function from simultaneous threads
     * with the same event pool handle.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * Similar to:
     * 
     * <code>
     *     clCreateUserEvent, vkCreateEvent
     * </code>
     * 
     * @param eventPool
     *            [IN] Handle of the event Pool
     * @param eventDescription
     *            [in] Object for the event descriptor
     * @param event
     *            [out] Object that contains a pointer to handle of event object
     *            created
     * @return An error code:
     * 
     *         <p>
     *         ZE_RESULT_SUCCESS
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_ENUMERATION
     *         </p>
     *         <p>
     *         ZE_RESULT_ERROR_OUT_OF_HOST_MEMORY
     *         </p>
     * 
     * 
     */
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

    private native int zeMemAllocHost_native(long contextPtr, ZeHostMemAllocDesc hostMemAllocDesc, int allocSize, int alignment, LevelZeroByteBuffer hostBuffer);

    public int zeMemAllocHost(long contextPtr, ZeHostMemAllocDesc hostMemAllocDesc, int allocSize, int alignment, LevelZeroByteBuffer hostBuffer) {
        return zeMemAllocHost_native(contextPtr, hostMemAllocDesc, allocSize, alignment, hostBuffer);
    }

}
