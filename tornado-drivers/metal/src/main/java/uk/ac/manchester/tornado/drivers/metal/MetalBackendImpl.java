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
package uk.ac.manchester.tornado.drivers.metal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalDeviceType;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalHotSpotBackendFactory;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalSuitesProvider;
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalBackend;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public final class MetalBackendImpl implements TornadoAcceleratorBackend {

    private static final List<MetalDeviceType> DEVICE_TYPE_LIST = Arrays.asList( //
            MetalDeviceType.CL_DEVICE_TYPE_GPU, //
            MetalDeviceType.CL_DEVICE_TYPE_CPU, //
            MetalDeviceType.CL_DEVICE_TYPE_ACCELERATOR, //
            MetalDeviceType.CL_DEVICE_TYPE_CUSTOM);
    private final MetalBackend[][] backends;
    private final List<MetalContextInterface> contexts;
    private MetalBackend[] flatBackends;
    private volatile List<TornadoDevice> devices;
    private final TornadoLogger logger;

    public MetalBackendImpl(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        // Only allow Metal backend initialization on macOS hosts.
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("mac") && !osName.contains("darwin")) {
            throw new TornadoBailoutRuntimeException("[WARNING] Metal backend only supported on macOS hosts. Deoptimizing to sequential execution.");
        }

        // Bail out early if native Metal JNI bindings are unavailable in this environment.
        if (!Metal.isNativeAvailable()) {
            throw new TornadoBailoutRuntimeException("[WARNING] Native Metal JNI bindings not available. Deoptimizing to sequential execution.");
        }

        final int numPlatforms = Metal.getNumPlatforms();

        if (numPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[WARNING] No Metal platforms found. Deoptimizing to sequential execution.");
        }

        backends = new MetalBackend[numPlatforms][];
        contexts = new ArrayList<>();
        logger = new TornadoLogger(this.getClass());
        discoverDevices(options, vmRuntime, vmConfig);
        flatBackends = flattenBackends(backends);
        flatBackends = orderFlattenBackends();

    }

    private MetalBackend[] flattenBackends(MetalBackend[][] backends) {
        MetalBackend[] flatBackendList = new MetalBackend[getNumDevices()];
        int index = 0;
        for (int i = 0; i < getNumPlatforms(); i++) {
            for (int j = 0; j < getNumDevices(i); j++, index++) {
                flatBackendList[index] = backends[i][j];
            }
        }
        return flatBackendList;
    }

    /**
     * Orders the flat list of Metal backends based on the provided device type
     * ordering.
     *
     * @return An array of Metal backends ordered according to the device type
     *     ordering.
     */
    private MetalBackend[] orderFlattenBackends() {
        List<MetalBackend> backendList = new ArrayList<>();
        EnumMap<MetalDeviceType, List<MetalBackend>> deviceTypeMap = new EnumMap<>(MetalDeviceType.class);

        // Populate deviceTypeMap with backends for each device type
        for (MetalBackend backend : flatBackends) {
            MetalDeviceType deviceType = backend.getDeviceContext().getDevice().getDeviceType();
            List<MetalBackend> backendListForDeviceType = deviceTypeMap.computeIfAbsent(deviceType, k -> new ArrayList<>());
            backendListForDeviceType.add(backend);
        }

        // Add backends to backendList in the order specified by deviceTypeOrdering
        for (MetalDeviceType deviceType : MetalBackendImpl.DEVICE_TYPE_LIST) {
            List<MetalBackend> backendListForDeviceType = deviceTypeMap.get(deviceType);
            if (backendListForDeviceType != null) {
                backendList.addAll(backendListForDeviceType);
            }
        }

        Map<MetalDeviceType, List<MetalBackend>> groupedByDeviceType = backendList.stream().collect(Collectors.groupingBy(backend -> backend.getDeviceContext().getDevice().getDeviceType()));

        // Sort each sublist by size in descending order
        groupedByDeviceType.forEach((deviceType, sublist) -> Collections.sort(sublist, (backend1, backend2) -> {
            long size1 = backend1.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            long size2 = backend2.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            return Long.compare(size2, size1); // Sort in descending order
        }));

        // Create a list to hold the sorted backends
        List<MetalBackend> sortedBackends = new ArrayList<>();

        // Iterate through 'MetalDriver.DEVICE_TYPE_LIST' and add backends in the
        // specified order
        for (MetalDeviceType deviceType : MetalBackendImpl.DEVICE_TYPE_LIST) {
            List<MetalBackend> backendsOfType = groupedByDeviceType.get(deviceType);
            if (backendsOfType != null) {
                sortedBackends.addAll(backendsOfType);
            }
        }

        // Update 'backendList' with the sorted backends
        backendList = sortedBackends;

        return backendList.toArray(new MetalBackend[0]);
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

    private MetalBackend checkAndInitBackend(final int platform, final int device) {
        final MetalBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private void swapDefaultDevice(final int device) {
        MetalBackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        MetalBackend backend = flatBackends[0];

        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    private MetalBackend createMetalJITCompiler(final OptionValues options, final HotSpotJVMCIRuntime jvmciRuntime, TornadoVMConfigAccess vmConfig, final MetalContextInterface context,
            final int deviceIndex) {
        final MetalTargetDevice device = context.devices().get(deviceIndex);
        logger.info("Creating backend for %s", device.getDeviceName());
        return MetalHotSpotBackendFactory.createJITCompiler(options, jvmciRuntime, vmConfig, context, device);
    }

    private void installDevices(int platformIndex, TornadoPlatformInterface platform, final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        logger.info("Metal[%d]: Platform %s", platformIndex, platform.getName());
        final MetalContextInterface context = platform.createContext();
        assert context != null : "Metal context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        logger.info("Metal[%d]: Has %d devices...", platformIndex, numDevices);
        backends[platformIndex] = new MetalBackend[numDevices];
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            final MetalTargetDevice device = context.devices().get(deviceIndex);
            logger.info("Metal[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][deviceIndex] = createMetalJITCompiler(options, vmRuntime, vmConfig, context, deviceIndex);
        }
    }

    private void discoverDevices(final OptionValues options, final HotSpotJVMCIRuntime vmRuntime, TornadoVMConfigAccess vmConfig) {
        IntStream.range(0, Metal.getNumPlatforms()).forEach(i -> {
            final TornadoPlatformInterface platform = Metal.getPlatform(i);
            installDevices(i, platform, options, vmRuntime, vmConfig);
        });
    }

    public MetalBackend getBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
    }

    @Override
    public MetalBackend getDefaultBackend() {
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

    public MetalContextInterface getPlatformContext(final int index) {
        return (index < contexts.size()) ? contexts.get(index) : contexts.get(0);
    }

    @Override
    public Providers getProviders() {
        return getDefaultBackend().getProviders();
    }

    @Override
    public MetalSuitesProvider getSuitesProvider() {
        return getDefaultBackend().getTornadoSuites();
    }

    @Override
    public String getName() {
        return "Metal";
    }

    @Override
    public TornadoVMBackendType getBackendType() {
        return TornadoVMBackendType.METAL;
    }

    public TornadoDeviceType getTypeDefaultDevice() {
        return getDefaultDevice().getDeviceType();
    }
}
