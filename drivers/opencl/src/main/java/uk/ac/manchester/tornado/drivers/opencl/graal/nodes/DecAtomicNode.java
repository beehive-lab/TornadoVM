package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(shortName = "INCREMENT_ATOMIC")
public class DecAtomicNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<DecAtomicNode> TYPE = NodeClass.create(DecAtomicNode.class);

    @Input(InputType.Extension)
    ValueNode atomicNode;

    public DecAtomicNode(ValueNode atomicValue) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.atomicNode = atomicValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

    }
}
