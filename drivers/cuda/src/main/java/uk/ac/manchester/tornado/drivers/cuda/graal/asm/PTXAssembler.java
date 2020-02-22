package uk.ac.manchester.tornado.drivers.cuda.graal.asm;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXReturnSlot;

import java.util.ArrayList;
import java.util.List;

import static org.graalvm.compiler.code.HexCodeFile.encodeString;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.*;

public class PTXAssembler extends Assembler {
    private boolean pushToStack;
    private List<String> operandStack;
    private String delimiter;
    private boolean emitEOL;

    public PTXAssembler(TargetDescription target) {
        super(target);
        pushToStack = false;
        emitEOL = true;
        delimiter = PTXAssemblerConstants.STMT_DELIMITER;
        operandStack = new ArrayList<>(10);
    }

    public void emitSymbol(String sym) {
        for (byte b : sym.getBytes()) {
            emitByte(b);
        }
    }

    public void emitValue(Value value) {
        if (value instanceof PTXReturnSlot) {
            ((PTXReturnSlot) value).emit(this);
        }
        else {
            emit(toString(value));
        }
    }

    private static String toString(Value value) {
        String result = "";
        if (value instanceof Variable) {
            Variable var = (Variable) value;
            return var.getName();
        } else if (value instanceof ConstantValue) {
            if (!((ConstantValue) value).isJavaConstant()) {
                shouldNotReachHere("constant value: ", value);
            }
            ConstantValue cv = (ConstantValue) value;
            return formatConstant(cv);
        } else {
            unimplemented("value: toString() type=%s, value=%s", value.getClass().getName(), value);
        }
        return result;
    }

    private static String formatConstant(ConstantValue cv) {
        String result = "";
        JavaConstant javaConstant = cv.getJavaConstant();
        Constant constant = cv.getConstant();
        PTXKind kind = (PTXKind) cv.getPlatformKind();
        if (javaConstant.isNull()) {
            if (kind.isVector()) {
                result = String.format("(%s)(%s)", kind.name(), result);
            }
        } else if (constant instanceof HotSpotObjectConstant) {
            HotSpotObjectConstant objConst = (HotSpotObjectConstant) constant;
            // TODO should this be replaced with isInternedString()?
            if (objConst.getJavaKind().isObject() && objConst.getType().getName().compareToIgnoreCase("Ljava/lang/String;") == 0) {
                result = encodeString(objConst.toValueString());
            }
        } else {
            result = constant.toValueString();
        }
        return result;
    }

    public void emit(String format, Object... args) {
        emitSubString(String.format(format, args));
    }

    public void delimiter() {
        emitSymbol(delimiter);
    }

    public void eol() {
        if (emitEOL) {
            emitSymbol(PTXAssemblerConstants.EOL);
        }
        else {
            space();
        }
    }

    public void space() {
        emitSymbol(" ");
    }

    public void assign() {
        emitSymbol(PTXAssemblerConstants.ASSIGN);
    }


    @Override
    public void align(int modulus) {
        //unimplemented();
    }

