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
import java.util.stream.Collectors;

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

    /**
     * Method to obtain the default device in TornadoVM. The default one corresponds
     * to the device assigned to the driver (backend) with index 0 and device 0.
     */
    public static TornadoDevice DEFAULT_DEVICE = TornadoRuntime.getTornadoRuntime().getDefaultDevice();

    /**
     * Method to obtain a specific device using the driver index (backend index) and
     * device index.
     *
     * @param driverIndex
     *            Integer value that identifies the backend to be used.
     * @param deviceIndex
     *            Integer value that identifies the device within the backend to be
     *            used.
     * @return {@link TornadoDevice}
     */
    public static TornadoDevice getDevice(int driverIndex, int deviceIndex) {
        return TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getDevice(deviceIndex);
    }

    private final TornadoExecutor tornadoExecutor;

    private GridScheduler gridScheduler;

    private Policy policy = null;

    private DRMode dynamicReconfigurationMode;
    private ProfilerMode profilerMode;
    private boolean disableProfiler;

    /**
     * Create an Execution Plan: Object to create and optimize an execution plan for
     * running a set of immutable tasks-graphs. An executor plan contains an
     * executor object, which in turn, contains a set of immutable task-graphs. All
     * actions applied to the execution plan affect to all the immutable graphs
     * associated with it.
     *
     * @param immutableTaskGraphs
     *            {@link ImmutableTaskGraph}
     */
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
     * Mark all device buffers that correspond to the current execution plan as free
     * in order for the TornadoVM runtime system to reuse those buffers and avoid
     * continuous device memory deallocation and allocation.
     *
     * <p>
     * Note that, in this context, "free device memory" means the TornadoVM runtime
     * system marks device buffers to be reusable, thus, for the runtime system,
     * device buffers are no longer linked to the current execution plan.
     * </p>
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
        tornadoExecutor.useDefaultScheduler(true);
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

    /**
     * Enables the profiler. The profiler includes options to query device kernel
     * time, data transfers and compilation at different stages (JIT, driver
     * compilation, Graal, etc).
     *
     * @param profilerMode
     *            {@link ProfilerMode}
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withProfiler(ProfilerMode profilerMode) {
        this.profilerMode = profilerMode;
        disableProfiler = false;
        return this;
    }

    /**
     * Disables the profiler if previous execution plan had the profiler enabled.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutProfiler() {
        this.disableProfiler = true;
        return this;
    }

    /**
     * Reset the execution context for the current execution plan. The TornadoVM
     * runtime system will clean the code cache and all events associated with the
     * current execution. It resets the internal GPU/FPGA/CPU execution context to
     * its default values.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan resetDevice() {
        tornadoExecutor.resetDevice();
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
            immutableTaskGraphList.forEach(ImmutableTaskGraph::execute);
        }

        void execute(GridScheduler gridScheduler) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.execute(gridScheduler));
        }

        void executeWithDynamicReconfiguration(Policy policy, DRMode mode) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.executeWithDynamicReconfiguration(policy, mode));
        }

        void warmup() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::warmup);
        }

        void withBatch(String batchSize) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withBatch(batchSize));
        }

        /**
         * For all task-graphs contained in an Executor, update the device
         *
         * @param device
         *            {@link TornadoDevice} object
         */
        void setDevice(TornadoDevice device) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.setDevice(device));
        }

        void freeDeviceMemory() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::freeDeviceMemory);
        }

        void transferToHost(Object... objects) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.transferToHost(objects));
        }

        boolean isFinished() {
            boolean result = true;
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                result &= immutableTaskGraph.isFinished();
            }
            return result;
        }

        void resetDevice() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::resetDevice);
        }

        long getTotalTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getTotalTime).mapToLong(Long::longValue).sum();
        }

        long getCompileTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getCompileTime).mapToLong(Long::longValue).sum();
        }

        long getTornadoCompilerTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getTornadoCompilerTime).mapToLong(Long::longValue).sum();
        }

        long getDriverInstallTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDriverInstallTime).mapToLong(Long::longValue).sum();
        }

        long getDataTransfersTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDataTransfersTime).mapToLong(Long::longValue).sum();
        }

        long getDeviceWriteTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDeviceWriteTime).mapToLong(Long::longValue).sum();
        }

        long getDeviceReadTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDeviceReadTime).mapToLong(Long::longValue).sum();
        }

        long getDataTransferDispatchTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDataTransferDispatchTime).mapToLong(Long::longValue).sum();
        }

        long getKernelDispatchTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getKernelDispatchTime).mapToLong(Long::longValue).sum();
        }

        long getDeviceKernelTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDeviceKernelTime).mapToLong(Long::longValue).sum();
        }

        String getProfileLog() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getProfileLog).collect(Collectors.joining());
        }

        void dumpProfiles() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::dumpProfiles);
        }

        void clearProfiles() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::clearProfiles);
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
