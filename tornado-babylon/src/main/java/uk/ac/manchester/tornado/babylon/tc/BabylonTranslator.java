/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.babylon.tc;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.incubator.code.Block;
import jdk.incubator.code.CodeType;
import jdk.incubator.code.Op;
import jdk.incubator.code.Value;
import jdk.incubator.code.dialect.core.CoreOp;
import jdk.incubator.code.dialect.java.JavaOp;
import jdk.incubator.code.dialect.java.JavaType;
import jdk.incubator.code.dialect.java.MethodRef;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.UnaryOp;
import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Translates one lowered, SSA-form Babylon code model ({@code CoreOp.FuncOp}) into a TC function at
 * the same Java level as the bytecode frontend's output: the same parameter types, Java array ops,
 * {@code call}s to device functions by their TC names, and the Tornado intrinsics. Code models already
 * have TC's shape (blocks with parameters, one op per value), so the translation is op by op.
 *
 * <p>HAT kernels: {@code hat.KernelContext}'s static index functions ({@code GIX()} ...) become thread
 * intrinsics and the parameter itself becomes TornadoVM's {@code KernelContext}; a HAT buffer
 * ({@code optkl.ifacemapper.Buffer}) becomes a {@code ByteArray} whose accessor calls ({@code array(i)},
 * {@code array(i, v)}, {@code length()}) load and store at the offsets of the buffer's bound layout.</p>
 */
final class BabylonTranslator {

    /** Unsupported code; reported as a TC diagnostic by the caller. */
    static final class Unsupported extends RuntimeException {
        final String code;

        Unsupported(String code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }

    /** What the translator needs from its caller: classes, layouts and the names of callees it emits. */
    interface Context {
        Class<?> load(JavaType type);

        /** The bound layout of the HAT buffer passed as parameter {@code index} of the function, or null. */
        GroupLayout bufferLayout(int index);

        /** Called for each {@code call} emitted: the callee must be added to the module. */
        void callee(Method method, String name);
    }

    private static final String HAT_KERNEL_CONTEXT = "hat.KernelContext";
    private static final String HAT_F16 = "Lhat/types/F16;";
    /** HAT's half-float types: F16 and the F16 elements of an F16Array. */
    private static final java.util.Set<String> HAT_F16_TYPES = java.util.Set.of(HAT_F16, "Lhat/buffer/F16Array$F16Impl;");
    private static final java.util.Set<String> HAT_F16_OWNERS = java.util.Set.of("hat.types.F16", "hat.types.S16ImplOfF16", "hat.buffer.F16Array$F16Impl");
    private static final String TORNADO_KERNEL_CONTEXT = "Luk/ac/manchester/tornado/api/KernelContext;";
    private static final String TORNADO_BYTE_ARRAY = "Luk/ac/manchester/tornado/api/types/arrays/ByteArray;";

    private final CoreOp.FuncOp model;
    private final Context context;
    private final Function function;
    private final String sourceFile;
    private final Map<Value, uk.ac.manchester.tornado.tc.ir.Value> values = new HashMap<>();
    private final Map<Block, uk.ac.manchester.tornado.tc.ir.Block> blocks = new LinkedHashMap<>();
    /** Kernel parameters that are HAT buffers: TC value to parameter index. */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, Integer> bufferParams = new HashMap<>();
    private final boolean kernel;
    /** HAT local and private memory ({@code createLocal()}, {@code createPrivate()}): pointer to element type. */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, Type> deviceArrays = new HashMap<>();
    /** Results of accessors that return a struct inside a HAT buffer. */
    private final Map<Value, View> views = new HashMap<>();
    /** F16 elements of HAT local or private memory: their addresses, for {@code value()} / {@code value(short)}. */
    private final Map<Value, uk.ac.manchester.tornado.tc.ir.Value> elementAddresses = new HashMap<>();

    private BabylonTranslator(CoreOp.FuncOp model, Context context, String name, boolean kernel, String sourceFile) {
        this.model = model;
        this.context = context;
        this.kernel = kernel;
        this.sourceFile = sourceFile;
        this.function = new Function(name, kernel ? Function.Kind.KERNEL : Function.Kind.DEVICE, valueType(model.invokableSignature().returnType()));
    }

    static Function translate(CoreOp.FuncOp model, Context context, String name, boolean kernel, String sourceFile) {
        return new BabylonTranslator(model, context, name, kernel, sourceFile).run();
    }