    @Override
    public void jmp(Label l) {
        unimplemented();
    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {
        unimplemented();
    }

    @Override
    public AbstractAddress makeAddress(Register base, int displacement) {
        unimplemented();
        return null;
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        unimplemented();
        return null;
    }

    @Override
    public void ensureUniquePC() {
        unimplemented();
    }


    private void beginStackPush() {
        pushToStack = true;
    }

    private void endStackPush() {
        pushToStack = false;
    }

    private String getLastOp() {
        StringBuilder sb = new StringBuilder();
        for (String str : operandStack) {
            sb.append(str);
        }
        operandStack.clear();
        return sb.toString();
    }

    public void emit(String opcode) {
        emitSubString(opcode);
    }

    private void emitSubString(String str) {
        guarantee(str != null, "emitting null string");
        if (pushToStack) {
            operandStack.add(str);
        } else {
            for (byte b : str.getBytes()) {
                emitByte(b);
            }
        }
    }

    public void emitValues(Value[] values) {
        for (int i = 0; i < values.length - 1; i++) {
            emitValue(values[i]);
            emitSymbol(COMMA);
            space();
        }
        emitValue(values[values.length-1]);
    }

    public String toString(LabelRef ref) {
        return ref.label().toString();
    }

    public void emitLine(String value) {
        emit(value);
        eol();
    }

    public void emitLine(String format, Object... args) {
        emit(format, args);
        eol();
    }

    public void loopBreak() {
        emitLine("LOOP_BREAK");
    }

    /**
     * Base class for PTX opcodes.
     */
    public static class PTXOp {

        protected final String opcode;
        protected final boolean isTyped;

        protected PTXOp(String opcode) {
            this(opcode, true);
        }

        protected PTXOp(String opcode, boolean isTyped) {
            this.opcode = opcode;
            this.isTyped = isTyped;
        }

        protected final void emitOpcode(PTXAssembler asm) {
            asm.emitSymbol(TAB);
            asm.emit(opcode);
        }

        public boolean equals(PTXOp other) {
            return opcode.equals(other.opcode);
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    public static class PTXGuardedOp extends PTXOp {
        protected final boolean isGuarded;
        protected final boolean isNegated;

        protected PTXGuardedOp(String opcode) {
            this(opcode, false, false);
        }

        protected PTXGuardedOp(String opcode, boolean isGuarded, boolean isNegated) {
            super(opcode);
            this.isGuarded = isGuarded;
            this.isNegated = isNegated;
        }

        protected final void emitOpcode(PTXAssembler asm, Value pred) {
            asm.emitSymbol(TAB);
            if (isGuarded && pred != null) {
                asm.emitSymbol(OP_GUARD);
                if (isNegated) {
                    asm.emitSymbol(NEGATION);
                }
                asm.emitValue(pred);
            }
            super.emitOpcode(asm);

            if (!isGuarded && pred != null) {
                asm.emitSymbol(TAB);
                asm.emitValue(pred);
            }

        }
    }

    /**
     * Nullary opcodes
     */
    public static class PTXNullaryOp extends PTXOp {

        // @formatter:off
        public static final PTXNullaryOp RETURN = new PTXNullaryOp("ret", false);
        // @formatter:on

        protected PTXNullaryOp(String opcode, boolean isTyped) {
            super(opcode, isTyped);
        }

        public void emit(PTXCompilationResultBuilder crb, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            asm.emitSymbol(TAB);
            emitOpcode(asm);

            if (dest != null) {
                asm.emitSymbol(TAB);
                asm.emitValue(dest);
            }
        }
    }


    public static class PTXNullaryTemplate extends PTXNullaryOp {
        public PTXNullaryTemplate(String opcode) {
            super(opcode, true);
        }

        public PTXNullaryTemplate(String opcode, boolean isTyped) {
            super(opcode, isTyped);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            asm.emitSymbol(TAB);
            asm.emit(opcode);
        }
    }

    /**
     * Unary opcodes
     */
    public static class PTXUnaryOp extends PTXGuardedOp {
        public static final PTXUnaryOp BRA = new PTXUnaryOp("bra");
        public static final PTXUnaryOp CONDITIONAL_BRA = new PTXUnaryOp("bra", false);

        protected PTXUnaryOp(String opcode) {
            super(opcode);
        }

        protected PTXUnaryOp(String opcode, boolean isNegated) {
            super(opcode, true, isNegated);
        }

        public void emit(PTXCompilationResultBuilder crb, Value value) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm, value);
        }
    }

    /**
     * Unary intrinsics
     */
    public static class PTXUnaryIntrinsic extends PTXUnaryOp {
        public static final PTXUnaryIntrinsic THREAD_ID = new PTXUnaryIntrinsic("blockIdx * blockDim + threadIdx");
        public static final PTXUnaryOp GLOBAL_SIZE = new PTXUnaryIntrinsic("gridDim * blockDim");

        public PTXUnaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit(".");
            asm.emitValue(x);
        }
    }

    public static class PTXUnaryTemplate extends PTXUnaryOp {
        public static final PTXUnaryTemplate LOAD_PARAM_S32 = new PTXUnaryTemplate("load param", "[%s]");
        public static final PTXUnaryTemplate LOAD_PARAM_U32 = new PTXUnaryTemplate("load param", "[%s]");
        public static final PTXUnaryTemplate LOAD_PARAM_F32 = new PTXUnaryTemplate("load param", "[%s]");
        public static final PTXUnaryTemplate LOAD_PARAM_U64 = new PTXUnaryTemplate("load param", "[%s]");
        public static final PTXUnaryTemplate LOAD_PARAM_S64 = new PTXUnaryTemplate("load param", "[%s]");
        public static final PTXUnaryTemplate LOAD_PARAM_F64 = new PTXUnaryTemplate("load param", "[%s]");

