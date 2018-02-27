/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.common.enums.Access;

public class CompilableTask implements SchedulableTask {

    protected final Object[] args;
//    protected final Access[] argumentsAccess;

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
        final StringBuilder sb = new StringBuilder();

        sb.append("task: ").append(meta.getId()).append(" ").append(method.getName()).append("()\n");
        Access[] argumentsAccess = meta.getArgumentsAccess();
        for (int i = 0; i < args.length; i++) {
            sb.append(String.format("arg  : [%s] %s -> %s\n", argumentsAccess[i], args[i], resolvedArgs[i]));
        }

        sb.append("meta : ").append(meta.toString());

        return sb.toString();
    }

    protected Object[] copyToArguments() {
        final int argOffset = (Modifier.isStatic(method.getModifiers())) ? 0
                : 1;
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
    public CompilableTask mapTo(final TornadoDevice mapping) {
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
