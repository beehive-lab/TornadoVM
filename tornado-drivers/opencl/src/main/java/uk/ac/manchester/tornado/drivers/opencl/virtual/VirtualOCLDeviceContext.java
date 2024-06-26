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
package uk.ac.manchester.tornado.drivers.opencl.virtual;

import java.util.Set;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.common.TornadoBufferProvider;
import uk.ac.manchester.tornado.drivers.opencl.OCLBackendImpl;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLProgram;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class VirtualOCLDeviceContext implements OCLDeviceContextInterface {

    private final OCLTargetDevice device;
    private final VirtualOCLContext context;
    private final OCLCodeCache codeCache;
    private boolean wasReset;

    protected VirtualOCLDeviceContext(OCLTargetDevice device, VirtualOCLContext context) {
        this.device = device;
        this.context = context;
        this.codeCache = new OCLCodeCache(this);
        device.setDeviceContext(this);
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
        return TornadoRuntimeProvider.getTornadoRuntime().getBackendIndex(OCLBackendImpl.class);
    }

    @Override
    public Set<Long> getRegisteredPlanIds() {
        return Set.of();
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
    public OCLProgram createProgramWithIL(byte[] binary, long[] lengths) {
        return null;
    }

    @Override
    public OCLMemoryManager getMemoryManager() {
        return null;
    }

    @Override
    public TornadoBufferProvider getBufferProvider() {
        return null;
    }

    @Override
    public void sync(long executionPlanId) {
    }

    @Override
    public int enqueueBarrier(long executionPlanId) {
        return 0;
    }

    @Override
    public int enqueueBarrier(long executionPlanId, int[] events) {
        return 0;
    }

    @Override
    public int enqueueMarker(long executionPlanId) {
        return 0;
    }

    @Override
    public int enqueueMarker(long executionPlanId, int[] events) {
        return 0;
    }

    @Override
    public Event resolveEvent(long executionPlanId, int event) {
        return new EmptyEvent();
    }

    @Override
    public void flushEvents(long executionPlanId) {
    }

    @Override
    public void reset(long executionPlanId) {
        wasReset = true;
    }

    @Override
    public VirtualOCLTornadoDevice asMapping() {
        return new VirtualOCLTornadoDevice(context.getPlatformIndex(), device.getIndex());
    }

    public String getId() {
        return String.format("opencl-%d-%d", context.getPlatformIndex(), device.getIndex());
    }

    @Override
    public void dumpEvents() {
    }

    @Override
    public void flush(long executionPlanId) {
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
        return this.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR && (getPlatformContext().getPlatform().getName().toLowerCase().contains("fpga") || isPlatformXilinxFPGA());
    }

    @Override
    public boolean isPlatformXilinxFPGA() {
        return getPlatformContext().getPlatform().getName().toLowerCase().contains("xilinx");
    }

    @Override
    public boolean isFP64Supported() {
        return device.isDeviceDoubleFPSupported();
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
    public boolean isKernelAvailable() {
        return true;
    }

    @Override
    public OCLInstalledCode installCode(OCLCompilationResult result) {
        return null;
    }

    @Override
    public OCLInstalledCode installCode(TaskMetaData meta, String id, String entryPoint, byte[] code) {
        return null;
    }

    @Override
    public OCLInstalledCode installCode(String id, String entryPoint, byte[] code, boolean printKernel) {
        return null;
    }

    @Override
    public boolean isCached(String id, String entryPoint) {
        return false;
    }

    @Override
    public OCLInstalledCode getInstalledCode(String id, String entryPoint) {
        return null;
    }

    @Override
    public OCLCodeCache getCodeCache() {
        return codeCache;
    }

    @Override
    public boolean isCached(String methodName, SchedulableTask task) {
        return false;
    }

}
