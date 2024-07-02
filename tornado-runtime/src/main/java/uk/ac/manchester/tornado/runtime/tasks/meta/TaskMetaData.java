/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.tasks.meta;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.EVENT_WINDOW;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoEvents;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.runtime.EventSet;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;

public class TaskMetaData extends AbstractMetaData {

    public static final String LOCAL_WORKGROUP_SUFFIX = ".local.workgroup.size";
    public static final String GLOBAL_WORKGROUP_SUFFIX = ".global.workgroup.size";
    protected final Map<TornadoXPUDevice, BitSet> profiles;
    private final byte[] constantData;
    private final ScheduleMetaData scheduleMetaData;
    private final int constantSize;
    protected Access[] argumentsAccess;
    protected DomainTree domain;
    private long[] globalOffset;
    private long[] globalWork;
    private final int localSize;
    private long[] localWork;
    private boolean localWorkDefined;
    private boolean globalWorkDefined;

    public TaskMetaData(ScheduleMetaData scheduleMetaData, String taskID, int numParameters) {
        super(STR."\{scheduleMetaData.getId()}.\{taskID}", scheduleMetaData);
        this.scheduleMetaData = scheduleMetaData;
        this.constantSize = 0;
        this.localSize = 0;
        this.constantData = null;
        profiles = new HashMap<>();
        argumentsAccess = new Access[numParameters];
        Arrays.fill(argumentsAccess, Access.NONE);

        inspectLocalWork();
        inspectGlobalWork();

        // Set the number of threads to run (subset of the input space)
        setNumThreads(scheduleMetaData.getNumThreads());
    }

    public TaskMetaData(ScheduleMetaData scheduleMetaData, String id) {
        this(scheduleMetaData, id, 0);
    }

    public static TaskMetaData create(ScheduleMetaData scheduleMeta, String id, Method method) {
        int numParameters = Modifier.isStatic(method.getModifiers()) ? method.getParameterCount() : method.getParameterCount() + 1;
        return new TaskMetaData(scheduleMeta, id, numParameters);
    }

    private static String formatWorkDimensionArray(final long[] array, final String defaults) {
        final StringBuilder sb = new StringBuilder();
        if (array == null || array.length == 0) {
            sb.append("[").append(defaults).append("]");
        } else {
            sb.append(Arrays.toString(array));
        }
        return sb.toString();
    }

    private static String getProperty(String key) {
        return System.getProperty(key);
    }

    private void inspectLocalWork() {
        localWorkDefined = getProperty(getId() + LOCAL_WORKGROUP_SUFFIX) != null;
        if (localWorkDefined) {
            final String[] values = getProperty(getId() + LOCAL_WORKGROUP_SUFFIX).split(",");
            localWork = new long[values.length];
            for (int i = 0; i < values.length; i++) {
                localWork[i] = Long.parseLong(values[i]);
            }
        }
    }

    private void inspectGlobalWork() {
        globalWorkDefined = getProperty(getId() + GLOBAL_WORKGROUP_SUFFIX) != null;
        if (globalWorkDefined) {
            final String[] values = getProperty(getId() + GLOBAL_WORKGROUP_SUFFIX).split(",");
            globalWork = new long[values.length];
            for (int i = 0; i < values.length; i++) {
                globalWork[i] = Long.parseLong(values[i]);
            }
        }
    }

    public boolean isLocalWorkDefined() {
        return localWorkDefined;
    }

    public boolean isGlobalWorkDefined() {
        return globalWorkDefined;
    }

    public void setLocalWorkToNull() {
        localWork = null;
    }

    public void setLocalWorkToNotDefined() {
        localWorkDefined = false;
    }

    public long[] initLocalWork() {
        localWork = new long[] { 1, 1, 1 };
        return localWork;
    }

    public void addProfile(int id) {
        final TornadoXPUDevice device = getLogicDevice();
        BitSet events;
        profiles.computeIfAbsent(device, k -> new BitSet(EVENT_WINDOW));
        events = profiles.get(device);
        events.set(id);
    }

