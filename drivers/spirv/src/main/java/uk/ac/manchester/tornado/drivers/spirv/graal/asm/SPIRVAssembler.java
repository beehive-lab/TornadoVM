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
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVPrimitiveTypes;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;

public final class SPIRVAssembler extends Assembler {

    public SPIRVModule module;
    public SPIRVInstScope functionScope;
    public SPIRVId mainFunctionID;
    public SPIRVId functionPre;
    public SPIRVInstScope currentBlockScope;

    // Table that stores the Block ID with its Label Reference ID
    public Map<String, SPIRVId> labelTable;
    public Map<String, SPIRVInstScope> blockTable;
    public Map<Integer, SPIRVId> parametersId;
    public SPIRVId prevId;
    public SPIRVId frameId;

    public final Map<String, SPIRVId> constants;
    public final Map<Value, SPIRVId> lirTable;
    public final Map<String, SPIRVId> lirTableName;
    public SPIRVPrimitiveTypes primitives;
    public SPIRVId pointerToGlobalMemoryHeap;
    public final Map<SPIRVOCLBuiltIn, SPIRVId> builtinTable;
    public SPIRVId v3ulong;
    public SPIRVId pointerToULongFunction;

    public SPIRVAssembler(TargetDescription target) {
        super(target);
        labelTable = new HashMap<>();
        blockTable = new HashMap<>();
        constants = new HashMap<>();
        parametersId = new HashMap<>();
        lirTable = new HashMap<>();
        lirTableName = new HashMap<>();
        builtinTable = new HashMap<>();

    }

    public void emitAttribute(SPIRVCompilationResultBuilder crb) {
        throw new RuntimeException("[Not supported for SPIR-V] FPGA ATTRIBUTES - Check with the OpenCL Backend");
    }

    public SPIRVInstScope emitBlockLabel(Block b, SPIRVInstScope functionScope) {
        String blockName = b.toString();
        return emitBlockLabel(blockName, functionScope);
    }

    public SPIRVInstScope emitBlockLabel(String labelName, SPIRVInstScope functionScope) {
        SPIRVId label = module.getNextId();
        module.add(new SPIRVOpName(label, new SPIRVLiteralString(labelName)));
        SPIRVInstScope block = functionScope.add(new SPIRVOpLabel(label));
        labelTable.put(labelName, label);
        blockTable.put(labelName, block);
        currentBlockScope = block;
        return block;
    }

    public SPIRVId emitOpTypeFunction(SPIRVId returnType, SPIRVId... operands) {
        functionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionPre, returnType, new SPIRVMultipleOperands<>(operands)));
        return functionPre;
    }

    public void emitEntryPointMainKernel(String kernelName, boolean isParallel) {
        mainFunctionID = module.getNextId();

        SPIRVMultipleOperands operands;
        if (isParallel) {
            // FIXME - Pending this - We should query exactly the builtins the code enables.
            operands = new SPIRVMultipleOperands(builtinTable.get(SPIRVOCLBuiltIn.GLOBAL_THREAD_ID), builtinTable.get(SPIRVOCLBuiltIn.GLOBAL_SIZE));
        } else {
            operands = new SPIRVMultipleOperands();
        }

        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), mainFunctionID, new SPIRVLiteralString(kernelName), operands));
    }

    public SPIRVId getFunctionPredefinition() {
        return functionPre;
    }

    public SPIRVId getMainKernelId() {
        return mainFunctionID;
    }

    public SPIRVInstScope emitOpFunction(SPIRVId voidType, SPIRVId functionID, SPIRVId functionPredefinition) {
        if (functionID == null || functionPredefinition == null) {
            throw new RuntimeException("MainFunction or FunctionPre SPIR-V IDs are null. It can't generate correct SPIR-V code");
        }
        functionScope = module.add(new SPIRVOpFunction(voidType, functionID, SPIRVFunctionControl.DontInline(), functionPredefinition));
        return functionScope;
    }

    public void emitParameterFunction(SPIRVId typeID, SPIRVId parameterId, SPIRVInstScope functionScope) {
        functionScope.add(new SPIRVOpFunctionParameter(typeID, parameterId));
    }

    public void closeFunction(SPIRVInstScope functionScope) {
        functionScope.add(new SPIRVOpFunctionEnd());
    }

    public void insertParameterId(int index, SPIRVId id) {
        parametersId.put(index, id);
    }

    public SPIRVId getParameterId(int parameterIndex) {
        return parametersId.get(parameterIndex);
    }

    public void registerLIRInstructionValue(Value valueLIRInstruction, SPIRVId spirvId) {
        lirTable.put(valueLIRInstruction, spirvId);
    }

    public void registerLIRInstructionValue(String valueLIRInstruction, SPIRVId spirvId) {
        lirTableName.put(valueLIRInstruction, spirvId);
    }

    public SPIRVId lookUpLIRInstructions(Value valueLIRInstruction) {
        return lirTable.get(valueLIRInstruction);
    }

    public SPIRVId lookUpLIRInstructionsName(String valueLIRInstruction) {
        return lirTableName.get(valueLIRInstruction);
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

    /**
     * Binary opcodes
     */
    public static class SPIRVBinaryOp extends SPIRVOp {

        public static final SPIRVBinaryOp ADD = new SPIRVBinaryOp("+");
        public static final SPIRVBinaryOp BITWISE_LEFT_SHIFT = new SPIRVBinaryOp("<<");;

        protected SPIRVBinaryOp(String opcode) {
            super(opcode);
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
        if (crb.getAssembler().lookUpLIRInstructions(value) == null) {
            System.out.println(">>>>>>>>>>>>>>>>>>>> VAR NAME: " + value.toString());
            SPIRVId id = crb.getAssembler().lookUpLIRInstructionsName(value.toString());
            crb.getAssembler().registerLIRInstructionValue(value, id);
        }
    }

    public void emitValueOrOp(SPIRVCompilationResultBuilder crb, Value value) {
        if (value instanceof SPIRVLIROp) {
            ((SPIRVLIROp) value).emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }
}
