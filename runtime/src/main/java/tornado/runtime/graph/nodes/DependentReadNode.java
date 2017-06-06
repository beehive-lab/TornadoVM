/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DependentReadNode extends ContextOpNode {
	
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
