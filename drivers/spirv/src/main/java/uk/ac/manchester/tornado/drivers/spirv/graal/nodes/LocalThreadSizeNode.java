package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
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
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

@NodeInfo
public class LocalThreadSizeNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<LocalThreadSizeNode> TYPE = NodeClass.create(LocalThreadSizeNode.class);

    @Input
    protected ConstantNode dimensionIndex;

    public LocalThreadSizeNode(ConstantNode dimensionIndex) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        this.dimensionIndex = dimensionIndex;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        SPIRVLogger.traceBuildLIR("emitLocalGroupSize: dim=%s", dimensionIndex);
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        Value valueDimension = generator.operand(dimensionIndex);
        LIRKind lirKind = tool.getLIRKind(stamp);
        tool.append(new SPIRVLIRStmt.AssignStmt(result, new SPIRVUnary.OpenCLBuiltinCallForSPIRV(SPIRVOCLBuiltIn.WORKGROUP_SIZE, lirKind, valueDimension)));
        generator.setResult(this, result);
    }

}
