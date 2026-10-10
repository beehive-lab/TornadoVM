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

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.UnaryOp;
import uk.ac.manchester.tornado.tc.ir.Type.ArrayType;
import uk.ac.manchester.tornado.tc.ir.Type.FloatType;
import uk.ac.manchester.tornado.tc.ir.Type.IntType;
import uk.ac.manchester.tornado.tc.ir.Type.ObjectType;
import uk.ac.manchester.tornado.tc.ir.Type.PointerType;
import uk.ac.manchester.tornado.tc.ir.Type.VectorType;
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;

/**
 * Checks the structural and type rules of TC-SSA ({@code ir-specification.md} §1.5) and reports
 * every violation as a {@link Diagnostic} that shows the offending instruction in context.
 *
 * <p>Codes: {@code TC-E2101} missing terminator, {@code E2102} terminator not last, {@code E2103}
 * successor argument count, {@code E2104} successor argument type, {@code E2105} use not dominated
 * by its definition, {@code E2106} operand count, {@code E2107} operand type, {@code E2108} result
 * type, {@code E2109} return type, {@code E2110} value from another function or detached,
 * {@code E2111} use of a void value, {@code E2112} Java-level construct after ABI lowering,
 * {@code E2113} shared memory outside a kernel, {@code E2114} store to constant memory,
 * {@code E2116} entry block has predecessors, {@code E2117} function without blocks.</p>
 */
public final class Verifier {

    /** Which lowering stage the IR is expected to be in. */
    public enum Stage {
        /** Java-level arrays, fields and objects are allowed (frontend and generic optimisation). */
        PRE_ABI,
        /** After TornadoVM ABI lowering: only scalars, vectors, pointers and tokens remain. */
        POST_ABI
    }

    private final Stage stage;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private IrPrinter printer;
    private Function function;
    private DominatorTree dominators;

    private Verifier(Stage stage) {
        this.stage = stage;
    }

    public static List<Diagnostic> verify(Function function) {
        return verify(function, Stage.PRE_ABI);
    }

    public static List<Diagnostic> verify(Function function, Stage stage) {
        var verifier = new Verifier(stage);
        verifier.run(function);
        return List.copyOf(verifier.diagnostics);
    }

    public static List<Diagnostic> verify(IrModule module, Stage stage) {
        var all = new ArrayList<Diagnostic>();
        for (var f : module.functions()) {
            all.addAll(verify(f, stage));
        }
        return all;
    }

    /** Throws {@link IllegalStateException} listing every diagnostic if the function is invalid. */
    public static void check(Function function, Stage stage) {
        var diagnostics = verify(function, stage);
        if (!diagnostics.isEmpty()) {
            var sb = new StringBuilder("invalid IR in @" + function.name() + ":");
            diagnostics.forEach(d -> sb.append('\n').append(d.render()));
            throw new IllegalStateException(sb.toString());
        }
    }

    private void run(Function f) {
        this.function = f;
        if (f.blocks().isEmpty()) {
            report("TC-E2117", "function @" + f.name() + " has no blocks", "", Loc.UNKNOWN);
            return;
        }
        this.printer = new IrPrinter(false);
        printer.printFunction(f);
        this.dominators = new DominatorTree(f);
        var preds = f.predecessors();
        if (!preds.get(f.entry()).isEmpty()) {
            report("TC-E2116", "the entry block ^" + printer.blockName(f.entry()) + " must not have predecessors", "", Loc.UNKNOWN);
        }
        if (stage == Stage.POST_ABI) {
            for (var param : f.params()) {
                checkPostAbiType(param.type(), "parameter %" + printer.name(param), Loc.UNKNOWN);
            }
        }
        for (var block : f.blocks()) {
            verifyBlock(block);
        }
    }

    private void verifyBlock(Block block) {
        var insts = block.insts();
        if (block.terminator().isEmpty()) {
            report("TC-E2101", "block ^" + printer.blockName(block) + " does not end with a terminator", "^" + printer.blockName(block), Loc.UNKNOWN);
        }
        for (int i = 0; i < insts.size(); i++) {
            var inst = insts.get(i);
            if (inst.isTerminator() && i != insts.size() - 1) {
                error(inst, "TC-E2102", "terminator '" + inst.opcode().mnemonic() + "' must be the last instruction of its block");
            }
            verifyUses(block, i, inst);
            verifySignature(inst);
            verifySuccessors(inst);
            if (stage == Stage.POST_ABI) {
                verifyPostAbi(inst);
            }
        }
    }

