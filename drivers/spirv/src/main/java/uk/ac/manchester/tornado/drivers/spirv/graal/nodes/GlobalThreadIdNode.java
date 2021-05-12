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
import uk.ac.manchester.tornado.runtime.graal.phases.MarkGlobalThreadID;

@NodeInfo(shortName = "SPIRV-Thread-ID")
public class GlobalThreadIdNode extends FloatingNode implements LIRLowerable, MarkGlobalThreadID {

    public static final NodeClass<GlobalThreadIdNode> TYPE = NodeClass.create(GlobalThreadIdNode.class);

    @Input
    protected ConstantNode dimensionIndex;

    public GlobalThreadIdNode(ConstantNode dimensionIndex) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.dimensionIndex = dimensionIndex;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        SPIRVLogger.trace("THREAD-ID FOR SPIRV: Operation not completed yet");
        // Complete operations here
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        Value valueDimension = generator.operand(dimensionIndex);
        LIRKind lirKind = tool.getLIRKind(stamp);
        tool.append(new SPIRVLIRStmt.AssignStmt(result, new SPIRVUnary.OpenCLBuiltinCallForSPIRV(SPIRVOCLBuiltIn.GLOBAL_THREAD_ID, lirKind, valueDimension)));
        generator.setResult(this, result);

    }
}
