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

/**
 * Types of TC-SSA values. See {@code docs/compiler-replacement/ir-specification.md} §1.2.
 *
 * <p>Scalar and vector types are target-independent. {@link ArrayType} and {@link ObjectType} are
 * Java-level types that exist only before TornadoVM ABI lowering; {@link PointerType} is the
 * post-ABI representation of memory.</p>
 */
public sealed interface Type {

    IntType I1 = new IntType(1);
    IntType I8 = new IntType(8);
    IntType I16 = new IntType(16);
    IntType I32 = new IntType(32);
    IntType I64 = new IntType(64);
    FloatType F16 = new FloatType(FloatType.Kind.F16);
    FloatType BF16 = new FloatType(FloatType.Kind.BF16);
    FloatType F32 = new FloatType(FloatType.Kind.F32);
    FloatType F64 = new FloatType(FloatType.Kind.F64);
    VoidType VOID = new VoidType();
    TokenType TOKEN = new TokenType();

    /** The textual form used by the printer and accepted by the parser. */
    String text();

    default boolean isInteger() {
        return this instanceof IntType;
    }

    default boolean isFloat() {
        return this instanceof FloatType;
    }

    /** Integer of {@code bits} width; {@code i1} is the boolean type. */
    record IntType(int bits) implements Type {
        public IntType {
            if (bits != 1 && bits != 8 && bits != 16 && bits != 32 && bits != 64) {
                throw new IllegalArgumentException("unsupported integer width: " + bits);
            }
        }

        @Override
        public String text() {
            return "i" + bits;
        }
    }

    /** IEEE 754 binary floating point, plus bfloat16. */
    record FloatType(Kind kind) implements Type {
        public enum Kind {
            F16("f16", 16), BF16("bf16", 16), F32("f32", 32), F64("f64", 64);

            final String text;
            final int bits;

            Kind(String text, int bits) {
                this.text = text;
                this.bits = bits;
            }
        }

        public int bits() {
            return kind.bits;
        }

        @Override
        public String text() {
            return kind.text;
        }
    }

    /** Fixed-length vector of a scalar element type, for TornadoVM vector types such as {@code Float4}. */
    record VectorType(Type element, int length) implements Type {
        public VectorType {
            if (!(element instanceof IntType || element instanceof FloatType)) {
                throw new IllegalArgumentException("vector element must be scalar: " + element.text());
            }
            if (length != 2 && length != 3 && length != 4 && length != 8 && length != 16) {
                throw new IllegalArgumentException("unsupported vector length: " + length);
            }
        }

        @Override
        public String text() {
            return "vec<" + length + " x " + element.text() + ">";
        }
    }

    /** Opaque pointer into one address space (post-ABI memory). */
    record PointerType(AddressSpace space) implements Type {
        @Override
        public String text() {
            return "ptr<" + space.text() + ">";
        }
    }

    /** A Java or TornadoVM native array before ABI lowering. */
    record ArrayType(Type element) implements Type {
        @Override
        public String text() {
            return "array<" + element.text() + ">";
        }
    }

    /** A Java object; only legal before TornadoVM lowering removes it. */
    record ObjectType(String className) implements Type {
        @Override
        public String text() {
            return "obj<" + className + ">";
        }
    }

    /**
     * A CUDA Tile value ({@code TileContext}): element type (a {@code DType} name such as
     * {@code F32} or {@code PRED}) and a static shape; {@code view} for a partition view, which
     * addresses tiles of that shape in memory instead of holding one.
     */
    record TileType(String dtype, java.util.List<Integer> shape, boolean view) implements Type {
        public TileType {
            shape = java.util.List.copyOf(shape);
        }

        @Override
        public String text() {
            var dims = shape.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
            return (view ? "tileview<" : "tile<") + dtype + (dims.isEmpty() ? "" : ", " + dims) + ">";
        }
    }

    /** Ordering-only result, for example of a barrier. */
    record TokenType() implements Type {
        @Override
        public String text() {
            return "token";
        }
    }

    /** The type of instructions that produce no value. */
    record VoidType() implements Type {
        @Override
        public String text() {
            return "void";
        }
    }
}
