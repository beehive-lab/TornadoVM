package tornado.graal.phases;


import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.GuardNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.IntegerBelowNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.phases.BasePhase;

public class ExceptionCheckingElimination extends BasePhase<TornadoMidTierContext> {
  /**
   * Removes all exception checking - loop bounds and null checks
   */
	@Override
	protected void run(StructuredGraph graph, TornadoMidTierContext context) {
		
		graph.getNodes().filterInterface(GuardedNode.class).snapshot().forEach((node) -> {
			GuardedNode guardedNode = (GuardedNode) node;
			if(guardedNode.getGuard() instanceof GuardNode ){
				GuardNode guard = (GuardNode) guardedNode.getGuard();
			
				LogicNode condition = guard.condition();
				
				if(condition instanceof IsNullNode){
				Node input  = condition.inputs().first();
				
				if(guard.isNegated()){
					condition.replaceFirstInput(input, LogicConstantNode.contradiction(graph));
				} else {
					condition.replaceFirstInput(input, LogicConstantNode.tautology(graph));
				}
				
				} else if( condition instanceof IntegerBelowNode){
					
					ValueNode x = ((IntegerBelowNode) condition).getX();
					condition.replaceFirstInput(x, graph.addOrUnique(ConstantNode.forInt(Integer.MAX_VALUE)));
				}
				
				//				guardedNode.setGuard(null);
//				if(guard.isAlive())
//					guard.replaceAndDelete(node);
//				else
//					guard.replaceAtUsages(node);
				
			}
			
		});
		


	}
}
