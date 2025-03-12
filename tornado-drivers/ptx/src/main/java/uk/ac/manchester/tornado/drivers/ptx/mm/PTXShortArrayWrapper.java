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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

public class PTXShortArrayWrapper extends PTXArrayWrapper<short[]> {
    private long setSubRegionSize;

    public PTXShortArrayWrapper(PTXDeviceContext deviceContext, Access access) {
        super(deviceContext, JavaKind.Short, access);
    }

    /**
     * Copy data from the device to the main host.
     *
     * @param address
     *     Device Buffer address
     * @param bytes
     *     Bytes to be copied back to the host
     * @param value
     *     Host array that resides the final data
     * @param hostOffset
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueReadArrayData(long executionPlanId, long address, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueReadBuffer(executionPlanId, address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected int readArrayData(long executionPlanId, long address, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.readBuffer(executionPlanId, address, bytes, value, hostOffset, waitEvents);
    }

    /**
     * Copy data that resides in the host to the target device.
     *
     * @param address
     *     Device Buffer address
     * @param bytes
     *     Bytes to be copied
     * @param value
     *     Host array to be copied
     * @param hostOffset
     * @param waitEvents
     *     List of events to wait for.
     * @return Event information
     */
    @Override
    protected int enqueueWriteArrayData(long executionPlanId, long address, long bytes, short[] value, long hostOffset, int[] waitEvents) {
        return deviceContext.enqueueWriteBuffer(executionPlanId, address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    protected void writeArrayData(long executionPlanId, long address, long bytes, short[] value, int hostOffset, int[] waitEvents) {
        deviceContext.writeBuffer(executionPlanId, address, bytes, value, hostOffset, waitEvents);
    }

    @Override
    public long getSizeSubRegionSize() {
        return setSubRegionSize;
    }

    @Override
    public void setSizeSubRegion(long batchSize) {
        this.setSubRegionSize = batchSize;
    }

    @Override
    public int[] getIntBuffer() {
        return super.getIntBuffer();
    }

    @Override
    public void setIntBuffer(int[] arr) {
        super.setIntBuffer(arr);
    }

    @Override
    public int getSizeOfType() {
        return PTXKind.B16.getSizeInBytes();
    }
}
