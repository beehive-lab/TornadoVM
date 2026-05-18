package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
/**
 * Graal IR node for
 * {@code KernelContext.mma(HalfFloat[], HalfFloat[], float[], MMAShape)}.
 *
 * <p>Warp-collective matrix multiply-accumulate: {@code D = A * B + C}.
 *
 * <p>Lowers to a single
 * {@code mma.sync.aligned.<shape>.row.col.f32.f16.f16.f32} instruction.
 * The shape parameter determines which instruction variant is emitted.
 *
 * <p>Extends {@link FixedWithNextNode} — {@code mma.sync} is warp-collective
 * and must not be hoisted or sunk out of the surrounding control flow.
 */
@NodeInfo(shortName = "MMACompute")
public class MMAComputeNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMAComputeNode> TYPE = NodeClass.create(MMAComputeNode.class);

    @Input private ValueNode fragA;
    @Input private ValueNode fragB;
    @Input private ValueNode fragC;

    private final MMAShape shape;

    public MMAComputeNode(ValueNode fragA, ValueNode fragB, ValueNode fragC, MMAShape shape) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.fragA = fragA;
        this.fragB = fragB;
        this.fragC = fragC;
        this.shape = shape;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
//        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
//        Value aVal = gen.operand(fragA);
//        Value bVal = gen.operand(fragB);
//        Value cVal = gen.operand(fragC);
//        Variable fragD = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_ACC_F32));
//        tool.append(new PTXLIRStmt.MMAComputeStmt(fragD, aVal, bVal, cVal, shape));
//        gen.setResult(this, fragD);
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value aVal = gen.operand(fragA);
        Value bVal = gen.operand(fragB);
        Value cVal = gen.operand(fragC);

        PTXKind accKind = (shape == MMAShape.M16N8K32)
                ? PTXKind.MMA_FRAG_ACC_S32
                : PTXKind.MMA_FRAG_ACC_F32;

        Variable fragD = tool.newVariable(LIRKind.value(accKind));
        tool.append(new PTXLIRStmt.MMAComputeStmt(fragD, aVal, bVal, cVal, shape));
        gen.setResult(this, fragD);
    }
}
