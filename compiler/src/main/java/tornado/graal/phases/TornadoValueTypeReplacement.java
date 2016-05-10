package tornado.graal.phases;

import java.util.HashMap;
import java.util.Map;

import tornado.api.Vector;
import tornado.common.Tornado;

import com.oracle.graal.api.meta.ResolvedJavaField;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.NodePredicate;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.NewInstanceNode;
import com.oracle.graal.nodes.java.StoreFieldNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.tiers.PhaseContext;

public class TornadoValueTypeReplacement extends BasePhase<TornadoHighTierContext> {

	private static final NodePredicate	valueTypeFilter	= new NodePredicate() {

															@Override
															public boolean apply(Node node) {
																return ((NewInstanceNode) node)
																		.instanceClass()
																		.getAnnotation(Vector.class) != null;
															}

														};

	private void simplify(NewInstanceNode newInstance) {
		Tornado.debug("simplify: node=%s", newInstance.toString());

		/*
		 * make dict
		 */
		final Map<ResolvedJavaField, ValueNode> fieldToValue = new HashMap<ResolvedJavaField, ValueNode>();
		newInstance.usages().filter(StoreFieldNode.class).forEach((store) -> {
			fieldToValue.put(store.field(), store.value());
			store.clearInputs();
			// store.clearSuccessors();
			// store.markDeleted();

				// store.replaceAtPredecessor(store.next());
				store.graph().removeFixed(store);
			});

		if (fieldToValue.isEmpty()) return;

		// print dict
		for (ResolvedJavaField field : fieldToValue.keySet())
			Tornado.debug("simplify: field=%s -> value=%s", field.getName(), fieldToValue
					.get(field).toString());

		/*
		 * contract fields
		 */
		newInstance
				.graph()
				.getNodes()
				.filter(LoadFieldNode.class)
				.forEach(
						(load) -> {
							if (load.object() == newInstance) {
								Tornado.debug("simplify: load field=%s", load.field().getName());

								Tornado.debug("simplify: load field=%s -> value=%s", load.field()
										.getName(), fieldToValue.get(load.field()).toString());

								load.replaceAtUsages(fieldToValue.get(load.field()));
								// load.clearInputs();
								// load.markDeleted();
								// load.replaceAtPredecessor(load.next());
								load.graph().removeFixed(load);

							}
						});
	}

	 public void execute(StructuredGraph graph, TornadoHighTierContext context){
	    	run(graph,context);
	    }
	 
	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {

		graph.getNodes().filter(NewInstanceNode.class).filter(valueTypeFilter)
				.forEach(this::simplify);
		graph.maybeCompress();
		// graph.getNodes().filter(VirtualArrayNode.class).filter(vectorizationFilter).forEach(this::vectorize);

	}

}
