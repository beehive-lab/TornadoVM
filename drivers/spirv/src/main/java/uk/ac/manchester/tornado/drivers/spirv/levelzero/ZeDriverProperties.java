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

public class ZeDriverProperties {

    public static final int ZE_MAX_DRIVER_UUID_SIZE = 16;

    private int type;
    private long pNext;
    private int[] uuid;
    private int driverVersion;

    private long nativePointer;

    public ZeDriverProperties() {
        this.type = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DRIVER_PROPERTIES;
        pNext = 0;
        uuid = new int[ZE_MAX_DRIVER_UUID_SIZE];
        this.nativePointer = -1;
    }

    public ZeDriverProperties(int type) {
        this.type = type;
        pNext = 0;
        uuid = new int[ZE_MAX_DRIVER_UUID_SIZE];
        this.nativePointer = -1;
    }

    public int[] getUUID() {
        return uuid;
    }

    public int getDriverVersion() {
        return this.driverVersion;
    }

}
