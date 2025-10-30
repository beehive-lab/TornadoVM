# TornadoVM Build and Run Guide

TornadoVM provides two main approaches for building and running applications:

## Option 1: Make Build + Tornado Command

This is the traditional approach using the make build system and the tornado wrapper script.

### Build
```bash
make
```

### Run
```bash
tornado --jvm="-Dtornado.option=value" -cp <classpath> <MainClass> [args]
```

**Example:**
```bash
tornado --jvm="-Dtornado.debug=true" -cp target/examples.jar examples.MatrixMultiplication
```

### When to use
- Standard development workflow
- When you want simplified command-line interface
- When working with pre-configured TornadoVM installations

### How it works
- `make` builds the entire TornadoVM SDK in `bin/sdk/`
- The `tornado` command is a wrapper script that:
  - Sets up the Java module path
  - Configures all required JVM flags and exports
  - Adds TornadoVM modules and native libraries
  - Launches the application with proper configuration

---

## Option 2: Maven Build + Java with Argfile

This is a Maven-friendly approach that uses an argument file for JVM configuration.

### Build
```bash
./mvnw clean install -DskipTests
```

### Run
```bash
java @tornado-argfile -cp <classpath> <MainClass> [args]
```

**Example:**
```bash
java @tornado-argfile \
  -cp tornado-examples/target/tornado-examples-1.1.2-SNAPSHOT.jar \
  uk.ac.manchester.tornado.examples.compute.MatrixMultiplication
```

### When to use
- Maven-based projects and workflows
- IDE integration (IntelliJ, Eclipse, VS Code)
- CI/CD pipelines
- When you need explicit control over JVM arguments
- When debugging with IDEs

### How it works
- `./mvnw clean install` builds all TornadoVM modules using Maven
- The `tornado-argfile` contains all necessary JVM flags:
  - JVM mode and memory settings (`-XX:+EnableJVMCI`, etc.)
  - Native library paths (`-Djava.library.path=...`)
  - Module system configuration (`--module-path`, `--add-modules`, etc.)
  - Required exports and opens for Graal compiler access
- The `@tornado-argfile` syntax tells Java to read arguments from the file

### Viewing the argfile
The `tornado-argfile` is located at the root of the TornadoVM repository. You can examine it to see all JVM configuration:
```bash
cat tornado-argfile
```

---

## Comparison

| Aspect | Make + Tornado Command | Maven + Java @argfile |
|--------|----------------------|----------------------|
| **Build Tool** | Make | Maven |
| **Run Command** | `tornado` wrapper | `java @tornado-argfile` |
| **IDE Integration** | Limited | Excellent |
| **Flexibility** | Simplified | Full control |
| **CI/CD** | Traditional scripts | Maven-native |
| **JVM Config** | Hidden in wrapper | Explicit in argfile |

---

## Configuration Files

### tornado-argfile
Located at the repository root, this file contains:
- JVM server mode and experimental VM options
- JVMCI enablement for Graal compiler
- Native library paths for TornadoVM
- Tornado runtime class implementations
- Module path and upgrade paths
- Extensive `--add-exports` and `--add-opens` declarations for Graal compiler internals

### .mvn/maven.config
Maven configuration file that applies to all Maven builds (both `./mvnw` and `bin/compile`):
- **Parallel builds**: `-T1.5C` (1.5 threads per CPU core)
- **Colored output**: `-Dstyle.color=always` for better readability
- **Default profiles**: JDK 21 + OpenCL backend (can be overridden)
- **Skip Javadoc**: `-Dmaven.javadoc.skip=true` for faster builds
- **Fail fast**: `-Dfailfast=true` for quicker feedback during development
- **Timestamps**: Shows build timestamps for performance tracking

These settings are automatically applied. To override:
```bash
# Single-threaded build
./mvnw -T1 install

# Generate Javadocs
./mvnw install -Dmaven.javadoc.skip=false
```

### Module Structure
Both approaches load these core TornadoVM modules:
- `tornado.runtime` - Core runtime implementation
- `tornado.annotation` - Annotation processing
- `tornado.drivers.common` - Common driver infrastructure
- `tornado.drivers.opencl` - OpenCL backend (additional drivers may be added)

---

## Tips

### Build Performance

**Standard workflow (same as master branch):**
```bash
make                           # Full clean build every time
bin/compile --jdk jdk21 --backend opencl
```

**Faster incremental workflow:**
```bash
# Skip 'mvn clean' - only builds changed modules
make incremental               # Faster for development
bin/compile --jdk jdk21 --backend opencl --incremental
```

**When to use incremental:**
- During active development on specific modules
- When you want faster iteration cycles (skips `mvn clean`)

**When NOT to use incremental:**
- After pulling changes from git
- When switching branches
- Before committing/pushing code

### Using Additional JVM Options
**Option 1:**
```bash
tornado --jvm="-Xmx8g -Dtornado.debug=true" -cp app.jar Main
```

**Option 2:**
```bash
java @tornado-argfile -Xmx8g -Dtornado.debug=true -cp app.jar Main
```

### Checking Available Devices
**Option 1:**
```bash
tornado --devices
```

**Option 2:**
```bash
java @tornado-argfile -cp bin/sdk/share/java/tornado/tornado-drivers-opencl-1.1.2-SNAPSHOT.jar \
  uk.ac.manchester.tornado.drivers.opencl.OCLDeviceQuery
```

### IDE Configuration
For IntelliJ IDEA, Eclipse, or VS Code, use Option 2:
1. Import as Maven project
2. Configure run configuration with VM options: `@tornado-argfile`
3. Set classpath to include your application jar
4. Run directly from the IDE

---

## Troubleshooting

### "Module not found" errors
- Ensure `tornado-argfile` paths are correct for your installation
- Check that `--module-path` points to the built SDK location

### Native library errors
- Verify `-Djava.library.path` in `tornado-argfile` points to correct location
- Ensure native libraries were built successfully

### Class export errors
- The `tornado-argfile` contains all necessary `--add-exports`
- If custom modules are added, you may need to extend the argfile

---

For more information, see the [TornadoVM Documentation](https://tornadovm.readthedocs.io/).
