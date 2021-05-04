package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public class GlobalThreadSizeNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GlobalThreadSizeNode> TYPE = NodeClass.create(GlobalThreadSizeNode.class);

    @Input
    protected ConstantNode dimension;

    public GlobalThreadSizeNode(ConstantNode dimension) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.dimension = dimension;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

        // This is similar to the Get_GLOBAL_ID node
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        // Complete operations here

        generator.setResult(this, result);
        throw new RuntimeException("Operation not supported yet");
    }
}
