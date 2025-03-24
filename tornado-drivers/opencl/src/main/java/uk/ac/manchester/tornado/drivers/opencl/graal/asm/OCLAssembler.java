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
package uk.ac.manchester.tornado.drivers.opencl.graal.asm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind.FLOAT;
import static uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind.LONG;
import static uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind.ULONG;

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
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIROp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLNullary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLReturnSlot;

public final class OCLAssembler extends Assembler {

    private static final boolean EMIT_INTRINSICS = false;
    private int indent;
    private int lastIndent;
    private String delimiter;
    private boolean emitEOL;
    private List<String> operandStack;
    private boolean pushToStack;

    public OCLAssembler(TargetDescription target) {
        super(target, null);
        indent = 0;
        delimiter = OCLAssemblerConstants.STMT_DELIMITER;
        emitEOL = true;
        operandStack = new ArrayList<>(10);
        pushToStack = false;

        if (((OCLTargetDescription) target).supportsFP64()) {
            emitLine("#pragma OPENCL EXTENSION cl_khr_fp64 : enable  ");
        }

        emitLine("#pragma OPENCL EXTENSION cl_khr_fp16 : enable  ");

        if (((OCLTargetDescription) target).supportsInt64Atomics()) {
            emitLine("#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable  ");
        }

        if (EMIT_INTRINSICS) {
            emitAtomicIntrinsics();
        }
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
        OCLVariablePrefix typePrefix = Arrays.stream(OCLVariablePrefix.values()).filter(tp -> tp.getType().equals(type)).findFirst().orElse(null);

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

    private void emitAtomicIntrinsics() {
        //@formatter:off
        emitLine("inline void atomicAdd_Tornado_Floats(volatile __global float *source, const float operand) {\n" +
                "   union {\n" +
                "       unsigned int intVal;\n" +
                "       float floatVal;\n" +
                "   } newVal;\n" +
                "   union {\n" +
                "       unsigned int intVal;\n" +
                "       float floatVal;\n" +
                "   } prevVal;\n" +
                "   barrier(CLK_GLOBAL_MEM_FENCE);\n" +
                "   do {\n" +
                "       prevVal.floatVal = *source;\n" +
                "       newVal.floatVal = prevVal.floatVal + operand;\n" +
                "   } while (atomic_cmpxchg((volatile __global unsigned int *)source, prevVal.intVal,\n" +
                "   newVal.intVal) != prevVal.intVal);" +
                "}");

        emitLine("inline void atomicAdd_Tornado_Floats2(volatile __global float *addr, float val)\n" +
                "{\n" +
                "    union {\n" +
                "        unsigned int u32;\n" +
                "        float f32;\n" +
                "    } next, expected, current;\n" +
                "    current.f32 = *addr;\n" +
                "barrier(CLK_GLOBAL_MEM_FENCE);\n" +
                "    do {\n" +
                "       expected.f32 = current.f32;\n" +
                "       next.f32 = expected.f32 + val;\n" +
                "       current.u32 = atomic_cmpxchg( (volatile __global unsigned int *)addr,\n" +
                "       expected.u32, next.u32);\n" +
                "    } while( current.u32 != expected.u32 );\n" +
                "}");

        emitLine("inline void atomicMul_Tornado_Int(volatile __global int *source, const float operand) {\n" +
                "   union {\n" +
                "       unsigned int intVal;\n" +
                "       int value;\n" +
                "   } newVal;\n" +
                "   union {\n" +
                "       unsigned int intVal;\n" +
                "       int value;\n" +
                "   } prevVal;\n" +
                "   barrier(CLK_GLOBAL_MEM_FENCE);\n" +
                "   do {\n" +
                "       prevVal.value = *source;\n" +
                "       newVal.value = prevVal.value * operand;\n" +
                "   } while (atomic_cmpxchg((volatile __global unsigned int *)source, prevVal.intVal,\n" +
                "   newVal.intVal) != prevVal.intVal);" +
                "}");
        //@formatter:on
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
            emitSymbol(OCLAssemblerConstants.TAB);
        }
    }

    public void comment(String comment) {
        emit(" /* " + comment + " */ ");
        eol();
    }

    public void loopBreak() {
        emit(OCLAssemblerConstants.BREAK);
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
            emitSymbol(OCLAssemblerConstants.EOL);
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
        emitLine(OCLAssemblerConstants.CURLY_BRACKET_CLOSE + "  // " + blockName);
    }

    public void endScope() {
        popIndent();
        emitLine(OCLAssemblerConstants.CURLY_BRACKET_CLOSE);
    }

    public void beginScope() {
        emitLine(OCLAssemblerConstants.CURLY_BRACKET_OPEN);
        pushIndent();
    }

