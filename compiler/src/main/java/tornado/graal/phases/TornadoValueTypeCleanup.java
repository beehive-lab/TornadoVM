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
