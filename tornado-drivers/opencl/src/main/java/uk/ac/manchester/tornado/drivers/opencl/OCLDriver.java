/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
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

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoVMConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class OCLDriver extends TornadoLogger implements TornadoAcceleratorDriver {
    public static final List<OCLDeviceType> DEVICE_TYPE_LIST = Arrays.asList( //
            OCLDeviceType.CL_DEVICE_TYPE_GPU, //
            OCLDeviceType.CL_DEVICE_TYPE_CPU, //
            OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR, //
            OCLDeviceType.CL_DEVICE_TYPE_CUSTOM);
    private OCLBackend[] flatBackends;
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
        flatBackends = flattenBackends(backends);
        flatBackends = orderFlattenBackends(DEVICE_TYPE_LIST);
    }

    private OCLBackend[] flattenBackends(OCLBackend[][] backends) {
        OCLBackend[] flatBackendList = new OCLBackend[getDeviceCount()];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevices(i); j++, index++) {
                flatBackendList[index] = backends[i][j];
            }
        }
        return flatBackendList;
    }

    private static String getString(String property) {
        return System.getProperty(property) == null ? null : System.getProperty(property);
    }

    /**
     * Orders the flat list of OpenCL backends based on the provided device type ordering.
     *
     * @param deviceTypeOrdering
     *         A list of OpenCL device types in the desired order.
     * @return An array of OpenCL backends ordered according to the device type ordering.
     */
    private OCLBackend[] orderFlattenBackends(List<OCLDeviceType> deviceTypeOrdering) {
        List<OCLBackend> backendList = new ArrayList<>();
        Map<OCLDeviceType, List<OCLBackend>> deviceTypeMap = new HashMap<>();

        // Populate deviceTypeMap with backends for each device type
        for (OCLBackend backend : flatBackends) {
            OCLDeviceType deviceType = backend.getDeviceContext().getDevice().getDeviceType();
            List<OCLBackend> backendListForDeviceType = deviceTypeMap.get(deviceType);
            if (backendListForDeviceType == null) {
                backendListForDeviceType = new ArrayList<>();
                deviceTypeMap.put(deviceType, backendListForDeviceType);
            }
            backendListForDeviceType.add(backend);
        }

        // Add backends to backendList in the order specified by deviceTypeOrdering
        for (OCLDeviceType deviceType : deviceTypeOrdering) {
            List<OCLBackend> backendListForDeviceType = deviceTypeMap.get(deviceType);
            if (backendListForDeviceType != null) {
                backendList.addAll(backendListForDeviceType);
            }
        }

        return backendList.toArray(new OCLBackend[0]);
    }

    @Override
    public TornadoAcceleratorDevice getDefaultDevice() {
        return flatBackends[0].getDeviceContext().asMapping();
    }

    @Override
    public void setDefaultDevice(int index) {
        swapDefaultDevice(index);
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
        return IntStream.range(0, getNumPlatforms()).map(this::getNumDevices).sum();
    }

    private OCLBackend checkAndInitBackend(final int platform, final int device) {
        final OCLBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private OCLBackend swapDefaultDevice(final int device) {
        OCLBackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        OCLBackend backend = flatBackends[0];

        if (!backend.isInitialised()) {
            backend.init();
        }
        return backend;
    }

    private OCLBackend createOCLJITCompiler(final OptionValues options, final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfig vmConfig, final OCLExecutionEnvironment context,
            final int deviceIndex) {
        final OCLTargetDevice device = context.devices().get(deviceIndex);
        info("Creating backend for %s", device.getDeviceName());
        return OCLHotSpotBackendFactory.createJITCompiler(options, jvmciRuntime, vmConfig, context, device);
    }

    private void installDevices(int platformIndex, TornadoPlatform platform, final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        info("OpenCL[%d]: Platform %s", platformIndex, platform.getName());
        final OCLExecutionEnvironment context = platform.createContext();
        assert context != null : "OpenCL context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        info("OpenCL[%d]: Has %d devices...", platformIndex, numDevices);
        backends[platformIndex] = new OCLBackend[numDevices];
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            final OCLTargetDevice device = context.devices().get(deviceIndex);
            info("OpenCL[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][deviceIndex] = createOCLJITCompiler(options, vmRuntime, vmConfig, context, deviceIndex);
        }
    }

    private void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfig vmConfig) {
        IntStream.range(0, OpenCL.getNumPlatforms()).forEach(i -> {
            final TornadoPlatform platform = OpenCL.getPlatform(i);
            installDevices(i, platform, options, vmRuntime, vmConfig);
        });
    }

    public OCLBackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }

    @Override
    public OCLBackend getDefaultBackend() {
        return checkAndInitBackend(0, 0);
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
        return (index < contexts.size()) ? contexts.get(index) : contexts.get(0);
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
    public TornadoVMBackendType getBackendType() {
        return TornadoVMBackendType.OPENCL;
    }

    public TornadoDeviceType getTypeDefaultDevice() {
        return getDefaultDevice().getDeviceType();
    }
}
