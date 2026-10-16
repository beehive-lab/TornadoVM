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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.UnaryOp;

/**
 * Parses the TC-SSA text form produced by {@link IrPrinter}. Comments run from {@code ;} to the end
 * of the line. Values and blocks may be referenced before their definition; names are resolved once
 * a function has been read completely.
 *
 * <p>Error codes: {@code TC-E2001} syntax, {@code E2002} undefined value, {@code E2003} undefined
 * block, {@code E2004} value redefined, {@code E2005} unknown operation, {@code E2006} invalid type,
 * {@code E2007} block redefined.</p>
 */
public final class IrParser {

    private enum Kind {
        IDENT, NUMBER, STRING, VALUE, BLOCK, SYMBOL, PUNCT, ARROW, EOF
    }

    private record Token(Kind kind, String text, int offset, int line, int column) {
    }

    /** An instruction whose operands and successors are still names. */
    private record Pending(Inst inst, List<Token> operands, List<PendingSuccessor> successors) {
    }

    private record PendingSuccessor(Token label, List<Token> args) {
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;

    private IrParser(String source) {
        this.source = source;
        lex();
    }

    public static IrModule parseModule(String text) {
        var parser = new IrParser(text);
        var module = new IrModule();
        while (parser.peek().kind != Kind.EOF) {
            module.add(parser.parseFunction());
        }
        return module;
    }

    public static Function parseFunction(String text) {
        var module = parseModule(text);
        if (module.functions().size() != 1) {
            throw new IllegalArgumentException("expected exactly one function, found " + module.functions().size());
        }
        return module.functions().get(0);
    }

    // ---------------------------------------------------------------- functions and blocks

    private Function parseFunction() {
        var kindToken = expect(Kind.IDENT, "'kernel' or 'device'");
        var kind = switch (kindToken.text) {
            case "kernel" -> Function.Kind.KERNEL;
            case "device" -> Function.Kind.DEVICE;
            default -> throw error("TC-E2001", kindToken, "expected 'kernel' or 'device', found '" + kindToken.text + "'");
        };
        var name = expect(Kind.SYMBOL, "function name '@...'").text;
        expectPunct("(");
        record Param(String name, Type type) {
        }
        var params = new ArrayList<Param>();
        if (!isPunct(")")) {
            do {
                var paramName = expect(Kind.VALUE, "parameter '%name'");
                expectPunct(":");
                params.add(new Param(paramName.text, parseType()));
            } while (acceptPunct(","));
        }
        expectPunct(")");
        expect(Kind.ARROW, "'->'");
        var function = new Function(name, kind, parseType());
        parseAttributes().forEach(function::addAttribute);
        expectPunct("{");

        Map<String, Value> values = new HashMap<>();
        Map<String, Block> blocks = new HashMap<>();
        var pending = new ArrayList<Pending>();
        var firstBlock = true;
        while (!isPunct("}")) {
            var labelToken = expect(Kind.BLOCK, "block label '^name'");
            if (blocks.containsKey(labelToken.text)) {
                throw error("TC-E2007", labelToken, "block ^" + labelToken.text + " is defined twice");
            }
            var block = function.newBlock(labelToken.text);
            blocks.put(labelToken.text, block);
            if (firstBlock) {
                for (var param : params) {
                    define(values, param.name, block.addArg(param.type, param.name), labelToken);
                }
            }
            if (acceptPunct("(")) {
                if (firstBlock) {
                    throw error("TC-E2001", labelToken, "the entry block takes the function parameters; it cannot declare arguments");
                }
                do {
                    var argName = expect(Kind.VALUE, "block argument '%name'");
                    expectPunct(":");
                    define(values, argName.text, block.addArg(parseType(), argName.text), argName);
                } while (acceptPunct(","));
                expectPunct(")");
            }
            parseAttributes().forEach(block::addAttribute);
            expectPunct(":");
            while (peek().kind != Kind.BLOCK && !isPunct("}")) {
                pending.add(parseInst(block, values));
            }
            firstBlock = false;
        }
        expectPunct("}");
        resolve(pending, values, blocks);
        return function;
    }

