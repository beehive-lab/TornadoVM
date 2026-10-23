/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.mm;

import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * The frame's slots as they are now, positioned at their end, for binding by value: a dispatch
     * bound to this copy reads the values it was launched with, whatever the frame holds later.
     */
    public ByteBuffer contents() {
        ByteBuffer copy = ByteBuffer.wrap(Arrays.copyOf(buffer.array(), RESERVED_SLOTS << 3)).order(buffer.order());
        copy.position(RESERVED_SLOTS << 3);
        return copy;
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
