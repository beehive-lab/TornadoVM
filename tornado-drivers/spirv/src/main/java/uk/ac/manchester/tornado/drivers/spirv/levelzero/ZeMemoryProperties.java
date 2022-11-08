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

public class ZeMemoryProperties {

    private int type;
    private long pNext;

    private long flags;
    private long maxClockRate;
    private long maxBusWidth;
    private long totalSize;
    private String name;

    private long ptrZeMemoryProperty;

    public ZeMemoryProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MEMORY_PROPERTIES;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Memory Properties\n");
        builder.append("=========================\n");
        builder.append("Type        : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext       : " + pNext + "\n");
        builder.append("flags       : " + ZeUtils.zeFlagsToString(flags) + "\n");
        builder.append("maxClockRate: " + maxClockRate + "\n");
        builder.append("maxBusWidth : " + maxBusWidth + "\n");
        builder.append("totalSize   : " + totalSize + "\n");
        builder.append("name        : " + name + "\n");
        return builder.toString();
    }

    public int getType() {
        return type;
    }

    public long getpNext() {
        return pNext;
    }

    public long getFlags() {
        return flags;
    }

    public long getMaxClockRate() {
        return maxClockRate;
    }

    public long getMaxBusWidth() {
        return maxBusWidth;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public String getName() {
        return name;
    }

    public long getPtrZeMemoryProperty() {
        return ptrZeMemoryProperty;
    }
}
