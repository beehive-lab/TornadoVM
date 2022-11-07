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

/**
 * Event Pool Descriptor
 */
public class ZeEventPoolDescriptor extends LevelZeroDescriptor {

    /**
     * [in] creation flags.
     */
    private int flags;

    /**
     * Must be 0 (default) or a valid combination of {@link ZeEventPoolFlags}
     * default behavior is signals and waits are visible to the entire device and
     * peer devices.
     */
    private int count;

    /**
     * C pointer with event description
     */
    private long ptrZeEventPoolDescriptor;

    public ZeEventPoolDescriptor() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_EVENT_POOL_DESC;
        this.ptrZeEventPoolDescriptor = -1;
    }

    private native void materializeNative_ZeEventPoolDescriptor();

    @Override
    public void materialize() {
        materializeNative_ZeEventPoolDescriptor();
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public long getPtrZeEventPoolDescription() {
        return ptrZeEventPoolDescriptor;
    }
}
