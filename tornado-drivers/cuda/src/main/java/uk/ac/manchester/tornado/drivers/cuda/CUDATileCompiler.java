/*
 * Copyright (c) 2018, 2020-2022, 2024, 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.cuda;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceTileNotSupported;

/**
 * Compiles a generated CUDA Tile C++ kernel to a cubin by driving nvcc.
 *
 * <p>
 * This is deliberately the first of the two compile routes rather than the better one. NVRTC
 * gained tile support in CUDA 13.3 ({@code --enable-tile}, with the tile code extracted through
 * {@code nvrtcGetTileIR} because the cubin does not carry it), which would keep compilation in
 * process. That route additionally needs driver R610 or newer, so this subprocess route exists
 * to make the tile path usable first: it needs no new native bindings, and it is what NVIDIA's
 * own TileGym does.
 * </p>
 *
 * <p>
 * The cost is a process spawn per distinct kernel, shape and architecture. It is paid once,
 * because the resulting cubin goes into the same on-disk module cache the NVRTC path uses.
 * </p>
 */
public final class CUDATileCompiler {

    /** Minimum toolkit with the CUDA Tile C++ surface, encoded as major * 1000 + minor. */
    private static final int MINIMUM_TOOLKIT = 13003;

    private static final Pattern RELEASE_PATTERN = Pattern.compile("release\\s+(\\d+)\\.(\\d+)");

    private static final long COMPILE_TIMEOUT_SECONDS = 120;

    private static String resolvedNvcc;
    private static String resolvedIdentity;

    private CUDATileCompiler() {
    }

    /**
     * @param source
     *     generated device source
     * @return true when this source is a CUDA Tile kernel and must go through nvcc rather than
     *     NVRTC. Keyed off the entry point qualifier, which only the tile prologue emits.
     */
    public static boolean isTileSource(byte[] source) {
        return new String(source, StandardCharsets.UTF_8).contains("__tile_global__");
    }

    /**
     * Identity of the tile toolchain, for the module cache key. Without it two different nvcc
     * installations would produce different cubins that hash to the same cache entry, because
     * the existing identity records the NVRTC version and the tile path does not use NVRTC.
     *
     * @return a stable string describing the nvcc in use, or "unavailable"
     */
    public static synchronized String toolchainIdentity() {
        if (resolvedIdentity == null) {
            try {
                String nvcc = locateNvcc();
                resolvedIdentity = nvcc + "|" + versionOf(nvcc);
            } catch (TornadoDeviceTileNotSupported e) {
                resolvedIdentity = "unavailable";
            }
        }
        return resolvedIdentity;
    }

    /**
     * Version of the toolkit that would compile a tile kernel, as major * 1000 + minor, or 0
     * when no usable nvcc was found.
     *
     * <p>
     * This is deliberately the nvcc version and not {@code CUDAProgram.getNvrtcVersion()}. The
     * tile path compiles with nvcc, so gating it on NVRTC asks the wrong question: a system
     * CUDA 12.6 with a userspace 13.3 nvcc can compile tile kernels perfectly well, and gating
     * on NVRTC would reject it.
     * </p>
     *
     * @return the toolkit version, or 0
     */
    public static int toolkitVersion() {
        try {
            return versionOf(locateNvcc());
        } catch (TornadoDeviceTileNotSupported e) {
            return 0;
        }
    }

