package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_BASE_OFFSET;

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
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;

public class SPIRVGenTool {

    protected SPIRVLIRGenerator generator;

    public SPIRVGenTool(SPIRVLIRGenerator gen) {
        this.generator = gen;
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {
        SPIRVLogger.trace("emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));
        LIRKind lirKind = generator.getLIRKind(paramNode.stamp(NodeView.DEFAULT));
        SPIRVKind spirvKind = (SPIRVKind) lirKind.getPlatformKind();

        SPIRVTargetDescription target = (SPIRVTargetDescription) generator.target();

        Variable result = (spirvKind.isVector()) ? generator.newVariable(LIRKind.value(target.getSPIRVKind(JavaKind.Object))) : generator.newVariable(lirKind);
        emitParameterLoad(result, index);

        if (spirvKind.isVector()) {
            throw new RuntimeException("Vector Parameter Load not supported yet");
        }

        return result;
    }

    /**
     * This represents a load from a parameter from the stack-frame.
     * 
     * The equivalent in OpenCL is as follows:
     * 
     * <code>
     *     ulong_0 = (ulong) _frame[STACK_INDEX];
     * </code>
     *
     * This an example of the target code to generate in SPIR-V:
     *
     * <code>
     *          %31 = OpLoad %_ptr_Function_ulong_0 %frame Aligned 8                ; Load Frame in private mem
     *          %32 = OpInBoundsPtrAccessChain %_ptr_Function_ulong_0 %31 %ulong_3  ; Access position 3 of frame
     *          %33 = OpLoad %ulong %32 Aligned 8                                   ; Load address of position 3
     *          OpStore %ul0 %33 Aligned 8                                          ; Store in ul0 
     * </code>
     *
     * @param resultValue
     *            result
     * @param index
     *            Parameter index to be loaded.
     *
     */
    private void emitParameterLoad(AllocatableValue resultValue, int index) {
        SPIRVKind spirvKind = (SPIRVKind) resultValue.getPlatformKind();
        LIRKind lirKind = LIRKind.value(spirvKind);
        ConstantValue stackIndex = new ConstantValue(LIRKind.value(SPIRVKind.OP_TYPE_INT_32), JavaConstant.forInt((index + STACK_BASE_OFFSET) * SPIRVKind.OP_TYPE_INT_64.getSizeInBytes()));

        generator.append(null);
    }

}
