package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkLocalArray;

/**
 * Generates the LIR for declaring a SPIR-V array in local memory.
 */
@NodeInfo
public class LocalArrayNode extends FixedNode implements LIRLowerable, MarkLocalArray, MemoryKill {

    public static final NodeClass<LocalArrayNode> TYPE = NodeClass.create(LocalArrayNode.class);

    @Input
    protected ConstantNode length;

    protected SPIRVArchitecture.SPIRVMemoryBase memoryBase;
    protected SPIRVKind elementKind;

    public LocalArrayNode(SPIRVArchitecture.SPIRVMemoryBase memoryBase, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryBase = memoryBase;
        this.length = length;
        this.elementKind = SPIRVKind.fromJavaKind(elementType.getJavaKind());
    }

    public LocalArrayNode(SPIRVArchitecture.SPIRVMemoryBase memoryBase, JavaKind elementType, ConstantNode length) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.memoryBase = memoryBase;
        this.length = length;
        this.elementKind = SPIRVKind.fromJavaKind(elementType);
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

        final SPIRVBinary.LocalArrayAllocation localArray = new SPIRVBinary.LocalArrayAllocation(lirKind, resultArray, lengthValue);
        SPIRVLogger.traceBuildLIR("Local Array Allocation: " + resultArray + " with type: " + lirKind);

        generator.setResult(this, resultArray);
        tool.append(new SPIRVLIRStmt.LocalArrayAllocation(localArray));

    }
}
