/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public final class PTXBackendImpl implements TornadoAcceleratorBackend {

    private final PTXBackend[] backends;
    private final TornadoLogger logger;
    private volatile List<TornadoDevice> devices;

    public PTXBackendImpl(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {

        int deviceCount = PTX.getPlatform().getDeviceCount();
        logger = new TornadoLogger(this.getClass());
        backends = new PTXBackend[deviceCount];
        logger.info("CUDA: Has %d devices...", deviceCount);
        if (deviceCount == 0) {
            throw new TornadoBailoutRuntimeException("[WARNING] No PTX devices found. Deoptimizing to sequential execution.");
        }

        for (int i = 0; i < deviceCount; i++) {
            installDevice(i, options, vmRuntime, vmConfig);
        }
    }

    private void installDevice(int deviceIndex, OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        PTXDevice device = PTX.getPlatform().getDevice(deviceIndex);
        logger.info("Creating backend for %s", device.getDeviceName());
        backends[deviceIndex] = PTXHotSpotBackendFactory.createJITCompiler(options, vmRuntime, vmConfig, device);
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
        return getDefaultBackend().getDeviceContext().toDevice();
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
    public int getNumDevices() {
        return PTX.getPlatform().getDeviceCount();
    }

    @Override
    public TornadoXPUDevice getDevice(int index) {
        if (index < backends.length) {
            return backends[index].getDeviceContext().toDevice();
        } else {
            throw new TornadoDeviceNotFound("[ERROR]-[PTX-DRIVER] Device required not found: " + index + " - Max: " + backends.length);
        }
    }

    @Override
    public List<TornadoDevice> getAllDevices() {
        if (devices == null) {
            synchronized (this) {
                if (devices == null) {
                    devices = new ArrayList<>();
                    for (int i = 0; i < getNumDevices(); i++) {
                        devices.add(backends[i].getDeviceContext().toDevice());
                    }
                }
            }
        }
        return devices;
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
