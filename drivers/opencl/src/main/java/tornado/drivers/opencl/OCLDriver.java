/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl;

import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import tornado.common.TornadoDevice;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.graal.OCLHotSpotBackendFactory;
import tornado.drivers.opencl.graal.OCLSuitesProvider;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.runtime.TornadoDriver;
import tornado.runtime.TornadoVMConfig;

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

    private OCLBackend checkAndInitBackend(final int platform,
            final int device) {
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

                    backends[i][j] = createOCLBackend(options, vmRuntime, vmConfig,
                            context, j);

                }
            }
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
