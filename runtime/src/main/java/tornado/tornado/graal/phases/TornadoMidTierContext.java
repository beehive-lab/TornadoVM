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
package tornado.graal.phases;

import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.tiers.MidTierContext;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.meta.TaskMetaData;

public class TornadoMidTierContext extends MidTierContext {

    protected final ResolvedJavaMethod method;
    protected final Object[] args;
    protected final TaskMetaData meta;

    public TornadoMidTierContext(
            Providers copyFrom,
            TargetProvider target,
            OptimisticOptimizations optimisticOpts,
            ProfilingInfo profilingInfo,
            ResolvedJavaMethod method, Object[] args, TaskMetaData meta) {
        super(copyFrom, target, optimisticOpts, profilingInfo);
        this.method = method;
        this.args = args;
        this.meta = meta;
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

}