    @Override
    public boolean enableParallelization() {
        return scheduleMetaData.isEnableParallelizationDefined() && !isEnableParallelizationDefined() ? scheduleMetaData.enableParallelization() : super.enableParallelization();
    }

    @Override
    public String getCpuConfig() {
        if (super.isCpuConfigDefined()) {
            return super.getCpuConfig();
        } else if (!super.isCpuConfigDefined() && scheduleMetaData.isCpuConfigDefined()) {
            return scheduleMetaData.getCpuConfig();
        } else {
            return "";
        }
    }

    public Access[] getArgumentsAccess() {
        return argumentsAccess;
    }

    public byte[] getConstantData() {
        return constantData;
    }

    public int getConstantSize() {
        return constantSize;
    }

    @Override
    public TornadoXPUDevice getLogicDevice() {
        if (scheduleMetaData.isDeviceManuallySet() || (scheduleMetaData.isDeviceDefined() && !isDeviceDefined())) {
            return scheduleMetaData.getLogicDevice();
        }
        return super.getLogicDevice();
    }

    public int getDims() {
        return domain.getDepth();
    }

    public DomainTree getDomain() {
        return domain;
    }

    public void setDomain(final DomainTree value) {

        domain = value;
        Coarseness coarseness = new Coarseness(domain.getDepth());

        final String config = getProperty(getId() + ".coarseness");
        if (config != null && !config.isEmpty()) {
            coarseness.applyConfig(config);
        }

        final int dims = domain.getDepth();
        globalOffset = new long[dims];
        if (!globalWorkDefined) {
            globalWork = new long[dims];
        }
        if (localWorkDefined) {
            guarantee(localWork.length == dims, "task %s has local work dims specified of wrong length", getId());
        } else {
            localWork = initLocalWork();
        }
    }

    public long[] getGlobalOffset() {
        return globalOffset;
    }

    @Override
    public long[] getGlobalWork() {
        return globalWork;
    }

    @Override
    public void setGlobalWork(long[] values) {
        if (globalWorkDefined) {
            return;
        }

        System.arraycopy(values, 0, globalWork, 0, values.length);
        globalWorkDefined = true;
    }

    public int getLocalSize() {
        return localSize;
    }

    @Override
    public long[] getLocalWork() {
        return localWork;
    }

    @Override
    public void setLocalWork(long[] values) {
        localWork = new long[values.length];
        System.arraycopy(values, 0, localWork, 0, values.length);
        localWorkDefined = true;
    }

    @Override
    public String getCompilerFlags() {
        return isOpenclCompilerFlagsDefined() ? super.getCompilerFlags() : scheduleMetaData.getCompilerFlags();
    }

    @Override
    public int getOpenCLGpuBlock2DX() {
        return isOpenclGpuBlock2DXDefined() ? super.getOpenCLGpuBlock2DX() : scheduleMetaData.getOpenCLGpuBlock2DX();
    }

    @Override
    public int getOpenCLGpuBlock2DY() {
        return isOpenclGpuBlock2DXDefined() ? super.getOpenCLGpuBlock2DY() : scheduleMetaData.getOpenCLGpuBlock2DY();
    }

    @Override
    public int getOpenCLGpuBlockX() {
        return isOpenclGpuBlockXDefined() ? super.getOpenCLGpuBlockX() : scheduleMetaData.getOpenCLGpuBlockX();
    }

    @Override
    public List<TornadoEvents> getProfiles(long executionPlanId) {
        final List<TornadoEvents> result = new ArrayList<>(profiles.keySet().size());
        for (TornadoXPUDevice device : profiles.keySet()) {
            result.add(new EventSet(device, profiles.get(device), executionPlanId));
        }
        return result;
    }

    public boolean hasDomain() {
        return domain != null;
    }

    @Override
    public boolean isDebug() {
        return super.isDebug() || scheduleMetaData.isDebug();
    }

    public boolean isParallel() {
        return enableParallelization() && hasDomain() && domain.getDepth() > 0;
    }

