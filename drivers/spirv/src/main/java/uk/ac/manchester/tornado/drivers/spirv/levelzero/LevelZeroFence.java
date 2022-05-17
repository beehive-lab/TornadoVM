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

public class LevelZeroFence {

    private ZeFenceHandle fenceHandler;
    private ZeFenceDescriptor fenceDesc;

    public LevelZeroFence(ZeFenceDescriptor desc, ZeFenceHandle handler) {
        this.fenceDesc = desc;
        this.fenceHandler = handler;
    }

    private native int zeFenceCreate_native(long commandQueueHandlerPointer, ZeFenceDescriptor fenceDesc, ZeFenceHandle fenceHandler);

    public long getHandlerPointer() {
        return this.fenceHandler.getPtrZeFenceHandle();
    }

    public int zeFenceCreate(long commandQueueHandlerPointer, ZeFenceDescriptor fenceDesc, ZeFenceHandle fenceHandler) {
        return zeFenceCreate_native(commandQueueHandlerPointer, fenceDesc, fenceHandler);
    }

    private native int zeFenceHostSynchronize_native(long ptrZeFenceHandle, long maxValue);

    public int zeFenceHostSynchronize(long ptrZeFenceHandle, long maxValue) {
        return zeFenceHostSynchronize_native(ptrZeFenceHandle, maxValue);
    }

    private native int zeFenceReset_native(long ptrZeFenceHandle);

    public int zeFenceReset(long ptrZeFenceHandle) {
        return zeFenceReset_native(ptrZeFenceHandle);
    }
}
