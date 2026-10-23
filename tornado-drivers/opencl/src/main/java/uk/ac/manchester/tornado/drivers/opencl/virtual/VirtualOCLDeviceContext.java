/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import uk.ac.manchester.tornado.drivers.opencl.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLMemoryManager;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class VirtualOCLDeviceContext implements OCLDeviceContextInterface {

    private final OCLTargetDevice device;
    private final VirtualOCLContext context;
    private final OCLCodeCache codeCache;
    /** Execution plans whose device state has been torn down; see TornadoDeviceContext. */
    private final java.util.Set<Long> resetPlans = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        resetPlans.add(executionPlanId);
    }

    @Override
    public VirtualOCLTornadoDevice toDevice() {
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
    public boolean wasReset(long executionPlanId) {
        return resetPlans.contains(executionPlanId);
    }

    @Override
    public void setResetToFalse(long executionPlanId) {
        resetPlans.remove(executionPlanId);
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
    public boolean isKernelAvailable(long executionPlanId) {
        return true;
    }

    @Override
    public OCLInstalledCode installCode(long executionPlanId, TaskDataContext meta, String id, String entryPoint, byte[] code) {
        return null;
    }

    @Override
    public boolean isCached(long executionPlanId, String id, String entryPoint) {
        return false;
    }

    @Override
    public OCLInstalledCode getInstalledCode(long executionPlanId, String id, String entryPoint) {
        return null;
    }

    @Override
    public OCLCodeCache getCodeCache(long executionPlanId) {
        return codeCache;
    }

    @Override
    public boolean isCached(long executionPlanId, String methodName, SchedulableTask task) {
        return false;
    }

}
