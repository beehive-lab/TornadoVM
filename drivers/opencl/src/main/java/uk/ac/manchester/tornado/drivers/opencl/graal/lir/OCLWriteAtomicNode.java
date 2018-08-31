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

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode
 * memory location}.
 */
@NodeInfo(nameTemplate = "OCLAtomicWrite#{p#location/s}")
public class OCLWriteAtomicNode extends AbstractWriteNode implements LIRLowerableAccess {

    @Input(InputType.Association) private AddressNode address;
    @Input private ValueNode accumulator;
    private Stamp accStamp;
    private JavaKind elementKind;
    private ATOMIC_OPERATION operation;

    //@formatter:off
    public enum ATOMIC_OPERATION {
        ADD,  
        MUL, 
        MAX,
        MIN,
        SUB,
        CUSTOM;
    }
    //@formatter:on

    public static final NodeClass<OCLWriteAtomicNode> TYPE = NodeClass.create(OCLWriteAtomicNode.class);

    public OCLWriteAtomicNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, ValueNode acc, Stamp accStamp, JavaKind elementKind,
            ATOMIC_OPERATION operation) {
        super(TYPE, address, location, value, barrierType);

        this.address = address;
        this.accumulator = acc;
        this.accStamp = accStamp;
        this.elementKind = elementKind;
        this.operation = operation;
    }

    protected OCLWriteAtomicNode(NodeClass<? extends OCLWriteAtomicNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(c, address, location, value, barrierType);
        this.address = address;
    }

    public OCLStamp getStampInt() {
        OCLStamp oclStamp = null;
        switch (operation) {
            case ADD:
                oclStamp = new OCLStamp(OCLKind.ATOMIC_ADD_INT);
                break;
            case MUL:
                oclStamp = new OCLStamp(OCLKind.ATOMIC_MUL_INT);
                break;
            default:
                throw new RuntimeException("Operation for reduction not supported yet: " + operation);
        }
        return oclStamp;
    }

    public OCLStamp getStampFloat() {
        OCLStamp oclStamp = null;
        switch (operation) {
            case ADD:
                oclStamp = new OCLStamp(OCLKind.ATOMIC_ADD_FLOAT);
                break;
            default:
                throw new RuntimeException("Operation for reduction not supported yet: " + operation);
        }
        return oclStamp;
    }

    public static void store() {

    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

        // New OpenCL nodes for atomic add
        OCLStamp oclStamp = null;
        switch (elementKind) {
            case Int:
                oclStamp = getStampInt();
                break;
            case Long:
                // DUE TO UNSUPPORTED FEATURE IN INTEL OpenCL PLATFORM
                oclStamp = new OCLStamp(OCLKind.ATOMIC_ADD_INT);
                break;
            case Float:
                oclStamp = getStampFloat();
                break;
            default:
                throw new RuntimeException("Data type for reduction not supported yet: " + elementKind);
        }

        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(oclStamp);
        LIRKind accKind = gen.getLIRGeneratorTool().getLIRKind(accStamp);

        // Atomic Store
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));

        // Update the accumulator
        gen.getLIRGeneratorTool().getArithmetic().emitStore(accKind, gen.operand(accumulator), gen.operand(value()), gen.state(this));
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