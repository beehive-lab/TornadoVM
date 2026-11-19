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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.IntrinsicRegistry;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * What a method compiled as a device function may not do (TC-E3502), checked on the Java-level IR
 * after inlining. A device function is a per-row function that a host library calls from its own
 * kernel ({@code cudf::transform} calls it once per row, from a grid-stride loop of its own): it sees
 * only its arguments. So it may not use anything that belongs to a kernel launch: thread or group
 * indices and sizes, {@code @Parallel} loops, barriers, warp or matrix operations, atomics, shared or
 * local memory, device launches, tiles, or {@code Debug.printf}. Private arrays ({@code new int[4]})
 * are allowed: they live in the function's own frame.
 *
 * <p>
 * Allowed intrinsics are a whitelist (math, vector values, {@code dp4a}), so an intrinsic added later
 * is refused here until it is known to be safe in a device function.
 */
public final class DeviceFunctionCheck {

    /** Intrinsics a device function may use: values computed from values. */
    private static final List<String> ALLOWED = List.of("math.", "tornado.vector.make", "tornado.vector.lane", "tornado.vector.insert", "tornado.dp4a");

    private DeviceFunctionCheck() {
    }

    public static List<Diagnostic> run(Function function, TargetCaps target) {
        var problems = new ArrayList<Diagnostic>();
        if (target.dialect() != TargetCaps.Dialect.CUDA) {
            problems.add(new Diagnostic("TC-E3504", "device-function output is CUDA C++ only, not " + target.dialect(), "@" + function.name(), Loc.UNKNOWN));
            return problems;
        }
        for (var block : function.blocks()) {
            if (block.attributes().stream().anyMatch(a -> a.name().equals("parallel"))) {
                problems.add(new Diagnostic("TC-E3502", "a device function cannot contain a @Parallel loop", "@" + function.name(), Loc.UNKNOWN));
            }
            for (var inst : block.insts()) {
                switch (inst.opcode()) {
                    case Opcode.Intrinsic(var name) when ALLOWED.stream().noneMatch(name::startsWith) -> {
                        var what = IntrinsicRegistry.lookup(name).map(IntrinsicRegistry.Def::description).filter(d -> !d.isBlank()).orElse(name);
                        problems.add(new Diagnostic("TC-E3502", "a device function cannot use " + what + " (" + name + ")", "@" + function.name(), inst.loc()));
                    }
                    case Opcode.Alloca alloca when alloca.space() != AddressSpace.PRIVATE -> problems.add(new Diagnostic("TC-E3502",
                            "a device function cannot allocate " + alloca.space().name().toLowerCase(java.util.Locale.ROOT) + " memory", "@" + function.name(), inst.loc()));
                    default -> {
                    }
                }
            }
        }
        return problems;
    }
}
