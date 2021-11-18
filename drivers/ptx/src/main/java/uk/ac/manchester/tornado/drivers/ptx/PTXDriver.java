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
 */

package uk.ac.manchester.tornado.drivers.ptx;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public final class PTXDriver extends TornadoLogger implements TornadoAcceleratorDriver {

    private final PTXBackend[] backends;

    public PTXDriver(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {

        int deviceCount = PTX.getPlatform().getDeviceCount();
        backends = new PTXBackend[deviceCount];
        info("CUDA: Has %d devices...", deviceCount);
        if (deviceCount == 0) {
            throw new TornadoBailoutRuntimeException("[WARNING] No PTX devices found. Deoptimizing to sequential execution.");
        }

        for (int i = 0; i < deviceCount; i++) {
            installDevice(i, options, vmRuntime, vmConfig);
        }
    }

    private void installDevice(int deviceIndex, OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        PTXDevice device = PTX.getPlatform().getDevice(deviceIndex);
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

    @Override
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
    public void setDefaultDevice(int index) {
        swapDefaultDevice(index);
    }

    private PTXBackend swapDefaultDevice(final int device) {
        PTXBackend tmp = backends[0];
        backends[0] = backends[device];
        backends[device] = tmp;
        PTXBackend backend = backends[0];
        if (!backend.isInitialised()) {
            backend.init();
        }
        return backend;
    }

    @Override
    public int getDeviceCount() {
        return PTX.getPlatform().getDeviceCount();
    }

    @Override
    public TornadoAcceleratorDevice getDevice(int index) {
        if (index < backends.length) {
            return backends[index].getDeviceContext().asMapping();
        } else {
            throw new TornadoRuntimeException("[ERROR]-[PTX-DRIVER] Device required not found: " + index + " - Max: " + backends.length);
        }
    }

    @Override
    public TornadoDeviceType getTypeDefaultDevice() {
        return TornadoDeviceType.GPU;
    }

    @Override
    public String getName() {
        return "PTX";
    }

    @Override
    public TornadoVMBackendType getBackendType() {
        return TornadoVMBackendType.PTX;
    }

    @Override
    public int getNumPlatforms() {
        return 1;
    }

    public PTXBackend getBackend(int device) {
        return checkAndInitBackend(device);
    }
}
