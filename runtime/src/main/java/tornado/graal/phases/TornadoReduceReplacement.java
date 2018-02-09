package tornado.graal.phases;

import java.lang.annotation.Annotation;
import java.util.Iterator;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

import tornado.api.Reduce;

public class TornadoReduceReplacement extends BasePhase<TornadoSketchTierContext> {

	@Override
	protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
		System.out.println(">> Reduction Phase Detection");
		findReduceAnnotations(graph, context);
	}
	
	private void findReduceAnnotations(StructuredGraph graph, TornadoSketchTierContext context) {
        final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof Reduce) {
                    final ParameterNode reduceParameter = graph.getParameter(i);
                    
                    NodeIterable<Node> usages = reduceParameter.usages();
                    
                    Iterator<Node> iterator = usages.iterator();
                    // For debugging
                    while (iterator.hasNext()) {
                    	System.out.println(iterator.next());
                    }
                    
                }
            }
        }

    }

}
