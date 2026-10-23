/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /**
     * Whether the task-graph transfers this object between host and device on every execution
     * ({@code EVERY_EXECUTION} uploads and downloads). Set before the buffer is allocated, so a
     * backend can decide how to back the transfers (e.g., pinned host memory).
     */
    private boolean transferredEveryExecution;

    public boolean isTransferredEveryExecution() {
        return transferredEveryExecution;
    }

    public void setTransferredEveryExecution(boolean transferredEveryExecution) {
        this.transferredEveryExecution = transferredEveryExecution;
    }
}
