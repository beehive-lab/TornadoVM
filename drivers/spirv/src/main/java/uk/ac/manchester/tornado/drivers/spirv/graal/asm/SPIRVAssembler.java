package uk.ac.manchester.tornado.drivers.spirv.graal.asm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.FRAME_REF_NAME;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.nodes.cfg.Block;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;

public final class SPIRVAssembler extends Assembler {

    public SPIRVModule module;
    public SPIRVInstScope functionScope;
    public SPIRVId mainFunctionID;
    public SPIRVId voidType;
    public SPIRVId functionPre;

    // Table that stores the Block ID with its Label Reference ID
    public Map<Integer, SPIRVId> labelTable;

    public SPIRVAssembler(TargetDescription target) {
        super(target);
        labelTable = new HashMap<>();
    }

    public void emitAttribute(SPIRVCompilationResultBuilder crb) {
        throw new RuntimeException("[Not supported for SPIR-V] FPGA ATTRIBUTES - Check with the OpenCL Backend");
    }

    public void emitBlockLabel(Block b) {
        SPIRVId label = module.getNextId();
        module.add(new SPIRVOpName(label, //
                new SPIRVLiteralString( //
                        Integer.toString(b.getId()) //
                )));
        labelTable.put(b.getId(), label);
        functionScope.add(new SPIRVOpLabel(label));
    }

    public void emitOpMainFunction(SPIRVId... operands) {
        functionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionPre, voidType, new SPIRVMultipleOperands<>(operands)));
    }

    public void emitEntryPointMainKernel(String kernelName) {
        mainFunctionID = module.getNextId();
        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), mainFunctionID, new SPIRVLiteralString(kernelName), new SPIRVMultipleOperands<>()));
    }

    public void emitOpFunction() {
        functionScope = module.add(new SPIRVOpFunction(voidType, mainFunctionID, SPIRVFunctionControl.DontInline(), functionPre));
    }

    public void closeFunction() {
        functionScope.add(new SPIRVOpFunctionEnd());
    }

    /**
     * Base class for SPIR-V opcodes.
     */
    public static class SPIRVOp {

        protected final String opcode;

        protected SPIRVOp(String opcode) {
            this.opcode = opcode;
        }

        protected final void emitOpcode(SPIRVAssembler asm) {
            asm.emit(opcode);
        }

        public boolean equals(SPIRVOp other) {
            return opcode.equals(other.opcode);
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    public static class SPIRVUnaryOp extends SPIRVOp {

        private final boolean prefix;

        protected SPIRVUnaryOp(String opcode) {
            this(opcode, false);
        }

        protected SPIRVUnaryOp(String opcode, boolean prefix) {
            super(opcode);
            this.prefix = prefix;
        }

        public static SPIRVUnaryOp CAST_TO_DOUBLE() {
            return null;
        }

        public static SPIRVUnaryOp CAST_TO_LONG() {
            // I think we can return an OpSConvert
            return null;
        }

        public void emit(SPIRVCompilationResultBuilder crb, Value x) {
            final SPIRVAssembler asm = crb.getAssembler();
            if (prefix) {
                emitOpcode(asm);
                asm.emitValueOrOp(crb, x);
            } else {
                asm.emitValueOrOp(crb, x);
                emitOpcode(asm);
            }
        }
    }

    public static class SPIRVUnaryTemplate extends SPIRVUnaryOp {
        // @formatter:off

        public static final SPIRVUnaryTemplate LOAD_PARAM_INT = new SPIRVUnaryTemplate("param", "(int) " + FRAME_REF_NAME + "[%s]");
        public static final SPIRVUnaryTemplate LOAD_PARAM_LONG = new SPIRVUnaryTemplate("param", "(long) " + FRAME_REF_NAME + "[%s]");
        
        // @formatter:on
        private final String template;

        protected SPIRVUnaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, Value x) {

        }

        public String getTemplate() {
            return template;
        }

    }

    public void emit(String str) {
        emitSubString(str);
    }

    public void emitSubString(String str) {
        guarantee(str != null, "emitting null string");
        for (byte b : str.getBytes()) {
            emitByte(b);
        }
    }

    @Override
    public void align(int modulus) {

    }

    @Override
    public void jmp(Label l) {

    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {

    }

    @Override
    public AbstractAddress makeAddress(Register base, int displacement) {
        return null;
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        return null;
    }

    @Override
    public void ensureUniquePC() {

    }

    public void emitValue(SPIRVCompilationResultBuilder crb, Value value) {
        // (toString(value));
    }

    public void emitValueOrOp(SPIRVCompilationResultBuilder crb, Value value) {
        if (value instanceof SPIRVLIROp) {
            ((SPIRVLIROp) value).emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }
}
