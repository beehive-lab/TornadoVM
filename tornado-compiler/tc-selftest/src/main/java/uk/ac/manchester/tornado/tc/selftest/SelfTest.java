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
package uk.ac.manchester.tornado.tc.selftest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.common.tc.TcTargetDevice;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * {@code tornado-selftest}: checks that TornadoVM's kernel compiler (TC) produces correct code on the
 * devices of this machine, and reports what TC can use on each of them.
 *
 * <p>For every device it prints the capabilities TC compiles for (backend, compute capability, NVRTC
 * version, fp64, fp16, warp width, local memory, tensor cores, device-side launches), then runs a
 * set of small kernels (see {@link Checks}), each compared with the same Java method run on the JVM:
 * </p>
 * <ul>
 * <li>{@code backend}: kernels in TC's IR, compiled for the device and run through the prebuilt-task
 * path, so the bytecode frontend is not involved;</li>
 * <li>{@code core}: {@code @Parallel} loops, control flow, local memory, {@code @Reduce}, atomics;</li>
 * <li>{@code types}: long, double, half;</li>
 * <li>{@code java}: where Java and C differ (integer division and shifts, float-to-int conversion,
 * Math.min/max, zeroed {@code new} arrays);</li>
 * <li>{@code strict-fp}: a plan with {@code withStrictFloatingPoint()};</li>
 * <li>{@code device} and {@code tensor-cores}: warp operations, device-side launches, F16 and FP8
 * tensor cores, where the device has them.</li>
 * </ul>
 * <p>
 * Each check reports PASS, FAIL (with the first mismatch), SKIP (the device lacks what the check needs)
 * or UNSUPPORTED (the runtime refused the kernel for this device). The exit status is 1 when a check
 * fails. {@code --json FILE} writes the same report as JSON, to attach to a bug report.
 * </p>
 */
public final class SelfTest {

    enum Status {
        PASS, FAIL, SKIP, UNSUPPORTED
    }

    record Result(Check check, Status status, String detail, double millis) {
    }

    record DeviceReport(String id, String name, TargetCaps caps, List<Result> results) {
    }

    private static final String USAGE = """
            usage: tornado-selftest [options]
              -d, --device B:D    check device D of backend B (as listed by tornado --devices); repeatable.
                                  Default: every device.
              -g, --group G       run only the checks of group G (backend, core, types, java, strict-fp,
                                  device, tensor-cores); repeatable, or a comma-separated list
              --list              list the checks and exit
              --json FILE         also write the report as JSON
              -v, --verbose       print the full exception of a failing check
              -h, --help          this text
            Exit status: 0 when no check fails, 1 when one does, 2 on a usage error.""";

    private SelfTest() {
    }

