package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.nodes.GuardingPiNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.VectorLoadElementProxyNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import tornado.graal.phases.TornadoHighTierContext;

public class TornadoParameterCleanup extends BasePhase<TornadoHighTierContext> {

	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {
		
		for(int i=0;i<graph.getNodes().filter(ParameterNode.class).count();i++){
			final ParameterNode param = graph.getParameter(i);
					
					if(param == null || !param.getKind().isObject()) continue;
					
					final Stamp stamp = param.stamp();
//					System.out.printf("node: node=%s, stamp=%s, type=%s\n",param, stamp,stamp.javaType(context.getMetaAccess()));
					final ResolvedJavaType type = stamp.javaType(context.getMetaAccess());
                                        final OCLKind vectorKind = OCLKind.fromResolvedJavaType(type);
					if(vectorKind != OCLKind.ILLEGAL){
						if(param.usages().filter(VectorValueNode.class).isEmpty()){
//							System.out.printf("inserting vector value...\n");
							
							final VectorValueNode vector = graph.addOrUnique(new VectorValueNode(vectorKind, param));
							if(context.isKernel())
								vector.setNeedsLoad();
									
							param.usages().filter(VectorLoadElementProxyNode.class).forEach(loadProxy -> {
								loadProxy.setOrigin(vector);
								if(loadProxy.canResolve())
									loadProxy.replaceAndDelete(loadProxy.tryResolve());
							});
							
							param.usages().filter(MethodCallTargetNode.class).forEach(methodCall -> {
								param.replaceAtMatchingUsages(vector, usage -> usage == methodCall);
							});
							
							param.usages().filter(GuardingPiNode.class).forEach(guard ->{
								guard.replaceAtUsages(vector);
								GraphUtil.tryKillUnused(guard);
							});
							
						}
					}
					
				
		}
	}

}