    private Function run() {
        var body = model.body();
        for (var block : body.blocks()) {
            blocks.put(block, function.newBlock(block.isEntryBlock() ? "entry" : "b" + blocks.size()));
        }
        // Parameters: the entry block's, typed as the bytecode frontend types kernel parameters.
        var entry = body.entryBlock();
        int index = 0;
        for (var p : entry.parameters()) {
            var tcType = parameterType(p.type());
            var arg = blocks.get(entry).addArg(tcType, "arg" + index); // named as the bytecode frontend names them
            values.put(p, arg);
            if (isHatBuffer(p.type())) {
                bufferParams.put(arg, index);
            }
            index++;
        }
        for (var block : body.blocks()) {
            if (block.isEntryBlock()) {
                continue;
            }
            for (var p : block.parameters()) {
                values.put(p, blocks.get(block).addArg(valueType(p.type()), ""));
            }
        }
        for (var block : body.blocks()) {
            var b = new IrBuilder(blocks.get(block));
            for (var op : block.ops()) {
                translate(b.at(location(op)), op);
            }
        }
        return function;
    }

    private Loc location(Op op) {
        var l = op.location();
        return l == null ? Loc.UNKNOWN : new Loc(sourceFile, l.line(), -1);
    }

    // ---- types

    private static String descriptor(CodeType type) {
        return ((JavaType) type).toNominalDescriptor().descriptorString();
    }

    private boolean isHatKernelContext(CodeType type) {
        return type instanceof JavaType j && j.toString().equals(HAT_KERNEL_CONTEXT);
    }

    private boolean isHatBuffer(CodeType type) {
        if (!(type instanceof JavaType j) || j.toNominalDescriptor().isPrimitive() || j.toNominalDescriptor().isArray()) {
            return false;
        }
        var c = context.load(j);
        return c != null && implementsByName(c, "optkl.ifacemapper.Buffer");
    }

    private static boolean implementsByName(Class<?> c, String name) {
        if (c == null) {
            return false;
        }
        if (c.getName().equals(name)) {
            return true;
        }
        for (var i : c.getInterfaces()) {
            if (implementsByName(i, name)) {
                return true;
            }
        }
        return implementsByName(c.getSuperclass(), name);
    }

    private Type parameterType(CodeType type) {
        if (isHatKernelContext(type)) {
            return Frontend.typeOf(TORNADO_KERNEL_CONTEXT);
        }
        if (isHatBuffer(type)) {
            return Frontend.typeOf(TORNADO_BYTE_ARRAY);
        }
        return valueType(type);
    }

    /** Values: booleans are i1 (compare results); byte, short and char are ints, as on the JVM stack. */
    private static Type valueType(CodeType type) {
        return switch (descriptor(type)) {
            case "Z" -> Type.I1;
            case "B", "S", "C", "I" -> Type.I32;
            case "J" -> Type.I64;
            case "F" -> Type.F32;
            case "D" -> Type.F64;
            case "V" -> Type.VOID;
            case String d when HAT_F16_TYPES.contains(d) -> Type.F16;
            default -> Frontend.typeOf(descriptor(type));
        };
    }

    // ---- ops

    private uk.ac.manchester.tornado.tc.ir.Value v(Value value) {
        var tc = values.get(value);
        if (tc == null && views.containsKey(value)) {
            throw new Unsupported("TC-E1003", "a HAT struct (" + views.get(value).layout().name().orElse("?") + ") used as a value is not supported yet");
        }
        if (tc == null) {
            throw new Unsupported("TC-E9003", "value used before its definition in the code model: " + value);
        }
        return tc;
    }

    private List<uk.ac.manchester.tornado.tc.ir.Value> args(List<Value> vs) {
        var out = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (var x : vs) {
            out.add(v(x));
        }
        return out;
    }

    private void define(Op op, uk.ac.manchester.tornado.tc.ir.Value value) {
        if (op.result() != null && value != null) {
            values.put(op.result(), value);
        }
    }

