package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.util.Arrays;

public class ZeDeviceProperties {

    private int type;
    private long pNext;
    private int vendorId;
    private int deviceId;
    private int flags;
    private int subdeviceId;

    private int coreClockRate;
    private int maxMemAllocSize;
    private int maxHardwareContexts;
    private int maxCommandQueuePriority;

    private int numThreadsPerEU;
    private int physicalEUSimdWidth;
    private int numEUsPerSubslice;
    private int numSubslicesPerSlice;
    private int numSlices;
    private int timerResolution;

    private int timestampValidBits;
    private int kernelTimestampValidBits;
    private int[] uuid;

    private String name;

    public ZeDeviceProperties() {
        type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Compute Properties\n");
        builder.append("=========================\n");
        builder.append("Type                : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext               : " + pNext + "\n");
        builder.append("vendorId            : " + vendorId + "\n");
        builder.append("deviceId            : " + deviceId + "\n");
        builder.append("flags               : " + flags + "\n");
        builder.append("subdeviceId         : " + subdeviceId + "\n");
        builder.append("coreClockRate       : " + coreClockRate + "\n");
        builder.append("maxMemAllocSize     : " + maxMemAllocSize + "\n");
        builder.append("maxHardwareContext  : " + maxHardwareContexts + "\n");
        builder.append("maxCommandQueuePriority: " + maxCommandQueuePriority + "\n");
        builder.append("numThreadsPerEU     : " + numThreadsPerEU + "\n");
        builder.append("physicalEUSimdWidth : " + physicalEUSimdWidth + "\n");
        builder.append("numEUsPerSubslice   : " + numEUsPerSubslice + "\n");
        builder.append("numSubslicesPerSlice: " + numSubslicesPerSlice + "\n");
        builder.append("numSlices           : " + numSlices + "\n");
        builder.append("timerResolution     : " + timerResolution + "\n");
        builder.append("timestampValidBits  : " + timestampValidBits + "\n");
        builder.append("kernelTimestampValidBits: " + kernelTimestampValidBits + "\n");
        builder.append("uuid                : " + Arrays.toString(uuid) + "\n");
        builder.append("name                : " + name + "\n");
        return builder.toString();
    }

    public int getType() {
        return type;
    }

    public long getpNext() {
        return pNext;
    }

    public int getVendorId() {
        return vendorId;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getFlags() {
        return flags;
    }

    public int getSubdeviceId() {
        return subdeviceId;
    }

    public int getCoreClockRate() {
        return coreClockRate;
    }

    public int getMaxMemAllocSize() {
        return maxMemAllocSize;
    }

    public int getMaxHardwareContexts() {
        return maxHardwareContexts;
    }

    public int getMaxCommandQueuePriority() {
        return maxCommandQueuePriority;
    }

    public int getNumThreadsPerEU() {
        return numThreadsPerEU;
    }

    public int getPhysicalEUSimdWidth() {
        return physicalEUSimdWidth;
    }

    public int getNumEUsPerSubslice() {
        return numEUsPerSubslice;
    }

    public int getNumSubslicesPerSlice() {
        return numSubslicesPerSlice;
    }

    public int getNumSlices() {
        return numSlices;
    }

    public int getTimerResolution() {
        return timerResolution;
    }

    public int getTimestampValidBits() {
        return timestampValidBits;
    }

    public int getKernelTimestampValidBits() {
        return kernelTimestampValidBits;
    }

    public int[] getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }
}
