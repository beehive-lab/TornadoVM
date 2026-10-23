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
package uk.ac.manchester.tornado.tc.lower;

import java.util.List;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;

/**
 * Prints TC-S as indented, dialect-neutral pseudo-C: the debugging view of structuring and
 * out-of-SSA translation before a target dialect is applied. Operations keep their TC mnemonics
 * except for arithmetic and comparisons, which are printed infix.
 */
public final class SPrinter {

    private final StringBuilder out = new StringBuilder();

    private SPrinter() {
    }

    public static String print(S.Kernel kernel) {
        var p = new SPrinter();
        p.kernel(kernel);
        return p.out.toString();
    }

    private void kernel(S.Kernel k) {
        var f = k.source();
        out.append(f.kind().text).append(' ').append(f.name()).append('(');
        out.append(k.params().stream().map(v -> v.type().text() + " " + v.name()).collect(Collectors.joining(", ")));
        out.append(") -> ").append(f.returnType().text()).append(" {\n");
        for (var alloca : k.allocas()) {
            var a = alloca.kind();
            indent(1).append(a.space().text()).append(' ').append(a.element().text()).append(' ').append(alloca.name()).append('[').append(a.count()).append("];\n");
        }
        for (var v : k.locals()) {
            indent(1).append(v.type().text()).append(' ').append(v.name()).append(";\n");
        }
        stmts(k.body(), 1);
        out.append("}\n");
    }

    private void stmts(List<S.Stmt> body, int depth) {
        for (var stmt : body) {
            stmt(stmt, depth);
        }
    }

    private void stmt(S.Stmt stmt, int depth) {
        switch (stmt) {
            case S.Assign(var target, var value, var loc) -> indent(depth).append(target.name()).append(" = ").append(expr(value)).append(";\n");
            case S.Effect(var op, var loc) -> indent(depth).append(expr(op)).append(";\n");
            case S.ZeroFill(var alloca, var loc) -> indent(depth).append("zero ").append(alloca.name()).append(";\n");
            case S.If(var c, var t, var e) -> {
                indent(depth).append("if (").append(expr(c)).append(") {\n");
                stmts(t, depth + 1);
                if (!e.isEmpty()) {
                    indent(depth).append("} else {\n");
                    stmts(e, depth + 1);
                }
                indent(depth).append("}\n");
            }
            case S.Loop(var label, var b) -> {
                indent(depth).append("loop ").append(label).append(" {\n");
                stmts(b, depth + 1);
                indent(depth).append("}\n");
            }
            case S.Labelled(var label, var b) -> {
                indent(depth).append("block ").append(label).append(" {\n");
                stmts(b, depth + 1);
                indent(depth).append("}\n");
            }
            case S.Break(var label) -> indent(depth).append("break ").append(label).append(";\n");
            case S.Continue(var label) -> indent(depth).append("continue ").append(label).append(";\n");
            case S.Switch(var sel, var cases, var d) -> {
                indent(depth).append("switch (").append(expr(sel)).append(") {\n");
                for (var k : cases) {
                    indent(depth).append("case ").append(k.key()).append(":\n");
                    stmts(k.body(), depth + 1);
                }
                indent(depth).append("default:\n");
                stmts(d, depth + 1);
                indent(depth).append("}\n");
            }
            case S.Return(var value) -> indent(depth).append(value.map(v -> "return " + expr(v) + ";\n").orElse("return;\n"));
            case S.Unreachable _u -> indent(depth).append("unreachable;\n");
        }
    }

    /** The text of one structured expression (as the structured-IR dump prints it). */
    public static String expr(S.Expr expr) {
        return switch (expr) {
            case S.VarRef(var v) -> v.name();
            case S.Const(var inst) -> switch (inst.opcode()) {
                case Opcode.ConstInt(var value) -> Long.toString(value);
                case Opcode.ConstFloat(var value) -> IrPrinter.floatText(value, inst.type());
                default -> inst.opcode().mnemonic();
            };
            case S.AllocaRef(var alloca) -> "&" + alloca.name();
            case S.ElementAddress e -> expr(e.address());
            case S.Op(var inst, var ops) -> switch (inst.opcode()) {
                case Opcode.Binary(var op) -> "(" + expr(ops.get(0)) + " " + symbol(op) + " " + expr(ops.get(1)) + ")";
                case Opcode.ICmp(var p) -> "(" + expr(ops.get(0)) + " " + p.text() + " " + expr(ops.get(1)) + ")";
                case Opcode.FCmp(var p) -> "(" + expr(ops.get(0)) + " " + p.text() + " " + expr(ops.get(1)) + ")";
                case Opcode.Select _s -> "(" + expr(ops.get(0)) + " ? " + expr(ops.get(1)) + " : " + expr(ops.get(2)) + ")";
                case Opcode.Convert(var op) -> op.text + "<" + inst.type().text() + ">(" + expr(ops.get(0)) + ")";
                case Opcode.ArrayLoad _l -> expr(ops.get(0)) + "[" + expr(ops.get(1)) + "]";
                case Opcode.ArrayStore _s -> expr(ops.get(0)) + "[" + expr(ops.get(1)) + "] = " + expr(ops.get(2));
                case Opcode.Load _l -> "load<" + inst.type().text() + ">(" + expr(ops.get(0)) + ")";
                case Opcode.Call(var callee) -> callee + "(" + args(ops) + ")";
                default -> inst.opcode().mnemonic() + inst.immediates().text() + "(" + args(ops) + ")";
            };
        };
    }

    private static String args(List<S.Expr> ops) {
        return ops.stream().map(SPrinter::expr).collect(Collectors.joining(", "));
    }

    private static String symbol(BinaryOp op) {
        return switch (op) {
            case ADD, FADD -> "+";
            case SUB, FSUB -> "-";
            case MUL, FMUL -> "*";
            case DIV, FDIV -> "/";
            case REM, FREM -> "%";
            case AND -> "&";
            case OR -> "|";
            case XOR -> "^";
            case SHL -> "<<";
            case SHR -> ">>";
            case USHR -> ">>>";
        };
    }

    private StringBuilder indent(int depth) {
        return out.append("  ".repeat(depth));
    }
}