    private void resolve(List<Pending> pending, Map<String, Value> values, Map<String, Block> blocks) {
        for (var p : pending) {
            for (var operand : p.operands) {
                p.inst.addOperand(lookup(values, operand));
            }
            for (var successor : p.successors) {
                var target = blocks.get(successor.label.text);
                if (target == null) {
                    throw error("TC-E2003", successor.label, "undefined block ^" + successor.label.text);
                }
                p.inst.addSuccessor(target, successor.args.stream().map(a -> lookup(values, a)).toList());
            }
        }
    }

    private Value lookup(Map<String, Value> values, Token token) {
        var value = values.get(token.text);
        if (value == null) {
            throw error("TC-E2002", token, "undefined value %" + token.text);
        }
        return value;
    }

    private void define(Map<String, Value> values, String name, Value value, Token at) {
        if (values.putIfAbsent(name, value) != null) {
            throw error("TC-E2004", at, "value %" + name + " is defined twice");
        }
    }

    // ---------------------------------------------------------------- instructions

    private Pending parseInst(Block block, Map<String, Value> values) {
        Token result = null;
        if (peek().kind == Kind.VALUE) {
            result = next();
            expectPunct("=");
        }
        var start = peek();
        var operands = new ArrayList<Token>();
        var successors = new ArrayList<PendingSuccessor>();
        var mnemonic = expect(Kind.IDENT, "an operation").text;
        Opcode opcode;
        Immediates immediates = Immediates.NONE;
        switch (mnemonic) {
            case "const" -> opcode = parseConstant();
            case "array.load" -> {
                opcode = new Opcode.ArrayLoad();
                operands.add(expect(Kind.VALUE, "array"));
                expectPunct("[");
                operands.add(expect(Kind.VALUE, "index"));
                expectPunct("]");
            }
            case "array.store" -> {
                opcode = new Opcode.ArrayStore();
                operands.add(expect(Kind.VALUE, "array"));
                expectPunct("[");
                operands.add(expect(Kind.VALUE, "index"));
                expectPunct("]");
                expectPunct(",");
                operands.add(expect(Kind.VALUE, "value"));
            }
            case "field.load" -> {
                operands.add(expect(Kind.VALUE, "object"));
                expectPunct(",");
                opcode = new Opcode.FieldLoad(unquote(expect(Kind.STRING, "field name")));
            }
            case "field.store" -> {
                operands.add(expect(Kind.VALUE, "object"));
                expectPunct(",");
                opcode = new Opcode.FieldStore(unquote(expect(Kind.STRING, "field name")));
                expectPunct(",");
                operands.add(expect(Kind.VALUE, "value"));
            }
            case "call" -> {
                opcode = new Opcode.Call(expect(Kind.SYMBOL, "callee '@name'").text);
                operands.addAll(parenthesisedValues());
            }
            case "br" -> {
                opcode = new Opcode.Branch();
                successors.add(parseSuccessor());
            }
            case "cond_br" -> {
                opcode = new Opcode.CondBranch();
                operands.add(expect(Kind.VALUE, "condition"));
                expectPunct(",");
                successors.add(parseSuccessor());
                expectPunct(",");
                successors.add(parseSuccessor());
            }
            case "switch" -> {
                operands.add(expect(Kind.VALUE, "selector"));
                expectPunct("[");
                var keys = new ArrayList<Long>();
                if (!isPunct("]")) {
                    do {
                        keys.add(parseLong(expect(Kind.NUMBER, "case key")));
                        expectPunct(":");
                        successors.add(parseSuccessor());
                    } while (acceptPunct(","));
                }
                expectPunct("]");
                var defaultKeyword = expect(Kind.IDENT, "'default'");
                if (!defaultKeyword.text.equals("default")) {
                    throw error("TC-E2001", defaultKeyword, "expected 'default'");
                }
                successors.add(parseSuccessor());
                opcode = new Opcode.Switch(keys);
            }
            case "ret" -> {
                opcode = new Opcode.Return();
                // Instructions are one per line: a value on the next line starts the next instruction.
                if (peek().kind == Kind.VALUE && peek().line == start.line) {
                    operands.add(next());
                }
            }
            case "unreachable" -> opcode = new Opcode.Unreachable();
            default -> {
                if (mnemonic.startsWith("alloca.")) {
                    AddressSpace space;
                    try {
                        space = AddressSpace.fromText(mnemonic.substring("alloca.".length()));
                    } catch (IllegalArgumentException e) {
                        throw error("TC-E2005", start, e.getMessage());
                    }
                    var element = parseType();
                    expectPunct("[");
                    var count = (int) parseLong(expect(Kind.NUMBER, "element count"));
                    expectPunct("]");
                    opcode = new Opcode.Alloca(space, element, count);
                } else if (isPunct("(") || isPunct("<")) {
                    opcode = new Opcode.Intrinsic(mnemonic);
                    immediates = parseImmediates();
                    operands.addAll(parenthesisedValues());
                } else {
                    opcode = simpleOpcode(mnemonic, start);
                    operands.addAll(valueList());
                }
            }
        }
        Type type = Type.VOID;
        if (result != null) {
            expectPunct(":");
            type = parseType();
        }
        var inst = new Inst(opcode, type);
        inst.setImmediates(immediates);
        inst.setTextLine(start.line);
        inst.setTextLine(start.line);
        if (result != null) {
            inst.setName(result.text);
        }
        parseAttributes().forEach(inst::addAttribute);
        if (peek().kind == Kind.IDENT && peek().text.equals("loc")) {
            inst.setLoc(parseLoc());
        }
        block.append(inst);
        if (result != null) {
            define(values, result.text, inst, result);
        }
        return new Pending(inst, operands, successors);
    }

