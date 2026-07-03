package uk.ac.manchester.tornado.runtime.graal.nodes;

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FrameState;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.calc.FloatingNode;

import static tornado.graal.compiler.nodeinfo.InputType.State;

@NodeInfo(nameTemplate = "AtomicWriteExtension")
public class WriteAtomicNodeExtension extends FloatingNode {

    public static final NodeClass<WriteAtomicNodeExtension> TYPE = NodeClass.create(WriteAtomicNodeExtension.class);

    @OptionalInput(State)
    FrameState stateAfter;
    @OptionalInput
    ValueNode extraOperation;
    @OptionalInput
    ValueNode startNode;

    public WriteAtomicNodeExtension(ValueNode startNode) {
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
