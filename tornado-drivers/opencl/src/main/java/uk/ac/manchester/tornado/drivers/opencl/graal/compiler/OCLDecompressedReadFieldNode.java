package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

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
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;

@NodeInfo
public class OCLDecompressedReadFieldNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<OCLDecompressedReadFieldNode> TYPE = NodeClass.create(OCLDecompressedReadFieldNode.class);
    @Input
    private ValueNode object;
    @Input
    private AddressNode address;

    public OCLDecompressedReadFieldNode(ValueNode object, AddressNode address, Stamp stamp) {
        super(TYPE, stamp);
        this.object = object;
        this.address = address;
    }

    public ValueNode getObject() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
//        OCLLIRGenerator tool = (OCLLIRGenerator) gen.getLIRGeneratorTool();
//        Value addressBase = gen.operand(address.getBase());
//        Value index = gen.operand(address.getIndex());
//        Variable addressValue = tool.getArithmetic().emitAdd(addressBase, index, false);
//        Variable compressedPointer = tool.newVariable(LIRKind.value(OCLKind.UINT));
//        tool.append(new OCLLIRStmt.CastCompressedStmt(compressedPointer, addressValue));
//        Value shiftValue = tool.emitConstant(LIRKind.value(OCLKind.INT), new RawConstant(3));
//        Variable decompressedPointer = tool.getArithmetic().emitAdd(addressValue, compressedPointer, false);
//        tool.append(new OCLLIRStmt.DecompressPointerStmt(decompressedPointer, addressValue, compressedPointer, shiftValue));
//        gen.setResult(this, decompressedPointer);
        OCLLIRGenerator tool = (OCLLIRGenerator) gen.getLIRGeneratorTool();
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
        Variable compressedPointer = tool.newVariable(LIRKind.value(OCLKind.UINT));
        // Emits: 'ui_6 = *((__global uint *) ul_5);'
        tool.append(new OCLLIRStmt.CastCompressedStmt(compressedPointer, fieldAddress));
        // 4. Decompress the pointer
        // This will be 'ul_7'
        Variable decompressedPointer = tool.newVariable(LIRKind.value(OCLKind.ULONG));
        // Emits: 'ul_7 = ul_0 + ((ulong) ui_6 << 3);'
        // NOTE: We pass 'addressBase' (ul_0), NOT 'fieldAddress' (ul_5)
        tool.append(new OCLLIRStmt.DecompressPointerStmt(decompressedPointer, addressBase, compressedPointer));
        // 5. Set the result of this node
        gen.setResult(this, decompressedPointer);
    }
}
