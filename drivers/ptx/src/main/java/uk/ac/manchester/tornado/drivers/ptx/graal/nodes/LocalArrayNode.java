package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkLocalArray;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXMemoryBase;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryTemplate;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

@NodeInfo
public class LocalArrayNode extends FixedNode implements LIRLowerable, MarkLocalArray {

    public static final NodeClass<LocalArrayNode> TYPE = NodeClass.create(LocalArrayNode.class);

    @Input
    protected ConstantNode length;

    protected PTXMemoryBase memoryRegister;
    protected PTXBinaryTemplate arrayTemplate;
    private PTXKind kind;

    public LocalArrayNode(PTXMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.kind = PTXKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = PTXKind.resolveTemplateType(elementType);
    }

    public PTXMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    public ConstantNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        trace("emitLocalArray length=%s kind=%s", length, kind);
        final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(kind);
        final Variable variable = ((PTXLIRGenerator) gen.getLIRGeneratorTool()).newVariable(lirKind, true);
        final PTXBinary.Expr declaration = new PTXBinary.Expr(arrayTemplate, lirKind, variable, lengthValue);

        final PTXLIRStmt.ExprStmt expr = new PTXLIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(expr);
        gen.setResult(this, variable);
    }
}
