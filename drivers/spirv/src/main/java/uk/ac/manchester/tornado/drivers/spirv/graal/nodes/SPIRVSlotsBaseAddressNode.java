package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public class SPIRVSlotsBaseAddressNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<SPIRVSlotsBaseAddressNode> TYPE = NodeClass.create(SPIRVSlotsBaseAddressNode.class);

    public SPIRVSlotsBaseAddressNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

    }
}