    private void translate(IrBuilder b, Op op) {
        switch (op) {
            case CoreOp.ConstantOp c -> define(op, constant(b, c.value(), c.resultType()));
            case JavaOp.BinaryOp bin -> define(op, binary(b, bin));
            case JavaOp.CompareOp cmp -> define(op, compare(b, cmp));
            case JavaOp.NegOp neg -> {
                var x = v(neg.operands().get(0));
                define(op, b.unary(x.type().isFloat() ? UnaryOp.FNEG : UnaryOp.NEG, x));
            }
            case JavaOp.ComplOp compl -> define(op, b.unary(UnaryOp.NOT, v(compl.operands().get(0))));
            case JavaOp.NotOp not -> define(op, b.binary(BinaryOp.XOR, v(not.operands().get(0)), b.constInt(Type.I1, 1)));
            case JavaOp.ConvOp conv -> define(op, convert(b, conv));
            case JavaOp.ArrayAccessOp.ArrayLoadOp load -> define(op, arrayLoad(b, v(load.operands().get(0)), v(load.operands().get(1)), valueType(load.resultType()),
                    descriptor(load.resultType()).equals("C")));
            case JavaOp.ArrayAccessOp.ArrayStoreOp store -> arrayStore(b, v(store.operands().get(0)), v(store.operands().get(1)), v(store.operands().get(2)));
            case JavaOp.ArrayLengthOp length -> define(op, b.arrayLength(v(length.operands().get(0))));
            case JavaOp.FieldAccessOp.FieldLoadOp field -> define(op, fieldLoad(b, field));
            case JavaOp.InvokeOp invoke -> define(op, invoke(b, invoke));
            case CoreOp.BranchOp br -> b.br(blocks.get(br.branch().targetBlock()), args(br.branch().arguments()));
            case CoreOp.ConditionalBranchOp cbr -> b.condBr(v(cbr.predicateOperand()), blocks.get(cbr.trueBranch().targetBlock()), args(cbr.trueBranch().arguments()),
                    blocks.get(cbr.falseBranch().targetBlock()), args(cbr.falseBranch().arguments()));
            case CoreOp.ReturnOp ret -> {
                if (ret.operands().isEmpty()) {
                    b.ret();
                } else {
                    b.ret(v(ret.operands().get(0)));
                }
            }
            default -> throw new Unsupported("TC-E1003", "code model op " + op.getClass().getSimpleName() + " is not supported by the Babylon frontend yet");
        }
    }

    private Inst constant(IrBuilder b, Object value, CodeType type) {
        var t = valueType(type);
        return switch (value) {
            case Boolean z -> b.constInt(Type.I1, z ? 1 : 0);
            case Character ch -> b.constInt(t, ch);
            case Float f -> b.constFloat(t, f);
            case Double d -> b.constFloat(t, d);
            case Number n -> b.constInt(t, n.longValue());
            case null -> throw new Unsupported("TC-E1003", "null constants are not supported in kernels");
            default -> throw new Unsupported("TC-E1003", "constant of type " + value.getClass().getSimpleName() + " is not supported in kernels");
        };
    }

    private Inst binary(IrBuilder b, JavaOp.BinaryOp op) {
        var x = v(op.operands().get(0));
        var y = v(op.operands().get(1));
        boolean f = x.type().isFloat();
        BinaryOp kind = switch (op) {
            case JavaOp.AddOp a -> f ? BinaryOp.FADD : BinaryOp.ADD;
            case JavaOp.SubOp s -> f ? BinaryOp.FSUB : BinaryOp.SUB;
            case JavaOp.MulOp m -> f ? BinaryOp.FMUL : BinaryOp.MUL;
            case JavaOp.DivOp d -> f ? BinaryOp.FDIV : BinaryOp.DIV;
            case JavaOp.ModOp m -> f ? BinaryOp.FREM : BinaryOp.REM;
            case JavaOp.AndOp a -> BinaryOp.AND;
            case JavaOp.OrOp o -> BinaryOp.OR;
            case JavaOp.XorOp xo -> BinaryOp.XOR;
            case JavaOp.LshlOp l -> BinaryOp.SHL;
            case JavaOp.AshrOp a -> BinaryOp.SHR;
            case JavaOp.LshrOp l -> BinaryOp.USHR;
            default -> throw new Unsupported("TC-E1003", "binary op " + op.getClass().getSimpleName() + " is not supported");
        };
        // Java shifts a long by an int count; TC's binary ops take one type.
        if ((kind == BinaryOp.SHL || kind == BinaryOp.SHR || kind == BinaryOp.USHR) && !y.type().equals(x.type())) {
            y = b.convert(x.type().equals(Type.I64) ? ConvertOp.SEXT : ConvertOp.TRUNC, y, x.type());
        }
        return b.binary(kind, x, y);
    }

