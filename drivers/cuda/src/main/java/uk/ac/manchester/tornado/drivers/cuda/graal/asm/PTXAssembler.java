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
import org.graalvm.compiler.nodes.cfg.Block;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXParam;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXReturnSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.graalvm.compiler.code.HexCodeFile.encodeString;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.*;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.*;

public class PTXAssembler extends Assembler {
    private boolean pushToStack;
    private List<String> operandStack;
    private boolean emitEOL;
    private boolean convertTabToSpace;
    private PTXLIRGenerationResult lirGenRes;

    public PTXAssembler(TargetDescription target, PTXLIRGenerationResult lirGenRes) {
        super(target);
        pushToStack = false;
        emitEOL = true;
        convertTabToSpace = false;
        operandStack = new ArrayList<>(10);
        this.lirGenRes = lirGenRes;
    }

    public Variable getVarForParam(PTXParam param) {
        return lirGenRes.getVarForParam(param);
    }

    public void emitSymbol(String sym) {
        byte[] symBytes = sym.getBytes();
        if (convertTabToSpace) {
            byte[] tabBytes = TAB.getBytes();
            if (Arrays.equals(symBytes, tabBytes)) {
                symBytes = SPACE.getBytes();
                convertTabToSpace = false;
            }
        }
        for (byte b : symBytes) {
            emitByte(b);
        }
    }

