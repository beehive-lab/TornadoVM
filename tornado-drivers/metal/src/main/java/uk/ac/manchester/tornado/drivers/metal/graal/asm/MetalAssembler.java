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
package uk.ac.manchester.tornado.drivers.metal.graal.asm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind.FLOAT;
import static uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind.LONG;
import static uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind.ULONG;

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
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDescription;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIROp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalNullary;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalReturnSlot;

public final class MetalAssembler extends Assembler {
    /**
     * Emits a Metal MSL kernel signature with explicit arguments for grid/threadgroup sizes.
     * Example:
     * kernel void my_kernel(
     *     device float* bufferA [[buffer(0)]],
     *     device float* bufferB [[buffer(1)]],
     *     constant int& global_size [[buffer(N)]],
     *     constant int& local_size [[buffer(N+1)]],
     *     constant int& group_size [[buffer(N+2)]],
     *     ... other scalars ...
     * )
     * Metal expects these sizes as explicit kernel arguments, passed from host.
     */
    public void emitKernelSignature(String kernelName, List<String> argList) {
        emitLine("kernel void %s(", kernelName);
        pushIndent();
        for (int i = 0; i < argList.size(); i++) {
            String arg = argList.get(i);
            String comma = (i < argList.size() - 1) ? "," : "";
            emitLine("%s%s", arg, comma);
        }
        popIndent();
        emitLine(")");
        emitLine("{");
        pushIndent();
        emitLine("// Metal MSL kernel entry. Built-ins:");
        emitLine("//   _thread_position_in_grid.x/y/z: global thread indices");
        emitLine("//   _global_sizes: pointer to global size array");
        popIndent();
        emitLine("}");
    }

    /**
     * Helper to generate Metal MSL kernel argument strings for buffers and sizes.
     * Usage: argList.addAll(emitKernelArguments(...));
     */
    public List<String> emitKernelArguments(List<String> bufferArgs, List<String> scalarArgs, int bufferStartIndex) {
        List<String> args = new ArrayList<>();
        int idx = bufferStartIndex;
        for (String buf : bufferArgs) {
            args.add(String.format("device %s [[buffer(%d)]]", buf, idx++));
        }
        for (String scalar : scalarArgs) {
            args.add(String.format("constant %s [[buffer(%d)]]", scalar, idx++));
        }
        // Metal MSL convention: use uint3 for thread position, and uint* for global sizes
        args.add("uint3 _thread_position_in_grid [[thread_position_in_grid]]");
        args.add(String.format("device uint* _global_sizes [[buffer(%d)]]", idx++));
        // Optionally add local/group sizes as needed
        // args.add(String.format("constant int& local_size [[buffer(%d)]]", idx++));
        // args.add(String.format("constant int& group_size [[buffer(%d)]]", idx++));
        return args;
    }

    private static final boolean EMIT_INTRINSICS = false;
    private int indent;
    private int lastIndent;
    private String delimiter;
    private boolean emitEOL;
    private List<String> operandStack;
    private boolean pushToStack;

    public MetalAssembler(TargetDescription target) {
        super(target, null);
        indent = 0;
        delimiter = MetalAssemblerConstants.STMT_DELIMITER;
        emitEOL = true;
        operandStack = new ArrayList<>(10);
        pushToStack = false;

        // Metal MSL does not use OpenCL-style #pragma extensions
        // Only Metal address space qualifiers are used: device, threadgroup, thread, constant
        // Remove any OpenCL-specific pragmas or qualifiers
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
        MetalVariablePrefix typePrefix = Arrays.stream(MetalVariablePrefix.values()).filter(tp -> tp.getType().equals(type)).findFirst().orElse(null);

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
    emitLine("inline void atomicAdd_Tornado_Floats(volatile " + MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " float *source, const float operand) {\n" +
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
                "   } while (atomic_cmpxchg((volatile " + MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " unsigned int *)source, prevVal.intVal,\n" +
                "   newVal.intVal) != prevVal.intVal);" +
                "}");

    emitLine("inline void atomicAdd_Tornado_Floats2(volatile " + MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " float *addr, float val)\n" +
                "{\n" +
                "    union {\n" +
                "        unsigned int u32;\n" +
                "        float f32;\n" +
                "    } next, expected, current;\n" +
                "    current.f32 = *addr;\n" +
                "    barrier(CLK_GLOBAL_MEM_FENCE);\n" +
                "    do {\n" +
                "       expected.f32 = current.f32;\n" +
                "       next.f32 = expected.f32 + val;\n" +
                "       current.u32 = atomic_cmpxchg( (volatile " + MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " unsigned int *)addr,\n" +
                "       expected.u32, next.u32);\n" +
                "    } while( current.u32 != expected.u32 );\n" +
                "}");

    emitLine("inline void atomicMul_Tornado_Int(volatile " + MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " int *source, const float operand) {\n" +
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
            "   } while (atomic_cmpxchg((volatile " + MetalAssemblerConstants.GLOBAL_MEM_MODIFIER + " unsigned int *)source, prevVal.intVal,\n" +
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
            emitSymbol(MetalAssemblerConstants.TAB);
        }
    }

    public void comment(String comment) {
        emit(" /* " + comment + " */ ");
        eol();
    }