    private Inst compare(IrBuilder b, JavaOp.CompareOp op) {
        var x = v(op.operands().get(0));
        var y = v(op.operands().get(1));
        if (x.type().isFloat()) {
            FloatPredicate p = switch (op) {
                case JavaOp.LtOp l -> FloatPredicate.OLT;
                case JavaOp.LeOp l -> FloatPredicate.OLE;
                case JavaOp.GtOp g -> FloatPredicate.OGT;
                case JavaOp.GeOp g -> FloatPredicate.OGE;
                case JavaOp.EqOp e -> FloatPredicate.OEQ;
                case JavaOp.NeqOp n -> FloatPredicate.UNE;
                default -> throw new Unsupported("TC-E1003", "comparison " + op.getClass().getSimpleName() + " is not supported");
            };
            return b.fcmp(p, x, y);
        }
        IntPredicate p = switch (op) {
            case JavaOp.LtOp l -> IntPredicate.SLT;
            case JavaOp.LeOp l -> IntPredicate.SLE;
            case JavaOp.GtOp g -> IntPredicate.SGT;
            case JavaOp.GeOp g -> IntPredicate.SGE;
            case JavaOp.EqOp e -> IntPredicate.EQ;
            case JavaOp.NeqOp n -> IntPredicate.NE;
            default -> throw new Unsupported("TC-E1003", "comparison " + op.getClass().getSimpleName() + " is not supported");
        };
        return b.icmp(p, x, y);
    }

    private uk.ac.manchester.tornado.tc.ir.Value convert(IrBuilder b, JavaOp.ConvOp op) {
        var x = v(op.valueOperand());
        var from = x.type();
        var toDescriptor = descriptor(op.resultType());
        var to = valueType(op.resultType());
        if (from.isFloat() && to.isFloat()) {
            return from.equals(to) ? x : b.convert(from.equals(Type.F32) ? ConvertOp.FEXT : ConvertOp.FTRUNC, x, to);
        }
        if (from.isFloat()) {
            // Java saturates and maps NaN to 0, then narrows to byte/short/char like an int.
            var wide = b.convert(ConvertOp.F2I_SAT, x, to.equals(Type.I64) ? Type.I64 : Type.I32);
            return narrow(b, wide, toDescriptor);
        }
        if (to.isFloat()) {
            return b.convert(ConvertOp.I2F, x, to);
        }
        if (from.equals(to)) {
            return narrow(b, x, toDescriptor);
        }
        var c = from.equals(Type.I64) ? b.convert(ConvertOp.TRUNC, x, Type.I32) : b.convert(ConvertOp.SEXT, x, Type.I64);
        return narrow(b, c, toDescriptor);
    }

    /** int to byte, short or char: truncate and extend back, as the JVM's i2b, i2s and i2c. */
    private static uk.ac.manchester.tornado.tc.ir.Value narrow(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value x, String to) {
        return switch (to) {
            case "B" -> b.convert(ConvertOp.SEXT, b.convert(ConvertOp.TRUNC, x, Type.I8), Type.I32);
            case "S" -> b.convert(ConvertOp.SEXT, b.convert(ConvertOp.TRUNC, x, Type.I16), Type.I32);
            case "C" -> b.convert(ConvertOp.ZEXT, b.convert(ConvertOp.TRUNC, x, Type.I16), Type.I32);
            default -> x;
        };
    }

    /** Java arrays, and shared arrays from {@code KernelContext.allocate*LocalArray} (pointers). */
    private uk.ac.manchester.tornado.tc.ir.Value arrayLoad(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value array, uk.ac.manchester.tornado.tc.ir.Value index, Type type, boolean charOrBoolean) {
        uk.ac.manchester.tornado.tc.ir.Value loaded;
        if (array instanceof Inst alloca && alloca.opcode() instanceof Opcode.Alloca(var space, var element, var count)) {
            loaded = b.load(elementAddress(b, array, index, element), element);
        } else {
            loaded = b.arrayLoad(array, index);
        }
        // byte, short and char elements widen to int (char unsigned), as the code model's values are ints.
        if ((loaded.type().equals(Type.I8) || loaded.type().equals(Type.I16)) && type.equals(Type.I32)) {
            return b.convert(charOrBoolean ? ConvertOp.ZEXT : ConvertOp.SEXT, loaded, Type.I32);
        }
        if (loaded.type().equals(Type.I8) && type.equals(Type.I1)) {
            return b.convert(ConvertOp.TRUNC, loaded, Type.I1);
        }
        return loaded;
    }

    private void arrayStore(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value array, uk.ac.manchester.tornado.tc.ir.Value index, uk.ac.manchester.tornado.tc.ir.Value value) {
        if (array instanceof Inst alloca && alloca.opcode() instanceof Opcode.Alloca(var space, var element, var count)) {
            b.store(elementAddress(b, array, index, element), narrow(b, value, element));
            return;
        }
        b.arrayStore(array, index, array.type() instanceof Type.ArrayType(var element) ? narrow(b, value, element) : value);
    }

