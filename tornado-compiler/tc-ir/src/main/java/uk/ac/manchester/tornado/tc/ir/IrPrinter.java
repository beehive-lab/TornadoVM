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
package uk.ac.manchester.tornado.tc.ir;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prints TC-SSA in its text form (see {@code ir-specification.md} §1.6). The output is accepted by
 * {@link IrParser}; printing, parsing and printing again yields identical text.
 *
 * <p>Names: a value keeps its name hint when it is unique within the function; otherwise a
 * {@code .N} suffix is added. Values without a hint are numbered. Blocks are named the same way,
 * with {@code entry} and {@code bbN} as defaults.</p>
 */
public final class IrPrinter {

    private final Map<Value, String> valueNames = new IdentityHashMap<>();
    private final Map<Block, String> blockNames = new IdentityHashMap<>();
    private final boolean printLocations;

    public IrPrinter() {
        this(true);
    }

    public IrPrinter(boolean printLocations) {
        this.printLocations = printLocations;
    }

    public static String print(IrModule module) {
        return new IrPrinter().printModule(module);
    }

    public static String print(Function function) {
        return new IrPrinter().printFunction(function);
    }

    public String printModule(IrModule module) {
        return module.functions().stream().map(this::printFunction).collect(Collectors.joining("\n"));
    }

    public String printFunction(Function function) {
        assignNames(function);
        var sb = new StringBuilder();
        sb.append(function.kind().text).append(" @").append(function.name()).append('(');
        sb.append(function.params().stream().map(p -> "%" + valueNames.get(p) + ": " + p.type().text()).collect(Collectors.joining(", ")));
        sb.append(") -> ").append(function.returnType().text());
        appendAttributes(sb, function.attributes());
        sb.append(" {\n");
        var first = true;
        for (var block : function.blocks()) {
            sb.append('^').append(blockNames.get(block));
            if (!first && !block.args().isEmpty()) {
                sb.append('(').append(block.args().stream().map(a -> "%" + valueNames.get(a) + ": " + a.type().text()).collect(Collectors.joining(", "))).append(')');
            }
            appendAttributes(sb, block.attributes());
            sb.append(":\n");
            for (var inst : block.insts()) {
                sb.append("  ").append(instText(inst)).append('\n');
            }
            first = false;
        }
        return sb.append("}\n").toString();
    }

    /** The text of one instruction, using the names assigned for its function. */
    public String instText(Inst inst) {
        var sb = new StringBuilder();
        if (inst.producesValue()) {
            sb.append('%').append(name(inst)).append(" = ");
        }
        var mnemonic = inst.opcode().mnemonic();
        switch (inst.opcode()) {
            case Opcode.ConstInt(var value) -> sb.append("const ").append(value);
            case Opcode.ConstFloat(var value) -> sb.append("const ").append(floatText(value, inst.type()));
            case Opcode.Binary _b -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.Unary _u -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.ICmp _c -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.FCmp _c -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.Select _s -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.Convert _c -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.ArrayLoad _l -> sb.append(mnemonic).append(' ').append(ref(inst.operand(0))).append('[').append(ref(inst.operand(1))).append(']');
            case Opcode.ArrayStore _s -> sb.append(mnemonic).append(' ').append(ref(inst.operand(0))).append('[').append(ref(inst.operand(1))).append("], ").append(ref(inst.operand(2)));
            case Opcode.ArrayLength _l -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.FieldLoad(var field) -> sb.append(mnemonic).append(' ').append(ref(inst.operand(0))).append(", ").append(quote(field));
            case Opcode.FieldStore(var field) -> sb.append(mnemonic).append(' ').append(ref(inst.operand(0))).append(", ").append(quote(field)).append(", ").append(ref(inst.operand(1)));
            case Opcode.PointerOffset _p -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.Load _l -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.Store _s -> sb.append(mnemonic).append(' ').append(operands(inst));
            case Opcode.Alloca(var space, var element, var count) -> sb.append(mnemonic).append(' ').append(element.text()).append('[').append(count).append(']');
            case Opcode.Call(var callee) -> sb.append("call @").append(callee).append('(').append(operands(inst)).append(')');
            case Opcode.Intrinsic(var name) -> sb.append(name).append('(').append(operands(inst)).append(')');
            case Opcode.Branch _b -> sb.append("br ").append(successor(inst, 0));
            case Opcode.CondBranch _c -> sb.append("cond_br ").append(ref(inst.operand(0))).append(", ").append(successor(inst, 0)).append(", ").append(successor(inst, 1));
            case Opcode.Switch(var keys) -> {
                sb.append("switch ").append(ref(inst.operand(0))).append(" [");
                for (int i = 0; i < keys.size(); i++) {
                    sb.append(i == 0 ? "" : ", ").append(keys.get(i)).append(": ").append(successor(inst, i));
                }
                sb.append("] default ").append(successor(inst, keys.size()));
            }
            case Opcode.Return _r -> sb.append(inst.operandCount() == 0 ? "ret" : "ret " + ref(inst.operand(0)));
            case Opcode.Unreachable _u -> sb.append("unreachable");
        }
        if (inst.producesValue()) {
            sb.append(" : ").append(inst.type().text());
        }
        appendAttributes(sb, inst.attributes());
        if (printLocations && inst.loc().isKnown()) {
            sb.append("  ").append(inst.loc().text());
        }
        return sb.toString();
    }

