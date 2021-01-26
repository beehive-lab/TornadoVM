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
package uk.ac.manchester.tornado.drivers.opencl.virtual;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoExecutionHandler;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OCLProgram;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class VirtualOCLDeviceContext extends TornadoLogger implements Initialisable, OCLDeviceContextInterface {

    private final OCLTargetDevice device;
    private final VirtualOCLContext context;

    private boolean wasReset;
    private boolean useRelativeAddresses;
    private boolean printOnce = true;
    private final OCLCodeCache codeCache;

    protected VirtualOCLDeviceContext(OCLTargetDevice device, VirtualOCLContext context) {
        this.device = device;
        this.context = context;
        this.codeCache = new OCLCodeCache(this);

        setRelativeAddressesFlag();
    }

    private void setRelativeAddressesFlag() {
        if (isPlatformFPGA() && !Tornado.OPENCL_USE_RELATIVE_ADDRESSES) {
            useRelativeAddresses = true;
        } else {
            useRelativeAddresses = Tornado.OPENCL_USE_RELATIVE_ADDRESSES;
        }
    }

    public OCLTargetDevice getDevice() {
        return device;
    }

    @Override
    public String getDeviceName() {
        return String.format(device.getDeviceName());
    }

    @Override
    public int getDriverIndex() {
        return TornadoRuntime.getTornadoRuntime().getDriverIndex(OCLDriver.class);
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    public VirtualOCLContext getPlatformContext() {
        return context;
    }

    @Override
    public long getDeviceId() {
        return 0;
    }

    @Override
    public OCLProgram createProgramWithSource(byte[] source, long[] lengths) {
        return null;
    }

    @Override
    public OCLProgram createProgramWithBinary(byte[] binary, long[] lengths) {
        return null;
    }

    @Override
    public OCLMemoryManager getMemoryManager() {
        return null;
    }

    @Override
    public void sync() {}

    @Override
    public void sync(TornadoExecutionHandler handler) {
        sync();
        handler.handle(TornadoExecutionStatus.COMPLETE, null);
    }

    @Override
    public int enqueueBarrier() {
        return 0;
    }

    @Override
    public int enqueueBarrier(int[] events) {
        return 0;
    }

    @Override
    public int enqueueMarker() {
        return 0;
    }

    @Override
    public int enqueueMarker(int[] events) {
        return 0;
    }

    @Override
    public Event resolveEvent(int event) {
        return new EmptyEvent();
    }

    @Override
    public void flushEvents() {}

    @Override
    public boolean isInitialised() {
        return true;
    }

    public void reset() {
        wasReset = true;
    }

    public VirtualOCLTornadoDevice asMapping() {
        return new VirtualOCLTornadoDevice(context.getPlatformIndex(), device.getIndex());
    }

    public String getId() {
        return String.format("opencl-%d-%d", context.getPlatformIndex(), device.getIndex());
    }

    public void dumpEvents() {}

    @Override
    public void flush() {}

    @Override
    public boolean needsBump() {
        return false;
    }

    @Override
    public boolean wasReset() {
        return wasReset;
    }

    @Override
    public void setResetToFalse() {
        wasReset = false;
    }

    @Override
    public boolean isPlatformFPGA() {
        return getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR
                && (getPlatformContext().getPlatform().getName().toLowerCase().contains("fpga") || getPlatformContext().getPlatform().getName().toLowerCase().contains("xilinx"));
    }

    @Override
    public int getDeviceIndex() {
        return device.getIndex();
    }

    @Override
    public int getDevicePlatform() {
        return context.getPlatformIndex();
    }

    @Override
    public boolean useRelativeAddresses() {
        if (isPlatformFPGA() && !Tornado.OPENCL_USE_RELATIVE_ADDRESSES && printOnce) {
            System.out.println("Warning: -Dtornado.opencl.userelative was set to False. TornadoVM changed it to True because it is required for FPGA execution.");
            printOnce = false;
        }

        return useRelativeAddresses;
    }

    public boolean isKernelAvailable() {
        return true;
    }

    public OCLInstalledCode installCode(OCLCompilationResult result) {
        return null;
    }

    public OCLInstalledCode installCode(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        return null;
    }

    public OCLInstalledCode installCode(String id, String entryPoint, byte[] code, boolean shouldCompile) {
        return null;
    }

    public boolean isCached(String id, String entryPoint) {
        return false;
    }

    public OCLInstalledCode getInstalledCode(String id, String entryPoint) {
        return null;
    }

    public OCLCodeCache getCodeCache() {
        return codeCache;
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return false;
    }
}
