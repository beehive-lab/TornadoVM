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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.tasks;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.Objects;

public class PrebuiltTask implements SchedulableTask {

    private final String entryPoint;
    private final String filename;
    protected final Object[] args;
    private final Access[] argumentsAccess;
    protected final TaskMetaData meta;
    protected long batchThreads;

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

    public PrebuiltTask(ScheduleMetaData scheduleMeta, String id, String entryPoint, String filename, Object[] args, Access[] access, TornadoDevice device, DomainTree domain, int[] atomics) {
        this(scheduleMeta, id, entryPoint, filename, args, access, device, domain);
        this.atomics = atomics;
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
    public TornadoAcceleratorDevice getDevice() {
        return meta.getLogicDevice();
    }

    @Override
    public String getFullName() {
        return "task - " + meta.getId() + "[" + entryPoint + "]";
    }

    @Override
    public String getTaskName() {
        return entryPoint;
    }

    public String getFilename() {
        return filename;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    @Override
    public String getId() {
        return meta.getId();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompilableTask) {
            CompilableTask other = (CompilableTask) obj;
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
    public void setBatchThreads(long batchThreads) {
        this.batchThreads = batchThreads;
    }

    @Override
    public long getBatchThreads() {
        return batchThreads;
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
    public boolean isGridSchedulerEnabled() {
        return meta.isGridSchedulerEnabled();
    }

    public int[] getAtomics() {
        return atomics;
    }

}
