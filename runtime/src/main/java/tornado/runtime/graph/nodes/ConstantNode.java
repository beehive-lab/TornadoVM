package tornado.runtime.graph.nodes;


public class ConstantNode extends AbstractNode {
	private int index;
	
	public ConstantNode(int index){
		this.index = index;
	}
	
	public int getIndex(){
		return index;
	}

	@Override
	public int compareTo(AbstractNode o) {
		if(!(o instanceof ConstantNode)) return -1;
		return Integer.compare(index,((ConstantNode)o).index);
	}
	
	public String toString(){
		return String.format("[%d]: constant %d",id,index);
	}
	
}
