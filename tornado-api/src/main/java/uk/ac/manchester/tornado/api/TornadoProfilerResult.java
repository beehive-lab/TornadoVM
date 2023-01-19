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

import uk.ac.manchester.tornado.api.TornadoExecutionPlan.TornadoExecutor;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.profiler.ProfileInterface;

/**
 * Object that stores all information related to profiling an executor. To be
 * able to return all timers, developers must enable the profiler from the
 * command line using the option:
 *
 * <p>
 * <code>
 * --enableProfiler <console|silent>
 * </code>
 * </p>
 *
 * <p>
 * Alternatively, the profiler can be enabled using the
 * {@link TornadoExecutionPlan#withProfiler(ProfilerMode)}.
 * </p>
 *
 * @since TornadoVM-0.15
 */
public class TornadoProfilerResult implements ProfileInterface {
    private TornadoExecutor executor;

    public TornadoProfilerResult(TornadoExecutor executor) {
        this.executor = executor;
    }

    /**
     * Returns the end-to-end time for all immutable task-graph to execute.
     *
     * @return long
     */
    @Override
    public long getTotalTime() {
        return executor.getTotalTime();
    }

    /**
     * Returns the JIT Compilation time for all immutable task-graphs associated to
     * the executor.
     *
     * @return long
     */
    @Override
    public long getCompileTime() {
        return executor.getCompileTime();
    }

    /**
     * Returns Tornado JIT Compilation time, in ns, (from Java bc to final step in
     * Graal LIR) for all immutable task-graphs associated to the executor.
     *
     * @return long
     */
    @Override
    public long getTornadoCompilerTime() {
        return executor.getTornadoCompilerTime();
    }

    /**
     * Returns the compilation time (in ns)that took the device driver (e.g., OpenCL
     * driver) to create the device binary (e.g., from OpenCL C to binary, or from
     * SPIR-V to binary).
     *
     * @return long
     */
    @Override
    public long getDriverInstallTime() {
        return executor.getDriverInstallTime();
    }

    /**
     * Returns the total data transfer time (in ns) for all immutable task-graphs to
     * perform copies from host to device and device to host.
     *
     * @return long
     */
    @Override
    public long getDataTransfersTime() {
        return executor.getDataTransfersTime();
    }

    /**
     * Return the total time for all immutable task-graphs that took to send data to
     * the device (host -> device).
     *
     * @return long
     */
    @Override
    public long getDeviceWriteTime() {
        return executor.getDeviceWriteTime();
    }

    /**
     * Return the total time (in ns) for all immutable task-graphs that took to
     * receive data to the host (device -> host).
     *
     * @return long
     */
    @Override
    public long getDeviceReadTime() {
        return executor.getDeviceReadTime();
    }

    /**
     * Returns the total time (in ns) that took for all immutable task-graphs to
     * dispatch the command to send and receive data. This depends on the driver
     * implementation.
     *
     * @return long
     */
    @Override
    public long getDataTransferDispatchTime() {
        return executor.getDataTransferDispatchTime();
    }

    /**
     * Returns the total time (in ns) that took all kernels to be dispatched by the
     * driver. This is mainly used for debugging purposes.
     *
     * @return long
     */
    @Override
    public long getKernelDispatchTime() {
        return executor.getKernelDispatchTime();
    }

    /**
     * Returns the total time (in ns) for all immutable task-graphs to run the
     * kernel. This metric is from the driver.
     *
     * @return long
     */
    @Override
    public long getDeviceKernelTime() {
        return executor.getDeviceKernelTime();
    }

    /**
     * Returns the profiler log in a JSON format for all the tasks within the
     * executor.
     *
     * @return String
     */
    @Override
    public String getProfileLog() {
        return executor.getProfileLog();
    }

    TornadoExecutor getExecutor() {
        return executor;
    }

    /**
     * Dump in STDOUT all metrics associated to an execution. This is for debugging
     * purposes.
     */
    public void dumpProfiles() {
        getExecutor().dumpProfiles();
    }
}
