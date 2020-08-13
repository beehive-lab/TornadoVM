package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary.MemoryAccess;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.globalSpace;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.STACK_BASE_OFFSET;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXGenTool {

    protected PTXLIRGenerator gen;

    public PTXGenTool(PTXLIRGenerator generator) {
        gen = generator;
    }

    public void emitVectorLoad(Variable result, MemoryAccess address) {
        trace("emitVectorLoad: %s = (%s) %s", result.toString(), result.getPlatformKind().toString(), address.toString());
        gen.append(new PTXLIRStmt.VectorLoadStmt(result, address));
    }

    public Value emitParameterLoad(ParameterNode paramNode, int paramOffset) {
        trace("emitParameterLoad: stamp=%s", paramNode.stamp(NodeView.DEFAULT));

        LIRKind lirKind = gen.getLIRKind(paramNode.stamp(NodeView.DEFAULT));

        PTXKind kind = (PTXKind) lirKind.getPlatformKind();

        PTXTargetDescription target = gen.target();

        Variable result = (kind.isVector()) ? gen.newVariable(LIRKind.value(target.getPTXKind(JavaKind.Object))) : gen.newVariable(lirKind);
        emitParameterLoad(result, paramOffset);

        if (kind.isVector()) {
            Variable vector = gen.newVariable(lirKind);
            PTXArchitecture.PTXMemoryBase base = globalSpace;
            MemoryAccess address = new MemoryAccess(base, result);

            emitVectorLoad(vector, address);
            result = vector;
        }

        return result;
    }

    private void emitParameterLoad(AllocatableValue dst, int index) {
        ConstantValue stackIndex = new ConstantValue(LIRKind.value(PTXKind.S32), JavaConstant.forInt(index * PTXKind.U64.getSizeInBytes() + STACK_BASE_OFFSET));

        gen.append(new PTXLIRStmt.LoadStmt(new MemoryAccess(globalSpace, gen.getParameterAllocation(PTXArchitecture.STACK_POINTER), stackIndex), (Variable) dst, PTXAssembler.PTXNullaryOp.LDU));
    }

}
