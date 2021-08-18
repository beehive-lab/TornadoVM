package uk.ac.manchester.tornado.drivers.spirv.timestamps;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;

/**
 * Helper class to measure the elapsed time between two append operations into a
 * LevelZero Command List.
 */
public class LevelZeroTransferTimeStamp implements TimeStamp {

    public static final int ALIGNMENT = 1;
    public static final int BYTES_FOR_METRIC_BUFFER = 64;
    public static final int INIT = -1;

    private LevelZeroCommandList commandList;
    private LevelZeroDevice device;
    private LevelZeroByteBuffer timeStampDeviceBuffer;
    private long[] resultTimeStamp;
    private long timeResolution;
    private SPIRVContext context;

    public LevelZeroTransferTimeStamp(SPIRVContext context, LevelZeroDevice device) {
        timeResolution = INIT;
        this.device = device;
        this.context = context;
    }

    public void flush() {
        context.flush(device.getDeviceIndex());
    }

    public void setCommandList(LevelZeroCommandList commandList) {
        this.commandList = commandList;
    }

    public void createBufferTimeStamp() {
        timeStampDeviceBuffer = new LevelZeroByteBuffer();
        LevelZeroContext context = commandList.getContext();
        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setOrdinal(0);
        deviceMemAllocDesc.setFlags(0);
        int result = context.zeMemAllocDevice(context.getContextHandle().getContextPtr()[0], deviceMemAllocDesc, BYTES_FOR_METRIC_BUFFER, ALIGNMENT, device.getDeviceHandlerPtr(),
                timeStampDeviceBuffer);
        LevelZeroUtils.errorLog("zeMemAllocDevice", result);
    }

    public void appendTimeStamp() {
        int result = commandList.zeCommandListAppendWriteGlobalTimestamp(commandList.getCommandListHandlerPtr(), timeStampDeviceBuffer, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendWriteGlobalTimestamp", result);
    }

    public void readTimeStamp() {
        resultTimeStamp = new long[1];
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), resultTimeStamp, timeStampDeviceBuffer, 8, 0, 0, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
    }

    public long getTimeStamp() {
        if (resultTimeStamp != null) {
            return resultTimeStamp[0];
        }
        return -1;
    }

    public long getTimeResolution() {
        if (timeResolution == INIT) {
            ZeDeviceProperties deviceProperties = new ZeDeviceProperties();
            int result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
            LevelZeroUtils.errorLog("zeDeviceGetProperties", result);
            timeResolution = deviceProperties.getTimerResolution();
        }
        return timeResolution;
    }
}