    /** An int (or boolean) value stored into a narrower element. */
    private static uk.ac.manchester.tornado.tc.ir.Value narrow(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value value, Type memory) {
        if (value.type().equals(memory) || !(memory.equals(Type.I8) || memory.equals(Type.I16))) {
            return value;
        }
        return b.convert(value.type().equals(Type.I1) ? ConvertOp.ZEXT : ConvertOp.TRUNC, value, memory);
    }

    private uk.ac.manchester.tornado.tc.ir.Value elementAddress(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value pointer, uk.ac.manchester.tornado.tc.ir.Value index, Type element) {
        var index64 = index.type().equals(Type.I64) ? index : b.convert(ConvertOp.SEXT, index, Type.I64);
        return b.pointerOffset(pointer, noSignedWrap(b.binary(BinaryOp.MUL, index64, b.constInt(Type.I64, bytes(element)))));
    }

    private uk.ac.manchester.tornado.tc.ir.Value fieldLoad(IrBuilder b, JavaOp.FieldAccessOp.FieldLoadOp op) {
        var ref = op.fieldReference();
        var api = Frontend.apiField(b, ref.refType().toString(), ref.name());
        if (api.isPresent()) {
            return api.get().value().orElseThrow();
        }
        if (!op.operands().isEmpty()) {
            throw new Unsupported("TC-E1003", "instance field " + ref.name() + " is not supported by the Babylon frontend yet");
        }
        // A static final field: its value, as the bytecode frontend folds computed constants.
        try {
            var owner = context.load((JavaType) ref.refType());
            var field = owner.getDeclaredField(ref.name());
            if (!Modifier.isFinal(field.getModifiers()) || !field.getType().isPrimitive()) {
                throw new Unsupported("TC-E1003", "static field " + ref.name() + " is not a primitive constant");
            }
            field.setAccessible(true);
            return constant(b, field.get(null), ref.type());
        } catch (ReflectiveOperationException e) {
            throw new Unsupported("TC-E1003", "field " + ref.name() + " cannot be read: " + e);
        }
    }

    private uk.ac.manchester.tornado.tc.ir.Value invoke(IrBuilder b, JavaOp.InvokeOp op) {
        MethodRef ref = op.invokeReference();
        var owner = ref.refType().toString();
        var name = ref.name();
        var operands = op.operands();
        if (owner.equals(HAT_KERNEL_CONTEXT)) {
            return hatKernelContext(b, name);
        }
        if (HAT_F16_OWNERS.contains(owner)) {
            return hatF16(b, op, name);
        }
        if (op.hasReceiver() && views.containsKey(operands.get(0))) {
            return fieldAccess(b, op, views.get(operands.get(0)), name);
        }
        if (op.hasReceiver() && isHatBuffer(ref.refType())) {
            return hatBufferAccess(b, op, name);
        }
        if (op.hasReceiver() && deviceArrays.containsKey(v(operands.get(0)))) {
            return deviceArrayAccess(b, op, name);
        }
        if (!op.hasReceiver() && (name.equals("createLocal") || name.equals("createPrivate")) && operands.isEmpty()) {
            return deviceArray(b, ref, name.equals("createLocal") ? AddressSpace.SHARED : AddressSpace.PRIVATE);
        }
        // The TornadoVM API (native arrays, KernelContext, TornadoMath) and the JDK methods it binds
        // (boxing, Math, Float bits): lowered as the bytecode frontend lowers them.
        var signature = ref.signature();
        var descriptor = signature.parameterTypes().stream().map(BabylonTranslator::descriptor).collect(java.util.stream.Collectors.joining("", "(", ")"))
                + descriptor(signature.returnType());
        var api = Frontend.apiCall(b, owner, name, descriptor, !op.hasReceiver(), args(operands));
        if (api.isPresent()) {
            return api.get().value().orElse(null);
        }
        if (owner.startsWith("uk.ac.manchester.tornado.api.")) {
            throw new Unsupported("TC-E1003", "TornadoVM API call " + owner + "." + name + descriptor + " is not supported by the Babylon frontend yet");
        }
        if (owner.equals("hat.HATMath")) {
            // cosf/cosd/native_cosf -> math.cos at the argument's precision, as HAT's own backends map
            // them; compiling their Java bodies would compute in double (Math.cos).
            var base = name.replaceFirst("^native_", "").replaceFirst("(f|d)$", "");
            if (java.util.Set.of("max", "min", "cos", "sin", "tan", "exp", "sqrt").contains(base)) {
                return math(b, base, args(operands), valueType(op.resultType()));
            }
        }
        if (owner.equals("java.lang.Math")) {
            return math(b, name, args(operands), valueType(op.resultType()));
        }
        if (op.hasReceiver()) {
            throw new Unsupported("TC-E1003", "instance call " + owner + "." + name + " is not supported by the Babylon frontend yet");
        }
        // A static call into user code: a device function, translated from its model or its bytecode.
        try {
            var method = resolve(ref);
            var calleeDescriptor = java.lang.invoke.MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
            var calleeName = Frontend.calleeName(method.getDeclaringClass().getName().replace('.', '/'), name, calleeDescriptor);
            // HAT buffers passed on keep their layout: the callee reads them at the same offsets.
            for (int i = 0; i < operands.size(); i++) {
                var index = bufferParams.get(v(operands.get(i)));
                var layout = index == null ? null : context.bufferLayout(index);
                if (layout != null) {
                    HatLayouts.record(method, i, layout);
                }
            }
            context.callee(method, calleeName);
            return b.call(calleeName, valueType(op.resultType()), args(operands));
        } catch (ReflectiveOperationException e) {
            throw new Unsupported("TC-E1007", "called method " + owner + "." + name + " cannot be resolved: " + e);
        }
    }

