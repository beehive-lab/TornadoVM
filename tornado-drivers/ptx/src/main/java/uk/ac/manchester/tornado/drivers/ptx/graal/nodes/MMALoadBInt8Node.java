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

@NodeInfo(shortName = "MMALoadBInt8")
public class MMALoadBInt8Node extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMALoadBInt8Node> TYPE = NodeClass.create(MMALoadBInt8Node.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;

    public MMALoadBInt8Node(ValueNode tile, ValueNode wmmaK) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile;
        this.wmmaK = wmmaK;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);

        Variable fragB = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_B_S8));

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);

        // 8×32 s8 viewed as 8×16 b16: rowStride = 16 b16 × 2 = 32 bytes
        // Wait — B is col-major: K rows × 8 cols. As b16: K/2 rows × 8 cols.
        // For K=16: 8 rows × 8 cols of b16. rowStride = 8 b16 × 2 = 16 bytes
        // For K=32: 16 rows × 8 cols of b16. rowStride = 8 b16 × 2 = 16 bytes
        int rowStride = 16;

        tool.append(new PTXLIRStmt.LdmatrixStmt(
                PTXLIRStmt.LdmatrixStmt.Variant.X2_TRANS,
                fragB, tileVal,
                tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                rowStride));

        gen.setResult(this, fragB);
    }
}
