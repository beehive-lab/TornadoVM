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
package uk.ac.manchester.tornado.tc.ir.analysis;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Static code features of a kernel, counted on its final IR: memory accesses per address space,
 * control flow and arithmetic. The runtime reports them with {@code -Dtornado.feature.extraction}
 * (for example to cost models that choose a device).
 */
public record KernelFeatures(Map<Feature, Integer> counts) {

    public enum Feature {
        GLOBAL_LOADS, GLOBAL_STORES, LOCAL_LOADS, LOCAL_STORES, CONSTANT_LOADS, CONSTANT_STORES, PRIVATE_LOADS, PRIVATE_STORES, //
        LOOPS, PARALLEL_LOOPS, IFS, SWITCH, CASE, CAST, VECTORS, //
        INTEGER_OPS, FLOAT_OPS, FP32, DOUBLES, BINARY, BOOLEAN, F_MATH, I_MATH, I_CMP, F_CMP
    }

    public static final KernelFeatures NONE = new KernelFeatures(Map.of());

    public KernelFeatures {
        counts = Map.copyOf(counts);
    }

    public int get(Feature feature) {
        return counts.getOrDefault(feature, 0);
    }

    public static KernelFeatures of(Function function) {
        var counts = new EnumMap<Feature, Integer>(Feature.class);
        for (var feature : Feature.values()) {
            counts.put(feature, 0);
        }
        var parallelDims = new HashSet<Long>();
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                count(inst, counts, parallelDims);
            }
        }
        counts.put(Feature.LOOPS, new LoopForest(function).loops().size());
        counts.put(Feature.PARALLEL_LOOPS, parallelDims.size());
        return new KernelFeatures(counts);
    }

    private static void count(Inst inst, Map<Feature, Integer> counts, java.util.Set<Long> parallelDims) {
        switch (inst.opcode()) {
            case Opcode.Load a -> access(inst.operand(0).type(), true, counts);
            case Opcode.Store b -> access(inst.operand(0).type(), false, counts);
            case Opcode.ArrayLoad c -> add(counts, Feature.GLOBAL_LOADS);
            case Opcode.FieldLoad d -> add(counts, Feature.GLOBAL_LOADS);
            case Opcode.ArrayStore e -> add(counts, Feature.GLOBAL_STORES);
            case Opcode.FieldStore f -> add(counts, Feature.GLOBAL_STORES);
            case Opcode.CondBranch g -> add(counts, Feature.IFS);
            case Opcode.Switch s -> {
                add(counts, Feature.SWITCH);
                counts.merge(Feature.CASE, s.keys().size(), Integer::sum);
            }
            case Opcode.Convert h -> add(counts, Feature.CAST);
            case Opcode.ICmp i -> add(counts, Feature.I_CMP);
            case Opcode.FCmp j -> add(counts, Feature.F_CMP);
            case Opcode.Binary b -> {
                arithmetic(inst.type(), counts);
                switch (b.op()) {
                    case AND, OR, XOR, SHL, SHR, USHR -> add(counts, isBoolean(inst.type()) ? Feature.BOOLEAN : Feature.BINARY);
                    default -> {
                    }
                }
            }
            case Opcode.Unary u -> {
                arithmetic(inst.type(), counts);
                if (u.op() == Opcode.UnaryOp.NOT) {
                    add(counts, isBoolean(inst.type()) ? Feature.BOOLEAN : Feature.BINARY);
                }
            }
            case Opcode.Intrinsic i when i.name().startsWith("math.") -> add(counts, element(inst.type()) instanceof Type.FloatType ? Feature.F_MATH : Feature.I_MATH);
            case Opcode.Intrinsic i when i.name().equals("tornado.thread_id.global") -> inst.immediates().integer("dim").ifPresent(parallelDims::add);
            case Opcode.Intrinsic i when i.name().startsWith("tornado.vector.") -> add(counts, Feature.VECTORS);
            default -> {
            }
        }
    }

    private static void access(Type pointer, boolean load, Map<Feature, Integer> counts) {
        var space = pointer instanceof Type.PointerType p ? p.space() : AddressSpace.GLOBAL;
        add(counts, switch (space) {
            case GLOBAL -> load ? Feature.GLOBAL_LOADS : Feature.GLOBAL_STORES;
            case SHARED -> load ? Feature.LOCAL_LOADS : Feature.LOCAL_STORES;
            case CONSTANT -> load ? Feature.CONSTANT_LOADS : Feature.CONSTANT_STORES;
            case PRIVATE -> load ? Feature.PRIVATE_LOADS : Feature.PRIVATE_STORES;
        });
    }

    private static void arithmetic(Type type, Map<Feature, Integer> counts) {
        if (type instanceof Type.VectorType) {
            add(counts, Feature.VECTORS);
        }
        if (element(type) instanceof Type.FloatType f) {
            add(counts, Feature.FLOAT_OPS);
            if (f.kind() == Type.FloatType.Kind.F64) {
                add(counts, Feature.DOUBLES);
            } else if (f.kind() == Type.FloatType.Kind.F32) {
                add(counts, Feature.FP32);
            }
        } else {
            add(counts, Feature.INTEGER_OPS);
        }
    }

    private static Type element(Type type) {
        return type instanceof Type.VectorType v ? v.element() : type;
    }

    private static boolean isBoolean(Type type) {
        return type instanceof Type.IntType i && i.bits() == 1;
    }

    private static void add(Map<Feature, Integer> counts, Feature feature) {
        counts.merge(feature, 1, Integer::sum);
    }
}
