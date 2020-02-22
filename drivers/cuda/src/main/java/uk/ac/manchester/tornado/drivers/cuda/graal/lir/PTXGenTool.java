package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.*;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.VectorLoadStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXUnary.PTXAddressCast;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorUtil;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXMemoryBase;
import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.globalSpace;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.STACK_BASE_OFFSET;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXGenTool {

    protected PTXLIRGenerator gen;

    public PTXGenTool(PTXLIRGenerator generator) {
        gen = generator;
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {
        trace("emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        // assert !(paramValue instanceof Variable) : "Creating a copy of a
        // variable via this method is not supported (and potentially a bug): "
        // + paramValue;
        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));

        PTXKind kind = (PTXKind) lirKind.getPlatformKind();

        CUDATargetDescription target = gen.target();

        Variable result = (kind.isVector()) ? gen.newVariable(LIRKind.value(target.getPTXKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, index);

        if (kind.isVector()) {

            Variable vector = gen.newVariable(lirKind);
            PTXMemoryBase base = globalSpace;
            PTXBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(kind);
            PTXAddressCast cast = new PTXAddressCast(base, LIRKind.value(kind.getElementKind()));
            PTXUnary.MemoryAccess address = new PTXUnary.MemoryAccess(base, result);

            emitVectorLoad(vector, intrinsic, new ConstantValue(LIRKind.value(PTXKind.S32), PrimitiveConstant.INT_0), cast, address);
            result = vector;
        }

        return result;
    }

    public void emitVectorLoad(AllocatableValue result, PTXBinaryIntrinsic op, Value index, PTXAddressCast cast, PTXUnary.MemoryAccess address) {
        trace("emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new VectorLoadStmt(result, op, index, cast, address));
    }

    private void emitParameterLoad(AllocatableValue dst, int index) {
        PTXKind kind = (PTXKind) dst.getPlatformKind();
        LIRKind lirKind = LIRKind.value(kind);
        final PTXUnaryOp op = getParameterLoadOp(kind);
        gen.append(new PTXLIRStmt.AssignStmt(
                dst,
                new PTXUnary.Expr(
                        op,
                        lirKind,
                        new ConstantValue(
                                LIRKind.value(PTXKind.S32),
                                JavaConstant .forInt(index + STACK_BASE_OFFSET)
                        )
                )
        ));
    }

    private PTXUnaryOp getParameterLoadOp(PTXKind type) {

        if (type.isVector()) {
            return PTXUnaryTemplate.LOAD_PARAM_U64;
        }

        switch (type) {

            case F64:
                return PTXUnaryTemplate.LOAD_PARAM_F64;
            case F32:
                return PTXUnaryTemplate.LOAD_PARAM_F32;
            case S32:
                return PTXUnaryTemplate.LOAD_PARAM_S32;
            case U32:
                return PTXUnaryTemplate.LOAD_PARAM_U32;
            case S64:
                return PTXUnaryTemplate.LOAD_PARAM_S64;
            case U64:
                return PTXUnaryTemplate.LOAD_PARAM_U64;
            default:
                unimplemented("parameter load: type=%s", type);
                break;
        }
        return null;
    }
}