    /**
     * Compiles tile source to a cubin.
     *
     * @param source
     *     generated CUDA Tile C++
     * @param computeMajor
     *     device compute capability, major
     * @param computeMinor
     *     device compute capability, minor
     * @param extraFlags
     *     per-task compiler flags, or null
     * @return the cubin image, ready for cuModuleLoadDataEx
     */
    public static byte[] compile(byte[] source, int computeMajor, int computeMinor, String extraFlags) {
        String nvcc = locateNvcc();
        requireTileCapableToolkit(nvcc);

        Path workingDirectory = null;
        try {
            workingDirectory = Files.createTempDirectory("tornado-cutile-");
            Path input = workingDirectory.resolve("tornado_tile_kernel.cu");
            Path output = workingDirectory.resolve("tornado_tile_kernel.cubin");
            Files.write(input, source);

            List<String> command = new ArrayList<>();
            command.add(nvcc);
            // -tilecubin emits a cubin containing the tile code; --tile-only skips SIMT
            // code generation, which the generated translation unit has none of anyway.
            command.add("-tilecubin");
            command.add("--tile-only");
            command.add("-std=c++20");
            command.add("-arch=sm_" + computeMajor + computeMinor);
            if (extraFlags != null && !extraFlags.isBlank()) {
                for (String flag : extraFlags.trim().split("\\s+")) {
                    command.add(flag);
                }
            }
            command.add("-o");
            command.add(output.toString());
            command.add(input.toString());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String log = readFully(process.getInputStream());
            boolean finished = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TornadoBailoutRuntimeException("[ERROR] The CUDA Tile compiler did not finish within "
                        + COMPILE_TIMEOUT_SECONDS + "s. Command: " + String.join(" ", command));
            }
            if (process.exitValue() != 0 || !Files.exists(output)) {
                throw new TornadoBailoutRuntimeException("[ERROR] The CUDA Tile compiler failed.\nCommand: "
                        + String.join(" ", command) + "\n\n" + log);
            }
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new TornadoBailoutRuntimeException("[ERROR] Unable to run the CUDA Tile compiler: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TornadoBailoutRuntimeException("[ERROR] Interrupted while compiling a CUDA Tile kernel.");
        } finally {
            deleteQuietly(workingDirectory);
        }
    }

    /**
     * Finds an nvcc that can compile tile code. The pip wheel location is searched on purpose:
     * CUDA Tile needs toolkit 13.3, and installing that as a wheel into the user's home is the
     * route that needs no root, so a system CUDA older than 13.3 does not have to block the
     * tile path.
     */
    static synchronized String locateNvcc() {
        if (resolvedNvcc != null) {
            return resolvedNvcc;
        }
        List<String> candidates = new ArrayList<>();
        String configured = System.getProperty("tornado.cuda.nvcc");
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
        }
        String cudaPath = System.getenv("CUDA_PATH");
        if (cudaPath != null && !cudaPath.isBlank()) {
            candidates.add(cudaPath + "/bin/nvcc");
        }
        String home = System.getProperty("user.home");
        if (home != null) {
            Path siteRoot = Paths.get(home, ".local", "lib");
            if (Files.isDirectory(siteRoot)) {
                try (var pythonDirectories = Files.list(siteRoot)) {
                    pythonDirectories.filter(Files::isDirectory) //
                            .map(directory -> directory.resolve("site-packages/nvidia/cu13/bin/nvcc")) //
                            .filter(Files::isExecutable) //
                            .map(Path::toString) //
                            .forEach(candidates::add);
                } catch (IOException ignored) {
                    // A home directory that cannot be listed simply contributes no candidate.
                }
            }
        }
        candidates.add("/usr/local/cuda/bin/nvcc");
        candidates.add("nvcc");

        for (String candidate : candidates) {
            if (candidate.indexOf('/') < 0 || Files.isExecutable(Paths.get(candidate))) {
                if (versionOf(candidate) > 0) {
                    resolvedNvcc = candidate;
                    return resolvedNvcc;
                }
            }
        }
        throw new TornadoDeviceTileNotSupported("No nvcc was found to compile a CUDA Tile kernel. CUDA Tile C++ needs "
                + "CUDA Toolkit 13.3 or newer. Install it, or point -Dtornado.cuda.nvcc at one; a userspace install "
                + "with 'pip install --user cuda-tile[tileiras]' is enough and needs no root. Searched: " + candidates);
    }

    private static void requireTileCapableToolkit(String nvcc) {
        int version = versionOf(nvcc);
        if (version < MINIMUM_TOOLKIT) {
            throw new TornadoDeviceTileNotSupported("CUDA Tile C++ requires CUDA Toolkit 13.3 or newer, but " + nvcc
                    + " reports " + (version / 1000) + "." + (version % 1000)
                    + ". Point -Dtornado.cuda.nvcc at a newer nvcc.");
        }
    }

    /**
     * @return the toolkit version as major * 1000 + minor, or 0 when nvcc cannot be run
     */
    private static int versionOf(String nvcc) {
        try {
            ProcessBuilder builder = new ProcessBuilder(nvcc, "--version");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = readFully(process.getInputStream());
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return 0;
            }
            Matcher matcher = RELEASE_PATTERN.matcher(output);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1)) * 1000 + Integer.parseInt(matcher.group(2));
            }
            return 0;
        } catch (IOException e) {
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var entries = Files.walk(directory)) {
            entries.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Leaving a temporary file behind is not worth failing a compilation over.
                }
            });
        } catch (IOException ignored) {
            // Same.
        }
    }
}