    /** A method named in a model, resolved with access to its declaring class (kernels often sit in private nested types). */
    private Method resolve(MethodRef ref) throws ReflectiveOperationException {
        var owner = context.load((JavaType) ref.refType());
        var lookup = owner == null ? java.lang.invoke.MethodHandles.lookup() : java.lang.invoke.MethodHandles.privateLookupIn(owner, java.lang.invoke.MethodHandles.lookup());
        return ref.resolveToMethod(lookup);
    }

    /**
     * {@code T.createLocal()} / {@code T.createPrivate()} for a HAT device type: a fixed-size shared or
     * private array, sized by the type's {@code deviceSchema} (one primitive array member, e.g.
     * {@code array("array", 256)}). Read reflectively, so this class does not link against HAT.
     */
    private uk.ac.manchester.tornado.tc.ir.Value deviceArray(IrBuilder b, MethodRef ref, AddressSpace space) {
        var owner = context.load((JavaType) ref.refType());
        try {
            var schemaField = owner.getDeclaredField("deviceSchema");
            schemaField.setAccessible(true);
            var schema = schemaField.get(null);
            var root = schema.getClass().getField("root").get(schema);
            var members = (List<?>) root.getClass().getMethod("members").invoke(root);
            if (members.size() != 1 || !members.get(0).getClass().getSimpleName().equals("Array")) {
                throw new Unsupported("TC-E1003", "HAT device type " + owner.getSimpleName() + ": only a schema with one array member is supported yet");
            }
            var member = members.get(0);
            var clazz = (Class<?>) member.getClass().getMethod("clazz").invoke(member);
            int size = (int) member.getClass().getMethod("size").invoke(member);
            if (!clazz.isPrimitive() && !clazz.getName().equals("hat.types.F16")) {
                throw new Unsupported("TC-E1003", "HAT device type " + owner.getSimpleName() + ": array of " + clazz.getSimpleName() + " is not supported yet");
            }
            var element = memoryType(clazz);
            var array = b.alloca(space, element, size);
            deviceArrays.put(array, element);
            return array;
        } catch (ReflectiveOperationException | NullPointerException e) {
            throw new Unsupported("TC-E1003", "HAT device type " + ref.refType() + " has no readable deviceSchema: " + e);
        }
    }

    /** {@code a.array(i)} / {@code a.array(i, v)} on HAT local or private memory. */
    private uk.ac.manchester.tornado.tc.ir.Value deviceArrayAccess(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var array = v(op.operands().get(0));
        var element = deviceArrays.get(array);
        var args = op.operands().subList(1, op.operands().size());
        if (!name.equals("array") || args.isEmpty() || args.size() > 2) {
            throw new Unsupported("TC-E1003", "HAT device memory accessor " + name + " is not supported yet");
        }
        var address = elementAddress(b, array, v(args.get(0)), element);
        if (args.size() == 1) {
            elementAddresses.put(op.result(), address);
            var loaded = b.load(address, element);
            return element.equals(Type.I8) || element.equals(Type.I16) ? b.convert(ConvertOp.SEXT, loaded, Type.I32) : loaded;
        }
        b.store(address, narrow(b, v(args.get(1)), element));
        return null;
    }

