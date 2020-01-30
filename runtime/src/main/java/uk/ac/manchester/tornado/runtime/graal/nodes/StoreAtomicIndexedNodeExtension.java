package uk.ac.manchester.tornado.runtime.graal.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.State;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;

@NodeInfo(nameTemplate = "AtomicIndexedStoreExtension")
public class StoreAtomicIndexedNodeExtension extends FloatingNode {

    public static final NodeClass<StoreAtomicIndexedNodeExtension> TYPE = NodeClass.create(StoreAtomicIndexedNodeExtension.class);

    @OptionalInput(State)
    FrameState stateAfter;
    @OptionalInput
    ValueNode extraOperation;
    @OptionalInput
    ValueNode startNode;

    public StoreAtomicIndexedNodeExtension(ValueNode startNode) {
        super(TYPE, StampFactory.forVoid());
        this.startNode = startNode;
    }

    public FrameState getStateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState stateAfter) {
        this.stateAfter = stateAfter;
    }

    public ValueNode getExtraOperation() {
        return extraOperation;
    }

    public ValueNode getStartNode() {
        return startNode;
    }

    public void setExtraOperation(ValueNode extraOperation) {
        this.extraOperation = extraOperation;
    }
}
