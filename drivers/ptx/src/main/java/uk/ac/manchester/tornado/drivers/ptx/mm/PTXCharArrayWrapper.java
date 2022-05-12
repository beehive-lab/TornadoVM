/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.ptx.mm;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;

public class PTXCharArrayWrapper extends PTXArrayWrapper<char[]> {
    public PTXCharArrayWrapper(PTXDeviceContext deviceContext) {
        super(deviceContext, JavaKind.Char);
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param address
     *            Device Buffer ID
     * @param bytes
     *            Bytes to be copied back to the host
     * @param value
     *            Host array that resides the final data
     * @param hostOffset
     * @param waitEvents
     *            List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueReadArrayData(long address, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int readArrayData(long address, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    /**
     * Copy data that resides in the host to the target device.
     *
     * @param address
     *            Device Buffer ID
     * @param bytes
     *            Bytes to be copied
     * @param value
     *            Host array to be copied
     * @param hostOffset
     * @param waitEvents
     *            List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueWriteArrayData(long address, long bytes, char[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long address, long bytes, char[] value, int hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(address, bytes, value, hostOffset, waitEvents);
    }
}
