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
package tornado.graal.phases;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.policy.InliningPolicy;
import org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.core.common.GraalOptions.MaximumInliningSize;

public class TornadoInliningPolicy implements InliningPolicy {

    @Override
    public boolean continueInlining(StructuredGraph graph) {
        if (graph.getNodeCount() >= MaximumDesiredSize.getValue(graph.getOptions())) {
            InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
            return false;
        }
        return true;
    }

    @Override
    public boolean isWorthInlining(Replacements replacements, MethodInvocation invocation,
            int inliningDepth, boolean fullyProcessed) {
        boolean doInline = true;

        final InlineInfo info = invocation.callee();
        final double probability = invocation.probability();
        final double relevance = invocation.relevance();

        int nodes = info.determineNodeCount();
        int methodCount = info.numberOfMethods();

        if (nodes > MaximumInliningSize.getValue(info.graph().getOptions()) && !invocation.isRoot()) {
            doInline = false;
        }

        //	System.out.printf("inliner: %s (%s) -> nodes=%d, count=%d\n",info.toString(),doInline, nodes,methodCount);
        return doInline;
    }

}
