/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

import java.util.function.Function;

import uk.ac.manchester.tornado.drivers.cuda.graal.backend.CUDABackend;

/**
 * One CUDA device as the runtime uses it: its device context and the TornadoVM memory regions on
 * it. The Graal compiler backend for the device is built only when Graal compiles a kernel (never
 * under TC, the Graal-free compiler), from the same device context.
 */
public final class CUDADeviceBackend {

    private final CUDADeviceContextInterface deviceContext;
    private final Function<CUDADeviceContextInterface, CUDABackend> graalFactory;
    private volatile CUDABackend graal;
    private volatile boolean initialised;

    CUDADeviceBackend(CUDADeviceContextInterface deviceContext, Function<CUDADeviceContextInterface, CUDABackend> graalFactory) {
        this.deviceContext = deviceContext;
        this.graalFactory = graalFactory;
    }

    public CUDADeviceContextInterface getDeviceContext() {
        return deviceContext;
    }

    public boolean isInitialised() {
        return initialised;
    }

    /** Allocates the TornadoVM memory regions on the device (once). */
    public synchronized void init() {
        if (initialised) {
            return;
        }
        if (!VIRTUAL_DEVICE_ENABLED) {
            deviceContext.getMemoryManager().allocateDeviceMemoryRegions();
        }
        initialised = true;
    }

    /** The Graal compiler backend of this device, built on first use. */
    public synchronized CUDABackend graal() {
        if (graal == null) {
            init();
            graal = graalFactory.apply(deviceContext);
            graal.markInitialised();
        }
        return graal;
    }
}
