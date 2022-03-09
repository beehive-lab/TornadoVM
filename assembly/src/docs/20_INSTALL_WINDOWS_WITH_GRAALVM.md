## Build TornadoVM with Graal 21.3.0 JDK 11/17 on Windows 10 (x64)

_**[DISCLAIMER] Please, notice that, although TornadoVM can run on Windows10, it is still experimental.**_

#### 1. Install prerequisites

##### Maven

Download Apache Maven from the [official site](https://maven.apache.org/download.cgi) and extract it to any location on
your computer. Below it's assumed that Maven's home is `C:/Maven`.

##### MSys2

1. Download the [MSys2](https://www.msys2.org/) installer from the official website and run it. You may choose any
   installation directory, below it will be referred as `<MSYS2>`.

**IMPORTANT:** the only executable you should use as a terminal is `<MSYS2>/mingw64.exe`.

2. Update MSys2 _**system**_ packages. Start `<MSYS2>/mingw64.exe` and run the following command in the terminal:

```bash
pacman -Syu
```

You might need to execute it several times until you see that no updates found.

3. Update MSys2 _**default**_ packages. In the terminal window of `<MSYS2>/mingw64.exe` run:

```bash
pacman -Su
```

You might need to execute it several times until you see that no updates found.

4. Install necessary tools to MSys2. In the terminal window of `<MSYS2>/mingw64.exe` run:

```bash
pacman -S \
mingw-w64-x86_64-make           \
mingw-w64-x86_64-cmake          \
mingw-w64-x86_64-gcc            \
mingw-w64-x86_64-opencl-headers \
mingw-w64-x86_64-opencl-icd \
python make git
```

5. Create default Maven repository for MSys2 user:

```bash
cd ~
mkdir .m2
```

6. Create default content for the file `~/.m2/settings.xml`:

```bash
cat > ~/.m2/settings.xml << EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <localRepository/>
    <interactiveMode/>
    <offline/>
    <pluginGroups/>
    <servers/>
    <mirrors/>
    <proxies/>
    <profiles/>
    <activeProfiles/>
</settings>
EOF
```

7. Create file `mvn` in `<MSYS2>/mingw64/bin` with any textual editor according to the template below:

```bash
#!/usr/bin/env bash
BASH_FILE=$(where bash)
SETTINGS=\"${BASH_FILE%\\*\\*\\*}${HOME}/.m2/settings.xml\"

C:/Maven/bin/mvn.cmd --settings ${SETTINGS} "$@"
```

You should only change path to Maven executable to the actual location of Maven in your system.

#### 2. Download TornadoVM

Clone the latest TornadoVM sources from the GitHub [repository](https://github.com/beehive-lab/TornadoVM)
using `<MSYS2>/mingw64.exe`:

```bash
cd D:/MyProjects
git clone https://github.com/beehive-lab/TornadoVM.git
cd TornadoVM
```

We will refer hereafter the directory with TornadoVM sources as `<TornadoVM>`.

#### 3. Download Graal 21.3.0

TornadoVM can run with JDK 11 and 17.
Visit [GraalVM Community Edition 21.3.0](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-21.3.0) and
download either of the following builds:

- [Download for JDK 11](https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java11-windows-amd64-21.3.0.zip)
- [Download for JDK 17](https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java17-windows-amd64-21.3.0.zip)

Extract the downloaded file to any directory.

#### 4. Install the NVIDIA drivers and CUDA SDK

###### A) CUDA Driver

Most Windows systems come with the NVIDIA drivers pre-installed. You can check your installation and the latest drivers
available by using [NVIDIA GEFORCE Experience](https://www.nvidia.com/it-it/geforce/geforce-experience/download/) tool.

Alternatively, all NVIDIA drivers can be found
here: [NVIDIA Driver Downloads](https://www.nvidia.com/Download/index.aspx).

##### B) OpenCL and NVIDIA PTX

If you plan to only use the OpenCL backend from TornadoVM, then you only need the NVIDIA driver from the previous step.

If you want to also use the PTX backend, then you need to install the NVIDIA CUDA Toolkit.

- Complete CUDA Toolkit
  from [CUDA Toolkit Downloads](https://developer.nvidia.com/cuda-downloads?target_os=Windows&target_arch=x86_64).

Note that this toolkit includes GPU drivers as well, so you may avoid downloading drivers separately. The only thing to
note is that GPU driver you are currently using should be of the same or higher version than the one shipped with CUDA
Toolkit. Thus, if you have existing driver make sure that it's version is same or higher, otherwise update GPU driver
during toolkit installation. Note, that NSight, BLAST libs and Visual Studio integration are irrelevant for TornadoVM
builds, you need just SDK - so you may skip installing them.

#### 5. Configure the build

Using any text editor create file `<TornadoVM>/etc/sources.env`  with the following content:

```bash
#!/bin/bash

# UPDATE PATH TO ACTUAL LOCATION OF THE JDK OR GRAAL (REMEMBER OF UNIX_STYLE SLASHES AND SPACES!!!)
export JAVA_HOME="C:/graalvm-ce-java11-21.3.0"

## NEXT TWO LINES NECESSARY TO BUILD PTX (NVIDIA CUDA) BACKEND
## COMMENT THEM OUT OR JUST IGNORE IF YOU ARE NOT INTERESTED IN PTX BUILD
## OTHERWISE UPDATE 'CUDA_PATH' WITH ACTUAL VALUE (REMEMBER OF UNIX_STYLE SLASHES AND SPACES!!!)
export CUDA_PATH="C:/Program Files/NVIDIA GPU Computing Toolkit/CUDA/v11.2"
export PTX_LDFLAGS=-L\"$CUDA_PATH/lib/x64\"

# LEAVE THE REST OF FILE 'AS IS'
# DON'T ALTER!
export PATH=$PWD/bin/bin:$PATH               ## This directory will be automatically generated during Tornado compilation
export TORNADO_SDK=$PWD/bin/sdk              ## This directory will be automatically generated during Tornado compilation
CMAKE_FILE=$(where cmake | head -n 1)
export CMAKE_ROOT=${CMAKE_FILE%\\*\\*}
```

There are only 2 places you should adjust:

1. `JAVA_HOME` path that points to your Graal installation
2. `CUDA_PATH` pointing to your NVIDIA GPU Computing Toolkit (CUDA) - this one is necessary only for builds with PTX
   backend.

#### 6. Compile TornadoVM

Start `<MSYS2>/mingw64.exe` terminal, navigate to the `<TornadoVM>` directory, and build TornadoVM as follows:

```bash
cd D:/MyProjects/TornadoVM
source etc/sources.env
make graal-jdk-11-plus BACKEND=ptx,opencl
```

The `BACKEND` parameter has to be a comma-separated list of `ptx` and `opencl` options. You may build `ptx` only when
NVIDIA GPU Computing Toolkit (CUDA) is installed.

#### 7. Check the installation

Don't close `<MSYS2>/mingw64.exe` after the build. Run the following command to see that TornadoVM is working:

```bash
tornado --devices
```

You should see a list of OpenCL and/or CUDA devices available on your system.

Now try to run a simple test. To run examples with Graal JDK 11, TornadoVM uses modules:

```bash 
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D 512
```

To run individual tests:

```bash
tornado -Dtornado.unittests.verbose=True -Xmx6g  -m  tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner uk.ac.manchester.tornado.unittests.arrays.TestArrays
```

To run all unit-tests:

```bash
make tests
```

### Known issues

1. If you already have MSys2 installed and heavily customized you may experience issues with build or tests. We are
   suggesting to start with fresh MSys2 installation in this case and follow the instructions above. Most notably, make
   sure that you have no `mingw-w64-x86_64-python` installed - it prevents Python scripts that execute tests from
   running. Also, make sure that you have updated all GCC / Make / CMake packages mentioned.
2. If you see no output from `tornado --devices` this may be either of 2 reasons:
   -- OpenCL / CUDA is misconfigured. Download any third-party tool for OpenCL / CUDA capabilities viewing and check
   that you can see your devices there. Sometimes order of installation of different OpenCL drivers matters - Intel
   OpenCL SDK may shadow NVIDIA OpenCL and alike. -- You build native code of the library using wrong compiler, most
   probably you ran `<MSYS2>/msys2.exe` terminal instead of `<MSYS2>/mingw64.exe` . Please re-try with correct
   terminal (and therefore GCC) version.
3. If you see JVM crashes or `UnsatisfiedLinkError` or some `Error initializing DLL` during `tornado --devices`
   execution than it's definitely due to wrong GCC (and hence terminal) version used during build.
