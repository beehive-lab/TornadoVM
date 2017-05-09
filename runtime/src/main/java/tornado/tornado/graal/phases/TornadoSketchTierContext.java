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
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.meta.TaskMetaData;

public class TornadoSketchTierContext extends HighTierContext {

    protected final ResolvedJavaMethod method;
    protected final TaskMetaData meta;

    public TornadoSketchTierContext(
            Providers providers,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts,
            ResolvedJavaMethod method,
            TaskMetaData meta) {
        super(providers, graphBuilderSuite, optimisticOpts);
        this.method = method;
        this.meta = meta;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public TaskMetaData getMeta() {
        return meta;
    }

    public boolean hasMeta() {
        return meta != null;
    }
}
