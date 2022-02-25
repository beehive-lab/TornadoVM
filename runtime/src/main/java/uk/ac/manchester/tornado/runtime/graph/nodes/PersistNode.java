package uk.ac.manchester.tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.List;

public class PersistNode extends ContextOpNode {

    public PersistNode(ContextNode context) {
        super(context);
        this.values = new ArrayList<>();
    }

    private final ArrayList<AbstractNode> values;

    public void addValue(ObjectNode object) {
        values.add(object);
    }

    public List<AbstractNode> getValues() {
        return values;
    }

    public String toString() {
        return String.format("[%d]: persist node", id);
    }

    public boolean hasInputs() {
        return !values.isEmpty();
    }

    public List<AbstractNode> getInputs() {
        return values;
    }
}
