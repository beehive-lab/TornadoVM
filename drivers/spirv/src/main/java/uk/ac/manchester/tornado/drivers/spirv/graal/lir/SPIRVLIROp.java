package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.ConstantValue;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public abstract class SPIRVLIROp extends Value {

    // protected SPIRVModule module;

    protected SPIRVLIROp(LIRKind valueKind) {
        super(valueKind);
    }

    protected SPIRVLIROp(LIRKind valueKind, SPIRVModule module) {
        super(valueKind);
        // this.module = module;
    }

    public final void emit(SPIRVCompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm);

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public SPIRVKind getSPIRVPlatformKind() {
        PlatformKind kind = getPlatformKind();
        return (kind instanceof SPIRVKind) ? (SPIRVKind) kind : SPIRVKind.ILLEGAL;
    }

    protected SPIRVId getId(Value inputValue, SPIRVAssembler asm, SPIRVKind spirvKind) {
        if (inputValue instanceof ConstantValue) {
            SPIRVKind kind = (SPIRVKind) inputValue.getPlatformKind();
            return asm.lookUpConstant(((ConstantValue) inputValue).getConstant().toValueString(), kind);
        } else {
            SPIRVId param = asm.lookUpLIRInstructions(inputValue);
            if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                // We need to perform a load first
                SPIRVLogger.traceCodeGen("emit LOAD Variable: " + inputValue);
                SPIRVId load = asm.module.getNextId();
                SPIRVId type = asm.primitives.getTypePrimitive(spirvKind);
                asm.currentBlockScope().add(new SPIRVOpLoad(//
                        type, //
                        load, //
                        param, //
                        new SPIRVOptionalOperand<>( //
                                SPIRVMemoryAccess.Aligned( //
                                        new SPIRVLiteralInteger(spirvKind.getByteCount())))//
                ));

                return load;
            } else {
                return param;
            }
        }
    }

    protected SPIRVId loadSPIRVId(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, Value x) {
        SPIRVId a;
        if (x instanceof SPIRVVectorElementSelect) {
            ((SPIRVVectorElementSelect) x).emit(crb, asm);
            a = asm.lookUpLIRInstructions(x);
        } else {
            a = getId(x, asm, (SPIRVKind) x.getPlatformKind());
        }
        return a;
    }
}
