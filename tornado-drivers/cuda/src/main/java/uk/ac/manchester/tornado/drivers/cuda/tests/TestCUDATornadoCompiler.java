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
package uk.ac.manchester.tornado.drivers.cuda.tests;

import uk.ac.manchester.tornado.drivers.cuda.CUDABackendImpl;
import uk.ac.manchester.tornado.drivers.cuda.CUDACodeCache;
import uk.ac.manchester.tornado.drivers.cuda.CUDAContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDAPlatform;
import uk.ac.manchester.tornado.drivers.cuda.CUDADriver;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.CUDABackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResult;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class TestCUDATornadoCompiler {

    // NVRTC compiles CUDA C, not OpenCL C, so the kernel must use CUDA spellings
    // (extern "C" __global__, blockIdx/blockDim/threadIdx) rather than __kernel /
    // __global / get_global_id, otherwise NVRTC rejects line 1 with
    // "this declaration has no storage class or type specifier".
    // @formatter:off
    private static final String CUDA_KERNEL = "extern \"C\" __global__ void saxpy(float *a," +
            "    float *b, float *c) { " +
            "      int idx = blockIdx.x * blockDim.x + threadIdx.x; " +
            "      c[idx]  =  a[idx] + b[idx]; " +
            "}";
    // @formatter:on

    public static void main(String[] args) {

        CUDAPlatform platform = (CUDAPlatform) CUDADriver.getPlatform(0);
        // Create context for the platform
        CUDAContext oclContext = platform.createContext();

        // Create command queue
        oclContext.createCommandQueue(0);

        // 1. Compile the code:
        CUDADeviceContext deviceContext = oclContext.createDeviceContext(0);
        CUDACodeCache codeCache = new CUDACodeCache(deviceContext);

        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();
        CUDABackend backend = tornadoRuntime.getBackend(CUDABackendImpl.class).getDefaultBackend();
        ScheduleContext scheduleMeta = new ScheduleContext("oclbackend");
        TaskDataContext meta = new TaskDataContext(scheduleMeta, "saxpy");
        new CUDACompilationResult("internal", "saxpy", meta, backend);

        byte[] source = CUDA_KERNEL.getBytes();
        CUDAInstalledCode code = codeCache.installSource(meta, "saxpy", "saxpy", source);

        String generatedSourceCode = code.getGeneratedSourceCode();
        if (meta.isPrintKernelEnabled()) {
            System.out.println("Compiled code: " + generatedSourceCode);
        }
    }
}
