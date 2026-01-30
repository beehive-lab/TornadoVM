/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVHotSpotBackendFactory;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public final class SPIRVBackendImpl implements TornadoAcceleratorBackend {

    /**
     * Matrix of backend instances. Each row has a driver implementation (e.g.,
     * Level Zero). Each column represents a device within that backend.
     */
    private final SPIRVBackend[][] spirvBackends;

    private final List<SPIRVPlatform> platforms;

    /**
     * Flat representation of the backends
     */
    private final SPIRVBackend[] flatBackends;

    private final TornadoLogger logger;

    private final HashMap<SPIRVDevice, SPIRVBackend> backendPerDevice;

    /**
     * Total number of devices available (include all backends platform and
     * devices).
     */
    private int backendCounter;
    private volatile List<TornadoDevice> devices;

    public SPIRVBackendImpl(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfigAccess) {
        int numPlatforms = SPIRVRuntimeImpl.getInstance().getNumPlatforms();
        logger = new TornadoLogger(this.getClass());
        logger.info("[SPIR-V] Found %d platforms", numPlatforms);

        if (numPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[ERROR] No SPIR-V platforms found. Deoptimizing to sequential execution");
        }
        platforms = new ArrayList<>();
        spirvBackends = new SPIRVBackend[numPlatforms][];
        backendPerDevice = new HashMap<>();

        discoverDevices(options, vmRuntime, vmConfigAccess, numPlatforms);

        flatBackends = new SPIRVBackend[backendCounter];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevicesForPlatform(i); j++, index++) {
                flatBackends[index] = spirvBackends[i][j];
            }
        }

    }

    private void discoverDevices(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmCon, int numPlatforms) {
        for (int platformIndex = 0; platformIndex < numPlatforms; platformIndex++) {
            SPIRVPlatform platform = SPIRVRuntimeImpl.getInstance().getPlatform(platformIndex);
            platforms.add(platform);
            SPIRVContext context = platform.createContext();
            int numDevices = platform.getNumDevices();

            // Since a platform can have more than one device and not all of them must be SPIR-V supported,
            // we need to filter again to only add those that support SPIR-V >= 1.2.
            List<SPIRVBackend> backendImplementations = new ArrayList<>();
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                SPIRVDevice device = platform.getDevice(deviceIndex);
                if (device.isSPIRVSupported()) {
                    SPIRVBackend backend = createSPIRVJITCompilerBackend(options, vmRuntime, vmCon, device, context, device.getSPIRVRuntime());
                    backendPerDevice.put(device, backend);
                    backendImplementations.add(backend);
                    backendCounter++;
                }
            }
            spirvBackends[platformIndex] = backendImplementations.toArray(new SPIRVBackend[0]);
        }
    }

    private SPIRVBackend createSPIRVJITCompilerBackend(OptionValues options, HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig, SPIRVDevice device, SPIRVContext context,
            SPIRVRuntimeType spirvRuntime) {
        return SPIRVHotSpotBackendFactory.createJITCompiler(options, vmRuntime, vmConfig, device, context, spirvRuntime);
    }

    private SPIRVBackend checkAndInitBackend(int platformIndex, int deviceIndex) {
        SPIRVBackend backend = spirvBackends[platformIndex][deviceIndex];
        if (!backend.isInitialised()) {
            logger.info("SPIR-V Backend Initialization");
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
        return getDefaultBackend().getDeviceContext().toDevice();
    }

    @Override
    public void setDefaultDevice(int index) {
        swapDefaultDevice(index);
    }

    private void swapDefaultDevice(final int device) {
        SPIRVBackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        SPIRVBackend backend = flatBackends[0];
        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    private int getNumDevicesForPlatform(int platform) {
        try {
            return spirvBackends[platform].length;
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public int getNumDevices() {
        return backendCounter;
    }

    @Override
    public TornadoXPUDevice getDevice(int index) {
        if (index < flatBackends.length) {
            return flatBackends[index].getDeviceContext().toDevice();
        } else {
            throw new TornadoDeviceNotFound("[ERROR]-[SPIRV-DRIVER] Device required not found: " + index + " - Max: " + spirvBackends.length);
        }
    }

    @Override
    public List<TornadoDevice> getAllDevices() {
        if (devices == null) {
            synchronized (this) {
                if (devices == null) {
                    devices = new ArrayList<>();
                    for (int i = 0; i < getNumDevices(); i++) {
                        devices.add(flatBackends[i].getDeviceContext().toDevice());
                    }
                }
            }
        }
        return devices;
    }

    @Override
    public TornadoDeviceType getTypeDefaultDevice() {
        return getDefaultDevice().getDeviceType();
    }

    @Override
    public int getNumPlatforms() {
        return spirvBackends.length;
    }

    @Override
    public TornadoVMBackendType getBackendType() {
        return TornadoVMBackendType.SPIRV;
    }

    public SPIRVBackend getBackend(SPIRVRuntimeType port) {
        for (SPIRVBackend[] spirvBackend : spirvBackends) {
            for (SPIRVBackend backend : spirvBackend) {
                SPIRVRuntimeType spirvRuntime = backend.getDeviceContext().device.getSPIRVRuntime();
                if (spirvRuntime.equals(port)) {
                    return backend;
                }
            }
        }
        return null;
    }

    public SPIRVBackend getBackendOfDevice(SPIRVDevice device) {
        return backendPerDevice.get(device);
    }

    public List<SPIRVPlatform> getPlatforms() {
        return this.platforms;
    }

    @Override
    public String getName() {
        return "SPIR-V";
    }
}
