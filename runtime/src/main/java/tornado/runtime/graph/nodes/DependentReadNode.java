package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DependentReadNode extends AsyncNode {
	
	public DependentReadNode(ContextNode context) {
		super(context);
	}

	private ObjectNode value;
	private TaskNode dependent;
	
	public void setValue(ObjectNode object){
		value = object;
	}
	
	public ObjectNode getValue(){
		return value;
	}
	
	public void setDependent(TaskNode task){
		dependent = task;
	}
	
	public TaskNode getDependent(){
		return dependent;
	}
	
	public String toString(){
		return String.format("[%d]: dependent write on object %d by task %d", id,value.getIndex(), dependent.id);
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
		result.add(dependent);
		return result;
	}
}
