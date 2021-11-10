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

import java.util.stream.IntStream;

public class LevelZeroDevice {

    private LevelZeroDriver driver;
    private ZeDriverHandle driverHandle;
    private int deviceIndex;
    private long deviceHandlerPtr;
    private ZeDeviceProperties deviceProperties;
    private ZeCommandQueueGroupProperties[] commandQueueGroupProperties;

    public LevelZeroDevice(LevelZeroDriver driver, ZeDriverHandle driverHandler, int deviceIndex, long deviceHandlerPointer) {
        this.driver = driver;
        this.driverHandle = driverHandler;
        this.deviceIndex = deviceIndex;
        this.deviceHandlerPtr = deviceHandlerPointer;
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public long getDeviceHandlerPtr() {
        return deviceHandlerPtr;
    }

    native int zeDeviceGetProperties_native(long deviceHandlerPtr, ZeDeviceProperties deviceProperties);

    public int zeDeviceGetProperties(long deviceHandlerPtr, ZeDeviceProperties deviceProperties) {
        int result = zeDeviceGetProperties_native(deviceHandlerPtr, deviceProperties);
        this.deviceProperties = deviceProperties;
        return result;
    }

    public ZeDeviceProperties getDeviceProperties() {
        return deviceProperties;
    }

    public native int zeDeviceGetComputeProperties(long deviceHandlerPtr, ZeComputeProperties computeProperties);

    public native int zeDeviceGetImageProperties(long deviceHandlerPtr, ZeDeviceImageProperties imageProperties);

    native int zeDeviceGetMemoryProperties_native(long deviceHandlerPtr, int[] memoryCount, ZeMemoryProperties[] memoryProperties);

    public int zeDeviceGetMemoryProperties(long deviceHandlerPtr, int[] memoryCount, ZeMemoryProperties[] memoryProperties) {
        if (memoryProperties != null) {
            // Initialize properties
            IntStream.range(0, memoryCount[0]).forEach(i -> memoryProperties[i] = new ZeMemoryProperties());
        }
        int result = zeDeviceGetMemoryProperties_native(deviceHandlerPtr, memoryCount, memoryProperties);
        return result;
    }

    public native int zeDeviceGetMemoryAccessProperties(long deviceHandlerPtr, ZeMemoryAccessProperties memoryAccessProperties);

    native int zeDeviceGetCacheProperties_native(long deviceHandlerPtr, int[] cacheCount, ZeDeviceCacheProperties[] cacheProperties);

    public int zeDeviceGetCacheProperties(long deviceHandlerPtr, int[] cacheCount, ZeDeviceCacheProperties[] cacheProperties) {
        if (cacheProperties != null) {
            // Initialize properties
            IntStream.range(0, cacheCount[0]).forEach(i -> cacheProperties[i] = new ZeDeviceCacheProperties());
        }
        return zeDeviceGetCacheProperties_native(deviceHandlerPtr, cacheCount, cacheProperties);
    }

    private native int zeDeviceGetModuleProperties_native(long deviceHandlerPtr, ZeDeviceModuleProperties deviceModuleProperties);

    public int zeDeviceGetModuleProperties(long deviceHandlerPtr, ZeDeviceModuleProperties deviceModuleProperties) {
        return zeDeviceGetModuleProperties_native(deviceHandlerPtr, deviceModuleProperties);
    }

    native int zeDeviceGetCommandQueueGroupProperties_native(long deviceHandlerPtr, int[] numQueueGroups, ZeCommandQueueGroupProperties[] commandQueueGroupProperties);

    public int zeDeviceGetCommandQueueGroupProperties(long deviceHandlerPtr, int[] numQueueGroups, ZeCommandQueueGroupProperties[] commandQueueGroupProperties) {
        if (commandQueueGroupProperties != null) {
            // Initialize properties
            IntStream.range(0, numQueueGroups[0]).forEach(i -> commandQueueGroupProperties[i] = new ZeCommandQueueGroupProperties());
        }
        int result = zeDeviceGetCommandQueueGroupProperties_native(deviceHandlerPtr, numQueueGroups, commandQueueGroupProperties);
        this.commandQueueGroupProperties = commandQueueGroupProperties;
        return result;
    }

    public ZeCommandQueueGroupProperties[] getCommandQueueGroupProperties() {
        return this.commandQueueGroupProperties;
    }

    public ZeCommandQueueGroupProperties getCommandQueueGroupProperties(int index) {
        return this.commandQueueGroupProperties[index];
    }

    public String getDeviceExtensions() {
        return null;
    }

    public LevelZeroDriver getDriver() {
        return this.driver;
    }

    public ZeDriverHandle getDriverHandler() {
        return this.driverHandle;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SPIRV LEVELZERO Device");
        return sb.toString();
    }

}
