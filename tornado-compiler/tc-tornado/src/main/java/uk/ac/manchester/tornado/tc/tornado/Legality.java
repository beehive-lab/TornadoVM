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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * What the target cannot run, refused before code is generated, from {@link TargetCaps} (the
 * driver's device queries). Each check names the construct and the limit:
 * <ul>
 * <li>TC-E4012: double precision on a device without it;</li>
 * <li>TC-E4013: half precision on a device without it (not on OpenCL, whose compilers take half
 * with the pragma);</li>
 * <li>TC-E4015: warp operations, written for 32-lane warps, on another SIMD width;</li>
 * <li>TC-E4016: a device launch where kernels cannot launch kernels;</li>
 * <li>TC-E4017: tensor-core MMA or {@code cp.async} below CUDA sm_80;</li>
 * <li>TC-E4018: FP8 tensor-core MMA below sm_89, or with an NVRTC older than 12.4 (PTX ISA 8.4);</li>
 * <li>TC-E4010, TC-E4011: CUDA Tile kernels ({@link TileTyping#checkTarget}).</li>
 * </ul>
 */
public final class Legality {

    private Legality() {
    }

    public static List<Diagnostic> run(Function function, TargetCaps target) {
        var problems = new ArrayList<Diagnostic>();
        if (!target.fp64()) {
            first(function, t -> uses(t, Type.F64)).ifPresent(i -> problems.add(at(function, i, "TC-E4012", "this kernel uses double precision, which the device does not support")));
        }
        // OpenCL compilers accept half, scalars and vectors, with the cl_khr_fp16 pragma even where the
        // extension is not advertised (the RTX 4090's driver), which the Graal path relies on too.
        if (!target.fp16() && target.dialect() != TargetCaps.Dialect.OPENCL) {
            first(function, t -> uses(t, Type.F16)).ifPresent(i -> problems.add(at(function, i, "TC-E4013", "this kernel uses half precision, which the device does not support")));
        }
        // Local memory is not checked here: the driver is the authority, and its limit is not the one the
        // device reports (CUDA takes up to the opt-in size, 99 KB on sm_89, against 48 KB reported;
        // NVIDIA's OpenCL adds its own bytes; Metal's pipeline aligns). It refuses with the reason.
        if (target.warpSize() != 32) {
            insts(function).filter(i -> intrinsic(i, "tornado.simd.")).findFirst()
                    .ifPresent(i -> problems.add(at(function, i, "TC-E4015", "warp operations are written for 32-lane warps; the device's SIMD width is " + target.warpSize())));
        }
        if (!target.deviceLaunch()) {
            insts(function).filter(i -> intrinsic(i, "tornado.device_launch")).findFirst()
                    .ifPresent(i -> problems.add(at(function, i, "TC-E4016", "KernelContext.launch needs kernels that launch kernels (CUDA dynamic parallelism), which this target lacks")));
        }
        if (target.dialect() == TargetCaps.Dialect.CUDA) {
            var mma = insts(function).filter(i -> intrinsic(i, "tornado.mma.") || intrinsic(i, "tornado.async_copy")).findFirst();
            var fp8 = insts(function).filter(i -> intrinsic(i, "tornado.mma.m16n8k32_e4m3") || intrinsic(i, "tornado.mma.m16n8k32_e5m2")).findFirst();
            if (!target.mma() && mma.isPresent()) {
                problems.add(at(function, mma.get(), "TC-E4017", "tensor-core MMA and cp.async need compute capability 8.0 or higher; the device is sm_" + target.computeCapability()));
            } else if (!target.fp8Mma() && fp8.isPresent()) {
                problems.add(at(function, fp8.get(), "TC-E4018", target.computeCapability() < 89
                        ? "FP8 tensor-core MMA needs compute capability 8.9 or higher; the device is sm_" + target.computeCapability()
                        : "FP8 tensor-core MMA needs PTX ISA 8.4 (NVRTC 12.4 or newer); NVRTC is " + target.nvrtcVersion() / 1000 + "." + target.nvrtcVersion() % 1000));
            }
        }
        insts(function).filter(i -> intrinsic(i, "tornado.vector.lane_at") || intrinsic(i, "tornado.vector.insert_at")).findFirst()
                .ifPresent(i -> problems.add(at(function, i, "TC-E1010", "vector lanes can only be accessed at a constant index (a loop over lanes must have a constant trip count to unroll)")));
                if (TileTyping.usesTiles(function)) {
            if (target.dialect() != TargetCaps.Dialect.CUDA) {
                problems.add(new Diagnostic("TC-E4010", "TileContext kernels are CUDA Tile C++: they run only on the CUDA backend", "@" + function.name(), Loc.UNKNOWN));
            } else {
                problems.addAll(TileTyping.checkTarget(function, target.computeCapability()));
            }
        }
        return problems;
    }

    private static Stream<Inst> insts(Function function) {
        return function.blocks().stream().flatMap(b -> b.insts().stream());
    }

    private static java.util.Optional<Inst> first(Function function, java.util.function.Predicate<Type> type) {
        // The first instruction producing or using such a value (parameters count where they are used).
        return insts(function).filter(i -> type.test(i.type()) || i.operands().stream().anyMatch(o -> type.test(o.type()))).findFirst();
    }

    private static boolean uses(Type type, Type scalar) {
        return switch (type) {
            case Type.VectorType(var element, var length) -> element.equals(scalar);
            case Type.ArrayType(var element) -> uses(element, scalar);
            default -> type.equals(scalar);
        };
    }

    private static boolean intrinsic(Inst inst, String prefix) {
        return inst.opcode() instanceof Opcode.Intrinsic(var name) && name.startsWith(prefix);
    }

    private static Diagnostic at(Function function, Inst inst, String code, String message) {
        return new Diagnostic(code, message, "@" + function.name(), inst.loc());
    }
}
