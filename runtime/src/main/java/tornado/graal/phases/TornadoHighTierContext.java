/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.graal.phases;

import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.meta.TaskMetaData;
import tornado.common.TornadoDevice;

public class TornadoHighTierContext extends HighTierContext {

    protected final ResolvedJavaMethod method;
    protected final Object[] args;
    protected final TaskMetaData meta;
    protected final boolean isKernel;

    public TornadoHighTierContext(
            Providers providers,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts,
            ResolvedJavaMethod method,
            Object[] args,
            TaskMetaData meta,
            boolean isKernel) {
        super(providers, graphBuilderSuite, optimisticOpts);
        this.method = method;
        this.args = args;
        this.meta = meta;
        this.isKernel = isKernel;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean hasArgs() {
        return args != null;
    }

    public Object getArg(int index) {
        return args[index];
    }

    public int getNumArgs() {
        return (hasArgs()) ? args.length : 0;
    }

    public TaskMetaData getMeta() {
        return meta;
    }

    public TornadoDevice getDeviceMapping() {
        return meta.getDevice();
    }

    public boolean hasMeta() {
        return meta != null;
    }

    public boolean isKernel() {
        return isKernel;
    }

}
