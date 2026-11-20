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
package uk.ac.manchester.tornado.tc.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import uk.ac.manchester.tornado.tc.check.ExternalTool;

/**
 * Finding a CUDA toolkit that can build for the GPU that is present, for the tests that check TC's
 * output by running it.
 *
 * <p>
 * This is shared rather than copied because the copies drifted: one test asked {@code nvidia-smi}
 * for the card and built for it, another was pinned to {@code sm_89}, and the pinned one skipped
 * itself when the binary would not load -- so on a machine with any other card it reported success
 * without having run anything. The rules are the same for every such test, and belong in one place.
 * </p>
 */
final class CudaProbe {

    private CudaProbe() {
    }

    /** What a built program did: its exit code and everything it wrote. */
    record Ran(int exit, String output) {
    }

    /** What to build for: a compute capability nvcc accepts, and the {@code -arch} that names it. */
    record Arch(int cc, String flag) {
    }

    /**
     * Runs {@code command} with its output going to a file, so the timeout holds: reading the output
     * from a pipe blocks until the process exits, and a hung nvcc would then hang the test rather
     * than fail it.
     *
     * @param dir
     *         where to put the log, or {@code null} for the system temporary directory.
     * @param libraryPath
     *         the {@code LD_LIBRARY_PATH} to run with, or {@code null} to inherit it.
     */
    static Ran run(Path dir, List<String> command, String libraryPath) throws IOException, InterruptedException {
        var log = dir == null ? Files.createTempFile("tc-run", ".log") : Files.createTempFile(dir, "run", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        if (libraryPath != null) {
            builder.environment().put("LD_LIBRARY_PATH", libraryPath);
        }
        var process = builder.start();
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
        var output = Files.readString(log, StandardCharsets.UTF_8);
        return new Ran(finished ? process.exitValue() : 124, output);
    }

    static Ran run(Path dir, List<String> command) throws IOException, InterruptedException {
        return run(dir, command, null);
    }

    /**
     * The {@code nvcc} to build with: the toolkit the native build uses ({@code CUDA_PATH} or
     * {@code CUDA_HOME}, where CMake looks first) ahead of whatever happens to come first on PATH. A
     * machine with two toolkits installed would otherwise check the generated CUDA with a compiler the
     * rest of the build never calls, and an older one at that -- old enough, on a CI runner, not to
     * know the name of the card it is running on, nor to carry the CCCL headers null-aware output
     * includes.
     */
    static Optional<Path> nvcc() {
        for (var variable : List.of("CUDA_PATH", "CUDA_HOME")) {
            var root = System.getenv(variable);
            if (root != null && !root.isBlank()) {
                var candidate = Path.of(root, "bin", "nvcc");
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return ExternalTool.find("nvcc", "/usr/local/cuda/bin");
    }

    /**
     * The compute capability of GPU 0 ({@code major * 10 + minor}), from {@code nvidia-smi}; empty when
     * there is no GPU to run on.
     */
    static Optional<Integer> computeCapability(Path dir) throws IOException, InterruptedException {
        var smi = ExternalTool.find("nvidia-smi", "/usr/bin");
        if (smi.isEmpty()) {
            return Optional.empty();
        }
        var query = run(dir, List.of(smi.get().toString(), "--query-gpu=compute_cap", "--format=csv,noheader"));
        var first = query.output().strip().lines().findFirst().orElse("");
        if (query.exit() != 0 || !first.matches("\\d+\\.\\d+")) {
            return Optional.empty();
        }
        var parts = first.split("\\.");
        return Optional.of(Integer.parseInt(parts[0]) * 10 + Integer.parseInt(parts[1]));
    }

    /**
     * The compute capabilities this {@code nvcc} can generate for, ascending, from
     * {@code --list-gpu-arch}; empty when it does not answer, which is an nvcc too old for the flag.
     */
    private static List<Integer> architectures(Path dir, Path nvcc) throws IOException, InterruptedException {
        var listed = run(dir, List.of(nvcc.toString(), "--list-gpu-arch"));
        if (listed.exit() != 0) {
            return List.of();
        }
        return listed.output().lines().map(String::strip).filter(line -> line.matches("compute_\\d+")).map(line -> Integer.parseInt(line.substring("compute_".length()))).sorted().toList();
    }

    /**
     * What to try building for, best first, given the GPU present and the architectures nvcc knows:
     *
     * <ul>
     * <li>{@code sm_<cc>}, a cubin for exactly this GPU, when nvcc knows the architecture. An nvcc
     * older than the card does not, and rejects the flag outright;
     * <li>{@code compute_<x>} for the newest architecture nvcc does know that this GPU can run, which
     * leaves PTX for the driver to compile on load. Falling back to a lower {@code sm_} instead would
     * build a cubin for another card; across a major version the GPU cannot load it at all, and within
     * one it silently runs code built for the wrong chip.
     * </ul>
     */
    private static List<Arch> architecturesToTry(int deviceCc, List<Integer> known) {
        var out = new ArrayList<Arch>();
        if (known.isEmpty() || known.contains(deviceCc)) {
            out.add(new Arch(deviceCc, "sm_" + deviceCc));
        }
        known.stream().filter(a -> a <= deviceCc).max(Integer::compare).ifPresent(a -> out.add(new Arch(a, "compute_" + a)));
        return out;
    }

    /**
     * Whether a program built for {@code arch}, over {@code preamble}, reaches this GPU -- established
     * by building a one-line kernel and reading back what it wrote. Nothing about a toolkit and a
     * driver guarantees it: nvcc accepts an architecture it can generate for whether or not the GPU can
     * load the result, a driver older than the toolkit refuses its PTX, and a partial toolkit is
     * missing the headers the output includes. Finding that out on a kernel whose answer is known keeps
     * it from being discovered as a wrong answer from a kernel whose answer is the thing under test.
     */
    private static boolean runs(Path dir, Path nvcc, Arch arch, String preamble) throws IOException, InterruptedException {
        var file = dir.resolve("probe-" + arch.flag() + ".cu");
        Files.writeString(file, preamble + """
                #include <cstdio>
                __global__ void probe(int *out) { *out = 1; }
                int main() {
                  int *d, h = 0;
                  if (cudaMalloc(&d, sizeof(int)) != cudaSuccess) return 3;
                  if (cudaMemcpy(d, &h, sizeof(int), cudaMemcpyHostToDevice) != cudaSuccess) return 3;
                  probe<<<1, 1>>>(d);
                  if (cudaGetLastError() != cudaSuccess || cudaDeviceSynchronize() != cudaSuccess) return 3;
                  if (cudaMemcpy(&h, d, sizeof(int), cudaMemcpyDeviceToHost) != cudaSuccess) return 3;
                  std::printf("%d\\n", h);
                  return h == 1 ? 0 : 3;
                }
                """);
        var exe = dir.resolve("probe-" + arch.flag());
        var build = run(dir, List.of(nvcc.toString(), "-std=c++17", "-arch=" + arch.flag(), "-o", exe.toString(), file.toString()));
        return build.exit() == 0 && run(dir, List.of(exe.toString())).exit() == 0;
    }

    /**
     * A toolkit and an architecture that between them build and run CUDA here, or as much of the answer
     * as there is. {@link #usable()} is the question the tests ask; {@link #why()} is what they say when
     * it is false.
     */
    record Toolkit(Optional<Path> nvcc, Optional<Integer> cc, List<String> tried, Optional<Arch> arch) {

        boolean usable() {
            return arch.isPresent();
        }

        /** The nvcc, the GPU and the architectures tried: enough to tell a missing GPU from a stale toolkit. */
        String why() {
            return "nvcc " + nvcc.map(Path::toString).orElse("(none)") + ", GPU " + cc.map(c -> "sm_" + c).orElse("(none)") + ", tried " + tried;
        }

        Path nvccPath() {
            return nvcc.orElseThrow();
        }

        Arch architecture() {
            return arch.orElseThrow();
        }
    }

    /**
     * Looks for a toolkit that builds and runs CUDA with {@code preamble} ahead of it on this machine.
     * {@code preamble} is what the generated code includes, so that a toolkit missing those headers is
     * found here rather than as a failure in every method under test.
     */
    static Toolkit find(Path dir, String preamble) throws IOException, InterruptedException {
        var nvcc = nvcc();
        var cc = computeCapability(dir);
        var tried = new ArrayList<String>();
        Optional<Arch> arch = Optional.empty();
        if (nvcc.isPresent() && cc.isPresent()) {
            for (var candidate : architecturesToTry(cc.get(), architectures(dir, nvcc.get()))) {
                tried.add(candidate.flag());
                if (runs(dir, nvcc.get(), candidate, preamble)) {
                    arch = Optional.of(candidate);
                    break;
                }
            }
        }
        return new Toolkit(nvcc, cc, List.copyOf(tried), arch);
    }
}
