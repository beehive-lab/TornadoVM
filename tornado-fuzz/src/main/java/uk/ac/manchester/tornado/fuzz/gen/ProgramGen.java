/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.fuzz.RandomGen;

/**
 * Phase 3: random typed programs, not single expressions. A program is a list of statements over
 * int, long, float and (when the device has fp64) double values: local variables, if/else, counted
 * loops (constant and data-dependent trip counts), private arrays, casts between the types, Java's
 * integer helpers ({@code Integer.compareUnsigned}, {@code divideUnsigned}, {@code rotateLeft},
 * {@code bitCount}, {@code reverse}...), {@code Math} calls and computed {@code static final}
 * constants. The body is the same text in the kernel and in the JVM reference, so the JVM is the oracle.
 *
 * <p>Every division is by {@code (x | 1)}: never zero (Java throws), but -1 is possible, so
 * {@code MIN_VALUE / -1} is exercised.</p>
 */
public final class ProgramGen {

    public enum Ty {
        INT("int"), LONG("long"), FLOAT("float"), DOUBLE("double");

        final String java;

        Ty(String java) {
            this.java = java;
        }

        boolean isFloat() {
            return this == FLOAT || this == DOUBLE;
        }
    }

    /** One generated program: its statements and, per output, the expression stored at index i. */
    public record Program(List<String> statements, String outInt, String outLong, String outFloat, String outDouble, boolean transcendental) {

        public Program withStatements(List<String> kept) {
            return new Program(kept, outInt, outLong, outFloat, outDouble, transcendental);
        }

        /** The per-element body: statements then the four stores. */
        public String body(String indent, boolean fp64) {
            var sb = new StringBuilder();
            for (var s : statements) {
                sb.append(indent).append(s.replace("\n", "\n" + indent)).append('\n');
            }
            sb.append(indent).append("oi.set(i, ").append(outInt).append(");\n");
            sb.append(indent).append("ol.set(i, ").append(outLong).append(");\n");
            sb.append(indent).append("of.set(i, ").append(outFloat).append(");\n");
            if (fp64) {
                sb.append(indent).append("od.set(i, ").append(outDouble).append(");\n");
            }
            return sb.toString();
        }
    }

    /** A variable; loop counters are read-only (assigning one could make its loop run forever). */
    private record Var(String name, Ty type, boolean readOnly) {
        Var(String name, Ty type) {
            this(name, type, false);
        }
    }

    private final RandomGen rng;
    private final boolean fp64;
    private final List<Var> vars = new ArrayList<>();
    private int names;
    private int loopDepth;
    private boolean transcendental;

    private ProgramGen(RandomGen rng, boolean fp64) {
        this.rng = rng;
        this.fp64 = fp64;
    }

    public static Program generate(RandomGen rng, boolean fp64) {
        return new ProgramGen(rng, fp64).program();
    }

    private Program program() {
        var statements = new ArrayList<String>();
        int n = rng.nextIntBetween(3, 10);
        for (int s = 0; s < n; s++) {
            statements.add(statement());
        }
        var outI = combine(Ty.INT);
        var outL = combine(Ty.LONG);
        var outF = combine(Ty.FLOAT);
        var outD = fp64 ? combine(Ty.DOUBLE) : "0.0";
        return new Program(statements, outI, outL, outF, outD, transcendental);
    }

    /** An output: the variables of that type folded together, or an expression when there are none. */
    private String combine(Ty t) {
        var of = vars.stream().filter(v -> v.type == t).map(Var::name).toList();
        if (of.isEmpty()) {
            return expr(t, 3);
        }
        String op = t.isFloat() ? " + " : " ^ ";
        return "(" + String.join(op, of) + ")";
    }

    private String statement() {
        int k = rng.nextInt(100);
        if (k < 45 || vars.isEmpty()) {
            return declare();
        }
        if (k < 65) {
            return ifElse();
        }
        if (k < 82 && loopDepth == 0 && vars.stream().anyMatch(x -> !x.readOnly)) {
            return loop();
        }
        if (k < 92) {
            return privateArray();
        }
        return assign(3);
    }

    private Ty randomType() {
        int k = rng.nextInt(fp64 ? 4 : 3);
        return Ty.values()[k];
    }

    private String declare() {
        var t = randomType();
        var e = expr(t, rng.nextIntBetween(1, 4));
        var v = new Var("v" + names++, t);
        vars.add(v);
        return t.java + " " + v.name + " = " + e + ";";
    }

    private String assign(int depth) {
        var writable = vars.stream().filter(x -> !x.readOnly).toList();
        if (writable.isEmpty()) {
            return declare();
        }
        var v = writable.get(rng.nextInt(writable.size()));
        var op = v.type.isFloat() ? rng.pick(new String[] { "=", "+=", "*=" }) : rng.pick(new String[] { "=", "+=", "^=", "-=" });
        return v.name + " " + op + " " + expr(v.type, depth) + ";";
    }