    /** HAT half floats: TC f16 values; arithmetic in f32, rounded back, as the bytecode frontend does for HalfFloat. */
    private uk.ac.manchester.tornado.tc.ir.Value hatF16(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var args = args(op.operands());
        if (op.hasReceiver() && views.containsKey(op.operands().get(0))) {
            // value() / value(short) on an element in memory
            return fieldAccess(b, op, views.get(op.operands().get(0)), name);
        }
        if (op.hasReceiver() && name.equals("value") && elementAddresses.containsKey(op.operands().get(0))) {
            var address = elementAddresses.get(op.operands().get(0));
            if (args.size() == 1) {
                return b.convert(ConvertOp.SEXT, b.load(address, Type.I16), Type.I32);
            }
            var bits = args.get(1);
            b.store(address, bits.type().equals(Type.I16) ? bits : b.convert(ConvertOp.TRUNC, bits, Type.I16));
            return null;
        }
        if (op.hasReceiver() && name.equals("value") && args.size() == 1) {
            return b.convert(ConvertOp.SEXT, b.convert(ConvertOp.BITCAST, args.get(0), Type.I16), Type.I32);
        }
        var binary = switch (name) {
            case "add" -> BinaryOp.FADD;
            case "sub" -> BinaryOp.FSUB;
            case "mul" -> BinaryOp.FMUL;
            case "div" -> BinaryOp.FDIV;
            default -> null;
        };
        if (binary != null && args.size() == 2) {
            return b.convert(ConvertOp.FTRUNC, b.binary(binary, toF32(b, args.get(0)), toF32(b, args.get(1))), Type.F16);
        }
        if (name.equals("f16ToFloat") && args.size() == 1) {
            return toF32(b, args.get(0));
        }
        if ((name.equals("floatToF16") || name.equals("of")) && args.size() == 1 && args.get(0).type().equals(Type.F32)) {
            return b.convert(ConvertOp.FTRUNC, args.get(0), Type.F16);
        }
        if (name.equals("of") && args.size() == 1) {
            return b.convert(ConvertOp.BITCAST, b.convert(ConvertOp.TRUNC, args.get(0), Type.I16), Type.F16);
        }
        throw new Unsupported("TC-E1003", "hat.types.F16." + name + " is not supported yet");
    }

    private static uk.ac.manchester.tornado.tc.ir.Value toF32(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value v) {
        return v.type().equals(Type.F16) ? b.convert(ConvertOp.FEXT, v, Type.F32) : v;
    }

    private Inst hatKernelContext(IrBuilder b, String name) {
        if (name.equals("barrier")) {
            return b.intrinsic("tornado.barrier.local", Type.VOID, List.of());
        }
        if (name.length() == 3) {
            int dim = "XYZ".indexOf(name.charAt(2));
            String intrinsic = switch (name.substring(0, 2)) {
                case "GI" -> "tornado.thread_id.global";
                case "GS" -> "tornado.global_size";
                case "LI" -> "tornado.thread_id.local";
                case "LS" -> "tornado.group_size.local";
                case "BI" -> "tornado.group_id";
                case "BS" -> "tornado.group_count";
                default -> null;
            };
            if (dim >= 0 && intrinsic != null) {
                return b.intrinsic(intrinsic, Immediates.of("dim", dim), Type.I32, List.of());
            }
        }
        throw new Unsupported("TC-E1003", "hat.KernelContext." + name + " is not supported yet");
    }

    /**
     * A HAT buffer accessor on a parameter: the field it names in the buffer's bound layout, loaded or
     * stored at its byte offset in the parameter's ByteArray.
     */
    private uk.ac.manchester.tornado.tc.ir.Value hatBufferAccess(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var receiver = v(op.operands().get(0));
        var index = bufferParams.get(receiver);
        var layout = index == null ? null : context.bufferLayout(index);
        if (layout == null) {
            throw new Unsupported("TC-E1003", "HAT buffer access " + name + "() on a value that is not a parameter with a known layout");
        }
        return fieldAccess(b, op, new View(receiver, b.constInt(Type.I64, 0), layout), name);
    }

    /**
     * A struct inside a HAT buffer ({@code input.complex(k)}): no value of its own, only the buffer, the
     * struct's byte offset in it and its layout. Its accessors ({@code .real()}) read and write there.
     */
    private record View(uk.ac.manchester.tornado.tc.ir.Value buffer, uk.ac.manchester.tornado.tc.ir.Value offset, GroupLayout layout) {
    }

