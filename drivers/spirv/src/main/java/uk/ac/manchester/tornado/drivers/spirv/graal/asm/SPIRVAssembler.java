package uk.ac.manchester.tornado.drivers.spirv.graal.asm;

import static uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.FRAME_REF_NAME;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.nodes.cfg.Block;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExecutionMode;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFAdd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFDiv;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFMul;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFSub;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIEqual;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIMul;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpISub;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSDiv;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSLessThan;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpShiftLeftLogical;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentDouble;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentFloat;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVExecutionMode;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVOCLBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVPrimitiveTypes;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;

public final class SPIRVAssembler extends Assembler {

    public static class ConstantKeyPair {
        private String name;
        private SPIRVKind kind;

        public ConstantKeyPair(String name, SPIRVKind kind) {
            this.name = name;
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public SPIRVKind getKind() {
            return kind;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((kind == null) ? 0 : kind.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConstantKeyPair) {
                ConstantKeyPair ckp = (ConstantKeyPair) obj;
                return (this.name.equals(ckp.name) && this.kind.equals(ckp.kind));
            }
            return false;
        }
    }

    public SPIRVModule module;
    public SPIRVInstScope functionScope;
    public SPIRVId mainFunctionID;
    public SPIRVId functionPre;

    public final Stack<SPIRVInstScope> currentBlockScopeStack;

    // Table that stores the Block ID with its Label Reference ID
    public Map<String, SPIRVId> labelTable;
    public Map<String, SPIRVInstScope> blockTable;
    public Map<Integer, SPIRVId> parametersId;
    public SPIRVId prevId;
    public SPIRVId frameId;

    private final Map<ConstantKeyPair, SPIRVId> constants;
    public final Map<Value, SPIRVId> lirTable;
    public final Map<String, SPIRVId> lirTableName;
    public SPIRVPrimitiveTypes primitives;
    public SPIRVId pointerToGlobalMemoryHeap;
    public final Map<SPIRVOCLBuiltIn, SPIRVId> builtinTable;
    public SPIRVId v3ulong;
    public SPIRVId pointerToULongFunction;
    public SPIRVId ptrCrossWorkULong;

    public SPIRVAssembler(TargetDescription target) {
        super(target);
        labelTable = new HashMap<>();
        blockTable = new HashMap<>();
        constants = new HashMap<>();
        parametersId = new HashMap<>();
        lirTable = new HashMap<>();
        lirTableName = new HashMap<>();
        builtinTable = new HashMap<>();
        currentBlockScopeStack = new Stack<>();

    }

    public SPIRVInstScope currentBlockScope() {
        return currentBlockScopeStack.peek();
    }

    public void pushScope(SPIRVInstScope scope) {
        currentBlockScopeStack.push(scope);
    }

    public SPIRVInstScope popScope() {
        return currentBlockScopeStack.pop();
    }

    public void emitAttribute(SPIRVCompilationResultBuilder crb) {
        throw new RuntimeException("[Not supported for SPIR-V] FPGA ATTRIBUTES - Check with the OpenCL Backend");
    }

    public SPIRVId emitBlockLabel(String blockName) {
        if (!labelTable.containsKey(blockName)) {
            SPIRVId label = module.getNextId();
            module.add(new SPIRVOpName(label, new SPIRVLiteralString(blockName)));
            labelTable.put(blockName, label);
        }
        return labelTable.get(blockName);
    }

    public void emitBlockLabelIfNotPresent(Block b, SPIRVInstScope functionScope) {
        String blockName = b.toString();
        if (!labelTable.containsKey(blockName)) {
            SPIRVId label = module.getNextId();
            module.add(new SPIRVOpName(label, new SPIRVLiteralString(blockName)));
            labelTable.put(blockName, label);
        }
        SPIRVId label = labelTable.get(blockName);
        SPIRVInstScope block = functionScope.add(new SPIRVOpLabel(label));
        blockTable.put(blockName, block);
        // currentBlockScopeStack.push(block);
    }

    public SPIRVInstScope emitBlockLabel(String labelName, SPIRVInstScope functionScope) {
        SPIRVId label = module.getNextId();
        module.add(new SPIRVOpName(label, new SPIRVLiteralString(labelName)));
        SPIRVInstScope block = functionScope.add(new SPIRVOpLabel(label));
        labelTable.put(labelName, label);
        blockTable.put(labelName, block);
        currentBlockScopeStack.push(block);
        return block;
    }

