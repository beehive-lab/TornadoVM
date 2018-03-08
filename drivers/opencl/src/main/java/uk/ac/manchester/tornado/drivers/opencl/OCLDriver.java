/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.common.TornadoLogger;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;

public final class OCLDriver extends TornadoLogger implements TornadoDriver {

    private final OCLBackend[] flatBackends;
    private final OCLBackend[][] backends;
    private final List<OCLContext> contexts;

    public OCLDriver(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();
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
    public TornadoDevice getDefaultDevice() {
        return getDefaultBackend().getDeviceContext().asMapping();
    }

    @Override
    public TornadoDevice getDevice(int index) {
        return flatBackends[index].getDeviceContext().asMapping();
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

    private OCLBackend createOCLBackend(final OptionValues options,
            final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, final OCLContext context,
            final int deviceIndex) {
        final OCLDevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getName());
        return OCLHotSpotBackendFactory.createBackend(options, jvmciRuntime.getHostJVMCIBackend(), vmConfig, context, device);
    }

    protected void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();
        if (numPlatforms > 0) {
            for (int i = 0; i < numPlatforms; i++) {
                final OCLPlatform platform = OpenCL.getPlatform(i);
                
                info("OpenCL[%d]: Platform %s", i, platform.getName());
                final OCLContext context = platform.createContext();
                contexts.add(context);
                final int numDevices = context.getNumDevices();
                info("OpenCL[%d]: Has %d devices...", i, numDevices);

                backends[i] = new OCLBackend[numDevices];

                for (int j = 0; j < numDevices; j++) {
                    final OCLDevice device = context.devices().get(j);
                    info("OpenCL[%d]: device=%s", i, device.getName());
                    backends[i][j] = createOCLBackend(options, vmRuntime, vmConfig, context, j);
                }
            }
        } else {
        	throw new RuntimeException("There is no OpenCL Platform available");
        }
    }

    public OCLBackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }

    public OCLBackend getDefaultBackend() {
        return checkAndInitBackend(0, 0);
    }

    public int getNumDevices(int platform) {
        return backends[platform].length;
    }

    public int getNumPlatforms() {
        return backends.length;
    }

    public OCLContext getPlatformContext(final int index) {
        return contexts.get(index);
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
        return "OpenCL Driver";
    }
}
