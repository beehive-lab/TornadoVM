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

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.memory.FloatingReadNode;
import com.oracle.graal.nodes.memory.MemoryPhiNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;

public class TornadoMemoryPhiElimination extends BasePhase<TornadoMidTierContext> {



	@Override
	protected void run(StructuredGraph graph, TornadoMidTierContext context) {
		
		graph.getNodes().filter(MemoryPhiNode.class)
				.forEach(memoryPhi -> {
					memoryPhi.usages().forEach(usage -> {
						if(usage instanceof FloatingReadNode)
							((FloatingReadNode) usage).setLastLocationAccess(null);
					});
					GraphUtil.tryKillUnused(memoryPhi);
				});

	}

}
