package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class PTXDecompressedReadFieldNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PTXDecompressedReadFieldNode> TYPE = NodeClass.create(PTXDecompressedReadFieldNode.class);
    @Input
    private ValueNode object;
    @Input
    private AddressNode address;

    public PTXDecompressedReadFieldNode(ValueNode object, AddressNode address, Stamp stamp) {
        super(TYPE, stamp);
        this.object = object;
        this.address = address;
    }

    public ValueNode getObject() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        PTXLIRGenerator tool = (PTXLIRGenerator) gen.getLIRGeneratorTool();
        // 1. Get operands
        // This is 'this' (e.g., ul_0)
        Value addressBase = gen.operand(address.getBase());
        // This is the offset (e.g., 12L)
        Value index = gen.operand(address.getIndex());
        // 2. Calculate field address
        // This is 'ul_5 = ul_0 + 12L'
        Variable fieldAddress = tool.getArithmetic().emitAdd(addressBase, index, false);
        // 3. Read 4-byte compressed pointer from field address
        // This will be 'ui_6'
        Variable compressedPointer = tool.newVariable(LIRKind.value(PTXKind.U32));
        // Emits: 'ui_6 = *((__global uint *) ul_5);'
        tool.append(new PTXLIRStmt.CastCompressedStmt(compressedPointer, fieldAddress));
        // 4. Decompress the pointer
        // This will be 'ul_7'
        Variable decompressedPointer = tool.newVariable(LIRKind.value(PTXKind.U64));
        // Emits: 'ul_7 = ul_0 + ((ulong) ui_6 << 3);'
        // NOTE: We pass 'addressBase' (ul_0), NOT 'fieldAddress' (ul_5)
        Variable temp64 = tool.newVariable(LIRKind.value(PTXKind.U64));               // Temporary for cvt
        Variable tempShifted = tool.newVariable(LIRKind.value(PTXKind.U64));          // Temporary for shl
        tool.append(new PTXLIRStmt.DecompressPointerStmt(decompressedPointer, addressBase, compressedPointer, temp64, tempShifted));
        // 5. Set the result of this node
        gen.setResult(this, decompressedPointer);
    }

}
