# JDK matrix (2026-10-11)

Each JDK on the RTX 4090 host. The `jdk21` SDK was built on JDK 21; the `jdk22plus` SDK was built on
JDK 25 and again on JDK 27-ea (both builds succeed).

| JDK | SDK | Devices | TC on CUDA | TC on OpenCL | Graal on CUDA | Kernel at the JDK's own class-file version, through the argfile |
|---|---|---:|---|---|---|---|
| 21.0.2 | jdk21 | 3 | 127 pass, 0 fail | 122 pass, 0 fail | 108 pass, 1 fail | v65: TC and Graal correct |
| 25.0.2 | jdk22plus | 3 | 127 / 0 | 122 / 0 | 108 / 1 | v69: TC and Graal correct |
| 26.0.1 | jdk22plus | 3 | 127 / 0 | 122 / 0 | 108 / 1 | v70: TC and Graal correct |
| 27-ea+24 | jdk22plus | 3 | 127 / 0 | 122 / 0 | 108 / 1 | v71: TC and Graal correct |

Test classes run, with fallback off:
- `arrays.TestArrays`, `reductions.TestReductionsFloats` and `kernelcontext.reductions.TestReductionsFloatsKernelContext`;
- `vectortypes.TestFloats` and `vectortypes.TestHalfFloats`;
- `math.TestMath`.

The Graal failure is `testComputePi`, a known baseline failure. TC passes more tests than Graal
because of `TestHalfFloats`.

JDK 22-24 were not tested; they are not installed here. They use the same `jdk22plus` SDK and
JVMCI setup as 25 and 26.

**Argfiles.** JDK 22-26 run with the platform JVMCI module patched; JDK 27+ has no JVMCI, and the
SDK supplies the module itself. An argfile written for one setup fails on the other: JDK 27
rejects `-XX:+EnableJVMCI`, and JDK 25 reports "JVMCI is not enabled". The `jdk22plus` SDK
therefore ships three argfiles, each stating the JDKs it is for:
- `tornado-argfile-jdk22-26`;
- `tornado-argfile-jdk27plus`;
- `tornado-argfile` for the build JDK.

All three run without regenerating. Set `TORNADOVM_JVMCI_MODE=absent|patched` to make the launcher
print a given setup's flags.

**Why the argfile is not leaner yet.** 46 of the 60 flag lines exist only for Graal and JVMCI:
- `-XX:+EnableJVMCI`, `--upgrade-module-path graalJars`, `--patch-module`;
- the opens and exports to `jdk.internal.vm.ci` and `tornado.graal`.

The SDK also carries `graalJars` (8.7 MB). Selecting TC replaces only the kernel compiler. Every
task still builds a Graal sketch (`TornadoSketcher`, whose `Access[]` TC copies), and method
resolution goes through JVMCI: 99 `tornado-runtime` files and 29 `drivers-common` files import
Graal or JVMCI. The argfile shrinks to about 14 lines in Phase 8, once:
- TC's own `Access[]` replaces the sketch (equal by C4c);
- the runtime resolves methods through the class-file model.
