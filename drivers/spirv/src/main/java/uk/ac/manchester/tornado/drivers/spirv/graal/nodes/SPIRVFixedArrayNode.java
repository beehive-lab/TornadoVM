package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

@NodeInfo
public class SPIRVFixedArrayNode extends FixedNode implements LIRLowerable {

    public static final NodeClass<SPIRVFixedArrayNode> TYPE = NodeClass.create(SPIRVFixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected SPIRVKind elementKind;
    protected SPIRVArchitecture.SPIRVMemoryBase memoryBase;
    protected ResolvedJavaType elemenType;
    // FIXME SPIRVBinarTemplate is missing

    protected SPIRVFixedArrayNode(SPIRVArchitecture.SPIRVMemoryBase memoryBase, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryBase = memoryBase;
        this.length = length;
        this.elemenType = elementType;
        this.elementKind = SPIRVKind.fromResolvedJavaType(elementType);
        System.out.println("Memory template is missing");
    }

    public SPIRVArchitecture.SPIRVMemoryBase getMemoryRegister() {
        return memoryBase;
    }

    public ConstantNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

    }
}
