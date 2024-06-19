package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;

import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

@NodeInfo
public class FixedArrayCopyNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<FixedArrayCopyNode> TYPE = NodeClass.create(FixedArrayCopyNode.class);

    @Input
    protected ValuePhiNode conditionalPhiNode;

    protected ResolvedJavaType elementType;
    //protected SPIRVAssembler.SPIRVBinaryTemplate pointerCopyTemplate;
    protected SPIRVArchitecture.SPIRVMemoryBase memoryRegister;

    public FixedArrayCopyNode(ValuePhiNode conditionalPhiNode, ResolvedJavaType elementType, SPIRVArchitecture.SPIRVMemoryBase memoryRegister) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.conditionalPhiNode = conditionalPhiNode;
        this.elementType = elementType;
        this.memoryRegister = memoryRegister;
        //this.pointerCopyTemplate = OCLKind.resolvePrivatePointerCopyTemplate(elementType);
    }

    public SPIRVArchitecture.SPIRVMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable ptr = gen.getLIRGeneratorTool().newVariable(lirKind);
        Value fixedArrayValue = gen.operand(conditionalPhiNode);
        final SPIRVLIRStmt.PrivateArrayPointerCopy ptrExpr = new SPIRVLIRStmt.PrivateArrayPointerCopy(lirKind, ptr, fixedArrayValue);
        gen.getLIRGeneratorTool().append(ptrExpr);
        gen.setResult(this, ptr);
    }
}
