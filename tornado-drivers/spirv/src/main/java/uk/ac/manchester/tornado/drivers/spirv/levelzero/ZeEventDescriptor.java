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
 * Event Descriptor
 */
public class ZeEventDescriptor extends LevelZeroDescriptor {

    /**
     * [in] index of the event within the pool; must be less-than the count
     * specified during pool creation
     */
    private long index;

    /**
     * [in] defines the scope of relevant cache hierarchies to flush on a signal
     * action before the event is triggered. Must be 0 (default) or a valid
     * combination of {@link ZeEventScopeFlags}
     *
     * The default behavior is synchronization within the command list only, no
     * additional cache hierarchies are flushed.
     */
    private int signal;

    /**
     * [in] defines the scope of relevant cache hierarchies to invalidate on a wait
     * action after the event is complete. Must be 0 (default) or a valid
     * combination of {@link ZeEventScopeFlags} default behavior is synchronization
     * within the command list only, no additional cache hierarchies are
     * invalidated.
     */
    private int wait;

    private long ptrZeEventDescriptor;

    public ZeEventDescriptor() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_EVENT_DESC;
        this.ptrZeEventDescriptor = -1;
    }

    private native void materializeNative_ZeEventDescriptor();

    @Override
    public void materialize() {
        materializeNative_ZeEventDescriptor();
    }

    public void setStype(int stype) {
        this.stype = stype;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public void setSignal(int signal) {
        this.signal = signal;
    }

    public void setWait(int wait) {
        this.wait = wait;
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public long getIndex() {
        return index;
    }

    public int getSignal() {
        return signal;
    }

    public int getWait() {
        return wait;
    }

    public long getPtrZeEventDescription() {
        return ptrZeEventDescriptor;
    }
}
