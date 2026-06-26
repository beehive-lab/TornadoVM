/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.runtime;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.cuda.CUDAContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDAMemFlags;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class CUDABufferProvider extends TornadoBufferProvider {

    public CUDABufferProvider(CUDADeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size, Access access) {
        CUDADeviceContext cudaDeviceContext = (CUDADeviceContext) deviceContext;
        if (cudaDeviceContext.getPlatformContext().isUnifiedMemoryEnabled()) {
            // CUDA Managed (Unified) Memory: cuMemAllocManaged ignores the OpenCL-style
            // access flags (CU_MEM_ATTACH_GLOBAL is applied inside the JNI call) and the
            // allocation is reachable from any processor.
            return cudaDeviceContext.getPlatformContext().createManagedBuffer(size).getBuffer();
        }
        long oclMemFlags = getCUDAMemFlagForAccess(access);
        return cudaDeviceContext.getMemoryManager().createBuffer(size, oclMemFlags).getBuffer();
    }

    /**
     * Device buffers obtained from {@code cuMemAlloc} are not zero-initialised,
     * and TornadoVM reuses pooled device buffers across executions. A
     * {@code WRITE_ONLY} output that the kernel does not fully write (for example,
     * an early-returning kernel) would otherwise read back stale data from a
     * previous allocation, instead of the expected zero.
     *
     * <p>
     * To match the behaviour expected by the runtime (unwritten output buffers
     * read back as zero), we zero the device buffer here, on (re)allocation, when
     * the buffer is handed out from the pool. This happens once per allocation
     * request rather than on every kernel launch, so the performance impact is
     * limited to the allocation path.
     *
     * <p>
     * The zeroing is restricted to {@code WRITE_ONLY} buffers on purpose:
     * <ul>
     * <li>{@code READ_ONLY} / {@code READ_WRITE} buffers receive their initial
     * contents from a host-to-device copy before the kernel runs, so zeroing them
     * is both unnecessary and potentially harmful. For instance, a reduction whose
     * accumulator is an output that is initialised on the host (e.g.
     * {@code result.init(Float.MAX_VALUE)}) and then read-modified-written by the
     * kernel is marked {@code READ_WRITE}; zeroing it would clobber the neutral
     * element.</li>
     * <li>{@code WRITE_ONLY} buffers are pure outputs that the runtime does not
     * copy in from the host. If the kernel skips writing some (or all) of the
     * buffer, the host must observe zeros, not stale pool data.</li>
     * </ul>
     */
    @Override
    public synchronized long getOrAllocateBufferWithSize(long sizeInBytes, Access access) {
        long buffer = super.getOrAllocateBufferWithSize(sizeInBytes, access);
        CUDAContext context = ((CUDADeviceContext) deviceContext).getPlatformContext();
        // cuMemAllocManaged already zero-initialises the allocation, so the explicit
        // cuMemsetD8 (a synchronous op) is redundant when Unified Memory is enabled.
        if (access == Access.WRITE_ONLY && !context.isUnifiedMemoryEnabled()) {
            context.zeroBuffer(buffer, sizeInBytes);
        }
        return buffer;
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((CUDADeviceContext) deviceContext).getMemoryManager().releaseBuffer(buffer);
    }

    private static long getCUDAMemFlagForAccess(Access access) {
        switch (access) {
            case READ_ONLY:
                return CUDAMemFlags.CL_MEM_READ_ONLY;
            case WRITE_ONLY:
                return CUDAMemFlags.CL_MEM_WRITE_ONLY;
            case READ_WRITE:
                return CUDAMemFlags.CL_MEM_READ_WRITE;
            default:
                // if access has not been deducted by sketcher set it as RW
                return CUDAMemFlags.CL_MEM_READ_WRITE;
        }
    }

}
