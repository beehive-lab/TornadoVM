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
package uk.ac.manchester.tornado.drivers.cuda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import tornado.graal.compiler.options.OptionValues;
import tornado.graal.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDASuitesProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.CUDABackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public final class CUDABackendImpl implements TornadoAcceleratorBackend {

    private static final List<CUDADeviceType> DEVICE_TYPE_LIST = Arrays.asList( //
            CUDADeviceType.CL_DEVICE_TYPE_GPU, //
            CUDADeviceType.CL_DEVICE_TYPE_CPU, //
            CUDADeviceType.CL_DEVICE_TYPE_ACCELERATOR, //
            CUDADeviceType.CL_DEVICE_TYPE_CUSTOM);
    private final CUDABackend[][] backends;
    private final List<CUDAContextInterface> contexts;
    private CUDABackend[] flatBackends;
    private volatile List<TornadoDevice> devices;
    private final TornadoLogger logger;

    public CUDABackendImpl(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        final int numPlatforms = CUDADriver.getNumPlatforms();

        if (numPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[WARNING] No CUDADriver platforms found. Deoptimizing to sequential execution.");
        }

        backends = new CUDABackend[numPlatforms][];
        contexts = new ArrayList<>();
        logger = new TornadoLogger(this.getClass());
        discoverDevices(options, vmRuntime, vmConfig);
        flatBackends = flattenBackends(backends);
        flatBackends = orderFlattenBackends();

    }

    private CUDABackend[] flattenBackends(CUDABackend[][] backends) {
        CUDABackend[] flatBackendList = new CUDABackend[getNumDevices()];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevices(i); j++, index++) {
                flatBackendList[index] = backends[i][j];
            }
        }
        return flatBackendList;
    }

    /**
     * Orders the flat list of CUDADriver backends based on the provided device type
     * ordering.
     *
     * @return An array of CUDADriver backends ordered according to the device type
     *     ordering.
     */
    private CUDABackend[] orderFlattenBackends() {
        List<CUDABackend> backendList = new ArrayList<>();
        EnumMap<CUDADeviceType, List<CUDABackend>> deviceTypeMap = new EnumMap<>(CUDADeviceType.class);

        // Populate deviceTypeMap with backends for each device type
        for (CUDABackend backend : flatBackends) {
            CUDADeviceType deviceType = backend.getDeviceContext().getDevice().getDeviceType();
            List<CUDABackend> backendListForDeviceType = deviceTypeMap.computeIfAbsent(deviceType, k -> new ArrayList<>());
            backendListForDeviceType.add(backend);
        }

        // Add backends to backendList in the order specified by deviceTypeOrdering
        for (CUDADeviceType deviceType : CUDABackendImpl.DEVICE_TYPE_LIST) {
            List<CUDABackend> backendListForDeviceType = deviceTypeMap.get(deviceType);
            if (backendListForDeviceType != null) {
                backendList.addAll(backendListForDeviceType);
            }
        }

        Map<CUDADeviceType, List<CUDABackend>> groupedByDeviceType = backendList.stream().collect(Collectors.groupingBy(backend -> backend.getDeviceContext().getDevice().getDeviceType()));

        // Sort each sublist by size in descending order
        groupedByDeviceType.forEach((deviceType, sublist) -> Collections.sort(sublist, (backend1, backend2) -> {
            long size1 = backend1.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            long size2 = backend2.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            return Long.compare(size2, size1); // Sort in descending order
        }));

        // Create a list to hold the sorted backends
        List<CUDABackend> sortedBackends = new ArrayList<>();

        // Iterate through 'CUDADriver.DEVICE_TYPE_LIST' and add backends in the
        // specified order
        for (CUDADeviceType deviceType : CUDABackendImpl.DEVICE_TYPE_LIST) {
            List<CUDABackend> backendsOfType = groupedByDeviceType.get(deviceType);
            if (backendsOfType != null) {
                sortedBackends.addAll(backendsOfType);
            }
        }

        // Update 'backendList' with the sorted backends
        backendList = sortedBackends;

        return backendList.toArray(new CUDABackend[0]);
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

    private CUDABackend checkAndInitBackend(final int platform, final int device) {
        final CUDABackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private void swapDefaultDevice(final int device) {
        CUDABackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        CUDABackend backend = flatBackends[0];

        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    private CUDABackend createCUDAJITCompiler(final OptionValues options, final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfigAccess vmConfig, final CUDAContextInterface context,
            final int deviceIndex) {
        final CUDATargetDevice device = context.devices().get(deviceIndex);
        logger.info("Creating backend for %s", device.getDeviceName());
        return CUDAHotSpotBackendFactory.createJITCompiler(options, jvmciRuntime, vmConfig, context, device);
    }

    private void installDevices(int platformIndex, TornadoPlatformInterface platform, final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        logger.info("CUDADriver[%d]: Platform %s", platformIndex, platform.getName());
        final CUDAContextInterface context = platform.createContext();
        assert context != null : "CUDADriver context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        logger.info("CUDADriver[%d]: Has %d devices...", platformIndex, numDevices);
        backends[platformIndex] = new CUDABackend[numDevices];
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            final CUDATargetDevice device = context.devices().get(deviceIndex);
            logger.info("CUDADriver[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][deviceIndex] = createCUDAJITCompiler(options, vmRuntime, vmConfig, context, deviceIndex);
        }
    }

    private void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        IntStream.range(0, CUDADriver.getNumPlatforms()).forEach(i -> {
            final TornadoPlatformInterface platform = CUDADriver.getPlatform(i);
            installDevices(i, platform, options, vmRuntime, vmConfig);
        });
    }

    public CUDABackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }

    @Override
    public CUDABackend getDefaultBackend() {
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

    public CUDAContextInterface getPlatformContext(final int index) {
        return (index < contexts.size()) ? contexts.get(index) : contexts.get(0);
    }

    @Override
    public Providers getProviders() {
        return getDefaultBackend().getProviders();
    }

    @Override
    public CUDASuitesProvider getSuitesProvider() {
        return getDefaultBackend().getTornadoSuites();
    }

    @Override
    public String getName() {
        return "CUDADriver";
    }

    @Override
    public TornadoVMBackendType getBackendType() {
        return TornadoVMBackendType.CUDA;
    }

    public TornadoDeviceType getTypeDefaultDevice() {
        return getDefaultDevice().getDeviceType();
    }
}
