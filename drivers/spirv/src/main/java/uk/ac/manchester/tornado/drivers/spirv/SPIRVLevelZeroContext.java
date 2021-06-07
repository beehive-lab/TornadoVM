package uk.ac.manchester.tornado.drivers.spirv;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListFlag;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandListHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueGroupPropertyFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueueMode;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeCommandQueuePriority;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVLevelZeroContext extends SPIRVContext {

    private LevelZeroContext levelZeroContext;
    private List<SPIRVDeviceContext> spirvDeviceContext;
    private List<SPIRVLevelZeroCommandQueue> commandQueues;
    LevelZeroByteBuffer deviceBuffer;

    // This class should only receives 1 device, not a list of devices.
    public SPIRVLevelZeroContext(SPIRVPlatform platform, List<SPIRVDevice> devices, LevelZeroContext levelZeroContext) {
        super(platform, devices);
        this.levelZeroContext = levelZeroContext;

        commandQueues = new ArrayList<>();
        for (SPIRVDevice device : devices) {
            LevelZeroCommandQueue commandQueue = createCommandQueue(levelZeroContext, device);
            LevelZeroCommandList commandList = createCommandList(levelZeroContext, device);
            commandQueues.add(new SPIRVLevelZeroCommandQueue(commandQueue, commandList, (LevelZeroDevice) device.getDevice()));
        }

        spirvDeviceContext = new ArrayList<>();

        // Create LevelZeroDeviceContext
        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            SPIRVDeviceContext deviceContext = new SPIRVLevelZeroDeviceContext(devices.get(deviceIndex), commandQueues.get(deviceIndex), this);
            devices.get(deviceIndex).setDeviceContext(deviceContext);
            spirvDeviceContext.add(deviceContext);
        }
    }

    public LevelZeroContext getLevelZeroContext() {
        return levelZeroContext;
    }

    public static int getCommandQueueOrdinal(LevelZeroDevice device) {
        int[] numQueueGroups = new int[1];
        int result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, null);
        LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        if (numQueueGroups[0] == 0) {
            throw new RuntimeException("Number of Queue Groups is 0 for device: " + device.getDeviceProperties().getName());
        }
        int ordinal = numQueueGroups[0];

        ZeCommandQueueGroupProperties[] commandQueueGroupProperties = new ZeCommandQueueGroupProperties[numQueueGroups[0]];
        result = device.zeDeviceGetCommandQueueGroupProperties(device.getDeviceHandlerPtr(), numQueueGroups, commandQueueGroupProperties);
        LevelZeroUtils.errorLog("zeDeviceGetCommandQueueGroupProperties", result);

        for (int i = 0; i < numQueueGroups[0]; i++) {
            if ((commandQueueGroupProperties[i].getFlags()
                    & ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) == ZeCommandQueueGroupPropertyFlags.ZE_COMMAND_QUEUE_GROUP_PROPERTY_FLAG_COMPUTE) {
                ordinal = i;
                break;
            }
        }
        return ordinal;
    }

    private LevelZeroCommandQueue createCommandQueue(LevelZeroContext context, SPIRVDevice spirvDevice) {
        LevelZeroDevice device = (LevelZeroDevice) spirvDevice.getDevice();
        // Create Command Queue
        ZeCommandQueueDescription cmdDescriptor = new ZeCommandQueueDescription();
        cmdDescriptor.setFlags(0);
        cmdDescriptor.setMode(ZeCommandQueueMode.ZE_COMMAND_QUEUE_MODE_DEFAULT);
        cmdDescriptor.setPriority(ZeCommandQueuePriority.ZE_COMMAND_QUEUE_PRIORITY_NORMAL);
        cmdDescriptor.setOrdinal(getCommandQueueOrdinal(device));
        cmdDescriptor.setIndex(0);

        ZeCommandQueueHandle zeCommandQueueHandle = new ZeCommandQueueHandle();
        int result = context.zeCommandQueueCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdDescriptor, zeCommandQueueHandle);
        LevelZeroUtils.errorLog("zeCommandQueueCreate", result);
        return new LevelZeroCommandQueue(context, zeCommandQueueHandle);
    }

    private LevelZeroCommandList createCommandList(LevelZeroContext context, SPIRVDevice spirvDevice) {
        LevelZeroDevice device = (LevelZeroDevice) spirvDevice.getDevice();
        ZeCommandListDescription cmdListDescriptor = new ZeCommandListDescription();
        cmdListDescriptor.setFlags(ZeCommandListFlag.ZE_COMMAND_LIST_FLAG_RELAXED_ORDERING);
        cmdListDescriptor.setCommandQueueGroupOrdinal(getCommandQueueOrdinal(device));
        ZeCommandListHandle commandListHandler = new ZeCommandListHandle();
        int result = context.zeCommandListCreate(context.getContextHandle().getContextPtr()[0], device.getDeviceHandlerPtr(), cmdListDescriptor, commandListHandler);
        LevelZeroUtils.errorLog("zeCommandListCreate", result);
        return new LevelZeroCommandList(context, commandListHandler);
    }

    @Override
    public SPIRVDeviceContext getDeviceContext(int deviceIndex) {
        return spirvDeviceContext.get(deviceIndex);
    }

    @Override
    public SPIRVCommandQueue createCommandQueue(int deviceIndex) {
        return getCommandQueueForDevice(deviceIndex);
    }

    @Override
    public SPIRVCommandQueue getCommandQueueForDevice(int deviceIndex) {
        if (deviceIndex < commandQueues.size()) {
            return commandQueues.get(deviceIndex);
        }
        return null;
    }

    private ZeDeviceMemAllocDesc createDeviceDescription() {
        ZeDeviceMemAllocDesc deviceMemAllocDesc = new ZeDeviceMemAllocDesc();
        deviceMemAllocDesc.setFlags(ZeDeviceMemAllocFlags.ZE_DEVICE_MEM_ALLOC_FLAG_BIAS_UNCACHED);
        deviceMemAllocDesc.setOrdinal(0);
        return deviceMemAllocDesc;
    }

    private ZeHostMemAllocDesc createHostMemDescription() {
        ZeHostMemAllocDesc hostMemAllocDesc = new ZeHostMemAllocDesc();
        hostMemAllocDesc.setFlags(ZeHostMemAllocFlags.ZE_HOST_MEM_ALLOC_FLAG_BIAS_UNCACHED);
        return hostMemAllocDesc;
    }

    @Override
    public long allocateMemory(int deviceIndex, long numBytes) {
        deviceBuffer = new LevelZeroByteBuffer();
        LevelZeroDevice l0Device = (LevelZeroDevice) devices.get(deviceIndex).getDevice();
        ZeDeviceMemAllocDesc deviceMemAllocDesc = createDeviceDescription();
        if (TornadoOptions.LEVEL_ZERO_SHARED_MEMORY) {
            ZeHostMemAllocDesc hostMemAllocDesc = createHostMemDescription();
            int result = levelZeroContext.zeMemAllocShared(levelZeroContext.getDefaultContextPtr(), deviceMemAllocDesc, hostMemAllocDesc, (int) numBytes, 1, l0Device.getDeviceHandlerPtr(),
                    deviceBuffer);
            LevelZeroUtils.errorLog("zeMemAllocShared", result);
            // FIXME NOTE: Not sure if we should return the raw pointer here for Level Zero
        } else {
            int result = levelZeroContext.zeMemAllocDevice(levelZeroContext.getDefaultContextPtr(), deviceMemAllocDesc, (int) numBytes, 1, l0Device.getDeviceHandlerPtr(), deviceBuffer);
            LevelZeroUtils.errorLog("zeMemAllocDevice", result);
        }
        return deviceBuffer.getPtrBuffer();
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, byte[] value, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, char[] value, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, short[] value, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, int[] array, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), array, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, float[] value, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, double[] value, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int readBuffer(int deviceIndex, long bufferId, long srcOffset, long bytes, long[] value, long dstOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), value, deviceBuffer, bytes, dstOffset, srcOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    // FIXME: <TODO> Events are still pending
    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, byte[] value, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, array, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, float[] array, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, array, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, double[] value, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public int enqueueWriteBuffer(int deviceIndex, long bufferId, long offset, long bytes, long[] value, long hostOffset, int[] waitEvents) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendMemoryCopyWithOffset(commandList.getCommandListHandlerPtr(), deviceBuffer, value, bytes, offset, hostOffset, null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendMemoryCopyWithOffset", result);
        enqueueBarrier(deviceIndex);
        return 0;
    }

    @Override
    public void enqueueBarrier(int deviceIndex) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        int result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);
    }

    @Override
    public void flush(int deviceIndex) {
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroCommandQueue commandQueue = spirvCommandQueue.getCommandQueue();

        // Close the command list
        int result = commandList.zeCommandListClose(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListClose", result);

        // Execute all commands within the command list
        result = commandQueue.zeCommandQueueExecuteCommandLists(commandQueue.getCommandQueueHandlerPtr(), 1, commandList.getCommandListHandler(), null);
        LevelZeroUtils.errorLog("zeCommandQueueExecuteCommandLists", result);

        // Synchronize
        result = commandQueue.zeCommandQueueSynchronize(commandQueue.getCommandQueueHandlerPtr(), Long.MAX_VALUE);
        LevelZeroUtils.errorLog("zeCommandQueueSynchronize", result);

        // Reset for the rest of executions
        result = commandList.zeCommandListReset(commandList.getCommandListHandlerPtr());
        LevelZeroUtils.errorLog("zeCommandListReset", result);

    }

    @Override
    public long executeAndReadLookupBufferAddressKernel(TaskMetaData meta) {
        int deviceIndex = 0;
        SPIRVLevelZeroCommandQueue spirvCommandQueue = commandQueues.get(deviceIndex);
        LevelZeroCommandList commandList = spirvCommandQueue.getCommandList();
        LevelZeroCommandQueue commandQueue = spirvCommandQueue.getCommandQueue();
        LevelZeroDevice device = (LevelZeroDevice) devices.get(deviceIndex).getDevice();
        String prebuiltBinaries = System.getenv("TORNADO_SDK") + "/examples/generated/lookUpBufferAddress.spv";
        LevelZeroKernel levelZeroKernel = LevelZeroUtils.compileSPIRVKernel(device, levelZeroContext, "lookUp", prebuiltBinaries);
        long[] output = new long[1];
        final int bufferSize = Sizeof.LONG.getNumBytes();
        long address = LevelZeroUtils.dispatchLookUpBuffer(commandList, commandQueue, levelZeroKernel, deviceBuffer, output, bufferSize);
        return address;
    }

}
