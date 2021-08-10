package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventHandle;
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

    public enum RegionAllocBuffer {
        HOST, SHARED
    }

    public SPIRVLevelZeroEvent(ZeEventHandle eventHandle, LevelZeroCommandList commandList, ZeDeviceProperties deviceProperties) {
        this(eventHandle, commandList, deviceProperties, null);
    }

    public SPIRVLevelZeroEvent(ZeEventHandle eventHandle, LevelZeroCommandList commandList, ZeDeviceProperties deviceProperties, LevelZeroDevice device) {
        this.eventHandle = eventHandle;
        this.commandList = commandList;
        this.deviceProperties = deviceProperties;
        this.device = device;
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
}
