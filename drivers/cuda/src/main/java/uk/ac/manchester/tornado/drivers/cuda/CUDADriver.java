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

import java.util.ArrayList;
import java.util.List;

public class CUDADriver extends TornadoLogger implements TornadoAcceleratorDriver {

    private final PTXBackend[] flatBackends;
    private final PTXBackend[][] backends;
    private final List<CUDAContext> contexts;

    public CUDADriver(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = 1; //OpenCL.getNumPlatforms();
        backends = new PTXBackend[numPlatforms][];
        contexts = new ArrayList<>();

        discoverDevices(options, vmRuntime, vmConfig);
        flatBackends = new PTXBackend[getDeviceCount()];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevices(i); j++, index++) {
                flatBackends[index] = backends[i][j];
            }
        }
    }

    protected void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = CUDA.getNumPlatforms();
        if (numPlatforms > 0) {
            String platformToIgnore = getString("tornado.ignore.platform");
            for (int i = 0; i < numPlatforms; i++) {
                final CUDAPlatform platform = CUDA.getPlatform(i);

                if (platformToIgnore != null && platform.getName().startsWith(platformToIgnore)) {
                    info("Ignore " + platform.getName());
                } else {
                    installDevices(i, platform, options, vmRuntime, vmConfig);
                }
            }
        }
    }

    private void installDevices(int platformIndex, CUDAPlatform platform, final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        info("CUDA[%d]: Platform %s", platformIndex, platform.getName());
        final CUDAContext context = platform.createContext();
        assert context != null : "CUDA context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        info("OpenCL[%d]: Has %d devices...", platformIndex, numDevices);

        backends[platformIndex] = new PTXBackend[numDevices];
        for (int j = 0; j < numDevices; j++) {
            //final CUDADevice device = null; //context.devices().get(j);
            //info("CUDA[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][j] = createPTXBackend(options, vmRuntime, vmConfig, context, j);
        }
    }

    private PTXBackend createPTXBackend(final OptionValues options, final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, final CUDAContext context, final int deviceIndex) {
        final CUDADevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getDeviceName());
        return PTXHotSpotBackendFactory.createBackend(options, jvmciRuntime.getHostJVMCIBackend(), vmConfig, context, device);
    }

    private static String getString(String property) {
        if (System.getProperty(property) == null) {
            return null;
        } else {
            return System.getProperty(property);
        }

    }

    public int getNumDevices(int platform) {
        try {
            return backends[platform].length;
        } catch (NullPointerException e) {
            return 0;
        }
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
        return checkAndInitBackend(0, 0);
    }

    private PTXBackend checkAndInitBackend(final int platform, final int device) {
        final PTXBackend backend = backends[platform][device];
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
            return flatBackends[index].getDeviceContext().asMapping();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new TornadoRuntimeException("[ERROR] device required not found: " + index + " - Max: " + flatBackends.length);
        }
    }

    @Override public
    TornadoDeviceType getTypeDefaultDevice() {
        return TornadoDeviceType.GPU;
    }

    @Override public
    String getName() {
        return "CUDA Driver";
    }

    @Override public int
    getNumPlatforms() {
        return 1;
    }

    public PTXBackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }
}
