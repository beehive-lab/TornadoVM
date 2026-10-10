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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Type.ArrayType;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * TornadoVM kernel ABI lowering (optimisation list item T7): Java-level arrays become byte pointers
 * into global memory, following the native-array layout that TornadoVM's runtime uses: a header of
 * {@link TargetCaps#arrayHeaderBytes()} bytes whose first 64-bit word is the element count, followed
 * by the elements.
 *
 * <ul>
 * <li>{@code array<T>} parameters become {@code ptr<global>} (the value is retyped in place).</li>
 * <li>{@code array.load %a[%i]} becomes {@code load(ptr.offset(ptr.offset(%a, header), sext(%i) * sizeof(T)))}.</li>
 * <li>{@code array.store} likewise becomes a {@code store}.</li>
 * <li>{@code array.length %a} becomes {@code trunc(load i64 %a)}.</li>
 * <li>An unused {@code KernelContext} parameter is removed from the signature.</li>
 * </ul>
 *
 * <p>Error codes: {@code TC-E3101} construct not yet supported by ABI lowering.</p>
 */
public final class AbiLowering {

    private final TargetCaps target;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /** Element type of each array parameter, recorded before parameters become pointers. */
    private final java.util.Map<Value, Type> elements = new java.util.IdentityHashMap<>();

    /** Layouts of the object arguments' device copies, by simple class name. */
    private final java.util.Map<String, ObjectLayout> layouts;
    /** Class of each object-valued pointer (parameters and loaded references). */
    private final java.util.Map<Value, String> classes = new java.util.IdentityHashMap<>();

    private AbiLowering(TargetCaps target, java.util.Map<String, ObjectLayout> layouts) {
        this.target = target;
        this.layouts = layouts;
    }

    /** Lowers {@code function} in place; returns diagnostics for constructs it cannot lower. */
    public static List<Diagnostic> run(Function function, TargetCaps target) {
        return run(function, target, List.of());
    }

    /** As {@link #run(Function, TargetCaps)}, with the layouts of object arguments ({@link ObjectLayout}). */
    public static List<Diagnostic> run(Function function, TargetCaps target, List<ObjectLayout> layouts) {
        var byName = new java.util.HashMap<String, ObjectLayout>();
        layouts.forEach(l -> byName.putIfAbsent(l.className(), l));
        var lowering = new AbiLowering(target, byName);
        lowering.lower(function);
        return List.copyOf(lowering.diagnostics);
    }

    private void lower(Function function) {
        var printer = new IrPrinter(false);
        printer.printFunction(function);
        for (var param : List.copyOf(function.params())) {
            switch (param.type()) {
                case ArrayType(var element) -> {
                    elements.put(param, element);
                    param.setType(new Type.PointerType(AddressSpace.GLOBAL));
                }
                // The ABI drops KernelContext: its uses were replaced by intrinsics in the frontend.
                // and AtomicIntegers: they are slots of the atomics region (AtomicSlots), not arguments.
                case Type.ObjectType o when (o.className().equals("KernelContext") || o.className().equals("TileContext") || o.className().equals("AtomicInteger")) && !param.hasUses() -> function.entry().removeArg(param);
                case Type.VectorType vec -> lowerVectorParam(function, param, vec);
                case Type.ObjectType o when layouts.containsKey(o.className()) -> {
                    classes.put(param, o.className());
                    param.setType(new Type.PointerType(AddressSpace.GLOBAL));
                }
                case Type.ObjectType o -> diagnostics.add(new Diagnostic("TC-E3101", "object parameters are not lowered yet: " + o.text(), "%" + printer.name(param), uk.ac.manchester.tornado.tc.ir.Loc.UNKNOWN));
                default -> {
                }
            }
        }
        // An array chosen at run time (a ternary, a loop-carried array) reaches a block argument: it is
        // the same device pointer as the parameters that flow into it (TP-5).
        for (var block : function.blocks()) {
            if (block == function.entry()) {
                continue;
            }
            for (var arg : block.args()) {
                if (arg.type() instanceof ArrayType(var element)) {
                    elements.put(arg, element);
                    arg.setType(new Type.PointerType(AddressSpace.GLOBAL));
                }
            }
        }
        // Dominator order: a reference is lowered (and its class known) before the fields read through it.
        for (var block : new uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree(function).reversePostorder()) {
            for (var inst : List.copyOf(block.insts())) {
                switch (inst.opcode()) {
                    case Opcode.ArrayLoad _l -> lowerLoad(inst);
                    case Opcode.ArrayStore _s -> lowerStore(inst);
                    case Opcode.ArrayLength _l -> lowerLength(inst);
                    case Opcode.FieldLoad(var field) when classes.containsKey(inst.operand(0)) -> lowerField(inst, field, printer);
                    case Opcode.Intrinsic(var name) when name.startsWith("tornado.atomic.") && elements.containsKey(inst.operand(0)) -> lowerAtomic(inst);
                    // A device launch passes arrays as they are (header included): the child has the same ABI.
                    case Opcode.Intrinsic(var name) when name.equals("tornado.device_launch") -> {
                    }
                    // Other intrinsics taking a whole array (mmaStore) get its data pointer.
                    case Opcode.Intrinsic _i when inst.operands().stream().anyMatch(elements::containsKey) -> {
                        var b = IrBuilder.insertingBefore(inst);
                        for (int k = 0; k < inst.operandCount(); k++) {
                            if (elements.containsKey(inst.operand(k))) {
                                var array = inst.operand(k);
                                var data = b.pointerOffset(array, b.constInt(Type.I64, target.arrayHeaderBytes()));
                                data.setName(array.name().isEmpty() ? "data" : array.name() + "_data");
                                inst.setOperand(k, data);
                            }
                        }
                    }
                    case Opcode.FieldLoad _f -> unsupported(inst, printer);
                    case Opcode.FieldStore(var field) when classes.containsKey(inst.operand(0)) -> lowerFieldStore(inst, field, printer);
                    case Opcode.FieldStore _f -> unsupported(inst, printer);
                    default -> {
                    }
                }
            }
        }
    }

    private void lowerLoad(Inst inst) {
        var b = IrBuilder.insertingBefore(inst);
        // A byte array read as another type (ByteArray.getHalfFloat) is indexed in bytes.
        var address = elementAddress(b, inst.operand(0), inst.operand(1), elements.getOrDefault(inst.operand(0), inst.type()));
        var load = b.load(address, inst.type());
        load.setName(inst.name());
        inst.replaceAllUsesWith(load);
        inst.erase();
    }

    private void lowerStore(Inst inst) {
        var b = IrBuilder.insertingBefore(inst);
        var address = elementAddress(b, inst.operand(0), inst.operand(1), elements.getOrDefault(inst.operand(0), inst.operand(2).type()));
        b.store(address, inst.operand(2));
        inst.erase();
    }

    /**
     * A field of an object argument's device copy: primitives in place at the field's JVM offset;
     * a reference as {@code object + 8 * u32} (the runtime's compressed relative offset).
     */
    private void lowerField(Inst inst, String field, IrPrinter printer) {
        var object = inst.operand(0);
        var layout = layouts.get(classes.get(object));
        var offset = layout.offsets().get(field);
        if (offset == null) {
            diagnostics.add(new Diagnostic("TC-E3102", "field '" + field + "' is not in the device layout of " + layout.className(), printer.instText(inst), inst.loc()));
            return;
        }
        var b = IrBuilder.insertingBefore(inst);
        var address = b.pointerOffset(object, b.constInt(Type.I64, offset));
        Value result;
        switch (inst.type()) {
            case ArrayType(var element) -> {
                result = reference(b, object, address);
                elements.put(result, element);
            }
            case Type.ObjectType o -> {
                result = reference(b, object, address);
                classes.put(result, layout.referenceClasses().getOrDefault(field, o.className()));
            }
            default -> result = b.load(address, inst.type());
        }
        result.setName(inst.name().isEmpty() || inst.name().chars().allMatch(Character::isDigit) ? object.name() + "_" + field : inst.name());
        inst.replaceAllUsesWith(result);
        inst.erase();
    }

    /**
     * A vector argument ({@code Float4} …) arrives as a pointer to its lanes (the runtime copies the
     * vector's storage array, without a header); the kernel works on the value loaded at entry.
     */
    private static void lowerVectorParam(Function function, uk.ac.manchester.tornado.tc.ir.BlockArg param, Type.VectorType vec) {
        var users = List.copyOf(param.users());
        param.setType(new Type.PointerType(AddressSpace.GLOBAL));
        var entry = function.entry();
        var first = entry.insts().isEmpty() ? null : entry.insts().get(0);
        var b = first == null ? new IrBuilder(entry).atEnd(entry) : new IrBuilder(entry).before(first);
        var lanes = new java.util.ArrayList<Value>();
        for (int l = 0; l < vec.length(); l++) {
            var address = l == 0 ? param : b.pointerOffset(param, b.constInt(Type.I64, (long) l * sizeOf(vec.element())));
            lanes.add(b.load(address, vec.element()));
        }
        var value = b.intrinsic("tornado.vector.make", vec, lanes);
        value.setName(param.name());
        for (var user : users) {
            // A vector parameter changed by setters: its lanes are stored back (the host copies them out).
            if (user.opcode() instanceof Opcode.Intrinsic(var name) && name.equals("tornado.vector.store_param") && user.operand(0) == param) {
                var updated = user.operand(1);
                if (updated != param) {
                    var s = IrBuilder.insertingBefore(user);
                    for (int l = 0; l < vec.length(); l++) {
                        var address = l == 0 ? param : s.pointerOffset(param, s.constInt(Type.I64, (long) l * sizeOf(vec.element())));
                        s.store(address, s.intrinsic("tornado.vector.lane", uk.ac.manchester.tornado.tc.ir.Immediates.of("lane", l), vec.element(), List.of(updated == param ? value : updated)));
                    }
                }
                user.erase();
                continue;
            }
            for (int k = 0; k < user.operandCount(); k++) {
                if (user.operand(k) == param) {
                    user.setOperand(k, value);
                }
            }
        }
    }

    /** A primitive field written in the object's device copy (the runtime copies the fields back). */
    private void lowerFieldStore(Inst inst, String field, IrPrinter printer) {
        var object = inst.operand(0);
        var layout = layouts.get(classes.get(object));
        var offset = layout.offsets().get(field);
        var value = inst.operand(1);
        if (offset == null || !(value.type().isInteger() || value.type().isFloat())) {
            diagnostics.add(new Diagnostic("TC-E3102", "field '" + field + "' cannot be written in the device copy of " + layout.className(), printer.instText(inst), inst.loc()));
            return;
        }
        var b = IrBuilder.insertingBefore(inst);
        b.store(b.pointerOffset(object, b.constInt(Type.I64, offset)), value);
        inst.erase();
    }

    private static Value reference(IrBuilder b, Value object, Value address) {
        var compressed = b.load(address, Type.I32);
        var wide = b.convert(ConvertOp.ZEXT, compressed, Type.I64);
        var bytes = b.binary(BinaryOp.MUL, wide, b.constInt(Type.I64, ObjectLayout.REFERENCE_SCALE));
        return b.pointerOffset(object, bytes);
    }

    /** An atomic on a global array element: {@code (array, index, values...)} becomes {@code (address, values...)}. */
    private void lowerAtomic(Inst inst) {
        var b = IrBuilder.insertingBefore(inst);
        var operands = new java.util.ArrayList<Value>(inst.operands());
        var address = elementAddress(b, operands.get(0), operands.get(1), elements.get(operands.get(0)));
        operands.subList(0, 2).clear();
        operands.add(0, address);
        inst.setOperands(operands);
    }

    private void lowerLength(Inst inst) {
        var b = IrBuilder.insertingBefore(inst);
        var count = b.load(inst.operand(0), Type.I64);
        var length = inst.type().equals(Type.I64) ? count : b.convert(ConvertOp.TRUNC, count, Type.I32);
        length.setName(!inst.name().isEmpty() && !inst.name().chars().allMatch(Character::isDigit) ? inst.name() : inst.operand(0).name() + "_len");
        inst.replaceAllUsesWith(length);
        inst.erase();
    }

    /**
     * {@code (array + header) + sext(index) * sizeof(element)}. The data pointer {@code array + header}
     * is a separate value named {@code <array>_data}: GVN shares it and LICM hoists it, and the emitter
     * prints accesses through it as {@code a_data[i]}.
     */
    private Value elementAddress(IrBuilder b, Value array, Value index, Type element) {
        if (array.type() instanceof Type.PointerType(var space) && space != AddressSpace.GLOBAL) {
            // A local (shared/private) array reached a Java-level access through an inlined helper: no header.
            var wide = index.type().equals(Type.I64) ? index : b.convert(ConvertOp.SEXT, index, Type.I64);
            return b.pointerOffset(array, b.binary(BinaryOp.MUL, wide, b.constInt(Type.I64, sizeOf(element))));
        }
        var data = b.pointerOffset(array, b.constInt(Type.I64, target.arrayHeaderBytes()));
        data.setName(array.name().isEmpty() ? "data" : array.name() + "_data");
        var wide = index.type().equals(Type.I64) ? index : b.convert(ConvertOp.SEXT, index, Type.I64);
        var scaled = b.binary(BinaryOp.MUL, wide, b.constInt(Type.I64, sizeOf(element)));
        return b.pointerOffset(data, scaled);
    }

    static int sizeOf(Type type) {
        return switch (type) {
            case Type.IntType(var bits) -> bits == 1 ? 1 : bits / 8;
            case Type.FloatType f -> f.bits() / 8;
            case Type.VectorType(var element, var length) -> sizeOf(element) * length;
            default -> throw new IllegalArgumentException("no ABI size for " + type.text());
        };
    }

    private void unsupported(Inst inst, IrPrinter printer) {
        diagnostics.add(new Diagnostic("TC-E3101", "'" + inst.opcode().mnemonic() + "' is not lowered yet", printer.instText(inst), inst.loc()));
    }
}
