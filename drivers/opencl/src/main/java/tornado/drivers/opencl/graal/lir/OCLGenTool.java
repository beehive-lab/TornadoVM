package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodes.ParameterNode;
import jdk.vm.ci.meta.*;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryTemplate;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.VectorLoadStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;
import tornado.drivers.opencl.graal.lir.OCLUnary.OCLAddressCast;
import tornado.drivers.opencl.graal.nodes.vector.VectorUtil;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.STACK_BASE_OFFSET;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

public class OCLGenTool {

    protected OCLLIRGenerator gen;

    public OCLGenTool(OCLLIRGenerator gen) {
        this.gen = gen;
    }

    public void emitVectorLoad(AllocatableValue result, OCLBinaryIntrinsic op, Value index, OCLAddressCast cast, MemoryAccess address) {
        trace("emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new VectorLoadStmt(result, op, index, cast, address));
    }

    public Value emitParameterLoad(ParameterNode paramNode, int index) {

        trace("emitParameterLoad: stamp=%s", paramNode.stamp());

        // assert !(paramValue instanceof Variable) : "Creating a copy of a variable via this method is not supported (and potentially a bug): " + paramValue;
        LIRKind lirKind = gen.getLIRKind(paramNode.stamp());

        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();

        OCLTargetDescription oclTarget = gen.target();

        Variable result = (oclKind.isVector()) ? gen.newVariable(LIRKind.value(oclTarget.getOCLKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, index);

        if (oclKind.isVector()) {

            Variable vector = gen.newVariable(lirKind);
            OCLMemoryBase base = OCLArchitecture.hp;
            OCLBinaryIntrinsic intrinsic = VectorUtil.resolveLoadIntrinsic(oclKind);
            OCLAddressCast cast = new OCLAddressCast(base, LIRKind.value(oclKind.getElementKind()));
            MemoryAccess address = new MemoryAccess(base, result, false);

            emitVectorLoad(vector, intrinsic, new ConstantValue(LIRKind.value(OCLKind.INT), PrimitiveConstant.INT_0), cast, address);
            result = vector;
        }

        return result;
    }

    private OCLUnaryOp getParameterLoadOp(OCLKind type) {

        if (type.isVector()) {
            return OCLUnaryTemplate.LOAD_PARAM_OBJECT_ABS;
        }

        switch (type) {

            case DOUBLE:
                return OCLUnaryTemplate.LOAD_PARAM_DOUBLE;
            case FLOAT:
                return OCLUnaryTemplate.LOAD_PARAM_FLOAT;
            case INT:
                return OCLUnaryTemplate.LOAD_PARAM_INT;
            case LONG:
                return OCLUnaryTemplate.LOAD_PARAM_LONG;
            case ULONG:
//				return (Tornado.OPENCL_USE_RELATIVE_ADDRESSES) ? OCLUnaryTemplate.LOAD_PARAM_OBJECT_REL : OCLUnaryTemplate.LOAD_PARAM_OBJECT_ABS;
                return OCLUnaryTemplate.LOAD_PARAM_OBJECT_ABS;
            default:
                unimplemented();
                break;
        }
        return null;
    }

    private void emitParameterLoad(AllocatableValue dst, int index) {
        OCLKind oclKind = (OCLKind) dst.getPlatformKind();
        LIRKind lirKind = LIRKind.value(oclKind);
        final OCLUnaryOp op = getParameterLoadOp(oclKind);
        gen.append(new AssignStmt(dst, new OCLUnary.Expr(op, lirKind, new ConstantValue(LIRKind.value(OCLKind.INT), JavaConstant.forInt(index + STACK_BASE_OFFSET)))));
    }
}
