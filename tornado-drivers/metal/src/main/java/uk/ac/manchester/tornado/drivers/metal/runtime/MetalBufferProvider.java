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
package uk.ac.manchester.tornado.drivers.metal.runtime;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContext;
import uk.ac.manchester.tornado.drivers.metal.enums.MetalMemFlags;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class MetalBufferProvider extends TornadoBufferProvider {

    public MetalBufferProvider(MetalDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public long allocateBuffer(long size, Access access) {
        long oclMemFlags = getMetalMemFlagForAccess(access);
        return ((MetalDeviceContext) deviceContext).getMemoryManager().createBuffer(size, oclMemFlags).getBuffer();
    }

    @Override
    protected void releaseBuffer(long buffer) {
        ((MetalDeviceContext) deviceContext).getMemoryManager().releaseBuffer(buffer);
    }

    private static long getMetalMemFlagForAccess(Access access) {
        switch (access) {
            case READ_ONLY:
                return MetalMemFlags.CL_MEM_READ_ONLY;
            case WRITE_ONLY:
                return MetalMemFlags.CL_MEM_WRITE_ONLY;
            case READ_WRITE:
                return MetalMemFlags.CL_MEM_READ_WRITE;
            default:
                // if access has not been deducted by sketcher set it as RW
                return MetalMemFlags.CL_MEM_READ_WRITE;
        }
    }

}
