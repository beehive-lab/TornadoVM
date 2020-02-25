package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXUnary.MemoryAccess;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.globalSpace;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.STACK_BASE_OFFSET;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXGenTool {

    protected PTXLIRGenerator gen;

    public PTXGenTool(PTXLIRGenerator generator) {
        gen = generator;
    }

    public Value emitParameterLoad(ParameterNode paramNode, int paramOffset) {
        trace("emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        // assert !(paramValue instanceof Variable) : "Creating a copy of a
        // variable via this method is not supported (and potentially a bug): "
        // + paramValue;
        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));

        PTXKind kind = (PTXKind) lirKind.getPlatformKind();

        CUDATargetDescription target = gen.target();

        Variable result = (kind.isVector()) ? gen.newVariable(LIRKind.value(target.getPTXKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, paramOffset);

        if (kind.isVector()) {

            unimplemented("Vector parameter load is not supported in CUDA-PTX");
//            Variable vector = gen.newVariable(lirKind);
//            PTXMemoryBase base = globalSpace;
//            PTXBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(kind);
//            PTXAddressCast cast = new PTXAddressCast(base, LIRKind.value(kind.getElementKind()));
//            MemoryAccess address = new MemoryAccess(base, result);
//
//            emitVectorLoad(vector, intrinsic, new ConstantValue(LIRKind.value(PTXKind.S32), PrimitiveConstant.INT_0), cast, address);
//            result = vector;
        }

        return result;
    }

    private void emitParameterLoad(AllocatableValue dst, int index) {
        ConstantValue stackIndex = new ConstantValue(
                LIRKind.value(PTXKind.S32),
                JavaConstant.forInt(index + STACK_BASE_OFFSET)
        );
        gen.append(new PTXLIRStmt.LoadStmt(
                new MemoryAccess(globalSpace, gen.getParameterAllocation(PTXArchitecture.STACK_POINTER), stackIndex),
                (Variable) dst
        ));
    }

}
