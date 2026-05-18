package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaConstant;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

/**
 * Graal IR node for
 * {@code KernelContext.mmaStore(float[], FloatArray, int, int, int)}.
 *
 * <p>Stores the D fragment to a global output matrix. Each lane writes 4 × f32
 * elements to its owned positions in the 16×8 output panel following PTX ISA
 * Table 108 C/D-operand row-major layout.
 *
 * <p>Lowers to 4 × {@code st.global.f32} with lane-specific offsets.
 */
@NodeInfo(shortName = "MMAStore")
public class MMAStoreNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<MMAStoreNode> TYPE = NodeClass.create(MMAStoreNode.class);

    @Input private ValueNode fragD;
    @Input private ValueNode target;
    @Input private ValueNode tileRow;
    @Input private ValueNode tileCol;
    @Input private ValueNode dimN;

    private final int headerElements;
    private final boolean isInt8;

    public MMAStoreNode(ValueNode fragD, ValueNode target,
                        ValueNode tileRow, ValueNode tileCol, ValueNode dimN,
                        int headerElements, boolean isInt8) {
        super(TYPE, StampFactory.forVoid());
        this.fragD = fragD;
        this.target = target;
        this.tileRow = tileRow;
        this.tileCol = tileCol;
        this.dimN = dimN;
        this.headerElements = headerElements;
        this.isInt8 = isInt8;
    }

    public MMAStoreNode(ValueNode fragD, ValueNode target,
                        ValueNode tileRow, ValueNode tileCol, ValueNode dimN,
                        int headerElements) {
        super(TYPE, StampFactory.forVoid());
        this.fragD = fragD;
        this.target = target;
        this.tileRow = tileRow;
        this.tileCol = tileCol;
        this.dimN = dimN;
        this.headerElements = headerElements;
        this.isInt8 = false;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value dVal = gen.operand(fragD);
        Value targetVal = gen.operand(target);
        Value rowVal = gen.operand(tileRow);
        Value colVal = gen.operand(tileCol);
        Value dimNVal = gen.operand(dimN);

        LIRKind u32 = LIRKind.value(PTXKind.U32);
        LIRKind u64 = LIRKind.value(PTXKind.U64);
        Variable laneId        = tool.newVariable(u32);
        Variable rowInTile     = tool.newVariable(u32);
        Variable colInTile     = tool.newVariable(u32);
        Variable globalRow     = tool.newVariable(u32);
        Variable globalCol     = tool.newVariable(u32);
        Variable elementOffset = tool.newVariable(u32);
        Variable byteOffset    = tool.newVariable(u64);
        Variable address       = tool.newVariable(u64);

        Value headerSize = new ConstantValue(u32, JavaConstant.forInt(headerElements));

        tool.append(new PTXLIRStmt.MMAStoreStmt(
                dVal, targetVal, rowVal, colVal, dimNVal,
                laneId, rowInTile, colInTile, globalRow, globalCol,
                elementOffset, byteOffset, address, headerSize, isInt8));
    }
}