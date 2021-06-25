package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

@NodeInfo
public class GetArrayNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GetArrayNode> TYPE = NodeClass.create(GetArrayNode.class);
    private final SPIRVKind spirvKind;

    @Input
    private ValueNode arrayNode;

    private JavaKind elementKind;

    public GetArrayNode(SPIRVKind spirvKind, ValueNode array, JavaKind elementKind) {
        super(TYPE, SPIRVStampFactory.getStampFor(spirvKind));
        this.spirvKind = spirvKind;
        this.arrayNode = array;
        this.elementKind = elementKind;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(SPIRVStampFactory.getStampFor(spirvKind));
    }

    public SPIRVKind getSpirvKind() {
        return spirvKind;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new SPIRVLIRStmt.AssignStmtWithLoad(result, generator.operand(arrayNode)));
        generator.setResult(this, result);
    }
}