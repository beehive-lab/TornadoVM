# tornado-meta

TornadoVM's metadata and code-description API: the interfaces and value types (`ResolvedJavaMethod`,
`ResolvedJavaType`, `JavaKind`, `JavaConstant`, `Register`, `TargetDescription`, ...) that the frozen
Graal 23.1.0 compiler in `tornado.graal` and the TornadoVM backends are written against.

TornadoVM does not use the JVM Compiler Interface (JVMCI). It reads class metadata through reflection
(`uk.ac.manchester.tornado.runtime.meta.reflection`) and implements these interfaces itself, so this module needs
no JVM support: it is plain Java and runs on any JDK from 22 up.

## Provenance

The sources are the JVMCI API packages of OpenJDK `jdk-21.0.2+13`
(`https://github.com/openjdk/jdk21u`, `src/jdk.internal.vm.ci/share/classes`), frozen at that version.

| OpenJDK package | Package here |
|---|---|
| `jdk.vm.ci.meta` | `tornado.meta` |
| `jdk.vm.ci.code`, `.code.site`, `.code.stack` | `tornado.meta.code`, `.code.site`, `.code.stack` |
| `jdk.vm.ci.common` | `tornado.meta.common` |
| `jdk.vm.ci.amd64`, `jdk.vm.ci.aarch64` | `tornado.meta.amd64`, `tornado.meta.aarch64` |

Changes from the original sources, each recorded in a notice under the file's license header:

- packages renamed as above;
- `TargetDescription` and `InitTimer` read `System.getProperty` instead of `jdk.vm.ci.services.Services`;
- the `package-info.java` files are not included.

The HotSpot-specific JVMCI packages (`jdk.vm.ci.hotspot*`, `jdk.vm.ci.runtime`, `jdk.vm.ci.services`)
are not part of this module. The one exception is `tornado.meta.services.Services`, a TornadoVM-written
class (not OpenJDK code) with the three members Graal reads: `IS_IN_NATIVE_IMAGE`,
`IS_BUILDING_NATIVE_IMAGE` and `getSavedProperties()`.

The module is `open`, as the JDK's JVMCI packages were opened to Graal: Graal checks that its JVMCI
module is open to the modules it serves, and would otherwise call into the JVMCI runtime.

## License

GNU General Public License version 2 only, **without** the Classpath Exception (see
[`LICENSE_GPLv2`](../LICENSE_GPLv2)), as in
OpenJDK. One file, `tornado.meta.code.site.ImplicitExceptionDispatch`, carries the Classpath
Exception in its own header.
