/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022-2023 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.mm;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MetalKernelStackFrame extends MetalByteBuffer implements KernelStackFrame {

    public static final int RETURN_VALUE_INDEX = 0;
    public static final int RESERVED_SLOTS = 3;

    private final ArrayList<CallArgument> callArguments;

    private boolean isValid;

    MetalKernelStackFrame(long bufferId, int numArgs, MetalDeviceContext device) {
        super(device, bufferId, 0, RESERVED_SLOTS << 3);
        this.callArguments = new ArrayList<>(numArgs);
        buffer.clear();
        this.isValid = true;
    }

    @Override
    public void addCallArgument(Object value, boolean isReferenceType) {
        callArguments.add(new CallArgument(value, isReferenceType));
    }

    @Override
    public void reset() {
        callArguments.clear();
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    @Override
    public void invalidate() {
        isValid = false;
        deviceContext.getPlatformContext().releaseBuffer(toBuffer());
    }

    @Override
    public List<CallArgument> getCallArguments() {
        return callArguments;
    }

    @Override
    public void setKernelContext(HashMap<Integer, Integer> map) {
        buffer.clear();
        for (int i = 0; i < RESERVED_SLOTS; i++) {
            if (map.containsKey(i)) {
                buffer.putLong(map.get(i));
            } else {
                buffer.putLong(0);
            }
        }
    }
}
