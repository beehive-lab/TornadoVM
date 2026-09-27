/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.graal.phases;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;

import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.phases.Phase;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceDynamicParallelismNotSupported;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.CUDAProgram;
import uk.ac.manchester.tornado.drivers.cuda.ffm.CUDADriverAPI;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDADeviceLaunchNode;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Rejects a kernel that launches kernels from device code ({@code KernelContext.launch}) when the
 * device, driver or toolkit cannot run it. The backend generates CDP2 code (tail-launch and
 * fire-and-forget streams, no device-side synchronisation), which needs CUDA 12.0 or newer in both
 * the NVRTC that compiles it and the driver that loads it, and a device of compute capability 3.5 or
 * higher.
 */
public class CUDADynamicParallelismSupportPhase extends Phase {

    /** CUDA 12.0, encoded as major * 1000 + minor (NVRTC) or major * 1000 + minor * 10 (driver). */
    private static final int CDP2_VERSION_MIN = 12000;
    private static final int DP_MAJOR_MIN = 3;
    private static final int DP_MINOR_MIN = 5;

    private final TornadoDeviceContext deviceContext;

    public CUDADynamicParallelismSupportPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.getNodes().filter(CUDADeviceLaunchNode.class).isEmpty()) {
            return;
        }
        if (!(deviceContext.getDevice() instanceof CUDADevice cudaDevice)) {
            throw new TornadoDeviceDynamicParallelismNotSupported("Launching kernels from the device (KernelContext.launch) needs an NVIDIA CUDA device, got: "
                    + deviceContext.getDevice().getClass().getName());
        }
        int major = cudaDevice.getComputeCapabilityMajor();
        int minor = cudaDevice.getComputeCapabilityMinor();
        if (major < DP_MAJOR_MIN || (major == DP_MAJOR_MIN && minor < DP_MINOR_MIN)) {
            throw new TornadoDeviceDynamicParallelismNotSupported("Launching kernels from the device (KernelContext.launch) needs compute capability " + DP_MAJOR_MIN + "." + DP_MINOR_MIN
                    + " or higher; " + cudaDevice.getDeviceName() + " is " + major + "." + minor + ".");
        }
        int nvrtc = CUDAProgram.getNvrtcVersion();
        if (nvrtc >= 0 && nvrtc < CDP2_VERSION_MIN) {
            throw new TornadoDeviceDynamicParallelismNotSupported("Launching kernels from the device (KernelContext.launch) needs CUDA 12.0 or newer (CUDA Dynamic Parallelism 2); the NVRTC in use is "
                    + (nvrtc / 1000) + "." + (nvrtc % 1000) + ".");
        }
        int driver = driverVersion();
        if (driver >= 0 && driver < CDP2_VERSION_MIN) {
            throw new TornadoDeviceDynamicParallelismNotSupported("Launching kernels from the device (KernelContext.launch) needs a CUDA 12.0 or newer driver (CUDA Dynamic Parallelism 2); the driver supports CUDA "
                    + (driver / 1000) + "." + (driver % 1000 / 10) + ".");
        }
    }

    private static int driverVersion() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = FFMSupport.allocateInt(arena);
            if (CUDADriverAPI.cuDriverGetVersion(version) != CUDADriverAPI.CUDA_SUCCESS) {
                return -1;
            }
            return version.get(FFMSupport.C_INT, 0);
        }
    }
}