    private Opcode simpleOpcode(String mnemonic, Token at) {
        for (var op : BinaryOp.values()) {
            if (op.text.equals(mnemonic)) {
                return new Opcode.Binary(op);
            }
        }
        for (var op : UnaryOp.values()) {
            if (op.text.equals(mnemonic)) {
                return new Opcode.Unary(op);
            }
        }
        for (var op : ConvertOp.values()) {
            if (op.text.equals(mnemonic)) {
                return new Opcode.Convert(op);
            }
        }
        if (mnemonic.startsWith("icmp.")) {
            for (var p : IntPredicate.values()) {
                if (mnemonic.equals("icmp." + p.text())) {
                    return new Opcode.ICmp(p);
                }
            }
        }
        if (mnemonic.startsWith("fcmp.")) {
            for (var p : FloatPredicate.values()) {
                if (mnemonic.equals("fcmp." + p.text())) {
                    return new Opcode.FCmp(p);
                }
            }
        }
        return switch (mnemonic) {
            case "select" -> new Opcode.Select();
            case "array.length" -> new Opcode.ArrayLength();
            case "ptr.offset" -> new Opcode.PointerOffset();
            case "load" -> new Opcode.Load();
            case "store" -> new Opcode.Store();
            default -> throw error("TC-E2005", at, "unknown operation '" + mnemonic + "' (intrinsics need parentheses: " + mnemonic + "(...))");
        };
    }

    private Opcode parseConstant() {
        var token = next();
        if (token.kind == Kind.IDENT) {
            return switch (token.text) {
                case "nan" -> new Opcode.ConstFloat(Double.NaN);
                case "inf" -> new Opcode.ConstFloat(Double.POSITIVE_INFINITY);
                case "-inf" -> new Opcode.ConstFloat(Double.NEGATIVE_INFINITY);
                default -> throw error("TC-E2001", token, "expected a constant, found '" + token.text + "'");
            };
        }
        if (token.kind != Kind.NUMBER) {
            throw error("TC-E2001", token, "expected a constant");
        }
        var text = token.text;
        if (text.contains(".") || text.contains("e") || text.contains("E")) {
            return new Opcode.ConstFloat(Double.parseDouble(text));
        }
        return new Opcode.ConstInt(parseLong(token));
    }

    private PendingSuccessor parseSuccessor() {
        var label = expect(Kind.BLOCK, "successor '^label'");
        var args = new ArrayList<Token>();
        if (isPunct("(")) {
            args.addAll(parenthesisedValues());
        }
        return new PendingSuccessor(label, args);
    }

    private List<Token> parenthesisedValues() {
        expectPunct("(");
        var values = isPunct(")") ? List.<Token>of() : valueList();
        expectPunct(")");
        return values;
    }

    private List<Token> valueList() {
        var values = new ArrayList<Token>();
        values.add(expect(Kind.VALUE, "operand '%name'"));
        while (acceptPunct(",")) {
            values.add(expect(Kind.VALUE, "operand '%name'"));
        }
        return values;
    }