    private String encodeString(String str) {
        return str.replace("\n", "\\n").replace("\t", "\\t").replace("\"", "");
    }

    private String addLiteralSuffix(OCLKind oclKind, String value) {
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
        OCLKind oclKind = (OCLKind) cv.getPlatformKind();
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
        } else if (value instanceof OCLNullary.Parameter) {
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

    public void emitValue(OCLCompilationResultBuilder crb, Value value) {
        if (value instanceof OCLReturnSlot) {
            ((OCLReturnSlot) value).emit(crb, this);
        } else {
            emit(toString(value));
        }
    }

    public void emitValueWithFormat(OCLCompilationResultBuilder crb, Value value) {
        if (value instanceof OCLReturnSlot) {
            ((OCLReturnSlot) value).emit(crb, this);
        } else {
            emit(OCLAssembler.convertValueFromGraalFormat(value));
        }
    }

    public String getStringValue(OCLCompilationResultBuilder crb, Value value) {
        if (value instanceof OCLReturnSlot) {
            return ((OCLReturnSlot) value).getStringFormat();
        } else {
            return toString(value);
        }
    }

    public void assign() {
        emitSymbol(OCLAssemblerConstants.ASSIGN);
    }

    public void ifStmt(OCLCompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(OCLAssemblerConstants.IF_STMT);
        emitSymbol(OCLAssemblerConstants.OPEN_PARENTHESIS);

        emit(toString(condition));
        if (((OCLKind) condition.getPlatformKind()) == OCLKind.INT) {
            emit(" == 1");
        }

        emitSymbol(OCLAssemblerConstants.CLOSE_PARENTHESIS);
        eol();

    }

    public void space() {
        emitSymbol(" ");
    }

    public void elseIfStmt(OCLCompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(OCLAssemblerConstants.ELSE);
        space();
        emitSymbol(OCLAssemblerConstants.IF_STMT);
        emitSymbol(OCLAssemblerConstants.OPEN_PARENTHESIS);

        emitValue(crb, condition);

        emitSymbol(OCLAssemblerConstants.CLOSE_PARENTHESIS);
        eol();

    }

    public void elseStmt() {
        emitSymbol(OCLAssemblerConstants.ELSE);
    }

    public void emitValueOrOp(OCLCompilationResultBuilder crb, Value value) {
        if (value instanceof OCLLIROp) {
            ((OCLLIROp) value).emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }

    /**
     * Base class for OpenCL opcodes.
     */
    public static class OCLOp {

        protected final String opcode;

        protected OCLOp(String opcode) {
            this.opcode = opcode;
        }

        protected final void emitOpcode(OCLAssembler asm) {
            asm.emit(opcode);
        }

        public boolean equals(OCLOp other) {
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
    public static class OCLNullaryOp extends OCLOp {

        // @formatter:off
        public static final OCLNullaryOp RETURN = new OCLNullaryOp("return");
        // @formatter:on

        protected OCLNullaryOp(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class OCLNullaryIntrinsic extends OCLNullaryOp {
        // @formatter:off

        // @formatter:on
        protected OCLNullaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class OCLNullaryTemplate extends OCLNullaryOp {

        public OCLNullaryTemplate(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.emit(opcode);
        }
    }

    /**
     * Unary opcodes
     */
    public static class OCLUnaryOp extends OCLOp {
        // @formatter:off

        public static final OCLUnaryOp RETURN = new OCLUnaryOp("return ", true);

        public static final OCLUnaryOp INC = new OCLUnaryOp("++", false);
        public static final OCLUnaryOp DEC = new OCLUnaryOp("--", false);
        public static final OCLUnaryOp NEGATE = new OCLUnaryOp("-", true);

        public static final OCLUnaryOp LOGICAL_NOT = new OCLUnaryOp("!", true);

        public static final OCLUnaryOp BITWISE_NOT = new OCLUnaryOp("~", true);

        public static final OCLUnaryOp CAST_TO_INT = new OCLUnaryOp("(int) ", true);
        public static final OCLUnaryOp CAST_TO_SHORT = new OCLUnaryOp("(short) ", true);
        public static final OCLUnaryOp CAST_TO_LONG = new OCLUnaryOp("(long) ", true);
        public static final OCLUnaryOp CAST_TO_ULONG = new OCLUnaryOp("(ulong) ", true);
        public static final OCLUnaryOp CAST_TO_FLOAT = new OCLUnaryOp("(float) ", true);
        public static final OCLUnaryOp CAST_TO_BYTE = new OCLUnaryOp("(char) ", true);
        public static final OCLUnaryOp CAST_TO_DOUBLE = new OCLUnaryOp("(double) ", true);

        public static final OCLUnaryOp CAST_TO_INT_PTR = new OCLUnaryOp("(int *) ", true);
        public static final OCLUnaryOp CAST_TO_SHORT_PTR = new OCLUnaryOp("(short *) ", true);
        public static final OCLUnaryOp CAST_TO_LONG_PTR = new OCLUnaryOp("(long *) ", true);
        public static final OCLUnaryOp CAST_TO_ULONG_PTR = new OCLUnaryOp("(ulong *) ", true);
        public static final OCLUnaryOp CAST_TO_FLOAT_PTR = new OCLUnaryOp("(float *) ", true);
        public static final OCLUnaryOp CAST_TO_BYTE_PTR = new OCLUnaryOp("(char *) ", true);
        // @formatter:on

        private final boolean prefix;

        protected OCLUnaryOp(String opcode) {
            this(opcode, false);
        }

        protected OCLUnaryOp(String opcode, boolean prefix) {
            super(opcode);
            this.prefix = prefix;
        }

        public void emit(OCLCompilationResultBuilder crb, Value x) {
            final OCLAssembler asm = crb.getAssembler();
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
    public static class OCLUnaryIntrinsic extends OCLUnaryOp {
        // @formatter:off

        public static final OCLUnaryIntrinsic GLOBAL_ID = new OCLUnaryIntrinsic("get_global_id");
        public static final OCLUnaryIntrinsic GLOBAL_SIZE = new OCLUnaryIntrinsic("get_global_size");

        public static final OCLUnaryIntrinsic OCL_KERNEL_CONTEXT_ACCESS = new OCLUnaryIntrinsic("_kernel_context");

        public static final OCLUnaryIntrinsic LOCAL_ID = new OCLUnaryIntrinsic("get_local_id");
        public static final OCLUnaryIntrinsic LOCAL_SIZE = new OCLUnaryIntrinsic("get_local_size");

        public static final OCLUnaryIntrinsic GROUP_ID = new OCLUnaryIntrinsic("get_group_id");
        public static final OCLUnaryIntrinsic GROUP_SIZE = new OCLUnaryIntrinsic("get_group_size");

        public static final OCLUnaryIntrinsic ATOMIC_INC = new OCLUnaryIntrinsic("atomic_inc");
        public static final OCLUnaryIntrinsic ATOMIC_FETCH_ADD_EXPLICIT = new OCLUnaryIntrinsic("atomic_fetch_add_explicit");
        public static final OCLUnaryIntrinsic ATOMIC_FETCH_SUB_EXPLICIT = new OCLUnaryIntrinsic("atomic_fetch_sub_explicit");
        public static final OCLUnaryIntrinsic ATOM_ADD = new OCLUnaryIntrinsic("atom_add");
        public static final OCLUnaryIntrinsic ATOMIC_ADD = new OCLUnaryIntrinsic("atomic_add");
        public static final OCLUnaryIntrinsic ATOMIC_VAR_INIT = new OCLUnaryIntrinsic("ATOMIC_VAR_INIT");
        public static final OCLUnaryIntrinsic ATOMIC_DEC = new OCLUnaryIntrinsic("atomic_dec");
        public static final OCLUnaryIntrinsic ATOMIC_GET = new OCLUnaryIntrinsic("atomic[0]");

        public static final OCLUnaryIntrinsic MEMORY_ORDER_RELAXED = new OCLUnaryIntrinsic("memory_order_relaxed");

        public static final OCLUnaryIntrinsic BARRIER = new OCLUnaryIntrinsic("barrier");
        public static final OCLUnaryIntrinsic MEM_FENCE = new OCLUnaryIntrinsic("mem_fence");
        public static final OCLUnaryIntrinsic READ_MEM_FENCE = new OCLUnaryIntrinsic("read_mem_fence");
        public static final OCLUnaryIntrinsic WRITE_MEM_FENCE = new OCLUnaryIntrinsic("write_mem_fence");

        public static final OCLUnaryIntrinsic ABS = new OCLUnaryIntrinsic("abs");

        public static final OCLUnaryIntrinsic CEIL = new OCLUnaryIntrinsic("ceil");
        public static final OCLUnaryIntrinsic EXP = new OCLUnaryIntrinsic("exp");
        public static final OCLUnaryIntrinsic SQRT = new OCLUnaryIntrinsic("sqrt");
        public static final OCLUnaryIntrinsic LOG = new OCLUnaryIntrinsic("log");
        public static final OCLUnaryIntrinsic RADIANS = new OCLUnaryIntrinsic("radians");
        public static final OCLUnaryIntrinsic RSQRT = new OCLUnaryIntrinsic("rsqrt");
        public static final OCLUnaryIntrinsic NATIVE_COS = new OCLUnaryIntrinsic("native_cos");
        public static final OCLUnaryIntrinsic NATIVE_SIN = new OCLUnaryIntrinsic("native_sin");
        public static final OCLUnaryIntrinsic NATIVE_SQRT = new OCLUnaryIntrinsic("native_sqrt");
        public static final OCLUnaryIntrinsic NATIVE_TAN = new OCLUnaryIntrinsic("native_tan");
        public static final OCLUnaryIntrinsic SIN = new OCLUnaryIntrinsic("sin");
        public static final OCLUnaryIntrinsic COS = new OCLUnaryIntrinsic("cos");
        public static final OCLUnaryIntrinsic TAN = new OCLUnaryIntrinsic("tan");
        public static final OCLUnaryIntrinsic TANH = new OCLUnaryIntrinsic("tanh");
        public static final OCLUnaryIntrinsic ATAN = new OCLUnaryIntrinsic("atan");
        public static final OCLUnaryIntrinsic ASIN = new OCLUnaryIntrinsic("asin");
        public static final OCLUnaryIntrinsic ASINH = new OCLUnaryIntrinsic("asinh");
        public static final OCLUnaryIntrinsic ACOS = new OCLUnaryIntrinsic("acos");
        public static final OCLUnaryIntrinsic ACOSH = new OCLUnaryIntrinsic("acosh");
        public static final OCLUnaryIntrinsic SINPI = new OCLUnaryIntrinsic("sinpi");
        public static final OCLUnaryIntrinsic COSPI = new OCLUnaryIntrinsic("cospi");

        public static final OCLUnaryIntrinsic SIGN = new OCLUnaryIntrinsic("sign");

        public static final OCLUnaryIntrinsic LOCAL_MEMORY = new OCLUnaryIntrinsic("__local");

        public static final OCLUnaryIntrinsic POPCOUNT = new OCLUnaryIntrinsic("popcount");

        public static final OCLUnaryIntrinsic FLOAT_ABS = new OCLUnaryIntrinsic("fabs");
        public static final OCLUnaryIntrinsic FLOAT_TRUNC = new OCLUnaryIntrinsic("trunc");
        public static final OCLUnaryIntrinsic FLOAT_FLOOR = new OCLUnaryIntrinsic("floor");

        public static final OCLUnaryIntrinsic SIGN_BIT = new OCLUnaryIntrinsic("signbit");

        public static final OCLUnaryIntrinsic ANY = new OCLUnaryIntrinsic("any");
        public static final OCLUnaryIntrinsic ALL = new OCLUnaryIntrinsic("all");

        public static final OCLUnaryIntrinsic AS_FLOAT = new OCLUnaryIntrinsic("as_float");
        public static final OCLUnaryIntrinsic AS_INT = new OCLUnaryIntrinsic("as_int");

        public static final OCLUnaryIntrinsic IS_FINITE = new OCLUnaryIntrinsic("isfinite");
        public static final OCLUnaryIntrinsic IS_INF = new OCLUnaryIntrinsic("isinf");
        public static final OCLUnaryIntrinsic IS_NAN = new OCLUnaryIntrinsic("isnan");
        public static final OCLUnaryIntrinsic IS_NORMAL = new OCLUnaryIntrinsic("isnormal");
        // @formatter:on

        protected OCLUnaryIntrinsic(String opcode) {
            super(opcode, true);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value x) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            if (x != null) {
                asm.emit("(");
                asm.emitValueOrOp(crb, x);
                asm.emit(")");
            }
        }

        public void emit(OCLCompilationResultBuilder crb) {
            emit(crb, null);
        }
    }

    public static class OCLUnaryTemplate extends OCLUnaryOp {
        // @formatter:off

        public static final OCLUnaryTemplate MEM_CHECK = new OCLUnaryTemplate("mem check", "MEM_CHECK(%s)");
        public static final OCLUnaryTemplate INDIRECTION = new OCLUnaryTemplate("deref", "*(%s)");
        public static final OCLUnaryTemplate CAST_TO_POINTER = new OCLUnaryTemplate("cast ptr", "(%s *)");
        public static final OCLUnaryTemplate LOAD_ADDRESS_ABS = new OCLUnaryTemplate("load address", "*(%s)");
        public static final OCLUnaryTemplate ADDRESS_OF = new OCLUnaryTemplate("address of", "&(%s)");

        public static final OCLUnaryTemplate NEW_INT_ARRAY = new OCLUnaryTemplate("int[]", "int[%s]");
        public static final OCLUnaryTemplate NEW_LONG_ARRAY = new OCLUnaryTemplate("long[]", "long[%s]");
        public static final OCLUnaryTemplate NEW_FLOAT_ARRAY = new OCLUnaryTemplate("float[]", "float[%s]");
        public static final OCLUnaryTemplate NEW_DOUBLE_ARRAY = new OCLUnaryTemplate("double[]", "double[%s]");
        public static final OCLUnaryTemplate NEW_BYTE_ARRAY = new OCLUnaryTemplate("char[]", "char[%s]");
        public static final OCLUnaryTemplate NEW_CHAR_ARRAY = new OCLUnaryTemplate("char[]", "char[%s]");
        public static final OCLUnaryTemplate NEW_SHORT_ARRAY = new OCLUnaryTemplate("short[]", "short[%s]");

        // @formatter:on
        private final String template;

        protected OCLUnaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value value) {
            final OCLAssembler asm = crb.getAssembler();
            asm.emit(template, asm.toString(value));
        }

        public String getTemplate() {
            return template;
        }

    }

    /**
     * Binary opcodes
     */
    public static class OCLBinaryOp extends OCLOp {
        // @formatter:off

        public static final OCLBinaryOp ADD = new OCLBinaryOp("+");
        public static final OCLBinaryOp SUB = new OCLBinaryOp("-");
        public static final OCLBinaryOp MUL = new OCLBinaryOp("*");
        public static final OCLBinaryOp DIV = new OCLBinaryOp("/");
        public static final OCLBinaryOp MOD = new OCLBinaryOp("%");

        public static final OCLBinaryOp BITWISE_AND = new OCLBinaryOp("&");
        public static final OCLBinaryOp BITWISE_OR = new OCLBinaryOp("|");
        public static final OCLBinaryOp BITWISE_XOR = new OCLBinaryOp("^");
        public static final OCLBinaryOp BITWISE_LEFT_SHIFT = new OCLBinaryOp("<<");
        public static final OCLBinaryOp BITWISE_RIGHT_SHIFT = new OCLBinaryOp(">>");

        public static final OCLBinaryOp LOGICAL_AND = new OCLBinaryOp("&&");
        public static final OCLBinaryOp LOGICAL_OR = new OCLBinaryOp("||");

        public static final OCLBinaryOp ASSIGN = new OCLBinaryOp("=");

        public static final OCLBinaryOp VECTOR_SELECT = new OCLBinaryOp(".");

        public static final OCLBinaryOp RELATIONAL_EQ = new OCLBinaryOp("==");
        public static final OCLBinaryOp RELATIONAL_NE = new OCLBinaryOp("!=");
        public static final OCLBinaryOp RELATIONAL_GT = new OCLBinaryOp(">");
        public static final OCLBinaryOp RELATIONAL_LT = new OCLBinaryOp("<");
        public static final OCLBinaryOp RELATIONAL_GTE = new OCLBinaryOp(">=");
        public static final OCLBinaryOp RELATIONAL_LTE = new OCLBinaryOp("<=");
        // @formatter:on

        protected OCLBinaryOp(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value x, Value y) {
            final OCLAssembler asm = crb.getAssembler();
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
    public static class OCLBinaryIntrinsic extends OCLBinaryOp {
        // @formatter:off

        public static final OCLBinaryIntrinsic INT_MIN = new OCLBinaryIntrinsic("min");
        public static final OCLBinaryIntrinsic INT_MAX = new OCLBinaryIntrinsic("max");

        public static final OCLBinaryIntrinsic ATAN2 = new OCLBinaryIntrinsic("atan2");

        public static final OCLBinaryIntrinsic FLOAT_MIN = new OCLBinaryIntrinsic("fmin");
        public static final OCLBinaryIntrinsic FLOAT_MAX = new OCLBinaryIntrinsic("fmax");
        public static final OCLBinaryIntrinsic FLOAT_POW = new OCLBinaryIntrinsic("pow");

        public static final OCLBinaryIntrinsic ATOMIC_ADD = new OCLBinaryIntrinsic("atomic_add");
        public static final OCLBinaryIntrinsic ATOMIC_SUB = new OCLBinaryIntrinsic("atomic_sub");
        public static final OCLBinaryIntrinsic ATOMIC_XCHG = new OCLBinaryIntrinsic("atomic_xchg");
        public static final OCLBinaryIntrinsic ATOMIC_MIN = new OCLBinaryIntrinsic("atomic_min");
        public static final OCLBinaryIntrinsic ATOMIC_MAX = new OCLBinaryIntrinsic("atomic_max");
        public static final OCLBinaryIntrinsic ATOMIC_AND = new OCLBinaryIntrinsic("atomic_and");
        public static final OCLBinaryIntrinsic ATOMIC_OR = new OCLBinaryIntrinsic("atomic_or");
        public static final OCLBinaryIntrinsic ATOMIC_XOR = new OCLBinaryIntrinsic("atomic_xor");

        public static final OCLBinaryIntrinsic VLOAD2 = new OCLBinaryIntrinsic("vload2");
        public static final OCLBinaryIntrinsic VLOAD3 = new OCLBinaryIntrinsic("vload3");
        public static final OCLBinaryIntrinsic VLOAD4 = new OCLBinaryIntrinsic("vload4");
        public static final OCLBinaryIntrinsic VLOAD8 = new OCLBinaryIntrinsic("vload8");
        public static final OCLBinaryIntrinsic VLOAD16 = new OCLBinaryIntrinsic("vload16");

        public static final OCLBinaryIntrinsic DOT = new OCLBinaryIntrinsic("dot");
        public static final OCLBinaryIntrinsic CROSS = new OCLBinaryIntrinsic("cross");
        // @formatter:on

        protected OCLBinaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value x, Value y) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValueOrOp(crb, x);
            asm.emit(", ");
            asm.emitValueOrOp(crb, y);
            asm.emit(")");
        }
    }

    public static class OCLBinaryIntrinsicCmp extends OCLBinaryOp {

        // @formatter:off
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_EQUAL = new OCLBinaryIntrinsicCmp("isequal");
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_NOT_EQUAL = new OCLBinaryIntrinsicCmp("isnotequal");
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_GREATER = new OCLBinaryIntrinsicCmp("isgreater");
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_GREATEREQUAL = new OCLBinaryIntrinsicCmp("isgreaterequal");
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_LESS = new OCLBinaryIntrinsicCmp("isless");
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_LESSEQUAL = new OCLBinaryIntrinsicCmp("islessequal");
        public static final OCLBinaryIntrinsicCmp FLOAT_IS_LESSGREATER = new OCLBinaryIntrinsicCmp("islessgreater");
        // @formatter:on

        protected OCLBinaryIntrinsicCmp(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value x, Value y) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValueOrOp(crb, x);
            asm.emit(", ");
            asm.emitValueOrOp(crb, y);
            asm.emit(")");
        }
    }

    public static class OCLBinaryTemplate extends OCLBinaryOp {
        // @formatter:off

        public static final OCLBinaryTemplate DECLARE_BYTE_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "byte %s[%s]");
        public static final OCLBinaryTemplate DECLARE_CHAR_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "char %s[%s]");
        public static final OCLBinaryTemplate DECLARE_SHORT_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "short %s[%s]");
        public static final OCLBinaryTemplate DECLARE_INT_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "int %s[%s]");
        public static final OCLBinaryTemplate DECLARE_LONG_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "long %s[%s]");
        public static final OCLBinaryTemplate DECLARE_FLOAT_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "float %s[%s]");
        public static final OCLBinaryTemplate DECLARE_DOUBLE_ARRAY = new OCLBinaryTemplate("DECLARE_ARRAY", "double %s[%s]");
        public static final OCLBinaryTemplate ARRAY_INDEX = new OCLBinaryTemplate("index", "%s[%s]");

        public static final OCLBinaryTemplate NEW_PRIVATE_CHAR_ARRAY = new OCLBinaryTemplate("new private array char", "__private char %s[%s]");
        public static final OCLBinaryTemplate NEW_PRIVATE_FLOAT_ARRAY = new OCLBinaryTemplate("new private array float", "__private float %s[%s]");
        public static final OCLBinaryTemplate NEW_PRIVATE_INT_ARRAY = new OCLBinaryTemplate("new private array int", "__private int %s[%s]");
        public static final OCLBinaryTemplate NEW_PRIVATE_DOUBLE_ARRAY = new OCLBinaryTemplate("new private array double", "__private double %s[%s]");
        public static final OCLBinaryTemplate NEW_PRIVATE_LONG_ARRAY = new OCLBinaryTemplate("new private array long", "__private long %s[%s]");
        public static final OCLBinaryTemplate NEW_PRIVATE_SHORT_ARRAY = new OCLBinaryTemplate("new private array short", "__private short %s[%s]");
        public static final OCLBinaryTemplate NEW_PRIVATE_BYTE_ARRAY = new OCLBinaryTemplate("new private array byte", "__private byte %s[%s]");

        public static final OCLBinaryTemplate PRIVATE_INT_ARRAY_PTR = new OCLBinaryTemplate("private pointer array int", "__private int* %s = %s");
        public static final OCLBinaryTemplate PRIVATE_CHAR_ARRAY_PTR = new OCLBinaryTemplate("private pointer array char", "__private char* %s = %s");
        public static final OCLBinaryTemplate PRIVATE_FLOAT_ARRAY_PTR = new OCLBinaryTemplate("private pointer array float", "__private float* %s = %s");
        public static final OCLBinaryTemplate PRIVATE_DOUBLE_ARRAY_PTR = new OCLBinaryTemplate("private pointer array double", "__private double* %s = %s");
        public static final OCLBinaryTemplate PRIVATE_LONG_ARRAY_PTR = new OCLBinaryTemplate("private pointer array long", "__private long* %s = %s");
        public static final OCLBinaryTemplate PRIVATE_SHORT_ARRAY_PTR = new OCLBinaryTemplate("private pointer array short", "__private short* %s = %s");
        public static final OCLBinaryTemplate PRIVATE_BYTE_ARRAY_PTR = new OCLBinaryTemplate("private pointer array byte", "__private byte* %s = %s");

        public static final OCLBinaryTemplate PRIVATE_INT_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array int", "__private int* %s = ((__private int *) %s)");
        public static final OCLBinaryTemplate PRIVATE_CHAR_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array char", "__private char* %s = ((__private char *) %s)");
        public static final OCLBinaryTemplate PRIVATE_FLOAT_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array float", "__private float* %s = ((__private float *) %s)");
        public static final OCLBinaryTemplate PRIVATE_DOUBLE_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array double", "__private double* %s = ((__private double *) %s)");
        public static final OCLBinaryTemplate PRIVATE_LONG_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array long", "__private long* %s = ((__private long *) %s)");
        public static final OCLBinaryTemplate PRIVATE_SHORT_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array short", "__private short* %s = ((__private short *) %s)");
        public static final OCLBinaryTemplate PRIVATE_BYTE_ARRAY_PTR_COPY = new OCLBinaryTemplate("private pointer copy array byte", "__private byte* %s = ((__private byte *) %s)");

        public static final OCLBinaryTemplate NEW_LOCAL_FLOAT_ARRAY = new OCLBinaryTemplate("local memory array float", "__local float %s[%s]");
        public static final OCLBinaryTemplate NEW_LOCAL_INT_ARRAY = new OCLBinaryTemplate("local memory array int", "__local int %s[%s]");
        public static final OCLBinaryTemplate NEW_LOCAL_DOUBLE_ARRAY = new OCLBinaryTemplate("local memory array double", "__local double %s[%s]");
        public static final OCLBinaryTemplate NEW_LOCAL_LONG_ARRAY = new OCLBinaryTemplate("local memory array long", "__local long %s[%s]");
        public static final OCLBinaryTemplate NEW_LOCAL_SHORT_ARRAY = new OCLBinaryTemplate("local memory array short", "__local short %s[%s]");
        public static final OCLBinaryTemplate NEW_LOCAL_CHAR_ARRAY = new OCLBinaryTemplate("local memory array char", "__local char %s[%s]");
        public static final OCLBinaryTemplate NEW_LOCAL_BYTE_ARRAY = new OCLBinaryTemplate("local memory array byte", "__local byte %s[%s]");
        // @formatter:on
        private final String template;

        protected OCLBinaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value x, Value y) {
            final OCLAssembler asm = crb.getAssembler();
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
    public static class OCLTernaryOp extends OCLOp {
        // @formatter:off

        // @formatter:on
        protected OCLTernaryOp(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value x, Value y, Value z) {
            final OCLAssembler asm = crb.getAssembler();
            asm.emitLine("// unimplemented ternary op:");
        }
    }

    /**
     * Ternary intrinsic
     */
    public static class OCLTernaryIntrinsic extends OCLTernaryOp {
        // @formatter:off

        public static final OCLTernaryIntrinsic VSTORE2 = new OCLTernaryIntrinsic("vstore2");
        public static final OCLTernaryIntrinsic VSTORE3 = new OCLTernaryIntrinsic("vstore3");
        public static final OCLTernaryIntrinsic VSTORE4 = new OCLTernaryIntrinsic("vstore4");
        public static final OCLTernaryIntrinsic VSTORE8 = new OCLTernaryIntrinsic("vstore8");
        public static final OCLTernaryIntrinsic VSTORE16 = new OCLTernaryIntrinsic("vstore16");
        public static final OCLTernaryIntrinsic CLAMP = new OCLTernaryIntrinsic("clamp");
        public static final OCLTernaryIntrinsic FMA = new OCLTernaryIntrinsic("fma");
        // @formatter:on

        protected OCLTernaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value x, Value y, Value z) {
            final OCLAssembler asm = crb.getAssembler();
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

    public static class OCLTernaryTemplate extends OCLTernaryOp {
        // @formatter:off

        public static final OCLTernaryTemplate SELECT = new OCLTernaryTemplate("select", "(%s) ? %s : %s");

        // @formatter:on
        private final String template;

        protected OCLTernaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value x, Value y, Value z) {
            final OCLAssembler asm = crb.getAssembler();
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

    public static class OCLOp2 extends OCLOp {

        // @formatter:off
        public static final OCLOp2 VMOV_SHORT2 = new OCLOp2("(short2)");
        public static final OCLOp2 VMOV_INT2 = new OCLOp2("(int2)");
        public static final OCLOp2 VMOV_FLOAT2 = new OCLOp2("(float2)");
        public static final OCLOp2 VMOV_BYTE2 = new OCLOp2("(char2)");
        public static final OCLOp2 VMOV_DOUBLE2 = new OCLOp2("(double2)");

        public static final OCLOp2 VMOV_HALF2 = new OCLOp2("(half2)");
        // @formatter:on

        protected OCLOp2(String opcode) {
            super(opcode);
        }

        // FIXME: Remove these emits from vector operations. They are not reachable
        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(")");
        }
    }

    public static class OCLOp3 extends OCLOp2 {
        // @formatter:off

        public static final OCLOp3 VMOV_SHORT3 = new OCLOp3("(short3)");
        public static final OCLOp3 VMOV_INT3 = new OCLOp3("(int3)");
        public static final OCLOp3 VMOV_FLOAT3 = new OCLOp3("(float3)");
        public static final OCLOp3 VMOV_BYTE3 = new OCLOp3("(char3)");
        public static final OCLOp3 VMOV_DOUBLE3 = new OCLOp3("(double3)");

        public static final OCLOp3 VMOV_HALF3 = new OCLOp3("(half3)");

        // @formatter:on
        public OCLOp3(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2) {
            final OCLAssembler asm = crb.getAssembler();
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

    public static class OCLOp4 extends OCLOp3 {
        // @formatter:off

        public static final OCLOp4 VMOV_SHORT4 = new OCLOp4("(short4)");
        public static final OCLOp4 VMOV_INT4 = new OCLOp4("(int4)");
        public static final OCLOp4 VMOV_FLOAT4 = new OCLOp4("(float4)");
        public static final OCLOp4 VMOV_BYTE4 = new OCLOp4("(char4)");
        public static final OCLOp4 VMOV_DOUBLE4 = new OCLOp4("(double4)");

        public static final OCLOp4 VMOV_HALF4 = new OCLOp4("(half4)");
        // @formatter:on

        protected OCLOp4(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3) {
            final OCLAssembler asm = crb.getAssembler();
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

    public static class OCLOp8 extends OCLOp4 {
        // @formatter:off

        public static final OCLOp8 VMOV_SHORT8 = new OCLOp8("(short8)");
        public static final OCLOp8 VMOV_INT8 = new OCLOp8("(int8)");
        public static final OCLOp8 VMOV_FLOAT8 = new OCLOp8("(float8)");
        public static final OCLOp8 VMOV_BYTE8 = new OCLOp8("(char8)");
        public static final OCLOp8 VMOV_DOUBLE8 = new OCLOp8("(double8)");

        public static final OCLOp8 VMOV_HALF8 = new OCLOp8("(half8)");

        // @formatter:on

        protected OCLOp8(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(", ");
            asm.emitValue(crb, s3);
            asm.emit(", ");
            asm.emitValue(crb, s4);
            asm.emit(", ");
            asm.emitValue(crb, s5);
            asm.emit(", ");
            asm.emitValue(crb, s6);
            asm.emit(", ");
            asm.emitValue(crb, s7);
            asm.emit(")");
        }
    }

    public static class OCLOp16 extends OCLOp8 {
        // @formatter:off
        public static final OCLOp16 VMOV_SHORT16 = new OCLOp16("(short16)");
        public static final OCLOp16 VMOV_INT16 = new OCLOp16("(int16)");
        public static final OCLOp16 VMOV_FLOAT16 = new OCLOp16("(float16)");
        public static final OCLOp16 VMOV_BYTE16 = new OCLOp16("(char16)");
        public static final OCLOp16 VMOV_DOUBLE16 = new OCLOp16("(double16)");
        public static final OCLOp16 VMOV_HALF16 = new OCLOp16("(half16)");
        // @formatter:on
        protected OCLOp16(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7, Value s8, Value s9, Value s10, Value s11, Value s12,
                Value s13, Value s14, Value s15) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(", ");
            asm.emitValue(crb, s2);
            asm.emit(", ");
            asm.emitValue(crb, s3);
            asm.emit(", ");
            asm.emitValue(crb, s4);
            asm.emit(", ");
            asm.emitValue(crb, s5);
            asm.emit(", ");
            asm.emitValue(crb, s6);
            asm.emit(", ");
            asm.emitValue(crb, s7);
            asm.emit(", ");
            asm.emitValue(crb, s8);
            asm.emit(", ");
            asm.emitValue(crb, s9);
            asm.emit(", ");
            asm.emitValue(crb, s10);
            asm.emit(", ");
            asm.emitValue(crb, s11);
            asm.emit(", ");
            asm.emitValue(crb, s12);
            asm.emit(", ");
            asm.emitValue(crb, s13);
            asm.emit(", ");
            asm.emitValue(crb, s14);
            asm.emit(", ");
            asm.emitValue(crb, s15);
            asm.emit(")");
        }
    }
}
