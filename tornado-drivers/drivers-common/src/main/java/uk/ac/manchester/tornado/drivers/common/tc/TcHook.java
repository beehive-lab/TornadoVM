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
package uk.ac.manchester.tornado.drivers.common.tc;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.domain.IntDomain;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/**
 * The runtime side of TC: compiles a task method with the Graal-free compiler instead of Graal when
 * {@code -Dtornado.compiler=tc}. Specialises on the actual task arguments (array lengths, scalars),
 * returns the source, entry point and launch domain, and records statistics. When TC cannot compile
 * a kernel the caller falls back to Graal unless {@code -Dtornado.compiler.fallback=false}.
 *
 * <p>Options: {@code tornado.compiler=graal|tc}, {@code tornado.compiler.fallback=true|false},
 * {@code tornado.tc.verbose=true} (one line per kernel), {@code tornado.tc.stats=true} (summary at
 * exit), and the {@code tornado.tc.*} dump options of {@link TcOptions}.</p>
 */
public final class TcHook {

    public static final boolean ENABLED = "tc".equals(System.getProperty("tornado.compiler", "graal"));
    public static final boolean FALLBACK = Boolean.parseBoolean(System.getProperty("tornado.compiler.fallback", "true"));
    private static final boolean VERBOSE = Boolean.getBoolean("tornado.tc.verbose");
    private static final boolean STATS = Boolean.getBoolean("tornado.tc.stats");

    private static final AtomicInteger COMPILED = new AtomicInteger();
    private static final Map<String, AtomicInteger> FALLBACKS = new ConcurrentHashMap<>();

    static {
        if (ENABLED && STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread(TcHook::printStats));
        }
    }

    /** A kernel compiled by TC. */
    public record Kernel(byte[] source, String entryName, Optional<DomainTree> domain, long nanos) {
    }

    private TcHook() {
    }

    /**
     * Compiles {@code method} for the actual {@code arguments}. Returns empty when TC cannot compile
     * it; throws when fallback is disabled.
     */
    public static Optional<Kernel> compile(Method method, Object[] arguments, TargetCaps target) {
        var facts = new ArrayList<ValueSpecialisation.Fact>();
        for (var argument : arguments) {
            facts.add(argument instanceof TornadoNativeArray array ? new ValueSpecialisation.Fact.Length(array.getSizeLong()) : ValueSpecialisation.Fact.of(argument));
        }
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        var c = TcCompiler.compileJava(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor, target, TcOptions.fromSystemProperties(), facts);
        var name = owner.getSimpleName() + "." + method.getName();
        if (!c.succeeded()) {
            var d = c.diagnostics().isEmpty() ? null : c.diagnostics().get(0);
            var reason = d == null ? "unknown" : d.code() + " " + d.message();
            FALLBACKS.computeIfAbsent(d == null ? "unknown" : d.code(), k -> new AtomicInteger()).incrementAndGet();
            if (!FALLBACK) {
                throw new IllegalStateException("[TC] cannot compile " + name + " and fallback is disabled: " + (d == null ? reason : d.render()));
            }
            if (VERBOSE) {
                System.out.println("[TC] fallback to Graal for " + name + ": " + reason);
            }
            return Optional.empty();
        }
        COMPILED.incrementAndGet();
        if (VERBOSE) {
            System.out.printf("[TC] compiled %s in %.2f ms%n", name, c.nanos() / 1e6);
        }
        Optional<DomainTree> domain = Optional.empty();
        if (!c.domain().isEmpty()) {
            var tree = new DomainTree(c.domain().size());
            for (var d : c.domain()) {
                tree.set(d.dim(), new IntDomain((int) d.offset(), (int) d.step(), (int) d.upper()));
            }
            domain = Optional.of(tree);
        }
        return Optional.of(new Kernel(c.source().getBytes(StandardCharsets.UTF_8), c.entryName(), domain, c.nanos()));
    }

    private static void printStats() {
        var reasons = new TreeMap<String, Integer>();
        FALLBACKS.forEach((k, v) -> reasons.put(k, v.get()));
        System.out.println("[TC] kernels compiled by TC: " + COMPILED.get() + ", fell back to Graal: " + reasons.values().stream().mapToInt(Integer::intValue).sum() + " " + reasons);
    }
}
