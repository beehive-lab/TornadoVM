package tornado.drivers.opencl.graal.asm;

import com.oracle.graal.asm.AbstractAddress;
import com.oracle.graal.asm.Assembler;
import com.oracle.graal.asm.Label;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.Variable;
import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIROp;
import tornado.drivers.opencl.graal.lir.OCLReturnSlot;

import static com.oracle.graal.compiler.common.util.Util.guarantee;
import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.*;
import static tornado.drivers.opencl.graal.lir.OCLKind.*;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLAssembler extends Assembler {

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
        public static final OCLNullaryOp SLOTS_BASE_ADDRESS = new OCLNullaryOp("(ulong) " + HEAP_REF_NAME);
        // @formatter:on

        protected OCLNullaryOp(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class OCLMemoryOp extends OCLNullaryOp {

        // @formatter:off
        public static final OCLMemoryOp GLOBAL_REGION = new OCLMemoryOp(GLOBAL_REGION_NAME);
        public static final OCLMemoryOp LOCAL_REGION = new OCLMemoryOp(LOCAL_REGION_NAME);
        public static final OCLMemoryOp PRIVATE_REGION = new OCLMemoryOp(PRIVATE_REGION_NAME);
        public static final OCLMemoryOp CONSTANT_REGION = new OCLMemoryOp(CONSTANT_REGION_NAME);
        // @formatter:on

        protected OCLMemoryOp(String opcode) {
            super(opcode);
        }

        @Override
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

        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
        }
    }

    public static class OCLNullaryTemplate extends OCLNullaryOp {
        // @formatter:off

        // @formatter:on
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
                asm.emitValue(
                        crb, x);
            } else {
                asm.emitValue(
                        crb, x);
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

        public static final OCLUnaryIntrinsic LOCAL_ID = new OCLUnaryIntrinsic("get_local_id");
        public static final OCLUnaryIntrinsic LOCAL_SIZE = new OCLUnaryIntrinsic("get_local_size");

        public static final OCLUnaryIntrinsic GROUP_ID = new OCLUnaryIntrinsic("get_group_id");
        public static final OCLUnaryIntrinsic GROUP_SIZE = new OCLUnaryIntrinsic("get_group_size");

        public static final OCLUnaryIntrinsic ATOMIC_INC = new OCLUnaryIntrinsic("atomic_inc");
        public static final OCLUnaryIntrinsic ATOMIC_DEC = new OCLUnaryIntrinsic("atomic_dec");

        public static final OCLUnaryIntrinsic BARRIER = new OCLUnaryIntrinsic("barrier");
        public static final OCLUnaryIntrinsic MEM_FENCE = new OCLUnaryIntrinsic("mem_fence");
        public static final OCLUnaryIntrinsic READ_MEM_FENCE = new OCLUnaryIntrinsic("read_mem_fence");
        public static final OCLUnaryIntrinsic WRITE_MEM_FENCE = new OCLUnaryIntrinsic("write_mem_fence");

        public static final OCLUnaryIntrinsic ABS = new OCLUnaryIntrinsic("abs");
        public static final OCLUnaryIntrinsic EXP = new OCLUnaryIntrinsic("exp");
        public static final OCLUnaryIntrinsic SQRT = new OCLUnaryIntrinsic("sqrt");
        public static final OCLUnaryIntrinsic LOG = new OCLUnaryIntrinsic("log");

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
            asm.emit("(");
            asm.emitValue(
                    crb, x);
            asm.emit(")");
        }
    }

    public static class OCLUnaryTemplate extends OCLUnaryOp {
        // @formatter:off

        public static final OCLUnaryTemplate LOAD_PARAM_INT = new OCLUnaryTemplate("param", "(int) slots[%s]");
        public static final OCLUnaryTemplate LOAD_PARAM_LONG = new OCLUnaryTemplate("param", "(long) slots[%s]");
        public static final OCLUnaryTemplate LOAD_PARAM_FLOAT = new OCLUnaryTemplate("param", "(float) slots[%s]");
        public static final OCLUnaryTemplate LOAD_PARAM_DOUBLE = new OCLUnaryTemplate("param", "(double) slots[%s]");
        public static final OCLUnaryTemplate LOAD_PARAM_OBJECT_ABS = new OCLUnaryTemplate("param", "(ulong) slots[%s]");
//        public static final OCLUnaryTemplate LOAD_PARAM_OBJECT_REL = new OCLUnaryTemplate("param", "(ulong) &"+OCLAssemblerConstants.HEAP_REF_NAME +" [slots[%s]]");
        public static final OCLUnaryTemplate SLOT_ADDRESS = new OCLUnaryTemplate("param", "(ulong) &slots[%s]");

        public static final OCLUnaryTemplate MEM_CHECK = new OCLUnaryTemplate("mem check", "MEM_CHECK(%s)");
        public static final OCLUnaryTemplate INDIRECTION = new OCLUnaryTemplate("deref", "*(%s)");
        public static final OCLUnaryTemplate CAST_TO_POINTER = new OCLUnaryTemplate("cast ptr", "(%s *)");
        public static final OCLUnaryTemplate LOAD_ADDRESS_ABS = new OCLUnaryTemplate("load address", "*(%s)");
        public static final OCLUnaryTemplate LOAD_ADDRESS_REL = new OCLUnaryTemplate("load address", "*(%s) + (ulong) " + OCLAssemblerConstants.HEAP_REF_NAME + ")");
        public static final OCLUnaryTemplate ADDRESS_OF = new OCLUnaryTemplate("address of", "&(%s)");

        public static final OCLUnaryTemplate NEW_INT_ARRAY = new OCLUnaryTemplate("int[]", "int[%s]");
        public static final OCLUnaryTemplate NEW_LONG_ARRAY = new OCLUnaryTemplate("int[]", "long[%s]");
        public static final OCLUnaryTemplate NEW_FLOAT_ARRAY = new OCLUnaryTemplate("int[]", "float[%s]");
        public static final OCLUnaryTemplate NEW_DOUBLE_ARRAY = new OCLUnaryTemplate("int[]", "double[%s]");
        public static final OCLUnaryTemplate NEW_BYTE_ARRAY = new OCLUnaryTemplate("int[]", "char[%s]");
        public static final OCLUnaryTemplate NEW_SHORT_ARRAY = new OCLUnaryTemplate("int[]", "short[%s]");

        // @formatter:on
        private final String template;

        protected OCLUnaryTemplate(String opcode, String template) {
            super(opcode);
            this.template = template;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb, Value value) {
            final OCLAssembler asm = crb.getAssembler();
            asm.emit(
                    template, asm.toString(value));
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

        public static final OCLBinaryIntrinsic FLOAT_MIN = new OCLBinaryIntrinsic("fmin");
        public static final OCLBinaryIntrinsic FLOAT_MAX = new OCLBinaryIntrinsic("fmax");

        public static final OCLBinaryIntrinsic ATOMIC_ADD = new OCLBinaryIntrinsic("atomic_add");
        public static final OCLBinaryIntrinsic ATOMIC_SUB = new OCLBinaryIntrinsic("atomic_sub");
        public static final OCLBinaryIntrinsic ATOMIC_XCHG = new OCLBinaryIntrinsic("atomic_xchg");
        public static final OCLBinaryIntrinsic ATOMIC_MIN = new OCLBinaryIntrinsic("atomic_min");
        public static final OCLBinaryIntrinsic ATOMIC_MAX = new OCLBinaryIntrinsic("atomic_max");
        public static final OCLBinaryIntrinsic ATOMIC_AND = new OCLBinaryIntrinsic("atomic_and");
        public static final OCLBinaryIntrinsic ATOMIC_OR = new OCLBinaryIntrinsic("atomic_or");
        public static final OCLBinaryIntrinsic ATOMIC_XOR = new OCLBinaryIntrinsic("atomic_xor");

        public static final OCLBinaryIntrinsic FLOAT_IS_EQUAL = new OCLBinaryIntrinsic("isequal");
        public static final OCLBinaryIntrinsic FLOAT_IS_NOT_EQUAL = new OCLBinaryIntrinsic("isnotequal");
        public static final OCLBinaryIntrinsic FLOAT_IS_GREATER = new OCLBinaryIntrinsic("isgreater");
        public static final OCLBinaryIntrinsic FLOAT_IS_GREATER_EQUAL = new OCLBinaryIntrinsic("isgreaterequal");
        public static final OCLBinaryIntrinsic FLOAT_IS_LESS = new OCLBinaryIntrinsic("isless");
        public static final OCLBinaryIntrinsic FLOAT_IS_LESSEQUAL = new OCLBinaryIntrinsic("islessequal");
        public static final OCLBinaryIntrinsic FLOAT_IS_LESSGREATER = new OCLBinaryIntrinsic("islessgreater");

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

        public static final OCLBinaryTemplate NEW_ARRAY = new OCLBinaryTemplate("new array", "char %s[%s]");

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
        // @formatter:on

        protected OCLOp2(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(
                    crb, s0);
            asm.emit(", ");
            asm.emitValue(
                    crb, s1);
            asm.emit(")");
        }
    }

    public static class OCLOp3 extends OCLOp2 {
        // @formatter:off

        public static final OCLOp3 VMOV_SHORT3 = new OCLOp3("(short3)");
        public static final OCLOp3 VMOV_INT3 = new OCLOp3("(int3)");
        public static final OCLOp3 VMOV_FLOAT3 = new OCLOp3("(float3)");
        public static final OCLOp3 VMOV_BYTE3 = new OCLOp3("(char3)");

        // @formatter:on
        public OCLOp3(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(
                    crb, s0);
            asm.emit(", ");
            asm.emitValue(
                    crb, s1);
            asm.emit(", ");
            asm.emitValue(
                    crb, s2);
            asm.emit(")");
        }
    }

    public static class OCLOp4 extends OCLOp3 {
        // @formatter:off

        public static final OCLOp4 VMOV_SHORT4 = new OCLOp4("(short4)");
        public static final OCLOp4 VMOV_INT4 = new OCLOp4("(int4)");
        public static final OCLOp4 VMOV_FLOAT4 = new OCLOp4("(float4)");
        public static final OCLOp4 VMOV_BYTE4 = new OCLOp4("(char4)");
        // @formatter:on

        protected OCLOp4(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(
                    crb, s0);
            asm.emit(", ");
            asm.emitValue(
                    crb, s1);
            asm.emit(", ");
            asm.emitValue(
                    crb, s2);
            asm.emit(", ");
            asm.emitValue(
                    crb, s3);
            asm.emit(")");
        }
    }

    public static class OCLOp8 extends OCLOp4 {
        // @formatter:off

        public static final OCLOp8 VMOV_SHORT8 = new OCLOp8("(short8)");
        public static final OCLOp8 VMOV_INT8 = new OCLOp8("(int8)");
        public static final OCLOp8 VMOV_FLOAT8 = new OCLOp8("(float8)");
        public static final OCLOp8 VMOV_BYTE8 = new OCLOp8("(char8)");
        // @formatter:on

        protected OCLOp8(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3,
                Value s4, Value s5, Value s6, Value s7) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(
                    crb, s0);
            asm.emit(", ");
            asm.emitValue(
                    crb, s1);
            asm.emit(", ");
            asm.emitValue(
                    crb, s2);
            asm.emit(", ");
            asm.emitValue(
                    crb, s3);
            asm.emit(", ");
            asm.emitValue(
                    crb, s4);
            asm.emit(", ");
            asm.emitValue(
                    crb, s5);
            asm.emit(", ");
            asm.emitValue(
                    crb, s6);
            asm.emit(", ");
            asm.emitValue(
                    crb, s7);
            asm.emit(")");
        }
    }

    public static class OCLOp16 extends OCLOp8 {
        // @formatter:off

        // @formatter:on
        protected OCLOp16(String opcode) {
            super(opcode);
        }

        public void emit(OCLCompilationResultBuilder crb, Value s0, Value s1, Value s2, Value s3,
                Value s4, Value s5, Value s6, Value s7, Value s8, Value s9, Value s10, Value s11,
                Value s12, Value s13, Value s14, Value s15) {
            final OCLAssembler asm = crb.getAssembler();
            emitOpcode(asm);
            asm.emit("(");
            asm.emitValue(
                    crb, s0);
            asm.emit(", ");
            asm.emitValue(
                    crb, s1);
            asm.emit(", ");
            asm.emitValue(
                    crb, s2);
            asm.emit(", ");
            asm.emitValue(
                    crb, s3);
            asm.emit(", ");
            asm.emitValue(
                    crb, s4);
            asm.emit(", ");
            asm.emitValue(
                    crb, s5);
            asm.emit(", ");
            asm.emitValue(
                    crb, s6);
            asm.emit(", ");
            asm.emitValue(
                    crb, s7);
            asm.emit(", ");
            asm.emitValue(
                    crb, s8);
            asm.emit(", ");
            asm.emitValue(
                    crb, s9);
            asm.emit(", ");
            asm.emitValue(
                    crb, s10);
            asm.emit(", ");
            asm.emitValue(
                    crb, s11);
            asm.emit(", ");
            asm.emitValue(
                    crb, s12);
            asm.emit(", ");
            asm.emitValue(
                    crb, s13);
            asm.emit(", ");
            asm.emitValue(
                    crb, s14);
            asm.emit(", ");
            asm.emitValue(
                    crb, s15);
            asm.emit(")");
        }
    }

    private int indent;
    private int lastIndent;
    private String delimiter;
    private boolean emitEOL;

    private List<String> operandStack;
    private boolean pushToStack;

    public OCLAssembler(TargetDescription target) {
        super(target);
        indent = 0;
        delimiter = OCLAssemblerConstants.STMT_DELIMITER;
        emitEOL = true;
        operandStack = new ArrayList<>(10);
        pushToStack = false;

    }

    private static OCLAssembler getAssembler(OCLCompilationResultBuilder crb) {
        return (OCLAssembler) crb.asm;
    }

    @Override
    public void align(int arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void ensureUniquePC() {
        // TODO Auto-generated method stub

    }

    @Override
    public AbstractAddress getPlaceholder(int i) {
        unimplemented();
        return null;
    }

    @Override
    public void jmp(Label arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public AbstractAddress makeAddress(Register arg0, int arg1) {
        unimplemented();
        return null;
    }

    @Override
    protected void patchJumpTarget(int arg0, int arg1) {
        unimplemented();
    }

    /**
     * *
     * Used to emit instructions within a method. i.e. ones that terminal with a
     * ';'
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
     * *
     * Used to emit function defs and control flow statements. i.e. strings that
     * do not terminate with a ';'
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
        emitLine(String.format(
                fmt, args));
    }

    public void emitLine(String str) {
        indent();
        emitSubString(str);
        eol();
    }

    public void emit(String str) {
        emitSubString(str);

    }

    public void emit(String fmt, Object... args) {
        emitSubString(String.format(
                fmt, args));
    }

    public void dump() {
        for (int i = 0; i < position(); i++) {
            System.out.printf("%c", (char) getByte(i));
        }
    }

    public void ret() {
        emitStmt("return");

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
        return str.replace("\n", "\\n").replace("\t", "\\t");
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
            HotSpotObjectConstant objConst = (HotSpotObjectConstant) cv;
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
            return var.getName();
        } else if (value instanceof ConstantValue) {
            if (!((ConstantValue) value).isJavaConstant()) {
                shouldNotReachHere("constant value: ", value);
            }

            ConstantValue cv = (ConstantValue) value;
            return formatConstant(cv);
        } else if (value instanceof OCLReturnSlot) {
            final String type = ((OCLKind) value.getPlatformKind()).name().toLowerCase();
            return String.format("*((__global %s *) %s)", type, HEAP_REF_NAME);
//        } else if (emitValue instanceof OCLNullary.Expr) {
//            return ((OCLNullary.Expr) emitValue).toString();
        } else {
            unimplemented("value: toString() type=%s, value=%s", value.getClass().getName(), value);
        }
        return result;
    }

    public void emitValue(OCLCompilationResultBuilder crb, Value value) {
//        System.out.printf("emitValue: %s (%s)\n", emitValue, emitValue.getClass().getName());
        emit(toString(value));
    }

    public void assign() {
        emitSymbol(OCLAssemblerConstants.ASSIGN);
    }

    public void ifStmt(OCLCompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(OCLAssemblerConstants.IF_STMT);
        emitSymbol(OCLAssemblerConstants.BRACKET_OPEN);

        emit(toString(condition));
        //value(crb, condition);

        emitSymbol(OCLAssemblerConstants.BRACKET_CLOSE);
        eol();

    }

    public void loadParam(Variable result, int index) {
        emit("(%s) slots[%d]", result.getPlatformKind().name(), index + 6);
    }

    @Deprecated
    public void loadParam64(Variable result, int paramIndex) {
        loadParam(result, paramIndex);
    }

    @Deprecated
    public void loadParam32(Variable result, int paramIndex) {
        loadParam(result, paramIndex);
    }

    public void space() {
        emitSymbol(" ");
    }

    public void elseIfStmt(OCLCompilationResultBuilder crb, Value condition) {

        indent();

        emitSymbol(OCLAssemblerConstants.ELSE);
        space();
        emitSymbol(OCLAssemblerConstants.IF_STMT);
        emitSymbol(OCLAssemblerConstants.BRACKET_OPEN);

        emitValue(crb, condition);

        emitSymbol(OCLAssemblerConstants.BRACKET_CLOSE);
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
}
