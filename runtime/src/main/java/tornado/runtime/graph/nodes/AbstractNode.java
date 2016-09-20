package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractNode implements Comparable<AbstractNode> {
	protected int id;

	protected final List<AbstractNode> uses;
	public AbstractNode() {
		id = -1;
		uses = new ArrayList<AbstractNode>();
	}
	
	public boolean hasInputs(){
		return false;
	}
	
	public List<AbstractNode> getInputs(){
		return Collections.emptyList();
	}

	public void addUse(AbstractNode use) {
		if (!uses.contains(use)) {
			uses.add(use);
		}
	}

	@Override
	public int compareTo(AbstractNode o) {
		if(o == null) return -1;
		
		return (this == o)? 0 : 1;
	}

	public int getId() {
		return id;
	}

	public List<AbstractNode> getUses() {
		return uses;
	}

	public void replaceAtUses(AbstractNode toReplace, AbstractNode replacement) {
		uses.remove(toReplace);
		uses.add(replacement);
	}

	public void setId(int value) {
		id = value;
	}

	@Override
	public String toString() {
		return String.format("[%d]: %s", id, this.getClass().getSimpleName());
	}
}
