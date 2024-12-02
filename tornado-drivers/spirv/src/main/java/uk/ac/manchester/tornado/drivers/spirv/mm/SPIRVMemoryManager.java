/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.mm;

import static uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVKernelStackFrame.RESERVED_SLOTS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLKernelStackFrame;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;

public class SPIRVMemoryManager implements TornadoMemoryProvider {

    private SPIRVDeviceContext deviceContext;

    private Map<Long, SPIRVKernelStackFrame> spirvKernelStackFrame = new ConcurrentHashMap<>();

    public SPIRVMemoryManager(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public long getHeapSize() {
        return DEVICE_AVAILABLE_MEMORY;
    }

    public SPIRVKernelStackFrame createKernelStackFrame(long threadId, final int maxArgs, Access access) {
        if (!spirvKernelStackFrame.containsKey(threadId)) {
            long kernelCallBuffer = deviceContext.getSpirvContext().allocateMemory(deviceContext.getDevice().getDeviceIndex(), RESERVED_SLOTS * Long.BYTES, access);
            spirvKernelStackFrame.put(threadId, new SPIRVKernelStackFrame(kernelCallBuffer, maxArgs, deviceContext));
        }
        return spirvKernelStackFrame.get(threadId);
    }

    public void releaseKernelStackFrame(long executionPlanId) {
        SPIRVKernelStackFrame stackFrame = spirvKernelStackFrame.remove(executionPlanId);
        if (stackFrame != null) {
            stackFrame.invalidate();
        }
    }

}
