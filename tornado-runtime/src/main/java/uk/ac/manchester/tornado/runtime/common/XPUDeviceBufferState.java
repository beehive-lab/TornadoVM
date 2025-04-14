/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;

import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;

public class XPUDeviceBufferState implements DeviceBufferState {

    private XPUBuffer xpuBuffer;
    private boolean atomicRegionPresent;

    private boolean bufferHasContent;
    private boolean lockBuffer;
    private long partialSize;
    private boolean reuseBuffer = false;

    @Override
    public void setXPUBuffer(XPUBuffer value) {
        xpuBuffer = value;
    }

    public void setAtomicRegion(XPUBuffer buffer) {
        this.xpuBuffer = buffer;
        atomicRegionPresent = true;
    }

    @Override
    public boolean hasObjectBuffer() {
        return xpuBuffer != null;
    }

    @Override
    public XPUBuffer getXPUBuffer() {
        return xpuBuffer;
    }

    @Override
    public boolean isLockedBuffer() {
        return lockBuffer;
    }

    public void setLockBuffer(boolean lockBuffer) {
        this.lockBuffer = lockBuffer;
    }

    @Override
    public boolean hasContent() {
        return bufferHasContent;
    }

    @Override
    public void setContents(boolean content) {
        bufferHasContent = content;
    }

    @Override
    public boolean isAtomicRegionPresent() {
        return atomicRegionPresent;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasObjectBuffer()) {
            sb.append(String.format(" buffer=0x%x, size=%s ", xpuBuffer.toBuffer(), humanReadableByteCount(xpuBuffer.size(), true)));
        } else {
            sb.append(" <unbuffered>");
        }

        return sb.toString();
    }

    @Override
    public void setAtomicRegion() {
        this.atomicRegionPresent = true;
    }

    @Override
    public void setPartialCopySize(long partialCopySize) {
        this.partialSize = partialCopySize;
    }

    @Override
    public long getPartialCopySize() {
        return this.partialSize;
    }

    @Override
    public boolean isBufferReused() {
        return reuseBuffer;
    }

    @Override
    public void markBufferAsReused() {
        reuseBuffer = true;
    }

    public XPUDeviceBufferState createSnapshot() {
        XPUDeviceBufferState xpuDeviceBufferState = new XPUDeviceBufferState();
        xpuDeviceBufferState.setLockBuffer(this.isLockedBuffer());
        return xpuDeviceBufferState;
    }
}