    /** The printed name of {@code value} (without {@code %}); assigns one on demand. */
    public String name(Value value) {
        return valueNames.computeIfAbsent(value, v -> "?" + Integer.toHexString(System.identityHashCode(v)));
    }

    public String blockName(Block block) {
        return blockNames.computeIfAbsent(block, b -> "?" + b.label());
    }

    private String operands(Inst inst) {
        return inst.operands().stream().map(this::ref).collect(Collectors.joining(", "));
    }

    private String ref(Value value) {
        return "%" + name(value);
    }

    private String successor(Inst inst, int index) {
        var successor = inst.successors().get(index);
        var text = "^" + blockName(successor.target());
        if (successor.args().isEmpty()) {
            return text;
        }
        return text + "(" + successor.args().stream().map(this::ref).collect(Collectors.joining(", ")) + ")";
    }

    private static void appendAttributes(StringBuilder sb, List<Attribute> attributes) {
        if (!attributes.isEmpty()) {
            sb.append(" [").append(attributes.stream().map(Attribute::text).collect(Collectors.joining(", "))).append(']');
        }
    }

    /** The text form of a floating-point constant of {@code type} (nan, inf, -0.0, shortest digits). */
    public static String floatText(double value, Type type) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (type instanceof Type.FloatType f && f.bits() <= 32) {
            return Float.toString((float) value);
        }
        return Double.toString(value);
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private void assignNames(Function function) {
        valueNames.clear();
        blockNames.clear();
        Set<String> usedValues = new HashSet<>();
        Set<String> usedBlocks = new HashSet<>();
        var counter = new int[] { 0 };
        for (var block : function.blocks()) {
            var defaultLabel = block == function.entry() ? "entry" : "bb" + function.blocks().indexOf(block);
            blockNames.put(block, unique(sanitize(block.label()).isEmpty() ? defaultLabel : sanitize(block.label()), usedBlocks));
            for (var arg : block.args()) {
                valueNames.put(arg, valueName(arg, usedValues, counter));
            }
            for (var inst : block.insts()) {
                if (inst.producesValue()) {
                    valueNames.put(inst, valueName(inst, usedValues, counter));
                }
            }
        }
    }

    private static String valueName(Value value, Set<String> used, int[] counter) {
        var hint = sanitize(value.name());
        if (hint.isEmpty() || isNumber(hint)) {
            String candidate;
            do {
                candidate = Integer.toString(counter[0]++);
            } while (used.contains(candidate));
            used.add(candidate);
            return candidate;
        }
        return unique(hint, used);
    }

    private static String unique(String base, Set<String> used) {
        var candidate = base;
        for (int k = 1; used.contains(candidate); k++) {
            candidate = base + "." + k;
        }
        used.add(candidate);
        return candidate;
    }

    static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_.$]", "_");
    }

    private static boolean isNumber(String text) {
        return text.chars().allMatch(Character::isDigit);
    }
}
