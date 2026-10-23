/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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


import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
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
    private final OCLDeviceBackend[][] backends;
    private final List<OCLContextInterface> contexts;
    private OCLDeviceBackend[] flatBackends;
    private volatile List<TornadoDevice> devices;
    private final TornadoLogger logger;

    public OCLBackendImpl(TornadoVMConfigAccess vmConfig) {
        final int numPlatforms = OpenCL.getNumPlatforms();

        if (numPlatforms < 1) {
            throw new TornadoBailoutRuntimeException("[WARNING] No OpenCL platforms found. Deoptimizing to sequential execution.");
        }

        backends = new OCLDeviceBackend[numPlatforms][];
        contexts = new ArrayList<>();
        logger = new TornadoLogger(this.getClass());
        discoverDevices(vmConfig);
        flatBackends = flattenBackends(backends);
        flatBackends = orderFlattenBackends();

    }

    private OCLDeviceBackend[] flattenBackends(OCLDeviceBackend[][] backends) {
        OCLDeviceBackend[] flatBackendList = new OCLDeviceBackend[getNumDevices()];
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
    private OCLDeviceBackend[] orderFlattenBackends() {
        List<OCLDeviceBackend> backendList = new ArrayList<>();
        EnumMap<OCLDeviceType, List<OCLDeviceBackend>> deviceTypeMap = new EnumMap<>(OCLDeviceType.class);

        // Populate deviceTypeMap with backends for each device type
        for (OCLDeviceBackend backend : flatBackends) {
            OCLDeviceType deviceType = backend.getDeviceContext().getDevice().getDeviceType();
            List<OCLDeviceBackend> backendListForDeviceType = deviceTypeMap.computeIfAbsent(deviceType, k -> new ArrayList<>());
            backendListForDeviceType.add(backend);
        }

        // Add backends to backendList in the order specified by deviceTypeOrdering
        for (OCLDeviceType deviceType : OCLBackendImpl.DEVICE_TYPE_LIST) {
            List<OCLDeviceBackend> backendListForDeviceType = deviceTypeMap.get(deviceType);
            if (backendListForDeviceType != null) {
                backendList.addAll(backendListForDeviceType);
            }
        }

        Map<OCLDeviceType, List<OCLDeviceBackend>> groupedByDeviceType = backendList.stream().collect(Collectors.groupingBy(backend -> backend.getDeviceContext().getDevice().getDeviceType()));

        // Sort each sublist by size in descending order
        groupedByDeviceType.forEach((deviceType, sublist) -> Collections.sort(sublist, (backend1, backend2) -> {
            long size1 = backend1.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            long size2 = backend2.getDeviceContext().getDevice().getDeviceContext().getDevice().getMaxThreadsPerBlock();
            return Long.compare(size2, size1); // Sort in descending order
        }));

        // Create a list to hold the sorted backends
        List<OCLDeviceBackend> sortedBackends = new ArrayList<>();

        // Iterate through 'OCLDriver.DEVICE_TYPE_LIST' and add backends in the
        // specified order
        for (OCLDeviceType deviceType : OCLBackendImpl.DEVICE_TYPE_LIST) {
            List<OCLDeviceBackend> backendsOfType = groupedByDeviceType.get(deviceType);
            if (backendsOfType != null) {
                sortedBackends.addAll(backendsOfType);
            }
        }

        // Update 'backendList' with the sorted backends
        backendList = sortedBackends;

        return backendList.toArray(new OCLDeviceBackend[0]);
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
                    // Fill a local list and publish it only when complete: readers skip the lock once the field is set
                    List<TornadoDevice> allDevices = new ArrayList<>();
                    for (int deviceIndex = 0; deviceIndex < getNumDevices(); deviceIndex++) {
                        allDevices.add(getDevice(deviceIndex));
                    }
                    devices = Collections.unmodifiableList(allDevices);
                }
            }
        }
        return devices;
    }

    @Override
    public int getNumDevices() {
        return IntStream.range(0, getNumPlatforms()).map(this::getNumDevices).sum();
    }

    private OCLDeviceBackend checkAndInitBackend(final int platform, final int device) {
        final OCLDeviceBackend backend = backends[platform][device];
        if (!backend.isInitialised()) {
            backend.init();
        }

        return backend;
    }

    private void swapDefaultDevice(final int device) {
        OCLDeviceBackend tmp = flatBackends[0];
        flatBackends[0] = flatBackends[device];
        flatBackends[device] = tmp;
        OCLDeviceBackend backend = flatBackends[0];

        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    private OCLDeviceBackend createOCLJITCompiler(TornadoVMConfigAccess vmConfig, final OCLContextInterface context,
            final int deviceIndex) {
        final OCLTargetDevice device = context.devices().get(deviceIndex);
        logger.info("Creating backend for %s", device.getDeviceName());
        var deviceContext = (OCLDeviceContextInterface) context.createDeviceContext(device.getIndex());
        return new OCLDeviceBackend(deviceContext);
    }

    private void installDevices(int platformIndex, TornadoPlatformInterface platform, TornadoVMConfigAccess vmConfig) {
        logger.info("OpenCL[%d]: Platform %s", platformIndex, platform.getName());
        final OCLContextInterface context = platform.createContext();
        assert context != null : "OpenCL context is null";
        contexts.add(context);
        final int numDevices = context.getNumDevices();
        logger.info("OpenCL[%d]: Has %d devices...", platformIndex, numDevices);
        backends[platformIndex] = new OCLDeviceBackend[numDevices];
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            final OCLTargetDevice device = context.devices().get(deviceIndex);
            logger.info("OpenCL[%d]: device=%s", platformIndex, device.getDeviceName());
            backends[platformIndex][deviceIndex] = createOCLJITCompiler(vmConfig, context, deviceIndex);
        }
    }

    private void discoverDevices(TornadoVMConfigAccess vmConfig) {
        IntStream.range(0, OpenCL.getNumPlatforms()).forEach(i -> {
            final TornadoPlatformInterface platform = OpenCL.getPlatform(i);
            installDevices(i, platform, vmConfig);
        });
    }

    /** A device: its context and memory. */
    public OCLDeviceBackend getDeviceBackend(int platform, int device) {
        return checkAndInitBackend(platform, device);
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