    private long[] calculateNumberOfWorkgroupsFromDomain(DomainTree domain) {
        long[] numOfWorkgroups = new long[domain.getDepth()];
        if (globalWork != null && localWork != null) {
            for (int i = 0; i < numOfWorkgroups.length; i++) {
                numOfWorkgroups[i] = globalWork[i] / localWork[i];
            }
        }
        return numOfWorkgroups;
    }

    public void printThreadDims() {
        StringBuilder deviceDebug = new StringBuilder();
        boolean deviceBelongsToPTX = isPTXDevice(getLogicDevice());
        deviceDebug.append(STR."Task info: \{getId()}\n");
        deviceDebug.append(STR."\tBackend           : \{getLogicDevice().getTornadoVMBackend().name()}\n");
        deviceDebug.append(STR."\tDevice            : \{getLogicDevice().getDescription()}\n");
        deviceDebug.append(STR."\tDims              : \{this.isWorkerGridAvailable() ? getWorkerGrid(getId()).dimension() : (hasDomain() ? domain.getDepth() : 0)}\n");
        if (!deviceBelongsToPTX) {
            long[] go = this.isWorkerGridAvailable() ? getWorkerGrid(getId()).getGlobalOffset() : globalOffset;
            deviceDebug.append(STR."\tGlobal work offset: \{formatWorkDimensionArray(go, "0")}\n");
        }
        long[] gw = this.isWorkerGridAvailable() ? getWorkerGrid(getId()).getGlobalWork() : globalWork;
        if (deviceBelongsToPTX) {
            deviceDebug.append(STR."\tThread dimensions : \{formatWorkDimensionArray(gw, "1")}\n");
            deviceDebug.append(STR."\tBlocks dimensions : \{formatWorkDimensionArray(getPTXBlockDim(), "1")}\n");
            deviceDebug.append(STR."\tGrids dimensions  : \{formatWorkDimensionArray(getPTXGridDim(), "1")}\n");
        } else {
            long[] lw = this.isWorkerGridAvailable() ? getWorkerGrid(getId()).getLocalWork() : localWork;
            long[] nw = this.isWorkerGridAvailable() ? getWorkerGrid(getId()).getNumberOfWorkgroups() : (hasDomain() ? calculateNumberOfWorkgroupsFromDomain(domain) : null);
            deviceDebug.append(STR."\tGlobal work size  : \{formatWorkDimensionArray(gw, "1")}\n");
            deviceDebug.append(STR."\tLocal  work size  : \{lw == null ? "null" : formatWorkDimensionArray(lw, "1")}\n");
            deviceDebug.append(STR."\tNumber of workgroups  : \{nw == null ? "null" : formatWorkDimensionArray(nw, "1")}\n");
        }
        System.out.println(deviceDebug);
    }

    public boolean isPTXDevice(TornadoXPUDevice device) {
        return device.getTornadoVMBackend().equals(TornadoVMBackendType.PTX);
    }

    @Override
    public boolean shouldDumpProfiles() {
        return super.shouldDumpProfiles() || scheduleMetaData.shouldDumpProfiles();
    }

    @Override
    public boolean shouldDumpEvents() {
        return super.shouldDumpEvents() || scheduleMetaData.shouldDumpEvents();
    }

    @Override
    public boolean shouldUseOpenCLDriverScheduling() {
        return super.shouldUseOpenCLDriverScheduling() || scheduleMetaData.shouldUseOpenCLDriverScheduling();
    }

    @Override
    public boolean enableThreadCoarsener() {
        return super.enableThreadCoarsener() || scheduleMetaData.enableThreadCoarsener();
    }

    @Override
    public boolean isCpuConfigDefined() {
        return super.isCpuConfigDefined() || scheduleMetaData.isCpuConfigDefined();
    }

    @Override
    public String toString() {
        return String.format("task meta data: domain=%s, global workgroup size=%s%n", domain, (getGlobalWork() == null) ? "null" : formatWorkDimensionArray(getGlobalWork(), "1"));
    }
}
