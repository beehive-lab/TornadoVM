package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StreamInNode extends AsyncNode {
	
	public StreamInNode(ContextNode context) {
		super(context);
	}

	private ObjectNode value;
	
	public void setValue(ObjectNode object){
		value = object;
	}
	
	public ObjectNode getValue(){
		return value;
	}
	
	public String toString(){
		return String.format("[%d]: stream in object %d", id,value.getIndex());
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
