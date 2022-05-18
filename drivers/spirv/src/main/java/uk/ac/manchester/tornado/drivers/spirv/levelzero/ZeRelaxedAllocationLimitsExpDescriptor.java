/*
 * MIT License
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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

/**
 * Relaxed limits memory allocation descriptor.
 *
 * - This structure may be passed to `zeMemAllocShared` or `zeMemAllocDevice`,
 * via `pNext` member of {@link ZeDeviceMemAllocDescriptor}.
 * - This structure may also be passed to `zeMemAllocHost`, via `pNext`
 * member of {@link ZeHostMemAllocDescriptor}.
 */
public class ZeRelaxedAllocationLimitsExpDescriptor extends LevelZeroDescriptor {

    private int flags;

    public ZeRelaxedAllocationLimitsExpDescriptor() {
        pNext = -1;
        stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_RELAXED_ALLOCATION_LIMITS_EXP_DESC;
        selfPtr = -1;
    }

    private native void materializeNative_ZeRelaxedAllocationLimitsExpDescriptor();

    @Override
    public void materialize() {
        // Native Call to fill with content of the selfPtr for the current object
        materializeNative_ZeRelaxedAllocationLimitsExpDescriptor();
    }

    public int getStype() {
        return this.stype;
    }

    public long getPNext() {
        return this.pNext;
    }

    public int getFlags() {
        return this.flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void setPNext(long pNextPtr) {
        this.pNext = pNextPtr;
    }

}