    private void verifyUses(Block block, int index, Inst inst) {
        for (var value : inst.allUses()) {
            Block defBlock = switch (value) {
                case BlockArg arg -> arg.block();
                case Inst def -> def.block().orElse(null);
            };
            if (defBlock == null || defBlock.function() != function) {
                error(inst, "TC-E2110", "operand %" + printer.name(value) + " is not defined in @" + function.name());
                continue;
            }
            if (value instanceof Inst def && !def.producesValue()) {
                error(inst, "TC-E2111", "operand refers to an instruction that produces no value ('" + def.opcode().mnemonic() + "')");
                continue;
            }
            if (!dominators.isReachable(block)) {
                continue;
            }
            boolean dominated;
            if (defBlock == block) {
                dominated = value instanceof BlockArg || block.insts().indexOf(value) < index;
            } else {
                dominated = dominators.dominates(defBlock, block);
            }
            if (!dominated) {
                error(inst, "TC-E2105", "use of %" + printer.name(value) + " is not dominated by its definition");
            }
        }
    }

    private void verifySuccessors(Inst inst) {
        for (var successor : inst.successors()) {
            var target = successor.target();
            var params = target.args();
            var args = successor.args();
            if (params.size() != args.size()) {
                error(inst, "TC-E2103", "branch to ^" + printer.blockName(target) + " passes " + args.size() + " argument(s), the block takes " + params.size());
                continue;
            }
            for (int i = 0; i < args.size(); i++) {
                if (!args.get(i).type().equals(params.get(i).type())) {
                    error(inst, "TC-E2104", "argument " + i + " to ^" + printer.blockName(target) + " has type " + args.get(i).type().text() + ", expected " + params.get(i).type().text());
                }
            }
        }
    }

