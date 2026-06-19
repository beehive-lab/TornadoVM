/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;
import uk.ac.manchester.tornado.runtime.TornadoVMConfigAccess;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoBackends;

/**
 * Service-provider entry point for the CUDA backend. Discovered by
 * {@link uk.ac.manchester.tornado.runtime.TornadoCoreRuntime} through the
 * {@code java.util.ServiceLoader} via the {@code META-INF/services} descriptor.
 *
 * <p>
 * The CUDA backend generates CUDA C source at runtime, compiles it with NVRTC to
 * PTX, and loads/launches it through the CUDA Driver API.
 * </p>
 */
public class CUDATornadoDriverProvider implements TornadoBackendProvider {

    /**
     * Check {@link TornadoBackendProvider} for documentation on priority.
     */
    private final TornadoBackends priority = TornadoBackends.CUDA;

    @Override
    public String getName() {
        return "CUDA Driver Provider";
    }

    @Override
    public TornadoAcceleratorBackend createBackend(OptionValues options, HotSpotJVMCIRuntime hostRuntime, TornadoVMConfigAccess config) {
        // TODO(Phase 3): return new CUDABackendImpl(options, hostRuntime, config).
        // Until the backend implementation lands, returning null causes the runtime
        // to skip this provider (see TornadoCoreRuntime.loadBackends), keeping the
        // backend registered/discoverable without affecting other backends.
        return null;
    }

    @Override
    public TornadoBackends getDevicePriority() {
        return priority;
    }

    @Override
    public int compareTo(TornadoBackendProvider o) {
        return o.getDevicePriority().value() - priority.value();
    }
}
