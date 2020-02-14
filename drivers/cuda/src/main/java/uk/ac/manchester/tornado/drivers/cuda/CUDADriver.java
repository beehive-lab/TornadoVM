/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.drivers.cuda;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public class CUDADriver extends TornadoLogger implements TornadoAcceleratorDriver {

    private final PTXBackend[] backends;

    public CUDADriver(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {

        int deviceCount = CUDA.getPlatform().getDeviceCount();
        backends = new PTXBackend[deviceCount];
        info("CUDA: Has %d devices...", deviceCount);

        for (int i = 0; i < deviceCount; i++) {
            installDevice(i, options, vmRuntime, vmConfig);
        }
    }

    private void installDevice(int deviceIndex, OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        CUDADevice device = CUDA.getPlatform().getDevice(deviceIndex);
        info("Creating backend for %s", device.getDeviceName());
        backends[deviceIndex] = PTXHotSpotBackendFactory.createBackend(options, vmRuntime, vmConfig, device);
    }

    @Override
    public Providers getProviders() {
        return getDefaultBackend().getProviders();
    }

    @Override
    public TornadoSuitesProvider getSuitesProvider() {
        return getDefaultBackend().getTornadoSuites();
    }

    public PTXBackend getDefaultBackend() {
        return checkAndInitBackend(0);
    }

    private PTXBackend checkAndInitBackend(final int device) {
        final PTXBackend backend = backends[device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    @Override
    public TornadoDevice getDefaultDevice() {
        return getDefaultBackend().getDeviceContext().asMapping();
    }

    @Override
    public int getDeviceCount() {
        return 1;
    }

    @Override
    public TornadoAcceleratorDevice getDevice(int index) {
        try {
            return backends[index].getDeviceContext().asMapping();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new TornadoRuntimeException("[ERROR] device required not found: " + index + " - Max: " + backends.length);
        }
    }

    @Override
    public TornadoDeviceType getTypeDefaultDevice() {
        return TornadoDeviceType.GPU;
    }

    @Override
    public String getName() {
        return "CUDA Driver";
    }

    @Override
    public int getNumPlatforms() {
        return 1;
    }

    public PTXBackend getBackend(int device) {
        return checkAndInitBackend(device);
    }
}
