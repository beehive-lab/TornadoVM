package tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryTemplate;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt;

@NodeInfo
public class OCLAtomicAdd extends FloatingNode implements LIRLowerable {

    public static final NodeClass<OCLAtomicAdd> TYPE = NodeClass.create(OCLAtomicAdd.class);

    @Input
    protected ConstantNode length;

    protected OCLKind elementKind;
    protected OCLMemoryBase memoryRegister;

    protected AddressNode address;
    protected ValueNode value;
    
    public OCLAtomicAdd(AddressNode address, ResolvedJavaType elementType, ValueNode value) {
    	super(TYPE, null);
    	//super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
    	this.address = address;
    	this.value = value;
    }

    public OCLAtomicAdd(OCLMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elementKind = OCLKind.fromResolvedJavaType(elementType);
    }

    public OCLAtomicAdd(ResolvedJavaType elementType, ConstantNode length) {
        this(OCLArchitecture.hp, elementType, length);
    }

    public OCLMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

    	final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        final OCLBinary.Expr declaration = new OCLBinary.Expr(OCLBinaryTemplate.NEW_ARRAY, lirKind, variable, lengthValue);

        final OCLLIRStmt.ExprStmt expr = new OCLLIRStmt.ExprStmt(declaration);

        gen.getLIRGeneratorTool().append(expr);

        gen.setResult(this, variable);
    }
}