    private void verifySignature(Inst inst) {
        var ops = inst.operands();
        var result = inst.type();
        switch (inst.opcode()) {
            case Opcode.ConstInt _c -> {
                count(inst, 0);
                resultIs(inst, result instanceof IntType, "an integer type");
            }
            case Opcode.ConstFloat _c -> {
                count(inst, 0);
                resultIs(inst, result instanceof FloatType, "a floating-point type");
            }
            case Opcode.Binary(var op) -> {
                if (count(inst, 2)) {
                    sameTypes(inst, ops.get(0), ops.get(1));
                    resultIs(inst, result.equals(ops.get(0).type()), "the operand type " + ops.get(0).type().text());
                    var scalar = scalarOf(result);
                    resultIs(inst, op.isFloat ? scalar instanceof FloatType : scalar instanceof IntType, op.isFloat ? "floating point" : "integer");
                }
            }
            case Opcode.Unary(var op) -> {
                if (count(inst, 1)) {
                    resultIs(inst, result.equals(ops.get(0).type()), "the operand type " + ops.get(0).type().text());
                    var scalar = scalarOf(result);
                    resultIs(inst, op == UnaryOp.FNEG ? scalar instanceof FloatType : scalar instanceof IntType, op.isFloat ? "floating point" : "integer");
                }
            }
            case Opcode.ICmp _c -> {
                if (count(inst, 2)) {
                    sameTypes(inst, ops.get(0), ops.get(1));
                    operandIs(inst, 0, ops.get(0).type() instanceof IntType || ops.get(0).type() instanceof PointerType, "an integer or pointer");
                }
                resultIs(inst, result.equals(Type.I1), "i1");
            }
            case Opcode.FCmp _c -> {
                if (count(inst, 2)) {
                    sameTypes(inst, ops.get(0), ops.get(1));
                    operandIs(inst, 0, ops.get(0).type() instanceof FloatType, "floating point");
                }
                resultIs(inst, result.equals(Type.I1), "i1");
            }
            case Opcode.Select _s -> {
                if (count(inst, 3)) {
                    operandIs(inst, 0, ops.get(0).type().equals(Type.I1), "i1");
                    sameTypes(inst, ops.get(1), ops.get(2));
                    resultIs(inst, result.equals(ops.get(1).type()), "the operand type " + ops.get(1).type().text());
                }
            }
            case Opcode.Convert(var op) -> {
                if (count(inst, 1)) {
                    verifyConversion(inst, op, ops.get(0).type(), result);
                }
            }
            case Opcode.ArrayLoad _l -> {
                if (count(inst, 2)) {
                    if (ops.get(0).type() instanceof ArrayType(var element)) {
                        // A byte array may be read as any type at a byte index (ByteArray.getHalfFloat); any
                        // array as a vector of its elements (packed access, HalfFloatArray.getHalf2).
                        resultIs(inst, result.equals(element) || element.equals(Type.I8) || packs(result, element), "the array element type " + element.text());
                    } else if (isLocalPointer(ops.get(0).type())) {
                        // A local array passed to an inlined helper: element type from the access.
                    } else {
                        operandIs(inst, 0, false, "an array");
                    }
                    operandIs(inst, 1, isIndex(ops.get(1).type()), "an i32 or i64 index");
                }
            }
            case Opcode.ArrayStore _s -> {
                if (count(inst, 3)) {
                    if (ops.get(0).type() instanceof ArrayType(var element)) {
                        operandIs(inst, 2, ops.get(2).type().equals(element) || element.equals(Type.I8) || packs(ops.get(2).type(), element), "the array element type " + element.text());
                    } else if (isLocalPointer(ops.get(0).type())) {
                        // As for array.load.
                    } else {
                        operandIs(inst, 0, false, "an array");
                    }
                    operandIs(inst, 1, isIndex(ops.get(1).type()), "an i32 or i64 index");
                }
                resultIs(inst, result instanceof Type.VoidType, "void");
            }
            case Opcode.ArrayLength _l -> {
                if (count(inst, 1)) {
                    operandIs(inst, 0, ops.get(0).type() instanceof ArrayType, "an array");
                }
                resultIs(inst, result.equals(Type.I32) || result.equals(Type.I64), "i32 or i64");
            }
            case Opcode.FieldLoad _f -> {
                if (count(inst, 1)) {
                    operandIs(inst, 0, ops.get(0).type() instanceof ObjectType, "an object");
                }
                resultIs(inst, !(result instanceof Type.VoidType), "a value type");
            }
            case Opcode.FieldStore _f -> {
                if (count(inst, 2)) {
                    operandIs(inst, 0, ops.get(0).type() instanceof ObjectType, "an object");
                }
                resultIs(inst, result instanceof Type.VoidType, "void");
            }
            case Opcode.PointerOffset _p -> {
                if (count(inst, 2)) {
                    operandIs(inst, 0, ops.get(0).type() instanceof PointerType, "a pointer");
                    operandIs(inst, 1, isIndex(ops.get(1).type()), "an i32 or i64 byte offset");
                    resultIs(inst, result.equals(ops.get(0).type()), "the pointer type " + ops.get(0).type().text());
                }
            }
            case Opcode.Load _l -> {
                if (count(inst, 1)) {
                    operandIs(inst, 0, ops.get(0).type() instanceof PointerType, "a pointer");
                }
                resultIs(inst, !(result instanceof Type.VoidType), "a value type");
            }
            case Opcode.Store _s -> {
                if (count(inst, 2)) {
                    if (ops.get(0).type() instanceof PointerType(var space)) {
                        if (space == AddressSpace.CONSTANT) {
                            error(inst, "TC-E2114", "store through a pointer to constant memory");
                        }
                    } else {
                        operandIs(inst, 0, false, "a pointer");
                    }
                }
                resultIs(inst, result instanceof Type.VoidType, "void");
            }
            case Opcode.Alloca(var space, var element, var size) -> {
                count(inst, 0);
                resultIs(inst, result.equals(new PointerType(space)), "ptr<" + space.text() + ">");
                if (size <= 0) {
                    error(inst, "TC-E2108", "alloca needs a positive element count, found " + size);
                }
                if (space == AddressSpace.SHARED && function.kind() != Function.Kind.KERNEL) {
                    error(inst, "TC-E2113", "shared memory can only be allocated in a kernel");
                }
                if (space == AddressSpace.GLOBAL || space == AddressSpace.CONSTANT) {
                    error(inst, "TC-E2108", "alloca can only allocate private or shared memory");
                }
            }
            case Opcode.Call _c -> {
                // Callee signatures are checked at module level once device functions exist (after C6).
            }
            case Opcode.Intrinsic _i -> {
                // Intrinsic signatures are checked against the intrinsic registry (tc-codegen).
            }
            case Opcode.Branch _b -> {
                count(inst, 0);
                successors(inst, 1);
            }
            case Opcode.CondBranch _c -> {
                if (count(inst, 1)) {
                    operandIs(inst, 0, ops.get(0).type().equals(Type.I1), "i1");
                }
                successors(inst, 2);
            }
            case Opcode.Switch(var keys) -> {
                if (count(inst, 1)) {
                    operandIs(inst, 0, ops.get(0).type() instanceof IntType, "an integer");
                }
                successors(inst, keys.size() + 1);
            }
            case Opcode.Return _r -> {
                var expected = function.returnType();
                if (expected instanceof Type.VoidType) {
                    if (!ops.isEmpty()) {
                        error(inst, "TC-E2109", "@" + function.name() + " returns void but 'ret' has a value");
                    }
                } else if (ops.size() != 1) {
                    error(inst, "TC-E2109", "@" + function.name() + " returns " + expected.text() + " but 'ret' has no value");
                } else if (!ops.get(0).type().equals(expected)) {
                    error(inst, "TC-E2109", "'ret' value has type " + ops.get(0).type().text() + ", @" + function.name() + " returns " + expected.text());
                }
            }
            case Opcode.Unreachable _u -> count(inst, 0);
        }
        if (!inst.isTerminator() && inst.successors().size() > 0) {
            error(inst, "TC-E2106", "only terminators can have successors");
        }
    }

