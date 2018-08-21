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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.TornadoDevice;

public class CompilableTask implements SchedulableTask {

    protected final Object[] args;

    protected TaskMetaData meta;

    protected final Method method;
    protected final Object[] resolvedArgs;
    protected boolean shouldCompile;

    protected Access thisAccess;

    public CompilableTask(ScheduleMetaData meta, String id, Method method, Object... args) {
        this.method = method;
        this.args = args;
        this.shouldCompile = true;
        this.resolvedArgs = args;
        this.meta = TaskMetaData.create(meta, id, method, false);
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("task: ").append(meta.getId()).append(" ").append(method.getName()).append("()\n");
        Access[] argumentsAccess = meta.getArgumentsAccess();
        for (int i = 0; i < args.length; i++) {
            buffer.append(String.format("arg  : [%s] %s -> %s\n", argumentsAccess[i], args[i], resolvedArgs[i]));
        }
        buffer.append("meta : ").append(meta.toString());
        return buffer.toString();
    }

    protected Object[] copyToArguments() {
        final int argOffset = (Modifier.isStatic(method.getModifiers())) ? 0 : 1;
        final int numArgs = args.length + argOffset;
        final Object[] arguments = new Object[numArgs];

        for (int i = 0; i < args.length; i++) {
            final Object object = args[i];
            arguments[i + argOffset] = object;
        }
        return arguments;
    }

    @Override
    public Object[] getArguments() {
        return resolvedArgs;
    }

    @Override
    public Access[] getArgumentsAccess() {
        return meta.getArgumentsAccess();
    }

    @Override
    public TornadoDevice getDevice() {
        return meta.getDevice();
    }

    public String getMethodName() {
        return method.getName();
    }

    @Override
    public String getName() {
        return "task " + meta.getId() + " - " + method.getName();
    }

    @Override
    public CompilableTask mapTo(final GenericDevice mapping) {
        meta.setDevice(mapping);
        return this;
    }

    @Override
    public TaskMetaData meta() {
        return meta;
    }

    public Method getMethod() {
        return method;
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
        hash = 71 * hash + Objects.hashCode(this.method);
        return hash;
    }

}
