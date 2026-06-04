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
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo(shortName = "MMALoadAInt8")
public class MMALoadAInt8Node extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMALoadAInt8Node> TYPE = NodeClass.create(MMALoadAInt8Node.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;

    public MMALoadAInt8Node(ValueNode tile, ValueNode wmmaK) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile;
        this.wmmaK = wmmaK;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);

        Variable fragA = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_A_S8));

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);

        // 16×32 s8 viewed as 16×16 b16: rowStride = 16 b16 × 2 bytes = 32
        int rowStride = 32;

        tool.append(new PTXLIRStmt.LdmatrixStmt(
                PTXLIRStmt.LdmatrixStmt.Variant.X4,
                fragA, tileVal,
                tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                rowStride));

        gen.setResult(this, fragA);
    }
}