    private void verifyConversion(Inst inst, ConvertOp op, Type from, Type to) {
        var ok = switch (op) {
            case SEXT, ZEXT -> from instanceof IntType(var a) && to instanceof IntType(var b) && b > a;
            case TRUNC -> from instanceof IntType(var a) && to instanceof IntType(var b) && b < a;
            case F2I_SAT -> from instanceof FloatType && to instanceof IntType;
            case I2F -> from instanceof IntType && to instanceof FloatType;
            case FEXT -> from instanceof FloatType a && to instanceof FloatType b && b.bits() > a.bits();
            case FTRUNC -> from instanceof FloatType a && to instanceof FloatType b && b.bits() < a.bits();
            case BITCAST -> bitWidth(from) > 0 && bitWidth(from) == bitWidth(to);
        };
        if (!ok) {
            error(inst, "TC-E2107", "'" + op.text + "' cannot convert " + from.text() + " to " + to.text());
        }
    }

    private void verifyPostAbi(Inst inst) {
        switch (inst.opcode()) {
            case Opcode.ArrayLoad _a -> error(inst, "TC-E2112", "Java-level array access after ABI lowering");
            case Opcode.ArrayStore _a -> error(inst, "TC-E2112", "Java-level array access after ABI lowering");
            case Opcode.ArrayLength _a -> error(inst, "TC-E2112", "Java-level array length after ABI lowering");
            case Opcode.FieldLoad _f -> error(inst, "TC-E2112", "Java-level field access after ABI lowering");
            case Opcode.FieldStore _f -> error(inst, "TC-E2112", "Java-level field access after ABI lowering");
            default -> checkPostAbiType(inst.type(), printer.instText(inst), inst.loc());
        }
    }

    private void checkPostAbiType(Type type, String where, Loc loc) {
        if (type instanceof ArrayType || type instanceof ObjectType) {
            report("TC-E2112", "Java-level type " + type.text() + " after ABI lowering", where, loc);
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean count(Inst inst, int expected) {
        if (inst.operandCount() != expected) {
            error(inst, "TC-E2106", "'" + inst.opcode().mnemonic() + "' takes " + expected + " operand(s), found " + inst.operandCount());
            return false;
        }
        return true;
    }

    private void successors(Inst inst, int expected) {
        if (inst.successors().size() != expected) {
            error(inst, "TC-E2106", "'" + inst.opcode().mnemonic() + "' needs " + expected + " successor(s), found " + inst.successors().size());
        }
    }

    /** {@code type} is a vector of {@code element}s: a packed access to consecutive array elements. */
    private static boolean packs(Type type, Type element) {
        return type instanceof Type.VectorType(var lane, var length) && lane.equals(element);
    }

    private void sameTypes(Inst inst, Value a, Value b) {
        if (!a.type().equals(b.type())) {
            error(inst, "TC-E2107", "operand types differ: " + a.type().text() + " and " + b.type().text());
        }
    }

    private void operandIs(Inst inst, int index, boolean ok, String expected) {
        if (!ok) {
            error(inst, "TC-E2107", "operand " + index + " must be " + expected + ", found " + inst.operand(index).type().text());
        }
    }

    private void resultIs(Inst inst, boolean ok, String expected) {
        if (!ok) {
            error(inst, "TC-E2108", "result of '" + inst.opcode().mnemonic() + "' must be " + expected + ", found " + inst.type().text());
        }
    }

    private static boolean isIndex(Type type) {
        return type.equals(Type.I32) || type.equals(Type.I64);
    }

    private static Type scalarOf(Type type) {
        return type instanceof VectorType(var element, var length) ? element : type;
    }

    private static int bitWidth(Type type) {
        return switch (type) {
            case IntType(var bits) -> bits;
            case FloatType f -> f.bits();
            case VectorType(var element, var length) -> bitWidth(element) * length;
            default -> -1;
        };
    }

    private void error(Inst inst, String code, String message) {
        var blockName = inst.block().map(b -> "^" + printer.blockName(b) + ": ").orElse("");
        diagnostics.add(new Diagnostic(code, message, blockName + printer.instText(inst), inst.loc(), inst.textLine()));
    }

    private void report(String code, String message, String where, Loc loc) {
        diagnostics.add(new Diagnostic(code, message, where, loc));
    }

    private static boolean isLocalPointer(Type type) {
        return type instanceof Type.PointerType(var space) && (space == AddressSpace.SHARED || space == AddressSpace.PRIVATE);
    }
}
