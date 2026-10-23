/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.Objects;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * A task that invokes a function from an external native library (e.g., NVIDIA
 * cuBLAS) instead of a JIT-compiled kernel. Library tasks have no sketch and
 * are never compiled; at launch time the interpreter resolves their arguments
 * to device buffers and dispatches the call through the matching
 * {@link uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider}.
 */
public class LibraryTask implements SchedulableTask {

    private final LibraryTaskDescriptor descriptor;
    private final TaskDataContext meta;
    private TornadoProfiler profiler;
    protected long batchThreads;
    protected int batchNumber;
    protected long batchSize;

    public LibraryTask(ScheduleContext scheduleMeta, String id, LibraryTaskDescriptor descriptor) {
        this.descriptor = descriptor;
        meta = new TaskDataContext(scheduleMeta, id, descriptor.getAccess().length);
        meta.setArgumentsAccess(descriptor.getAccess());
    }

    public LibraryTaskDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public Object[] getArguments() {
        return descriptor.getParameters();
    }

    @Override
    public Access[] getArgumentsAccess() {
        return descriptor.getAccess();
    }

    @Override
    public TaskDataContext meta() {
        return meta;
    }

    @Override
    public void setDevice(TornadoDevice device) {
        meta.setDevice(device);
    }

    @Override
    public TornadoXPUDevice getDevice() {
        return meta.getXPUDevice();
    }

    @Override
    public String getFullName() {
        return "task - " + meta.getId() + "[" + descriptor.getFunctionName() + "]";
    }

    @Override
    public String getNormalizedName() {
        return meta.getId() + "." + descriptor.getFunctionName();
    }

    @Override
    public String getTaskName() {
        return descriptor.getFunctionName();
    }

    @Override
    public String getId() {
        return meta.getId();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LibraryTask other) {
            return getId().equals(other.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(getId());
        hash = 71 * hash + Objects.hashCode(descriptor.getLibraryName());
        hash = 71 * hash + Objects.hashCode(descriptor.getFunctionName());
        return hash;
    }

    @Override
    public long getBatchThreads() {
        return batchThreads;
    }

    @Override
    public void setBatchThreads(long batchThreads) {
        this.batchThreads = batchThreads;
    }

    @Override
    public void setBatchNumber(int batchNumber) {
        this.batchNumber = batchNumber;
    }

    @Override
    public int getBatchNumber() {
        return this.batchNumber;
    }

    @Override
    public void setBatchSize(long batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public long getBatchSize() {
        return this.batchSize;
    }

    @Override
    public void attachProfiler(TornadoProfiler tornadoProfiler) {
        this.profiler = tornadoProfiler;
    }

    @Override
    public TornadoProfiler getProfiler() {
        return this.profiler;
    }

    @Override
    public void forceCompilation() {
        // Library tasks are never compiled.
    }

    @Override
    public boolean shouldCompile() {
        return false;
    }

    @Override
    public void enableDefaultThreadScheduler(boolean useDefaultScheduler) {
        meta.enableDefaultThreadScheduler(useDefaultScheduler);
    }

    @Override
    public void setUseGridScheduler(boolean use) {
        meta.setUseGridScheduler(use);
    }

    @Override
    public void setGridScheduler(GridScheduler gridScheduler) {
        meta.setGridScheduler(gridScheduler);
    }

    @Override
    public boolean isGridSchedulerEnabled() {
        return meta.isGridSchedulerEnabled();
    }
}