    private uk.ac.manchester.tornado.tc.ir.Value fieldAccess(IrBuilder b, JavaOp.InvokeOp op, View view, String name) {
        var layout = view.layout();
        MemoryLayout field;
        long fieldOffset;
        try {
            field = layout.select(MemoryLayout.PathElement.groupElement(name));
            fieldOffset = layout.byteOffset(MemoryLayout.PathElement.groupElement(name));
        } catch (IllegalArgumentException e) {
            throw new Unsupported("TC-E1003", "HAT type " + layout.name().orElse("?") + " has no field " + name);
        }
        var base = noSignedWrap(b.add(view.offset(), b.constInt(Type.I64, fieldOffset)));
        var args = op.operands().subList(1, op.operands().size());
        if (field instanceof SequenceLayout seq && !args.isEmpty()) {
            var i = v(args.get(0));
            var index64 = i.type().equals(Type.I64) ? i : b.convert(ConvertOp.SEXT, i, Type.I64);
            // byte offsets of a sign-extended int index cannot overflow i64: plain signed arithmetic
            var offset = noSignedWrap(b.add(base, noSignedWrap(b.binary(BinaryOp.MUL, index64, b.constInt(Type.I64, seq.elementLayout().byteSize())))));
            return element(b, op, new View(view.buffer(), offset, null), seq.elementLayout(), args.subList(1, args.size()), name);
        }
        return element(b, op, new View(view.buffer(), base, null), field, args, name);
    }

    /** The element at {@code at.offset()}: a value loaded or stored, or a struct view for later accessors. */
    private uk.ac.manchester.tornado.tc.ir.Value element(IrBuilder b, JavaOp.InvokeOp op, View at, MemoryLayout element, List<Value> rest, String name) {
        if (element instanceof ValueLayout scalar) {
            var memory = memoryType(scalar);
            if (rest.isEmpty()) {
                var loaded = b.arrayLoad(at.buffer(), at.offset(), memory);
                // Sub-int fields widen to int, as Java values do (char unsigned, others signed).
                return memory.equals(Type.I8) || memory.equals(Type.I16)
                        ? b.convert(scalar.carrier() == char.class || scalar.carrier() == boolean.class ? ConvertOp.ZEXT : ConvertOp.SEXT, loaded, Type.I32)
                        : loaded;
            }
            var value = v(rest.get(0));
            b.arrayStore(at.buffer(), at.offset(), value.type().equals(memory) ? value
                    : b.convert(value.type().equals(Type.I1) ? ConvertOp.ZEXT : ConvertOp.TRUNC, value, memory));
            return null;
        }
        if (element instanceof GroupLayout group && rest.isEmpty()) {
            views.put(op.result(), new View(at.buffer(), at.offset(), group));
            if (op.resultType() instanceof JavaType j && HAT_F16_TYPES.contains(descriptor(j))) {
                // A half float ({short value}): also its value, for F16 arithmetic. Unused, it is dead code.
                return b.convert(ConvertOp.BITCAST, b.arrayLoad(at.buffer(), at.offset(), Type.I16), Type.F16);
            }
            return null;
        }
        throw new Unsupported("TC-E1003", "HAT field " + name + " (" + element + ") is not supported yet");
    }

    private static uk.ac.manchester.tornado.tc.ir.Inst noSignedWrap(uk.ac.manchester.tornado.tc.ir.Inst inst) {
        inst.addAttribute(uk.ac.manchester.tornado.tc.ir.Attribute.of("nsw"));
        return inst;
    }

    private static int bytes(Type t) {
        return t.equals(Type.I64) || t.equals(Type.F64) ? 8 : t.equals(Type.I16) || t.equals(Type.F16) ? 2 : t.equals(Type.I8) ? 1 : 4;
    }

    private static Type memoryType(ValueLayout layout) {
        return memoryType(layout.carrier());
    }

    private static Type memoryType(Class<?> c) {
        if (c.getName().equals("hat.types.F16")) {
            return Type.F16;
        }
        if (c == int.class) {
            return Type.I32;
        } else if (c == long.class) {
            return Type.I64;
        } else if (c == float.class) {
            return Type.F32;
        } else if (c == double.class) {
            return Type.F64;
        } else if (c == short.class || c == char.class) {
            return Type.I16;
        } else if (c == byte.class || c == boolean.class) {
            return Type.I8;
        }
        throw new Unsupported("TC-E1003", "HAT buffer element " + c + " is not supported");
    }

    private static Inst math(IrBuilder b, String name, List<uk.ac.manchester.tornado.tc.ir.Value> args, Type type) {
        return switch (name) {
            case "sqrt", "exp", "log", "sin", "cos", "tan", "tanh", "abs", "floor", "ceil", "rint", "atan", "asin", "acos", "min", "max", "pow", "fma", "atan2" ->
                b.intrinsic("math." + name, type, args);
            default -> throw new Unsupported("TC-E1003", "Math." + name + " is not supported by the Babylon frontend yet");
        };
    }
}
