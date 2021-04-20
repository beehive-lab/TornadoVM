package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_BASE_OFFSET;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;

public class SPIRVGenTool {

    protected SPIRVLIRGenerator gen;

    public SPIRVGenTool(SPIRVLIRGenerator gen) {
        this.gen = gen;
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {
        trace("emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));
        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));
        SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();

        SPIRVTargetDescription target = (SPIRVTargetDescription) gen.target();

        Variable result = (spirvKind.isVector()) ? gen.newVariable(LIRKind.value(target.getSPIRVKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, index);

        if (spirvKind.isVector()) {
            throw new RuntimeException("Vector Parameter Load not supported yet");
        }

        return result;
    }

    /**
     * This represents a load from a parameter.
     *
     * This an example of the target code to generate:
     *
     * <code>
     *          %36 = OpBitcast %_ptr_CrossWorkgroup_ulong %ptridx   ;(while ptridx is a load of the heap address).
     *                OpStore %_frame %36 Aligned 8
     *          %37 = OpLoad %_ptr_CrossWorkgroup_ulong %_frame Aligned 8
     *     %ptridx1 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_ulong %37 %ulong_3
     *          %40 = OpLoad %ulong %ptridx1 Aligned 8
     *                OpStore %ul_0 %40 Aligned 8   
     * </code>
     *
     * @param resultValue
     *            result
     * @param index
     *            Parameter index to be loaded.
     *
     */
    private void emitParameterLoad(AllocatableValue resultValue, int index) {
        ConstantValue stackIndex = new ConstantValue(LIRKind.value(SPIRVKind.OP_TYPE_INT_32), JavaConstant.forInt((index + STACK_BASE_OFFSET) * SPIRVKind.OP_TYPE_INT_64.getSizeInBytes()));

        gen.append(null);
    }

}