    private String ifElse() {
        var c = cond(2);
        if (vars.stream().noneMatch(x -> !x.readOnly)) {
            return declare();
        }
        return "if (" + c + ") {\n    " + assign(2) + "\n} else {\n    " + assign(2) + "\n}";
    }

    private String loop() {
        String k = "k" + names++;
        String trips = switch (rng.nextInt(3)) {
            case 0 -> Integer.toString(rng.nextIntBetween(0, 6));
            case 1 -> "(ia.get(i) & 7)";
            default -> "(ib.get(i) >>> 29)";
        };
        loopDepth++;
        vars.add(new Var(k, Ty.INT, true));
        var body = assign(2);
        String extra = rng.nextBoolean() ? "\n    if (" + cond(1) + ") {\n        break;\n    }" : "";
        vars.remove(vars.size() - 1);
        loopDepth--;
        return "for (int " + k + " = 0; " + k + " < " + trips + "; " + k + "++) {\n    " + body + extra + "\n}";
    }

    private String privateArray() {
        var t = rng.nextBoolean() ? Ty.INT : Ty.FLOAT;
        String a = "p" + names++;
        String k = "k" + names++;
        vars.add(new Var(k, Ty.INT, true));
        var fill = expr(t, 2);
        vars.remove(vars.size() - 1);
        var v = new Var("v" + names++, t);
        var read = a + "[(" + expr(Ty.INT, 1) + ") & 3]";
        vars.add(v);
        return t.java + "[] " + a + " = new " + t.java + "[4];\nfor (int " + k + " = 0; " + k + " < 4; " + k + "++) {\n    " + a + "[" + k + "] = " + fill + ";\n}\n" + t.java + " "
                + v.name + " = " + read + ";";
    }

    private String cond(int depth) {
        int k = rng.nextInt(10);
        if (depth > 0 && k < 2) {
            return "(" + cond(depth - 1) + (rng.nextBoolean() ? " && " : " || ") + cond(depth - 1) + ")";
        }
        if (depth > 0 && k == 2) {
            return "!" + cond(depth - 1);
        }
        var t = randomType();
        var op = rng.pick(new String[] { "<", "<=", ">", ">=", "==", "!=" });
        return "(" + expr(t, 2) + " " + op + " " + expr(t, 2) + ")";
    }

    private static final int[] INT_EDGES = { Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, 31, 32, -32, 255, 65535 };
    private static final String[] LONG_EDGES = { "Long.MIN_VALUE", "Long.MAX_VALUE", "-1L", "0L", "1L", "63L", "64L", "4294967296L", "-4294967297L" };
    private static final String[] FLOAT_EDGES = { "0.0f", "-0.0f", "1.0f", "-1.5f", "1e30f", "-1e-30f", "3.4e38f", "Float.MIN_VALUE", "0.1f", "2147483648.0f" };
    private static final String[] DOUBLE_EDGES = { "0.0", "-0.0", "1.0", "-2.5", "1e300", "1e-300", "9.3e18", "Double.MIN_VALUE", "0.1" };

    private String leaf(Ty t) {
        var ofType = vars.stream().filter(v -> v.type == t).toList();
        int k = rng.nextInt(100);
        if (k < 35 && !ofType.isEmpty()) {
            return ofType.get(rng.nextInt(ofType.size())).name;
        }
        if (k < 70) {
            return switch (t) {
                case INT -> rng.pick(new String[] { "ia.get(i)", "ib.get(i)", "i" });
                case LONG -> "la.get(i)";
                case FLOAT -> "fa.get(i)";
                case DOUBLE -> "da.get(i)";
            };
        }
        if (k < 76) {
            // Computed static final constants: folded by the compiler from the host's value.
            return switch (t) {
                case INT -> "K.SCALE";
                case LONG -> "K.BIG";
                case FLOAT -> "K.HALF";
                case DOUBLE -> "K.TO_RADIANS";
            };
        }
        return switch (t) {
            case INT -> "(" + (rng.nextBoolean() ? INT_EDGES[rng.nextInt(INT_EDGES.length)] : rng.nextIntBetween(-20, 20)) + ")";
            case LONG -> "(" + rng.pick(LONG_EDGES) + ")";
            case FLOAT -> "(" + rng.pick(FLOAT_EDGES) + ")";
            case DOUBLE -> "(" + rng.pick(DOUBLE_EDGES) + ")";
        };
    }

    private String expr(Ty t, int depth) {
        if (depth <= 0 || rng.nextInt(100) < 20) {
            return leaf(t);
        }
        int d = depth - 1;
        int k = rng.nextInt(100);
        if (k < 8) {
            return "(" + cond(1) + " ? " + expr(t, d) + " : " + expr(t, d) + ")";
        }
        if (k < 18) {
            return cast(t, d);
        }
        return switch (t) {
            case INT -> intExpr(d);
            case LONG -> longExpr(d);
            case FLOAT -> floatExpr(Ty.FLOAT, d);
            case DOUBLE -> floatExpr(Ty.DOUBLE, d);
        };
    }

