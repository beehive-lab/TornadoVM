package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo(shortName = "AtomicAdd")
public class AtomicAddNodeTemplate extends FloatingNode implements LIRLowerable {

    public static final NodeClass<AtomicAddNodeTemplate> TYPE = NodeClass.create(AtomicAddNodeTemplate.class);

    @Input
    ValueNode array;
    @Input
    ValueNode index;
    @Input
    ValueNode inc;

    public AtomicAddNodeTemplate(ValueNode array, ValueNode index, ValueNode inc) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.array = array;
        this.index = index;
        this.inc = inc;

    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRStmt.AssignStmt(result, new OCLUnary.Intrinsic(OCLAssembler.OCLUnaryIntrinsic.ATOMIC_INC, tool.getLIRKind(stamp), gen.operand(inc))));
        gen.setResult(this, result);
    }
}