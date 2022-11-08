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

import java.util.Arrays;

public class ZeDeviceProperties {

    private int stype;
    private long pNext;
    private int type;
    private int vendorId;
    private int deviceId;
    private int flags;
    private int subdeviceId;

    private int coreClockRate;
    private long maxMemAllocSize;
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
        stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES;
    }

    public ZeDeviceType getType(int type) {
        switch (type) {
            case 1:
                return ZeDeviceType.ZE_DEVICE_TYPE_GPU;
            case 2:
                return ZeDeviceType.ZE_DEVICE_TYPE_CPU;
            case 3:
                return ZeDeviceType.ZE_DEVICE_TYPE_FPGA;
            case 4:
                return ZeDeviceType.ZE_DEVICE_TYPE_MCA;
            case 5:
                return ZeDeviceType.ZE_DEVICE_TYPE_VPU;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public ZeDeviceType getType() {
        return getType(this.type);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Properties\n");
        builder.append("=========================\n");
        builder.append("STye                : " + ZeUtils.zeTypeToString(stype) + "\n");
        builder.append("pNext               : " + pNext + "\n");
        builder.append("Type                : " + getType(type) + "\n");
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

    public int getStype() {
        return stype;
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

    public long getMaxMemAllocSize() {
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
