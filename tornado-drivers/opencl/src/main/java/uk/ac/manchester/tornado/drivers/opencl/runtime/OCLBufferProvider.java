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
package uk.ac.manchester.tornado.drivers.opencl.runtime;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLMemFlags;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class OCLBufferProvider extends TornadoBufferProvider {

    public OCLBufferProvider(OCLDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size, Access access) {
        long flag = getOCLMemFlagForAccess(access);
        return ((OCLDeviceContext) deviceContext).getMemoryManager().createBuffer(size, flag).getBuffer();
    }

    @Override
    public long allocateSubBuffer(long parentBuffer, long offset, long size, Access access) {
        long flag = getOCLMemFlagForAccess(access);
        return ((OCLDeviceContext) deviceContext).getMemoryManager().createSubBuffer(parentBuffer, offset, size, flag);
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((OCLDeviceContext) deviceContext).getMemoryManager().releaseBuffer(buffer);
    }

    private long getOCLMemFlagForAccess(Access access) {
        switch (access) {
            case READ_ONLY:
                return OCLMemFlags.CL_MEM_READ_ONLY;
            case WRITE_ONLY:
                return OCLMemFlags.CL_MEM_WRITE_ONLY;
            case READ_WRITE:
                return OCLMemFlags.CL_MEM_READ_WRITE;
            default:
                // if access has not been deducted by sketcher set it as RW
                return OCLMemFlags.CL_MEM_READ_WRITE;
        }
    }

}
