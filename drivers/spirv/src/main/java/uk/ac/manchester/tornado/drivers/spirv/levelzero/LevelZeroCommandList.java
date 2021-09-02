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

public class LevelZeroCommandList {

    private LevelZeroContext context;
    private ZeCommandListHandle zeCommandList;

    public LevelZeroCommandList(LevelZeroContext context, ZeCommandListHandle zeCommandList) {
        this.context = context;
        this.zeCommandList = zeCommandList;
    }

    public ZeCommandListHandle getCommandListHandler() {
        return this.zeCommandList;
    }

    public long getCommandListHandlerPtr() {
        return this.zeCommandList.getPtrZeCommandListHandle();
    }

    public LevelZeroContext getContext() {
        return this.context;
    }

    native int zeCommandListAppendLaunchKernel_native(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, ZeEventHandle signalEvent, int numWaitEvents, Object phWaitEvents);

    public int zeCommandListAppendLaunchKernel(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, ZeEventHandle signalEvent, int numWaitEvents, Object phWaitEvents) {
        return zeCommandListAppendLaunchKernel_native(commandListHandler, ptrZeKernelHandle, dispatch, signalEvent, numWaitEvents, phWaitEvents);
    }

    native int zeCommandListClose_native(long ptrZeCommandListHandle);

    /**
     * Closes a command list; ready to be executed by a command queue.
     *
     * <ul>
     * <li>The application must **not** call this function from simultaneous threads
     * with the same command list handle.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     *
     * @param ptrZeCommandListHandle
     *            [in] Pointer handler of command list object to close
     * @return ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: (null == hCommandList)
     */
    public int zeCommandListClose(long ptrZeCommandListHandle) {
        return zeCommandListClose_native(ptrZeCommandListHandle);
    }

