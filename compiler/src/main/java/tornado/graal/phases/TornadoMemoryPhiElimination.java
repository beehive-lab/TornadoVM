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