        public static final PTXUnaryTemplate CAST_TO_POINTER = new PTXUnaryTemplate("cast ptr", "(%s *)");

        private final String template;

        protected PTXUnaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value value) {
            final PTXAssembler asm = crb.getAssembler();
            asm.emit(template, PTXAssembler.toString(value));
        }

        public String getTemplate() {
            return template;
        }
    }


    /**
     * Binary opcodes
     */
    public static class PTXBinaryOp extends PTXOp {

        public static final PTXBinaryOp BITWISE_LEFT_SHIFT = new PTXBinaryOp("shl");
        public static final PTXBinaryOp ADD = new PTXBinaryOp("add");

        public static final PTXBinaryOp RELATIONAL_EQ = new PTXBinaryOp("==");
        public static final PTXBinaryOp RELATIONAL_NE = new PTXBinaryOp("!=");
        public static final PTXBinaryOp RELATIONAL_GT = new PTXBinaryOp(">");
        public static final PTXBinaryOp RELATIONAL_LT = new PTXBinaryOp("<");
        public static final PTXBinaryOp RELATIONAL_GTE = new PTXBinaryOp(">=");
        public static final PTXBinaryOp RELATIONAL_LTE = new PTXBinaryOp("<=");

        public PTXBinaryOp(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            if (isTyped) asm.emit("." + dest.getPlatformKind());
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[]{dest, x, y});
        }
    }

    /**
     * Binary intrinsic
     */
    public static class PTXBinaryIntrinsic extends PTXBinaryOp {
        // @formatter:off
        public static final PTXBinaryIntrinsic VLOAD2 = new PTXBinaryIntrinsic("vload2");
        public static final PTXBinaryIntrinsic VLOAD3 = new PTXBinaryIntrinsic("vload3");
        public static final PTXBinaryIntrinsic VLOAD4 = new PTXBinaryIntrinsic("vload4");
        public static final PTXBinaryIntrinsic VLOAD8 = new PTXBinaryIntrinsic("vload8");
        public static final PTXBinaryIntrinsic VLOAD16 = new PTXBinaryIntrinsic("vload16");
        // @formatter:on

        protected PTXBinaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emitSymbol(TAB);
            asm.emit("(");
            asm.emitValue(x);
            asm.emit(", ");
            asm.emitValue(y);
            asm.emit(")");
        }
    }

    public static class PTXBinaryTemplate extends PTXBinaryOp {
        //TODO: These need to be PTX
        public static final PTXBinaryTemplate NEW_ARRAY = new PTXBinaryTemplate("new array", "char %s[%s]");

        public static final PTXBinaryTemplate NEW_LOCAL_FLOAT_ARRAY =  new PTXBinaryTemplate("local memory array float", "__local float %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_INT_ARRAY =    new PTXBinaryTemplate("local memory array int", "__local int %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_DOUBLE_ARRAY = new PTXBinaryTemplate("local memory array double", "__local double %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_LONG_ARRAY =   new PTXBinaryTemplate("local memory array long", "__local long %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_SHORT_ARRAY =  new PTXBinaryTemplate("local memory array short", "__local short %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_CHAR_ARRAY =   new PTXBinaryTemplate("local memory array char", "__local char %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_BYTE_ARRAY =   new PTXBinaryTemplate("local memory array byte", "__local byte %s[%s]");

        private final String template;

        protected PTXBinaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            asm.beginStackPush();
            asm.emitValue(x);
            final String input1 = asm.getLastOp();
            asm.emitValue(y);
            final String input2 = asm.getLastOp();
            asm.endStackPush();

            asm.emit(template, input1, input2);
        }
    }

    public static class PTXTernaryOp extends PTXOp {
        public static final PTXTernaryOp SETP = new PTXTernaryOp("setp");

        protected PTXTernaryOp(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Value z, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[] {dest, x, y, z});
        }
    }
}
