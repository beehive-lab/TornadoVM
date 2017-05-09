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

import static com.oracle.graal.compiler.common.GraalOptions.*;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.InlineInfo;
import com.oracle.graal.phases.common.inlining.policy.InliningPolicy;
import com.oracle.graal.phases.common.inlining.walker.MethodInvocation;

public class TornadoInliningPolicy implements InliningPolicy {

	@Override
	public boolean continueInlining(StructuredGraph graph) {
		if (graph.getNodeCount() >= MaximumDesiredSize.getValue()) {
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
		
		
		if(nodes > MaximumInliningSize.getValue() && !invocation.isRoot())
			doInline = false;
		
	//	System.out.printf("inliner: %s (%s) -> nodes=%d, count=%d\n",info.toString(),doInline, nodes,methodCount);
		
		
		return doInline;
	}

}
