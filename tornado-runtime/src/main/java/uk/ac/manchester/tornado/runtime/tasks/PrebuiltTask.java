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
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.Objects;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class PrebuiltTask implements SchedulableTask {

    protected final Object[] args;
    protected final TaskMetaData meta;
    private final String entryPoint;
    private final String filename;
    private final Access[] argumentsAccess;
    protected long batchThreads;
    protected int batchNumber;
    protected long batchSize;

    private TornadoProfiler profiler;
    private boolean forceCompiler;
    private int[] atomics;

    public PrebuiltTask(ScheduleMetaData scheduleMeta, String id, String entryPoint, String filename, Object[] args, Access[] access, TornadoDevice device, DomainTree domain) {
        this.entryPoint = entryPoint;
        this.filename = filename;
        this.args = args;
        this.argumentsAccess = access;
        meta = new TaskMetaData(scheduleMeta, id, access.length);
        for (int i = 0; i < access.length; i++) {
            meta.getArgumentsAccess()[i] = access[i];
        }
        meta.setDevice(device);
        meta.setDomain(domain);

        final long[] values = new long[domain.getDepth()];
        for (int i = 0; i < domain.getDepth(); i++) {
            values[i] = domain.get(i).cardinality();
        }
        meta.setGlobalWork(values);

    }

    public PrebuiltTask(ScheduleMetaData scheduleMeta, String id, String entryPoint, String filename, Object[] args, Access[] access) {
        this.entryPoint = entryPoint;
        this.filename = filename;
        this.args = args;
        this.argumentsAccess = access;
        meta = new TaskMetaData(scheduleMeta, id, access.length);
        for (int i = 0; i < access.length; i++) {
            meta.getArgumentsAccess()[i] = access[i];
        }

    }

    public PrebuiltTask withAtomics(int[] atomics) {
        this.atomics = atomics;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("task: ").append(entryPoint).append("()\n");
        for (int i = 0; i < args.length; i++) {
            sb.append(String.format("arg  : [%s] %s\n", argumentsAccess[i], args[i]));
        }

        sb.append("meta : ").append(meta.toString());

        return sb.toString();
    }

    @Override
    public Object[] getArguments() {
        return args;
    }

    @Override
    public Access[] getArgumentsAccess() {
        return argumentsAccess;
    }

    @Override
    public TaskMetaData meta() {
        return meta;
    }

    @Override
    public SchedulableTask mapTo(TornadoDevice mapping) {
        meta.setDevice(mapping);
        return this;
    }

    @Override
    public TornadoXPUDevice getDevice() {
        return meta.getLogicDevice();
    }

    @Override
    public String getFullName() {
        return STR."task - \{meta.getId()}[\{entryPoint}]";
    }

    @Override
    public String getNormalizedName() {
        return STR."\{meta.getId()}.\{entryPoint}";
    }

    @Override
    public String getTaskName() {
        return entryPoint;
    }

    public String getFilename() {
        return filename;
    }

    public String getEntryPoint() {
        return getTaskName();
    }

    @Override
    public String getId() {
        return meta.getId();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompilableTask other) {
            return getId().equals(other.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(getId());
        hash = 71 * hash + Objects.hashCode(this.entryPoint);
        hash = 71 * hash + Objects.hashCode(this.filename);
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
        this.forceCompiler = true;
    }

    @Override
    public boolean shouldCompile() {
        return forceCompiler;
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

    public int[] getAtomics() {
        return atomics;
    }

}
