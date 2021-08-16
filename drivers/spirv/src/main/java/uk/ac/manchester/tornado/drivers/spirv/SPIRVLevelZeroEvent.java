package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventScopeFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelTimeStampResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

public class SPIRVLevelZeroEvent extends SPIRVEvent {

    private ZeEventHandle eventHandle;
    private LevelZeroCommandList commandList;
    private ZeKernelTimeStampResult kernelTimeStampResult;
    private ZeDeviceProperties deviceProperties;
    private LevelZeroByteBuffer levelZeroBufferKernelResult;
    private LevelZeroDevice device;
    private int eventId;
    private EventDescriptor descriptorId;

    public enum RegionAllocBuffer {
        HOST, //
        SHARED //
    }

    public SPIRVLevelZeroEvent(ZeEventHandle eventHandle, LevelZeroCommandList commandList, ZeDeviceProperties deviceProperties, int eventId, EventDescriptor descriptorId) {
        this(eventHandle, commandList, deviceProperties, null, eventId, descriptorId);
    }

    public SPIRVLevelZeroEvent(ZeEventHandle eventHandle, LevelZeroCommandList commandList, ZeDeviceProperties deviceProperties, LevelZeroDevice device, int eventId, EventDescriptor descriptorId) {
        this.eventHandle = eventHandle;
        this.commandList = commandList;
        this.deviceProperties = deviceProperties;
        this.device = device;
        this.eventId = eventId;
    }

    private void setDevice(LevelZeroDevice device) {
        this.device = device;
    }

    private void createKernelTimeStampResult(ZeDeviceProperties deviceProperties) {
        kernelTimeStampResult = new ZeKernelTimeStampResult(deviceProperties);
    }

    private void solveKernelTimeStampResult(LevelZeroByteBuffer buffer) {
        kernelTimeStampResult.resolve(buffer);
    }

    private void createKernelBufferForResult(RegionAllocBuffer region) {
        LevelZeroContext context = commandList.getContext();
        ZeHostMemAllocDesc hostMemAllocDesc = new ZeHostMemAllocDesc();
        levelZeroBufferKernelResult = new LevelZeroByteBuffer();
        if (region == RegionAllocBuffer.HOST) {
            int result = context.zeMemAllocHost(context.getDefaultContextPtr(), hostMemAllocDesc, Sizeof.ze_kernel_timestamp_result_t.getNumBytes(), 1, levelZeroBufferKernelResult);
            LevelZeroUtils.errorLog("zeMemAllocHost", result);
        } else if (region == RegionAllocBuffer.SHARED) {
            if (device == null) {
                throw new RuntimeException("Device is null");
            }
            ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
            levelZeroBufferKernelResult = new LevelZeroByteBuffer();
            int result = context.zeMemAllocShared(context.getDefaultContextPtr(), deviceMemAllocDesc, hostMemAllocDesc, Sizeof.ze_kernel_timestamp_result_t.getNumBytes(), 1,
                    device.getDeviceHandlerPtr(), levelZeroBufferKernelResult);
            LevelZeroUtils.errorLog("zeMemAllocShared", result);
        }
    }

    public void createEventPoolAndEvents(ZeEventPoolHandle eventPoolHandle, int poolEventFlags, int poolSize, ZeEventHandle kernelEvent) {

        LevelZeroContext context = commandList.getContext();
        ZeEventPoolDescription eventPoolDescription = new ZeEventPoolDescription();

        eventPoolDescription.setCount(poolSize);
        eventPoolDescription.setFlags(poolEventFlags);

        // Create a pool of events
        int result = context.zeEventPoolCreate(context.getDefaultContextPtr(), eventPoolDescription, 1, device.getDeviceHandlerPtr(), eventPoolHandle);
        LevelZeroUtils.errorLog("zeEventPoolCreate", result);

        // Create the Kernel Event
        ZeEventDescription eventDescription = new ZeEventDescription();
        eventDescription.setIndex(0);
        eventDescription.setSignal(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        eventDescription.setWait(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        result = context.zeEventCreate(eventPoolHandle, eventDescription, kernelEvent);
        LevelZeroUtils.errorLog("zeEventCreate", result);
    }

    @Override
    public void waitForEvents() {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public long getQueuedTime() {
        return 0;
    }

    @Override
    public long getSubmitTime() {
        return 0;
    }

    @Override
    public long getStartTime() {
        return 0;
    }

    @Override
    public long getEndTime() {
        return 0;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public long getDriverDispatchTime() {
        return 0;
    }

    @Override
    public double getElapsedTimeInSeconds() {
        return 0;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return null;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return 0;
    }

    @Override
    public void waitOn() {

    }

    @Override
    public void destroy() {
    }

}
