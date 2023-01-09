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

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;

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

    private final TornadoExecutor tornadoExecutor;

    private GridScheduler gridScheduler;

    private Policy policy = null;

    private boolean useDefaultScheduler;
    private DRMode dynamicReconfigurationMode;
    private ProfilerMode profilerMode;
    private boolean disableProfiler;

    TornadoExecutionPlan(TornadoExecutor tornadoExecutor) {
        this.tornadoExecutor = tornadoExecutor;
    }

    /**
     * Execute an execution plan. It returns a {@link TornadoExecutionPlan} for
     * further build different optimization after the execution as well as obtain
     * the profiler results.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionResult execute() {

        if (this.profilerMode != null && !this.disableProfiler) {
            tornadoExecutor.enableProfiler(profilerMode);
        } else if (this.profilerMode != null && this.disableProfiler) {
            tornadoExecutor.disableProfiler(profilerMode);
        }

        if (this.policy != null) {
            tornadoExecutor.executeWithDynamicReconfiguration(this.policy, this.dynamicReconfigurationMode);
        } else if (gridScheduler != null) {
            tornadoExecutor.execute(gridScheduler);
        } else {
            tornadoExecutor.execute();
        }
        return new TornadoExecutionResult(tornadoExecutor.getOutputs(), new TornadoProfilerResult(tornadoExecutor));
    }

    /**
     * It invokes the JIT compiler for all immutable tasks-graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withWarmUp() {
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

}
