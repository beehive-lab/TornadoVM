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
package uk.ac.manchester.tornado.tc.lower;

import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * TC-S: the structured, non-SSA representation that source emitters print. Values live in mutable
 * variables, control flow is nested regions (blocks, loops, conditionals), and expressions are trees.
 * Operations are not re-encoded: an {@link Expr.Op} keeps the TC-SSA instruction, so emitters reuse
 * the opcode and type semantics of TC-SSA. See {@code ir-specification.md} section 2.
 */
public final class S {

    private S() {
    }

    /** A mutable variable; {@code name} is unique within its kernel. */
    public record Var(String name, Type type) {
    }

    /**
     * One structured kernel or device function. {@code elementTypes} gives, for pointer variables that
     * are only used to access elements of one type (array data pointers), that element type, so
     * emitters can declare them typed and print {@code a_data[i]}.
     */
    public record Kernel(Function source, List<Var> params, List<Var> locals, List<Alloca> allocas, List<Stmt> body, java.util.Map<Var, Type> elementTypes) {
    }

    /** A private or shared allocation declared at the top of the kernel. */
    public record Alloca(Inst inst, String name) {
        public uk.ac.manchester.tornado.tc.ir.Opcode.Alloca kind() {
            return (uk.ac.manchester.tornado.tc.ir.Opcode.Alloca) inst.opcode();
        }
    }

    // ---------------------------------------------------------------- expressions

    public sealed interface Expr {
        Type type();
    }

    public record VarRef(Var var) implements Expr {
        @Override
        public Type type() {
            return var.type();
        }
    }

    /** A constant (the TC-SSA const instruction). */
    public record Const(Inst inst) implements Expr {
        @Override
        public Type type() {
            return inst.type();
        }
    }

    /** An operation applied to operand expressions; {@code inst} supplies the opcode and type. */
    public record Op(Inst inst, List<Expr> operands) implements Expr {
        @Override
        public Type type() {
            return inst.type();
        }
    }

    /** The address of a private or shared allocation (the TC-SSA alloca instruction). */
    public record AllocaRef(Alloca alloca) implements Expr {
        @Override
        public Type type() {
            return alloca.inst().type();
        }
    }

    /**
     * Element {@code index} of {@code base} seen as an array of {@code element} (a typed data pointer or
     * a local array), recognised once by the structurizer so every dialect prints {@code base[index]};
     * {@code address} is the same address as a plain pointer computation, for accesses of another type.
     */
    public record ElementAddress(Op address, Expr base, Expr index, Type element) implements Expr {
        @Override
        public Type type() {
            return address.type();
        }
    }

    // ---------------------------------------------------------------- statements

    public sealed interface Stmt {
    }

    public record Assign(Var target, Expr value, Loc loc) implements Stmt {
    }

    /** An operation evaluated for its effect (stores, barriers, void calls). */
    public record Effect(Op op, Loc loc) implements Stmt {
    }

    /**
     * Zeroes a private array where its {@code new} executes. The declaration is at the top of the
     * kernel; an allocation inside a loop must start from zero on every iteration, as in Java.
     */
    public record ZeroFill(Alloca alloca, Loc loc) implements Stmt {
    }

    public record If(Expr condition, List<Stmt> thenBody, List<Stmt> elseBody) implements Stmt {
    }

    /** An infinite loop; leave it with {@link Break} to an enclosing {@link Labelled} block. */
    public record Loop(String label, List<Stmt> body) implements Stmt {
    }

    /** A block whose end is the target of {@link Break}s with its label. */
    public record Labelled(String label, List<Stmt> body) implements Stmt {
    }

    public record Break(String label) implements Stmt {
    }

    /** Jump to the start of the enclosing {@link Loop} with this label. */
    public record Continue(String label) implements Stmt {
    }

    public record Switch(Expr selector, List<Case> cases, List<Stmt> defaultBody) implements Stmt {
    }

    public record Case(long key, List<Stmt> body) {
    }

    public record Return(Optional<Expr> value) implements Stmt {
    }

    public record Unreachable() implements Stmt {
    }
}
