package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class GetArrayNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GetArrayNode> TYPE = NodeClass.create(GetArrayNode.class);
    private final PTXKind ptxKind;

    @Input
    private ValueNode arrayNode;

    private JavaKind elementKind;

    public GetArrayNode(PTXKind ptxKind, ValueNode array, JavaKind elementKind) {
        super(TYPE, StampFactory.forKind(ptxKind.asJavaKind()));
        this.ptxKind = ptxKind;
        this.arrayNode = array;
        this.elementKind = elementKind;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(PTXStampFactory.getStampFor(ptxKind));
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new PTXLIRStmt.AssignStmt(result, generator.operand(arrayNode)));
        generator.setResult(this, result);
    }
}