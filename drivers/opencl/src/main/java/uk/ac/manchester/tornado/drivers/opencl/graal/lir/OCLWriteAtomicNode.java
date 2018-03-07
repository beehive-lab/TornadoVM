package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode memory location}.
 */
@NodeInfo(nameTemplate = "OCLAtomicWrite#{p#location/s}")
public class OCLWriteAtomicNode extends AbstractWriteNode implements LIRLowerableAccess {
	
	 @Input(InputType.Association) private AddressNode address;
	 @Input private ValueNode accumulator;
	 private Stamp accStamp;

    public static final NodeClass<OCLWriteAtomicNode> TYPE = NodeClass.create(OCLWriteAtomicNode.class);

    public OCLWriteAtomicNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, ValueNode acc, Stamp accStamp) {
        super(TYPE, address, location, value, barrierType);

        this.address = address;
        this.accumulator = acc;
        this.accStamp = accStamp;
    }

    protected OCLWriteAtomicNode(NodeClass<? extends OCLWriteAtomicNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(c, address, location, value, barrierType);
        this.address = address;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
    	
    	// New OpenCL nodes for atomic add
    	
    	// ========= 
    	
    	OCLStamp oclStamp = new OCLStamp(OCLKind.ATOMIC_INT);
    	
    	//System.out.println("OCL STAMP? " + value().stamp());
    	
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(oclStamp);
        LIRKind accKind = gen.getLIRGeneratorTool().getLIRKind(accStamp);
        
        //System.out.println("EMIT: OCLWRITEATOMIC: " + writeKind);
        //System.out.println("EMIT: OCLWRITEATOMIC: " + gen.operand(address));
        //System.out.println("EMIT: OCLWRITEATOMIC: " + gen.operand(value()));
        //System.out.println("EMIT: OCLWRITEATOMIC: " + gen.operand(accumulator));

        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));
        gen.getLIRGeneratorTool().getArithmetic().emitStore(accKind, gen.operand(accumulator), gen.operand(value()), gen.state(this));
        
    	// ==========        
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public Stamp getAccessStamp() {
        return value().stamp();
    }
}