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

    private AbiLowering(TargetCaps target) {
        this.target = target;
    }

    /** Lowers {@code function} in place; returns diagnostics for constructs it cannot lower. */
    public static List<Diagnostic> run(Function function, TargetCaps target) {
        var lowering = new AbiLowering(target);
        lowering.lower(function);
        return List.copyOf(lowering.diagnostics);
    }

    private void lower(Function function) {
        var printer = new IrPrinter(false);
        printer.printFunction(function);
        for (var param : List.copyOf(function.params())) {
            switch (param.type()) {
                case ArrayType a -> param.setType(new Type.PointerType(AddressSpace.GLOBAL));
                // The ABI drops KernelContext: its uses were replaced by intrinsics in the frontend.
                case Type.ObjectType o when o.className().equals("KernelContext") && !param.hasUses() -> function.entry().removeArg(param);
                case Type.ObjectType o -> diagnostics.add(new Diagnostic("TC-E3101", "object parameters are not lowered yet: " + o.text(), "%" + printer.name(param), uk.ac.manchester.tornado.tc.ir.Loc.UNKNOWN));
                default -> {
                }
            }
        }
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                switch (inst.opcode()) {
                    case Opcode.ArrayLoad _l -> lowerLoad(inst);
                    case Opcode.ArrayStore _s -> lowerStore(inst);
                    case Opcode.ArrayLength _l -> lowerLength(inst);
                    case Opcode.FieldLoad _f -> unsupported(inst, printer);
                    case Opcode.FieldStore _f -> unsupported(inst, printer);
                    default -> {
                    }
                }
            }
        }
    }

    private void lowerLoad(Inst inst) {
        var element = inst.type();
        var b = new IrBuilder(inst.block().orElseThrow()).before(inst).at(inst.loc());
        var address = elementAddress(b, inst.operand(0), inst.operand(1), element);
        var load = b.load(address, element);
        load.setName(inst.name());
        inst.replaceAllUsesWith(load);
        inst.erase();
    }

    private void lowerStore(Inst inst) {
        var element = inst.operand(2).type();
        var b = new IrBuilder(inst.block().orElseThrow()).before(inst).at(inst.loc());
        var address = elementAddress(b, inst.operand(0), inst.operand(1), element);
        b.store(address, inst.operand(2));
        inst.erase();
    }

    private void lowerLength(Inst inst) {
        var b = new IrBuilder(inst.block().orElseThrow()).before(inst).at(inst.loc());
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
