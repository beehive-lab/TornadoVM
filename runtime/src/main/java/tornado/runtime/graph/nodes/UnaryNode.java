package tornado.runtime.graph.nodes;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public abstract class UnaryNode extends AbstractNode {
	protected AbstractNode input;
	
	public AbstractNode getInput(){
		return input;
	}
	
	public void replaceAtInputs(AbstractNode toReplace, AbstractNode replacement){
		if(input == toReplace){
			input = replacement;
		} else {
			shouldNotReachHere();
		}
			
	}
	
}
