package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

public class LocalThreadIdFixedNode extends FixedWithNextNode implements LIRLowerable {

    public static NodeClass<LocalThreadIdFixedNode> TYPE = NodeClass.create(LocalThreadIdFixedNode.class);

    @Input
    protected ConstantNode dimensionIndex;

    public LocalThreadIdFixedNode(ConstantNode dimensionIndex) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.dimensionIndex = dimensionIndex;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        Value valueDimension = generator.operand(dimensionIndex);
        LIRKind lirKind = tool.getLIRKind(stamp);
        tool.append(new SPIRVLIRStmt.AssignStmt(result, new SPIRVUnary.OpenCLBuiltinCallForSPIRV(SPIRVOCLBuiltIn.GLOBAL_THREAD_ID, lirKind, valueDimension)));
        generator.setResult(this, result);
    }
}
