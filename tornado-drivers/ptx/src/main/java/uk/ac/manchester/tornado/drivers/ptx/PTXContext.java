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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.DUMP_EVENTS;

public class PTXContext {

    private final long ptxContext;
    private final PTXDevice device;
    private final PTXStream stream;
    private final PTXDeviceContext deviceContext;

    public PTXContext(PTXDevice device) {
        this.device = device;

        ptxContext = cuCtxCreate(device.getCuDevice());

        stream = new PTXStream();
        deviceContext = new PTXDeviceContext(device, stream);
    }

    private native static long cuCtxCreate(long deviceIndex);

    private native static long cuCtxDestroy(long cuContext);

    private native static long cuMemAlloc(long cuContext, long numBytes);

    private native static long cuMemFree(long cuContext, long devicePtr);

    private native static long cuCtxSetCurrent(long cuContext);

    public void enablePTXContext() {
        cuCtxSetCurrent(ptxContext);
    }

    public void cleanup() {
        if (DUMP_EVENTS) {
            deviceContext.dumpEvents();
        }

        deviceContext.cleanup();
        cuCtxDestroy(ptxContext);
    }

    public PTXDeviceContext getDeviceContext() {
        return deviceContext;
    }

    public long allocateMemory(long numBytes) {
        try {
            return cuMemAlloc(ptxContext, numBytes);
        } catch (Exception e) {
            throw new TornadoBailoutRuntimeException("[Error during memory allocation] ", e);
        }
    }

    public void freeMemory(long address) {
        cuMemFree(ptxContext, address);
    }
}
