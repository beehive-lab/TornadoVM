package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;

@NodeInfo(nameTemplate = "AtomicWrite")
public class WriteAtomicNode extends FixedWithNextNode  implements StateSplit, Lowerable {

    public static final NodeClass<WriteAtomicNode> TYPE = NodeClass.create(WriteAtomicNode.class);

    //@formatter:off
    @Input ValueNode value;
    @Input ValueNode accumulator;
    @Input ValueNode inputArray;
    @Input WriteAtomicNodeExtension writeAtomicExtraNode;
    @Input AddressNode address;
    @Input ValueNode outArray;
    JavaKind kind;
    //@formatter:on

    public WriteAtomicNode(JavaKind kind, AddressNode address, ValueNode value, ValueNode accumulator, ValueNode inputArray, ValueNode outArray, WriteAtomicNodeExtension extension) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
        this.accumulator = accumulator;
        this.inputArray = inputArray;
        this.writeAtomicExtraNode = extension;
        this.address = address;
        this.kind = kind;
        this.outArray = outArray;
    }


    public ValueNode getIndex() {
        return null;
    }

    public JavaKind getElementKind() {
        return kind;
    }
    public ValueNode value() {
        return value;
    }

    public ValueNode getAccumulator() {
        return accumulator;
    }

    public ValueNode getStartNode() {
        return writeAtomicExtraNode.getStartNode();
    }

    public ValueNode getInputArray() {
        return inputArray;
    }

    public void setOptionalOperation(ValueNode node) {
        writeAtomicExtraNode.setExtraOperation(node);
    }

    public ValueNode getExtraOperation() {
        return writeAtomicExtraNode.getExtraOperation();
    }

    public ValueNode getOutArray () {
        return outArray;
    }

    @Override
    public FrameState stateAfter() {
        return writeAtomicExtraNode.getStateAfter();
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(writeAtomicExtraNode.getStateAfter(), x);
        writeAtomicExtraNode.setStateAfter(x);
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }


//    @Override
//    public MemoryOrderMode getMemoryOrder() {
//        return null;
//    }
//    @Override
//    public LocationIdentity getKilledLocationIdentity() {
//        return null;
//    }
}
