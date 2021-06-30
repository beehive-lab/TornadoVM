package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_BASE_OFFSET;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;

/**
 * This class specifies how to load a parameter to the kernel from the TornadoVM
 * Stack-Frame.
 */
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
            Variable vectorToLoad = generator.newVariable(lirKind);
            SPIRVArchitecture.SPIRVMemoryBase base = SPIRVArchitecture.globalSpace;
            SPIRVUnary.MemoryAccess address = new SPIRVUnary.MemoryAccess(base, result);
            SPIRVUnary.SPIRVAddressCast cast = new SPIRVUnary.SPIRVAddressCast(address, base, lirKind);
            generator.append(new SPIRVLIRStmt.LoadVectorStmt(vectorToLoad, cast, address));
            result = vectorToLoad;
        }

        return result;
    }

    private void emitParameterLoad(AllocatableValue resultValue, int index) {
        SPIRVKind spirvKind = (SPIRVKind) resultValue.getPlatformKind();
        LIRKind lirKind = LIRKind.value(spirvKind);

        // ConstantValue stackIndex = new
        // ConstantValue(LIRKind.value(SPIRVKind.OP_TYPE_INT_32),
        // JavaConstant.forInt((index + STACK_BASE_OFFSET) *
        // SPIRVKind.OP_TYPE_INT_64.getSizeInBytes()));

        // Implement an LIR utility to load the ptr parameter from the TornadoVM call
        // stack
        // generator.append(new
        // SPIRVLIRStmt.LoadFromStackFrame(SPIRVKind.OP_TYPE_INT_64, (STACK_BASE_OFFSET
        // + index), index));

        // generator.append(new SPIRVLIRStmt.LoadFrame(SPIRVKind.OP_TYPE_INT_64));
        // System.out.println("INDEX: " + index);
        // generator.append(new SPIRVLIRStmt.AccessPointerChain(STACK_BASE_OFFSET));

        // SPIRVLIRStmt.AssignStmt assignStmt = new SPIRVLIRStmt.AssignStmt(resultValue,
        // new SPIRVUnary.Expr(op, lirKind, stackIndex));

        // ASSIGN ( result, LOAD_FROM_STACK_FRAME_EXPR)
        SPIRVLIRStmt.ASSIGNParameter assignStmt = new SPIRVLIRStmt.ASSIGNParameter( //
                resultValue, //
                new SPIRVUnary.LoadFromStackFrameExpr( //
                        lirKind, //
                        SPIRVKind.OP_TYPE_INT_64, //
                        (STACK_BASE_OFFSET + index), //
                        index), //
                SPIRVKind.OP_TYPE_INT_64.getSizeInBytes(), //
                index); //

        generator.append(assignStmt);
    }

}
