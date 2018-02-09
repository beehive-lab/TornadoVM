package tornado.graal.phases;

import java.lang.annotation.Annotation;
import java.util.Iterator;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;

import tornado.api.Reduce;
import tornado.graal.nodes.StoreAtomicIndexedNode;

public class TornadoReduceReplacement extends BasePhase<TornadoSketchTierContext> {

	@Override
	protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
		System.out.println(">> Reduction Phase Detection");
		findParametersWithReduceAnnotations(graph, context);
		// TODO: Pending, if it is local variable
	}
	
	private void findParametersWithReduceAnnotations(StructuredGraph graph, TornadoSketchTierContext context) {
        final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof Reduce) {
                    final ParameterNode reduceParameter = graph.getParameter(i);
                    
                    NodeIterable<Node> usages = reduceParameter.usages();
                    
                    Iterator<Node> iterator = usages.iterator();
                    // For debugging
                    System.out.println("Reduction var: " + reduceParameter);
                    while (iterator.hasNext()) {
                    	Node node = iterator.next();
                    	System.out.println("\t" + node);
                    	if (node instanceof StoreIndexedNode) {
                    		System.out.println("\t\t store index node");
                    		StoreIndexedNode store = (StoreIndexedNode) node;
                    		Node pred = node.predecessor();
                    		final StoreAtomicIndexedNode atomicStore = graph.addOrUnique(new StoreAtomicIndexedNode(store.array(), store.index(), store.elementKind(), store.value()));
                    		atomicStore.setNext(store.next()); 
                    		pred.replaceFirstSuccessor(store, atomicStore);
                    		store.replaceAndDelete(atomicStore);
                    	} else if (node instanceof StoreFieldNode) {
                    		System.out.println("\t\t store field");
                    	}
                    	
                    }
                    
                }
            }
        }
    }

}
