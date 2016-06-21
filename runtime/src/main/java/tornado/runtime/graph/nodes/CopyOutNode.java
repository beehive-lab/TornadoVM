package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CopyOutNode extends AsyncNode {
	
	public CopyOutNode(ContextNode context) {
		super(context);
	}

	private DependentReadNode value;
	
	public void setValue(DependentReadNode object){
		value = object;
	}
	
	public DependentReadNode getValue(){
		return value;
	}
	
	public String toString(){
		return String.format("[%d]: copy out object %d after task %d", id,value.getValue().getIndex(),value.getDependent().getId());
	}
	
	public boolean hasInputs(){
		return value != null;
	}
	
	public List<AbstractNode> getInputs(){
		if(!hasInputs()){
			return Collections.emptyList();
		}
		
		final List<AbstractNode> result = new ArrayList<AbstractNode>();
		result.add(value);
		return result;
	}
}