    public void loopBreak() {
        emit(MetalAssemblerConstants.BREAK);
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
            emitSymbol(MetalAssemblerConstants.EOL);
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
        emitLine(MetalAssemblerConstants.CURLY_BRACKET_CLOSE + "  // " + blockName);
    }

    public void endScope() {
        popIndent();
        emitLine(MetalAssemblerConstants.CURLY_BRACKET_CLOSE);
    }

    public void beginScope() {
        emitLine(MetalAssemblerConstants.CURLY_BRACKET_OPEN);
        pushIndent();
    }

    private String encodeString(String str) {
        return str.replace("\n", "\\n").replace("\t", "\\t").replace("\"", "");
    }

    /**
     * Emit pointer arithmetic in Metal style. Example output:
     * (device float*)((device char*)base + offset)
     */
    public void emitPointerAdd(String targetType, String baseExpr, String offsetExpr) {
        // (device <targetType>*)((device char*)<baseExpr> + <offsetExpr>)
        emit(String.format("(device %s*)((device char*)%s + %s)", targetType, baseExpr, offsetExpr));
    }

    private String addLiteralSuffix(MetalKind oclKind, String value) {
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
        MetalKind oclKind = (MetalKind) cv.getPlatformKind();
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
        } else if (value instanceof MetalNullary.Parameter) {
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

    public void emitValue(MetalCompilationResultBuilder crb, Value value) {
        if (value instanceof MetalReturnSlot) {
            ((MetalReturnSlot) value).emit(crb, this);
        } else {
            emit(toString(value));
        }
    }

    public void emitValueWithFormat(MetalCompilationResultBuilder crb, Value value) {
        if (value instanceof MetalReturnSlot) {
            ((MetalReturnSlot) value).emit(crb, this);
        } else {
            emit(MetalAssembler.convertValueFromGraalFormat(value));
        }
    }

    public String getStringValue(MetalCompilationResultBuilder crb, Value value) {
        if (value instanceof MetalReturnSlot) {
            return ((MetalReturnSlot) value).getStringFormat();
        } else {
            return toString(value);
        }
    }

    public void assign() {
        emitSymbol(MetalAssemblerConstants.ASSIGN);
    }

    public void ifStmt(MetalCompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(MetalAssemblerConstants.IF_STMT);
        emitSymbol(MetalAssemblerConstants.OPEN_PARENTHESIS);

        emit(toString(condition));
        if (((MetalKind) condition.getPlatformKind()) == MetalKind.INT) {
            emit(" == 1");
        }

        emitSymbol(MetalAssemblerConstants.CLOSE_PARENTHESIS);
        eol();

    }

    public void space() {
        emitSymbol(" ");
    }

    public void elseIfStmt(MetalCompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(MetalAssemblerConstants.ELSE);
        space();
        emitSymbol(MetalAssemblerConstants.IF_STMT);
        emitSymbol(MetalAssemblerConstants.OPEN_PARENTHESIS);

        emitValue(crb, condition);

        emitSymbol(MetalAssemblerConstants.CLOSE_PARENTHESIS);
        eol();

    }

    public void elseStmt() {
        emitSymbol(MetalAssemblerConstants.ELSE);
    }

    public void emitValueOrOp(MetalCompilationResultBuilder crb, Value value) {
        if (value instanceof MetalLIROp) {
            ((MetalLIROp) value).emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }

    /**
     * Base class for Metal opcodes.
     */
    public static class MetalOp {

        protected final String opcode;

        protected MetalOp(String opcode) {
            this.opcode = opcode;
        }

        protected final void emitOpcode(MetalAssembler asm) {
            asm.emit(opcode);
        }

        public boolean equals(MetalOp other) {
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
    public static class MetalNullaryOp extends MetalOp {

        // @formatter:off
        public static final MetalNullaryOp RETURN = new MetalNullaryOp("return");
        // @formatter:on

        protected MetalNullaryOp(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb) {
            final MetalAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class MetalNullaryIntrinsic extends MetalNullaryOp {
        // @formatter:off

        // @formatter:on
        protected MetalNullaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb) {
            final MetalAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class MetalNullaryTemplate extends MetalNullaryOp {

        public MetalNullaryTemplate(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb) {
            final MetalAssembler asm = crb.getAssembler();
            asm.emit(opcode);
        }
    }

    /**
     * Unary opcodes
     */
    public static class MetalUnaryOp extends MetalOp {
        // @formatter:off

        public static final MetalUnaryOp RETURN = new MetalUnaryOp("return ", true);

        public static final MetalUnaryOp INC = new MetalUnaryOp("++", false);
        public static final MetalUnaryOp DEC = new MetalUnaryOp("--", false);
        public static final MetalUnaryOp NEGATE = new MetalUnaryOp("-", true);

        public static final MetalUnaryOp LOGICAL_NOT = new MetalUnaryOp("!", true);

        public static final MetalUnaryOp BITWISE_NOT = new MetalUnaryOp("~", true);

        public static final MetalUnaryOp CAST_TO_INT = new MetalUnaryOp("(int) ", true);
        public static final MetalUnaryOp CAST_TO_SHORT = new MetalUnaryOp("(short) ", true);
    public static final MetalUnaryOp CAST_TO_LONG = new MetalUnaryOp("(long) ", true);
    // Cast pointer to ulong so pointer arithmetic can be done as integer math.
    // The result is later cast back to a device pointer for dereference.
    public static final MetalUnaryOp CAST_TO_ULONG = new MetalUnaryOp("(ulong)(device char*) ", true);
        public static final MetalUnaryOp CAST_TO_FLOAT = new MetalUnaryOp("(float) ", true);
        public static final MetalUnaryOp CAST_TO_BYTE = new MetalUnaryOp("(char) ", true);
        public static final MetalUnaryOp CAST_TO_DOUBLE = new MetalUnaryOp("(double) ", true);

        public static final MetalUnaryOp CAST_TO_INT_PTR = new MetalUnaryOp("(int *) ", true);
        public static final MetalUnaryOp CAST_TO_SHORT_PTR = new MetalUnaryOp("(short *) ", true);
        public static final MetalUnaryOp CAST_TO_LONG_PTR = new MetalUnaryOp("(long *) ", true);
    public static final MetalUnaryOp CAST_TO_ULONG_PTR = new MetalUnaryOp("(device char*) ", true);
        public static final MetalUnaryOp CAST_TO_FLOAT_PTR = new MetalUnaryOp("(float *) ", true);
        public static final MetalUnaryOp CAST_TO_BYTE_PTR = new MetalUnaryOp("(char *) ", true);
        // @formatter:on

        private final boolean prefix;

        protected MetalUnaryOp(String opcode) {
            this(opcode, false);
        }

        protected MetalUnaryOp(String opcode, boolean prefix) {
            super(opcode);
            this.prefix = prefix;
        }

        public void emit(MetalCompilationResultBuilder crb, Value x) {
            final MetalAssembler asm = crb.getAssembler();
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
    public static class MetalUnaryIntrinsic extends MetalUnaryOp {
        // @formatter:off

    public static final MetalUnaryIntrinsic GLOBAL_ID = new MetalUnaryIntrinsic("get_global_id");
    public static final MetalUnaryIntrinsic GLOBAL_SIZE = new MetalUnaryIntrinsic("get_global_size");

        public static final MetalUnaryIntrinsic Metal_KERNEL_CONTEXT_ACCESS = new MetalUnaryIntrinsic("_kernel_context");

        public static final MetalUnaryIntrinsic LOCAL_ID = new MetalUnaryIntrinsic("get_local_id");
        public static final MetalUnaryIntrinsic LOCAL_SIZE = new MetalUnaryIntrinsic("get_local_size");

        public static final MetalUnaryIntrinsic GROUP_ID = new MetalUnaryIntrinsic("get_group_id");
        public static final MetalUnaryIntrinsic GROUP_SIZE = new MetalUnaryIntrinsic("get_group_size");

        public static final MetalUnaryIntrinsic ATOMIC_INC = new MetalUnaryIntrinsic("atomic_inc");
        public static final MetalUnaryIntrinsic ATOMIC_FETCH_ADD_EXPLICIT = new MetalUnaryIntrinsic("atomic_fetch_add_explicit");
        public static final MetalUnaryIntrinsic ATOMIC_FETCH_SUB_EXPLICIT = new MetalUnaryIntrinsic("atomic_fetch_sub_explicit");
        public static final MetalUnaryIntrinsic ATOM_ADD = new MetalUnaryIntrinsic("atom_add");
        public static final MetalUnaryIntrinsic ATOMIC_ADD = new MetalUnaryIntrinsic("atomic_add");
        public static final MetalUnaryIntrinsic ATOMIC_VAR_INIT = new MetalUnaryIntrinsic("ATOMIC_VAR_INIT");
        public static final MetalUnaryIntrinsic ATOMIC_DEC = new MetalUnaryIntrinsic("atomic_dec");
        public static final MetalUnaryIntrinsic ATOMIC_GET = new MetalUnaryIntrinsic("atomic[0]");

        public static final MetalUnaryIntrinsic MEMORY_ORDER_RELAXED = new MetalUnaryIntrinsic("memory_order_relaxed");

        public static final MetalUnaryIntrinsic BARRIER = new MetalUnaryIntrinsic("barrier");
        public static final MetalUnaryIntrinsic MEM_FENCE = new MetalUnaryIntrinsic("mem_fence");
        public static final MetalUnaryIntrinsic READ_MEM_FENCE = new MetalUnaryIntrinsic("read_mem_fence");
        public static final MetalUnaryIntrinsic WRITE_MEM_FENCE = new MetalUnaryIntrinsic("write_mem_fence");

        public static final MetalUnaryIntrinsic ABS = new MetalUnaryIntrinsic("abs");

        public static final MetalUnaryIntrinsic CEIL = new MetalUnaryIntrinsic("ceil");
        public static final MetalUnaryIntrinsic EXP = new MetalUnaryIntrinsic("exp");
        public static final MetalUnaryIntrinsic SQRT = new MetalUnaryIntrinsic("sqrt");
        public static final MetalUnaryIntrinsic LOG = new MetalUnaryIntrinsic("log");
        public static final MetalUnaryIntrinsic RADIANS = new MetalUnaryIntrinsic("radians");
        public static final MetalUnaryIntrinsic RSQRT = new MetalUnaryIntrinsic("rsqrt");
        public static final MetalUnaryIntrinsic NATIVE_COS = new MetalUnaryIntrinsic("native_cos");
        public static final MetalUnaryIntrinsic NATIVE_SIN = new MetalUnaryIntrinsic("native_sin");
        public static final MetalUnaryIntrinsic NATIVE_SQRT = new MetalUnaryIntrinsic("native_sqrt");
        public static final MetalUnaryIntrinsic NATIVE_TAN = new MetalUnaryIntrinsic("native_tan");
        public static final MetalUnaryIntrinsic SIN = new MetalUnaryIntrinsic("sin");
        public static final MetalUnaryIntrinsic COS = new MetalUnaryIntrinsic("cos");
        public static final MetalUnaryIntrinsic TAN = new MetalUnaryIntrinsic("tan");
        public static final MetalUnaryIntrinsic TANH = new MetalUnaryIntrinsic("tanh");
        public static final MetalUnaryIntrinsic ATAN = new MetalUnaryIntrinsic("atan");
        public static final MetalUnaryIntrinsic ASIN = new MetalUnaryIntrinsic("asin");
        public static final MetalUnaryIntrinsic ASINH = new MetalUnaryIntrinsic("asinh");
        public static final MetalUnaryIntrinsic ACOS = new MetalUnaryIntrinsic("acos");
        public static final MetalUnaryIntrinsic ACOSH = new MetalUnaryIntrinsic("acosh");
        public static final MetalUnaryIntrinsic SINPI = new MetalUnaryIntrinsic("sinpi");
        public static final MetalUnaryIntrinsic COSPI = new MetalUnaryIntrinsic("cospi");

        public static final MetalUnaryIntrinsic SIGN = new MetalUnaryIntrinsic("sign");

        public static final MetalUnaryIntrinsic LOCAL_MEMORY = new MetalUnaryIntrinsic(MetalAssemblerConstants.LOCAL_MEM_MODIFIER);

        public static final MetalUnaryIntrinsic POPCOUNT = new MetalUnaryIntrinsic("popcount");

        public static final MetalUnaryIntrinsic FLOAT_ABS = new MetalUnaryIntrinsic("fabs");
        public static final MetalUnaryIntrinsic FLOAT_TRUNC = new MetalUnaryIntrinsic("trunc");
        public static final MetalUnaryIntrinsic FLOAT_FLOOR = new MetalUnaryIntrinsic("floor");

        public static final MetalUnaryIntrinsic SIGN_BIT = new MetalUnaryIntrinsic("signbit");

        public static final MetalUnaryIntrinsic ANY = new MetalUnaryIntrinsic("any");
        public static final MetalUnaryIntrinsic ALL = new MetalUnaryIntrinsic("all");

        public static final MetalUnaryIntrinsic AS_FLOAT = new MetalUnaryIntrinsic("as_float");
        public static final MetalUnaryIntrinsic AS_INT = new MetalUnaryIntrinsic("as_int");

        public static final MetalUnaryIntrinsic IS_FINITE = new MetalUnaryIntrinsic("isfinite");
        public static final MetalUnaryIntrinsic IS_INF = new MetalUnaryIntrinsic("isinf");
        public static final MetalUnaryIntrinsic IS_NAN = new MetalUnaryIntrinsic("isnan");
        public static final MetalUnaryIntrinsic IS_NORMAL = new MetalUnaryIntrinsic("isnormal");
        // @formatter:on

        protected MetalUnaryIntrinsic(String opcode) {
            super(opcode, true);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value x) {
            final MetalAssembler asm = crb.getAssembler();
            // Map OpenCL builtins to Metal MSL equivalents
            if (opcode.equals("get_global_id")) {
                // Metal: use _thread_position_in_grid.x/y/z
                String idx = (x == null) ? "0" : asm.toString(x);
                switch (idx.trim()) {
                    case "0":
                        asm.emit("_thread_position_in_grid.x");
                        return;
                    case "1":
                        asm.emit("_thread_position_in_grid.y");
                        return;
                    case "2":
                        asm.emit("_thread_position_in_grid.z");
                        return;
                    default:
                        asm.emit("_thread_position_in_grid[");
                        asm.emit(asm.toString(x));
                        asm.emit("]");
                        return;
                }
            }

            if (opcode.equals("get_global_size")) {
                // Metal: use _global_sizes array passed as kernel argument
                if (x == null) {
                    asm.emit("_global_sizes[0]");
                } else {
                    asm.emit("_global_sizes[");
                    asm.emit(asm.toString(x));
                    asm.emit("]");
                }
                return;
            }

            if (opcode.equals("get_local_id")) {
                // Metal: use _thread_position_in_threadgroup.x/y/z
                String idx = (x == null) ? "0" : asm.toString(x);
                switch (idx.trim()) {
                    case "0":
                        asm.emit("_thread_position_in_threadgroup.x");
                        return;
                    case "1":
                        asm.emit("_thread_position_in_threadgroup.y");
                        return;
                    case "2":
                        asm.emit("_thread_position_in_threadgroup.z");
                        return;
                    default:
                        asm.emit("_thread_position_in_threadgroup[");
                        asm.emit(asm.toString(x));
                        asm.emit("]");
                        return;
                }
            }

            if (opcode.equals("get_local_size")) {
                // Metal: use _local_size array passed as kernel argument
                if (x == null) {
                    asm.emit("_local_size[0]");
                } else {
                    asm.emit("_local_size[");
                    asm.emit(asm.toString(x));
                    asm.emit("]");
                }
                return;
            }

            if (opcode.equals("get_group_id")) {
                // Metal: use _threadgroup_position_in_grid.x/y/z
                String idx = (x == null) ? "0" : asm.toString(x);
                switch (idx.trim()) {
                    case "0":
                        asm.emit("_threadgroup_position_in_grid.x");
                        return;
                    case "1":
                        asm.emit("_threadgroup_position_in_grid.y");
                        return;
                    case "2":
                        asm.emit("_threadgroup_position_in_grid.z");
                        return;
                    default:
                        asm.emit("_threadgroup_position_in_grid[");
                        asm.emit(asm.toString(x));
                        asm.emit("]");
                        return;
                }
            }

            if (opcode.equals("get_group_size")) {
                // Metal: use _group_size array passed as kernel argument
                if (x == null) {
                    asm.emit("_group_size[0]");
                } else {
                    asm.emit("_group_size[");
                    asm.emit(asm.toString(x));
                    asm.emit("]");
                }
                return;
            }

            // default behaviour for other intrinsics
            emitOpcode(asm);
            if (x != null) {
                asm.emit("(");
                asm.emitValueOrOp(crb, x);
                asm.emit(")");
            }
        }

        public void emit(MetalCompilationResultBuilder crb) {
            emit(crb, null);
        }
    }

    public static class MetalUnaryTemplate extends MetalUnaryOp {
        // @formatter:off

        public static final MetalUnaryTemplate MEM_CHECK = new MetalUnaryTemplate("mem check", "MEM_CHECK(%s)");
        public static final MetalUnaryTemplate INDIRECTION = new MetalUnaryTemplate("deref", "*(%s)");
        public static final MetalUnaryTemplate CAST_TO_POINTER = new MetalUnaryTemplate("cast ptr", "(%s *)");
        public static final MetalUnaryTemplate LOAD_ADDRESS_ABS = new MetalUnaryTemplate("load address", "*(%s)");
        public static final MetalUnaryTemplate ADDRESS_OF = new MetalUnaryTemplate("address of", "&(%s)");

        public static final MetalUnaryTemplate NEW_INT_ARRAY = new MetalUnaryTemplate("int[]", "int[%s]");
        public static final MetalUnaryTemplate NEW_LONG_ARRAY = new MetalUnaryTemplate("long[]", "long[%s]");
        public static final MetalUnaryTemplate NEW_FLOAT_ARRAY = new MetalUnaryTemplate("float[]", "float[%s]");
        public static final MetalUnaryTemplate NEW_DOUBLE_ARRAY = new MetalUnaryTemplate("double[]", "double[%s]");
        public static final MetalUnaryTemplate NEW_BYTE_ARRAY = new MetalUnaryTemplate("char[]", "char[%s]");
        public static final MetalUnaryTemplate NEW_CHAR_ARRAY = new MetalUnaryTemplate("char[]", "char[%s]");
        public static final MetalUnaryTemplate NEW_SHORT_ARRAY = new MetalUnaryTemplate("short[]", "short[%s]");

        // @formatter:on
        private final String template;

        protected MetalUnaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value value) {
            final MetalAssembler asm = crb.getAssembler();
            asm.emit(template, asm.toString(value));
        }

        public String getTemplate() {
            return template;
        }

    }

    /**
     * Binary opcodes
     */
    public static class MetalBinaryOp extends MetalOp {
        // @formatter:off

        public static final MetalBinaryOp ADD = new MetalBinaryOp("+");
        public static final MetalBinaryOp SUB = new MetalBinaryOp("-");
        public static final MetalBinaryOp MUL = new MetalBinaryOp("*");
        public static final MetalBinaryOp DIV = new MetalBinaryOp("/");
        public static final MetalBinaryOp MOD = new MetalBinaryOp("%");

        public static final MetalBinaryOp BITWISE_AND = new MetalBinaryOp("&");
        public static final MetalBinaryOp BITWISE_OR = new MetalBinaryOp("|");
        public static final MetalBinaryOp BITWISE_XOR = new MetalBinaryOp("^");
        public static final MetalBinaryOp BITWISE_LEFT_SHIFT = new MetalBinaryOp("<<");
        public static final MetalBinaryOp BITWISE_RIGHT_SHIFT = new MetalBinaryOp(">>");

        public static final MetalBinaryOp LOGICAL_AND = new MetalBinaryOp("&&");
        public static final MetalBinaryOp LOGICAL_OR = new MetalBinaryOp("||");

        public static final MetalBinaryOp ASSIGN = new MetalBinaryOp("=");

        public static final MetalBinaryOp VECTOR_SELECT = new MetalBinaryOp(".");

        public static final MetalBinaryOp RELATIONAL_EQ = new MetalBinaryOp("==");
        public static final MetalBinaryOp RELATIONAL_NE = new MetalBinaryOp("!=");
        public static final MetalBinaryOp RELATIONAL_GT = new MetalBinaryOp(">");
        public static final MetalBinaryOp RELATIONAL_LT = new MetalBinaryOp("<");
        public static final MetalBinaryOp RELATIONAL_GTE = new MetalBinaryOp(">=");
        public static final MetalBinaryOp RELATIONAL_LTE = new MetalBinaryOp("<=");
        // @formatter:on

        protected MetalBinaryOp(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb, Value x, Value y) {
            final MetalAssembler asm = crb.getAssembler();
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
    public static class MetalBinaryIntrinsic extends MetalBinaryOp {
        // @formatter:off

        public static final MetalBinaryIntrinsic INT_MIN = new MetalBinaryIntrinsic("min");
        public static final MetalBinaryIntrinsic INT_MAX = new MetalBinaryIntrinsic("max");

        public static final MetalBinaryIntrinsic ATAN2 = new MetalBinaryIntrinsic("atan2");

        public static final MetalBinaryIntrinsic FLOAT_MIN = new MetalBinaryIntrinsic("fmin");
        public static final MetalBinaryIntrinsic FLOAT_MAX = new MetalBinaryIntrinsic("fmax");
        public static final MetalBinaryIntrinsic FLOAT_POW = new MetalBinaryIntrinsic("pow");

        public static final MetalBinaryIntrinsic ATOMIC_ADD = new MetalBinaryIntrinsic("atomic_add");
        public static final MetalBinaryIntrinsic ATOMIC_SUB = new MetalBinaryIntrinsic("atomic_sub");
        public static final MetalBinaryIntrinsic ATOMIC_XCHG = new MetalBinaryIntrinsic("atomic_xchg");
        public static final MetalBinaryIntrinsic ATOMIC_MIN = new MetalBinaryIntrinsic("atomic_min");
        public static final MetalBinaryIntrinsic ATOMIC_MAX = new MetalBinaryIntrinsic("atomic_max");
        public static final MetalBinaryIntrinsic ATOMIC_AND = new MetalBinaryIntrinsic("atomic_and");
        public static final MetalBinaryIntrinsic ATOMIC_OR = new MetalBinaryIntrinsic("atomic_or");
        public static final MetalBinaryIntrinsic ATOMIC_XOR = new MetalBinaryIntrinsic("atomic_xor");

        public static final MetalBinaryIntrinsic VLOAD2 = new MetalBinaryIntrinsic("vload2");
        public static final MetalBinaryIntrinsic VLOAD3 = new MetalBinaryIntrinsic("vload3");
        public static final MetalBinaryIntrinsic VLOAD4 = new MetalBinaryIntrinsic("vload4");
        public static final MetalBinaryIntrinsic VLOAD8 = new MetalBinaryIntrinsic("vload8");
        public static final MetalBinaryIntrinsic VLOAD16 = new MetalBinaryIntrinsic("vload16");

        public static final MetalBinaryIntrinsic DOT = new MetalBinaryIntrinsic("dot");
        public static final MetalBinaryIntrinsic CROSS = new MetalBinaryIntrinsic("cross");
        // @formatter:on

        protected MetalBinaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value x, Value y) {
            final MetalAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValueOrOp(crb, x);
            asm.emit(", ");
            asm.emitValueOrOp(crb, y);
            asm.emit(")");
        }
    }

    public static class MetalBinaryIntrinsicCmp extends MetalBinaryOp {

        // @formatter:off
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_EQUAL = new MetalBinaryIntrinsicCmp("isequal");
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_NOT_EQUAL = new MetalBinaryIntrinsicCmp("isnotequal");
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_GREATER = new MetalBinaryIntrinsicCmp("isgreater");
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_GREATEREQUAL = new MetalBinaryIntrinsicCmp("isgreaterequal");
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_LESS = new MetalBinaryIntrinsicCmp("isless");
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_LESSEQUAL = new MetalBinaryIntrinsicCmp("islessequal");
        public static final MetalBinaryIntrinsicCmp FLOAT_IS_LESSGREATER = new MetalBinaryIntrinsicCmp("islessgreater");
        // @formatter:on

        protected MetalBinaryIntrinsicCmp(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value x, Value y) {
            final MetalAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValueOrOp(crb, x);
            asm.emit(", ");
            asm.emitValueOrOp(crb, y);
            asm.emit(")");
        }
    }

    public static class MetalBinaryTemplate extends MetalBinaryOp {
        // @formatter:off

        public static final MetalBinaryTemplate DECLARE_BYTE_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "byte %s[%s]");
        public static final MetalBinaryTemplate DECLARE_CHAR_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "char %s[%s]");
        public static final MetalBinaryTemplate DECLARE_SHORT_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "short %s[%s]");
        public static final MetalBinaryTemplate DECLARE_INT_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "int %s[%s]");
        public static final MetalBinaryTemplate DECLARE_LONG_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "long %s[%s]");
        public static final MetalBinaryTemplate DECLARE_FLOAT_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "float %s[%s]");
        public static final MetalBinaryTemplate DECLARE_DOUBLE_ARRAY = new MetalBinaryTemplate("DECLARE_ARRAY", "double %s[%s]");
        public static final MetalBinaryTemplate ARRAY_INDEX = new MetalBinaryTemplate("index", "%s[%s]");

    public static final MetalBinaryTemplate NEW_PRIVATE_CHAR_ARRAY = new MetalBinaryTemplate("new private array char", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " char %s[%s]");
    public static final MetalBinaryTemplate NEW_PRIVATE_FLOAT_ARRAY = new MetalBinaryTemplate("new private array float", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " float %s[%s]");
    public static final MetalBinaryTemplate NEW_PRIVATE_INT_ARRAY = new MetalBinaryTemplate("new private array int", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " int %s[%s]");
    public static final MetalBinaryTemplate NEW_PRIVATE_DOUBLE_ARRAY = new MetalBinaryTemplate("new private array double", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " double %s[%s]");
    public static final MetalBinaryTemplate NEW_PRIVATE_LONG_ARRAY = new MetalBinaryTemplate("new private array long", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " long %s[%s]");
    public static final MetalBinaryTemplate NEW_PRIVATE_SHORT_ARRAY = new MetalBinaryTemplate("new private array short", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " short %s[%s]");
    public static final MetalBinaryTemplate NEW_PRIVATE_BYTE_ARRAY = new MetalBinaryTemplate("new private array byte", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " byte %s[%s]");

    public static final MetalBinaryTemplate PRIVATE_INT_ARRAY_PTR = new MetalBinaryTemplate("private pointer array int", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " int* %s = %s");
    public static final MetalBinaryTemplate PRIVATE_CHAR_ARRAY_PTR = new MetalBinaryTemplate("private pointer array char", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " char* %s = %s");
    public static final MetalBinaryTemplate PRIVATE_FLOAT_ARRAY_PTR = new MetalBinaryTemplate("private pointer array float", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " float* %s = %s");
    public static final MetalBinaryTemplate PRIVATE_DOUBLE_ARRAY_PTR = new MetalBinaryTemplate("private pointer array double", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " double* %s = %s");
    public static final MetalBinaryTemplate PRIVATE_LONG_ARRAY_PTR = new MetalBinaryTemplate("private pointer array long", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " long* %s = %s");
    public static final MetalBinaryTemplate PRIVATE_SHORT_ARRAY_PTR = new MetalBinaryTemplate("private pointer array short", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " short* %s = %s");
    public static final MetalBinaryTemplate PRIVATE_BYTE_ARRAY_PTR = new MetalBinaryTemplate("private pointer array byte", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " byte* %s = %s");

    public static final MetalBinaryTemplate PRIVATE_INT_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array int", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " int* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " int *) %s)");
    public static final MetalBinaryTemplate PRIVATE_CHAR_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array char", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " char* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " char *) %s)");
    public static final MetalBinaryTemplate PRIVATE_FLOAT_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array float", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " float* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " float *) %s)");
    public static final MetalBinaryTemplate PRIVATE_DOUBLE_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array double", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " double* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " double *) %s)");
    public static final MetalBinaryTemplate PRIVATE_LONG_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array long", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " long* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " long *) %s)");
    public static final MetalBinaryTemplate PRIVATE_SHORT_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array short", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " short* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " short *) %s)");
    public static final MetalBinaryTemplate PRIVATE_BYTE_ARRAY_PTR_COPY = new MetalBinaryTemplate("private pointer copy array byte", MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " byte* %s = ((" + MetalAssemblerConstants.PRIVATE_MEM_MODIFIER + " byte *) %s)");

        public static final MetalBinaryTemplate NEW_LOCAL_FLOAT_ARRAY = new MetalBinaryTemplate("local memory array float", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " float %s[%s]");
        public static final MetalBinaryTemplate NEW_LOCAL_INT_ARRAY = new MetalBinaryTemplate("local memory array int", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " int %s[%s]");
        public static final MetalBinaryTemplate NEW_LOCAL_DOUBLE_ARRAY = new MetalBinaryTemplate("local memory array double", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " double %s[%s]");
        public static final MetalBinaryTemplate NEW_LOCAL_LONG_ARRAY = new MetalBinaryTemplate("local memory array long", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " long %s[%s]");
        public static final MetalBinaryTemplate NEW_LOCAL_SHORT_ARRAY = new MetalBinaryTemplate("local memory array short", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " short %s[%s]");
        public static final MetalBinaryTemplate NEW_LOCAL_CHAR_ARRAY = new MetalBinaryTemplate("local memory array char", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " char %s[%s]");
        public static final MetalBinaryTemplate NEW_LOCAL_BYTE_ARRAY = new MetalBinaryTemplate("local memory array byte", MetalAssemblerConstants.LOCAL_MEM_MODIFIER + " byte %s[%s]");
        // @formatter:on
        private final String template;

        protected MetalBinaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value x, Value y) {
            final MetalAssembler asm = crb.getAssembler();
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
    public static class MetalTernaryOp extends MetalOp {
        // @formatter:off

        // @formatter:on
        protected MetalTernaryOp(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb, Value x, Value y, Value z) {
            final MetalAssembler asm = crb.getAssembler();
            asm.emitLine("// unimplemented ternary op:");
        }
    }

    /**
     * Ternary intrinsic
     */
    public static class MetalTernaryIntrinsic extends MetalTernaryOp {
        // @formatter:off

        public static final MetalTernaryIntrinsic VSTORE2 = new MetalTernaryIntrinsic("vstore2");
        public static final MetalTernaryIntrinsic VSTORE3 = new MetalTernaryIntrinsic("vstore3");
        public static final MetalTernaryIntrinsic VSTORE4 = new MetalTernaryIntrinsic("vstore4");
        public static final MetalTernaryIntrinsic VSTORE8 = new MetalTernaryIntrinsic("vstore8");
        public static final MetalTernaryIntrinsic VSTORE16 = new MetalTernaryIntrinsic("vstore16");
        public static final MetalTernaryIntrinsic CLAMP = new MetalTernaryIntrinsic("clamp");
        public static final MetalTernaryIntrinsic FMA = new MetalTernaryIntrinsic("fma");
        // @formatter:on

        protected MetalTernaryIntrinsic(String opcode) {
            super(opcode);
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value x, Value y, Value z) {
            final MetalAssembler asm = crb.getAssembler();
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

    public static class MetalTernaryTemplate extends MetalTernaryOp {
        // @formatter:off

        public static final MetalTernaryTemplate SELECT = new MetalTernaryTemplate("select", "(%s) ? %s : %s");

        // @formatter:on
        private final String template;

        protected MetalTernaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(MetalCompilationResultBuilder crb, Value x, Value y, Value z) {
            final MetalAssembler asm = crb.getAssembler();
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

    public static class MetalOp2 extends MetalOp {

        // @formatter:off
        public static final MetalOp2 VMOV_SHORT2 = new MetalOp2("(short2)");
        public static final MetalOp2 VMOV_INT2 = new MetalOp2("(int2)");
        public static final MetalOp2 VMOV_FLOAT2 = new MetalOp2("(float2)");
        public static final MetalOp2 VMOV_BYTE2 = new MetalOp2("(char2)");
        public static final MetalOp2 VMOV_DOUBLE2 = new MetalOp2("(double2)");

        public static final MetalOp2 VMOV_HALF2 = new MetalOp2("(half2)");
        // @formatter:on

        protected MetalOp2(String opcode) {
            super(opcode);
        }

        // FIXME: Remove these emits from vector operations. They are not reachable
        public void emit(MetalCompilationResultBuilder crb, Value s0, Value s1) {
            final MetalAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(crb, s0);
            asm.emit(", ");
            asm.emitValue(crb, s1);
            asm.emit(")");
        }
    }

    public static class MetalOp3 extends MetalOp2 {
        // @formatter:off

        public static final MetalOp3 VMOV_SHORT3 = new MetalOp3("(short3)");
        public static final MetalOp3 VMOV_INT3 = new MetalOp3("(int3)");
        public static final MetalOp3 VMOV_FLOAT3 = new MetalOp3("(float3)");
        public static final MetalOp3 VMOV_BYTE3 = new MetalOp3("(char3)");
        public static final MetalOp3 VMOV_DOUBLE3 = new MetalOp3("(double3)");

        public static final MetalOp3 VMOV_HALF3 = new MetalOp3("(half3)");

        // @formatter:on
        public MetalOp3(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb, Value s0, Value s1, Value s2) {
            final MetalAssembler asm = crb.getAssembler();
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

    public static class MetalOp4 extends MetalOp3 {
        // @formatter:off

        public static final MetalOp4 VMOV_SHORT4 = new MetalOp4("(short4)");
        public static final MetalOp4 VMOV_INT4 = new MetalOp4("(int4)");
        public static final MetalOp4 VMOV_FLOAT4 = new MetalOp4("(float4)");
        public static final MetalOp4 VMOV_BYTE4 = new MetalOp4("(char4)");
        public static final MetalOp4 VMOV_DOUBLE4 = new MetalOp4("(double4)");

        public static final MetalOp4 VMOV_HALF4 = new MetalOp4("(half4)");
        // @formatter:on

        protected MetalOp4(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3) {
            final MetalAssembler asm = crb.getAssembler();
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

    public static class MetalOp8 extends MetalOp4 {
        // @formatter:off

        public static final MetalOp8 VMOV_SHORT8 = new MetalOp8("(short8)");
        public static final MetalOp8 VMOV_INT8 = new MetalOp8("(int8)");
        public static final MetalOp8 VMOV_FLOAT8 = new MetalOp8("(float8)");
        public static final MetalOp8 VMOV_BYTE8 = new MetalOp8("(char8)");
        public static final MetalOp8 VMOV_DOUBLE8 = new MetalOp8("(double8)");

        public static final MetalOp8 VMOV_HALF8 = new MetalOp8("(half8)");

        // @formatter:on

        protected MetalOp8(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7) {
            final MetalAssembler asm = crb.getAssembler();
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

    public static class MetalOp16 extends MetalOp8 {
        // @formatter:off
        public static final MetalOp16 VMOV_SHORT16 = new MetalOp16("(short16)");
        public static final MetalOp16 VMOV_INT16 = new MetalOp16("(int16)");
        public static final MetalOp16 VMOV_FLOAT16 = new MetalOp16("(float16)");
        public static final MetalOp16 VMOV_BYTE16 = new MetalOp16("(char16)");
        public static final MetalOp16 VMOV_DOUBLE16 = new MetalOp16("(double16)");
        public static final MetalOp16 VMOV_HALF16 = new MetalOp16("(half16)");
        // @formatter:on
        protected MetalOp16(String opcode) {
            super(opcode);
        }

        public void emit(MetalCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3, Value s4, Value s5, Value s6, Value s7, Value s8, Value s9, Value s10, Value s11, Value s12,
                Value s13, Value s14, Value s15) {
            final MetalAssembler asm = crb.getAssembler();
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
