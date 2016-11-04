package tornado.runtime.graph;

import java.util.BitSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import tornado.runtime.graph.nodes.AbstractNode;

public class Graph {

    private final static int INITIAL_SIZE = 256;

    private AbstractNode[] nodes;
    private BitSet valid;
    private int nextNode;

    public Graph() {
        nodes = new AbstractNode[INITIAL_SIZE];
        valid = new BitSet(INITIAL_SIZE);
        nextNode = 0;
    }

    public AbstractNode getNode(int index) {
        return nodes[index];
    }

    public void add(AbstractNode node) {
        if (nextNode >= nodes.length) {
            resize();
        }

        node.setId(nextNode);
        nodes[nextNode] = node;
        valid.set(nextNode);
        nextNode++;
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractNode> T addUnique(T node) {
        for (int i = valid.nextSetBit(0); i != -1 && i < nodes.length; i = valid.nextSetBit(i + 1)) {
            if (nodes[i].compareTo(node) == 0) {
                return (T) nodes[i];
            }
        }

        add(node);
        return node;
    }

    public void delete(AbstractNode node) {
        valid.clear(node.getId());
        node.setId(-node.getId());
    }

    private void resize() {
        unimplemented();
    }

    public <T extends AbstractNode> BitSet filter(Class<T> type) {
        final BitSet nodes = new BitSet(valid.length());
        apply((AbstractNode n) -> {
            if (n.getClass().equals(type)) {
                nodes.set(n.getId());
            }
        });
        return nodes;
    }

    public BitSet filter(Predicate<AbstractNode> test) {
        final BitSet nodes = new BitSet(valid.length());
        apply((AbstractNode n) -> {
            if (test.test(n)) {
                nodes.set(n.getId());
            }
        });
        return nodes;
    }

    public void papply(BitSet predicates, Consumer<AbstractNode> consumer) {
        for (int i = predicates.nextSetBit(0); i != -1 && i < predicates.length(); i = predicates.nextSetBit(i + 1)) {
            consumer.accept(nodes[i]);
        }
    }

    public void apply(Consumer<AbstractNode> consumer) {
        papply(valid, consumer);
    }

    public void print() {
        System.out.println("graph:");
        apply(System.out::println);
    }

    public BitSet getValid() {
        return valid;
    }
}
