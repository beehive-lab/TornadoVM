package tornado.graal.phases;

import java.util.HashSet;
import java.util.Set;

import com.oracle.graal.nodes.GuardNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.phases.BasePhase;

public class ExceptionSuppression extends BasePhase<TornadoHighTierContext> {

	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {

		Set<LogicNode> conditions = new HashSet<LogicNode>();
		Set<GuardNode> guards  = new HashSet<GuardNode>();
		
		graph.getNodes().filterInterface(GuardedNode.class).forEach((node) -> {
			GuardedNode guardedNode = (GuardedNode) node;
			if(guardedNode.getGuard() instanceof GuardNode  ){
				GuardNode guard = (GuardNode) guardedNode.getGuard();
				LogicNode condition = guard.condition();
				
				conditions.add(condition);
				guards.add(guard);
				guardedNode.setGuard(null);
				
			}
		});
		
		graph.getNodes().filter(ValueAnchorNode.class).forEach(anchor -> {
			if(anchor.getAnchoredNode() instanceof GuardNode){
				final GuardNode guard = (GuardNode) anchor.getAnchoredNode();
				guards.add(guard);
				conditions.add(guard.condition());
				anchor.removeAnchoredNode();
			}
		});
		
		
		guards.forEach(guard -> {
			guard.clearInputs();
			graph.removeFloating(guard);
		});
		
		conditions.forEach(condition -> {
			condition.clearInputs();
			graph.removeFloating(condition);
		});
		
	}

}
