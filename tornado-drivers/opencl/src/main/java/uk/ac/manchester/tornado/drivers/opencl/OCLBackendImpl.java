/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2024, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public final class OCLBackendImpl implements TornadoAcceleratorBackend {

    private static final List<OCLDeviceType> DEVICE_TYPE_LIST = Arrays.asList( //
            OCLDeviceType.CL_DEVICE_TYPE_GPU, //
            OCLDeviceType.CL_DEVICE_TYPE_CPU, //
            OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR, //
            OCLDeviceType.CL_DEVICE_TYPE_CUSTOM);
    private final OCLBackend[][] backends;
    private final List<OCLContextInterface> contexts;
    private OCLBackend[] flatBackends;
    private volatile List<TornadoDevice> devices;
    private final TornadoLogger logger;

    public OCLBackendImpl(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();

        if (numPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[WARNING] No OpenCL platforms found. Deoptimizing to sequential execution.");
        }

        backends = new OCLBackend[numPlatforms][];
        contexts = new ArrayList<>();
        logger = new TornadoLogger(this.getClass());
        discoverDevices(options, vmRuntime, vmConfig);
        flatBackends = flattenBackends(backends);
        flatBackends = orderFlattenBackends();

    }

    private OCLBackend[] flattenBackends(OCLBackend[][] backends) {
        OCLBackend[] flatBackendList = new OCLBackend[getNumDevices()];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevices(i); j++, index++) {
                flatBackendList[index] = backends[i][j];
            }
        }
        return flatBackendList;
    }

    /**
     * Orders the flat list of OpenCL backends based on the provided device type
     * ordering.
     *
     * @return An array of OpenCL backends ordered according to the device type
     *     ordering.
     */
    private OCLBackend[] orderFlattenBackends() {
        List<OCLBackend> backendList = new ArrayList<>();
        EnumMap<OCLDeviceType, List<OCLBackend>> deviceTypeMap = new EnumMap<>(OCLDeviceType.class);

        // Populate deviceTypeMap with backends for each device type
        for (OCLBackend backend : flatBackends) {
            OCLDeviceType deviceType = backend.getDeviceContext().getDevice().getDeviceType();
            List<OCLBackend> backendListForDeviceType = deviceTypeMap.computeIfAbsent(deviceType, k -> new ArrayList<>());
            backendListForDeviceType.add(backend);
        }

        // Add backends to backendList in the order specified by deviceTypeOrdering
        for (OCLDeviceType deviceType : OCLBackendImpl.DEVICE_TYPE_LIST) {
            List<OCLBackend> backendListForDeviceType = deviceTypeMap.get(deviceType);
            if (backendListForDeviceType != null) {
                backendList.addAll(backendListForDeviceType);
            }
        }

        Map<OCLDeviceType, List<OCLBackend>> groupedByDeviceType = backendList.stream().collect(Collectors.groupingBy(backend -> backend.getDeviceContext().getDevice().getDeviceType()));

        // Sort each sublist by size in descending order
        groupedByDeviceType.forEach((deviceType, sublist) -> Collections.sort(sublist, (backend1, backend2) -> {
            long size1 = backend1.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            long size2 = backend2.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            return Long.compare(size2, size1); // Sort in descending order
        }));

        // Create a list to hold the sorted backends
        List<OCLBackend> sortedBackends = new ArrayList<>();

        // Iterate through 'OCLDriver.DEVICE_TYPE_LIST' and add backends in the
        // specified order
        for (OCLDeviceType deviceType : OCLBackendImpl.DEVICE_TYPE_LIST) {
            List<OCLBackend> backendsOfType = groupedByDeviceType.get(deviceType);
            if (backendsOfType != null) {
                sortedBackends.addAll(backendsOfType);
            }
        }

        // Update 'backendList' with the sorted backends
        backendList = sortedBackends;

        return backendList.toArray(new OCLBackend[0]);
    }

    @Override
    public TornadoXPUDevice getDefaultDevice() {
        return flatBackends[0].getDeviceContext().toDevice();
    }

    @Override
    public void setDefaultDevice(int index) {
        swapDefaultDevice(index);
    }

    @Override
    public TornadoXPUDevice getDevice(int index) {
        if (index < flatBackends.length) {
            return flatBackends[index].getDeviceContext().toDevice();
        } else {
            throw new TornadoDeviceNotFound("[ERROR] device required not found: " + index + " - Max: " + flatBackends.length);
        }
    }

    @Override
    public List<TornadoDevice> getAllDevices() {
        if (devices == null) {
            synchronized (this) {
                if (devices == null) {
                    devices = new ArrayList<>();
                    for (int deviceIndex = 0; deviceIndex < getNumDevices(); deviceIndex++) {
                        devices.add(getDevice(deviceIndex));
                    }
                }
            }
        }
        return devices;
    }

    @Override
    public int getNumDevices() {
        return IntStream.range(0, getNumPlatforms()).map(this::getNumDevices).sum();
    }

    private OCLBackend checkAndInitBackend(final int platform, final int device) {
        final OCLBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private void swapDefaultDevice(final int device) {
        OCLBackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        OCLBackend backend = flatBackends[0];

        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    private OCLBackend createOCLJITCompiler(final OptionValues options, final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfigAccess vmConfig, final OCLContextInterface context,
            final int deviceIndex) {
        final OCLTargetDevice device = context.devices().get(deviceIndex);
        logger.info("Creating backend for %s", device.getDeviceName());
        return OCLHotSpotBackendFactory.createJITCompiler(options, jvmciRuntime, vmConfig, context, device);
    }

    private void installDevices(int platformIndex, TornadoPlatformInterface platform, final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        logger.info("OpenCL[%d]: Platform %s", platformIndex, platform.getName());
        final OCLContextInterface context = platform.createContext();
        assert context != null : "OpenCL context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        logger.info("OpenCL[%d]: Has %d devices...", platformIndex, numDevices);
        backends[platformIndex] = new OCLBackend[numDevices];
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            final OCLTargetDevice device = context.devices().get(deviceIndex);
            logger.info("OpenCL[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][deviceIndex] = createOCLJITCompiler(options, vmRuntime, vmConfig, context, deviceIndex);
        }
    }

    private void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        IntStream.range(0, OpenCL.getNumPlatforms()).forEach(i -> {
            final TornadoPlatformInterface platform = OpenCL.getPlatform(i);
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

    public OCLContextInterface getPlatformContext(final int index) {
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