    public void emitValue(Value value) {
        if (value instanceof PTXReturnSlot) {
            ((PTXReturnSlot) value).emit(this);
        } else {
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
        emitSymbol(STMT_DELIMITER);
    }

    public void eol() {
        if (emitEOL) {
            emitSymbol(PTXAssemblerConstants.EOL);
        } else {
            space();
        }
    }

    public void space() {
        emitSymbol(PTXAssemblerConstants.SPACE);
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
        emitValue(values[values.length - 1]);
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

    public void emitConstant(ConstantValue constant) {
        emitSymbol(constant.getJavaConstant().asLong() > 0 ? "+" : "-");
        emit(constant.getConstant().toValueString());
    }

    public void convertNextTabToSpace() {
        convertTabToSpace = true;
    }

    public void emitBlock(int id) {
        emit("BLOCK_%d", id);
    }

    private void emitBlockLabel(int id) {
        emitBlock(id);
        emitSymbol(COLON);
        eol();
    }

    public void emitBlockLabel(Block b) {
        emitBlockLabel(b.getId());
    }

    /**
     * Base class for PTX opcodes.
     */
    public static class PTXOp {

        protected final String opcode;
        protected final boolean isTyped;
        protected final boolean isWeaklyTyped;

        protected PTXOp(String opcode) {
            this(opcode, false);
        }

        protected PTXOp(String opcode, boolean isWeaklyTyped) {
            this.opcode = opcode;
            this.isTyped = true;
            this.isWeaklyTyped = isWeaklyTyped;
        }

        protected PTXOp(String opcode, boolean isTyped, boolean isWeaklyTyped) {
            this.opcode = opcode;
            this.isTyped = isTyped;
            this.isWeaklyTyped = isWeaklyTyped;
        }

        public final void emitOpcode(PTXAssembler asm) {
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

    /**
     * Nullary opcodes
     */
    public static class PTXNullaryOp extends PTXOp {

        public static final PTXNullaryOp LD = new PTXNullaryOp("ld");
        public static final PTXNullaryOp LDU = new PTXNullaryOp("ldu");
        public static final PTXNullaryOp RETURN = new PTXNullaryOp("ret");

        protected PTXNullaryOp(String opcode) {
            this(opcode, false);
        }

        protected PTXNullaryOp(String opcode, boolean isWeaklyTyped) {
            super(opcode, isWeaklyTyped);
        }

        public void emit(PTXCompilationResultBuilder crb, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
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

        public PTXNullaryTemplate(String opcode, boolean isWeaklyTyped) {
            super(opcode, isWeaklyTyped);
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
    public static class PTXUnaryOp extends PTXOp {
        public static final PTXUnaryOp NOT = new PTXUnaryOp("not", true, false);

        private final boolean needsRounding;

        public PTXUnaryOp(String opcode) {
            this(opcode, true);
        }

        public PTXUnaryOp(String opcode, boolean needsRounding) {
            super(opcode);
            this.needsRounding = needsRounding;
        }

        public PTXUnaryOp(String opcode, boolean needsRounding, boolean isTyped, boolean isWeaklyTyped) {
            super(opcode, isTyped, isWeaklyTyped);
            this.needsRounding = needsRounding;
        }

        protected PTXUnaryOp(String opcode, boolean isWeaklyTyped, boolean needsRounding) {
            super(opcode, isWeaklyTyped);
            this.needsRounding = needsRounding;
        }

        public void emit(PTXCompilationResultBuilder crb, Value value, Value dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            PTXKind type = (PTXKind) dest.getPlatformKind();
            if (needsRounding && type.isFloating()) {
                asm.emitSymbol(DOT);
                asm.emit(ROUND_NEAREST_EVEN);
            }

            if (isTyped){
                if (type == PTXKind.PRED) type = (PTXKind) value.getPlatformKind(); // Make sure setp doesn't end up with pred
                if (isWeaklyTyped) type = type.toUntyped();
                asm.emit("." + type);
            }
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[]{dest, value});
        }
    }

    /**
     * Unary intrinsic
     */
    public static class PTXUnaryIntrinsic extends PTXUnaryOp {
        // @formatter:off

        public static final PTXUnaryIntrinsic GLOBAL_ID = new PTXUnaryIntrinsic("get_global_id");
        public static final PTXUnaryIntrinsic GLOBAL_SIZE = new PTXUnaryIntrinsic("get_global_size");

        public static final PTXUnaryIntrinsic LOCAL_ID = new PTXUnaryIntrinsic("get_local_id");
        public static final PTXUnaryIntrinsic LOCAL_SIZE = new PTXUnaryIntrinsic("get_local_size");

        public static final PTXUnaryIntrinsic GROUP_ID = new PTXUnaryIntrinsic("get_group_id");
        public static final PTXUnaryIntrinsic GROUP_SIZE = new PTXUnaryIntrinsic("get_group_size");

        public static final PTXUnaryIntrinsic ATOMIC_INC = new PTXUnaryIntrinsic("atomic_inc");
        public static final PTXUnaryIntrinsic ATOMIC_DEC = new PTXUnaryIntrinsic("atomic_dec");

        public static final PTXUnaryIntrinsic BARRIER_SYNC = new PTXUnaryIntrinsic("barrier.sync", false, false, false);
        public static final PTXUnaryIntrinsic MEM_FENCE = new PTXUnaryIntrinsic("mem_fence");
        public static final PTXUnaryIntrinsic READ_MEM_FENCE = new PTXUnaryIntrinsic("read_mem_fence");
        public static final PTXUnaryIntrinsic WRITE_MEM_FENCE = new PTXUnaryIntrinsic("write_mem_fence");

        public static final PTXUnaryIntrinsic ABS = new PTXUnaryIntrinsic("abs", false);
        public static final PTXUnaryIntrinsic EXP2 = new PTXUnaryIntrinsic("ex2.approx", false);
        public static final PTXUnaryIntrinsic SQRT = new PTXUnaryIntrinsic("sqrt");
        public static final PTXUnaryIntrinsic LOG2 = new PTXUnaryIntrinsic("lg2.approx", false);
        public static final PTXUnaryIntrinsic SIN = new PTXUnaryIntrinsic("sin.approx", false);
        public static final PTXUnaryIntrinsic COS = new PTXUnaryIntrinsic("cos.approx", false);

        public static final PTXUnaryIntrinsic LOCAL_MEMORY = new PTXUnaryIntrinsic("__local");

        public static final PTXUnaryIntrinsic POPCOUNT = new PTXUnaryIntrinsic("popc", false);

        public static final PTXUnaryIntrinsic AS_FLOAT = new PTXUnaryIntrinsic("as_float");
        public static final PTXUnaryIntrinsic AS_INT = new PTXUnaryIntrinsic("as_int");

        public static final PTXUnaryIntrinsic IS_FINITE = new PTXUnaryIntrinsic("isfinite");
        public static final PTXUnaryIntrinsic IS_INF = new PTXUnaryIntrinsic("isinf");
        public static final PTXUnaryIntrinsic IS_NAN = new PTXUnaryIntrinsic("isnan");
        public static final PTXUnaryIntrinsic IS_NORMAL = new PTXUnaryIntrinsic("isnormal");
        // @formatter:on

        protected PTXUnaryIntrinsic(String opcode) {
            super(opcode, true);
        }

        protected PTXUnaryIntrinsic(String opcode, boolean needsRounding) {
            super(opcode, needsRounding);
        }

        protected PTXUnaryIntrinsic(String opcode, boolean needsRounding, boolean isTyped, boolean isWeaklyTyped) {
            super(opcode, needsRounding, isTyped, isWeaklyTyped);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x, Value dest) {
            super.emit(crb, x, dest);
        }
    }


    /**
     * Binary opcodes
     */
    public static class PTXBinaryOp extends PTXOp {

        public static final PTXBinaryOp BITWISE_LEFT_SHIFT = new PTXBinaryOp("shl", true, false);
        public static final PTXBinaryOp BITWISE_RIGHT_SHIFT = new PTXBinaryOp("shr", true, false);
        public static final PTXBinaryOp BITWISE_AND = new PTXBinaryOp("and", true, false);
        public static final PTXBinaryOp BITWISE_OR = new PTXBinaryOp("or", true, false);
        public static final PTXBinaryOp BITWISE_XOR = new PTXBinaryOp("xor", true, false);
        public static final PTXBinaryOp ADD = new PTXBinaryOp("add");
        public static final PTXBinaryOp SUB = new PTXBinaryOp("sub");
        public static final PTXBinaryOp MUL = new PTXBinaryOp("mul");
        public static final PTXBinaryOp MUL_LO = new PTXBinaryOp("mul.lo");
        public static final PTXBinaryOp DIV = new PTXBinaryOp("div");
        public static final PTXBinaryOp DIV_APPROX = new PTXBinaryOp("div.approx", false);

        public static final PTXBinaryOp RELATIONAL_EQ = new PTXBinaryOp("==");
        public static final PTXBinaryOp SETP_LT = new PTXBinaryOp("setp.lt");
        public static final PTXBinaryOp SETP_EQ = new PTXBinaryOp("setp.eq");
        public static final PTXBinaryOp SETP_LE = new PTXBinaryOp("setp.le");
        public static final PTXBinaryOp SETP_GE = new PTXBinaryOp("setp.ge");
        public static final PTXBinaryOp SETP_GT = new PTXBinaryOp("setp.gt");
        public static final PTXBinaryOp SETP_NE = new PTXBinaryOp("setp.ne");

        private final boolean needsRounding;

        public PTXBinaryOp(String opcode) {
            this(opcode, true);
        }

        public PTXBinaryOp(String opcode, boolean needsRounding) {
            super(opcode);
            this.needsRounding = needsRounding;
        }

        public PTXBinaryOp(String opcode, boolean isWeaklyTyped, boolean needsRounding) {
            super(opcode, isWeaklyTyped);
            this.needsRounding = needsRounding;
        }

        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            PTXKind type = (PTXKind) dest.getPlatformKind();
            if (needsRounding && type.isFloating()) {
                asm.emitSymbol(DOT);
                asm.emit(ROUND_NEAREST_EVEN);
            }

            if (isTyped) {
                if (type == PTXKind.PRED)
                    type = (PTXKind) x.getPlatformKind(); // Make sure setp doesn't end up with pred
                if (isWeaklyTyped) type = type.toUntyped();
                asm.emit("." + type);
            }
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[]{dest, x, y});
        }
    }

    /**
     * Binary intrinsic
     */
    public static class PTXBinaryIntrinsic extends PTXBinaryOp {
        // @formatter:off
        public static final PTXBinaryIntrinsic INT_MIN = new PTXBinaryIntrinsic("min", false);
        public static final PTXBinaryIntrinsic INT_MAX = new PTXBinaryIntrinsic("max", false);

        public static final PTXBinaryIntrinsic FLOAT_MIN = new PTXBinaryIntrinsic("min", false);
        public static final PTXBinaryIntrinsic FLOAT_MAX = new PTXBinaryIntrinsic("max", false);

        public static final PTXBinaryIntrinsic ATOMIC_ADD = new PTXBinaryIntrinsic("atomic_add");
        public static final PTXBinaryIntrinsic ATOMIC_SUB = new PTXBinaryIntrinsic("atomic_sub");
        public static final PTXBinaryIntrinsic ATOMIC_XCHG = new PTXBinaryIntrinsic("atomic_xchg");
        public static final PTXBinaryIntrinsic ATOMIC_MIN = new PTXBinaryIntrinsic("atomic_min");
        public static final PTXBinaryIntrinsic ATOMIC_MAX = new PTXBinaryIntrinsic("atomic_max");
        public static final PTXBinaryIntrinsic ATOMIC_AND = new PTXBinaryIntrinsic("atomic_and");
        public static final PTXBinaryIntrinsic ATOMIC_OR = new PTXBinaryIntrinsic("atomic_or");
        public static final PTXBinaryIntrinsic ATOMIC_XOR = new PTXBinaryIntrinsic("atomic_xor");

        public static final PTXBinaryIntrinsic VLOAD2 = new PTXBinaryIntrinsic("vload2");
        public static final PTXBinaryIntrinsic VLOAD3 = new PTXBinaryIntrinsic("vload3");
        public static final PTXBinaryIntrinsic VLOAD4 = new PTXBinaryIntrinsic("vload4");
        public static final PTXBinaryIntrinsic VLOAD8 = new PTXBinaryIntrinsic("vload8");
        public static final PTXBinaryIntrinsic VLOAD16 = new PTXBinaryIntrinsic("vload16");

        public static final PTXBinaryIntrinsic DOT = new PTXBinaryIntrinsic("dot");
        public static final PTXBinaryIntrinsic CROSS = new PTXBinaryIntrinsic("cross");
        // @formatter:on

        protected PTXBinaryIntrinsic(String opcode) {
            super(opcode, true);
        }

        protected PTXBinaryIntrinsic(String opcode, boolean needsRounding) {
            super(opcode, needsRounding);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Variable dest) {
            super.emit(crb, x, y, dest);
        }
    }

    public static class PTXBinaryTemplate extends PTXBinaryOp {
        //TODO: These need to be PTX
        public static final PTXBinaryTemplate NEW_ARRAY = new PTXBinaryTemplate("new array", ".u8 %s[%s]");

        public static final PTXBinaryTemplate NEW_LOCAL_FLOAT_ARRAY = new PTXBinaryTemplate("local memory array float", ".shared .f32 %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_INT_ARRAY = new PTXBinaryTemplate("local memory array int", ".shared .s32 %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_DOUBLE_ARRAY = new PTXBinaryTemplate("local memory array double", ".shared .f64 %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_LONG_ARRAY = new PTXBinaryTemplate("local memory array long", ".shared .s64 %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_SHORT_ARRAY = new PTXBinaryTemplate("local memory array short", ".shared .s16 %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_CHAR_ARRAY = new PTXBinaryTemplate("local memory array char", ".shared .u8 %s[%s]");
        public static final PTXBinaryTemplate NEW_LOCAL_BYTE_ARRAY = new PTXBinaryTemplate("local memory array byte", ".shared .s8 %s[%s]");

        private final String template;

        protected PTXBinaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            asm.emitSymbol(TAB);
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
        public static final PTXTernaryOp MAD_LO = new PTXTernaryOp("mad.lo");
        public static final PTXTernaryOp MAD = new PTXTernaryOp("mad");
        public static final PTXTernaryOp SELP = new PTXTernaryOp("selp");

        protected PTXTernaryOp(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Value z, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emitSymbol(DOT);
            if (((PTXKind) dest.getPlatformKind()).isFloating()) {
                asm.emit(PTXAssemblerConstants.ROUND_NEAREST_EVEN);
                asm.emitSymbol(DOT);
            }
            asm.emit(dest.getPlatformKind().toString());
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[]{dest, x, y, z});
        }
    }
}
