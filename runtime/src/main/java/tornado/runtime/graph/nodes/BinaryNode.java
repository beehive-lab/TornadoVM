package tornado.runtime.graph.nodes;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public abstract class BinaryNode extends AbstractNode {
	protected AbstractNode x;
	protected AbstractNode y;
	
	public AbstractNode getX(){
		return x;
	}
	
	public AbstractNode getY(){
		return y;
	}
	
	public void replaceAtInputs(AbstractNode toReplace, AbstractNode replacement){
		if(x == toReplace){
			x = replacement;
		} else if(y == toReplace){
			y = replacement;
		} else {
			shouldNotReachHere();
		}
	}
	
}
