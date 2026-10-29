/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;


import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;
import uk.ac.manchester.tornado.runtime.common.RuntimeServiceLoader;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoBackends;

public final class TornadoCoreRuntime implements TornadoRuntime {

    private static final TornadoCoreRuntime runtime = new TornadoCoreRuntime();
    private static final JVMMapping JVM = new JVMMapping();
    private static final int DEFAULT_DRIVER = 0;

    private static final int DEFAULT_BACKEND = 0;

    private final TornadoVMConfigAccess vmConfig;
    private final TornadoAcceleratorBackend[] tornadoVMBackends;
    private int backendCount;

    private TornadoCoreRuntime() {
        vmConfig = new TornadoVMConfigAccess();
        tornadoVMBackends = loadBackends();
    }

    public static TornadoCoreRuntime getTornadoRuntime() {
        return runtime;
    }

    public static TornadoVMConfigAccess getVMConfig() {
        return runtime.vmConfig;
    }

    private TornadoAcceleratorBackend[] loadBackends() {
        ServiceLoader<TornadoBackendProvider> loader = RuntimeServiceLoader.load(TornadoBackendProvider.class);
        List<TornadoBackendProvider> installed = StreamSupport.stream(loader.spliterator(), false).sorted().toList();
        List<TornadoBackendProvider> providerList = installed.stream().filter(TornadoCoreRuntime::isEnabled).toList();
        if (providerList.isEmpty() && !installed.isEmpty()) {
            throw new TornadoBackendNotFound("tornado.enabled.backends=" + TornadoOptions.ENABLED_BACKENDS + " selects none of the installed backends: " + installed.stream()
                    .map(p -> p.getDevicePriority().name().toLowerCase(Locale.ROOT)).toList());
        }
        TornadoAcceleratorBackend[] tornadoAcceleratorBackends = new TornadoAcceleratorBackend[TornadoBackends.values().length];
        int index = 0;
        for (TornadoBackendProvider provider : providerList) {
            if (TornadoOptions.FULL_DEBUG) {
                System.out.println("[INFO] TornadoVM Loading Backend: " + provider.getName());
            }
            TornadoAcceleratorBackend backend = provider.createBackend(vmConfig);
            if (backend != null) {
                tornadoAcceleratorBackends[index] = backend;
                index++;
            }
        }
        backendCount = index;
        return tornadoAcceleratorBackends;
    }

    private static boolean isEnabled(TornadoBackendProvider provider) {
        String enabled = TornadoOptions.ENABLED_BACKENDS;
        if (enabled.isBlank()) {
            return true;
        }
        String name = provider.getDevicePriority().name();
        return Arrays.stream(enabled.split(",")).map(String::trim).anyMatch(name::equalsIgnoreCase);
    }

    @Override
    public <D extends TornadoBackend> int getBackendIndex(Class<D> backendClass) {
        for (int backendIndex = 0; backendIndex < tornadoVMBackends.length; backendIndex++) {
            if (tornadoVMBackends[backendIndex] != null && tornadoVMBackends[backendIndex].getClass() == backendClass) {
                return backendIndex;
            }
        }
        throw shouldNotReachHere("Could not find index for backend: " + backendClass);
    }

    @Override
    public boolean isProfilerEnabled() {
        return TornadoOptions.PROFILER_LOGS_ACCUMULATE() && TornadoOptions.isProfilerEnabled();
    }

    @Override
    public TornadoAcceleratorBackend getBackend(int index) {
        if (index < 0 || index >= backendCount || tornadoVMBackends[index] == null) {
            if (backendCount == 0) {
                throw new TornadoBackendNotFound("No TornadoVM backend was loaded: no TornadoBackendProvider is visible from the TornadoVM runtime module");
            }
            throw new TornadoBackendNotFound("Tornado Backend Not Found: index " + index + ", but " + backendCount + " backend(s) loaded");
        }
        return tornadoVMBackends[index];
    }

    @Override
    public void setDefaultBackend(int index) {
        TornadoAcceleratorBackend tmp = tornadoVMBackends[0];
        tornadoVMBackends[0] = tornadoVMBackends[index];
        tornadoVMBackends[index] = tmp;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends TornadoBackend> D getBackend(Class<D> type) {
        for (TornadoAcceleratorBackend backend : tornadoVMBackends) {
            if (backend.getClass() == type) {
                return (D) backend;
            }
        }
        return null;
    }

    @Override
    public TornadoVMBackendType getBackendType(int index) {
        return tornadoVMBackends[index].getBackendType();
    }

    @Override
    public int getNumBackends() {
        return backendCount;
    }

    @Override
    public TornadoXPUDevice getDefaultDevice() {
        return (tornadoVMBackends == null || tornadoVMBackends[DEFAULT_BACKEND] == null) ? JVM : (TornadoXPUDevice) tornadoVMBackends[DEFAULT_BACKEND].getDefaultDevice();
    }

}
