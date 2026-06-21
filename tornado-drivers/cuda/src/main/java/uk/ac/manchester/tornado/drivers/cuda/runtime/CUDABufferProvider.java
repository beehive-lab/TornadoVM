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
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDAMemFlags;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class CUDABufferProvider extends TornadoBufferProvider {

    public CUDABufferProvider(CUDADeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size, Access access) {
        long oclMemFlags = getCUDAMemFlagForAccess(access);
        return ((CUDADeviceContext) deviceContext).getMemoryManager().createBuffer(size, oclMemFlags).getBuffer();
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
     * This method is only reached for buffers (re)allocated from the pool; locked
     * / persisted buffers are reused directly and never pass through here, so
     * zeroing here can never clobber data that the runtime intends to keep
     * resident on the device.
     *
     * <p>
     * The zeroing is applied to {@code WRITE_ONLY} and {@code READ_ONLY} buffers,
     * and skipped only for {@code READ_WRITE}:
     * <ul>
     * <li>{@code WRITE_ONLY} buffers are pure outputs the runtime does not copy in
     * from the host. If the kernel skips writing some (or all) of the buffer, the
     * host must observe zeros, not stale pool data.</li>
     * <li>{@code READ_ONLY} buffers normally receive their contents from a
     * host-to-device copy that runs right after this allocation, in which case the
     * zeroing is simply overwritten. However, the access deducer can mark a
     * pure-output array as {@code READ_ONLY} (e.g. an early-returning kernel that
     * only writes through a conditional), and in that case no host copy populates
     * the buffer — so it must be zeroed to avoid leaking stale pool data.</li>
     * <li>{@code READ_WRITE} is left untouched: it always receives a host-to-device
     * copy before use, and zeroing could clobber a host-initialised accumulator
     * (e.g. a reduction seeded with {@code result.init(Float.MAX_VALUE)}) on paths
     * where the copy is elided.</li>
     * </ul>
     */
    @Override
    public synchronized long getOrAllocateBufferWithSize(long sizeInBytes, Access access) {
        long buffer = super.getOrAllocateBufferWithSize(sizeInBytes, access);
        if (access == Access.WRITE_ONLY || access == Access.READ_ONLY) {
            ((CUDADeviceContext) deviceContext).getPlatformContext().zeroBuffer(buffer, sizeInBytes);
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