    public static void main(String[] args) throws IOException {
        var devices = new ArrayList<String>();
        var groups = new ArrayList<String>();
        Path json = null;
        boolean verbose = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d", "--device" -> devices.add(value(args, ++i));
                case "-g", "--group" -> groups.addAll(List.of(value(args, ++i).split(",")));
                case "--json" -> json = Path.of(value(args, ++i));
                case "-v", "--verbose" -> verbose = true;
                case "--list" -> {
                    Checks.all().forEach(c -> System.out.printf("%-28s %s%s%n", c.id(), c.description(), c.requirement().isEmpty() ? "" : " [needs " + c.requirement() + "]"));
                    return;
                }
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    return;
                }
                default -> usage("unknown option " + args[i]);
            }
        }
        var checks = Checks.all().stream().filter(c -> groups.isEmpty() || groups.contains(c.group())).toList();
        if (checks.isEmpty()) {
            usage("no check in group(s) " + groups);
        }
        var reports = new ArrayList<DeviceReport>();
        var runtime = TornadoRuntimeProvider.getTornadoRuntime();
        for (int b = 0; b < runtime.getNumBackends(); b++) {
            var backend = runtime.getBackend(b);
            for (int d = 0; d < backend.getNumDevices(); d++) {
                var id = b + ":" + d;
                if (devices.isEmpty() || devices.contains(id)) {
                    reports.add(check(id, backend.getDevice(d), checks, verbose));
                }
            }
        }
        if (reports.isEmpty()) {
            usage(devices.isEmpty() ? "TornadoVM found no device" : "no device " + devices + " (see tornado --devices)");
        }
        long failed = reports.stream().flatMap(r -> r.results().stream()).filter(r -> r.status() == Status.FAIL).count();
        System.out.println();
        System.out.println(failed == 0 ? "tornado-selftest: all checks passed on " + reports.size() + " device(s)" : "tornado-selftest: " + failed + " check(s) FAILED");
        if (json != null) {
            Files.writeString(json, Json.of(reports));
            System.out.println("report written to " + json.toAbsolutePath());
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    private static DeviceReport check(String id, TornadoDevice device, List<Check> checks, boolean verbose) {
        var name = device.getPhysicalDevice().getDeviceName();
        System.out.println();
        System.out.println("Device " + id + ": " + name + " (" + device.getTornadoVMBackend() + ")");
        if (!(device instanceof TcTargetDevice target)) {
            System.out.println("  not a device TC compiles for: skipped");
            return new DeviceReport(id, name, null, List.of());
        }
        var caps = target.tcTarget();
        System.out.println("  " + describe(caps));
        var results = new ArrayList<Result>();
        for (var check : checks) {
            var result = run(check, device, caps, verbose);
            results.add(result);
            System.out.printf(Locale.ROOT, "  %-11s %-28s %8s  %s%n", result.status(), check.id(), result.status() == Status.SKIP ? "" : String.format(Locale.ROOT, "%.0f ms", result.millis()),
                    result.detail());
        }
        var counts = new java.util.EnumMap<Status, Long>(Status.class);
        results.forEach(r -> counts.merge(r.status(), 1L, Long::sum));
        System.out.println("  " + counts.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().name().toLowerCase(Locale.ROOT)).reduce((x, y) -> x + ", " + y).orElse(""));
        return new DeviceReport(id, name, caps, results);
    }

    private static Result run(Check check, TornadoDevice device, TargetCaps caps, boolean verbose) {
        if (!check.needs().test(caps)) {
            return new Result(check, Status.SKIP, "device has no " + check.requirement(), 0);
        }
        long start = System.nanoTime();
        try {
            var detail = check.body().run(device, caps);
            return new Result(check, Status.PASS, detail, (System.nanoTime() - start) / 1e6);
        } catch (AssertionError e) {
            return new Result(check, Status.FAIL, e.getMessage() + " (" + check.description() + ")", (System.nanoTime() - start) / 1e6);
        } catch (Throwable e) {
            double millis = (System.nanoTime() - start) / 1e6;
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getClass().getSimpleName().matches("TornadoDevice\\w+NotSupported")) {
                    return new Result(check, Status.UNSUPPORTED, firstLine(t.getMessage()), millis);
                }
            }
            if (verbose) {
                e.printStackTrace(System.out);
            }
            var root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return new Result(check, Status.FAIL, root.getClass().getSimpleName() + ": " + firstLine(root.getMessage()), millis);
        }
    }

    /** The capabilities TC compiles for, in one line. */
    static String describe(TargetCaps caps) {
        var sb = new StringBuilder("TC target: ").append(caps.dialect());
        if (caps.dialect() == TargetCaps.Dialect.CUDA) {
            sb.append(" sm_").append(caps.computeCapability());
            if (caps.nvrtcVersion() > 0) {
                sb.append(", NVRTC ").append(caps.nvrtcVersion() / 1000).append('.').append(caps.nvrtcVersion() % 1000);
            }
        }
        sb.append(", fp64 ").append(yes(caps.fp64())).append(", fp16 ").append(yes(caps.fp16()));
        sb.append(", warp ").append(caps.warpSize() == 0 ? "unknown" : caps.warpSize());
        sb.append(", local memory ").append(caps.localMemoryBytes() / 1024).append(" KB, work-group ").append(caps.maxWorkGroupSize());
        sb.append(", tensor cores ").append(caps.fp8Mma() ? "F16+FP8" : caps.mma() ? "F16" : "no");
        sb.append(", device launch ").append(yes(caps.deviceLaunch()));
        return sb.toString();
    }

    private static String yes(boolean b) {
        return b ? "yes" : "no";
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            usage(args[i - 1] + " needs a value");
        }
        return args[i];
    }

    private static void usage(String problem) {
        System.err.println("tornado-selftest: " + problem);
        System.err.println(USAGE);
        System.exit(2);
    }
}
