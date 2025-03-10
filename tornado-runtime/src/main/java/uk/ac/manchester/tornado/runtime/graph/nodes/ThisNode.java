package uk.ac.manchester.tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThisNode extends ContextOpNode {

    public ThisNode(ContextNode context) {
        super(context);
    }

    private ObjectNode value;

    public void setValue(ObjectNode object) {
        value = object;
    }

    public ObjectNode getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("[%d]: this object %d", id, value.getIndex());
    }

    @Override
    public boolean hasInputs() {
        return value != null;
    }

    @Override
    public List<AbstractNode> getInputs() {
        if (!hasInputs()) {
            return Collections.emptyList();
        }

        final List<AbstractNode> result = new ArrayList<AbstractNode>();
        result.add(value);
        return result;
    }
}