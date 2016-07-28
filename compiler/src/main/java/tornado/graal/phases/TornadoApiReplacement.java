package tornado.graal.phases;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tornado.api.Atomic;
import tornado.common.Tornado;
import tornado.graal.nodes.AtomicAccessNode;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;

import com.oracle.graal.api.meta.LocalAnnotation;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.graph.Node;
import com.oracle.graal.loop.InductionVariable;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.IntegerLessThanNode;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.phases.BasePhase;

public class TornadoApiReplacement extends BasePhase<TornadoHighTierContext> {

	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {
		replaceParameterAnnotations(graph, context);
		replaceLocalAnnotations(graph, context);
	}
	
	
	
	private void replaceParameterAnnotations(StructuredGraph graph, TornadoHighTierContext context) {
		final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();
		
		for(int i=0;i<parameterAnnotations.length;i++){
		for(Annotation an : parameterAnnotations[i]){
//			sSystem.out.printf("annotation: param[%d]: %s\n",i,an);
			if(an instanceof Atomic){
				
				final ParameterNode param = graph.getParameter(i);
				final AtomicAccessNode atomicAccess = graph.addOrUnique(new AtomicAccessNode(param));
				param.replaceAtMatchingUsages(atomicAccess, usage -> usage instanceof StoreIndexedNode);
			}
		}
		}
		
	}



	private void replaceLocalAnnotations(StructuredGraph graph, TornadoHighTierContext context) {

		// build node -> annotation mapping
		Map<ResolvedJavaMethod, LocalAnnotation[]> methodToAnnotations = new HashMap<ResolvedJavaMethod, LocalAnnotation[]>();

		methodToAnnotations.put(context.getMethod(), context.getMethod().getTypeAnnotations());

		for (ResolvedJavaMethod inlinee : graph.getInlinedMethods()) {
			if (inlinee.getTypeAnnotations().length > 0) methodToAnnotations.put(inlinee,
					inlinee.getTypeAnnotations());
		}

		Map<Node, LocalAnnotation> parallelNodes = new HashMap<Node, LocalAnnotation>();
		
		graph.getNodes().filter(FrameState.class).forEach((fs) -> {
			// Tornado.trace("framestate: method=%s,",fs.method().getName());
				if (methodToAnnotations.containsKey(fs.method())) {
					for (LocalAnnotation an : methodToAnnotations.get(fs.method())) {
						if (fs.bci >= an.getStart() && fs.bci < an.getStart() + an.getLength()) {
							Node localNode = fs.localAt(an.getIndex());
							
							if (!parallelNodes.containsKey(localNode)) {
								// Tornado.info("found parallel node: %s",localNode);
								parallelNodes.put(localNode, an);
							}
						}
					}
				}
			});

		if (graph.hasLoops()) {

			final LoopsData data = new LoopsData(graph);
			data.detectedCountedLoops();

			int loopIndex = 0;
			for (LoopEx loop : data.innerFirst()) {

				for (InductionVariable iv : loop.getInductionVariables().values()) {
					if (!parallelNodes.containsKey(iv.valueNode())) continue;

					ValueNode maxIterations = null;
					List<IntegerLessThanNode> conditions = iv.valueNode().usages()
							.filter(IntegerLessThanNode.class).snapshot();
					if (conditions.size() == 1) {
						final IntegerLessThanNode lessThan = conditions.get(0);
						maxIterations = lessThan.getY();
					} else {
						Tornado.debug("Unable to parallelise: multiple uses of iv");
						continue;
					}

					if (iv.isConstantInit() && iv.isConstantStride()) {

						final ConstantNode newInit = graph.addWithoutUnique(ConstantNode
								.forInt((int) iv.constantInit()));
						final ConstantNode newStride = graph.addWithoutUnique(ConstantNode
								.forInt((int) iv.constantStride()));

						final ParallelOffsetNode offset = graph
								.addWithoutUnique(new ParallelOffsetNode(loopIndex, newInit));

						final ParallelStrideNode stride = graph
								.addWithoutUnique(new ParallelStrideNode(loopIndex, newStride));

						final ParallelRangeNode range = graph
								.addWithoutUnique(new ParallelRangeNode(loopIndex, maxIterations,
										offset, stride));

						final ValuePhiNode phi = (ValuePhiNode) iv.valueNode();
						final ValueNode oldStride = phi.singleBackValue();
						
						//System.out.printf("oldStride: %s\n",oldStride.toString());
						if(oldStride.usages().count() > 1){
							final ValueNode duplicateStride = (ValueNode) oldStride.copyWithInputs(true);
							
							oldStride.replaceAtMatchingUsages(duplicateStride, usage -> !usage.equals(phi));
								
							//duplicateStride.removeUsage(phi);
							//oldStride.removeUsage(node)
						}

						iv.initNode().replaceAtMatchingUsages(offset, node -> node.equals(phi));
						iv.strideNode().replaceAtMatchingUsages(stride,
								node -> node.equals(oldStride));
						
						// only replace this node in the loop condition
						maxIterations.replaceAtMatchingUsages(range, node -> node.equals(conditions.get(0)));

					} else {
						Tornado.debug("Unable to parallelise: non-constant stride or offset");
						continue;
					}
					loopIndex++;
				}

			}
		}
	}
}
