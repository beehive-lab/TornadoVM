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

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.NodePredicate;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.NewInstanceNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.nodes.virtual.VirtualInstanceNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.tiers.HighTierContext;

public class TornadoValueTypeCleanup extends BasePhase<TornadoHighTierContext> {

	

	private static final NodePredicate	valueTypeFilter		= new NodePredicate() {

																@Override
																public boolean apply(Node node) {
																	return ((VirtualInstanceNode) node).hasNoUsages();
																}

															};


	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {
		
		graph.getNodes().filter(NewInstanceNode.class).filter(valueTypeFilter)
				.forEach(instance -> {
					GraphUtil.tryKillUnused(instance);
				});

	}

}