    public SPIRVId emitOpTypeFunction(SPIRVId returnType, SPIRVId... operands) {
        functionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionPre, returnType, new SPIRVMultipleOperands<>(operands)));
        return functionPre;
    }

    public void emitEntryPointMainKernel(String kernelName, boolean isParallel, boolean fp64Capability) {
        mainFunctionID = module.getNextId();

        SPIRVMultipleOperands operands;
        if (isParallel) {
            // FIXME - Pending this - We should query exactly the builtins the code enables.
            operands = new SPIRVMultipleOperands(builtinTable.get(SPIRVOCLBuiltIn.GLOBAL_THREAD_ID), builtinTable.get(SPIRVOCLBuiltIn.GLOBAL_SIZE));
        } else {
            operands = new SPIRVMultipleOperands();
        }

        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), mainFunctionID, new SPIRVLiteralString(kernelName), operands));

        // if (fp64Capability) {
        module.add(new SPIRVOpExecutionMode(mainFunctionID, SPIRVExecutionMode.ContractionOff()));
        // }

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

    public SPIRVId emitConstantValue(SPIRVKind type, String valueConstant) {
        SPIRVId newConstantId = module.getNextId();
        SPIRVId typeID = primitives.getTypePrimitive(type);
        switch (type) {
            case OP_TYPE_INT_32:
                module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentInt(BigInteger.valueOf(Integer.parseInt(valueConstant)))));
                break;
            case OP_TYPE_INT_64:
                module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentLong(BigInteger.valueOf(Integer.parseInt(valueConstant)))));
                break;
            case OP_TYPE_FLOAT_32:
                module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentFloat(Float.parseFloat(valueConstant))));
                break;
            case OP_TYPE_FLOAT_64:
                module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentDouble(Double.parseDouble(valueConstant))));
                break;
            default:
                throw new RuntimeException("Data type not supported yet: " + type);
        }
        return newConstantId;
    }

    public SPIRVId lookUpConstant(String valueConstant, SPIRVKind type) {
        ConstantKeyPair ckp = new ConstantKeyPair(valueConstant, type);
        if (constants.containsKey(ckp)) {
            return constants.get(ckp);
        } else {
            SPIRVId newConstantId = emitConstantValue(type, valueConstant);
            constants.put(ckp, newConstantId);
            return newConstantId;
        }
    }

    public Map<ConstantKeyPair, SPIRVId> getConstants() {
        return this.constants;
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

    /**
     * Unary operations
     */
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

    @Deprecated
    public static class SPIRVUnaryTemplate extends SPIRVUnaryOp {
        // @formatter:off

        @Deprecated
        public static final SPIRVUnaryTemplate LOAD_PARAM_INT = new SPIRVUnaryTemplate("param", "(int) " + FRAME_REF_NAME + "[%s]");
        @Deprecated
        public static final SPIRVUnaryTemplate LOAD_PARAM_LONG = new SPIRVUnaryTemplate("param", "(long) " + FRAME_REF_NAME + "[%s]");

        // @formatter:on
        private final String template;

        @Deprecated
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
     * Binary operations
     */
    public abstract static class SPIRVBinaryOp extends SPIRVOp {

        public static final SPIRVBinaryOp ADD_INTEGER = new SPIRVBinaryOpIAdd("+", "SPIRVOpIAdd");
        public static final SPIRVBinaryOp ADD_FLOAT = new SPIRVBinaryOpFAdd("+", "SPIRVOpFAdd");
        public static final SPIRVBinaryOp SUB_INTEGER = new SPIRVBinaryOpISub("-", "SPIRVOpISub");
        public static final SPIRVBinaryOp SUB_FLOAT = new SPIRVBinaryOpFSub("-", "SPIRVOpFSub");
        public static final SPIRVBinaryOp MULT_INTEGER = new SPIRVBinaryOpIMul("*", "SPIRVOpIMul");
        public static final SPIRVBinaryOp MULT_FLOAT = new SPIRVBinaryOpFMul("*", "SPIRVOpFMul");
        public static final SPIRVBinaryOp DIV_INTEGER = new SPIRVBinaryOpIDiv("/", "SPIRVOpSDiv");
        public static final SPIRVBinaryOp DIV_FLOAT = new SPIRVBinaryOpFDiv("/", "SPIRVOpFDiv");
        public static final SPIRVBinaryOp BITWISE_LEFT_SHIFT = new SPIRVBinaryOpLeftShift("<<", "SPIRVOpShiftLeftLogical");
        public static final SPIRVBinaryOp INTEGER_LESS_THAN = new SPIRVBinaryOpSLessThan("<", "SPIRVOpSLessThan");
        public static final SPIRVBinaryOp INTEGER_EQUALS = new SPIRVBinaryOpIEqual("<", "SPIRVBinaryOpIEqual");
        public static final SPIRVBinaryOp INTEGER_BELOW = new SPIRVBinaryOpIBelow("<", "SPIRVOpSLessThan");

        protected String spirvInstruction;

        protected SPIRVBinaryOp(String opcode, String spirvInstruction) {
            super(opcode);
            this.spirvInstruction = spirvInstruction;
        }

        public String getOpcode() {
            return this.opcode;
        }

        /**
         * Instruction used for debugging
         *
         * @return String
         */
        public String getInstruction() {
            return spirvInstruction;
        }

        public abstract SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2);

    }

    public static class SPIRVBinaryOpIAdd extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIAdd(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpIAdd(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFAdd extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFAdd(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFAdd(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpISub extends SPIRVBinaryOp {

        protected SPIRVBinaryOpISub(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpISub(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFSub extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFSub(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFSub(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIMul extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIMul(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpIMul(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFMul extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFMul(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFMul(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIDiv extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIDiv(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSDiv(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFDiv extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFDiv(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFDiv(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpLeftShift extends SPIRVBinaryOp {

        protected SPIRVBinaryOpLeftShift(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpShiftLeftLogical(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpSLessThan extends SPIRVBinaryOp {

        protected SPIRVBinaryOpSLessThan(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSLessThan(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIEqual extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIEqual(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpIEqual(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIBelow extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIBelow(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSLessThan(idResultType, idResult, operand1, operand2);
        }
    }

    public void emit(String str) {
        emitSubString(str);
    }

    public void emitSubString(String str) {
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
    public AbstractAddress makeAddress(int transferSize, Register base, int displacement) {
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