    private String cast(Ty t, int d) {
        var from = randomType();
        var e = expr(from, d);
        return switch (t) {
            case INT -> rng.pick(new String[] { "(int) ", "(short) ", "(byte) ", "(char) " }) + "(" + e + ")";
            default -> "(" + t.java + ") (" + e + ")";
        };
    }

    private String intExpr(int d) {
        String a = expr(Ty.INT, d);
        String b = expr(Ty.INT, d);
        return switch (rng.nextInt(16)) {
            case 0, 1, 2 -> "(" + a + " " + rng.pick(new String[] { "+", "-", "*" }) + " " + b + ")";
            case 3, 4 -> "(" + a + " " + rng.pick(new String[] { "&", "|", "^" }) + " " + b + ")";
            case 5 -> "(" + a + " " + rng.pick(new String[] { "<<", ">>", ">>>" }) + " " + b + ")";
            case 6 -> "(" + a + " " + rng.pick(new String[] { "/", "%" }) + " (" + b + " | 1))";
            case 7 -> rng.pick(new String[] { "-", "~" }) + "(" + a + ")";
            case 8 -> "Math." + rng.pick(new String[] { "min", "max" }) + "(" + a + ", " + b + ")";
            case 9 -> "Math.abs(" + a + ")";
            case 10 -> "Integer." + rng.pick(new String[] { "bitCount", "numberOfLeadingZeros", "numberOfTrailingZeros", "reverse", "reverseBytes", "highestOneBit", "lowestOneBit", "signum" }) + "(" + a + ")";
            case 11 -> "Integer." + rng.pick(new String[] { "rotateLeft", "rotateRight" }) + "(" + a + ", " + b + ")";
            case 12 -> "Integer.compareUnsigned(" + a + ", " + b + ")";
            case 13 -> "Integer." + rng.pick(new String[] { "divideUnsigned", "remainderUnsigned" }) + "(" + a + ", (" + b + " | 1))";
            case 14 -> "Math." + rng.pick(new String[] { "floorDiv", "floorMod" }) + "(" + a + ", (" + b + " | 1))";
            default -> "Long.compare(" + expr(Ty.LONG, d) + ", " + expr(Ty.LONG, d) + ")";
        };
    }

    private String longExpr(int d) {
        String a = expr(Ty.LONG, d);
        String b = expr(Ty.LONG, d);
        return switch (rng.nextInt(13)) {
            case 0, 1, 2 -> "(" + a + " " + rng.pick(new String[] { "+", "-", "*" }) + " " + b + ")";
            case 3 -> "(" + a + " " + rng.pick(new String[] { "&", "|", "^" }) + " " + b + ")";
            case 4 -> "(" + a + " " + rng.pick(new String[] { "<<", ">>", ">>>" }) + " " + expr(Ty.INT, d) + ")";
            case 5 -> "(" + a + " " + rng.pick(new String[] { "/", "%" }) + " (" + b + " | 1L))";
            case 6 -> rng.pick(new String[] { "-", "~" }) + "(" + a + ")";
            case 7 -> "Math." + rng.pick(new String[] { "min", "max" }) + "(" + a + ", " + b + ")";
            case 8 -> "Long." + rng.pick(new String[] { "reverse", "reverseBytes", "highestOneBit", "lowestOneBit" }) + "(" + a + ")";
            case 9 -> "(long) Long." + rng.pick(new String[] { "bitCount", "numberOfLeadingZeros", "numberOfTrailingZeros", "signum" }) + "(" + a + ")";
            case 10 -> "Long." + rng.pick(new String[] { "divideUnsigned", "remainderUnsigned" }) + "(" + a + ", (" + b + " | 1L))";
            case 11 -> "(long) Long.compareUnsigned(" + a + ", " + b + ")";
            default -> "Integer.toUnsignedLong(" + expr(Ty.INT, d) + ")";
        };
    }

    private String floatExpr(Ty t, int d) {
        String a = expr(t, d);
        String b = expr(t, d);
        String c = t == Ty.FLOAT ? "(float) " : "";
        return switch (rng.nextInt(10)) {
            case 0, 1, 2, 3 -> "(" + a + " " + rng.pick(new String[] { "+", "-", "*", "/" }) + " " + b + ")";
            case 4 -> "-(" + a + ")";
            case 5 -> "Math." + rng.pick(new String[] { "min", "max" }) + "(" + a + ", " + b + ")";
            case 6 -> "Math.abs(" + a + ")";
            case 7 -> c + "Math." + rng.pick(new String[] { "floor", "ceil", "rint" }) + "(" + a + ")";
            case 8 -> c + "Math.sqrt(Math.abs(" + a + "))";
            default -> {
                transcendental = true;
                yield c + "Math." + rng.pick(new String[] { "sin", "cos", "exp", "log", "tanh", "atan" }) + "(" + a + ")";
            }
        };
    }
}
