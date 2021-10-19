/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackend;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public final class OCLDriver extends TornadoLogger implements TornadoAcceleratorDriver {

    private final OCLBackend[] flatBackends;
    private final OCLBackend[][] backends;
    private final List<OCLExecutionEnvironment> contexts;

    public OCLDriver(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();

        if (numPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[WARNING] No OpenCL platforms found. Deoptimizing to sequential execution.");
        }

        backends = new OCLBackend[numPlatforms][];
        contexts = new ArrayList<>();
        discoverDevices(options, vmRuntime, vmConfig);
        flatBackends = new OCLBackend[getDeviceCount()];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevices(i); j++, index++) {
                flatBackends[index] = backends[i][j];
            }
        }
    }

    @Override
    public TornadoAcceleratorDevice getDefaultDevice() {
        return getDefaultBackend().getDeviceContext().asMapping();
    }

    @Override
    public void setDefaultDevice(int index) {
        swapDefaultDevice(0, index);
    }

    @Override
    public TornadoAcceleratorDevice getDevice(int index) {
        if (index < flatBackends.length) {
            return flatBackends[index].getDeviceContext().asMapping();
        } else {
            throw new TornadoRuntimeException("[ERROR] device required not found: " + index + " - Max: " + flatBackends.length);
        }
    }

    @Override
    public int getDeviceCount() {
        int count = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            count += getNumDevices(i);
        }
        return count;
    }

    private OCLBackend checkAndInitBackend(final int platform, final int device) {
        final OCLBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private OCLBackend swapDefaultDevice(final int platform, final int device) {
        OCLBackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        OCLBackend backend = flatBackends[0];

        if (!backend.isInitialised()) {
            backend.init();
        }
        return backend;
    }

    private OCLBackend createOCLBackend(final OptionValues options, final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, final OCLExecutionEnvironment context, final int deviceIndex) {
        final OCLTargetDevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getDeviceName());
        return OCLHotSpotBackendFactory.createBackend(options, jvmciRuntime, vmConfig, context, device);
    }

    private static String getString(String property) {
        if (System.getProperty(property) == null) {
            return null;
        } else {
            return System.getProperty(property);
        }

    }

    private void installDevices(int platformIndex, TornadoPlatform platform, final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        info("OpenCL[%d]: Platform %s", platformIndex, platform.getName());
        final OCLExecutionEnvironment context = platform.createContext();
        assert context != null : "OpenCL context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        info("OpenCL[%d]: Has %d devices...", platformIndex, numDevices);

        backends[platformIndex] = new OCLBackend[numDevices];
        for (int j = 0; j < numDevices; j++) {
            final OCLTargetDevice device = context.devices().get(j);
            info("OpenCL[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][j] = createOCLBackend(options, vmRuntime, vmConfig, context, j);
        }
    }

    private void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();
        String platformToIgnore = getString("tornado.ignore.platform");
        for (int i = 0; i < numPlatforms; i++) {
            final TornadoPlatform platform = OpenCL.getPlatform(i);
            if (platformToIgnore != null && platform.getName().startsWith(platformToIgnore)) {
                info("Ignore " + platform.getName());
            } else {
                installDevices(i, platform, options, vmRuntime, vmConfig);
            }
        }
    }

    public OCLBackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }

    @Override
    public OCLBackend getDefaultBackend() {
        return checkAndInitBackend(0, 0);
    }

    public OCLBackend swapDefaultBackend(int index) {
        return checkAndInitBackend(0, index);
    }

    public int getNumDevices(int platform) {
        try {
            return backends[platform].length;
        } catch (NullPointerException e) {
            return 0;
        }
    }

    @Override
    public int getNumPlatforms() {
        return backends.length;
    }

    public OCLExecutionEnvironment getPlatformContext(final int index) {
        if (index < contexts.size()) {
            return contexts.get(index);
        } else {
            // We return device 0 by default
            // This only happens if we ignore a platform
            return contexts.get(0);
        }
    }

    @Override
    public Providers getProviders() {
        return getDefaultBackend().getProviders();
    }

    @Override
    public OCLSuitesProvider getSuitesProvider() {
        return getDefaultBackend().getTornadoSuites();
    }

    @Override
    public String getName() {
        return "OpenCL";
    }

    @Override
    public TornadoVMBackend getBackendType() {
        return TornadoVMBackend.OpenCL;
    }

    public TornadoDeviceType getTypeDefaultDevice() {
        return getDefaultDevice().getDeviceType();
    }
}
