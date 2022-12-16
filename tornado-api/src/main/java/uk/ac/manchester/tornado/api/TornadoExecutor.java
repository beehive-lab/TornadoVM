/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.profiler.ProfileInterface;

public class TornadoExecutor implements ProfileInterface {

    private List<ImmutableTaskGraph> immutableTaskGraphList;

    public TornadoExecutor(ImmutableTaskGraph... immutableTaskGraphs) {
        immutableTaskGraphList = new ArrayList<>();
        Collections.addAll(immutableTaskGraphList, immutableTaskGraphs);
    }

    public TornadoExecutorPlan build() {
        return new TornadoExecutorPlan(this);
    }

    public void execute() {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.execute();
        }
    }

    public void execute(GridScheduler gridScheduler) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.execute(gridScheduler);
        }
    }

    public void executeWithDynamicReconfiguration(Policy policy, DRMode mode) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.executeWithDynamicReconfiguration(policy, mode);
        }
    }

    public void warmup() {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.warmup();
        }
    }

    public void withBatch(String batchSize) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.withBatch(batchSize);
        }
    }

    /**
     * For all task-graphs contained in an Executor, update the device
     *
     * @param device
     *            {@link TornadoDevice} object
     */
    public void setDevice(TornadoDevice device) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.setDevice(device);
        }
    }

    public void lockObjectsInMemory(Object... objects) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.lockObjectsInMemory(objects);
        }
    }

    public void unlockObjectFromMemory(Object... objects) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.unlockObjectFromMemory(objects);
        }
    }

    public void syncObjects(Object... objects) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.syncObject(objects);
        }
    }

    public void syncField(Object object) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.syncField(object);
        }
    }

    public void replaceParameter(Object oldParameter, Object newParameter) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.replaceParameter(oldParameter, newParameter);
        }
    }

    public boolean isFinished() {
        boolean result = true;
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            result &= immutableTaskGraph.isFinished();
        }
        return result;
    }

    public void resetDevices() {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.resetDevices();
        }
    }

    @Override
    public long getTotalTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getTotalTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getCompileTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getCompileTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getTornadoCompilerTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getTornadoCompilerTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getDriverInstallTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getDriverInstallTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getDataTransfersTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getDataTransfersTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getWriteTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getWriteTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getReadTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getReadTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getDataTransferDispatchTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getDataTransferDispatchTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getKernelDispatchTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getKernelDispatchTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getDeviceWriteTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getDeviceWriteTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getDeviceKernelTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getDeviceKernelTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public long getDeviceReadTime() {
        return immutableTaskGraphList.stream().map(itg -> itg.getDeviceReadTime()).mapToLong(Long::longValue).sum();
    }

    @Override
    public String getProfileLog() {
        return null;
    }

    public void dumpProfiles() {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList)
            immutableTaskGraph.dumpProfiles();
    }

    public void clearProfiles() {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.clearProfiles();
        }
    }

    public void useDefaultScheduler(boolean useDefaultScheduler) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.useDefaultScheduler(useDefaultScheduler);
        }
    }

}
