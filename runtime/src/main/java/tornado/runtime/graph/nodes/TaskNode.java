package tornado.runtime.graph.nodes;

import java.util.ArrayList;
import java.util.List;

public class TaskNode extends AsyncNode {

    private AbstractNode[] arguments;
    private int taskIndex;

    public TaskNode(ContextNode context, int index, AbstractNode[] arguments) {
        super(context);
        this.taskIndex = index;
        this.arguments = arguments;
    }

    public AbstractNode getArg(int index) {
        return arguments[index];
    }

    public int getTaskIndex() {
        return taskIndex;
    }

    @Override
    public List<AbstractNode> getInputs() {
        final List<AbstractNode> inputs = new ArrayList<>();
        for (AbstractNode input : arguments) {
            inputs.add(input);
        }
        return inputs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[" + id + "]: ");
        sb.append("task=" + taskIndex);
        sb.append(", args=[ ");
        for (AbstractNode arg : arguments) {
            sb.append("" + arg.getId() + " ");
        }
        sb.append("]");
        return sb.toString();
    }

    public int getNumArgs() {
        return arguments.length;
    }

}
