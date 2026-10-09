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

import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.UnaryOp;

/**
 * Creates instructions at an insertion point (the end of a block, or before an anchor instruction)
 * and infers result types. A location set with {@link #at(Loc)} is attached to every instruction
 * created afterwards.
 */
public final class IrBuilder {

    private Block block;
    private Inst anchor;
    private Loc loc = Loc.UNKNOWN;

    public IrBuilder(Block block) {
        this.block = block;
    }

    /** Appends subsequent instructions to the end of {@code block}. */
    public IrBuilder atEnd(Block block) {
        this.block = block;
        this.anchor = null;
        return this;
    }

    /** Inserts subsequent instructions before {@code anchor}. */
    public IrBuilder before(Inst anchor) {
        this.block = anchor.block().orElseThrow(() -> new IllegalArgumentException("anchor is not in a block"));
        this.anchor = anchor;
        return this;
    }

    public IrBuilder at(Loc loc) {
        this.loc = loc;
        return this;
    }

    public Block block() {
        return block;
    }

    // ---------------------------------------------------------------- constants

    public Inst constInt(Type type, long value) {
        return insert(new Inst(new Opcode.ConstInt(value), type), List.of());
    }

    public Inst constFloat(Type type, double value) {
        return insert(new Inst(new Opcode.ConstFloat(value), type), List.of());
    }

    // ---------------------------------------------------------------- arithmetic

    public Inst binary(BinaryOp op, Value lhs, Value rhs) {
        return insert(new Inst(new Opcode.Binary(op), lhs.type()), List.of(lhs, rhs));
    }

    public Inst add(Value lhs, Value rhs) {
        return binary(BinaryOp.ADD, lhs, rhs);
    }

    public Inst fadd(Value lhs, Value rhs) {
        return binary(BinaryOp.FADD, lhs, rhs);
    }

    public Inst unary(UnaryOp op, Value operand) {
        return insert(new Inst(new Opcode.Unary(op), operand.type()), List.of(operand));
    }

    public Inst icmp(IntPredicate predicate, Value lhs, Value rhs) {
        return insert(new Inst(new Opcode.ICmp(predicate), Type.I1), List.of(lhs, rhs));
    }

    public Inst fcmp(FloatPredicate predicate, Value lhs, Value rhs) {
        return insert(new Inst(new Opcode.FCmp(predicate), Type.I1), List.of(lhs, rhs));
    }

    public Inst select(Value condition, Value ifTrue, Value ifFalse) {
        return insert(new Inst(new Opcode.Select(), ifTrue.type()), List.of(condition, ifTrue, ifFalse));
    }

    public Inst convert(ConvertOp op, Value value, Type to) {
        return insert(new Inst(new Opcode.Convert(op), to), List.of(value));
    }

    // ---------------------------------------------------------------- Java-level memory

    public Inst arrayLoad(Value array, Value index) {
        var element = array.type() instanceof Type.ArrayType(var e) ? e : Type.VOID;
        return insert(new Inst(new Opcode.ArrayLoad(), element), List.of(array, index));
    }

    public Inst arrayStore(Value array, Value index, Value value) {
        return insert(new Inst(new Opcode.ArrayStore(), Type.VOID), List.of(array, index, value));
    }

    public Inst arrayLength(Value array) {
        return insert(new Inst(new Opcode.ArrayLength(), Type.I32), List.of(array));
    }

    public Inst fieldLoad(Value object, String field, Type type) {
        return insert(new Inst(new Opcode.FieldLoad(field), type), List.of(object));
    }

    public Inst fieldStore(Value object, String field, Value value) {
        return insert(new Inst(new Opcode.FieldStore(field), Type.VOID), List.of(object, value));
    }

    // ---------------------------------------------------------------- pointer memory

    public Inst pointerOffset(Value pointer, Value bytes) {
        return insert(new Inst(new Opcode.PointerOffset(), pointer.type()), List.of(pointer, bytes));
    }

    public Inst load(Value pointer, Type type) {
        return insert(new Inst(new Opcode.Load(), type), List.of(pointer));
    }

    public Inst store(Value pointer, Value value) {
        return insert(new Inst(new Opcode.Store(), Type.VOID), List.of(pointer, value));
    }

    public Inst alloca(AddressSpace space, Type element, int count) {
        return insert(new Inst(new Opcode.Alloca(space, element, count), new Type.PointerType(space)), List.of());
    }

    // ---------------------------------------------------------------- calls and intrinsics

    public Inst call(String callee, Type returnType, List<? extends Value> args) {
        return insert(new Inst(new Opcode.Call(callee), returnType), args);
    }

    public Inst intrinsic(String name, Type type, List<? extends Value> args) {
        return insert(new Inst(new Opcode.Intrinsic(name), type), args);
    }

    // ---------------------------------------------------------------- terminators

    public Inst br(Block target, List<? extends Value> args) {
        var inst = new Inst(new Opcode.Branch(), Type.VOID);
        inst.addSuccessor(target, args);
        return insert(inst, List.of());
    }

    public Inst condBr(Value condition, Block ifTrue, List<? extends Value> trueArgs, Block ifFalse, List<? extends Value> falseArgs) {
        var inst = new Inst(new Opcode.CondBranch(), Type.VOID);
        inst.addSuccessor(ifTrue, trueArgs);
        inst.addSuccessor(ifFalse, falseArgs);
        return insert(inst, List.of(condition));
    }

    /** {@code cases.get(i)} is taken for {@code keys.get(i)}; {@code defaultTarget} otherwise. */
    public Inst switchOn(Value selector, List<Long> keys, List<Block> cases, Block defaultTarget) {
        if (keys.size() != cases.size()) {
            throw new IllegalArgumentException("keys and cases differ in length");
        }
        var inst = new Inst(new Opcode.Switch(keys), Type.VOID);
        cases.forEach(target -> inst.addSuccessor(target, List.of()));
        inst.addSuccessor(defaultTarget, List.of());
        return insert(inst, List.of(selector));
    }

    public Inst ret() {
        return insert(new Inst(new Opcode.Return(), Type.VOID), List.of());
    }

    public Inst ret(Value value) {
        return insert(new Inst(new Opcode.Return(), Type.VOID), List.of(value));
    }

    public Inst unreachable() {
        return insert(new Inst(new Opcode.Unreachable(), Type.VOID), List.of());
    }

    /** Inserts an instruction created elsewhere (for example by the parser) at the insertion point. */
    public Inst insert(Inst inst, List<? extends Value> operands) {
        operands.forEach(inst::addOperand);
        inst.setLoc(loc);
        if (anchor == null) {
            block.append(inst);
        } else {
            block.insertBefore(inst, anchor);
        }
        return inst;
    }
}
