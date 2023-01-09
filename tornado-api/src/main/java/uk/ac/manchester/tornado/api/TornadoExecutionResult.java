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

import java.util.List;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * Object created when the {@link TornadoExecutionPlan#execute()} is finished.
 * This objects stores the results of the execution. Additionally, if the
 * execution plan enabled the profiler information, this object also stores all
 * profiler information (e.g., read/write time, kernel time, etc.) through the
 * {@link TornadoProfilerResult} object.
 */
public class TornadoExecutionResult {

    private List<Object> outputs;

    private TornadoProfilerResult tornadoProfilerResult;

    TornadoExecutionResult(List<Object> outputs, TornadoProfilerResult profilerResult) {
        this.outputs = outputs;
        this.tornadoProfilerResult = profilerResult;
    }

    /**
     * Method to obtain all object results related to an execution plan. The return
     * type is a linked list that contains all object results in the same order as
     * specified in the {@link TaskGraph}.
     *
     * @return {@link List<Object>}
     */
    public List<Object> getOutputs() {
        return this.outputs;
    }

    /**
     * Method to obtain a specific input from the output result list.
     *
     * @param index
     *            Index of the object within the result list.
     * @return {@link Object}
     */
    public Object getOutput(int index) {
        if (outputs.size() >= index) {
            throw new TornadoRuntimeException("Output not found");
        }
        return outputs.get(index);
    }

    /**
     * Method to obtain the profiler information associated to the latest execution
     * plan. Note that, all timers associated to the profiler are enabled only if
     * the execution plan enables the profiler.
     *
     * @return {@link TornadoProfilerResult}
     */
    public TornadoProfilerResult getProfilerResult() {
        return tornadoProfilerResult;
    }

    /**
     * Transfer data from device to host. This is applied for all immutable
     * task-graphs within an executor. This method is used when a task-graph defines
     * transferToHost using the
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#LAST}. This
     * indicates the runtime to not to copy-out the data en every iteration and
     * transfer the data under demand.
     *
     * @param objects
     *            Host objects to transfer the data to.
     */
    public void transferToHost(Object... objects) {
        tornadoProfilerResult.getExecutor().transferToHost(objects);
    }

    /**
     * It returns true if all task-graphs associated to the executor finished
     * execution.
     *
     * @return boolean
     */
    public boolean isFinished() {
        return tornadoProfilerResult.getExecutor().isFinished();
    }

}
