package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;

@NodeInfo
public class OCLMemorySegmentArray extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<OCLMemorySegmentArray> TYPE = NodeClass.create(OCLMemorySegmentArray.class);

    @Input
    ParameterNode segmentParameter;

    public OCLMemorySegmentArray(ParameterNode segmentParameter) {
        super(TYPE, StampFactory.object());
        this.segmentParameter = segmentParameter;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRStmt.AssignStmt(result, gen.operand(segmentParameter)));
        gen.setResult(this, result);
    }
}
