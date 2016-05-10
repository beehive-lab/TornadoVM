package tornado.drivers.opencl.graal.phases;

import java.util.List;

import tornado.api.Vector;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.graal.nodes.vector.VectorKind;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.nodes.BeginNode;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.GuardingPiNode;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.KillingBeginNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.phases.BasePhase;

public class TornadoInvokeCleanup extends BasePhase<TornadoHighTierContext> {

	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {
		graph.getNodes().filter(InvokeWithExceptionNode.class).forEach(invoke -> {
			//System.out.printf("cleaning: %s\n",invoke);
			final List<GuardingPiNode> guardingPis = invoke.usages().filter(GuardingPiNode.class).snapshot();
			
			if(invoke.exceptionEdge() != null)
				invoke.killExceptionEdge();
			
			AbstractBeginNode begin = invoke.next();
            if (begin instanceof KillingBeginNode) {
                AbstractBeginNode newBegin = new BeginNode();
                graph.addAfterFixed(begin, graph.add(newBegin));
                begin.replaceAtUsages(newBegin);
                graph.removeFixed(begin);
            }
		
            if(invoke.getKind().isObject()){
            	final ResolvedJavaType type = invoke.stamp().javaType(context.getMetaAccess());
            	
           
            	
            	if(type.getAnnotation(Vector.class)!= null ){
            		final VectorKind vectorKind = VectorKind.fromResolvedJavaType(type);
            		final VectorValueNode vector = graph.addOrUnique(new VectorValueNode(vectorKind,invoke));
            		
            		invoke.usages().filter(IsNullNode.class).forEach(isNullNode -> {
            			isNullNode.replaceAndDelete(LogicConstantNode.contradiction(graph));
            		});
            		
            		for(GuardingPiNode guardingPi : guardingPis){
            			graph.replaceFixedWithFloating((FixedWithNextNode)guardingPi, vector);
            		}         		
            	}
            	
            	
            
            }           
		});
	}

}
