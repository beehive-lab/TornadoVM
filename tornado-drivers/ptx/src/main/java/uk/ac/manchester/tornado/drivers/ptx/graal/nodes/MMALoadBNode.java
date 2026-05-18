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

/**
 * Graal IR node for {@code KernelContext.mmaLoadB(float[], int)}.
 *
 * <p>Loads the B fragment for {@code mma.sync.m16n8k16}: 4 × f16 elements per
 * lane, packed into 2 × b32 registers ({@code rb0..rb1}).
 *
 * <p>The input tile is expected to be col-major in shared memory as required
 * by the {@code .col} specifier in the {@code mma.sync} instruction. Callers
 * arrange this layout in their cooperative load loop.
 *
 * <p>Lowers to 2 × {@code ld.shared.b32} with lane-specific offsets following
 * PTX ISA Table 108 B-operand col-major layout.
 */
@NodeInfo(shortName = "MMALoadB")
public class MMALoadBNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMALoadBNode> TYPE = NodeClass.create(MMALoadBNode.class);

    @Input private ValueNode tile;
    @Input private ValueNode wmmaK;

    public MMALoadBNode(ValueNode tile, ValueNode wmmaK) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tile = tile;
        this.wmmaK = wmmaK;
    }

//    @Override
//    public void generate(NodeLIRBuilderTool gen) {
//        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
//        Value tileVal = gen.operand(tile);
//        Value kVal = gen.operand(wmmaK);
//
//        Variable fragA = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_A_F16));
//
//        // Separate variables for each stage of the address computation —
//        // reusing one variable (as we did before) caused shl.b32 overwrites
//        // instead of additions in the emitted PTX.
//        LIRKind u32 = LIRKind.value(PTXKind.U32);
//        LIRKind u64 = LIRKind.value(PTXKind.U64);
//        Variable laneId       = tool.newVariable(u32);
//        Variable rowId        = tool.newVariable(u32);
//        Variable kOffset      = tool.newVariable(u32);
//        Variable rowComponent = tool.newVariable(u32);  // rowId * 8
//        Variable kComponent   = tool.newVariable(u32);  // kOffset * 2
//        Variable baseSlot     = tool.newVariable(u32);  // rowComponent + kComponent
//        Variable slotWithStep = tool.newVariable(u32);  // baseSlot + perLoadStep
//        Variable byteOffset   = tool.newVariable(u64);
//        Variable sharedBase   = tool.newVariable(u64);  // generic-addr-converted shared base
//        Variable address      = tool.newVariable(u64);
//        Variable tmpF32   = tool.newVariable(LIRKind.value(PTXKind.F32));
//        Variable tmpF16lo = tool.newVariable(LIRKind.value(PTXKind.F16));
//        Variable tmpF16hi = tool.newVariable(LIRKind.value(PTXKind.F16));
//
//        tool.append(new PTXLIRStmt.MMALoadStmt(
//                PTXLIRStmt.MMALoadStmt.Operand.B,  // or .B
//                fragA, tileVal, kVal,
//                laneId, rowId, kOffset,
//                rowComponent, kComponent, baseSlot, slotWithStep,
//                byteOffset, sharedBase, address,
//                tmpF32, tmpF16lo, tmpF16hi));
//
//        gen.setResult(this, fragA);
//    }
@Override
public void generate(NodeLIRBuilderTool gen) {
    LIRGeneratorTool tool = gen.getLIRGeneratorTool();
    Value tileVal = gen.operand(tile);

    Variable fragB = tool.newVariable(LIRKind.value(PTXKind.MMA_FRAG_B_F16));

    LIRKind u32 = LIRKind.value(PTXKind.U32);
    LIRKind u64 = LIRKind.value(PTXKind.U64);

    // B tile is 8 columns × K rows, col-major: each column = K f16 × 2 bytes = 32 bytes
    int rowStride = 32;

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
