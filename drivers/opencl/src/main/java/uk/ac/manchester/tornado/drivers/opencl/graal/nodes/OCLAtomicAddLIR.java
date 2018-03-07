package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node.Input;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName="atomic_add")
public class OCLAtomicAddLIR extends FixedNode implements LIRLowerable {

    public static final NodeClass<OCLAtomicAddLIR> TYPE = NodeClass.create(OCLAtomicAddLIR.class);
    
    public static final String ATOMIC_ADD = "atomic_add";


    @Input
    protected AddressNode address;
    
    @Input
    protected ValueNode value;
    
    public OCLAtomicAddLIR(AddressNode address, Stamp stamp,  ValueNode value) {
    	super(TYPE, stamp);
    	this.address = address;
    	this.value = value;
    }

  
    public AddressNode getAddress() {
        return address;
    }
    
    public ValueNode getValue() {
    	return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

    	//final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        //final OCLBinary.Expr declaration = new OCLBinary.Expr(OCLBinaryTemplate.NEW_ARRAY, lirKind, variable, lengthValue);

        //final OCLLIRStmt.ExprStmt expr = new OCLLIRStmt.ExprStmt(declaration);

        //gen.getLIRGeneratorTool().append(expr);

        gen.setResult(this, variable);
    }
}