    private List<Attribute> parseAttributes() {
        var attributes = new ArrayList<Attribute>();
        if (!acceptPunct("[")) {
            return attributes;
        }
        do {
            var name = expect(Kind.IDENT, "attribute name").text;
            attributes.add(new Attribute(name, parseImmediates()));
        } while (acceptPunct(","));
        expectPunct("]");
        return attributes;
    }

    private Loc parseLoc() {
        next(); // loc
        expectPunct("(");
        var file = unquote(expect(Kind.STRING, "file name"));
        expectPunct(":");
        var line = (int) parseLong(expect(Kind.NUMBER, "line"));
        var bci = -1;
        if (acceptPunct(",")) {
            var key = expect(Kind.IDENT, "'bci'");
            if (!key.text.equals("bci")) {
                throw error("TC-E2001", key, "expected 'bci'");
            }
            expectPunct("=");
            bci = (int) parseLong(expect(Kind.NUMBER, "bytecode index"));
        }
        expectPunct(")");
        return new Loc(file, line, bci);
    }

    // ---------------------------------------------------------------- types

    private Type parseType() {
        var token = expect(Kind.IDENT, "a type");
        return switch (token.text) {
            case "i1" -> Type.I1;
            case "i8" -> Type.I8;
            case "i16" -> Type.I16;
            case "i32" -> Type.I32;
            case "i64" -> Type.I64;
            case "f16" -> Type.F16;
            case "bf16" -> Type.BF16;
            case "f32" -> Type.F32;
            case "f64" -> Type.F64;
            case "void" -> Type.VOID;
            case "token" -> Type.TOKEN;
            case "vec" -> {
                expectPunct("<");
                var length = (int) parseLong(expect(Kind.NUMBER, "vector length"));
                var x = expect(Kind.IDENT, "'x'");
                if (!x.text.equals("x")) {
                    throw error("TC-E2006", x, "expected 'x' in vector type");
                }
                var element = parseType();
                expectPunct(">");
                try {
                    yield new Type.VectorType(element, length);
                } catch (IllegalArgumentException e) {
                    throw error("TC-E2006", token, e.getMessage());
                }
            }
            case "ptr" -> {
                expectPunct("<");
                var space = expect(Kind.IDENT, "address space");
                expectPunct(">");
                try {
                    yield new Type.PointerType(AddressSpace.fromText(space.text));
                } catch (IllegalArgumentException e) {
                    throw error("TC-E2006", space, e.getMessage());
                }
            }
            case "array" -> {
                expectPunct("<");
                var element = parseType();
                expectPunct(">");
                yield new Type.ArrayType(element);
            }
            case "obj" -> {
                expectPunct("<");
                var className = expect(Kind.IDENT, "class name").text;
                expectPunct(">");
                yield new Type.ObjectType(className);
            }
            case "tile", "tileview" -> {
                expectPunct("<");
                var dtype = expect(Kind.IDENT, "tile element type").text;
                var shape = new ArrayList<Integer>();
                while (acceptPunct(",")) {
                    shape.add((int) parseLong(expect(Kind.NUMBER, "tile extent")));
                }
                expectPunct(">");
                yield new Type.TileType(dtype, shape, token.text.equals("tileview"));
            }
            default -> throw error("TC-E2006", token, "unknown type '" + token.text + "'");
        };
    }

    // ---------------------------------------------------------------- token helpers

    private Token peek() {
        return tokens.get(pos);
    }

    private Token next() {
        var token = tokens.get(pos);
        if (token.kind != Kind.EOF) {
            pos++;
        }
        return token;
    }

    private Token expect(Kind kind, String what) {
        var token = peek();
        if (token.kind != kind) {
            throw error("TC-E2001", token, "expected " + what + ", found " + describe(token));
        }
        return next();
    }

    private boolean isPunct(String text) {
        var token = peek();
        return token.kind == Kind.PUNCT && token.text.equals(text);
    }

    private boolean acceptPunct(String text) {
        if (isPunct(text)) {
            next();
            return true;
        }
        return false;
    }

    private void expectPunct(String text) {
        if (!acceptPunct(text)) {
            throw error("TC-E2001", peek(), "expected '" + text + "', found " + describe(peek()));
        }
    }

