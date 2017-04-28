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
package tornado.drivers.opencl.graal;

import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.nodes.spi.NodeCostProvider;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class OCLProviders extends Providers {

    private final OCLSuitesProvider suites;
    private final Plugins graphBuilderPlugins;

    public OCLProviders(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, NodeCostProvider nodeCostProvider, Plugins plugins, OCLSuitesProvider suitesProvider) {
        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer,
                replacements, stampProvider, nodeCostProvider);
        this.suites = suitesProvider;
        this.graphBuilderPlugins = plugins;
    }

    public OCLSuitesProvider getSuitesProvider() {
        return suites;
    }

    public Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }
}
