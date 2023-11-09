/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.mm;

import static uk.ac.manchester.tornado.drivers.ptx.mm.PTXKernelArgs.RESERVED_SLOTS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DEVICE_AVAILABLE_MEMORY;

import uk.ac.manchester.tornado.api.memory.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class PTXMemoryManager extends TornadoLogger implements TornadoMemoryProvider {

    private PTXDeviceContext deviceContext;
    private PTXKernelArgs ptxKernelCallWrapper = null;

    public PTXMemoryManager(PTXDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public long getHeapSize() {
        return DEVICE_AVAILABLE_MEMORY;
    }

    public PTXKernelArgs createCallWrapper(final int maxArgs) {
        if (this.ptxKernelCallWrapper == null) {
            long kernelCallBuffer = deviceContext.getDevice().getPTXContext().allocateMemory(RESERVED_SLOTS * Long.BYTES);
            this.ptxKernelCallWrapper = new PTXKernelArgs(kernelCallBuffer, maxArgs, deviceContext);
        }
        return this.ptxKernelCallWrapper;
    }

}
