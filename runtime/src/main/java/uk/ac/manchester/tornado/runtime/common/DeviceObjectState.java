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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;

import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;

public class DeviceObjectState implements TornadoDeviceObjectState {

    private ObjectBuffer buffer;
    private boolean atomicRegionPresent;

    private boolean contents;
    private boolean pinnedBuffer;

    public DeviceObjectState() {
        buffer = null;
        atomicRegionPresent = false;
        contents = false;
        pinnedBuffer = false;
    }

    public void setBuffer(ObjectBuffer value) {
        buffer = value;
    }

    public void setAtomicRegion(ObjectBuffer buffer) {
        this.buffer = buffer;
        atomicRegionPresent = true;
    }

    public boolean hasBuffer() {
        return buffer != null;
    }

    public ObjectBuffer getBuffer() {
        return buffer;
    }

    public boolean isPinnedBuffer() {
        return pinnedBuffer;
    }

    public void setPinnedBuffer(boolean pinnedBuffer) {
        this.pinnedBuffer = pinnedBuffer;
    }

    public boolean hasContents() {
        return contents;
    }

    public void setContents(boolean value) {
        contents = value;
    }

    @Override
    public boolean isAtomicRegionPresent() {
        return atomicRegionPresent;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasBuffer()) {
            sb.append(String.format(" buffer=0x%x, size=%s ", buffer.toBuffer(), humanReadableByteCount(buffer.size(), true)));
        } else {
            sb.append(" <unbuffered>");
        }

        return sb.toString();
    }

    @Override
    public void setAtomicRegion() {
        this.atomicRegionPresent = true;
    }
}
