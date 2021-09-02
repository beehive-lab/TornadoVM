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

public class ZeDeviceImageProperties {

    private int type;
    private long pNext;
    private long maxImageDims1D;
    private long maxImageDims2D;
    private long maxImageDims3D;
    private long maxImageBufferSize;
    private long maxImageArraySlices;
    private long maxSamplers;
    private long maxReadImageArgs;
    private long maxWriteImageArgs;

    public ZeDeviceImageProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_IMAGE_PROPERTIES;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("=========================\n");
        builder.append("Device Image Properties\n");
        builder.append("=========================\n");
        builder.append("Type                : " + ZeUtils.zeTypeToString(type) + "\n");
        builder.append("pNext               : " + pNext + "\n");
        builder.append("maxImageDims1D      : " + maxImageDims1D + "\n");
        builder.append("maxImageDims2D      : " + maxImageDims2D + "\n");
        builder.append("maxImageDims3D      : " + maxImageDims3D + "\n");
        builder.append("maxImageBufferSize  : " + maxImageBufferSize + "\n");
        builder.append("maxImageArraySlices : " + maxImageArraySlices + "\n");
        builder.append("maxSamplers         : " + maxSamplers + "\n");
        builder.append("maxReadImageArgs    : " + maxReadImageArgs + "\n");
        builder.append("maxWriteImageArgs   : " + maxWriteImageArgs + "\n");
        return builder.toString();
    }

    public int getType() {
        return type;
    }

    public long getpNext() {
        return pNext;
    }

    public long getMaxImageDims1D() {
        return maxImageDims1D;
    }

    public long getMaxImageDims2D() {
        return maxImageDims2D;
    }

    public long getMaxImageDims3D() {
        return maxImageDims3D;
    }

    public long getMaxImageBufferSize() {
        return maxImageBufferSize;
    }

    public long getMaxImageArraySlices() {
        return maxImageArraySlices;
    }

    public long getMaxSamplers() {
        return maxSamplers;
    }

    public long getMaxReadImageArgs() {
        return maxReadImageArgs;
    }

    public long getMaxWriteImageArgs() {
        return maxWriteImageArgs;
    }
}
