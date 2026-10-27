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
package uk.ac.manchester.tornado.drivers.common.tc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.analysis.KernelFeatures;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.AtomicSlots;
import uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact;

public class TcDiskCacheTest {

    @TempDir
    Path dir;

    /** Class files and static finals a compilation reads, changeable between "runs". */
    private final Map<String, byte[]> classes = new HashMap<>();
    private final Map<String, Number> statics = new HashMap<>();

    private final Frontend.BytecodeSource live = new Frontend.BytecodeSource() {
        @Override
        public Optional<byte[]> classBytes(String internalName) {
            return Optional.ofNullable(classes.get(internalName));
        }

        @Override
        public Optional<Number> staticFinal(String owner, String field) {
            return Optional.ofNullable(statics.get(owner + "." + field));
        }
    };

    @BeforeEach
    public void setUp() {
        TcDiskCache.useForTests(dir, "test-build");
        classes.put("pkg/Kernels", "kernel v1".getBytes(StandardCharsets.UTF_8));
        classes.put("pkg/Helper", "helper v1".getBytes(StandardCharsets.UTF_8));
        statics.put("pkg/Consts.SCALE", 2.5f);
    }

    private static String key(List<Fact> facts) {
        return TcDiskCache.key("pkg.Kernels", "add", "(LA;)V", TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, facts).orElseThrow();
    }

    private static TcHook.Compiled compiled() {
        return new TcHook.Compiled("extern \"C\" __global__ void Kernels_add() {}\n", "Kernels_add", 7, List.of(new ParallelLoopLowering.Domain(0, 0, 1, 1024)),
                new AtomicSlots.Info(List.of(3, 5), Map.of(1, 0)), new KernelFeatures(Map.of(KernelFeatures.Feature.values()[0], 4)), "t0", false);
    }

    /** Stores an entry whose compilation read the kernel, its helper and a constant. */
    private String storeEntry() {
        var recording = new TcDiskCache.Recording(live);
        recording.classBytes("pkg/Kernels");
        recording.classBytes("pkg/Helper");
        recording.staticFinal("pkg/Consts", "SCALE");
        String key = key(List.of(new Fact.Length(1024)));
        TcDiskCache.store(key, recording.reads(), compiled());
        return key;
    }

    @Test
    public void keyIgnoresMapOrderAndTracksFacts() {
        var ab = new LinkedHashMap<String, Number>();
        ab.put("a", 1);
        ab.put("b", 2);
        var ba = new LinkedHashMap<String, Number>();
        ba.put("b", 2);
        ba.put("a", 1);
        assertEquals(key(List.of(new Fact.Instance(ab, List.of()))), key(List.of(new Fact.Instance(ba, List.of()))));
        assertNotEquals(key(List.of(new Fact.Length(1024))), key(List.of(new Fact.Length(2048))));
        assertNotEquals(key(List.of(new Fact.Scalar(1))), key(List.of(new Fact.Scalar(1L))), "an int and a long specialise differently");
    }

    @Test
    public void dumpsAndReportsBypassTheCache() {
        var dumping = new TcOptions(Set.of("all"), Path.of("tc-dumps"), true, Optional.empty(), false);
        assertTrue(TcDiskCache.key("pkg.Kernels", "add", "(LA;)V", TargetCaps.CUDA_SM89, dumping, List.of()).isEmpty());
    }

    @Test
    public void anEntryRoundTrips() {
        String key = storeEntry();
        var loaded = TcDiskCache.load(key, live).orElseThrow();
        var stored = compiled();
        assertEquals(stored.source(), loaded.source());
        assertEquals(stored.entryName(), loaded.entryName());
        assertEquals(stored.domain(), loaded.domain());
        assertEquals(stored.atomics(), loaded.atomics());
        assertEquals(stored.features(), loaded.features());
    }

    @Test
    public void aChangedClassOrConstantInvalidatesTheEntry() {
        String key = storeEntry();
        classes.put("pkg/Helper", "helper v2".getBytes(StandardCharsets.UTF_8));
        assertTrue(TcDiskCache.load(key, live).isEmpty(), "a callee changed");
        classes.put("pkg/Helper", "helper v1".getBytes(StandardCharsets.UTF_8));
        assertTrue(TcDiskCache.load(key, live).isPresent());
        statics.put("pkg/Consts.SCALE", 3.5f);
        assertTrue(TcDiskCache.load(key, live).isEmpty(), "a static final changed");
        statics.put("pkg/Consts.SCALE", 2.5f);
        classes.remove("pkg/Kernels");
        assertTrue(TcDiskCache.load(key, live).isEmpty(), "the kernel class is gone");
    }

    /** Per-method analyses (access, loop index) are kept like compilations, and invalidated the same way. */
    @Test
    public void anAnalysisRoundTripsUntilItsClassChanges() {
        String access = TcDiskCache.analysisKey("access", "pkg.Kernels", "add", "(LA;)V").orElseThrow();
        String loopIndex = TcDiskCache.analysisKey("loop-index", "pkg.Kernels", "add", "(LA;)V").orElseThrow();
        assertNotEquals(access, loopIndex, "each analysis has its own entry");
        var recording = new TcDiskCache.Recording(live);
        recording.classBytes("pkg/Kernels");
        TcDiskCache.storeAnalysis(access, recording.reads(), new int[] { 1, 2, 3 });
        assertArrayEquals(new int[] { 1, 2, 3 }, TcDiskCache.loadAnalysis(access, live).orElseThrow());
        assertTrue(TcDiskCache.loadAnalysis(loopIndex, live).isEmpty(), "not stored");
        classes.put("pkg/Kernels", "kernel v2".getBytes(StandardCharsets.UTF_8));
        assertTrue(TcDiskCache.loadAnalysis(access, live).isEmpty(), "the kernel changed");
    }

    @Test
    public void kernelsNamingTheirTaskAreNotStored() {
        var c = compiled();
        var deviceLaunch = new TcHook.Compiled(c.source(), c.entryName(), 0, c.domain(), c.atomics(), c.features(), "t0", true);
        String key = key(List.of(new Fact.Length(7)));
        TcDiskCache.store(key, Map.of(), deviceLaunch);
        assertTrue(TcDiskCache.load(key, live).isEmpty());
    }
}
