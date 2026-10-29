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

        /** The kernel a {@code static final DeviceKernel} field names (for {@code KernelContext.launch}). */
        java.util.Optional<Frontend.MethodRef> deviceKernel(String ownerInternalName, String field);

        /** The method being translated, if known (for HatWrites). */
        default Method method() {
            return null;
        }
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
    private final java.util.Set<Value> storedArrays = new java.util.HashSet<>();
    private uk.ac.manchester.tornado.tc.ir.Block entryTc;

    /** A private slot for a vector, allocated in the entry block (before its first instruction). */
    private uk.ac.manchester.tornado.tc.ir.Value slotFor(Type.VectorType type) {
        var insts = entryTc.insts();
        var at = insts.isEmpty() ? new IrBuilder(entryTc) : IrBuilder.insertingBefore(insts.get(0));
        return at.alloca(AddressSpace.PRIVATE, type.element(), type.length());
    }

    /** Vector parameters of the kernel updated in place: their TC parameters. */
    private final Map<Value, uk.ac.manchester.tornado.tc.ir.Value> mutatedParams = new LinkedHashMap<>();
    /** The builder of the op being translated: reading a mutable vector loads its slot there. */
    private IrBuilder current;
    /** Initial values of {@code @Parallel} loop variables ({@link Markers#parallel}). */
    private final java.util.Set<Value> parallelInits = new java.util.HashSet<>();
    /** Local arrays ({@code new float[n]}): pointer to element type. */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, Type> localArrays = new HashMap<>();
    /** HAT local/private memory of a struct-shaped device type: its layout, for the accessors' views. */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, GroupLayout> deviceStructs = new HashMap<>();
    /** The memory type at each recorded element address (half floats are stored as half, not punned as short). */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, Type> elementTypes = new HashMap<>();
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
        findVirtualParams(body);
        for (var block : body.blocks()) {
            if (block.isEntryBlock()) {
                continue;
            }
            var order = new ArrayList<Value>();
            paramOrder.put(block, order);
            for (var p : block.parameters()) {
                if (aliases.containsKey(p)) {
                    continue;
                }
                if (descriptor(p.type()).startsWith("[")) {
                    // created by the first branch translated into the block, typed as its incoming value: an
                    // array in the model can be a TC vector (an MMA fragment) or a pointer (a local array)
                    deferredParams.add(p);
                    continue;
                }
                values.put(p, blocks.get(block).addArg(isEnum(p.type()) ? Type.I32 : valueType(p.type()), ""));
                order.add(p);
            }
        }
        // Arrays written element by element: if one turns out to be a TC vector (an MMA fragment), it
        // lives in a private slot, so its lanes can be written at any index.
        model.elements().forEach(e -> {
            if (e instanceof JavaOp.ArrayAccessOp.ArrayStoreOp store) {
                storedArrays.add(store.operands().get(0));
            }
        });
        // Mutated HAT vectors get their private slots first, in the entry block.
        var entryBuilder = new IrBuilder(blocks.get(entry));
        entryTc = blocks.get(entry);
        model.elements().forEach(e -> {
            if (e instanceof JavaOp.InvokeOp invoke && invoke.hasReceiver() && invoke.operands().size() >= 2 && isMutation(invoke)) {
                var vector = invoke.operands().get(0);
                var type = (Type.VectorType) valueType(vector.type());
                vectorSlots.computeIfAbsent(vector, x -> entryBuilder.alloca(AddressSpace.PRIVATE, type.element(), type.length()));
            }
        });
        // Reverse post-order: every value is translated before the blocks that use it (inlined code
        // can put a continuation block before the blocks that branch to it).
        for (var block : reversePostOrder(body)) {
            var b = new IrBuilder(blocks.get(block));
            current = b;
            for (var p : block.parameters()) {
                if (!vectorSlots.containsKey(p) && storedArrays.contains(p) && values.get(p) instanceof uk.ac.manchester.tornado.tc.ir.Value pv && pv.type() instanceof Type.VectorType vec) {
                    vectorSlots.put(p, slotFor(vec));
                }
                if (vectorSlots.containsKey(p)) {
                    var arg = values.remove(p);
                    if (block.isEntryBlock()) {
                        mutatedParams.put(p, arg);
                    }
                    storeVector(b, vectorSlots.get(p), arg);
                }
            }
            for (var op : block.ops()) {
                translate(b.at(location(op)), op);
            }
        }
        var problems = Frontend.resolveLocalArrays(function);
        if (!problems.isEmpty()) {
            throw new Unsupported(problems.get(0).code(), problems.get(0).message());
        }
        recordWrites();
        return function;
    }

    private static List<Block> reversePostOrder(jdk.incubator.code.Body body) {
        var post = new ArrayList<Block>();
        var seen = new java.util.HashSet<Block>();
        var stack = new java.util.ArrayDeque<Object[]>();
        stack.push(new Object[] { body.entryBlock(), 0 });
        seen.add(body.entryBlock());
        while (!stack.isEmpty()) {
            var top = stack.peek();
            var block = (Block) top[0];
            var successors = block.successors();
            int next = (int) top[1];
            if (next < successors.size()) {
                top[1] = next + 1;
                var target = successors.get(next).targetBlock();
                if (seen.add(target)) {
                    stack.push(new Object[] { target, 0 });
                }
            } else {
                post.add(block);
                stack.pop();
            }
        }
        java.util.Collections.reverse(post);
        // unreachable blocks keep their place after the reachable ones
        for (var block : body.blocks()) {
            if (!seen.contains(block)) {
                post.add(block);
            }
        }
        return post;
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

    private boolean isEnum(CodeType type) {
        return type instanceof JavaType j && !j.toNominalDescriptor().isPrimitive() && !j.toNominalDescriptor().isArray() && context.load(j) instanceof Class<?> c && c.isEnum();
    }

    private Type parameterType(CodeType type) {
        if (isEnum(type)) {
            return Type.I32; // an enum is its ordinal in kernels
        }
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
            case "Lhat/types/Tile;" -> uk.ac.manchester.tornado.tc.ir.ApiTypes.TILE;
            default -> Frontend.typeOf(descriptor(type));
        };
    }

    // ---- ops

    private uk.ac.manchester.tornado.tc.ir.Value v(Value value) {
        if (vectorSlots.containsKey(value) && !values.containsKey(value)) {
            return loadVector(current, vectorSlots.get(value), (Type.VectorType) valueTypeOfSlot(value));
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

    /** Values that are MMA operand fragments (A or B): packed registers, whose elements cannot be read or written. */
    private final java.util.Set<Value> operandFragments = new java.util.HashSet<>();

    /** The bytecode frontend's rules for tensor-core fragments: accumulators only, at a constant index within the fragment. */
    private long fragmentLane(Value array, uk.ac.manchester.tornado.tc.ir.Value index, int length) {
        if (operandFragments.contains(array)) {
            throw new Unsupported("TC-E1010", "elements of an MMA operand fragment (A or B) cannot be read; only accumulator fragments can");
        }
        var lane = uk.ac.manchester.tornado.tc.ir.analysis.Constants.integer(index);
        if (lane.isEmpty() || lane.getAsLong() < 0 || lane.getAsLong() >= length) {
            throw new Unsupported("TC-E1010", "fragment elements can only be read at a constant index within the fragment");
        }
        return lane.getAsLong();
    }

    private void define(Op op, uk.ac.manchester.tornado.tc.ir.Value value) {
        if (op.result() != null && value instanceof Inst producer && producer.opcode() instanceof Opcode.Intrinsic(var name) && (name.contains(".load_a") || name.contains(".load_b"))) {
            operandFragments.add(op.result());
        }
        if (op.result() != null && value != null && value.type() instanceof Type.VectorType vec && storedArrays.contains(op.result()) && !vectorSlots.containsKey(op.result())) {
            vectorSlots.put(op.result(), slotFor(vec));
        }
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
            case CoreOp.ConstantOp c when c.value() instanceof String text -> strings.put(op.result(), text); // a Debug.printf format
            case CoreOp.ConstantOp c when descriptor(c.resultType()).equals("Ljava/lang/Class;") -> {
                // a class literal (float.class for Tensor.create): compile-time only
            }
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
            case JavaOp.ArrayAccessOp.ArrayLoadOp load when ranges.containsKey(resolve(load.operands().get(0))) -> define(op, b.add(ranges.get(resolve(load.operands().get(0)))[0], v(load.operands().get(1))));
            case JavaOp.ArrayLengthOp length when ranges.containsKey(resolve(length.operands().get(0))) -> {
                var r = ranges.get(resolve(length.operands().get(0)));
                define(op, b.binary(BinaryOp.SUB, r[1], r[0]));
            }
            case JavaOp.ArrayAccessOp.ArrayLoadOp load when isView(load.operands().get(0)) -> define(op, viewLoad(b, load));
            case JavaOp.ArrayAccessOp.ArrayStoreOp store when isView(store.operands().get(0)) -> viewStore(b, store);
            case JavaOp.ArrayLengthOp length when arrayViews.containsKey(length.operands().get(0)) -> define(op, viewLength(b, arrayViews.get(length.operands().get(0))));
            case JavaOp.ArrayAccessOp.ArrayLoadOp load when vectorSlots.containsKey(load.operands().get(0)) && !values.containsKey(load.operands().get(0)) -> {
                var slot = vectorSlots.get(load.operands().get(0));
                var type = (Type.VectorType) valueTypeOfSlot(load.operands().get(0));
                long lane = fragmentLane(load.operands().get(0), v(load.operands().get(1)), type.length());
                define(op, widen(b, b.load(elementAddress(b, slot, b.constInt(Type.I32, lane), type.element()), type.element()), valueType(load.resultType())));
            }
            case JavaOp.ArrayAccessOp.ArrayStoreOp store when vectorSlots.containsKey(store.operands().get(0)) && !values.containsKey(store.operands().get(0)) -> {
                var slot = vectorSlots.get(store.operands().get(0));
                var type = (Type.VectorType) valueTypeOfSlot(store.operands().get(0));
                long lane = fragmentLane(store.operands().get(0), v(store.operands().get(1)), type.length());
                b.store(elementAddress(b, slot, b.constInt(Type.I32, lane), type.element()), narrow(b, v(store.operands().get(2)), type.element()));
            }
            case JavaOp.ArrayAccessOp.ArrayLoadOp load when values.get(load.operands().get(0)) instanceof uk.ac.manchester.tornado.tc.ir.Value vec && vec.type() instanceof Type.VectorType vt -> {
                long lane = fragmentLane(load.operands().get(0), v(load.operands().get(1)), vt.length());
                var value = b.intrinsic("tornado.vector.lane", Immediates.of("lane", lane), vt.element(), List.of(vec));
                define(op, widen(b, value, valueType(load.resultType())));
            }
            case JavaOp.ArrayLengthOp length when values.get(length.operands().get(0)) instanceof uk.ac.manchester.tornado.tc.ir.Value vec && vec.type() instanceof Type.VectorType vt ->
                define(op, b.constInt(Type.I32, vt.length()));
            case JavaOp.ArrayAccessOp.ArrayLoadOp load -> define(op, arrayLoad(b, v(load.operands().get(0)), v(load.operands().get(1)), valueType(load.resultType()),
                    descriptor(load.resultType()).equals("C")));
            case JavaOp.ArrayAccessOp.ArrayStoreOp store -> arrayStore(b, v(store.operands().get(0)), v(store.operands().get(1)), v(store.operands().get(2)));
            case JavaOp.ArrayLengthOp length -> {
                var array = v(length.operands().get(0));
                define(op, array.type() instanceof Type.PointerType ? Frontend.localArrayLength(b, array) : b.arrayLength(array));
            }
            case JavaOp.FieldAccessOp.FieldLoadOp field -> define(op, fieldLoad(b, field));
            case JavaOp.FieldAccessOp.FieldStoreOp field when !field.operands().isEmpty() && field.operands().size() == 2 -> {
                var object = v(field.operands().get(0));
                var memory = memoryOrValueType(field.fieldReference().type());
                b.fieldStore(object, field.fieldReference().name(), narrow(b, v(field.operands().get(1)), memory));
            }
            case JavaOp.InvokeOp invoke -> define(op, invoke(b, invoke));
            case JavaOp.NewOp n -> define(op, newValue(b, n));
            case CoreOp.BranchOp br -> {
                markParallel(br.branch());
                carryTileDims(br.branch());
                b.br(blocks.get(br.branch().targetBlock()), branchArgs(br.branch()));
            }
            case CoreOp.ConditionalBranchOp cbr when carryTileDims(cbr.trueBranch()) && carryTileDims(cbr.falseBranch()) -> b.condBr(v(cbr.predicateOperand()), blocks.get(cbr.trueBranch().targetBlock()), branchArgs(cbr.trueBranch()),
                    blocks.get(cbr.falseBranch().targetBlock()), branchArgs(cbr.falseBranch()));
            case CoreOp.ReturnOp ret -> {
                // A vector parameter updated in place: a kernel writes it back (the host copies it out).
                mutatedParams.forEach((param, arg) -> {
                    if (!kernel) {
                        throw new Unsupported("TC-E1003", "a device function cannot update a vector parameter in place");
                    }
                    b.intrinsic("tornado.vector.store_param", Type.VOID, List.of(arg, loadVector(b, vectorSlots.get(param), (Type.VectorType) arg.type())));
                });
                if (ret.operands().isEmpty()) {
                    b.ret();
                } else {
                    b.ret(v(ret.operands().get(0)));
                }
            }
            default -> throw new Unsupported("TC-E1003", "code model op " + op.getClass().getSimpleName() + " is not supported by the Babylon frontend yet");
        }
    }

    /** A branch that passes a {@code @Parallel} variable's initial value enters its loop: tag the header as the bytecode frontend does. */
    private void markParallel(Block.Reference branch) {
        var arguments = branch.arguments();
        for (int k = 0; k < arguments.size(); k++) {
            if (parallelInits.contains(arguments.get(k))) {
                var header = blocks.get(branch.targetBlock());
                if (header.attributes().stream().noneMatch(a -> a.name().equals("parallel"))) {
                    header.addAttribute(uk.ac.manchester.tornado.tc.ir.Attribute.of("parallel", "iv", k));
                }
            }
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
        if (localArrays.containsKey(array)) {
            loaded = b.load(elementAddress(b, array, index, localArrays.get(array)), localArrays.get(array));
        } else if (array instanceof Inst alloca && alloca.opcode() instanceof Opcode.Alloca(var space, var element, var count)) {
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
        if (localArrays.containsKey(array)) {
            b.store(elementAddress(b, array, index, localArrays.get(array)), narrow(b, value, localArrays.get(array)));
            return;
        }
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
            // A field of an object parameter, as the bytecode frontend reads it (getfield).
            var object = v(op.operands().get(0));
            if (!(object.type() instanceof Type.ObjectType)) {
                throw new Unsupported("TC-E1003", "instance field " + ref.name() + " of a " + object.type() + " is not supported in kernels");
            }
            var loaded = b.fieldLoad(object, ref.name(), memoryOrValueType(ref.type()));
            return switch (descriptor(ref.type())) {
                case "Z" -> b.icmp(IntPredicate.NE, loaded, b.constInt(Type.I8, 0)); // code models keep booleans as i1
                case "C" -> b.convert(ConvertOp.ZEXT, loaded, Type.I32);
                case "B", "S" -> b.convert(ConvertOp.SEXT, loaded, Type.I32);
                default -> loaded;
            };
        }
        if (descriptor(ref.type()).equals("Luk/ac/manchester/tornado/api/DeviceKernel;")) {
            // KernelContext.launch's child kernel: known by name only (dynamic parallelism)
            var owner = ((JavaType) ref.refType()).toNominalDescriptor().descriptorString();
            deviceKernels.put(op.result(), context.deviceKernel(owner.substring(1, owner.length() - 1), ref.name())
                    .orElseThrow(() -> new Unsupported("TC-E1013", "DeviceKernel field " + ref.name() + " cannot be resolved: KernelContext.launch needs a static final field")));
            return null;
        }
        if (descriptor(ref.type()).startsWith("Ljdk/incubator/code/")) {
            // A code type (JavaType.FLOAT for Tile.arange): compile-time only, like a class literal
            return null;
        }
        // A static final field: its value, as the bytecode frontend folds computed constants.
        try {
            var owner = context.load((JavaType) ref.refType());
            var field = owner.getDeclaredField(ref.name());
            if (field.isEnumConstant()) {
                // Enum constants are their ordinals in kernels, as in the bytecode frontend (read without
                // field access, so an enum in another module works too).
                @SuppressWarnings({ "unchecked", "rawtypes" })
                int ordinal = Enum.valueOf((Class) owner, ref.name()).ordinal();
                return b.constInt(Type.I32, ordinal);
            }
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
        if (owner.equals("uk.ac.manchester.tornado.api.exceptions.Debug") && name.equals("printf") && op.isVarArgs()) {
            var format = strings.get(operands.get(0));
            if (format == null) {
                throw new Unsupported("TC-E1013", "Debug.printf needs a constant format string and its arguments passed directly");
            }
            // The format is kept as the Java string: each dialect spells (escapes) it itself.
            b.intrinsic("tornado.printf", Immediates.of("format", format), Type.VOID, args(operands.subList(1, operands.size())));
            return null;
        }
        if (owner.equals("uk.ac.manchester.tornado.api.KernelContext") && (name.equals("launch") || name.equals("launch3D"))) {
            deviceLaunch(b, op, name);
            return null;
        }
        if (owner.equals(Markers.class.getName()) && name.equals("parallel")) {
            parallelInits.add(op.result());
            return v(operands.get(0));
        }
        if (owner.equals("hat.types.Tensor")) {
            return hatTensor(b, op, name);
        }
        if (owner.equals("hat.TileContext") || owner.equals("hat.types.Tile")) {
            return hatTile(b, op, name);
        }
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
            if (api.get().receiver().isPresent()) {
                updateReceiver(b, operands.get(0), api.get().receiver().get());
            }
            var result = api.get().value().orElse(null);
            if (result != null && descriptor(op.resultType()).equals("Z") && !result.type().equals(Type.I1)) {
                // an intrinsic answering a boolean as an int (isNaN, warp votes): code models keep booleans as i1
                result = b.icmp(IntPredicate.NE, result, b.constInt(result.type(), 0));
            }
            if (result != null && result.type() instanceof Type.PointerType && op.resultType() instanceof jdk.incubator.code.dialect.java.ArrayType array) {
                // a local array (KernelContext.allocateFloatLocalArray(n)): indexed through its element type
                localArrays.put(result, descriptor(array.componentType()).length() == 1 ? memoryType(primitive(array.componentType()))
                        : Frontend.typeOf(descriptor(array.componentType())));
            }
            return result;
        }
        if (op.hasReceiver() && name.equals("ordinal") && operands.size() == 1 && context.load((JavaType) ref.refType()) instanceof Class<?> c && c.isEnum()) {
            return v(operands.get(0));
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
        if (owner.equals("java.lang.Math") && MATH.contains(name)) {
            return math(b, name, args(operands), valueType(op.resultType()));
        }
        // Instance calls compiled as device functions with the receiver first, as the bytecode frontend
        // does: on objects (vectors, collections, fields' owners), on an enum (its ordinal) and the
        // KernelContext methods whose Java body is their device semantics (swizzled loads and stores).
        if (op.hasReceiver() && !(v(operands.get(0)).type() instanceof Type.ObjectType || isEnum(operands.get(0).type()))) {
            throw new Unsupported("TC-E1003", "instance call " + owner + "." + name + " is not supported by the Babylon frontend yet");
        }
        // A static call, or a call on an object (a TornadoVM vector collection, ...): a device function,
        // translated from its model or its bytecode and inlined, as the bytecode frontend does.
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
                if (index != null && context.method() != null) {
                    HatWrites.passes(context.method(), index, method, i);
                }
            }
            context.callee(method, calleeName);
            if (method.isAnnotationPresent(jdk.incubator.code.Reflect.class)) {
                return b.call(calleeName, valueType(op.resultType()), args(operands));
            }
            // From bytecode: booleans travel as ints, as on the JVM stack.
            var callArgs = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
            for (var a : args(operands)) {
                callArgs.add(a.type().equals(Type.I1) ? b.convert(ConvertOp.ZEXT, a, Type.I32) : a);
            }
            if (descriptor(op.resultType()).equals("Z")) {
                return b.icmp(IntPredicate.NE, b.call(calleeName, Type.I32, callArgs), b.constInt(Type.I32, 0));
            }
            return b.call(calleeName, valueType(op.resultType()), callArgs);
        } catch (ReflectiveOperationException e) {
            throw new Unsupported("TC-E1007", "called method " + owner + "." + name + " cannot be resolved: " + e);
        }
    }

    /** String constants: only Debug.printf formats read them. */
    private final Map<Value, String> strings = new HashMap<>();

    /** {@code static final DeviceKernel} fields read: the child kernels they name. */
    private final Map<Value, Frontend.MethodRef> deviceKernels = new HashMap<>();

    /**
     * {@code kc.launch(kernel, [mode,] gridX, blockX, args...)} (or launch3D): a device-side launch of a
     * child kernel (CUDA dynamic parallelism), as the bytecode frontend emits it.
     */
    private void deviceLaunch(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var operands = op.operands();
        var child = deviceKernels.get(operands.get(1));
        if (child == null) {
            throw new Unsupported("TC-E1013", "the DeviceKernel passed to KernelContext.launch must be read from a static final field");
        }
        int next = 2;
        var mode = "default";
        if (op.invokeReference().signature().parameterTypes().stream().anyMatch(t -> descriptor(t).equals("Luk/ac/manchester/tornado/api/enums/DeviceLaunchMode;"))) {
            var ordinal = uk.ac.manchester.tornado.tc.ir.analysis.Constants.integer(v(operands.get(next++)))
                    .orElseThrow(() -> new Unsupported("TC-E1013", "the DeviceLaunchMode of KernelContext.launch must be a constant"));
            mode = uk.ac.manchester.tornado.api.enums.DeviceLaunchMode.values()[(int) ordinal].name().toLowerCase(java.util.Locale.ROOT);
        }
        var one = b.constInt(Type.I32, 1);
        var launch = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        if (name.equals("launch3D")) {
            for (int k = 0; k < 6; k++) {
                launch.add(v(operands.get(next++)));
            }
        } else {
            launch.addAll(List.of(v(operands.get(next)), one, one, v(operands.get(next + 1)), one, one));
            next += 2;
        }
        if (!op.isVarArgs()) {
            throw new Unsupported("TC-E1013", "the arguments of KernelContext.launch must be passed directly (as varargs)");
        }
        for (var argument : operands.subList(next, operands.size())) {
            var value = v(argument);
            if (value.type() instanceof Type.PointerType) {
                throw new Unsupported("TC-E1014", "a local-memory array cannot be passed to KernelContext.launch: it belongs to the launching block");
            }
            launch.add(value);
        }
        b.intrinsic("tornado.device_launch", Immediates.of("mode", mode, "callee", child.owner().replace('/', '.') + "." + child.name(), "descriptor", child.descriptor()), Type.VOID,
                launch);
    }

    /** A value-type receiver updated in place: later reads see {@code updated} (through its slot, see isMutation). */
    private void updateReceiver(IrBuilder b, Value receiver, uk.ac.manchester.tornado.tc.ir.Value updated) {
        var slot = vectorSlots.get(receiver);
        if (slot == null) {
            throw new Unsupported("TC-E9003", "an in-place update of a value that has no slot: " + receiver);
        }
        storeVector(b, slot, updated);
    }

    /** {@code new T(...)}: TornadoVM value types (HalfFloat, vectors, AtomicInteger) as the bytecode frontend builds them. */
    private uk.ac.manchester.tornado.tc.ir.Value newValue(IrBuilder b, JavaOp.NewOp op) {
        if (op.resultType() instanceof jdk.incubator.code.dialect.java.ArrayType array) {
            if (array.dimensions() != 1 || op.operands().size() != 1) {
                throw new Unsupported("TC-E1002", "only one-dimensional local arrays are supported in kernels"); // the bytecode frontend's code for them
            }
            var element = descriptor(array.componentType()).length() == 1 ? memoryType(primitive(array.componentType()))
                    : Frontend.typeOf(descriptor(array.componentType())); // HalfFloat[] fragments: f16
            var local = Frontend.localArray(b, AddressSpace.PRIVATE, element, v(op.operands().get(0)));
            localArrays.put(local, element);
            return local;
        }
        var ctor = op.constructorReference();
        var owner = ((JavaType) ctor.refType()).toString();
        var descriptor = ctor.signature().parameterTypes().stream().map(BabylonTranslator::descriptor).collect(java.util.stream.Collectors.joining("", "(", ")V"));
        return Frontend.apiNew(b, owner, descriptor, args(op.operands()))
                .orElseThrow(() -> new Unsupported("TC-E1003", "new " + owner + descriptor + " is not supported in kernels"));
    }

    /** A field's type as stored: byte, short and char keep their width (the bytecode frontend's field types). */
    private static Type memoryOrValueType(CodeType type) {
        return switch (descriptor(type)) {
            case "B", "Z" -> Type.I8;
            case "S", "C" -> Type.I16;
            default -> valueType(type);
        };
    }

    /** A slot-backed vector's type: its slot's element and count. */
    private Type valueTypeOfSlot(Value value) {
        var slot = (Inst) vectorSlots.get(value);
        var alloca = (Opcode.Alloca) slot.opcode();
        return new Type.VectorType(alloca.element(), alloca.count());
    }

    /** A sub-int element widened to an int value, as Java reads it. */
    private static uk.ac.manchester.tornado.tc.ir.Value widen(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value value, Type type) {
        if ((value.type().equals(Type.I8) || value.type().equals(Type.I16)) && type.equals(Type.I32)) {
            return b.convert(ConvertOp.SEXT, value, Type.I32);
        }
        return value;
    }

    private static Class<?> primitive(CodeType type) {
        return switch (descriptor(type)) {
            case "Z" -> boolean.class;
            case "B" -> byte.class;
            case "S" -> short.class;
            case "C" -> char.class;
            case "I" -> int.class;
            case "J" -> long.class;
            case "F" -> float.class;
            case "D" -> double.class;
            default -> throw new Unsupported("TC-E1003", "local array of " + type + " is not supported in kernels");
        };
    }

    /** A method named in a model, resolved with access to its declaring class (kernels often sit in private nested types). */
    private Method resolve(MethodRef ref) throws ReflectiveOperationException {
        var owner = context.load((JavaType) ref.refType());
        if (owner != null) {
            try {
                return ref.resolveToMethod(java.lang.invoke.MethodHandles.privateLookupIn(owner, java.lang.invoke.MethodHandles.lookup()));
            } catch (IllegalAccessException notOpen) {
                // another module (TornadoMath, java.lang.Integer, ...) that does not open its package: its public methods
            }
        }
        // A public method of a class this module cannot open: find it by name and parameter classes.
        if (owner == null) {
            throw new ClassNotFoundException(ref.refType().toString());
        }
        var parameters = new ArrayList<Class<?>>();
        for (var t : ref.signature().parameterTypes()) {
            var d = descriptor(t);
            parameters.add(d.length() == 1 ? primitive(t) : context.load((JavaType) t));
        }
        return owner.getMethod(ref.name(), parameters.toArray(Class<?>[]::new));
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
                // An array of primitives or half floats without a nested schema. Half floats declared with
                // their value field (array("a", n, half -> half.field("value"))) take the struct path below,
                // which NVIDIA's OpenCL compiles (it refuses private half[] arrays).
                if (nested.isEmpty() && (clazz.isPrimitive() || clazz.getName().equals("hat.types.F16") || clazz.getName().equals("hat.types.BF16"))) {
                    var element = memoryType(clazz);
                    var array = b.alloca(space, element, size);
                    deviceArrays.put(array, element);
                    return array;
                }
            }
            // Any other schema (structs, arrays of structs): memory laid out as its MemoryLayout, read through views.
            var layout = DeviceLayouts.of(owner);
            // Words of the layout's field size when every field has the same size (an array of F16
            // structs is short[]), else 8-byte words: memory of 8-byte words that holds 2-byte values
            // makes the device compiler pack and unpack them (HAT's FP16 register-tiled matmul: 912
            // SASS instructions with 175 PRMT, against 392 with 16 as short[]).
            int word = uniformFieldBytes(layout);
            var wordType = switch (word) {
                case 1 -> Type.I8;
                case 2 -> Type.I16;
                case 4 -> Type.I32;
                default -> Type.I64;
            };
            int wordBytes = word == 1 || word == 2 || word == 4 ? word : 8;
            long words = (layout.byteSize() + wordBytes - 1) / wordBytes;
            var memory = b.alloca(space, wordType, (int) Math.max(1, words));
            deviceStructs.put(memory, layout);
            return memory;
        } catch (ReflectiveOperationException | NullPointerException e) {
            throw new Unsupported("TC-E1003", "HAT device type " + ref.refType() + " has no readable deviceSchema: " + e);
        }
    }

    /** The byte size shared by every value field of {@code layout} (nested structs and arrays included), or 0. */
    private static int uniformFieldBytes(MemoryLayout layout) {
        return switch (layout) {
            case ValueLayout v -> (int) v.byteSize();
            case SequenceLayout seq -> uniformFieldBytes(seq.elementLayout());
            case GroupLayout g -> {
                int size = -1;
                for (var member : g.memberLayouts()) {
                    if (member instanceof java.lang.foreign.PaddingLayout) {
                        yield 0;
                    }
                    int m = uniformFieldBytes(member);
                    if (m == 0 || size >= 0 && m != size) {
                        yield 0;
                    }
                    size = m;
                }
                yield Math.max(size, 0);
            }
            default -> 0;
        };
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
            elementTypes.put(address, element);
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

    private static uk.ac.manchester.tornado.tc.ir.Value loadVector(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value slot, Type.VectorType type) {
        var values = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (int l = 0; l < type.length(); l++) {
            values.add(b.load(b.pointerOffset(slot, b.constInt(Type.I64, (long) bytes(type.element()) * l)), type.element()));
        }
        return make(b, type, values);
    }

    private static void storeVector(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value slot, uk.ac.manchester.tornado.tc.ir.Value vector) {
        var type = (Type.VectorType) vector.type();
        for (int l = 0; l < type.length(); l++) {
            b.store(b.pointerOffset(slot, b.constInt(Type.I64, (long) bytes(type.element()) * l)), laneOf(b, vector, l));
        }
    }

    /**
     * Whether {@code invoke} updates its receiver, a vector, in place: a HAT vector's lane setter
     * ({@code vA.x(v)}) or a TornadoVM vector's {@code set}/{@code setX}/{@code fill}.
     */
    private static boolean isMutation(JavaOp.InvokeOp invoke) {
        var receiver = invoke.operands().get(0).type();
        var name = invoke.invokeReference().name();
        if (vectorLanes(receiver) > 0) {
            return lane(name) >= 0;
        }
        return valueType(receiver) instanceof Type.VectorType && (name.startsWith("set") || name.equals("fill"));
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
        // f32 vectors: one wide access where the address allows it (the CUDA/OpenCL spelling), as HAT's
        // own backends do; lane by lane on other targets or when the index is not aligned.
        boolean wide = type.element().equals(Type.F32);
        if (name.startsWith("store")) {
            var at = viewAddress(b, view, null, v(op.operands().get(2)));
            if (wide) {
                b.intrinsic("tornado.vector.store", Type.VOID, List.of(receiver, at, v(op.operands().get(1))));
            } else {
                storeVectorAt(b, receiver, at, type, v(op.operands().get(1)));
            }
            return null;
        }
        var at = viewAddress(b, view, null, v(op.operands().get(1)));
        return wide ? b.intrinsic("tornado.vector.load", type, List.of(receiver, at)) : loadVectorAt(b, receiver, at, type);
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
            b.store(b.pointerOffset(slot, b.constInt(Type.I64, (long) bytes(Type.F32) * lane)), args.get(1));
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

    // ---- HAT tiles (TileContext, Tile): CUDA Tile through TC's tile.* intrinsics

    /** The HAT-level dimensions of each tile value (1 or 2): a store partitions its tensor by them. */
    private final Map<uk.ac.manchester.tornado.tc.ir.Value, List<uk.ac.manchester.tornado.tc.ir.Value>> tileDims = new HashMap<>();
    /** {@code TileContext.index(...)} and {@code TileContext.shape(...)}: their components, which only other tile calls read. */
    private final Map<Value, List<uk.ac.manchester.tornado.tc.ir.Value>> tileTuples = new HashMap<>();
    /** {@code TileContext.irange(start, end)}: an int[] {start, ..., end - 1} that is only iterated. */
    private final Map<Value, uk.ac.manchester.tornado.tc.ir.Value[]> ranges = new HashMap<>();

    /**
     * Block parameters that carry a value with no TC value of its own ({@code irange}, {@code index},
     * {@code shape}): no block argument, and uses read the value they alias.
     */
    private final Map<Value, Value> aliases = new HashMap<>();

    private static boolean isVirtual(Value value) {
        if (!(value instanceof Op.Result r && r.op() instanceof JavaOp.InvokeOp invoke)) {
            return false;
        }
        var owner = invoke.invokeReference().refType().toString();
        return owner.equals("hat.TileContext") && java.util.Set.of("irange", "index", "shape").contains(invoke.invokeReference().name()) || owner.equals("hat.types.Tensor");
    }

    private void findVirtualParams(jdk.incubator.code.Body body) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : body.blocks()) {
                for (var op : block.ops()) {
                    for (var ref : op.successors()) {
                        var params = ref.targetBlock().parameters();
                        for (int k = 0; k < ref.arguments().size(); k++) {
                            var arg = ref.arguments().get(k);
                            if ((isVirtual(arg) || aliases.containsKey(arg)) && !aliases.containsKey(params.get(k))) {
                                aliases.put(params.get(k), aliases.getOrDefault(arg, arg));
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private Value resolve(Value value) {
        return aliases.getOrDefault(value, value);
    }

    /** Block parameters in the order of their TC block arguments; array-typed ones are created late (deferredParams). */
    private final Map<Block, List<Value>> paramOrder = new HashMap<>();
    private final java.util.Set<Value> deferredParams = new java.util.HashSet<>();

    /** A branch's arguments in its target's TC argument order, without those for alias parameters. */
    private List<uk.ac.manchester.tornado.tc.ir.Value> branchArgs(Block.Reference ref) {
        var target = ref.targetBlock();
        var params = target.parameters();
        for (int k = 0; k < params.size(); k++) {
            var p = params.get(k);
            if (deferredParams.contains(p) && !values.containsKey(p)) {
                var incoming = v(ref.arguments().get(k));
                var arg = blocks.get(target).addArg(incoming.type(), "");
                values.put(p, arg);
                if (localArrays.containsKey(incoming)) {
                    localArrays.put(arg, localArrays.get(incoming));
                }
                paramOrder.get(target).add(p);
            }
        }
        var out = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>();
        for (var p : paramOrder.get(target)) {
            out.add(v(ref.arguments().get(params.indexOf(p))));
        }
        return out;
    }

    /** Loop-carried tiles (an accumulator) keep the dimensions of the tile that enters the loop. */
    private boolean carryTileDims(Block.Reference branch) {
        var target = branch.targetBlock();
        for (int k = 0; k < branch.arguments().size(); k++) {
            var incoming = values.get(branch.arguments().get(k));
            var param = values.get(target.parameters().get(k));
            if (aliases.containsKey(target.parameters().get(k))) {
                continue;
            }
            if (incoming != null && param != null && tileDims.containsKey(incoming)) {
                tileDims.putIfAbsent(param, tileDims.get(incoming));
            }
        }
        return true;
    }

    private static int dtype(String name) {
        return uk.ac.manchester.tornado.tc.tornado.TileTyping.DTYPES.indexOf(name);
    }

    /**
     * A HAT tensor (TensorF32, Tensor2DF32, Tensor2DF16) as a strided CUDA Tile view: its array starts
     * after HAT's header, at an element offset; a 1D tensor is one row.
     */
    private uk.ac.manchester.tornado.tc.ir.Value tensorView(IrBuilder b, Value tensor) {
        var buffer = v(tensor);
        var index = bufferParams.get(buffer);
        var layout = index == null ? null : context.bufferLayout(index);
        if (layout == null || !(layout.memberLayouts().getLast() instanceof SequenceLayout array)) {
            throw new Unsupported("TC-E1003", "a HAT tile load or store needs a tensor parameter with a known layout");
        }
        String dtype;
        long elementBytes = array.elementLayout().byteSize();
        if (array.elementLayout() instanceof ValueLayout value && value.carrier() == float.class) {
            dtype = "F32";
        } else if (elementBytes == 2) {
            dtype = "F16";
        } else {
            throw new Unsupported("TC-E1003", "HAT tiles over " + array.elementLayout() + " tensors are not supported");
        }
        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(array.name().orElseThrow())) / elementBytes;
        var m = field(b, buffer, layout, "m");
        var two = layout.memberLayouts().stream().anyMatch(l -> l.name().orElse("").equals("n"));
        var rows = two ? m : b.constInt(Type.I32, 1);
        var cols = two ? field(b, buffer, layout, "n") : m;
        return b.intrinsic("tile.view_strided", uk.ac.manchester.tornado.tc.ir.ApiTypes.TENSOR_VIEW,
                List.of(buffer, b.constInt(Type.I32, dtype(dtype)), b.constInt(Type.I32, offset), rows, cols, cols));
    }

    private uk.ac.manchester.tornado.tc.ir.Value field(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value buffer, GroupLayout layout, String name) {
        var at = b.constInt(Type.I64, layout.byteOffset(MemoryLayout.PathElement.groupElement(name)));
        return b.arrayLoad(buffer, at, memoryType((ValueLayout) layout.select(MemoryLayout.PathElement.groupElement(name))));
    }

    /** A HAT-level shape or index as CUDA Tile's two extents: 1D (n) is (1, n). */
    private List<uk.ac.manchester.tornado.tc.ir.Value> rank2(IrBuilder b, List<uk.ac.manchester.tornado.tc.ir.Value> dims, boolean index) {
        return dims.size() == 1 ? List.of(b.constInt(Type.I32, index ? 0 : 1), dims.get(0)) : dims;
    }

    private List<uk.ac.manchester.tornado.tc.ir.Value> tuple(IrBuilder b, Value value) {
        var t = tileTuples.get(resolve(value));
        return t != null ? t : List.of(v(value));
    }

    private uk.ac.manchester.tornado.tc.ir.Value tile(IrBuilder b, String intrinsic, List<? extends uk.ac.manchester.tornado.tc.ir.Value> operands,
            List<uk.ac.manchester.tornado.tc.ir.Value> dims) {
        var result = b.intrinsic(intrinsic, uk.ac.manchester.tornado.tc.ir.ApiTypes.TILE, operands);
        tileDims.put(result, dims);
        return result;
    }

    private List<uk.ac.manchester.tornado.tc.ir.Value> dims(uk.ac.manchester.tornado.tc.ir.Value tile) {
        var dims = tileDims.get(tile);
        if (dims == null) {
            throw new Unsupported("TC-E1003", "the shape of this HAT tile cannot be followed");
        }
        return dims;
    }

    private uk.ac.manchester.tornado.tc.ir.Value hatTile(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var operands = op.operands();
        switch (name) {
            case "BIDX", "BIDY", "BIDZ" -> {
                return b.intrinsic("tile.bid", Immediates.of("dim", "XYZ".indexOf(name.charAt(3))), Type.I32, List.of());
            }
            case "index", "shape" -> {
                tileTuples.put(op.result(), args(operands));
                return null;
            }
            case "irange" -> {
                var a = args(operands);
                ranges.put(op.result(), a.size() == 1 ? new uk.ac.manchester.tornado.tc.ir.Value[] { b.constInt(Type.I32, 0), a.get(0) }
                        : new uk.ac.manchester.tornado.tc.ir.Value[] { a.get(0), a.get(1) });
                return null;
            }
            case "ceildiv" -> {
                if (operands.stream().allMatch(x -> descriptor(x.type()).equals("I"))) {
                    var a = v(operands.get(0));
                    var c = v(operands.get(1));
                    return b.binary(BinaryOp.DIV, b.binary(BinaryOp.SUB, b.binary(BinaryOp.ADD, a, c), b.constInt(Type.I32, 1)), c);
                }
            }
            case "numTiles" -> {
                // ceil(extent of the tensor along the dimension / tile extent along it)
                var buffer = v(operands.get(0));
                var layout = context.bufferLayout(bufferParams.get(buffer));
                int dim = (int) uk.ac.manchester.tornado.tc.ir.analysis.Constants.integer(v(operands.get(1))).orElseThrow(
                        () -> new Unsupported("TC-E1003", "Tile.numTiles needs a constant dimension"));
                var extent = field(b, buffer, layout, dim == 0 ? "m" : "n");
                var shape = tuple(b, operands.get(2));
                var size = shape.get(Math.min(dim, shape.size() - 1));
                return b.binary(BinaryOp.DIV, b.binary(BinaryOp.SUB, b.binary(BinaryOp.ADD, extent, size), b.constInt(Type.I32, 1)), size);
            }
            case "load" -> {
                var view = tensorView(b, operands.get(0));
                var where = tuple(b, operands.get(1));
                var shape = tuple(b, operands.get(2));
                var parts = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(view));
                parts.addAll(rank2(b, shape, false));
                var partition = b.intrinsic("tile.partition", uk.ac.manchester.tornado.tc.ir.ApiTypes.PARTITION_VIEW, parts);
                var loadOps = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(partition));
                loadOps.addAll(rank2(b, where, true));
                return tile(b, "tile.load", loadOps, shape);
            }
            case "store" -> {
                var view = tensorView(b, operands.get(0));
                var where = tuple(b, operands.get(1));
                var value = v(operands.get(2));
                var parts = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(view));
                parts.addAll(rank2(b, dims(value), false));
                var partition = b.intrinsic("tile.partition", uk.ac.manchester.tornado.tc.ir.ApiTypes.PARTITION_VIEW, parts);
                var storeOps = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(partition, value));
                storeOps.addAll(rank2(b, where, true));
                b.intrinsic("tile.store", Type.VOID, storeOps);
                return null;
            }
            case "zeros" -> {
                var dims = args(operands);
                var ops = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(b.constInt(Type.I32, dtype("F32"))));
                ops.addAll(rank2(b, dims, false));
                return tile(b, "tile.zeros", ops, dims);
            }
            case "full" -> {
                var dims = tuple(b, operands.get(0));
                var ops = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(b.constInt(Type.I32, dtype("F32")), toF64(b, v(operands.get(1)))));
                ops.addAll(rank2(b, dims, false));
                return tile(b, "tile.full", ops, dims);
            }
            case "mma" -> {
                var acc = v(operands.get(2));
                return tile(b, "tile.mma", List.of(v(operands.get(0)), v(operands.get(1)), acc), dims(acc));
            }
            case "transpose" -> {
                var t = v(operands.get(0));
                var d = dims(t);
                return tile(b, "tile.unary.transpose", List.of(t), d.size() == 2 ? List.of(d.get(1), d.get(0)) : d);
            }
            case "arange" -> {
                // arange(size[, codeType]) and arange(size, start, step[, codeType]): 0, 1, ... (times step, plus start), as F32
                var size = v(operands.get(0));
                var dims = List.of(size);
                // CUDA Tile's iota is integral: S32, then cast to F32
                var ops = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(b.constInt(Type.I32, dtype("S32"))));
                ops.addAll(rank2(b, dims, false));
                var iota = tile(b, "tile.cast", List.of(tile(b, "tile.iota", ops, dims), b.constInt(Type.I32, dtype("F32"))), dims);
                if (operands.size() >= 3 && descriptor(operands.get(1).type()).equals("I")) {
                    var scaled = tile(b, "tile.scale", List.of(iota, toF64(b, v(operands.get(2)))), dims);
                    var fullOps = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(b.constInt(Type.I32, dtype("F32")), toF64(b, v(operands.get(1)))));
                    fullOps.addAll(rank2(b, dims, false));
                    return tile(b, "tile.binary.add", List.of(scaled, tile(b, "tile.full", fullOps, dims)), dims);
                }
                return iota;
            }
            case "reshape" -> {
                var t = v(operands.get(0));
                var dims = tuple(b, operands.get(1));
                var ops = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(t));
                ops.addAll(rank2(b, dims, false));
                return tile(b, "tile.reshape", ops, dims);
            }
            case "permute" -> {
                var t = v(operands.get(0));
                var d = dims(t);
                var order = tuple(b, operands.get(1)).stream().map(x -> uk.ac.manchester.tornado.tc.ir.analysis.Constants.integer(x).orElse(-1L)).toList();
                if (d.size() == 2 && order.equals(List.of(1L, 0L))) {
                    return tile(b, "tile.unary.transpose", List.of(t), List.of(d.get(1), d.get(0)));
                }
                if (order.equals(java.util.stream.LongStream.range(0, d.size()).boxed().toList())) {
                    return t;
                }
                throw new Unsupported("TC-E1003", "Tile.permute supports the identity and the 2D transpose (1, 0); got " + order);
            }
            case "sum", "min", "max" -> {
                if (name.equals("min") && operands.stream().allMatch(x -> descriptor(x.type()).equals("I"))) {
                    // A select, not math.min: CUDA Tile kernels cannot call device functions such as min().
                    var a = v(operands.get(0));
                    var c = v(operands.get(1));
                    return b.select(b.icmp(IntPredicate.SLT, a, c), a, c);
                }
                if (operands.size() == 2 && !descriptor(operands.get(0).type()).equals("I")) {
                    var t = v(operands.get(0));
                    var d = dims(t);
                    long axis = uk.ac.manchester.tornado.tc.ir.analysis.Constants.integer(v(operands.get(1))).orElseThrow(
                            () -> new Unsupported("TC-E1003", "Tile." + name + " needs a constant axis"));
                    var reduced = new ArrayList<>(d);
                    reduced.set((int) axis, b.constInt(Type.I32, 1));
                    return tile(b, "tile.reduce." + name, List.of(t, b.constInt(Type.I32, axis + (d.size() == 1 ? 1 : 0))), reduced);
                }
            }
            case "add", "sub", "mul", "truediv" -> {
                var x = v(operands.get(0));
                var y = v(operands.get(1));
                var intrinsic = "tile.binary." + (name.equals("truediv") ? "div" : name);
                if (tileDims.containsKey(x) && tileDims.containsKey(y)) {
                    // elementwise, broadcasting extents of 1: the result has the larger extents
                    return tile(b, intrinsic, List.of(x, y), broadcast(dims(x), dims(y)));
                }
                var t = tileDims.containsKey(x) ? x : y;
                var scalar = t == x ? y : x;
                if (name.equals("mul")) {
                    return tile(b, "tile.scale", List.of(t, toF64(b, scalar)), dims(t));
                }
                var fullOps = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(List.of(b.constInt(Type.I32, dtype("F32")), toF64(b, scalar)));
                fullOps.addAll(rank2(b, dims(t), false));
                var full = tile(b, "tile.full", fullOps, dims(t));
                return tile(b, intrinsic, t == x ? List.of(t, full) : List.of(full, t), dims(t));
            }
            default -> {
            }
        }
        throw new Unsupported("TC-E1003", "HAT tile operation " + name + " is not supported yet");
    }

    /** A scalar for a tile operand, as a double; a constant stays a constant (CUDA Tile fill values are literals). */
    private static uk.ac.manchester.tornado.tc.ir.Value toF64(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value x) {
        if (x instanceof Inst c && c.opcode() instanceof Opcode.ConstFloat(var value)) {
            return b.constFloat(Type.F64, value);
        }
        if (x instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var value)) {
            return b.constFloat(Type.F64, value);
        }
        if (x.type().equals(Type.F64)) {
            return x;
        }
        return b.convert(x.type().isFloat() ? ConvertOp.FEXT : ConvertOp.I2F, x, Type.F64);
    }

    /**
     * The extents of an elementwise result, axis by axis: an extent that is the constant 1 is broadcast
     * to the other operand's (a (1, n) bias row over an (m, n) tile is (m, n)).
     */
    private static List<uk.ac.manchester.tornado.tc.ir.Value> broadcast(List<uk.ac.manchester.tornado.tc.ir.Value> x, List<uk.ac.manchester.tornado.tc.ir.Value> y) {
        if (x.size() != y.size()) {
            return x.size() > y.size() ? x : y;
        }
        var result = new ArrayList<uk.ac.manchester.tornado.tc.ir.Value>(x.size());
        for (int i = 0; i < x.size(); i++) {
            boolean xOne = uk.ac.manchester.tornado.tc.ir.analysis.Constants.integer(x.get(i)).orElse(-1L) == 1;
            result.add(xOne ? y.get(i) : x.get(i));
        }
        return result;
    }

    // ---- HAT tensors (Tensor): warp-level 16x16x16 F16 multiply-accumulate on TC's m16n8k16 intrinsics

    /**
     * A HAT tensor: an operand loaded from a matrix (its fragments are loaded where an mma uses it, as
     * A or B), or an accumulator kept in a private slot of two m16n8 halves (loop-carried by reference).
     */
    private record TensorValue(uk.ac.manchester.tornado.tc.ir.Value matrix, uk.ac.manchester.tornado.tc.ir.Value row, uk.ac.manchester.tornado.tc.ir.Value col,
            uk.ac.manchester.tornado.tc.ir.Value ld, boolean columnMajor, uk.ac.manchester.tornado.tc.ir.Value accumulator) {
    }

    private final Map<Value, TensorValue> tensors = new HashMap<>();
    private final Map<Value, Boolean> tensorLayouts = new HashMap<>();

    private TensorValue tensor(Value value) {
        var t = tensors.get(resolve(value));
        if (t == null) {
            throw new Unsupported("TC-E1003", "this HAT tensor cannot be followed");
        }
        return t;
    }

    private static final Type.VectorType ACC = new Type.VectorType(Type.F32, 4);

    /** The element offset of a HAT buffer's array (its header), in elements. */
    private uk.ac.manchester.tornado.tc.ir.Value headerElements(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value buffer) {
        var index = bufferParams.get(buffer);
        var layout = index == null ? null : context.bufferLayout(index);
        if (layout == null || !(layout.memberLayouts().getLast() instanceof SequenceLayout array)) {
            throw new Unsupported("TC-E1003", "a HAT tensor load or store needs a buffer parameter with a known layout");
        }
        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(array.name().orElseThrow()));
        return b.constInt(Type.I32, offset / array.elementLayout().byteSize());
    }

    private uk.ac.manchester.tornado.tc.ir.Value hatTensor(IrBuilder b, JavaOp.InvokeOp op, String name) {
        var operands = op.operands();
        switch (name) {
            case "shape" -> {
                return null;
            }
            case "ofColumnMajor", "ofRowMajor" -> {
                tensorLayouts.put(op.result(), name.equals("ofColumnMajor"));
                return null;
            }
            case "create", "zeros" -> {
                var slot = entrySlot(new Type.VectorType(Type.F32, 8));
                fillSlot(b, slot, b.constFloat(Type.F32, 0.0));
                tensors.put(op.result(), new TensorValue(null, null, null, null, false, slot));
                return null;
            }
            case "fill" -> {
                fillSlot(b, tensor(operands.get(0)).accumulator(), v(operands.get(1)));
                return null;
            }
            case "loadF16" -> {
                var matrix = v(operands.get(0));
                boolean columnMajor = operands.size() == 6 && Boolean.TRUE.equals(tensorLayouts.get(resolve(operands.get(5))));
                // HAT's header is folded into the contiguous coordinate (addresses are linear in it)
                var row = v(operands.get(1));
                var col = v(operands.get(2));
                var header = headerElements(b, matrix);
                if (columnMajor) {
                    row = b.add(row, header);
                } else {
                    col = b.add(col, header);
                }
                tensors.put(op.result(), new TensorValue(matrix, row, col, v(operands.get(3)), columnMajor, null));
                return null;
            }
            case "mma" -> {
                var a = tensor(operands.get(0));
                var bt = tensor(operands.get(1));
                var acc = tensor(operands.get(2));
                if (a.accumulator() != null || bt.accumulator() != null || acc.accumulator() == null) {
                    throw new Unsupported("TC-E1003", "Tensor.mma takes two loaded tensors and an accumulator");
                }
                var fragmentA = b.intrinsic(a.columnMajor() ? "tornado.mma.load_a.global_col" : "tornado.mma.load_a.global", new Type.VectorType(Type.I32, 4),
                        List.of(a.matrix(), a.row(), a.col(), a.ld()));
                for (int half = 0; half < 2; half++) {
                    // B's n columns 8 * half .. 8 * half + 7
                    var bRow = bt.columnMajor() ? bt.row() : bt.row();
                    var bCol = b.add(bt.col(), b.constInt(Type.I32, 8 * half));
                    var fragmentB = b.intrinsic(bt.columnMajor() ? "tornado.mma.load_b.global_col" : "tornado.mma.load_b.global", new Type.VectorType(Type.I32, 2),
                            List.of(bt.matrix(), bRow, bCol, bt.ld()));
                    var address = b.pointerOffset(acc.accumulator(), b.constInt(Type.I64, 16L * half));
                    var c = loadAcc(b, address);
                    var d = b.intrinsic("tornado.mma.m16n8k16", ACC, List.of(fragmentA, fragmentB, c));
                    storeVector(b, address, d);
                }
                tensors.put(op.result(), acc);
                return null;
            }
            case "store" -> {
                var matrix = v(operands.get(0));
                var acc = tensor(operands.get(3));
                boolean columnMajor = operands.size() == 6 && Boolean.TRUE.equals(tensorLayouts.get(resolve(operands.get(5))));
                var row = v(operands.get(1));
                var col = v(operands.get(2));
                var header = headerElements(b, matrix);
                if (columnMajor) {
                    row = b.add(row, header);
                } else {
                    col = b.add(col, header);
                }
                for (int half = 0; half < 2; half++) {
                    var d = loadAcc(b, b.pointerOffset(acc.accumulator(), b.constInt(Type.I64, 16L * half)));
                    b.intrinsic(columnMajor ? "tornado.mma.store_col" : "tornado.mma.store", Type.VOID,
                            List.of(d, matrix, row, b.add(col, b.constInt(Type.I32, 8 * half)), v(operands.get(4))));
                }
                return null;
            }
            default -> {
            }
        }
        throw new Unsupported("TC-E1003", "hat.types.Tensor." + name + " is not supported yet");
    }

    private uk.ac.manchester.tornado.tc.ir.Value entrySlot(Type.VectorType type) {
        return slotFor(type);
    }

    private static void fillSlot(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value slot, uk.ac.manchester.tornado.tc.ir.Value value) {
        for (int l = 0; l < 8; l++) {
            b.store(b.pointerOffset(slot, b.constInt(Type.I64, 4L * l)), value);
        }
    }

    private static uk.ac.manchester.tornado.tc.ir.Value loadAcc(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value address) {
        return loadVector(b, address, ACC);
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
            boolean half = Type.F16.equals(elementTypes.get(address));
            if (args.size() == 1) {
                var loaded = half ? b.convert(ConvertOp.BITCAST, b.load(address, Type.F16), Type.I16) : b.load(address, Type.I16);
                return b.convert(ConvertOp.SEXT, loaded, Type.I32);
            }
            var bits = args.get(1);
            var shortBits = bits.type().equals(Type.I16) ? bits : b.convert(ConvertOp.TRUNC, bits, Type.I16);
            b.store(address, half ? b.convert(ConvertOp.BITCAST, shortBits, Type.F16) : shortBits);
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
        if (name.equals("WRS")) {
            return b.constInt(Type.I32, 32); // the warp size of the devices HAT's warp operations target (NVIDIA)
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
    private record View(uk.ac.manchester.tornado.tc.ir.Value buffer, uk.ac.manchester.tornado.tc.ir.Value offset, GroupLayout layout, String member) {
        View(uk.ac.manchester.tornado.tc.ir.Value buffer, uk.ac.manchester.tornado.tc.ir.Value offset, GroupLayout layout) {
            this(buffer, offset, layout, null);
        }
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
            return element(b, op, new View(view.buffer(), offset, null, member(view, name)), seq.elementLayout(), args.subList(1, args.size()), name);
        }
        return element(b, op, new View(view.buffer(), base, null, member(view, name)), field, args, name);
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
            var param = bufferParams.get(at.buffer());
            if (param != null && at.member() != null && lastStore != null) {
                memberStores.add(lastStore);
                writes.add(new String[] { String.valueOf(param), at.member() });
            }
            return null;
        }
        if (element instanceof GroupLayout group && rest.isEmpty()) {
            views.put(op.result(), new View(at.buffer(), at.offset(), group, at.member()));
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

    private void memStore(IrBuilder b, uk.ac.manchester.tornado.tc.ir.Value memory, uk.ac.manchester.tornado.tc.ir.Value offset, uk.ac.manchester.tornado.tc.ir.Value value) {
        if (isAlloca(memory)) {
            b.store(b.pointerOffset(memory, offset), value);
            lastStore = null;
        } else {
            lastStore = b.arrayStore(memory, offset, value);
        }
    }

    /** The top-level member of a HAT buffer an access is in: the view's, or this field when the view is the buffer. */
    private static String member(View view, String field) {
        return view.member() != null ? view.member() : view.layout() != null ? field : null;
    }

    // ---- which members of its HAT buffer parameters a function writes (for HatWrites)

    private uk.ac.manchester.tornado.tc.ir.Inst lastStore;
    private final java.util.Set<uk.ac.manchester.tornado.tc.ir.Inst> memberStores = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final List<String[]> writes = new ArrayList<>();

    /** Records this function's writes to its buffer parameters: by member where known, else all of the buffer. */
    private void recordWrites() {
        var method = context.method();
        if (method == null) {
            return;
        }
        HatWrites.analysed(method);
        writes.forEach(w -> HatWrites.member(method, Integer.parseInt(w[0]), w[1]));
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                boolean writesMemory = inst.opcode() instanceof Opcode.ArrayStore && !memberStores.contains(inst)
                        || inst.opcode() instanceof Opcode.Intrinsic(var n) && (n.startsWith("tile.store") || n.startsWith("tile.atomic") || n.startsWith("tornado.atomic")
                                || n.startsWith("tornado.mma.store") || n.equals("tornado.async_copy") || n.equals("tornado.vector.store_param"));
                if (writesMemory) {
                    for (var operand : inst.operands()) {
                        var param = bufferParams.get(operand);
                        if (param != null) {
                            HatWrites.all(method, param);
                        }
                    }
                }
            }
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

    /** java.lang.Math methods with a TC intrinsic; the others are compiled from their Java bodies. */
    private static final java.util.Set<String> MATH = java.util.Set.of("sqrt", "exp", "log", "sin", "cos", "tan", "tanh", "abs", "floor", "ceil", "rint", "atan", "asin", "acos",
            "min", "max", "pow", "fma", "atan2");

    private static Inst math(IrBuilder b, String name, List<uk.ac.manchester.tornado.tc.ir.Value> args, Type type) {
        return switch (name) {
            case "sqrt", "exp", "log", "sin", "cos", "tan", "tanh", "abs", "floor", "ceil", "rint", "atan", "asin", "acos", "min", "max", "pow", "fma", "atan2" ->
                b.intrinsic("math." + name, type, args);
            default -> throw new Unsupported("TC-E1003", "Math." + name + " is not supported by the Babylon frontend yet");
        };
    }
}
