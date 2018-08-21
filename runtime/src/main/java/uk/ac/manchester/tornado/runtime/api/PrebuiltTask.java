/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.runtime.api;

import java.util.Objects;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.meta.domain.DomainTree;

public class PrebuiltTask implements SchedulableTask {

    protected final String entryPoint;
    protected final String filename;
    protected final Object[] args;
    protected final Access[] argumentsAccess;
    protected final TaskMetaData meta;

    protected PrebuiltTask(ScheduleMetaData scheduleMeta, String id, String entryPoint, String filename, Object[] args, Access[] access, GenericDevice device, DomainTree domain) {
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
    public SchedulableTask mapTo(GenericDevice mapping) {
        meta.setDevice(mapping);
        return this;
    }

    @Override
    public TornadoDevice getDevice() {
        return meta.getDevice();
    }

    @Override
    public String getName() {
        return "task - " + meta.getId() + "[" + entryPoint + "]";
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
}
