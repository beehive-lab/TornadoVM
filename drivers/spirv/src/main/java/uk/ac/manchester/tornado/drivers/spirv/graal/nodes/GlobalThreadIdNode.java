package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkGlobalThreadID;

@NodeInfo
public class GlobalThreadIdNode extends FloatingNode implements LIRLowerable, MarkGlobalThreadID {

    public static final NodeClass<GlobalThreadIdNode> TYPE = NodeClass.create(GlobalThreadIdNode.class);

    @Input
    protected ConstantNode dimensionIndex;

    public GlobalThreadIdNode(ConstantNode dimensionIndex) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.dimensionIndex = dimensionIndex;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {

        // This should generate the following SPIR-V instruction sequence:

        /**
         * <code>
         *          %37 = OpLoad %v3ulong %__spirv_BuiltInGlobalInvocationId Aligned 32
         *        %call = OpCompositeExtract %ulong %37 0
         *        %conv = OpUConvert %uint %call
         *                OpStore %i_1 %conv Aligned 4
         * </code>
         */
        // We should get the function SPIRVFunctionID I am in from the generator
        // ALso we need:
        // - ID of the intrinsic
        // - ID of the type v3long

        // Possible sequence:
        // LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        // Variable variable = tool.newVariable(XXX);
        //
        // result = Load(TYPEV3Long, ID_GET_GLOBAL_ID);
        // callExpression = Expression(CompositeExtreact, TYPE, Result, Index)
        // Store(variable, Convert(callExpression, UINT))
        // gen.setResult(this, variable);

        // In the OpenCL backend:
        // tool.append(Assignemtn(result, Unary(Intrinsic.GLOBAL_ID)));

        // Complete operations here

        throw new RuntimeException("Unsupported operation");
    }
}
