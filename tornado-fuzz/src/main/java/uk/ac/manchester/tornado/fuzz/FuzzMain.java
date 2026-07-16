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
package uk.ac.manchester.tornado.fuzz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.fuzz.kernels.AtomicKernel;
import uk.ac.manchester.tornado.fuzz.kernels.ElementwiseArithKernel;
import uk.ac.manchester.tornado.fuzz.kernels.Kernels;
import uk.ac.manchester.tornado.fuzz.kernels.KernelTemplate;
import uk.ac.manchester.tornado.fuzz.kernels.LocalMemReduceKernel;
import uk.ac.manchester.tornado.fuzz.kernels.MathIntrinsicKernel;
import uk.ac.manchester.tornado.fuzz.gen.InProcessCompiler;
import uk.ac.manchester.tornado.fuzz.gen.Phase2Fuzzer;
import uk.ac.manchester.tornado.fuzz.report.Bucketer;
import uk.ac.manchester.tornado.fuzz.report.BundleWriter;
import uk.ac.manchester.tornado.fuzz.report.FindingRecorder;

/**
 * Fuzzer entry point. Launched under the tornado launcher:
 *
 * <pre>
 * tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \
 *     --params "seed=1 count=200 phase=1 outDir=/tmp/fuzz"
 * </pre>
 *
 * Runs a deterministic sweep of KernelContext kernels on the CUDA backend and
 * checks each against the JVM golden reference. Prints {@code CASE seed=<s>}
 * before every case so the Python driver can attribute a native crash to the
 * in-flight seed.
 */
public final class FuzzMain {

    private static final int[] SIZES = { 256, 1024, 4096, 4097, 512, 2048 };

    private FuzzMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        long startSeed = Long.parseLong(opts.getOrDefault("seed", "1"));
        int count = Integer.parseInt(opts.getOrDefault("count", "100"));
        int phase = Integer.parseInt(opts.getOrDefault("phase", "1"));
        Path outDir = Path.of(opts.getOrDefault("outDir", "/tmp/fuzz"));

        TornadoDevice device = DeviceSelector.cudaDevice();
        System.out.println("CUDA device: " + device.getDeviceName());

        // Optional template filter (phase 1), e.g. only=localmem-reduce,atomic-add — used to
        // target race/shared-memory-prone kernels for the compute-sanitizer pass.
        KernelTemplate[] allTemplates = { new ElementwiseArithKernel(), new MathIntrinsicKernel(), new LocalMemReduceKernel(), new AtomicKernel() };
        KernelTemplate[] templates = filterTemplates(allTemplates, opts.get("only"));
        DataGen.Profile[] profiles = DataGen.Profile.values();

        Phase2Fuzzer phase2 = null;
        if (phase == 2) {
            InProcessCompiler compiler = new InProcessCompiler();
            if (!compiler.usesSystemLoader()) {
                System.out.println("WARNING: phase 2 needs -Dtornado.fuzz.genDir=<dir> AND that <dir> on the launch -cp,");
                System.out.println("         else generated classes are invisible to TornadoVM's class reader and every case bails.");
                System.out.println("         Example: tornado -cp /tmp/gen -m tornado.fuzz/...FuzzMain \\");
                System.out.println("                    --jvm=\"-Dtornado.fuzz.genDir=/tmp/gen\" --params \"phase=2 ...\"");
            }
            phase2 = new Phase2Fuzzer(compiler);
        }

        FindingRecorder recorder = new FindingRecorder(outDir);
        BundleWriter bundles = new BundleWriter(outDir);
        Map<String, Integer> buckets = new LinkedHashMap<>();

        int findings = 0;
        int passes = 0;
        for (long i = 0; i < count; i++) {
            long seed = startSeed + i;

            RandomGen rng = new RandomGen(seed);
            FuzzConfig cfg = new FuzzConfig(seed, phase);
            cfg.dataProfile = profiles[rng.nextInt(profiles.length)];

            CaseResult result;
            if (phase == 2) {
                // Kernel class is G<seed>, so a compute-sanitizer error naming G<seed> maps back to this seed.
                cfg.templateId = "gen-expr";
                System.out.println("CASE seed=" + seed + " template=gen-expr");
                System.out.flush();
                result = phase2.run(cfg, rng, device);
            } else {
                KernelTemplate template = templates[rng.nextInt(templates.length)];
                cfg.templateId = template.id();
                cfg.size = SIZES[rng.nextInt(SIZES.length)];
                cfg.localSize = Kernels.chooseLocalWork(cfg.size, 256);
                System.out.println("CASE seed=" + seed + " template=" + cfg.templateId);
                System.out.flush();
                result = template.run(cfg, rng, device);
            }

            switch (result.status) {
                case PASS -> passes++;
                case MISMATCH, EXCEPTION -> {
                    findings++;
                    recorder.record(cfg, result);
                    String bucket = Bucketer.key(cfg, result);
                    int seen = buckets.merge(bucket, 1, Integer::sum);
                    if (seen == 1) {
                        // First of its kind: write the full debug bundle.
                        Path dir = bundles.write(cfg, result);
                        System.out.println("FINDING " + result.status + " seed=" + seed + " bucket=[" + bucket + "] -> " + dir);
                    } else {
                        System.out.println("FINDING " + result.status + " seed=" + seed + " bucket=[" + bucket + "] (dup #" + seen + ", bundle skipped)");
                    }
                }
            }
        }

        System.out.println("Done. cases=" + count + " pass=" + passes + " findings=" + findings + " buckets=" + buckets.size() + " outDir=" + outDir);
        writeBucketSummary(outDir, buckets);
        buckets.forEach((k, n) -> System.out.println("  bucket " + n + "x  " + k));
    }

    /** Restrict the active phase-1 templates to a comma-separated list of ids, or all when null. */
    private static KernelTemplate[] filterTemplates(KernelTemplate[] all, String only) {
        if (only == null || only.isBlank()) {
            return all;
        }
        java.util.Set<String> wanted = new java.util.HashSet<>(java.util.Arrays.asList(only.split(",")));
        java.util.List<KernelTemplate> kept = new java.util.ArrayList<>();
        for (KernelTemplate t : all) {
            if (wanted.contains(t.id())) {
                kept.add(t);
            }
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("only=" + only + " matched no templates; known: elementwise-arith, math-intrinsic, localmem-reduce, atomic-add");
        }
        return kept.toArray(new KernelTemplate[0]);
    }

    private static void writeBucketSummary(Path outDir, Map<String, Integer> buckets) throws Exception {
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<String, Integer> e : buckets.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("  \"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\": ").append(e.getValue());
        }
        sb.append("\n}\n");
        Files.writeString(outDir.resolve("buckets.json"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        StringBuilder joined = new StringBuilder();
        for (String a : args) {
            joined.append(a).append(' ');
        }
        for (String token : joined.toString().trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq > 0) {
                opts.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }
        return opts;
    }
}
