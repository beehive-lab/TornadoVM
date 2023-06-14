/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVHotSpotBackendFactory;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public final class SPIRVDriver extends TornadoLogger implements TornadoAcceleratorDriver {

    /**
     * Matrix of backend instances. Each row has a driver implementation (e.g.,
     * Level Zero). Each column represents a device within that backend.
     */
    private final SPIRVBackend[][] backends;

    /**
     * Flat representation of the backends
     */
    private final SPIRVBackend[] flatBackends;

    /**
     * Total number of devices available (include all backends platform and
     * devices).
     */
    private int deviceCount;

    public SPIRVDriver(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmCon) {
        int numSPIRVPlatforms = SPIRVProxy.getNumPlatforms();
        TornadoLogger.info("[SPIRV] Found %d platforms", numSPIRVPlatforms);

        if (numSPIRVPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[Warning] No SPIRV platforms found. Deoptimizing to sequential execution");
        }

        backends = new SPIRVBackend[numSPIRVPlatforms][];
        discoverDevices(options, vmRuntime, vmCon, numSPIRVPlatforms);
        flatBackends = new SPIRVBackend[deviceCount];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevicesForPlatform(i); j++, index++) {
                flatBackends[index] = backends[i][j];
            }
        }
    }

    private void discoverDevices(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmCon, int numPlatforms) {
        for (int platformIndex = 0; platformIndex < numPlatforms; platformIndex++) {
            SPIRVPlatform platform = SPIRVProxy.getPlatform(platformIndex);
            SPIRVContext context = platform.createContext();
            int numDevices = platform.getNumDevices();
            backends[platformIndex] = new SPIRVBackend[numDevices];
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                SPIRVDevice device = platform.getDevice(deviceIndex);
                backends[platformIndex][deviceIndex] = createSPIRVJITCompiler(options, vmRuntime, vmCon, device, context);
            }
        }
        deviceCount = getNumDevices();
    }

    private SPIRVBackend createSPIRVJITCompiler(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig, SPIRVDevice device, SPIRVContext context) {
        return SPIRVHotSpotBackendFactory.createJITCompiler(options, vmRuntime, vmConfig, device, context);
    }

    private SPIRVBackend checkAndInitBackend(int platformIndex, int deviceIndex) {
        SPIRVBackend backend = backends[platformIndex][deviceIndex];
        if (!backend.isInitialised()) {
            Tornado.info("SPIRV Backend Initialization");
            backend.init();
        }
        return backend;
    }

    @Override
    public SPIRVBackend getDefaultBackend() {
        return checkAndInitBackend(0, 0);
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
    public TornadoDevice getDefaultDevice() {
        return getDefaultBackend().getDeviceContext().asMapping();
    }

    @Override
    public void setDefaultDevice(int index) {
    }

    private int getNumDevicesForPlatform(int platform) {
        try {
            return backends[platform].length;
        } catch (NullPointerException e) {
            return 0;
        }
    }

    private int getNumDevices() {
        int count = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            count += getNumDevicesForPlatform(i);
        }
        return count;
    }

    @Override
    public int getDeviceCount() {
        return deviceCount;
    }

    @Override
    public TornadoAcceleratorDevice getDevice(int index) {
        if (index < flatBackends.length) {
            return flatBackends[index].getDeviceContext().asMapping();
        } else {
            throw new TornadoRuntimeException("[ERROR]-[SPIRV-DRIVER] Device required not found: " + index + " - Max: " + backends.length);
        }
    }

    @Override
    public TornadoDeviceType getTypeDefaultDevice() {
        return getDefaultDevice().getDeviceType();
    }

    @Override
    public String getName() {
        return "SPIRV";
    }

    @Override
    public int getNumPlatforms() {
        return backends.length;
    }

    @Override
    public TornadoVMBackendType getBackendType() {
        return TornadoVMBackendType.SPIRV;
    }

    public SPIRVBackend getBackend(int platformIndex, int deviceIndex) {
        return checkAndInitBackend(platformIndex, deviceIndex);
    }
}
