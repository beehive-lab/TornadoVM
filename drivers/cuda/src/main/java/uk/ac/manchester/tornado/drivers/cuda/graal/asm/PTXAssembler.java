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
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIROp;
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

    public void emitValueOrOp(PTXCompilationResultBuilder crb, Value value, Variable dest) {
        if (value instanceof PTXLIROp) {
            ((PTXLIROp) value).emit(crb, this, dest);
        } else {
            emitValue(value);
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
        // unimplemented();
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

    public void emitValuesOrOp(PTXCompilationResultBuilder crb, Value[] values, Variable dest) {
        for (int i = 0; i < values.length - 1; i++) {
            emitValueOrOp(crb, values[i], dest);
            emitSymbol(COMMA);
            space();
        }
        emitValueOrOp(crb, values[values.length - 1], dest);
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
        emitConstant(constant.getJavaConstant().asLong());
    }

    public void emitConstant(long constant) {
        emitSymbol(constant > 0 ? "+" : "-");
        emit(Long.toString(constant));
    }

    public void convertNextTabToSpace() {
        convertTabToSpace = true;
    }

    public void emitLoop(int id) {
        emit("LOOP_COND_%d", id);
    }

    public void emitLoopLabel(int id) {
        emitLoop(id);
        emitSymbol(COLON);
        eol();
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
        public static final PTXNullaryOp ST = new PTXNullaryOp("st");
        public static final PTXNullaryOp STU = new PTXNullaryOp("stu");
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

            if (isTyped) {
                if (type == PTXKind.PRED)
                    type = (PTXKind) value.getPlatformKind(); // Make sure setp doesn't end up with pred
                if (isWeaklyTyped)
                    type = type.toUntyped();
                asm.emit("." + type);
            }
            asm.emitSymbol(TAB);
            asm.emitValues(new Value[] { dest, value });
        }
    }

    /**
     * Unary intrinsic
     */
    public static class PTXUnaryIntrinsic extends PTXUnaryOp {
        // @formatter:off
        public static final PTXUnaryIntrinsic BARRIER_SYNC = new PTXUnaryIntrinsic("barrier.sync", false, false, false);

        public static final PTXUnaryIntrinsic ABS = new PTXUnaryIntrinsic("abs", false);
        public static final PTXUnaryIntrinsic EXP2 = new PTXUnaryIntrinsic("ex2.approx", false);
        public static final PTXUnaryIntrinsic SQRT = new PTXUnaryIntrinsic("sqrt");
        public static final PTXUnaryIntrinsic LOG2 = new PTXUnaryIntrinsic("lg2.approx", false);
        public static final PTXUnaryIntrinsic SIN = new PTXUnaryIntrinsic("sin.approx", false);
        public static final PTXUnaryIntrinsic COS = new PTXUnaryIntrinsic("cos.approx", false);

        public static final PTXUnaryIntrinsic LOCAL_MEMORY = new PTXUnaryIntrinsic("__local");

        public static final PTXUnaryIntrinsic POPCOUNT = new PTXUnaryIntrinsic("popc", false);
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
        public static final PTXBinaryOp MUL_WIDE = new PTXBinaryOp("mul.wide");
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

            // mul.wide.u32 rud, rui, rui. For mul.wide, we need to specify the
            // type of the sources and not destination
            if (MUL_WIDE.opcode.equals(opcode)) {
                type = (PTXKind) x.getPlatformKind();
            }

            if (needsRounding && type.isFloating()) {
                asm.emitSymbol(DOT);
                asm.emit(ROUND_NEAREST_EVEN);
            }

            if (isTyped) {
                if (type == PTXKind.PRED)
                    type = (PTXKind) x.getPlatformKind(); // Make sure setp doesn't end up with pred
                if (isWeaklyTyped)
                    type = type.toUntyped();
                asm.emit("." + type);
            }
            asm.emitSymbol(TAB);
            asm.emitValuesOrOp(crb, new Value[] { dest, x, y }, dest);
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
        // TODO: These need to be PTX
        public static final PTXBinaryTemplate NEW_ARRAY = new PTXBinaryTemplate("new array", ".local .u8 %s[%s]");

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
            asm.emitValues(new Value[] { dest, x, y, z });
        }
    }

    public static class PTXTernaryIntrinsic extends PTXTernaryOp {

        // @formatter:off
        public static final PTXTernaryIntrinsic VSTORE2 = new PTXTernaryIntrinsic("vstore2");
        public static final PTXTernaryIntrinsic VSTORE3 = new PTXTernaryIntrinsic("vstore3");
        public static final PTXTernaryIntrinsic VSTORE4 = new PTXTernaryIntrinsic("vstore4");
        public static final PTXTernaryIntrinsic VSTORE8 = new PTXTernaryIntrinsic("vstore8");
        public static final PTXTernaryIntrinsic VSTORE16 = new PTXTernaryIntrinsic("vstore16");
        // @formatter:on

        protected PTXTernaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(PTXCompilationResultBuilder crb, Value x, Value y, Value z, Variable dest) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(x);
            asm.emit(", ");
            asm.emitValue(y);
            asm.emit(", ");
            asm.emitValue(z);
            asm.emit(")");
        }
    }

    public static class PTXOp2 extends PTXOp {

        // @formatter:off
        public static final PTXOp2 VMOV_SHORT2 = new PTXOp2("(short2)");
        public static final PTXOp2 VMOV_INT2 = new PTXOp2("(int2)");
        public static final PTXOp2 VMOV_FLOAT2 = new PTXOp2("(float2)");
        public static final PTXOp2 VMOV_BYTE2 = new PTXOp2("(char2)");
        public static final PTXOp2 VMOV_DOUBLE2 = new PTXOp2("(double2)");
        // @formatter:on

        protected PTXOp2(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value s0, Value s1) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(s0);
            asm.emit(", ");
            asm.emitValue(s1);
            asm.emit(")");
        }
    }

    public static class PTXOp3 extends PTXOp2 {
        public PTXOp3(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value s0, Value s1, Value s2) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(s0);
            asm.emit(", ");
            asm.emitValue(s1);
            asm.emit(", ");
            asm.emitValue(s2);
            asm.emit(")");
        }
    }

    public static class PTXOp4 extends PTXOp3 {
        // @formatter:off

        public static final PTXOp4 VMOV_SHORT4 = new PTXOp4("(short4)");
        public static final PTXOp4 VMOV_INT4 = new PTXOp4("(int4)");
        public static final PTXOp4 VMOV_FLOAT4 = new PTXOp4("(float4)");
        public static final PTXOp4 VMOV_BYTE4 = new PTXOp4("(char4)");
        public static final PTXOp4 VMOV_DOUBLE4 = new PTXOp4("(double4)");
        // @formatter:on

        protected PTXOp4(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(s0);
            asm.emit(", ");
            asm.emitValue(s1);
            asm.emit(", ");
            asm.emitValue(s2);
            asm.emit(", ");
            asm.emitValue(s3);
            asm.emit(")");
        }
    }

    public static class PTXOp8 extends PTXOp4 {
        // @formatter:off

        public static final PTXOp8 VMOV_SHORT8 = new PTXOp8("(short8)");
        public static final PTXOp8 VMOV_INT8 = new PTXOp8("(int8)");
        public static final PTXOp8 VMOV_FLOAT8 = new PTXOp8("(float8)");
        public static final PTXOp8 VMOV_BYTE8 = new PTXOp8("(char8)");
        public static final PTXOp8 VMOV_DOUBLE8 = new PTXOp8("(double8)");

        // @formatter:on

        protected PTXOp8(String opcode) {
            super(opcode);
        }

        public void emit(PTXCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            final PTXAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(s0);
            asm.emit(", ");
            asm.emitValue(s1);
            asm.emit(", ");
            asm.emitValue(s2);
            asm.emit(", ");
            asm.emitValue(s3);
            asm.emit(", ");
            asm.emitValue(s4);
            asm.emit(", ");
            asm.emitValue(s5);
            asm.emit(", ");
            asm.emitValue(s6);
            asm.emit(", ");
            asm.emitValue(s7);
            asm.emit(")");
        }
    }
}
