package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
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

/**
 * Graal IR node for {@code KernelContext.mmaLoadA(float[], int)}.
 *
 * <p>Loads the A fragment for {@code mma.sync.m16n8k16}: 8 × f16 elements per
 * lane, packed into 4 × b32 registers ({@code ra0..ra3}).
 *
 * <p>Lowers to 4 × {@code ld.shared.b32} with lane-specific offsets following
 * PTX ISA Table 108 A-operand row-major layout.
 */
@NodeInfo(shortName = "MMALoadA")
public class MMALoadANode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMALoadANode> TYPE = NodeClass.create(MMALoadANode.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;
    @OptionalInput private ValueNode byteOffset;

    public MMALoadANode(ValueNode tile, ValueNode wmmaK) {
          this(tile, wmmaK, null);
      }
      public MMALoadANode(ValueNode tile, ValueNode wmmaK, ValueNode byteOffset) {
          super(TYPE, StampFactory.forKind(JavaKind.Object));
          this.tile = tile;
          this.wmmaK = wmmaK;
          this.byteOffset = byteOffset;
      }
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value tileVal = gen.operand(tile);

        Variable fragA = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_A_F16));

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);

        // rowStride = 16 b16 × 2 bytes = 32 bytes for 16-wide f16 row
        int rowStride = 32;

        if (byteOffset == null) {
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X4,
                    fragA, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride));
        } else {
            Value byteOffsetVal = gen.operand(byteOffset);
            tool.append(new PTXLIRStmt.LdmatrixStmt(
                    PTXLIRStmt.LdmatrixStmt.Variant.X4,
                    fragA, tileVal,
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u32),
                    tool.newVariable(u32), tool.newVariable(u32), tool.newVariable(u64),
                    rowStride, byteOffsetVal));
        }

        gen.setResult(this, fragA);
    }
}
