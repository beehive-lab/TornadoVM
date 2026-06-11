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
    @OptionalInput private ValueNode byteOffset;

    public MMALoadBInt8Node(ValueNode tile, ValueNode wmmaK) {
        this(tile, wmmaK, null);
    }


    public MMALoadBInt8Node(ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile;
        this.wmmaK = wmmaK;
        this.byteOffset = byteOffset;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);

        Variable fragB = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_B_S8));

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);

        // Canonical stacked layout: 16 b16-rows × 8 b16-cols, row-major.
        // Each b16 packs 2 s8 along K. rowStride = 8 b16 = 16 bytes.
        int rowStride = 16;

        if (byteOffset == null) {
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X2_TRANS,
                    fragB, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride));
        } else {
            Value byteOffsetVal = gen.operand(byteOffset);
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X2_TRANS,
                    fragB, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride, byteOffsetVal));
        }

        gen.setResult(this, fragB);
    }
}
