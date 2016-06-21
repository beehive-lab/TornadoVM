package tornado.runtime.graph.nodes;


public abstract class AsyncNode extends AbstractNode {
	private final ContextNode context;
	
	public AsyncNode(ContextNode context){
		this.context = context;
	}
	
	public ContextNode getContext(){
		return context;
	}
}
