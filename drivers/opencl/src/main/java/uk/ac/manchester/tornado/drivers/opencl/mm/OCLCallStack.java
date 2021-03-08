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
package uk.ac.manchester.tornado.drivers.opencl.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;

import java.util.HashMap;

import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;

public class OCLCallStack extends OCLByteBuffer implements CallStack {

    public final static int RETURN_VALUE_INDEX = 0;
    public static final int RESERVED_SLOTS = 3;

    private final int numArgs;
    private OCLDeviceContext deviceContext;

    private boolean onDevice;

    OCLCallStack(long offset, int numArgs, OCLDeviceContext device) {
        super(device, offset, (numArgs + RESERVED_SLOTS) << 3);
        this.numArgs = numArgs;
        this.deviceContext = device;

        buffer.clear();
        onDevice = false;
    }

    @Override
    public boolean isOnDevice() {
        return onDevice;
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
    public void write() {
        super.write();
        onDevice = true;
    }

    @Override
    public int enqueueWrite() {
        return enqueueWrite(null);
    }

    @Override
    public int enqueueWrite(int[] events) {
        onDevice = true;
        return super.enqueueWrite(events);
    }

    public int getSlotCount() {
        return (int) bytes >> 3;
    }

    @Override
    public void reset() {
        buffer.mark();
        buffer.reset();
        onDevice = false;
    }

    @Override
    public long getDeoptValue() {
        return buffer.getLong(8);
    }

    @Override
    public long getReturnValue() {
        read();
        return buffer.getLong(0);
    }

    @Override
    public int getArgCount() {
        return buffer.getInt(10);
    }

    @Override
    public String toString() {
        return String.format("Call Stack: num args = %d, device = %s, size = %s @ 0x%x (0x%x)", numArgs, deviceContext.getDevice().getDeviceName(), humanReadableByteCount(bytes, true),
                toAbsoluteAddress(), toRelativeAddress());
    }

    @Override
    public void dump() {
        super.dump(8);
    }

    @Override
    public void setHeader(HashMap<Integer, Integer> map) {
        buffer.clear();
        for (int i = 0; i < RESERVED_SLOTS; i++) {
            if (map.containsKey(i)) {
                buffer.putLong(map.get(i));
            } else {
                buffer.putLong(0);
            }
        }
    }

    @Override
    public void push(Object arg) {
        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else if (isBoxedPrimitive(arg) || arg.getClass().isPrimitive()) {
            if (DEBUG) {
                debug("arg : type=%s, value=%s", arg.getClass().getName(), arg.toString());
            }
            PrimitiveSerialiser.put(buffer, arg, 8);
        } else {
            shouldNotReachHere();
        }
    }

    @Override
    public void push(Object arg, DeviceObjectState state) {
        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else {
            if (DEBUG) {
                debug("arg : [0x%x] type=%s, value=%s, address=0x%x (0x%x)", arg.hashCode(), arg.getClass().getSimpleName(), arg, state.getAddress(), state.getOffset());
            }
            if (deviceContext.useRelativeAddresses()) {
                buffer.putLong(state.getOffset());
            } else {
                buffer.putLong(state.getAddress());
            }
        }
    }
}
