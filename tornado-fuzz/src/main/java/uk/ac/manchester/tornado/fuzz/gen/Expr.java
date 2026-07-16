/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.fuzz.gen;

import java.util.List;

import uk.ac.manchester.tornado.fuzz.RandomGen;

/**
 * A random integer-expression AST over the fixed kernel shape
 * {@code (KernelContext c, IntArray a, IntArray b, IntArray out)}. Leaves read
 * {@code a.get(i)}, {@code b.get(i)}, the thread index {@code i}, or a constant;
 * nodes are arithmetic / bitwise / shift / unary / high-multiply operators.
 *
 * <p>{@link #render()} produces Java source used verbatim by BOTH the device
 * kernel and the JVM reference, so the two can never semantically diverge except
 * through a codegen bug. Generation is <b>weighted toward fragile operations</b>
 * (division/remainder, shifts, and a 64-bit high-multiply that stresses long
 * multiply + narrowing convert) and edge constants (INT_MIN/MAX/-1), because that
 * is where the CUDA integer code generator is thinnest. Divisors are OR-ed with 1
 * so division stays defined (a shared div-by-zero throw is uninteresting).
 *
 * <p>The tree is walkable ({@link #children()} / {@link #withChildren(List)}) so
 * the {@link Shrinker} can minimize a failing expression.
 */
public abstract class Expr {

    public abstract String render();

    public abstract List<Expr> children();

    public abstract Expr withChildren(List<Expr> kids);

    public int nodeCount() {
        int n = 1;
        for (Expr c : children()) {
            n += c.nodeCount();
        }
        return n;
    }

    // ---- leaf factories (also used by the shrinker) ----

    public static Expr leafA() {
        return new Leaf("a.get(i)");
    }

    public static Expr leafB() {
        return new Leaf("b.get(i)");
    }

    public static Expr constant(int v) {
        return new Leaf("(" + v + ")");
    }

    private static final String[] ARITH = { "+", "-", "*", "&", "|", "^" };
    private static final int[] EDGE_CONSTS = { Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, 2, -2, 255 };

    public static Expr generate(RandomGen rng, int depth) {
        if (depth <= 0 || rng.nextInt(100) < 25) {
            return leaf(rng);
        }
        int w = rng.nextInt(100);
        if (w < 30) {
            return new Binary(rng.pick(ARITH), generate(rng, depth - 1), generate(rng, depth - 1));
        }
        if (w < 55) { // fragile: division / remainder
            return new Div(rng.nextBoolean() ? "/" : "%", generate(rng, depth - 1), generate(rng, depth - 1));
        }
        if (w < 75) { // fragile: shifts
            return new Shift(rng.nextBoolean() ? "<<" : ">>", generate(rng, depth - 1), generate(rng, depth - 1));
        }
        if (w < 88) { // fragile: 64-bit high-multiply + narrowing convert
            return new HighMul(generate(rng, depth - 1), generate(rng, depth - 1));
        }
        return new Unary(rng.nextBoolean() ? "-" : "~", generate(rng, depth - 1));
    }

    private static Expr leaf(RandomGen rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> leafA();
            case 1 -> leafB();
            case 2 -> new Leaf("i");
            default -> constant(rng.nextInt(100) < 40 ? EDGE_CONSTS[rng.nextInt(EDGE_CONSTS.length)] : rng.nextIntBetween(-16, 16));
        };
    }

    private static final class Leaf extends Expr {
        private final String text;

        Leaf(String text) {
            this.text = text;
        }

        @Override
        public String render() {
            return text;
        }

        @Override
        public List<Expr> children() {
            return List.of();
        }

        @Override
        public Expr withChildren(List<Expr> kids) {
            return this;
        }
    }

    private abstract static class Bin extends Expr {
        final Expr left;
        final Expr right;

        Bin(Expr left, Expr right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public List<Expr> children() {
            return List.of(left, right);
        }
    }

    private static final class Binary extends Bin {
        private final String op;

        Binary(String op, Expr left, Expr right) {
            super(left, right);
            this.op = op;
        }

        @Override
        public String render() {
            return "(" + left.render() + " " + op + " " + right.render() + ")";
        }

        @Override
        public Expr withChildren(List<Expr> kids) {
            return new Binary(op, kids.get(0), kids.get(1));
        }
    }

    private static final class Div extends Bin {
        private final String op;

        Div(String op, Expr left, Expr right) {
            super(left, right);
            this.op = op;
        }

        @Override
        public String render() {
            return "(" + left.render() + " " + op + " ((" + right.render() + ") | 1))";
        }

        @Override
        public Expr withChildren(List<Expr> kids) {
            return new Div(op, kids.get(0), kids.get(1));
        }
    }

    private static final class Shift extends Bin {
        private final String op;

        Shift(String op, Expr left, Expr right) {
            super(left, right);
            this.op = op;
        }

        @Override
        public String render() {
            return "(" + left.render() + " " + op + " ((" + right.render() + ") & 31))";
        }

        @Override
        public Expr withChildren(List<Expr> kids) {
            return new Shift(op, kids.get(0), kids.get(1));
        }
    }

    private static final class HighMul extends Bin {
        HighMul(Expr left, Expr right) {
            super(left, right);
        }

        @Override
        public String render() {
            return "((int) (((long) " + left.render() + " * (long) " + right.render() + ") >> 32))";
        }

        @Override
        public Expr withChildren(List<Expr> kids) {
            return new HighMul(kids.get(0), kids.get(1));
        }
    }

    private static final class Unary extends Expr {
        private final String op;
        private final Expr operand;

        Unary(String op, Expr operand) {
            this.op = op;
            this.operand = operand;
        }

        @Override
        public String render() {
            return "(" + op + operand.render() + ")";
        }

        @Override
        public List<Expr> children() {
            return List.of(operand);
        }

        @Override
        public Expr withChildren(List<Expr> kids) {
            return new Unary(op, kids.get(0));
        }
    }
}
