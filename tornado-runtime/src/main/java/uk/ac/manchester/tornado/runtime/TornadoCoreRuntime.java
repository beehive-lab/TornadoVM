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

import java.util.List;
import java.util.ServiceLoader;

import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.api.internal.options.OptionDiagnostic;
import uk.ac.manchester.tornado.api.internal.options.OptionRegistry;
import uk.ac.manchester.tornado.api.internal.options.OptionResolver;
import uk.ac.manchester.tornado.api.options.InvalidOptionValueException;
import uk.ac.manchester.tornado.api.options.RuntimeFlags;
import uk.ac.manchester.tornado.runtime.common.ProfilerActivation;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

public final class TornadoCoreRuntime implements TornadoRuntime {

    private static final TornadoCoreRuntime runtime = new TornadoCoreRuntime();
    private static final JVMMapping JVM = new JVMMapping();

    private final TornadoVMConfigAccess vmConfig;
    private final Backends backends;

    private TornadoCoreRuntime() {
        reportOptionProblems();
        vmConfig = new TornadoVMConfigAccess();
        List<TornadoBackendProvider> installed = ServiceLoader.load(TornadoBackendProvider.class).stream().map(ServiceLoader.Provider::get).toList();
        backends = new Backends(installed, RuntimeFlags.ENABLED_BACKENDS.get(), this::createBackend);
    }

    /**
     * Warns about every {@code -Dtornado.*} option that is unknown (with the closest existing one), removed,
     * deprecated, or set under both its names; then parses every option that is set, so that a bad value stops
     * TornadoVM here rather than when the option is first read.
     *
     * @throws InvalidOptionValueException
     *     for the first option set to a value its type does not accept, after every such option is reported
     */
    private static void reportOptionProblems() {
        OptionResolver resolver = OptionResolver.global();
        OptionRegistry registry = OptionRegistry.catalog();
        List<String> allowed = RuntimeFlags.OPTIONS_ALLOW.find().map(list -> List.of(list.split(","))).orElse(List.of());
        for (OptionDiagnostic diagnostic : registry.diagnose(resolver.source(), allowed)) {
            // The resolver warns about a deprecated key when it reads it; report each once.
            resolver.reportOnce(diagnostic.key(), diagnostic.message());
        }
        List<InvalidOptionValueException> errors = registry.validate(resolver.source());
        if (!errors.isEmpty()) {
            errors.forEach(error -> System.err.println("[TornadoVM] ERROR: " + error.getMessage()));
            throw errors.get(0);
        }
    }

    private TornadoAcceleratorBackend createBackend(TornadoBackendProvider provider) {
        if (DebugFlags.FULL_DEBUG.get()) {
            System.out.println("[INFO] TornadoVM Loading Backend: " + provider.getName());
        }
        return provider.createBackend(vmConfig);
    }

    public static TornadoCoreRuntime getTornadoRuntime() {
        return runtime;
    }

    public static TornadoVMConfigAccess getVMConfig() {
        return runtime.vmConfig;
    }

    @Override
    public <D extends TornadoBackend> int getBackendIndex(Class<D> backendClass) {
        int index = backends.indexOf(backendClass);
        if (index < 0) {
            throw shouldNotReachHere("Could not find index for backend: " + backendClass);
        }
        return index;
    }

    @Override
    public boolean isProfilerEnabled() {
        return ProfilerActivation.keepsReport() && ProfilerActivation.isEnabled();
    }

    @Override
    public TornadoAcceleratorBackend getBackend(int index) {
        return backends.get(index);
    }

    @Override
    public void setDefaultBackend(int index) {
        backends.makeDefault(index);
    }

    @Override
    public <D extends TornadoBackend> D getBackend(Class<D> type) {
        int index = backends.indexOf(type);
        return index < 0 ? null : type.cast(backends.get(index));
    }

    @Override
    public TornadoVMBackendType getBackendType(int index) {
        return backends.get(index).getBackendType();
    }

    @Override
    public int getNumBackends() {
        return backends.count();
    }

    @Override
    public TornadoXPUDevice getDefaultDevice() {
        return backends.count() == 0 ? JVM : (TornadoXPUDevice) backends.get(0).getDefaultDevice();
    }

}
