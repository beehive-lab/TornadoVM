package uk.ac.manchester.tornado.drivers.spirv.timestamps;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventScopeFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelTimeStampResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class LevelZeroKernelTimeStamp {

    private ZeKernelTimeStampResult resultKernel;
    private LevelZeroByteBuffer timeStampBuffer;
    private ZeEventPoolHandle eventPoolHandle;
    private SPIRVDeviceContext deviceContext;
    private LevelZeroCommandList commandList;
    private ZeEventHandle kernelEventTimer;
    private SPIRVLevelZeroCommandQueue commandQueue;

    public LevelZeroKernelTimeStamp(SPIRVDeviceContext deviceContext, LevelZeroCommandList commandList, SPIRVLevelZeroCommandQueue commandQueue) {
        this.deviceContext = deviceContext;
        this.commandList = commandList;
        this.commandQueue = commandQueue;
    }

    public ZeEventHandle getKernelEventTimer() {
        return this.kernelEventTimer;
    }

    public void createEventTimer() {
        if (eventPoolHandle == null) {
            eventPoolHandle = new ZeEventPoolHandle();
        }
        kernelEventTimer = new ZeEventHandle();
        LevelZeroDevice device = commandQueue.getDevice();
        LevelZeroContext context = commandList.getContext();
        createEventPoolAndEvents(context, device, eventPoolHandle, ZeEventPoolFlags.ZE_EVENT_POOL_FLAG_KERNEL_TIMESTAMP, 1, kernelEventTimer);
    }

    private static void createEventPoolAndEvents(LevelZeroContext context, LevelZeroDevice device, ZeEventPoolHandle eventPoolHandle, int poolEventFlags, int poolSize, ZeEventHandle kernelEvent) {

        ZeEventPoolDescription eventPoolDescription = new ZeEventPoolDescription();

        eventPoolDescription.setCount(poolSize);
        eventPoolDescription.setFlags(poolEventFlags);

        int result = context.zeEventPoolCreate(context.getDefaultContextPtr(), eventPoolDescription, 1, device.getDeviceHandlerPtr(), eventPoolHandle);
        LevelZeroUtils.errorLog("zeEventPoolCreate", result);

        // Create Kernel Event
        ZeEventDescription eventDescription = new ZeEventDescription();
        eventDescription.setIndex(0);
        eventDescription.setSignal(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        eventDescription.setWait(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        result = context.zeEventCreate(eventPoolHandle, eventDescription, kernelEvent);
        LevelZeroUtils.errorLog("zeEventCreate", result);
    }

    public void solveEvent(TaskMetaData meta) {
        timeStampBuffer = new LevelZeroByteBuffer();
        ZeHostMemAllocDesc hostMemAllocDesc = new ZeHostMemAllocDesc();
        LevelZeroContext context = commandList.getContext();
        int result = context.zeMemAllocHost(context.getDefaultContextPtr(), hostMemAllocDesc, Sizeof.ze_kernel_timestamp_result_t.getNumBytes(), 1, timeStampBuffer);
        LevelZeroUtils.errorLog("zeMemAllocHost", result);

        result = commandList.zeCommandListAppendQueryKernelTimestamps(commandList.getCommandListHandlerPtr(), 1, kernelEventTimer, timeStampBuffer, null, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendQueryKernelTimestamps", result);
        LevelZeroDevice device = commandQueue.getDevice();
        solveKernelEvent(device);
        updateProfiler(resultKernel, meta);
    }

    public void solveKernelEvent(LevelZeroDevice device) {
        ZeDeviceProperties deviceProperties = new ZeDeviceProperties();
        int result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        LevelZeroUtils.errorLog("zeDeviceGetProperties", result);
        resultKernel = new ZeKernelTimeStampResult(deviceProperties);
        deviceContext.flush(device.getDeviceIndex());

        resultKernel.resolve(timeStampBuffer);

        if (Tornado.DEBUG) {
            resultKernel.printTimers();
        }
    }

    private void updateProfiler(ZeKernelTimeStampResult resultKernel, final TaskMetaData meta) {
        long timer = meta.getProfiler().getTimer(ProfilerType.TOTAL_KERNEL_TIME);
        long kernelElapsedTime = (long) resultKernel.getKernelElapsedTime();
        // Register globalTime
        meta.getProfiler().setTimer(ProfilerType.TOTAL_KERNEL_TIME, timer + kernelElapsedTime);
        // Register the time for the task
        meta.getProfiler().setTaskTimer(ProfilerType.TASK_KERNEL_TIME, meta.getId(), kernelElapsedTime);
    }

}