    private native int zeCommandListAppendMemoryCopy_native(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, byte[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeChar(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, char[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeShort(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, short[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeInt(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, int[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeFloat(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, float[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeDouble(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, double[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeLong(long commandListHandlerPtr, LevelZeroByteBuffer deviceBuffer, long[] heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    /**
     * Copies host, device, or shared memory.
     * <p>
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
     * <p>
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
     *         <p>
     *         ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == hCommandList
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: null == dstptr or null ==
     *         srcptr
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SIZE: (null == phWaitEvents) && (0 <
     *         numWaitEvents)
     */
    public int zeCommandListAppendMemoryCopy(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, byte[] srcBuffer, long allocSize, ZeEventHandle hSignalEvents, int numWaitEvents,
            ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_native(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, 0, 0, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    /**
     * Copies host, device, or shared memory.
     * <p>
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
     * <p>
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
     * @param dstOffset
     *            [in] offset in bytes for the dstBuffer
     * @param srcOffset
     *            [in] offset in bytes for the srcBuffer
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
     *         <p>
     *         ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == hCommandList
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: null == dstptr or null ==
     *         srcptr
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SIZE: (null == phWaitEvents) && (0 <
     *         numWaitEvents)
     */
    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, byte[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_native(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, char[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeChar(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, short[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeShort(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, int[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeInt(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, float[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeFloat(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, double[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeDouble(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, long[] srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeLong(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, int[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBackInt(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, char[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBackChar(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, short[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBackShort(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, float[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBackFloat(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, double[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBackDouble(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, long[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBackLong(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    private native int zeCommandListAppendMemoryCopy_nativeBack(long commandListHandlerPtr, byte[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeBackChar(long commandListHandlerPtr, char[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeBackShort(long commandListHandlerPtr, short[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeBackInt(long commandListHandlerPtr, int[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeBackFloat(long commandListHandlerPtr, float[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeBackDouble(long commandListHandlerPtr, double[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    private native int zeCommandListAppendMemoryCopy_nativeBackLong(long commandListHandlerPtr, long[] deviceBuffer, LevelZeroByteBuffer heapBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents);

    public int zeCommandListAppendMemoryCopy(long commandListHandlerPtr, byte[] dstBuffer, LevelZeroByteBuffer srcBuffer, int allocSize, ZeEventHandle hSignalEvents, int numWaitEvents,
            ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBack(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, 0, 0, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    public int zeCommandListAppendMemoryCopyWithOffset(long commandListHandlerPtr, byte[] dstBuffer, LevelZeroByteBuffer srcBuffer, long allocSize, long dstOffset, long srcOffset,
            ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBack(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, dstOffset, srcOffset, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    private native int zeCommandListAppendBarrier_native(long commandListHandlerPtr, ZeEventHandle hSignalEvent, int numWaitEvents, Object phWaitEvents);

    /**
     * Appends an execution and global memory barrier into a command list.
     *
     * @param commandListHandlerPtr
     *            [in] handle pointer of the command list
     * @param hSignalEvent
     *            [in][optional] {@link ZeEventHandle} of the event to signal on
     *            completion
     * @param numWaitEvents
     *            [in][optional] number of events to wait on before executing
     *            barrier; It must be 0 if `null == phWaitEvents`
     * @param phWaitEvents
     *            [in][optional][range(0, numWaitEvents)] handle of the events to
     *            wait on before executing the barrier.
     * @return ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: nullptr == hCommandList
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SIZE: (nullptr == phWaitEvents) && (0 <
     *         numWaitEvents)
     * @remark: This method is similar to: clEnqueueBarrierWithWaitList
     */
    public int zeCommandListAppendBarrier(long commandListHandlerPtr, ZeEventHandle hSignalEvent, int numWaitEvents, Object phWaitEvents) {
        return zeCommandListAppendBarrier_native(commandListHandlerPtr, hSignalEvent, numWaitEvents, phWaitEvents);
    }

    private native int zeCommandListAppendMemoryCopy_nativeBuffers(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, LevelZeroByteBuffer srcBuffer, int allocSize, ZeEventHandle hSignalEvents,
            int numWaitEvents, ZeEventHandle phWaitEvents);

    /**
     * Copies host, device, or shared memory.
     * <p>
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
     * <p>
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
     *         <p>
     *         ZE_RESULT_SUCCESS
     *         <p>
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *         <p>
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == hCommandList
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_NULL_POINTER: null == dstptr or null ==
     *         srcptr
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     *         <p>
     *         ZE_RESULT_ERROR_INVALID_SIZE: (null == phWaitEvents) && (0 <
     *         numWaitEvents)
     */
    public int zeCommandListAppendMemoryCopy(long commandListHandlerPtr, LevelZeroByteBuffer dstBuffer, LevelZeroByteBuffer srcBuffer, int allocSize, ZeEventHandle hSignalEvents, int numWaitEvents,
            ZeEventHandle phWaitEvents) {
        return zeCommandListAppendMemoryCopy_nativeBuffers(commandListHandlerPtr, dstBuffer, srcBuffer, allocSize, hSignalEvents, numWaitEvents, phWaitEvents);
    }

    private native int zeCommandListReset_native(long commandListHandlerPtr);

    /**
     * Reset a command list to initial (empty) state; ready for appending commands.
     * 
     * <ul>
     * <li>The application must ensure the device is not currently referencing the
     * command list before it is reset</li>
     * <li>The application must not call this function from simultaneous threads
     * with the same command list handle.</li>
     * <li>The implementation of this function should be lock-free.</li>
     * </ul>
     * 
     * @param commandListHandlerPtr
     *            [in] handle of command list object to reset
     * @return
     * 
     *         ZE_RESULT_SUCCESS
     *
     *         ZE_RESULT_ERROR_UNINITIALIZED
     *
     *         ZE_RESULT_ERROR_DEVICE_LOST
     *
     *         ZE_RESULT_ERROR_INVALID_NULL_HANDLE: null == commandListHandlerPtr
     */
    public int zeCommandListReset(long commandListHandlerPtr) {
        return zeCommandListReset_native(commandListHandlerPtr);
    }

    private native int zeCommandListAppendQueryKernelTimestamps_native(long commandListHandlerPtr, int numEvents, ZeEventHandle event, LevelZeroByteBuffer timeStampBuffer, int[] offsets,
            ZeEventHandle signalEventHandler, int numWaitEvents, ZeEventHandle[] waitEventsHandlers);

    public int zeCommandListAppendQueryKernelTimestamps(long commandListHandlerPtr, int numEvents, ZeEventHandle event, LevelZeroByteBuffer timeStampBuffer, int[] offsets,
            ZeEventHandle signalEventHandler, int numWaitEvents, ZeEventHandle[] waitEventsHandlers) {
        return zeCommandListAppendQueryKernelTimestamps_native(commandListHandlerPtr, numEvents, event, timeStampBuffer, offsets, signalEventHandler, numWaitEvents, waitEventsHandlers);
    }

    private native int zeCommandListAppendWriteGlobalTimestamp_native(long commandListHandlerPtr, LevelZeroByteBuffer bufferTimeStamp, ZeEventHandle hSignalEvents, int numWaitEvents,
            ZeEventHandle phWaitEvents);

    /**
     * Appends a memory write of the device's global timestamp value into a command
     * list.
     * 
     * @param commandListHandlerPtr
     *            Pointer to the command list handler
     * @param bufferTimeStamp
     *            pointer to memory where timestamp value will be written; must be
     *            8byte-aligned.
     * @param hSignalEvents
     *            handle of the event to signal on completion
     * @param numWaitEvents
     *            number of events to wait on before executing query;
     * @param phWaitEvents
     *            handle of the events to wait on before executing query
     * @return Error code
     * 
     *         <code>
     *  - ZE_RESULT_SUCCESS
     *  - ZE_RESULT_ERROR_UNINITIALIZED
     *  - ZE_RESULT_ERROR_DEVICE_LOST
     *  - ZE_RESULT_ERROR_INVALID_NULL_HANDLE
     *    + `-1 == commandListHandlerPtr`
     *  - ZE_RESULT_ERROR_INVALID_NULL_POINTER
     *    + `null == bufferTimeStamp`
     *  - ZE_RESULT_ERROR_INVALID_SYNCHRONIZATION_OBJECT
     *  - ZE_RESULT_ERROR_INVALID_SIZE
     *   + `(null == phWaitEvents) && (0 < numWaitEvents)`
     *      </code>
     * 
     */
    public int zeCommandListAppendWriteGlobalTimestamp(long commandListHandlerPtr, LevelZeroByteBuffer bufferTimeStamp, ZeEventHandle hSignalEvents, int numWaitEvents, ZeEventHandle phWaitEvents) {
        return zeCommandListAppendWriteGlobalTimestamp_native(commandListHandlerPtr, bufferTimeStamp, hSignalEvents, numWaitEvents, phWaitEvents);
    }
}
