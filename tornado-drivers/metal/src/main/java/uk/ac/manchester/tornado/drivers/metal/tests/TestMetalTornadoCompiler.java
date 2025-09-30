/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
 *
 */
package uk.ac.manchester.tornado.drivers.metal.tests;

import uk.ac.manchester.tornado.drivers.metal.MetalBackendImpl;
import uk.ac.manchester.tornado.drivers.metal.MetalCodeCache;
import uk.ac.manchester.tornado.drivers.metal.MetalContext;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.MetalPlatform;
import uk.ac.manchester.tornado.drivers.metal.Metal;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalBackend;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResult;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class TestMetalTornadoCompiler {

    // @formatter:off
    private static final String METAL_KERNEL = "kernel void saxpy(device float *a, device float *b, device float *c, uint3 _thread_position_in_grid [[thread_position_in_grid]]) {" +
        "  uint idx = _thread_position_in_grid.x; " +
        "  c[idx] = a[idx] + b[idx]; " +
        "}";
    // @formatter:on

    public static void main(String[] args) {

        MetalPlatform platform = (MetalPlatform) Metal.getPlatform(0);
        // Create context for the platform
        MetalContext oclContext = platform.createContext();

        // Create command queue
        oclContext.createCommandQueue(0);

        // 1. Compile the code:
        MetalDeviceContext deviceContext = oclContext.createDeviceContext(0);
        MetalCodeCache codeCache = new MetalCodeCache(deviceContext);

        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();
        MetalBackend backend = tornadoRuntime.getBackend(MetalBackendImpl.class).getDefaultBackend();
        ScheduleContext scheduleMeta = new ScheduleContext("oclbackend");
        TaskDataContext meta = new TaskDataContext(scheduleMeta, "saxpy");
        new MetalCompilationResult("internal", "saxpy", meta, backend);

        byte[] source = METAL_KERNEL.getBytes();
        MetalInstalledCode code = codeCache.installSource(meta, "saxpy", "saxpy", source);

        String generatedSourceCode = code.getGeneratedSourceCode();
        if (meta.isPrintKernelEnabled()) {
            System.out.println("Compiled code: " + generatedSourceCode);
        }
    }
}
