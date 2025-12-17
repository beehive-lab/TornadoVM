## TornadoVM SDK

This directory contains a pre-built TornadoVM SDK distribution. To use TornadoVM, you need to configure your environment variables according to your platform.

### Environment Configuration

#### Required Environment Variables

TornadoVM requires the following environment variables to be set:

1. **`JAVA_HOME`**: Path to your Java installation (JDK 21 or later required)
2. **`TORNADOVM_HOME`**: Path to this SDK installation directory
3. **`PATH`**: Must include `$TORNADOVM_HOME/bin` (or `%TORNADOVM_HOME%\bin` on Windows)

#### Linux and macOS

Set the environment variables in your terminal:

```bash
export JAVA_HOME=/path/to/your/jdk
export TORNADOVM_HOME=/path/to/tornadovm/sdk
export PATH=$TORNADOVM_HOME/bin:$PATH
```

**Tip**: Add these lines to your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.bash_profile`) to automatically configure the environment in every new terminal session.

Example for `~/.bashrc`:

```bash
# TornadoVM Configuration
export JAVA_HOME=/usr/lib/jvm/graalvm-jdk-21
export TORNADOVM_HOME=$HOME/opt/tornadovm-sdk
export PATH=$TORNADOVM_HOME/bin:$PATH
```

#### Windows (Command Prompt)

Set the environment variables in your command prompt:

```cmd
set JAVA_HOME=C:\path\to\your\jdk
set TORNADOVM_HOME=C:\path\to\tornadovm\sdk
set PATH=%TORNADOVM_HOME%\bin;%PATH%
```

#### Windows (PowerShell)

Set the environment variables in PowerShell:

```powershell
$env:JAVA_HOME="C:\path\to\your\jdk"
$env:TORNADOVM_HOME="C:\path\to\tornadovm\sdk"
$env:PATH="$env:TORNADOVM_HOME\bin;$env:PATH"
```

**Tip**: To persist these settings across sessions, use System Environment Variables or add them to your PowerShell profile (`$PROFILE`).

### Verifying Your Installation

After configuring the environment variables, verify your installation:

```bash
tornado --version
```

You should see the TornadoVM version information displayed.

### Running TornadoVM Programs

Once your environment is configured, you can run TornadoVM applications using the `tornado` command:

```bash
tornado --jvm="-Xmx8g" -cp target/example-1.0-SNAPSHOT.jar com.example.MyTornadoApp
```

Or run the test suite:

```bash
tornado-test --version
```

### Additional Resources

- **Documentation**: See the `share/tornado/README.md` for comprehensive documentation
- **Examples**: Check the `examples/` directory for sample TornadoVM programs
- **Changelog**: Review `CHANGELOG.rst` for version history and updates

### Troubleshooting

If you encounter issues:

1. Ensure `JAVA_HOME` points to a compatible JDK (JDK 21 or later)
2. Verify that all environment variables are correctly set in your current session
3. Check that your GPU drivers are properly installed for your target backend (OpenCL, PTX, or SPIR-V)
4. Ensure `TORNADOVM_HOME` points to the root directory of this SDK

For more help, visit: https://github.com/beehive-lab/TornadoVM
