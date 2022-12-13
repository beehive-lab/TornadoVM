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
import uk.ac.manchester.tornado.api.profiler.ProfileInterface;

public class TornadoExecutorPlan implements ProfileInterface {

    private final TornadoExecutor tornadoExecutor;

    private GridScheduler gridScheduler;

    private boolean isReusableBuffer;
    private Policy policy = null;

    TornadoExecutorPlan(TornadoExecutor tornadoExecutor) {
        this.tornadoExecutor = tornadoExecutor;
    }

    public TornadoExecutorPlan withReusableBuffers() {
        this.isReusableBuffer = true;
        return this;
    }

    public TornadoExecutorPlan withDynamicReconfiguration() {
        // empty implementation for now
        return this;
    }

    public TornadoExecutorPlan warmup() {
        tornadoExecutor.warmup();
        return this;
    }

    public TornadoExecutorPlan execute() {
        if (this.policy != null) {
            tornadoExecutor.executeWithDynamicReconfiguration(this.policy);
        } else if (gridScheduler != null) {
            tornadoExecutor.execute(gridScheduler);
        } else {
            tornadoExecutor.execute();
        }
        return this;
    }

    public TornadoExecutorPlan setDevice(TornadoDevice device) {
        tornadoExecutor.setDevice(device);
        return this;
    }

    public TornadoExecutorPlan lockObjectsInMemory(Object... objects) {
        tornadoExecutor.lockObjectsInMemory(objects);
        return this;
    }

    public TornadoExecutorPlan unlockObjectsFromMemory(Object... objects) {
        tornadoExecutor.unlockObjectFromMemory(objects);
        return this;
    }

    public TornadoExecutorPlan syncObjects(Object... objects) {
        tornadoExecutor.syncObjects(objects);
        return this;
    }

    public TornadoExecutorPlan syncField(Object object) {
        tornadoExecutor.syncField(object);
        return this;
    }

    public TornadoExecutorPlan replaceParameter(Object oldParameter, Object newParameter) {
        tornadoExecutor.replaceParameter(oldParameter, newParameter);
        return this;
    }

    public TornadoExecutorPlan withGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        return this;
    }

    public TornadoExecutorPlan withDynamicReconfiguration(Policy policy) {
        this.policy = policy;
        return this;
    }

    public boolean isFinished() {
        return tornadoExecutor.isFinished();
    }

    @Override
    public long getTotalTime() {
        return tornadoExecutor.getTotalTime();
    }

    @Override
    public long getCompileTime() {
        return tornadoExecutor.getCompileTime();
    }

    @Override
    public long getTornadoCompilerTime() {
        return tornadoExecutor.getTornadoCompilerTime();
    }

    @Override
    public long getDriverInstallTime() {
        return tornadoExecutor.getDriverInstallTime();
    }

    @Override
    public long getDataTransfersTime() {
        return tornadoExecutor.getDataTransfersTime();
    }

    @Override
    public long getWriteTime() {
        return tornadoExecutor.getWriteTime();
    }

    @Override
    public long getReadTime() {
        return tornadoExecutor.getReadTime();
    }

    @Override
    public long getDataTransferDispatchTime() {
        return tornadoExecutor.getDataTransferDispatchTime();
    }

    @Override
    public long getKernelDispatchTime() {
        return tornadoExecutor.getKernelDispatchTime();
    }

    @Override
    public long getDeviceWriteTime() {
        return tornadoExecutor.getDeviceWriteTime();
    }

    @Override
    public long getDeviceKernelTime() {
        return tornadoExecutor.getDeviceKernelTime();
    }

    @Override
    public long getDeviceReadTime() {
        return tornadoExecutor.getDeviceReadTime();
    }

    @Override
    public String getProfileLog() {
        return tornadoExecutor.getProfileLog();
    }

}
