package tornado.runtime.graph.nodes;


public class ObjectNode extends AbstractNode {
	private int index;
	
	public ObjectNode(int index){
		this.index = index;
	}
	
	public int getIndex(){
		return index;
	}
	
	@Override
	public int compareTo(AbstractNode o) {
		if(!(o instanceof ObjectNode)) return -1;
		return Integer.compare(index,((ObjectNode)o).index);
	}
	
	public String toString(){
		return String.format("[%d]: object %d",id,index);
	}
	
}
