package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;

/**
 * Fixed Node for private array allocation.
 */
@NodeInfo
public class FixedArrayNode extends FixedNode implements LIRLowerable {

    public static final NodeClass<FixedArrayNode> TYPE = NodeClass.create(FixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected SPIRVKind elementKind;
    protected SPIRVArchitecture.SPIRVMemoryBase memoryBase;

    public FixedArrayNode(SPIRVArchitecture.SPIRVMemoryBase memoryBase, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryBase = memoryBase;
        this.length = length;
        this.elementKind = SPIRVKind.fromJavaKind(elementType.getJavaKind());
    }

    public SPIRVArchitecture.SPIRVMemoryBase getMemoryRegister() {
        return memoryBase;
    }

    public ConstantNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

        final Value lengthValue = generator.operand(length);
        LIRKind lirKind = LIRKind.value(elementKind);
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        final AllocatableValue resultArray = tool.newVariable(lirKind);

        final SPIRVBinary.PrivateArrayAllocation privateAllocationExpr = new SPIRVBinary.PrivateArrayAllocation(lirKind, resultArray, lengthValue);

        SPIRVLogger.traceBuildLIR("Private Array Allocation: " + resultArray + " with type: " + lirKind);
        generator.setResult(this, resultArray);

        tool.append(new SPIRVLIRStmt.PrivateArrayAllocation(privateAllocationExpr));
    }
}
