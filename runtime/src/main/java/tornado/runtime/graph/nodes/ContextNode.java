package tornado.runtime.graph.nodes;

public class ContextNode extends AbstractNode {

    private final int deviceIndex;

    public ContextNode(int index) {
        deviceIndex = index;
    }

    @Override
    public int compareTo(AbstractNode o) {
        if (!(o instanceof ContextNode)) {
            return -1;
        }

        return Integer.compare(deviceIndex, ((ContextNode) o).deviceIndex);
    }

    public int getIndex() {
        return deviceIndex;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("[%d]: context device=%d, [ ", id, deviceIndex));
        for (AbstractNode use : uses) {
            sb.append("").append(use.getId()).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }

}
