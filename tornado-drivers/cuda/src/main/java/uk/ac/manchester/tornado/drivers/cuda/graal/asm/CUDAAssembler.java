/*
 * Copyright (c) 2018, 2022-2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.asm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind.FLOAT;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind.LONG;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind.ULONG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIROp;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDANullary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAReturnSlot;

public final class CUDAAssembler extends Assembler {

    private int indent;
    private int lastIndent;
    private String delimiter;
    private boolean emitEOL;
    private List<String> operandStack;
    private boolean pushToStack;

    public CUDAAssembler(TargetDescription target) {
        super(target, null);
        indent = 0;
        delimiter = CUDAAssemblerConstants.STMT_DELIMITER;
        emitEOL = true;
        operandStack = new ArrayList<>(10);
        pushToStack = false;

        // CUDA C / NVRTC supports double, half and 64-bit atomics natively for the
        // target compute capability; no OpenCL '#pragma OPENCL EXTENSION ...' is needed
        // (and NVRTC rejects/warns on unrecognised pragmas), so none are emitted here.
        // Atomic operations are emitted inline as native CUDA intrinsics
        // (atomicAdd/atomicSub/atomicCAS/...) at their codegen sites; no global
        // helper functions are generated.
    }

    /**
     * It converts the format of a Value input to a specific format based on its
     * platform type.
     *
     * @param input
     *     The {@link Value} input to convert.
     * @return The converted format string.
     */
    public static String convertValueFromGraalFormat(Value input) {
        String type = input.getPlatformKind().name().toLowerCase();
        String result;

        // Extract the index value between "v" and "|"
        // v10|DOUBLE --> v->indexValue<-|
        String indexValue = getAbsoluteIndexFromValue(input);

        // Find the matching TypePrefix enum for the given type
        CUDAVariablePrefix typePrefix = Arrays.stream(CUDAVariablePrefix.values()).filter(tp -> tp.getType().equals(type)).findFirst().orElse(null);

        if (typePrefix != null) {
            result = typePrefix.getPrefix() + indexValue;
        } else {
            throw new TornadoRuntimeException("Unsupported type: " + type);
        }

        return result;
    }

    /**
     * It retrieves the absolute index from the given Value object.
     *
     * @param value
     *     the {@link Value} object to extract the index from. It should be
     *     in the format "int[20|0x14]".
     * @return the absolute index as a String
     */
    public static String getAbsoluteIndexFromValue(Value value) {
        int startIndex = value.toString().indexOf('[') + 1;
        int endIndex = value.toString().indexOf('|');

        return value.toString().substring(startIndex, endIndex).trim().replace("v", "");
    }

    @Override
    public void align(int arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void halt() {
        // TODO Auto-generated method stub
    }

    @Override
    public void ensureUniquePC() {
        // TODO Auto-generated method stub

    }

    @Override
    public AbstractAddress getPlaceholder(int i) {
        unimplemented("Place holder not implemented yet.");
        return null;
    }

    @Override
    public void jmp(Label arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void patchJumpTarget(int arg0, int arg1) {
        unimplemented("Patch jump target not implemented yet.");
    }

    @Override
    public AbstractAddress makeAddress(int transferSize, Register base, int displacement) {
        unimplemented("Make address not implemented yet.");
        return null;
    }

    /**
     * Used to emit instructions within a method. i.e. ones that terminal with a ';'
     *
     * @param fmt
     * @param args
     */
    public void emitStmt(String fmt, Object... args) {
        indent();
        emit("%s", String.format(fmt, args));
        delimiter();
        eol();
    }

    /**
     * Used to emit function defs and control flow statements. i.e. strings that do
     * not terminate with a ';'
     *
     * @param fmt
     * @param args
     */
    public void emitString(String fmt, Object... args) {
        indent();
        emitString(String.format(fmt, args));
    }

    public void emitSubString(String str) {
        guarantee(str != null, "emitting null string");
        if (pushToStack) {
            operandStack.add(str);
        } else {
            for (byte b : str.getBytes()) {
                emitByte(b);
            }
        }
    }

    public List<String> getOperandStack() {
        return operandStack;
    }

    public void beginStackPush() {
        pushToStack = true;
    }

    public void endStackPush() {
        pushToStack = false;
    }

    public String getLastOp() {
        StringBuilder sb = new StringBuilder();
        for (String str : operandStack) {
            sb.append(str);
        }
        operandStack.clear();
        return sb.toString();
    }

    public void pushIndent() {
        assert (indent >= 0);
        indent++;
    }

    public void popIndent() {
        assert (indent > 0);
        indent--;
    }

    public void indentOff() {
        lastIndent = indent;
        indent = 0;
    }

    public void indentOn() {
        indent = lastIndent;
    }

    public void indent() {
        for (int i = 0; i < indent; i++) {
            emitSymbol(CUDAAssemblerConstants.TAB);
        }
    }

    public void comment(String comment) {
        emit(" /* " + comment + " */ ");
        eol();
    }

    public void loopBreak() {
        emit(CUDAAssemblerConstants.BREAK);
    }

    public void emitSymbol(String sym) {
        for (byte b : sym.getBytes()) {
            emitByte(b);
        }
    }

    public void eolOff() {
        emitEOL = false;
    }

    public void eolOn() {
        emitEOL = true;
    }

    public void eol() {
        if (emitEOL) {
            emitSymbol(CUDAAssemblerConstants.EOL);
        } else {
            space();
        }
    }

    public void setDelimiter(String value) {
        delimiter = value;
    }

    public void delimiter() {
        emitSymbol(delimiter);
    }

    public void emitLine(String fmt, Object... args) {
        emitLine(String.format(fmt, args));
    }

    public void emitLine(String str) {
        indent();
        emitSubString(str);
        eol();
    }

    public void emit(String str) {
        emitSubString(str);
    }

    public void emitLineGlobal(String str) {
        int size = position();
        byte[] codeCopy = copy(0, size);
        String s = new String(codeCopy);
        str += s;
        emitString(str, 0);
    }

    public void emit(String fmt, Object... args) {
        emitSubString(String.format(fmt, args));
    }

    public void dump() {
        for (int i = 0; i < position(); i++) {
            System.out.printf("%c", (char) getByte(i));
        }
    }

    public void ret() {
        emitStmt("return");

    }

    public void endScope(String blockName) {
        popIndent();
        emitLine(CUDAAssemblerConstants.CURLY_BRACKET_CLOSE + "  // " + blockName);
    }

    public void endScope() {
        popIndent();
        emitLine(CUDAAssemblerConstants.CURLY_BRACKET_CLOSE);
    }

    public void beginScope() {
        emitLine(CUDAAssemblerConstants.CURLY_BRACKET_OPEN);
        pushIndent();
    }

    private String encodeString(String str) {
        return str.replace("\n", "\\n").replace("\t", "\\t").replace("\"", "");
    }

    private String addLiteralSuffix(CUDAKind oclKind, String value) {
        String result = value;
        if (oclKind == FLOAT) {
            result += "F";
        } else if (oclKind.isInteger()) {
            if (oclKind.isUnsigned()) {
                result += "U";
            }

            if (oclKind == LONG || oclKind == ULONG) {
                result += "L";
            }
        }
        return result;
    }

    public void emitConstant(ConstantValue cv) {
        emit(formatConstant(cv));
    }

    public void emitConstant(Constant constant) {
        emit(constant.toValueString());
    }

    public String formatConstant(ConstantValue cv) {
        String result = "";
        JavaConstant javaConstant = cv.getJavaConstant();
        Constant constant = cv.getConstant();
        CUDAKind oclKind = (CUDAKind) cv.getPlatformKind();
        if (oclKind == CUDAKind.HALF && !(constant instanceof HotSpotObjectConstant)) {
            // A __half (cuda_fp16.h) constant cannot be written as a bare numeric
            // literal: such a literal is a float/double and would either make the
            // overloaded __half operators ambiguous or silently pick the wrong type.
            // Materialise it through __float2half(<value>F) so the value is a
            // genuine __half in the emitted expression.
            String literal = javaConstant.isNull() ? "0" : constant.toValueString();
            return "__float2half(" + literal + "F)";
        }
        if (javaConstant.isNull()) {
            result = addLiteralSuffix(oclKind, "0");
            if (oclKind.isVector()) {
                result = String.format("(%s)(%s)", oclKind.name(), result);
            }
        } else if (constant instanceof HotSpotObjectConstant) {
            HotSpotObjectConstant objConst = (HotSpotObjectConstant) constant;
            // TODO should this be replaced with isInternedString()?
            if (objConst.getJavaKind().isObject() && objConst.getType().getName().compareToIgnoreCase("Ljava/lang/String;") == 0) {
                result = encodeString(objConst.toValueString());
            }
        } else {
            result = constant.toValueString();
            result = addLiteralSuffix(oclKind, result);
        }
        return result;
    }

    public String toString(Value value) {
        String result = "";
        if (value instanceof Variable) {
            Variable var = (Variable) value;
            return convertValueFromGraalFormat(var);
        } else if (value instanceof ConstantValue) {
            if (!((ConstantValue) value).isJavaConstant()) {
                shouldNotReachHere("constant value: ", value);
            }
            ConstantValue cv = (ConstantValue) value;
            return formatConstant(cv);
        } else if (value instanceof CUDANullary.Parameter) {
            /*
             * This case covers when we want to pass a caller method parameter further down
             * to a callee and there is no assignment of the parameter inside the caller.
             */
            return value.toString();
        } else {
            unimplemented("value: toString() type=%s, value=%s", value.getClass().getName(), value);
        }
        return result;
    }

    public void emitValue(CUDACompilationResultBuilder crb, Value value) {
        if (value instanceof CUDAReturnSlot) {
            ((CUDAReturnSlot) value).emit(crb, this);
        } else {
            emit(toString(value));
        }
    }

    public void emitValueWithFormat(CUDACompilationResultBuilder crb, Value value) {
        if (value instanceof CUDAReturnSlot) {
            ((CUDAReturnSlot) value).emit(crb, this);
        } else {
            emit(CUDAAssembler.convertValueFromGraalFormat(value));
        }
    }

    public String getStringValue(CUDACompilationResultBuilder crb, Value value) {
        if (value instanceof CUDAReturnSlot) {
            return ((CUDAReturnSlot) value).getStringFormat();
        } else {
            return toString(value);
        }
    }

    public void assign() {
        emitSymbol(CUDAAssemblerConstants.ASSIGN);
    }

    public void ifStmt(CUDACompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(CUDAAssemblerConstants.IF_STMT);
        emitSymbol(CUDAAssemblerConstants.OPEN_PARENTHESIS);

        emit(toString(condition));
        if (((CUDAKind) condition.getPlatformKind()) == CUDAKind.INT) {
            emit(" == 1");
        }

        emitSymbol(CUDAAssemblerConstants.CLOSE_PARENTHESIS);
        eol();

    }

    public void space() {
        emitSymbol(" ");
    }

    public void elseIfStmt(CUDACompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(CUDAAssemblerConstants.ELSE);
        space();
        emitSymbol(CUDAAssemblerConstants.IF_STMT);
        emitSymbol(CUDAAssemblerConstants.OPEN_PARENTHESIS);

        emitValue(crb, condition);

        emitSymbol(CUDAAssemblerConstants.CLOSE_PARENTHESIS);
        eol();

    }

    public void elseStmt() {
        emitSymbol(CUDAAssemblerConstants.ELSE);
    }

    public void emitValueOrOp(CUDACompilationResultBuilder crb, Value value) {
        if (value instanceof CUDALIROp) {
            ((CUDALIROp) value).emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }

    /**
     * Emits an operand that participates in {@code __half} (cuda_fp16.h)
     * arithmetic.
     *
     * <p>The C++ operators that cuda_fp16.h defines for {@code __half} accept
     * only {@code __half} operands. A bare floating constant such as
     * {@code 0.0} is a {@code double} and {@code double + __half} is ambiguous
     * (both {@code double->__half} and {@code __half->float->double} conversions
     * exist), which makes NVRTC reject the kernel. To keep the expression
     * unambiguous, any constant operand whose platform kind is not already
     * {@code __half} is materialised through {@code __float2half(...)}.
     */
    public void emitHalfOperand(CUDACompilationResultBuilder crb, Value value) {
        if (value instanceof ConstantValue && value.getPlatformKind() != CUDAKind.HALF) {
            emit("__float2half(");
            emitValue(crb, value);
            emit(")");
        } else {
            emitValueOrOp(crb, value);
        }
    }

    /**
     * Base class for CUDADriver opcodes.
     */
    public static class CUDAOp {

        protected final String opcode;

        protected CUDAOp(String opcode) {
            this.opcode = opcode;
        }

        protected final void emitOpcode(CUDAAssembler asm) {
            asm.emit(opcode);
        }

        public boolean equals(CUDAOp other) {
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
    public static class CUDANullaryOp extends CUDAOp {

        // @formatter:off
        public static final CUDANullaryOp RETURN = new CUDANullaryOp("return");
        // @formatter:on

        protected CUDANullaryOp(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb) {
            final CUDAAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class CUDANullaryIntrinsic extends CUDANullaryOp {
        // @formatter:off

        // @formatter:on
        protected CUDANullaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb) {
            final CUDAAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class CUDANullaryTemplate extends CUDANullaryOp {

        public CUDANullaryTemplate(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb) {
            final CUDAAssembler asm = crb.getAssembler();
            asm.emit(opcode);
        }
    }

    /**
     * Unary opcodes
     */
    public static class CUDAUnaryOp extends CUDAOp {
        // @formatter:off

        public static final CUDAUnaryOp RETURN = new CUDAUnaryOp("return ", true);

        public static final CUDAUnaryOp INC = new CUDAUnaryOp("++", false);
        public static final CUDAUnaryOp DEC = new CUDAUnaryOp("--", false);
        public static final CUDAUnaryOp NEGATE = new CUDAUnaryOp("-", true);

        public static final CUDAUnaryOp LOGICAL_NOT = new CUDAUnaryOp("!", true);

        public static final CUDAUnaryOp BITWISE_NOT = new CUDAUnaryOp("~", true);

        public static final CUDAUnaryOp CAST_TO_INT = new CUDAUnaryOp("(int) ", true);
        public static final CUDAUnaryOp CAST_TO_SHORT = new CUDAUnaryOp("(short) ", true);
        public static final CUDAUnaryOp CAST_TO_LONG = new CUDAUnaryOp("(long) ", true);
        public static final CUDAUnaryOp CAST_TO_ULONG = new CUDAUnaryOp("(unsigned long) ", true);
        public static final CUDAUnaryOp CAST_TO_FLOAT = new CUDAUnaryOp("(float) ", true);
        public static final CUDAUnaryOp CAST_TO_BYTE = new CUDAUnaryOp("(char) ", true);
        public static final CUDAUnaryOp CAST_TO_DOUBLE = new CUDAUnaryOp("(double) ", true);

        public static final CUDAUnaryOp CAST_TO_INT_PTR = new CUDAUnaryOp("(int *) ", true);
        public static final CUDAUnaryOp CAST_TO_SHORT_PTR = new CUDAUnaryOp("(short *) ", true);
        public static final CUDAUnaryOp CAST_TO_LONG_PTR = new CUDAUnaryOp("(long *) ", true);
        public static final CUDAUnaryOp CAST_TO_ULONG_PTR = new CUDAUnaryOp("(unsigned long *) ", true);
        public static final CUDAUnaryOp CAST_TO_FLOAT_PTR = new CUDAUnaryOp("(float *) ", true);
        public static final CUDAUnaryOp CAST_TO_BYTE_PTR = new CUDAUnaryOp("(char *) ", true);
        // @formatter:on

        private final boolean prefix;

        protected CUDAUnaryOp(String opcode) {
            this(opcode, false);
        }

        protected CUDAUnaryOp(String opcode, boolean prefix) {
            super(opcode);
            this.prefix = prefix;
        }

        public void emit(CUDACompilationResultBuilder crb, Value x) {
            final CUDAAssembler asm = crb.getAssembler();
            if (prefix) {
                emitOpcode(asm);
                asm.emitValueOrOp(crb, x);
            } else {
                asm.emitValueOrOp(crb, x);
                emitOpcode(asm);
            }
        }
    }

    /**
     * Unary intrinsic
     */
    public static class CUDAUnaryIntrinsic extends CUDAUnaryOp {
        // @formatter:off

        public static final CUDAUnaryIntrinsic GLOBAL_ID = new CUDAUnaryIntrinsic("get_global_id");
        public static final CUDAUnaryIntrinsic GLOBAL_SIZE = new CUDAUnaryIntrinsic("get_global_size");

        public static final CUDAUnaryIntrinsic CUDA_KERNEL_CONTEXT_ACCESS = new CUDAUnaryIntrinsic("_kernel_context");

        public static final CUDAUnaryIntrinsic LOCAL_ID = new CUDAUnaryIntrinsic("get_local_id");
        public static final CUDAUnaryIntrinsic LOCAL_SIZE = new CUDAUnaryIntrinsic("get_local_size");

        public static final CUDAUnaryIntrinsic GROUP_ID = new CUDAUnaryIntrinsic("get_group_id");
        public static final CUDAUnaryIntrinsic GROUP_SIZE = new CUDAUnaryIntrinsic("get_group_size");

        public static final CUDAUnaryIntrinsic ATOMIC_INC = new CUDAUnaryIntrinsic("atomicInc");
        public static final CUDAUnaryIntrinsic ATOMIC_FETCH_ADD_EXPLICIT = new CUDAUnaryIntrinsic("atomicAdd");
        public static final CUDAUnaryIntrinsic ATOMIC_FETCH_SUB_EXPLICIT = new CUDAUnaryIntrinsic("atomicSub");
        public static final CUDAUnaryIntrinsic ATOM_ADD = new CUDAUnaryIntrinsic("atomicAdd");
        public static final CUDAUnaryIntrinsic ATOMIC_ADD = new CUDAUnaryIntrinsic("atomicAdd");
        public static final CUDAUnaryIntrinsic ATOMIC_VAR_INIT = new CUDAUnaryIntrinsic("");
        public static final CUDAUnaryIntrinsic ATOMIC_DEC = new CUDAUnaryIntrinsic("atomicDec");
        public static final CUDAUnaryIntrinsic ATOMIC_GET = new CUDAUnaryIntrinsic("atomic[0]");

        public static final CUDAUnaryIntrinsic MEMORY_ORDER_RELAXED = new CUDAUnaryIntrinsic("memory_order_relaxed");

        public static final CUDAUnaryIntrinsic BARRIER = new CUDAUnaryIntrinsic("barrier");
        public static final CUDAUnaryIntrinsic MEM_FENCE = new CUDAUnaryIntrinsic("mem_fence");
        public static final CUDAUnaryIntrinsic READ_MEM_FENCE = new CUDAUnaryIntrinsic("read_mem_fence");
        public static final CUDAUnaryIntrinsic WRITE_MEM_FENCE = new CUDAUnaryIntrinsic("write_mem_fence");

        public static final CUDAUnaryIntrinsic ABS = new CUDAUnaryIntrinsic("abs");

        public static final CUDAUnaryIntrinsic CEIL = new CUDAUnaryIntrinsic("ceil");
        public static final CUDAUnaryIntrinsic EXP = new CUDAUnaryIntrinsic("exp");
        public static final CUDAUnaryIntrinsic SQRT = new CUDAUnaryIntrinsic("sqrt");
        public static final CUDAUnaryIntrinsic LOG = new CUDAUnaryIntrinsic("log");
        public static final CUDAUnaryIntrinsic RADIANS = new CUDAUnaryIntrinsic("radians");
        public static final CUDAUnaryIntrinsic RSQRT = new CUDAUnaryIntrinsic("rsqrt");
        public static final CUDAUnaryIntrinsic NATIVE_COS = new CUDAUnaryIntrinsic("cos");
        public static final CUDAUnaryIntrinsic NATIVE_SIN = new CUDAUnaryIntrinsic("sin");
        public static final CUDAUnaryIntrinsic NATIVE_SQRT = new CUDAUnaryIntrinsic("sqrt");
        public static final CUDAUnaryIntrinsic NATIVE_TAN = new CUDAUnaryIntrinsic("tan");
        public static final CUDAUnaryIntrinsic SIN = new CUDAUnaryIntrinsic("sin");
        public static final CUDAUnaryIntrinsic COS = new CUDAUnaryIntrinsic("cos");
        public static final CUDAUnaryIntrinsic TAN = new CUDAUnaryIntrinsic("tan");
        public static final CUDAUnaryIntrinsic TANH = new CUDAUnaryIntrinsic("tanh");
        public static final CUDAUnaryIntrinsic ATAN = new CUDAUnaryIntrinsic("atan");
        public static final CUDAUnaryIntrinsic ASIN = new CUDAUnaryIntrinsic("asin");
        public static final CUDAUnaryIntrinsic ASINH = new CUDAUnaryIntrinsic("asinh");
        public static final CUDAUnaryIntrinsic ACOS = new CUDAUnaryIntrinsic("acos");
        public static final CUDAUnaryIntrinsic ACOSH = new CUDAUnaryIntrinsic("acosh");
        public static final CUDAUnaryIntrinsic SINPI = new CUDAUnaryIntrinsic("sinpi");
        public static final CUDAUnaryIntrinsic COSPI = new CUDAUnaryIntrinsic("cospi");

        public static final CUDAUnaryIntrinsic SIGN = new CUDAUnaryIntrinsic("sign");

        public static final CUDAUnaryIntrinsic LOCAL_MEMORY = new CUDAUnaryIntrinsic("__shared__");

        public static final CUDAUnaryIntrinsic POPCOUNT = new CUDAUnaryIntrinsic("__popc");
        public static final CUDAUnaryIntrinsic POPCOUNT_LONG = new CUDAUnaryIntrinsic("__popcll");

        public static final CUDAUnaryIntrinsic FLOAT_ABS = new CUDAUnaryIntrinsic("fabs");
        public static final CUDAUnaryIntrinsic FLOAT_TRUNC = new CUDAUnaryIntrinsic("trunc");
        public static final CUDAUnaryIntrinsic FLOAT_FLOOR = new CUDAUnaryIntrinsic("floor");

        public static final CUDAUnaryIntrinsic SIGN_BIT = new CUDAUnaryIntrinsic("signbit");

        public static final CUDAUnaryIntrinsic ANY = new CUDAUnaryIntrinsic("any");
        public static final CUDAUnaryIntrinsic ALL = new CUDAUnaryIntrinsic("all");

        public static final CUDAUnaryIntrinsic AS_FLOAT = new CUDAUnaryIntrinsic("as_float");
        public static final CUDAUnaryIntrinsic AS_INT = new CUDAUnaryIntrinsic("as_int");

        public static final CUDAUnaryIntrinsic IS_FINITE = new CUDAUnaryIntrinsic("isfinite");
        public static final CUDAUnaryIntrinsic IS_INF = new CUDAUnaryIntrinsic("isinf");
        public static final CUDAUnaryIntrinsic IS_NAN = new CUDAUnaryIntrinsic("isnan");
        public static final CUDAUnaryIntrinsic IS_NORMAL = new CUDAUnaryIntrinsic("isnormal");
        // @formatter:on

        protected CUDAUnaryIntrinsic(String opcode) {
            super(opcode, true);
        }

        /**
         * Maps an OpenCL-style dimension argument (0/1/2) to a CUDA built-in
         * component suffix (x/y/z). Non-constant dimensions fall back to {@code x}
         * (the only dimension exercised by the MVP scalar kernels).
         */
        private static String dimComponent(CUDACompilationResultBuilder crb, Value x) {
            String dim = crb.getAssembler().getStringValue(crb, x).trim();
            return switch (dim) {
                case "1" -> "y";
                case "2" -> "z";
                default -> "x";
            };
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value x) {
            final CUDAAssembler asm = crb.getAssembler();
            // CUDA C has no OpenCL-style thread-id / synchronization built-ins.
            // Remap them to inline parenthesized CUDA expressions.
            if (this == GLOBAL_ID && x != null) {
                String c = dimComponent(crb, x);
                asm.emit("(blockIdx." + c + "*blockDim." + c + "+threadIdx." + c + ")");
                return;
            } else if (this == GLOBAL_SIZE && x != null) {
                String c = dimComponent(crb, x);
                asm.emit("(gridDim." + c + "*blockDim." + c + ")");
                return;
            } else if (this == LOCAL_ID && x != null) {
                asm.emit("(threadIdx." + dimComponent(crb, x) + ")");
                return;
            } else if (this == LOCAL_SIZE && x != null) {
                asm.emit("(blockDim." + dimComponent(crb, x) + ")");
                return;
            } else if (this == GROUP_ID && x != null) {
                asm.emit("(blockIdx." + dimComponent(crb, x) + ")");
                return;
            } else if (this == GROUP_SIZE && x != null) {
                asm.emit("(gridDim." + dimComponent(crb, x) + ")");
                return;
            } else if (this == BARRIER) {
                asm.emit("__syncthreads()");
                return;
            } else if (this == RADIANS && x != null) {
                // OpenCL radians(x) has no CUDA equivalent: emit inline degrees->radians.
                asm.emit("((");
                asm.emitValueOrOp(crb, x);
                asm.emit(") * 0.017453292519943295)");
                return;
            } else if (this == SIGN && x != null) {
                // Java Math.signum semantics, NaN-preserving (x != x is true for NaN),
                // and preserving signed zero by returning the operand for the 0 case.
                asm.beginStackPush();
                asm.emitValueOrOp(crb, x);
                final String v = asm.getLastOp();
                asm.endStackPush();
                asm.emit(String.format("((%s) != (%s) ? (%s) : ((%s) > 0 ? 1 : ((%s) < 0 ? -1 : (%s))))", v, v, v, v, v, v));
                return;
            }
            emitOpcode(asm);
            if (x != null) {
                asm.emit("(");
                asm.emitValueOrOp(crb, x);
                asm.emit(")");
            }
        }

        public void emit(CUDACompilationResultBuilder crb) {
            emit(crb, null);
        }
    }

    public static class CUDAUnaryTemplate extends CUDAUnaryOp {
        // @formatter:off

        public static final CUDAUnaryTemplate MEM_CHECK = new CUDAUnaryTemplate("mem check", "MEM_CHECK(%s)");
        public static final CUDAUnaryTemplate INDIRECTION = new CUDAUnaryTemplate("deref", "*(%s)");
        public static final CUDAUnaryTemplate CAST_TO_POINTER = new CUDAUnaryTemplate("cast ptr", "(%s *)");
        public static final CUDAUnaryTemplate LOAD_ADDRESS_ABS = new CUDAUnaryTemplate("load address", "*(%s)");
        public static final CUDAUnaryTemplate ADDRESS_OF = new CUDAUnaryTemplate("address of", "&(%s)");

        public static final CUDAUnaryTemplate NEW_INT_ARRAY = new CUDAUnaryTemplate("int[]", "int[%s]");
        public static final CUDAUnaryTemplate NEW_LONG_ARRAY = new CUDAUnaryTemplate("long[]", "long[%s]");
        public static final CUDAUnaryTemplate NEW_FLOAT_ARRAY = new CUDAUnaryTemplate("float[]", "float[%s]");
        public static final CUDAUnaryTemplate NEW_DOUBLE_ARRAY = new CUDAUnaryTemplate("double[]", "double[%s]");
        public static final CUDAUnaryTemplate NEW_BYTE_ARRAY = new CUDAUnaryTemplate("char[]", "char[%s]");
        public static final CUDAUnaryTemplate NEW_CHAR_ARRAY = new CUDAUnaryTemplate("char[]", "char[%s]");
        public static final CUDAUnaryTemplate NEW_SHORT_ARRAY = new CUDAUnaryTemplate("short[]", "short[%s]");

        // @formatter:on
        private final String template;

        protected CUDAUnaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value value) {
            final CUDAAssembler asm = crb.getAssembler();
            asm.emit(template, asm.toString(value));
        }

        public String getTemplate() {
            return template;
        }

    }

    /**
     * Binary opcodes
     */
    public static class CUDABinaryOp extends CUDAOp {
        // @formatter:off

        public static final CUDABinaryOp ADD = new CUDABinaryOp("+");
        public static final CUDABinaryOp SUB = new CUDABinaryOp("-");
        public static final CUDABinaryOp MUL = new CUDABinaryOp("*");
        public static final CUDABinaryOp DIV = new CUDABinaryOp("/");
        public static final CUDABinaryOp MOD = new CUDABinaryOp("%");

        public static final CUDABinaryOp BITWISE_AND = new CUDABinaryOp("&");
        public static final CUDABinaryOp BITWISE_OR = new CUDABinaryOp("|");
        public static final CUDABinaryOp BITWISE_XOR = new CUDABinaryOp("^");
        public static final CUDABinaryOp BITWISE_LEFT_SHIFT = new CUDABinaryOp("<<");
        public static final CUDABinaryOp BITWISE_RIGHT_SHIFT = new CUDABinaryOp(">>");

        public static final CUDABinaryOp LOGICAL_AND = new CUDABinaryOp("&&");
        public static final CUDABinaryOp LOGICAL_OR = new CUDABinaryOp("||");

        public static final CUDABinaryOp ASSIGN = new CUDABinaryOp("=");

        public static final CUDABinaryOp VECTOR_SELECT = new CUDABinaryOp(".");

        public static final CUDABinaryOp RELATIONAL_EQ = new CUDABinaryOp("==");
        public static final CUDABinaryOp RELATIONAL_NE = new CUDABinaryOp("!=");
        public static final CUDABinaryOp RELATIONAL_GT = new CUDABinaryOp(">");
        public static final CUDABinaryOp RELATIONAL_LT = new CUDABinaryOp("<");
        public static final CUDABinaryOp RELATIONAL_GTE = new CUDABinaryOp(">=");
        public static final CUDABinaryOp RELATIONAL_LTE = new CUDABinaryOp("<=");
        // @formatter:on

        protected CUDABinaryOp(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb, Value x, Value y) {
            final CUDAAssembler asm = crb.getAssembler();
            // When one side of a binary expression is a __half (cuda_fp16.h) and the
            // other is a non-__half constant (e.g. a reduction neutral element such
            // as 0.0), the C++ operator overload for __half becomes ambiguous under
            // NVRTC. Materialise such constants through __float2half(...) so the
            // expression stays unambiguous. This is the general scalar-binary path.
            boolean halfContext = x.getPlatformKind() == CUDAKind.HALF || y.getPlatformKind() == CUDAKind.HALF;
            if (halfContext) {
                asm.emitHalfOperand(crb, x);
                asm.space();
                emitOpcode(asm);
                asm.space();
                asm.emitHalfOperand(crb, y);
                return;
            }
            asm.emitValueOrOp(crb, x);
            asm.space();
            emitOpcode(asm);
            asm.space();
            asm.emitValueOrOp(crb, y);
        }
    }

    /**
     * Binary intrinsic
     */
    public static class CUDABinaryIntrinsic extends CUDABinaryOp {
        // @formatter:off

        public static final CUDABinaryIntrinsic INT_MIN = new CUDABinaryIntrinsic("min");
        public static final CUDABinaryIntrinsic INT_MAX = new CUDABinaryIntrinsic("max");

        public static final CUDABinaryIntrinsic ATAN2 = new CUDABinaryIntrinsic("atan2");

        public static final CUDABinaryIntrinsic FLOAT_MIN = new CUDABinaryIntrinsic("fmin");
        public static final CUDABinaryIntrinsic FLOAT_MAX = new CUDABinaryIntrinsic("fmax");
        public static final CUDABinaryIntrinsic FLOAT_POW = new CUDABinaryIntrinsic("pow");

        public static final CUDABinaryIntrinsic ATOMIC_ADD = new CUDABinaryIntrinsic("atomicAdd");
        public static final CUDABinaryIntrinsic ATOMIC_SUB = new CUDABinaryIntrinsic("atomicSub");
        public static final CUDABinaryIntrinsic ATOMIC_XCHG = new CUDABinaryIntrinsic("atomicExch");
        public static final CUDABinaryIntrinsic ATOMIC_MIN = new CUDABinaryIntrinsic("atomicMin");
        public static final CUDABinaryIntrinsic ATOMIC_MAX = new CUDABinaryIntrinsic("atomicMax");
        public static final CUDABinaryIntrinsic ATOMIC_AND = new CUDABinaryIntrinsic("atomicAnd");
        public static final CUDABinaryIntrinsic ATOMIC_OR = new CUDABinaryIntrinsic("atomicOr");
        public static final CUDABinaryIntrinsic ATOMIC_XOR = new CUDABinaryIntrinsic("atomicXor");

        public static final CUDABinaryIntrinsic VLOAD2 = new CUDABinaryIntrinsic("vload2");
        public static final CUDABinaryIntrinsic VLOAD3 = new CUDABinaryIntrinsic("vload3");
        public static final CUDABinaryIntrinsic VLOAD4 = new CUDABinaryIntrinsic("vload4");
        public static final CUDABinaryIntrinsic VLOAD8 = new CUDABinaryIntrinsic("vload8");
        public static final CUDABinaryIntrinsic VLOAD16 = new CUDABinaryIntrinsic("vload16");

        public static final CUDABinaryIntrinsic DOT = new CUDABinaryIntrinsic("dot");
        public static final CUDABinaryIntrinsic CROSS = new CUDABinaryIntrinsic("cross");
        // @formatter:on

        protected CUDABinaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value x, Value y) {
            final CUDAAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValueOrOp(crb, x);
            asm.emit(", ");
            asm.emitValueOrOp(crb, y);
            asm.emit(")");
        }
    }

    public static class CUDABinaryIntrinsicCmp extends CUDABinaryOp {

        // @formatter:off
        // The 'opcode' here is the C relational operator to emit inline. CUDA C / NVRTC
        // has no OpenCL isequal/isless/... built-ins for scalars, so we emit the
        // corresponding C operator which already yields an int (0/1).
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_EQUAL = new CUDABinaryIntrinsicCmp("==");
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_NOT_EQUAL = new CUDABinaryIntrinsicCmp("!=");
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_GREATER = new CUDABinaryIntrinsicCmp(">");
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_GREATEREQUAL = new CUDABinaryIntrinsicCmp(">=");
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_LESS = new CUDABinaryIntrinsicCmp("<");
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_LESSEQUAL = new CUDABinaryIntrinsicCmp("<=");
        // islessgreater(a,b) == (a<b)||(a>b); handled specially below.
        public static final CUDABinaryIntrinsicCmp FLOAT_IS_LESSGREATER = new CUDABinaryIntrinsicCmp("<>");
        // @formatter:on

        protected CUDABinaryIntrinsicCmp(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value x, Value y) {
            final CUDAAssembler asm = crb.getAssembler();
            if (this == FLOAT_IS_LESSGREATER) {
                asm.beginStackPush();
                asm.emitValueOrOp(crb, x);
                final String a = asm.getLastOp();
                asm.emitValueOrOp(crb, y);
                final String b = asm.getLastOp();
                asm.endStackPush();
                asm.emit(String.format("(((%s) < (%s)) || ((%s) > (%s)))", a, b, a, b));
                return;
            }
            asm.emit("((");
            asm.emitValueOrOp(crb, x);
            asm.emit(") ");
            emitOpcode(asm);
            asm.emit(" (");
            asm.emitValueOrOp(crb, y);
            asm.emit("))");
        }
    }

    public static class CUDABinaryTemplate extends CUDABinaryOp {
        // @formatter:off

        public static final CUDABinaryTemplate DECLARE_BYTE_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "byte %s[%s]");
        public static final CUDABinaryTemplate DECLARE_CHAR_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "char %s[%s]");
        public static final CUDABinaryTemplate DECLARE_SHORT_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "short %s[%s]");
        public static final CUDABinaryTemplate DECLARE_INT_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "int %s[%s]");
        public static final CUDABinaryTemplate DECLARE_LONG_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "long %s[%s]");
        public static final CUDABinaryTemplate DECLARE_FLOAT_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "float %s[%s]");
        public static final CUDABinaryTemplate DECLARE_DOUBLE_ARRAY = new CUDABinaryTemplate("DECLARE_ARRAY", "double %s[%s]");
        public static final CUDABinaryTemplate ARRAY_INDEX = new CUDABinaryTemplate("index", "%s[%s]");

        public static final CUDABinaryTemplate NEW_PRIVATE_CHAR_ARRAY = new CUDABinaryTemplate("new private array char", "char %s[%s]");
        public static final CUDABinaryTemplate NEW_PRIVATE_FLOAT_ARRAY = new CUDABinaryTemplate("new private array float", "float %s[%s]");
        public static final CUDABinaryTemplate NEW_PRIVATE_INT_ARRAY = new CUDABinaryTemplate("new private array int", "int %s[%s]");
        public static final CUDABinaryTemplate NEW_PRIVATE_DOUBLE_ARRAY = new CUDABinaryTemplate("new private array double", "double %s[%s]");
        public static final CUDABinaryTemplate NEW_PRIVATE_LONG_ARRAY = new CUDABinaryTemplate("new private array long", "long %s[%s]");
        public static final CUDABinaryTemplate NEW_PRIVATE_SHORT_ARRAY = new CUDABinaryTemplate("new private array short", "short %s[%s]");
        public static final CUDABinaryTemplate NEW_PRIVATE_BYTE_ARRAY = new CUDABinaryTemplate("new private array byte", "byte %s[%s]");

        public static final CUDABinaryTemplate PRIVATE_INT_ARRAY_PTR = new CUDABinaryTemplate("private pointer array int", "int* %s = %s");
        public static final CUDABinaryTemplate PRIVATE_CHAR_ARRAY_PTR = new CUDABinaryTemplate("private pointer array char", "char* %s = %s");
        public static final CUDABinaryTemplate PRIVATE_FLOAT_ARRAY_PTR = new CUDABinaryTemplate("private pointer array float", "float* %s = %s");
        public static final CUDABinaryTemplate PRIVATE_DOUBLE_ARRAY_PTR = new CUDABinaryTemplate("private pointer array double", "double* %s = %s");
        public static final CUDABinaryTemplate PRIVATE_LONG_ARRAY_PTR = new CUDABinaryTemplate("private pointer array long", "long* %s = %s");
        public static final CUDABinaryTemplate PRIVATE_SHORT_ARRAY_PTR = new CUDABinaryTemplate("private pointer array short", "short* %s = %s");
        public static final CUDABinaryTemplate PRIVATE_BYTE_ARRAY_PTR = new CUDABinaryTemplate("private pointer array byte", "byte* %s = %s");

        public static final CUDABinaryTemplate PRIVATE_INT_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array int", "int* %s = ((int *) %s)");
        public static final CUDABinaryTemplate PRIVATE_CHAR_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array char", "char* %s = ((char *) %s)");
        public static final CUDABinaryTemplate PRIVATE_FLOAT_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array float", "float* %s = ((float *) %s)");
        public static final CUDABinaryTemplate PRIVATE_DOUBLE_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array double", "double* %s = ((double *) %s)");
        public static final CUDABinaryTemplate PRIVATE_LONG_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array long", "long* %s = ((long *) %s)");
        public static final CUDABinaryTemplate PRIVATE_SHORT_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array short", "short* %s = ((short *) %s)");
        public static final CUDABinaryTemplate PRIVATE_BYTE_ARRAY_PTR_COPY = new CUDABinaryTemplate("private pointer copy array byte", "byte* %s = ((byte *) %s)");

        public static final CUDABinaryTemplate NEW_LOCAL_FLOAT_ARRAY = new CUDABinaryTemplate("local memory array float", "__shared__ float %s[%s]");
        public static final CUDABinaryTemplate NEW_LOCAL_INT_ARRAY = new CUDABinaryTemplate("local memory array int", "__shared__ int %s[%s]");
        public static final CUDABinaryTemplate NEW_LOCAL_DOUBLE_ARRAY = new CUDABinaryTemplate("local memory array double", "__shared__ double %s[%s]");
        public static final CUDABinaryTemplate NEW_LOCAL_LONG_ARRAY = new CUDABinaryTemplate("local memory array long", "__shared__ long %s[%s]");
        public static final CUDABinaryTemplate NEW_LOCAL_SHORT_ARRAY = new CUDABinaryTemplate("local memory array short", "__shared__ short %s[%s]");
        public static final CUDABinaryTemplate NEW_LOCAL_CHAR_ARRAY = new CUDABinaryTemplate("local memory array char", "__shared__ char %s[%s]");
        public static final CUDABinaryTemplate NEW_LOCAL_HALF_FLOAT_ARRAY = new CUDABinaryTemplate("local memory array half", "__shared__ half %s[%s]");
        // @formatter:on
        private final String template;

        protected CUDABinaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value x, Value y) {
            final CUDAAssembler asm = crb.getAssembler();
            asm.beginStackPush();
            asm.emitValueOrOp(crb, x);
            final String input1 = asm.getLastOp();
            asm.emitValueOrOp(crb, y);
            final String input2 = asm.getLastOp();
            asm.endStackPush();

            asm.emit(template, input1, input2);
        }

    }

    /**
     * Ternary opcodes
     */
    public static class CUDATernaryOp extends CUDAOp {
        // @formatter:off

        // @formatter:on
        protected CUDATernaryOp(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb, Value x, Value y, Value z) {
            final CUDAAssembler asm = crb.getAssembler();
            asm.emitLine("// unimplemented ternary op:");
        }
    }

    /**
     * Ternary intrinsic
     */
    public static class CUDATernaryIntrinsic extends CUDATernaryOp {
        // @formatter:off

        public static final CUDATernaryIntrinsic VSTORE2 = new CUDATernaryIntrinsic("vstore2");
        public static final CUDATernaryIntrinsic VSTORE3 = new CUDATernaryIntrinsic("vstore3");
        public static final CUDATernaryIntrinsic VSTORE4 = new CUDATernaryIntrinsic("vstore4");
        public static final CUDATernaryIntrinsic VSTORE8 = new CUDATernaryIntrinsic("vstore8");
        public static final CUDATernaryIntrinsic VSTORE16 = new CUDATernaryIntrinsic("vstore16");
        public static final CUDATernaryIntrinsic CLAMP = new CUDATernaryIntrinsic("clamp");
        public static final CUDATernaryIntrinsic FMA = new CUDATernaryIntrinsic("fma");
        // @formatter:on

        protected CUDATernaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value x, Value y, Value z) {
            final CUDAAssembler asm = crb.getAssembler();
            if (this == CLAMP) {
                // OpenCL clamp(x, lo, hi) has no CUDA equivalent. Emit it inline.
                // Floating point: use the precision-correct CUDA fmin/fmax
                // (fminf/fmaxf for float, fmin/fmax for double). Integer kinds:
                // a branchless ternary to avoid floating-point math.
                final CUDAKind kind = (CUDAKind) x.getPlatformKind();
                asm.beginStackPush();
                asm.emitValueOrOp(crb, x);
                final String xs = asm.getLastOp();
                asm.emitValueOrOp(crb, y);
                final String lo = asm.getLastOp();
                asm.emitValueOrOp(crb, z);
                final String hi = asm.getLastOp();
                asm.endStackPush();
                if (kind == CUDAKind.FLOAT) {
                    asm.emit(String.format("fminf(fmaxf(%s, %s), %s)", xs, lo, hi));
                } else if (kind == CUDAKind.DOUBLE) {
                    asm.emit(String.format("fmin(fmax(%s, %s), %s)", xs, lo, hi));
                } else {
                    asm.emit(String.format("(((%s) < (%s)) ? (%s) : (((%s) > (%s)) ? (%s) : (%s)))", xs, lo, lo, xs, hi, hi, xs));
                }
                return;
            }
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValueOrOp(crb, x);
            asm.emit(", ");
            asm.emitValueOrOp(crb, y);
            asm.emit(", ");
            asm.emitValueOrOp(crb, z);
            asm.emit(")");
        }
    }

    public static class CUDATernaryTemplate extends CUDATernaryOp {
        // @formatter:off

        public static final CUDATernaryTemplate SELECT = new CUDATernaryTemplate("select", "(%s) ? %s : %s");

        // @formatter:on
        private final String template;

        protected CUDATernaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(CUDACompilationResultBuilder crb, Value x, Value y, Value z) {
            final CUDAAssembler asm = crb.getAssembler();
            asm.beginStackPush();
            asm.emitValueOrOp(crb, x);
            final String input1 = asm.getLastOp();
            asm.emitValueOrOp(crb, y);
            final String input2 = asm.getLastOp();
            asm.emitValueOrOp(crb, z);
            final String input3 = asm.getLastOp();
            asm.endStackPush();

            asm.emit(template, input1, input2, input3);
        }

    }

    public static class CUDAOp2 extends CUDAOp {

        // @formatter:off
        public static final CUDAOp2 VMOV_SHORT2 = new CUDAOp2("make_short2");
        public static final CUDAOp2 VMOV_INT2 = new CUDAOp2("make_int2");
        public static final CUDAOp2 VMOV_FLOAT2 = new CUDAOp2("make_float2");
        public static final CUDAOp2 VMOV_BYTE2 = new CUDAOp2("make_char2");
        public static final CUDAOp2 VMOV_DOUBLE2 = new CUDAOp2("make_double2");

        public static final CUDAOp2 VMOV_HALF2 = new CUDAOp2("make_half2");
        // @formatter:on

        protected CUDAOp2(String opcode) {
            super(opcode);
        }

        // FIXME: Remove these emits from vector operations. They are not reachable
        public void emit(CUDACompilationResultBuilder crb, Value s0, Value s1) {
            final CUDAAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(")");
        }
    }

    public static class CUDAOp3 extends CUDAOp2 {
        // @formatter:off

        public static final CUDAOp3 VMOV_SHORT3 = new CUDAOp3("make_short3");
        public static final CUDAOp3 VMOV_INT3 = new CUDAOp3("make_int3");
        public static final CUDAOp3 VMOV_FLOAT3 = new CUDAOp3("make_float3");
        public static final CUDAOp3 VMOV_BYTE3 = new CUDAOp3("make_char3");
        public static final CUDAOp3 VMOV_DOUBLE3 = new CUDAOp3("make_double3");

        public static final CUDAOp3 VMOV_HALF3 = new CUDAOp3("make_half3");

        // @formatter:on
        public CUDAOp3(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb, Value s0, Value s1, Value s2) {
            final CUDAAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(")");
        }
    }

    public static class CUDAOp4 extends CUDAOp3 {
        // @formatter:off

        public static final CUDAOp4 VMOV_SHORT4 = new CUDAOp4("make_short4");
        public static final CUDAOp4 VMOV_INT4 = new CUDAOp4("make_int4");
        public static final CUDAOp4 VMOV_FLOAT4 = new CUDAOp4("make_float4");
        public static final CUDAOp4 VMOV_BYTE4 = new CUDAOp4("make_char4");
        public static final CUDAOp4 VMOV_DOUBLE4 = new CUDAOp4("make_double4");

        public static final CUDAOp4 VMOV_HALF4 = new CUDAOp4("make_half4");
        // @formatter:on

        protected CUDAOp4(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3) {
            final CUDAAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(", ");
            asm.emitValue(crb, s3);
            asm.emit(")");
        }
    }

    public static class CUDAOp8 extends CUDAOp4 {
        // @formatter:off

        public static final CUDAOp8 VMOV_SHORT8 = new CUDAOp8("(short8)");
        public static final CUDAOp8 VMOV_INT8 = new CUDAOp8("(int8)");
        public static final CUDAOp8 VMOV_FLOAT8 = new CUDAOp8("(float8)");
        public static final CUDAOp8 VMOV_BYTE8 = new CUDAOp8("(char8)");
        public static final CUDAOp8 VMOV_DOUBLE8 = new CUDAOp8("(double8)");

        public static final CUDAOp8 VMOV_HALF8 = new CUDAOp8("(half8)");

        // @formatter:on

        protected CUDAOp8(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            throw new uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException("CUDA backend does not support width-8 vector types.");
        }
    }

    public static class CUDAOp16 extends CUDAOp8 {
        // @formatter:off
        public static final CUDAOp16 VMOV_SHORT16 = new CUDAOp16("(short16)");
        public static final CUDAOp16 VMOV_INT16 = new CUDAOp16("(int16)");
        public static final CUDAOp16 VMOV_FLOAT16 = new CUDAOp16("(float16)");
        public static final CUDAOp16 VMOV_BYTE16 = new CUDAOp16("(char16)");
        public static final CUDAOp16 VMOV_DOUBLE16 = new CUDAOp16("(double16)");
        public static final CUDAOp16 VMOV_HALF16 = new CUDAOp16("(half16)");
        // @formatter:on
        protected CUDAOp16(String opcode) {
            super(opcode);
        }

        public void emit(CUDACompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7, Value s8, Value s9, Value s10, Value s11, Value s12,
                Value s13, Value s14, Value s15) {
            throw new uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException("CUDA backend does not support width-16 vector types.");
        }
    }
}
