package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.meta.PTXMemorySpace;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

@NodeInfo
public class PTXAddressNode extends AddressNode implements LIRLowerable {
    public static final NodeClass<PTXAddressNode> TYPE = NodeClass.create(PTXAddressNode.class);

    @OptionalInput
    private ValueNode base;

    @OptionalInput
    private ValueNode index;

    private PTXMemoryBase memoryRegister;

    public PTXAddressNode(ValueNode base, ValueNode index, PTXMemoryBase memoryRegister) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.memoryRegister = memoryRegister;
    }

    public PTXAddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        PTXLIRGenerator tool = (PTXLIRGenerator) gen.getLIRGeneratorTool();

        Value baseValue = base == null ? Value.ILLEGAL : gen.operand(base);
        Value indexValue = index == null ? Value.ILLEGAL : gen.operand(index);
        if (index == null) {
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, baseValue, null));
        }
        setMemoryAccess(gen, baseValue, indexValue, tool);
    }

    private boolean isLocalMemoryAccess() {
        return memoryRegister.memorySpace.index() == PTXMemorySpace.LOCAL.index();
    }

    private boolean isSharedMemoryAccess() {
        return memoryRegister.memorySpace.index() == PTXMemorySpace.SHARED.index();
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    @Override
    public long getMaxConstantDisplacement() {
        return 0;
    }

    private void setMemoryAccess(NodeLIRBuilderTool gen, Value baseValue, Value indexValue, PTXLIRGenerator tool) {
        Variable addressValue;
        if (isLocalMemoryAccess()) {
            Variable basePointer = tool.getArithmetic().emitUnaryAssign(PTXAssembler.PTXUnaryOp.MOV, LIRKind.value(PTXKind.U32), baseValue);
            Value indexOffset = tool.getArithmetic().emitMul(indexValue, new ConstantValue(LIRKind.value(PTXKind.U32), JavaConstant.forInt(baseValue.getPlatformKind().getSizeInBytes())), false);
            addressValue = tool.getArithmetic().emitAdd(basePointer, indexOffset, false);
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, addressValue, null));
        } else if (isSharedMemoryAccess()) {
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, baseValue, indexValue));
        } else {
            addressValue = tool.getArithmetic().emitAdd(baseValue, indexValue, false);
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, addressValue, null));
        }
    }
}
