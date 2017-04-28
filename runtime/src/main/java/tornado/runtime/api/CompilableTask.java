/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.runtime.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import tornado.common.SchedulableTask;
import tornado.common.TornadoDevice;
import tornado.common.enums.Access;
import tornado.meta.Meta;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

public class CompilableTask implements SchedulableTask {

    protected final String id;

    protected final Object[] args;
//    protected final Access[] argumentsAccess;

    protected Meta meta;

    protected final Method method;
    protected final Object[] resolvedArgs;
    protected boolean shouldCompile;

    protected Access thisAccess;

    public CompilableTask(String id, Method method, Object... args) {
        this.id = id;
        this.method = method;
        this.args = args;
        this.shouldCompile = true;
        this.resolvedArgs = args;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("task: ").append(method.getName()).append("()\n");
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
    public TornadoDevice getDeviceMapping() {
        return (meta.hasProvider(TornadoDevice.class)) ? meta
                .getProvider(TornadoDevice.class) : null;
    }

    public String getMethodName() {
        return method.getName();
    }

    @Override
    public String getName() {
        return "task " + id + " - " + method.getName();
    }

    @Override
    public CompilableTask mapTo(final TornadoDevice mapping) {
        if (meta == null) {
            meta = mapping.createMeta(method);
        }
        if (meta.hasProvider(TornadoDevice.class)
                && meta.getProvider(TornadoDevice.class) == mapping) {
            return this;
        }

        meta.addProvider(TornadoDevice.class, mapping);
        return this;
    }

    @Override
    public Meta meta() {
        guarantee(meta != null, "task needs to be assigned first");
        return meta;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public String getId() {
        return id;
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
        hash = 71 * hash + Objects.hashCode(this.id);
        hash = 71 * hash + Objects.hashCode(this.method);
        return hash;
    }

}
