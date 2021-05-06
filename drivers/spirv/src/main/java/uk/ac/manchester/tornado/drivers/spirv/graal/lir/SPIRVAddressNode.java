package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;

@NodeInfo
public class SPIRVAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<SPIRVAddressNode> TYPE = NodeClass.create(SPIRVAddressNode.class);

    @OptionalInput
    private ValueNode base;

    @OptionalInput
    private ValueNode index;

    private SPIRVArchitecture.SPIRVMemoryBase memoryRegion;

    protected SPIRVAddressNode(ValueNode base, ValueNode index, SPIRVArchitecture.SPIRVMemoryBase memoryRegion) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.memoryRegion = memoryRegion;
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

    private Value genValue(NodeLIRBuilderTool generator, ValueNode value) {
        return (value == null) ? Value.ILLEGAL : generator.operand(value);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        SPIRVLIRGenerator tool = (SPIRVLIRGenerator) generator.getLIRGeneratorTool();

        Value baseValue = genValue(generator, base);
        Value indexValue = genValue(generator, index);
        if (index == null) {
            throw new RuntimeException("Operation not supported -- INDEX IS NULL");
            // generator.setResult(this, new SPIRVUnary.MemoryAccess(memoryRegion,
            // baseValue, false));
        }
        setMemoryAccess(generator, baseValue, indexValue, tool);
    }

    private void setMemoryAccess(NodeLIRBuilderTool generator, Value baseValue, Value indexValue, SPIRVLIRGenerator tool) {
        System.out.println("SET ADDRESS NODE MISSING");
        // throw new RuntimeException("Operation not supported");
    }
}
