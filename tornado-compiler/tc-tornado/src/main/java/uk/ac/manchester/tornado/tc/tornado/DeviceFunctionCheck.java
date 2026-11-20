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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.IntrinsicRegistry;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
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
 * A {@code byte[]} parameter is one row of a strings column: the library passes a
 * {@code cudf::string_view}, and the method sees its UTF-8 bytes. It is read-only (a store into it is
 * refused), and its {@code .length} becomes {@link #STRING_LENGTH}, the view's {@code size_bytes()},
 * since a headerless view has no length of its own to read.
 *
 * <p>
 * Read-only is enforced by where a store may go, not by tracking the row: a store must go directly into
 * a private array the function allocates. A store into anything else -- a string row, or an array
 * chosen at run time ({@code byte[] a = cond ? s : new byte[4]}, a block argument or a select) that
 * might be one -- is refused. The emitter also declares the row {@code const}, so a store that got past
 * this check would not compile.
 *
 * <p>
 * Allowed intrinsics are a whitelist (math, vector values, {@code dp4a}), so an intrinsic added later
 * is refused here until it is known to be safe in a device function.
 */
public final class DeviceFunctionCheck {

    /** Intrinsics a device function may use: values computed from values. */
    private static final List<String> ALLOWED = List.of("math.", "tornado.vector.make", "tornado.vector.lane", "tornado.vector.insert", "tornado.dp4a", "tornado.devfn.");

    /** The byte length of a string row ({@code byte[]} parameter): the emitter prints the view's {@code size_bytes()}. */
    public static final String STRING_LENGTH = "tornado.devfn.string_length";

    private DeviceFunctionCheck() {
    }

    public static List<Diagnostic> run(Function function, TargetCaps target) {
        var problems = new ArrayList<Diagnostic>();
        if (target.dialect() != TargetCaps.Dialect.CUDA) {
            problems.add(new Diagnostic("TC-E3504", "device-function output is CUDA C++ only, not " + target.dialect(), "@" + function.name(), Loc.UNKNOWN));
            return problems;
        }
        var strings = new HashSet<Value>();
        var privateArrays = new HashSet<Value>();
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Alloca alloca && alloca.space() == AddressSpace.PRIVATE) {
                    privateArrays.add(inst);
                }
            }
        }
        for (var param : function.params()) {
            if (param.type() instanceof Type.ArrayType) {
                strings.add(param);
            }
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
                    case Opcode.ArrayStore _s when strings.contains(inst.operand(0)) -> problems.add(new Diagnostic("TC-E3502",
                            "a device function cannot write into a string row (a byte[] parameter is the row's bytes, read-only)", "@" + function.name(), inst.loc()));
                    case Opcode.ArrayStore _s when !privateArrays.contains(inst.operand(0)) -> problems.add(new Diagnostic("TC-E3502",
                            "a device function stores only directly into a private array it allocates, not into an array chosen at run time, which might be a read-only string row",
                            "@" + function.name(), inst.loc()));
                    case Opcode.Alloca alloca when alloca.space() != AddressSpace.PRIVATE -> problems.add(new Diagnostic("TC-E3502",
                            "a device function cannot allocate " + alloca.space().name().toLowerCase(Locale.ROOT) + " memory", "@" + function.name(), inst.loc()));
                    default -> {
                    }
                }
            }
        }
        if (problems.isEmpty()) {
            lowerStringLengths(function, strings);
        }
        return problems;
    }

    /** {@code row.length} on a string row becomes {@link #STRING_LENGTH} of the row. */
    private static void lowerStringLengths(Function function, Set<Value> strings) {
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (inst.opcode() instanceof Opcode.ArrayLength && strings.contains(inst.operand(0))) {
                    var length = IrBuilder.insertingBefore(inst).intrinsic(STRING_LENGTH, inst.type(), List.of(inst.operand(0)));
                    inst.replaceAllUsesWith(length);
                    inst.erase();
                }
            }
        }
    }
}
