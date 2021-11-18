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

public class ZeDeviceMemAllocDesc {

    private int stype;
    private long pNext;
    private long flags;
    private long ordinal;

    private long ptrZeDeviceMemAllocDesc;

    public ZeDeviceMemAllocDesc() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_DEVICE_MEM_ALLOC_DESC;
        this.ptrZeDeviceMemAllocDesc = -1;
        this.pNext = -1;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getFlags() {
        return flags;
    }

    public long getOrdinal() {
        return ordinal;
    }

    public long getPtrZeDeviceMemAllocDesc() {
        return ptrZeDeviceMemAllocDesc;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }
}
