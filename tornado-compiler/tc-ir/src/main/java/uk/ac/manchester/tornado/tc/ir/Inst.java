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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An instruction. It is also the SSA value it produces; instructions of type {@code void} produce
 * none. The op kind is an immutable {@link Opcode}; operands, successors, location and attributes are
 * mutable so that passes can rewrite in place. All edits keep the users lists of the referenced
 * values consistent.
 */
public final class Inst extends Value {

    private Opcode opcode;
    private final List<Value> operands = new ArrayList<>();
    private final List<Successor> successors = new ArrayList<>();
    private final List<Attribute> attributes = new ArrayList<>();
    private Immediates immediates = Immediates.NONE;
    private Block block;
    private Loc loc = Loc.UNKNOWN;
    private int textLine = -1;

    public Inst(Opcode opcode, Type type) {
        super(type, "");
        this.opcode = opcode;
    }

    public Opcode opcode() {
        return opcode;
    }

    public void setOpcode(Opcode opcode) {
        this.opcode = opcode;
    }

    public boolean isTerminator() {
        return opcode.isTerminator();
    }

    public boolean producesValue() {
        return !(type() instanceof Type.VoidType);
    }

    // ---------------------------------------------------------------- operands

    public List<Value> operands() {
        return Collections.unmodifiableList(operands);
    }

    public Value operand(int index) {
        return operands.get(index);
    }

    public int operandCount() {
        return operands.size();
    }

    public void addOperand(Value value) {
        operands.add(value);
        value.addUser(this);
    }

    public void setOperand(int index, Value value) {
        operands.get(index).removeUser(this);
        operands.set(index, value);
        value.addUser(this);
    }

    public void setOperands(List<? extends Value> values) {
        for (var old : operands) {
            old.removeUser(this);
        }
        operands.clear();
        values.forEach(this::addOperand);
    }

    // ---------------------------------------------------------------- successors

    public List<Successor> successors() {
        return Collections.unmodifiableList(successors);
    }

    public void addSuccessor(Block target, List<? extends Value> args) {
        var successor = new Successor(target, List.copyOf(args));
        successors.add(successor);
        args.forEach(arg -> arg.addUser(this));
        cfgChanged();
    }

    public void setSuccessorTarget(int index, Block target) {
        successors.get(index).setTarget(target);
        cfgChanged();
    }

    private void cfgChanged() {
        if (block != null) {
            block.function().cfgChanged();
        }
    }

    public void setSuccessorArgs(int index, List<? extends Value> args) {
        var successor = successors.get(index);
        for (var old : successor.mutableArgs()) {
            old.removeUser(this);
        }
        successor.mutableArgs().clear();
        successor.mutableArgs().addAll(args);
        args.forEach(arg -> arg.addUser(this));
    }

    /** All values this instruction reads: operands followed by successor arguments. */
    public List<Value> allUses() {
        var all = new ArrayList<Value>(operands);
        for (var successor : successors) {
            all.addAll(successor.args());
        }
        return all;
    }

    void replaceUsesOf(Value old, Value replacement) {
        for (int i = 0; i < operands.size(); i++) {
            if (operands.get(i) == old) {
                setOperand(i, replacement);
            }
        }
        for (var successor : successors) {
            var args = successor.mutableArgs();
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i) == old) {
                    old.removeUser(this);
                    args.set(i, replacement);
                    replacement.addUser(this);
                }
            }
        }
    }

    // ---------------------------------------------------------------- position, location, attributes

    public Optional<Block> block() {
        return Optional.ofNullable(block);
    }

    void setBlock(Block block) {
        this.block = block;
    }

    public Loc loc() {
        return loc;
    }

    public void setLoc(Loc loc) {
        this.loc = loc == null ? Loc.UNKNOWN : loc;
    }

    /** Line of this instruction in the IR text it was parsed from, or {@code -1}. */
    public int textLine() {
        return textLine;
    }

    void setTextLine(int textLine) {
        this.textLine = textLine;
    }

    /** Typed compile-time parameters of the instruction (lane, mode, callee, ...). */
    public Immediates immediates() {
        return immediates;
    }

    public void setImmediates(Immediates immediates) {
        this.immediates = java.util.Objects.requireNonNull(immediates);
    }

    /**
     * A detached copy of this instruction: opcode, type, name, location, attributes and immediates,
     * with no operands or successors (the caller maps those). The one way passes clone instructions.
     */
    public Inst copyShell() {
        var copy = new Inst(opcode, type());
        if (hasDerivedName()) {
            copy.setDerivedName(name());
        } else {
            copy.setName(name());
        }
        copy.setLoc(loc);
        attributes.forEach(copy::addAttribute);
        copy.immediates = immediates;
        return copy;
    }

    public List<Attribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(Attribute attribute) {
        attributes.add(attribute);
    }

    public boolean hasAttribute(String name) {
        return attributes.stream().anyMatch(a -> a.name().equals(name));
    }

    /**
     * Removes this instruction from its block and drops its operand references. The instruction must
     * have no remaining users.
     */
    public void erase() {
        if (hasUses()) {
            throw new IllegalStateException("cannot erase an instruction that still has users: " + opcode.mnemonic());
        }
        dropReferences();
        if (block != null) {
            block.remove(this);
        }
    }

    /** Moves this instruction, with its operands, to just before {@code anchor} (possibly in another block). */
    public void moveBefore(Inst anchor) {
        var target = anchor.block().orElseThrow(() -> new IllegalArgumentException("anchor is not in a block"));
        if (block != null) {
            block.remove(this);
        }
        target.insertBefore(this, anchor);
    }

    /** Moves this instruction to the end of {@code target}. */
    public void moveToEnd(Block target) {
        if (block != null) {
            block.remove(this);
        }
        target.append(this);
    }

    /** Releases every operand and successor-argument reference held by this instruction. */
    public void dropReferences() {
        setOperands(List.of());
        for (int i = 0; i < successors.size(); i++) {
            setSuccessorArgs(i, List.of());
        }
    }

    @Override
    public String toString() {
        return opcode.mnemonic() + (name().isEmpty() ? "" : " %" + name());
    }
}