    private long parseLong(Token token) {
        try {
            return Long.parseLong(token.text);
        } catch (NumberFormatException e) {
            throw error("TC-E2001", token, "expected an integer, found '" + token.text + "'");
        }
    }

    /** {@code <lane = 3, mode = "tail">} after an intrinsic's name; none when absent. */
    private Immediates parseImmediates() {
        if (!acceptPunct("<")) {
            return Immediates.NONE;
        }
        var values = new java.util.TreeMap<String, Object>();
        do {
            var key = expect(Kind.IDENT, "immediate name").text;
            expectPunct("=");
            if (peek().kind == Kind.STRING) {
                values.put(key, unquote(next()));
            } else {
                values.put(key, parseLong(expect(Kind.NUMBER, "immediate value")));
            }
        } while (acceptPunct(","));
        expectPunct(">");
        return new Immediates(values);
    }

    private static String unquote(Token token) {
        return Immediates.unquote(token.text);
    }

    private static String describe(Token token) {
        return switch (token.kind) {
            case EOF -> "end of input";
            case VALUE -> "'%" + token.text + "'";
            case BLOCK -> "'^" + token.text + "'";
            case SYMBOL -> "'@" + token.text + "'";
            default -> "'" + token.text + "'";
        };
    }

    private IrParseException error(String code, Token token, String message) {
        return new IrParseException(code, message, token.line, token.column);
    }

    // ---------------------------------------------------------------- lexer

    private void lex() {
        int i = 0;
        int line = 1;
        int lineStart = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            int column = i - lineStart + 1;
            if (c == '\n') {
                line++;
                lineStart = i + 1;
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else if (c == ';') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '%' || c == '^' || c == '@') {
                int end = scanName(i + 1);
                if (end == i + 1) {
                    throw new IrParseException("TC-E2001", "empty name after '" + c + "'", line, column);
                }
                var kind = c == '%' ? Kind.VALUE : c == '^' ? Kind.BLOCK : Kind.SYMBOL;
                tokens.add(new Token(kind, source.substring(i + 1, end), i, line, column));
                i = end;
            } else if (c == '"') {
                int end = i + 1;
                while (end < source.length() && source.charAt(end) != '"') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                if (end >= source.length()) {
                    throw new IrParseException("TC-E2001", "unterminated string", line, column);
                }
                tokens.add(new Token(Kind.STRING, source.substring(i, end + 1), i, line, column));
                i = end + 1;
            } else if (c == '-' && i + 1 < source.length() && source.charAt(i + 1) == '>') {
                tokens.add(new Token(Kind.ARROW, "->", i, line, column));
                i += 2;
            } else if (c == '-' && source.startsWith("-inf", i)) {
                tokens.add(new Token(Kind.IDENT, "-inf", i, line, column));
                i += 4;
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < source.length() && Character.isDigit(source.charAt(i + 1)))) {
                int end = i + 1;
                while (end < source.length()) {
                    char d = source.charAt(end);
                    boolean exponentSign = (d == '-' || d == '+') && (source.charAt(end - 1) == 'e' || source.charAt(end - 1) == 'E');
                    if (Character.isDigit(d) || d == '.' || d == 'e' || d == 'E' || exponentSign) {
                        end++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(Kind.NUMBER, source.substring(i, end), i, line, column));
                i = end;
            } else if (Character.isLetter(c) || c == '_') {
                int end = scanName(i);
                tokens.add(new Token(Kind.IDENT, source.substring(i, end), i, line, column));
                i = end;
            } else if ("()[]{},:=<>".indexOf(c) >= 0) {
                tokens.add(new Token(Kind.PUNCT, String.valueOf(c), i, line, column));
                i++;
            } else {
                throw new IrParseException("TC-E2001", "unexpected character '" + c + "'", line, column);
            }
        }
        tokens.add(new Token(Kind.EOF, "", source.length(), line, source.length() - lineStart + 1));
    }

    private int scanName(int from) {
        int end = from;
        while (end < source.length()) {
            char d = source.charAt(end);
            if (Character.isLetterOrDigit(d) || d == '_' || d == '.' || d == '$') {
                end++;
            } else {
                break;
            }
        }
        return end;
    }
}
