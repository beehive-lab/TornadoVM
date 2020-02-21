package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerator;

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

    public PTXAddressNode(ValueNode base, PTXMemoryBase memoryRegister) {
        this(base, null, memoryRegister);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        PTXLIRGenerator tool = (PTXLIRGenerator) gen.getLIRGeneratorTool();

        Value baseValue = base == null ? Value.ILLEGAL : gen.operand(base);
        Value indexValue = index == null ? Value.ILLEGAL : gen.operand(index);
        if (index == null) {
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, baseValue, false));
        }
        setMemoryAccess(gen, baseValue, indexValue, tool);
    }

    private boolean isLocalMemoryAccess() {
        return false;
        //return this.memoryRegister.name.equals(PTXAssemblerConstants.LOCAL_REGION_NAME);
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
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, baseValue, indexValue, false));
        } else {
            addressValue = tool.getArithmetic().emitAdd(baseValue, indexValue, false);
            gen.setResult(this, new PTXUnary.MemoryAccess(memoryRegister, addressValue, false));
        }
    }
}
