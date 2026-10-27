/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    /** HAT's bfloat16: an int whose low 16 bits are the bfloat16's bits. */
    private static final java.util.Set<String> HAT_BF16_TYPES = java.util.Set.of("Lhat/types/BF16;", "Lhat/buffer/BF16Array$BF16Impl;");
    private static final java.util.Set<String> HAT_BF16_OWNERS = java.util.Set.of("hat.types.BF16", "hat.buffer.BF16Array$BF16Impl");
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
    /**
     * HAT vectors that kernels mutate ({@code vA.x(v)} on a {@code Float4.MutableImpl}): each lives in a
     * private slot, read on every use, so a mutation is seen wherever the Java reference is.
     */
    private final Map<Value, uk.ac.manchester.tornado.tc.ir.Value> vectorSlots = new HashMap<>();
    /** The builder of the op being translated: reading a mutable vector loads its slot there. */
    private IrBuilder current;
    /** HAT local/private memory of a struct-shaped device type: its layout, for the accessors' views. */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, GroupLayout> deviceStructs = new HashMap<>();
    /** Results of {@code arrayView()} calls (Java arrays over HAT memory) and of indexing a 2D one by its first index. */
    private final Map<Value, ArrayView> arrayViews = new HashMap<>();
    private final Map<Value, Row> rows = new HashMap<>();
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
        // Mutated HAT vectors get their private slots first, in the entry block.
        var entryBuilder = new IrBuilder(blocks.get(entry));
        model.elements().forEach(e -> {
            if (e instanceof JavaOp.InvokeOp invoke && invoke.hasReceiver() && invoke.operands().size() == 2
                    && vectorLanes(invoke.operands().get(0).type()) > 0 && lane(invoke.invokeReference().name()) >= 0) {
                var vector = invoke.operands().get(0);
                vectorSlots.computeIfAbsent(vector, x -> entryBuilder.alloca(AddressSpace.PRIVATE, Type.F32, vectorLanes(x.type())));
            }
        });
        for (var block : body.blocks()) {
            var b = new IrBuilder(blocks.get(block));
            current = b;
            for (var p : block.parameters()) {
                if (vectorSlots.containsKey(p)) {
                    storeVector(b, vectorSlots.get(p), values.remove(p));
                }
            }
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
            case String d when HAT_BF16_TYPES.contains(d) -> Type.I32;
            case String d when vectorLanes(d) > 0 -> new Type.VectorType(Type.F32, vectorLanes(d));
            default -> Frontend.typeOf(descriptor(type));
        };
    }

    // ---- ops

    private uk.ac.manchester.tornado.tc.ir.Value v(Value value) {
        if (vectorSlots.containsKey(value) && !values.containsKey(value)) {
            return loadVector(current, vectorSlots.get(value), vectorLanes(value.type()));
        }
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
            if (vectorSlots.containsKey(op.result())) {
                storeVector(current, vectorSlots.get(op.result()), value);
            } else {
                values.put(op.result(), value);
            }
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
            case JavaOp.ArrayAccessOp.ArrayLoadOp load when isView(load.operands().get(0)) -> define(op, viewLoad(b, load));
            case JavaOp.ArrayAccessOp.ArrayStoreOp store when isView(store.operands().get(0)) -> viewStore(b, store);
            case JavaOp.ArrayLengthOp length when arrayViews.containsKey(length.operands().get(0)) -> define(op, viewLength(b, arrayViews.get(length.operands().get(0))));
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
        if (HAT_BF16_OWNERS.contains(owner)) {
            return hatBF16(b, op, name);
        }
        if (VECTOR_OWNERS.contains(owner) || (op.hasReceiver() && vectorLanes(operands.get(0).type()) > 0)) {
            return hatVector(b, op, owner, name);
        }
        if (op.hasReceiver() && isHatBuffer(ref.refType()) && vectorView(name) > 0) {
            return hatVectorView(b, op, name);
        }
        if (op.hasReceiver() && name.toLowerCase(java.util.Locale.ROOT).contains("arrayview") && operands.size() == 1) {
            arrayViews.put(op.result(), arrayView(b, op, name));
            return null;
        }
        if (op.hasReceiver() && views.containsKey(operands.get(0))) {
            return fieldAccess(b, op, views.get(operands.get(0)), name);
        }
        if (op.hasReceiver() && isHatBuffer(ref.refType())) {
            return hatBufferAccess(b, op, name);
        }
        if (op.hasReceiver() && values.get(operands.get(0)) instanceof uk.ac.manchester.tornado.tc.ir.Value memory && deviceStructs.containsKey(memory)) {
            return fieldAccess(b, op, new View(memory, b.constInt(Type.I64, 0), deviceStructs.get(memory)), name);
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
        if (owner.equals("hat.HATMath") && name.endsWith("f16")) {
            var base = name.substring(0, name.length() - 3);
            var floats = args(operands).stream().map(x -> toF32(b, x)).toList();
            return b.convert(ConvertOp.FTRUNC, math(b, base, floats, Type.F32), Type.F16);
        }
        if (owner.equals("hat.HATMath") && (name.equals("min") || name.equals("max"))) {
            return math(b, name, args(operands), valueType(op.resultType()));
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
            if (members.size() == 1 && members.get(0).getClass().getSimpleName().equals("Array")) {
                var member = members.get(0);
                var clazz = (Class<?>) member.getClass().getMethod("clazz").invoke(member);
                int size = (int) member.getClass().getMethod("size").invoke(member);
                var nested = (List<?>) member.getClass().getMethod("members").invoke(member);
                if (nested.isEmpty() && (clazz.isPrimitive() || clazz.getName().equals("hat.types.F16") || clazz.getName().equals("hat.types.BF16"))) {
                    var element = memoryType(clazz);
                    var array = b.alloca(space, element, size);
                    deviceArrays.put(array, element);
                    return array;
                }
            }
            // Any other schema (structs, arrays of structs): memory laid out as its MemoryLayout, read through views.
            var layout = DeviceLayouts.of(owner);
            long words = (layout.byteSize() + 7) / 8;
            var memory = b.alloca(space, Type.I64, (int) Math.max(1, words));
            deviceStructs.put(memory, layout);
            return memory;
        } catch (ReflectiveOperationException | NullPointerException e) {
            throw new Unsupported("TC-E1003", "HAT device type " + ref.refType() + " has no readable deviceSchema: " + e);
        }
    }

    /** {@code a.array(i)} / {@code a.array(i, v)} on HAT local or private memory. */
    private uk.ac.manchester.tornado.tc.ir.Value deviceArrayAccess(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var array = v(op.operands().get(0));
        var element = deviceArrays.get(array);
        var args = op.operands().subList(1, op.operands().size());
        if (vectorView(name) > 0) {
            var type = new Type.VectorType(element, vectorView(name));
            if (name.startsWith("store")) {
                storeDeviceVector(b, elementAddress(b, array, v(args.get(1)), element), type, v(args.get(0)));
                return null;
            }
            return loadDeviceVector(b, elementAddress(b, array, v(args.get(0)), element), type);
        }
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

    /**
     * A Java array over HAT memory, from {@code buffer.arrayView()} or a device type's
     * {@code ...ArrayView()}: indexing it reads and writes the memory, as HAT's own backends do. The
     * memory is a HAT buffer's array (its last field; a 2D view strides by the first field, e.g.
     * {@code width}) or HAT local or private memory.
     */
    private record ArrayView(uk.ac.manchester.tornado.tc.ir.Value base, boolean device, Type element, boolean unsigned, long offset, long length, String stride, GroupLayout layout,
            int dimensions) {
    }

    /** {@code view2d[i]}: a row of a 2D view, indexed next by {@code j}: element {@code j * stride + i}. */
    private record Row(ArrayView view, uk.ac.manchester.tornado.tc.ir.Value first) {
    }

    private boolean isView(Value value) {
        return arrayViews.containsKey(value) || rows.containsKey(value);
    }

    private ArrayView arrayView(IrBuilder b, JavaOp.InvokeOp op, String name) {
        int dimensions = op.resultType() instanceof jdk.incubator.code.dialect.java.ArrayType at ? at.dimensions() : 0;
        if (dimensions < 1 || dimensions > 2) {
            throw new Unsupported("TC-E1003", "HAT " + name + "() must return a 1D or 2D Java array");
        }
        var receiver = v(op.operands().get(0));
        if (deviceArrays.containsKey(receiver)) {
            if (dimensions != 1) {
                throw new Unsupported("TC-E1003", "2D views of HAT local or private memory are not supported");
            }
            Type element = deviceArrays.get(receiver);
            if (op.resultType() instanceof jdk.incubator.code.dialect.java.ArrayType at && vectorLanes(at.componentType()) > 0) {
                element = new Type.VectorType(element, vectorLanes(at.componentType()));
            }
            return new ArrayView(receiver, true, element, false, 0, -1, null, null, 1);
        }
        var index = bufferParams.get(receiver);
        var layout = index == null ? null : context.bufferLayout(index);
        if (layout == null) {
            throw new Unsupported("TC-E1003", "HAT " + name + "() on a value that is not a buffer parameter with a known layout");
        }
        var members = layout.memberLayouts();
        if (members.isEmpty() || !(members.getLast() instanceof SequenceLayout array) || !(array.elementLayout() instanceof ValueLayout element)) {
            throw new Unsupported("TC-E1003", "HAT " + name + "(): the buffer's last field is not an array of primitives");
        }
        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(array.name().orElseThrow()));
        String stride = null;
        if (dimensions == 2) {
            if (members.size() < 2 || members.getFirst().name().isEmpty()) {
                throw new Unsupported("TC-E1003", "HAT " + name + "(): a 2D view needs a stride field before the array");
            }
            stride = members.getFirst().name().get();
        }
        boolean unsigned = element.carrier() == char.class || element.carrier() == boolean.class;
        var elementType = op.resultType() instanceof jdk.incubator.code.dialect.java.ArrayType at && vectorLanes(at.componentType()) > 0
                ? new Type.VectorType(memoryType(element), vectorLanes(at.componentType()))
                : memoryType(element);
        return new ArrayView(receiver, false, elementType, unsigned, offset, array.elementCount(), stride, layout, dimensions);
    }

    private uk.ac.manchester.tornado.tc.ir.Value viewLoad(IrBuilder b, JavaOp.ArrayAccessOp.ArrayLoadOp load) {
        var array = load.operands().get(0);
        var index = v(load.operands().get(1));
        if (arrayViews.get(array) instanceof ArrayView view && view.dimensions() == 2) {
            rows.put(load.result(), new Row(view, index));
            return null;
        }
        var view = arrayViews.containsKey(array) ? arrayViews.get(array) : rows.get(array).view();
        if (view.element() instanceof Type.VectorType vec) {
            var at = viewAddress(b, scalarView(view), null, index);
            return view.device() ? loadDeviceVector(b, at, vec) : loadVectorAt(b, view.base(), at, vec);
        }
        var address = viewAddress(b, view, rows.get(array), index);
        var loaded = view.device() ? b.load(address, view.element()) : b.arrayLoad(view.base(), address, view.element());
        var type = valueType(load.resultType());
        if ((loaded.type().equals(Type.I8) || loaded.type().equals(Type.I16)) && type.equals(Type.I32)) {
            return b.convert(view.unsigned() ? ConvertOp.ZEXT : ConvertOp.SEXT, loaded, Type.I32);
        }
        if (loaded.type().equals(Type.I8) && type.equals(Type.I1)) {
            return b.convert(ConvertOp.TRUNC, loaded, Type.I1);
        }
        return loaded;
    }

    private void viewStore(IrBuilder b, JavaOp.ArrayAccessOp.ArrayStoreOp store) {
        var array = store.operands().get(0);
        if (arrayViews.get(array) instanceof ArrayView view && view.dimensions() == 2) {
            throw new Unsupported("TC-E1003", "storing a row into a 2D HAT array view is not supported");
        }
        var view = arrayViews.containsKey(array) ? arrayViews.get(array) : rows.get(array).view();
        if (view.element() instanceof Type.VectorType vec) {
            var at = viewAddress(b, scalarView(view), null, v(store.operands().get(1)));
            if (view.device()) {
                storeDeviceVector(b, at, vec, v(store.operands().get(2)));
            } else {
                storeVectorAt(b, view.base(), at, vec, v(store.operands().get(2)));
            }
            return;
        }
        var address = viewAddress(b, view, rows.get(array), v(store.operands().get(1)));
        var value = narrow(b, v(store.operands().get(2)), view.element());
        if (view.device()) {
            b.store(address, value);
        } else {
            b.arrayStore(view.base(), address, value);
        }
    }

    /** The element's address (device memory) or byte offset in the buffer: {@code [j * stride + i]} for a row. */
    private uk.ac.manchester.tornado.tc.ir.Value viewAddress(IrBuilder b, ArrayView view, Row row, uk.ac.manchester.tornado.tc.ir.Value index) {
        var linear = index64(b, index);
        if (row != null) {
            var stride = b.arrayLoad(view.base(), b.constInt(Type.I64, view.layout().byteOffset(MemoryLayout.PathElement.groupElement(view.stride()))),
                    memoryType((ValueLayout) view.layout().select(MemoryLayout.PathElement.groupElement(view.stride()))));
            linear = noSignedWrap(b.add(noSignedWrap(b.binary(BinaryOp.MUL, linear, index64(b, stride))), index64(b, row.first())));
        }
        if (view.device()) {
            return elementAddress(b, view.base(), linear, view.element());
        }
        return noSignedWrap(b.add(b.constInt(Type.I64, view.offset()), noSignedWrap(b.binary(BinaryOp.MUL, linear, b.constInt(Type.I64, bytes(view.element()))))));
    }

    private uk.ac.manchester.tornado.tc.ir.Value viewLength(IrBuilder b, ArrayView view) {
        if (view.device()) {
            var alloca = (Inst) view.base();
            return b.constInt(Type.I32, ((Opcode.Alloca) alloca.opcode()).count());
        }
        // HAT's backends read the buffer's length field; the bound layout has the same count.
        return b.constInt(Type.I32, view.dimensions() == 1 ? view.length() : -1);
    }

    private static uk.ac.manchester.tornado.tc.ir.Value index64(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value i) {
        if (i.type().equals(Type.I64)) {
            return i;
        }
        return b.convert(ConvertOp.SEXT, i.type().equals(Type.I32) ? i : b.convert(ConvertOp.SEXT, i, Type.I32), Type.I64);
    }

    // ---- HAT vectors: Float2, Float4 (hat.types), as TC vectors of f32

    private static final java.util.Set<String> VECTOR_OWNERS = java.util.Set.of("hat.types.Float4", "hat.types.Float4$MutableImpl", "hat.types.Float4$ImmutableImpl",
            "hat.types.Float2", "hat.types.Float2$MutableImpl", "hat.types.Float2$ImmutableImpl");

    /** Lanes of a HAT vector type (descriptor or code type), 0 if it is not one. */
    private static int vectorLanes(String descriptor) {
        return switch (descriptor) {
            case "Lhat/types/Float4;", "Lhat/types/Float4$MutableImpl;", "Lhat/types/Float4$ImmutableImpl;" -> 4;
            case "Lhat/types/Float2;", "Lhat/types/Float2$MutableImpl;", "Lhat/types/Float2$ImmutableImpl;" -> 2;
            default -> 0;
        };
    }

    private static int vectorLanes(CodeType type) {
        return type instanceof JavaType j && !j.toNominalDescriptor().isPrimitive() ? vectorLanes(descriptor(j)) : 0;
    }

    private static int lane(String name) {
        return name.length() == 1 ? "xyzw".indexOf(name.charAt(0)) : -1;
    }

    /** {@code float4View}, {@code storeFloat4View}, {@code float2View}, ...: the lanes, or 0. */
    private static int vectorView(String name) {
        return switch (name) {
            case "float4View", "storeFloat4View" -> 4;
            case "float2View", "storeFloat2View" -> 2;
            default -> 0;
        };
    }

    private static ArrayView scalarView(ArrayView view) {
        return new ArrayView(view.base(), view.device(), ((Type.VectorType) view.element()).element(), false, view.offset(), view.length(), null, view.layout(), 1);
    }

    private static uk.ac.manchester.tornado.tc.ir.Value laneOf(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value vector, int lane) {
        return b.intrinsic("tornado.vector.lane", Immediates.of("lane", lane), ((Type.VectorType) vector.type()).element(), List.of(vector));
    }

    private static uk.ac.manchester.tornado.tc.ir.Value make(IrBuilder b, Type.VectorType type, List<uk.ac.manchester.tornado.tc.ir.Value> lanes) {
        return b.intrinsic("tornado.vector.make", type, lanes);
    }

    private static uk.ac.manchester.tornado.tc.ir.Value loadVector(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value slot, int lanes) {
        var values = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (int l = 0; l < lanes; l++) {
            values.add(b.load(b.pointerOffset(slot, b.constInt(Type.I64, 4L * l)), Type.F32));
        }
        return make(b, new Type.VectorType(Type.F32, lanes), values);
    }

    private static void storeVector(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value slot, uk.ac.manchester.tornado.tc.ir.Value vector) {
        int lanes = ((Type.VectorType) vector.type()).length();
        for (int l = 0; l < lanes; l++) {
            b.store(b.pointerOffset(slot, b.constInt(Type.I64, 4L * l)), laneOf(b, vector, l));
        }
    }

    /** The vector whose first lane is at byte offset {@code at} of a buffer. */
    private static uk.ac.manchester.tornado.tc.ir.Value loadVectorAt(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value buffer, uk.ac.manchester.tornado.tc.ir.Value at, Type.VectorType type) {
        var values = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (int l = 0; l < type.length(); l++) {
            values.add(b.arrayLoad(buffer, l == 0 ? at : noSignedWrap(b.add(at, b.constInt(Type.I64, (long) bytes(type.element()) * l))), type.element()));
        }
        return make(b, type, values);
    }

    private static void storeVectorAt(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value buffer, uk.ac.manchester.tornado.tc.ir.Value at, Type.VectorType type,
            uk.ac.manchester.tornado.tc.ir.Value vector) {
        for (int l = 0; l < type.length(); l++) {
            b.arrayStore(buffer, l == 0 ? at : noSignedWrap(b.add(at, b.constInt(Type.I64, (long) bytes(type.element()) * l))), laneOf(b, vector, l));
        }
    }

    /** A vector in HAT local or private memory, from the address of its first lane. */
    private static uk.ac.manchester.tornado.tc.ir.Value loadDeviceVector(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value address, Type.VectorType type) {
        var values = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (int l = 0; l < type.length(); l++) {
            values.add(b.load(l == 0 ? address : b.pointerOffset(address, b.constInt(Type.I64, (long) bytes(type.element()) * l)), type.element()));
        }
        return make(b, type, values);
    }

    private static void storeDeviceVector(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value address, Type.VectorType type, uk.ac.manchester.tornado.tc.ir.Value vector) {
        for (int l = 0; l < type.length(); l++) {
            b.store(l == 0 ? address : b.pointerOffset(address, b.constInt(Type.I64, (long) bytes(type.element()) * l)), laneOf(b, vector, l));
        }
    }

    /** {@code buffer.float4View(i)} and {@code buffer.storeFloat4View(v, i)}: lanes {@code i .. i+3} of the buffer's array. */
    private uk.ac.manchester.tornado.tc.ir.Value hatVectorView(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var receiver = v(op.operands().get(0));
        var index = bufferParams.get(receiver);
        var layout = index == null ? null : context.bufferLayout(index);
        if (layout == null || !(layout.memberLayouts().getLast() instanceof SequenceLayout array) || !(array.elementLayout() instanceof ValueLayout element)) {
            throw new Unsupported("TC-E1003", "HAT " + name + "() on a value that is not a buffer parameter with a known array layout");
        }
        var type = new Type.VectorType(memoryType(element), vectorView(name));
        var view = new ArrayView(receiver, false, type.element(), false, layout.byteOffset(MemoryLayout.PathElement.groupElement(array.name().orElseThrow())), array.elementCount(),
                null, layout, 1);
        if (name.startsWith("store")) {
            storeVectorAt(b, receiver, viewAddress(b, view, null, v(op.operands().get(2))), type, v(op.operands().get(1)));
            return null;
        }
        return loadVectorAt(b, receiver, viewAddress(b, view, null, v(op.operands().get(1))), type);
    }

    /** Float2/Float4: construction, lanes, lane updates (mutable vectors) and lane-wise arithmetic. */
    private uk.ac.manchester.tornado.tc.ir.Value hatVector(IrBuilder b, JavaOp.InvokeOp op, String owner, String name) {
        var operands = op.operands();
        var args = args(operands);
        int lane = lane(name);
        if (op.hasReceiver() && lane >= 0 && args.size() == 1) {
            return laneOf(b, args.get(0), lane);
        }
        if (op.hasReceiver() && lane >= 0 && args.size() == 2) {
            var slot = vectorSlots.get(operands.get(0));
            b.store(b.pointerOffset(slot, b.constInt(Type.I64, 4L * lane)), args.get(1));
            return null;
        }
        var resultLanes = vectorLanes(op.resultType());
        if (name.equals("of") && resultLanes > 0 && args.size() == resultLanes) {
            return make(b, new Type.VectorType(Type.F32, resultLanes), args);
        }
        if (name.equals("makeMutable") && args.size() == 1) {
            return args.get(0);
        }
        BinaryOp kind = switch (name) {
            case "add" -> BinaryOp.FADD;
            case "sub" -> BinaryOp.FSUB;
            case "mul", "sqr" -> BinaryOp.FMUL;
            case "div" -> BinaryOp.FDIV;
            default -> null;
        };
        if (kind != null && resultLanes > 0) {
            var x = args.get(0);
            var y = name.equals("sqr") ? x : args.get(1);
            var type = (Type.VectorType) x.type();
            var lanes = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
            for (int l = 0; l < type.length(); l++) {
                lanes.add(b.binary(kind, laneOf(b, x, l), y.type() instanceof Type.VectorType ? laneOf(b, y, l) : y));
            }
            return make(b, type, lanes);
        }
        throw new Unsupported("TC-E1003", owner + "." + name + " is not supported on HAT vectors yet");
    }

    /**
     * HAT bfloat16s: ints carrying the 16 bits; arithmetic in f32 and rounded back exactly as
     * {@code BF16.of(float)} rounds (to nearest even, in integer arithmetic, so every backend agrees).
     * The static helpers follow their Java bodies.
     */
    private uk.ac.manchester.tornado.tc.ir.Value hatBF16(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var operands = op.operands();
        if (op.hasReceiver() && views.containsKey(operands.get(0))) {
            return fieldAccess(b, op, views.get(operands.get(0)), name);
        }
        var args = args(operands);
        if (op.hasReceiver() && name.equals("value") && elementAddresses.containsKey(operands.get(0))) {
            var address = elementAddresses.get(operands.get(0));
            if (args.size() == 1) {
                return b.convert(ConvertOp.SEXT, b.load(address, Type.I16), Type.I32);
            }
            b.store(address, narrow(b, args.get(1), Type.I16));
            return null;
        }
        if (op.hasReceiver() && name.equals("value") && args.size() == 1) {
            return b.convert(ConvertOp.SEXT, b.convert(ConvertOp.TRUNC, args.get(0), Type.I16), Type.I32);
        }
        var floats = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (int i = 0; i < operands.size(); i++) {
            floats.add(HAT_BF16_TYPES.contains(descriptor(operands.get(i).type())) ? bf16ToF32(b, args.get(i)) : args.get(i));
        }
        if (args.size() == 2 && java.util.Set.of("add", "sub", "mul", "div").contains(name)) {
            // BF16.add(BF16, float) divides in HAT's Java: TC compiles what the Java does.
            boolean addDivides = name.equals("add") && HAT_BF16_TYPES.contains(descriptor(operands.get(0).type())) && descriptor(operands.get(1).type()).equals("F");
            var kind = switch (addDivides ? "div" : name) {
                case "add" -> BinaryOp.FADD;
                case "sub" -> BinaryOp.FSUB;
                case "mul" -> BinaryOp.FMUL;
                default -> BinaryOp.FDIV;
            };
            return f32ToBF16(b, b.binary(kind, floats.get(0), floats.get(1)));
        }
        if (name.equals("bfloat162float") && args.size() == 1) {
            return floats.get(0);
        }
        if ((name.equals("of") || name.equals("float2bfloat16")) && args.size() == 1) {
            return args.get(0).type().equals(Type.F32) ? f32ToBF16(b, args.get(0)) : args.get(0);
        }
        throw new Unsupported("TC-E1003", "hat.types.BF16." + name + " is not supported yet");
    }

    private static uk.ac.manchester.tornado.tc.ir.Value bf16ToF32(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value bits) {
        return b.convert(ConvertOp.BITCAST, b.binary(BinaryOp.SHL, bits, b.constInt(Type.I32, 16)), Type.F32);
    }

    /** {@code BF16.of(float).value()}, as an int. */
    private static uk.ac.manchester.tornado.tc.ir.Value f32ToBF16(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value f) {
        var bits = b.convert(ConvertOp.BITCAST, f, Type.I32);
        var sign = b.binary(BinaryOp.SHR, b.binary(BinaryOp.AND, bits, b.constInt(Type.I32, Integer.MIN_VALUE)), b.constInt(Type.I32, 16));
        var lsb = b.binary(BinaryOp.AND, bits, b.constInt(Type.I32, 0x1_0000));
        var round = b.binary(BinaryOp.AND, bits, b.constInt(Type.I32, 0x0_8000));
        var sticky = b.binary(BinaryOp.AND, bits, b.constInt(Type.I32, 0x0_7FFF));
        var zero = b.constInt(Type.I32, 0);
        var up = b.binary(BinaryOp.AND, b.icmp(IntPredicate.NE, round, zero), b.icmp(IntPredicate.NE, b.binary(BinaryOp.OR, lsb, sticky), zero));
        var rounded = b.select(up, b.binary(BinaryOp.ADD, bits, b.constInt(Type.I32, 0x1_0000)), bits);
        return b.binary(BinaryOp.AND, b.binary(BinaryOp.OR, b.binary(BinaryOp.SHR, rounded, b.constInt(Type.I32, 16)), sign), b.constInt(Type.I32, 0xffff));
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
                var loaded = memLoad(b, at.buffer(), at.offset(), memory);
                // Sub-int fields widen to int, as Java values do (char unsigned, others signed).
                return memory.equals(Type.I8) || memory.equals(Type.I16)
                        ? b.convert(scalar.carrier() == char.class || scalar.carrier() == boolean.class ? ConvertOp.ZEXT : ConvertOp.SEXT, loaded, Type.I32)
                        : loaded;
            }
            var value = v(rest.get(0));
            memStore(b, at.buffer(), at.offset(), value.type().equals(memory) ? value
                    : b.convert(value.type().equals(Type.I1) ? ConvertOp.ZEXT : ConvertOp.TRUNC, value, memory));
            return null;
        }
        if (element instanceof GroupLayout group && rest.isEmpty()) {
            views.put(op.result(), new View(at.buffer(), at.offset(), group));
            if (op.resultType() instanceof JavaType j && HAT_F16_TYPES.contains(descriptor(j))) {
                // A half float ({short value}): also its value, for F16 arithmetic. Unused, it is dead code.
                return b.convert(ConvertOp.BITCAST, memLoad(b, at.buffer(), at.offset(), Type.I16), Type.F16);
            }
            if (op.resultType() instanceof JavaType j && HAT_BF16_TYPES.contains(descriptor(j))) {
                return b.convert(ConvertOp.SEXT, memLoad(b, at.buffer(), at.offset(), Type.I16), Type.I32);
            }
            return null;
        }
        throw new Unsupported("TC-E1003", "HAT field " + name + " (" + element + ") is not supported yet");
    }

    /** A load at a byte offset: of a HAT buffer (a ByteArray parameter) or of HAT local/private memory (an alloca). */
    private static uk.ac.manchester.tornado.tc.ir.Value memLoad(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value memory, uk.ac.manchester.tornado.tc.ir.Value offset, Type type) {
        return isAlloca(memory) ? b.load(b.pointerOffset(memory, offset), type) : b.arrayLoad(memory, offset, type);
    }

    private static void memStore(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value memory, uk.ac.manchester.tornado.tc.ir.Value offset, uk.ac.manchester.tornado.tc.ir.Value value) {
        if (isAlloca(memory)) {
            b.store(b.pointerOffset(memory, offset), value);
        } else {
            b.arrayStore(memory, offset, value);
        }
    }

    private static boolean isAlloca(uk.ac.manchester.tornado.tc.ir.Value value) {
        return value instanceof Inst inst && inst.opcode() instanceof Opcode.Alloca;
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
        if (c.getName().equals("hat.types.BF16")) {
            return Type.I16;
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
