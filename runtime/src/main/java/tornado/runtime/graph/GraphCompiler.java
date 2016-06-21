package tornado.runtime.graph;

import java.util.BitSet;

import tornado.common.DeviceMapping;
import tornado.runtime.graph.nodes.AbstractNode;
import tornado.runtime.graph.nodes.AsyncNode;
import tornado.runtime.graph.nodes.ContextNode;
import tornado.runtime.graph.nodes.TaskNode;

public class GraphCompiler {
	
	public static GraphCompilationResult compile(Graph graph, ExecutionContext context){
		
		final BitSet deviceContexts = graph.filter(ContextNode.class);
		if(deviceContexts.cardinality() == 1){
			final ContextNode contextNode = (ContextNode) graph.getNode(deviceContexts.nextSetBit(0));
			return compileSingleContext(graph,context,context.getDevice(contextNode.getIndex()));
		}
		
		return null;
	}

	/*
	 * Simplest case where all tasks are executed on the same device
	 */
	private static GraphCompilationResult compileSingleContext(Graph graph, ExecutionContext context,
			DeviceMapping device) {
		
		final GraphCompilationResult result = new GraphCompilationResult();
		
		final BitSet asyncNodes = graph.filter((AbstractNode n) -> n instanceof AsyncNode);
		
//		System.out.printf("found: [%s]\n",toString(asyncNodes));
		
		final BitSet[] deps = new BitSet[asyncNodes.cardinality()];
		final BitSet tasks = new BitSet(asyncNodes.cardinality());
		final int[] nodeIds = new int[asyncNodes.cardinality()];
		int index = 0;
		int numDepLists = 0;
		for(int i=asyncNodes.nextSetBit(0);i!=-1 &&i<asyncNodes.length();i=asyncNodes.nextSetBit(i + 1) ){
			deps[index] = calculateDeps(graph,context, i);
			nodeIds[index] = i;
			if(graph.getNode(i) instanceof TaskNode){
				tasks.set(index);
			}
			
			if(!deps[index].isEmpty()){
				numDepLists++;
			}
			index++;
		}
		
//		printMatrix(graph,nodeIds,deps,tasks);
		
		result.begin(1,tasks.cardinality(),numDepLists + 1);
		
		schedule(result, graph, context, nodeIds, deps, tasks);
		
		result.end(numDepLists);
		
//		result.dump();
		
		return result;
	}

	private static void schedule(GraphCompilationResult result, Graph graph, ExecutionContext context,
			int[] nodeIds, BitSet[] deps, BitSet tasks) {
		
		final BitSet scheduled = new BitSet(deps.length);
		scheduled.clear();
		final BitSet nodes = new BitSet(graph.getValid().length());
		
		final int[] depLists = new int[deps.length];
		int index = 0;
		for(int i=0;i<deps.length;i++){
			if(!deps[i].isEmpty()){
				depLists[i] = index;
				index++;
			}
		}
		
		while(scheduled.cardinality() < deps.length){
//			System.out.printf("nodes: %s\n",toString(nodes));
//			System.out.printf("scheduled: %s\n",toString(scheduled));
			for(int i=0;i<deps.length;i++){
				if(!scheduled.get(i)){
					
					final BitSet outstandingDeps = new BitSet(nodes.length());
					outstandingDeps.or(deps[i]);
					outstandingDeps.andNot(nodes);
//				System.out.printf("trying: %d - %s\n",nodeIds[i],toString(outstandingDeps));
				if(outstandingDeps.isEmpty()){
					final AsyncNode asyncNode = (AsyncNode) graph.getNode(nodeIds[i]);
					result.emitAsyncNode(graph, context,asyncNode ,asyncNode.getContext().getIndex(), (deps[i].isEmpty())? -1 : depLists[i]);
					for(int j=0;j<deps.length;j++){
						if(j==i){
							continue;
						}
//						System.out.printf("checking: %d - %s\n",nodeIds[j],toString(deps[j]));
						if(deps[j].get(nodeIds[i])){
							result.emitAddDep(depLists[j]);
						}
					}
					scheduled.set(i);
					nodes.set(nodeIds[i]);
				}
				}
			}
		}
		
	}

	private static String toString(BitSet set){
		if(set.isEmpty()){
			return "<none>";
		}
		
		StringBuilder sb = new StringBuilder();
		for(int i=set.nextSetBit(0);i!=-1 &&i<set.length();i=set.nextSetBit(i + 1) ){
			sb.append("" + i + " ");
		}
		return sb.toString();
	}
	private static void printMatrix(Graph graph, int[] nodeIds, BitSet[] deps,
			BitSet tasks) {
		
		System.out.println("dependency matrix...");
		for(int i=0;i<nodeIds.length;i++){
			final int nodeId = nodeIds[i];
			System.out.printf("%d [%s]| %s\n",nodeId,(tasks.get(i))? "task":"data",toString(deps[i]));
		}
		
	}

	private static BitSet calculateDeps(Graph graph, ExecutionContext context, int i) {
		final BitSet deps = new BitSet(graph.getValid().length());
		
		final AbstractNode node = graph.getNode(i);
		for(AbstractNode input : node.getInputs()){
			if(input instanceof AsyncNode){
				deps.set(input.getId());
			}
		}
		
		return deps;
	}

}
