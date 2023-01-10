/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * Object to create and optimize an execution plan for running a set of
 * immutable tasks-graphs. An executor plan contains an executor object, which
 * in turn, contains a set of immutable task-graphs. All actions applied to the
 * execution plan affect to all the immutable graphs associated with it.
 *
 * @since TornadoVM-0.15
 *
 */
public class TornadoExecutionPlan {

    public static TornadoDevice DEFAULT_DEVICE = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
    private boolean isWarmUp;

    public static TornadoDevice getDevice(int driverIndex, int deviceIndex) {
        return TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getDevice(deviceIndex);
    }

    private final TornadoExecutor tornadoExecutor;

    private GridScheduler gridScheduler;

    private Policy policy = null;

    private boolean useDefaultScheduler;
    private DRMode dynamicReconfigurationMode;
    private ProfilerMode profilerMode;
    private boolean disableProfiler;

    public TornadoExecutionPlan(ImmutableTaskGraph... immutableTaskGraphs) {
        this.tornadoExecutor = new TornadoExecutor(immutableTaskGraphs);
    }

    /**
     * Execute an execution plan. It returns a {@link TornadoExecutionPlan} for
     * further build different optimization after the execution as well as obtain
     * the profiler results.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionResult execute() {

        checkProfilerEnabled();

        if (this.policy != null) {
            tornadoExecutor.executeWithDynamicReconfiguration(this.policy, this.dynamicReconfigurationMode);
        } else if (gridScheduler != null) {
            tornadoExecutor.execute(gridScheduler);
        } else {
            tornadoExecutor.execute();
        }
        return new TornadoExecutionResult(new TornadoProfilerResult(tornadoExecutor));
    }

    private void checkProfilerEnabled() {
        if (this.profilerMode != null && !this.disableProfiler) {
            tornadoExecutor.enableProfiler(profilerMode);
        } else if (this.profilerMode != null) {
            tornadoExecutor.disableProfiler(profilerMode);
        }

    }

    /**
     * It invokes the JIT compiler for all immutable tasks-graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withWarmUp() {
        checkProfilerEnabled();
        tornadoExecutor.warmup();
        return this;
    }

    /**
     * It selects a specific device for all immutable tasks graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDevice(TornadoDevice device) {
        tornadoExecutor.setDevice(device);
        return this;
    }

    /**
     * It obtains the device for a specific immutable task-graph. Note that,
     * ideally, different task immutable task-graph could be executed on different
     * devices.
     *
     * @param immutableTaskGraphIndex
     *            Index of a specific immutable task-graph
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoDevice getDevice(int immutableTaskGraphIndex) {
        return tornadoExecutor.getDevice(immutableTaskGraphIndex);
    }

    /**
     * Test Free device memory (device buffers) associated with all immutable tasks
     * graphs.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan freeDeviceMemory() {
        tornadoExecutor.freeDeviceMemory();
        return this;
    }

    /**
     * Use a {@link GridScheduler} for thread dispatch. The same GridScheduler will
     * be applied to all tasks within the executor. Note that the grid-scheduler API
     * can specify all workers for each task-graph.
     *
     * @param gridScheduler
     *            {@link GridScheduler}
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        return this;
    }

    /**
     * Notify the TornadoVM runtime that utilizes the default thread scheduler.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDefaultScheduler() {
        this.useDefaultScheduler = true;
        tornadoExecutor.useDefaultScheduler(useDefaultScheduler);
        return this;
    }

    /**
     * Notify the TornadoVM runtime that utilizes the default device for the
     * execution.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDefaultDevice() {
        // pending implementation
        return this;
    }

    /**
     * Use the TornadoVM dynamic reconfiguration (akka live task migration) across
     * visible devices.
     *
     * @param policy
     *            {@link Policy}
     * @param mode
     *            {@link DRMode}
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDynamicReconfiguration(Policy policy, DRMode mode) {
        this.policy = policy;
        this.dynamicReconfigurationMode = mode;
        return this;
    }

    /**
     * Enable batch processing. TornadoVM will split the iteration space in smaller
     * batches (with batch size specified by the user). This is used mainly when
     * users want to execute big data applications that do not fit on the device's
     * global memory.
     *
     * @param batchSize
     *            String in the format a number + "MB" Example "512MB".
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withBatch(String batchSize) {
        tornadoExecutor.withBatch(batchSize);
        return this;
    }

    public TornadoExecutionPlan withProfiler(ProfilerMode profilerMode) {
        this.profilerMode = profilerMode;
        disableProfiler = false;
        return this;
    }

    public TornadoExecutionPlan withoutProfiler() {
        this.disableProfiler = true;
        return this;
    }

    /**
     * Invoke the reset device for each device use in previous executions. This
     * clean-up will reset all device buffers.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan resetDevices() {
        tornadoExecutor.resetDevices();
        return this;
    }

    /**
     * Clean all events associated with previous executions.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan clearProfiles() {
        tornadoExecutor.clearProfiles();
        return this;
    }

    static class TornadoExecutor {

        private List<ImmutableTaskGraph> immutableTaskGraphList;

        TornadoExecutor(ImmutableTaskGraph... immutableTaskGraphs) {
            immutableTaskGraphList = new ArrayList<>();
            Collections.addAll(immutableTaskGraphList, immutableTaskGraphs);
        }

        void execute() {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.execute();
            }
        }

        void execute(GridScheduler gridScheduler) {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.execute(gridScheduler);
            }
        }

        void executeWithDynamicReconfiguration(Policy policy, DRMode mode) {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.executeWithDynamicReconfiguration(policy, mode);
            }
        }

        void warmup() {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.warmup();
            }
        }

        void withBatch(String batchSize) {
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
        void setDevice(TornadoDevice device) {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.setDevice(device);
            }
        }

        void freeDeviceMemory() {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.freeDeviceMemory();
            }
        }

        void transferToHost(Object... objects) {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.transferToHost(objects);
            }
        }

        boolean isFinished() {
            boolean result = true;
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                result &= immutableTaskGraph.isFinished();
            }
            return result;
        }

        void resetDevices() {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.resetDevices();
            }
        }

        long getTotalTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getTotalTime()).mapToLong(Long::longValue).sum();
        }

        long getCompileTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getCompileTime()).mapToLong(Long::longValue).sum();
        }

        long getTornadoCompilerTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getTornadoCompilerTime()).mapToLong(Long::longValue).sum();
        }

        long getDriverInstallTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getDriverInstallTime()).mapToLong(Long::longValue).sum();
        }

        long getDataTransfersTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getDataTransfersTime()).mapToLong(Long::longValue).sum();
        }

        long getDeviceWriteTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getDeviceWriteTime()).mapToLong(Long::longValue).sum();
        }

        long getDeviceReadTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getDeviceReadTime()).mapToLong(Long::longValue).sum();
        }

        long getDataTransferDispatchTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getDataTransferDispatchTime()).mapToLong(Long::longValue).sum();
        }

        long getKernelDispatchTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getKernelDispatchTime()).mapToLong(Long::longValue).sum();
        }

        long getDeviceKernelTime() {
            return immutableTaskGraphList.stream().map(itg -> itg.getDeviceKernelTime()).mapToLong(Long::longValue).sum();
        }

        String getProfileLog() {
            return null;
        }

        void dumpProfiles() {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList)
                immutableTaskGraph.dumpProfiles();
        }

        void clearProfiles() {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                immutableTaskGraph.clearProfiles();
            }
        }

        void useDefaultScheduler(boolean useDefaultScheduler) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.useDefaultScheduler(useDefaultScheduler));
        }

        TornadoDevice getDevice(int immutableTaskGraphIndex) {
            if (immutableTaskGraphList.size() < immutableTaskGraphIndex) {
                throw new TornadoRuntimeException("TaskGraph index #" + immutableTaskGraphIndex + " does not exist in current executor");
            }
            return immutableTaskGraphList.get(immutableTaskGraphIndex).getDevice();
        }

        List<Object> getOutputs() {
            List<Object> outputs = new ArrayList<>();
            immutableTaskGraphList.forEach(immutableTaskGraph -> outputs.addAll(immutableTaskGraph.getOutputs()));
            return outputs;
        }

        void enableProfiler(ProfilerMode profilerMode) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.enableProfiler(profilerMode));
        }

        void disableProfiler(ProfilerMode profilerMode) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.disableProfiler(profilerMode));
        }
    }

}
