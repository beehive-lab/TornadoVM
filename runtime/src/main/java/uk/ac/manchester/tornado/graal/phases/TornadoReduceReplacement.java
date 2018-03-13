package uk.ac.manchester.tornado.graal.phases;

import java.lang.annotation.Annotation;
import java.util.Iterator;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.Reduce;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceSubNode;
import uk.ac.manchester.tornado.graal.nodes.StoreAtomicIndexedNode;

public class TornadoReduceReplacement extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        findParametersWithReduceAnnotations(graph, context);

        // TODO: Pending, if it is local variable
    }

    // XXX: Cover all the cases here as soon as we discover more reductions
    // use-cases
    private boolean recursiveCheck(ValueNode arrayToStore, ValueNode indexToStore, ValueNode currentNode) {
        boolean isReduction = false;
        if (currentNode instanceof BinaryArithmeticNode) {
            @SuppressWarnings("rawtypes") BinaryArithmeticNode value = (BinaryArithmeticNode) currentNode;
            ValueNode x = value.getX();
            isReduction = recursiveCheck(arrayToStore, indexToStore, x);
            if (isReduction == false) {
                ValueNode y = value.getY();
                return recursiveCheck(arrayToStore, indexToStore, y);
            }
        } else if (currentNode instanceof LoadIndexedNode) {
            LoadIndexedNode loadNode = (LoadIndexedNode) currentNode;
            if (loadNode.array() == arrayToStore && loadNode.index() == indexToStore) {
                isReduction = true;
            }
        }
        return isReduction;
    }

    private boolean checkIfReduction(StoreIndexedNode store) {
        ValueNode arrayToStore = store.array();
        ValueNode indexToStore = store.index();
        ValueNode valueToStore = store.value();
        return recursiveCheck(arrayToStore, indexToStore, valueToStore);
    }

    private static class ReductionNodes {
        private ValueNode value;
        private ValueNode accumulator;

        public ReductionNodes(ValueNode value, ValueNode accumulator) {
            super();
            this.value = value;
            this.accumulator = accumulator;
        }

    }

    private ReductionNodes createReductionNode(StructuredGraph graph, StoreIndexedNode store) {
        ValueNode value = null;
        ValueNode accumulator = null;
        if (store.value() instanceof AddNode) {
            AddNode addNode = (AddNode) store.value();
            final OCLReduceAddNode atomicAdd = graph.addOrUnique(new OCLReduceAddNode(addNode.getX(), addNode.getY()));
            accumulator = addNode.getX();
            value = atomicAdd;
            addNode.safeDelete();
        } else if (store.value() instanceof MulNode) {
            MulNode mulNode = (MulNode) store.value();
            final OCLReduceMulNode atomicMultiplication = graph.addOrUnique(new OCLReduceMulNode(mulNode.getX(), mulNode.getY()));
            accumulator = mulNode.getX();
            value = atomicMultiplication;
            mulNode.safeDelete();
        } else if (store.value() instanceof SubNode) {
            SubNode subNode = (SubNode) store.value();
            final OCLReduceSubNode atomicSub = graph.addOrUnique(new OCLReduceSubNode(subNode.getX(), subNode.getY()));
            accumulator = subNode.getX();
            value = atomicSub;
            subNode.safeDelete();
        } else {
            throw new RuntimeException("\n\n[NOT SUPPORTED] Node : " + store.value() + " not suported yet.");
        }
        return new ReductionNodes(value, accumulator);
    }

    private void performNodeReplacement(StructuredGraph graph, StoreIndexedNode store, Node pred, ReductionNodes reductionNode) {
        // Final Replacement
        ValueNode value = reductionNode.value;
        ValueNode accumulator = reductionNode.accumulator;
        final StoreAtomicIndexedNode atomicStore = graph.addOrUnique(new StoreAtomicIndexedNode(store.array(), store.index(), store.elementKind(), value, accumulator));
        atomicStore.setNext(store.next());
        pred.replaceFirstSuccessor(store, atomicStore);
        store.replaceAndDelete(atomicStore);
    }

    private void processReduceAnnotation(StructuredGraph graph, int index) {
        final ParameterNode reduceParameter = graph.getParameter(index);

        NodeIterable<Node> usages = reduceParameter.usages();
        Iterator<Node> iterator = usages.iterator();

        while (iterator.hasNext()) {
            Node node = iterator.next();

            if (node instanceof StoreIndexedNode) {
                StoreIndexedNode store = (StoreIndexedNode) node;

                boolean isReductionValue = checkIfReduction(store);
                if (!isReductionValue) {
                    continue;
                }

                ReductionNodes reductionNode = createReductionNode(graph, store);
                Node pred = node.predecessor();
                performNodeReplacement(graph, store, pred, reductionNode);

            } else if (node instanceof StoreFieldNode) {
                throw new RuntimeException("\n\n[NOT SUPPORTED] Node StoreFieldNode: not suported yet.");
            }
        }
    }

    private void findParametersWithReduceAnnotations(StructuredGraph graph, TornadoSketchTierContext context) {
        final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();
        for (int index = 0; index < parameterAnnotations.length; index++) {
            for (Annotation annotation : parameterAnnotations[index]) {
                // Get the reduce annotations
                if (annotation instanceof Reduce) {
                    processReduceAnnotation(graph, index);
                }
            }
        }
    }
}
