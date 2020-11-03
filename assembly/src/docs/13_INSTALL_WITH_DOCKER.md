# Using Docker with TornadoVM

_We have tested our docker images for CentOS >= 7.4 and Ubuntu >= 16.04._ We currently have docker images for NVIDIA and Intel Integrated GPUs using OpenJDK 8 and GraalVM for JDK 8 and 11:
* TornadoVM docker images for **NVIDIA GPUs**
* TornadoVM docker images for **Intel Integrated Graphics**

## Nvidia GPUs

### Prerequisites

The `tornado-gpu` docker image needs the docker `nvidia` daemon.  More info here: [https://github.com/NVIDIA/nvidia-docker](https://github.com/NVIDIA/nvidia-docker).

### How to run?

#### 1) Pull the image

#### 1-A) Using TornadoVM with OpenJDK 8 for NVIDIA GPUs:

```bash
# Docker Image for TornadoVM image and OpenJDK 8
$ docker pull beehivelab/tornado-gpu:latest
```

#### 1-B) Using TornadoVM with GraalVM for NVIDIA GPUs:

```bash
## GraalVM with JDK 8
$ docker pull beehivelab/tornado-gpu-graalvm-jdk8:latest

## GraalVM with JDK 11
$ docker pull beehivelab/tornado-gpu-graalvm-jdk11:latest
```

#### 2) Run an experiment

We provide a runner script that compiles and run your Java programs with Tornado. Here's an example:

```bash
$ git clone https://github.com/beehive-lab/docker-tornado
$ cd docker-tornado

## Compile Matrix Multiplication - provided in the docker-tornado repository
$ ./run_nvidia.sh javac.py example/MatrixMultiplication.java

## Run with TornadoVM on the NVIDIA GPU !
$ ./run_nvidia.sh tornado example/MatrixMultiplication 2048   ## Running on NVIDIA GP100
Computing MxM of 2048x2048
	CPU Execution: 0.36 GFlops, Total time = 48254 ms
	GPU Execution: 277.09 GFlops, Total Time = 62 ms
	Speedup: 778x 
```

### Some options

```bash
# To see the generated OpenCL kernel
$ ./run_nvidia.sh tornado --printKernel example/MatrixMultiplication

# To check some runtime info about the kernel execution and device
$ ./run_nvidia.sh tornado --debug example/MatrixMultiplication
```

The `tornado` command is just an alias to the `java` command with all the parameters for TornadoVM execution. So you can pass any Java (OpenJDK or Hotspot) parameter.

```bash
$ ./run_nvidia.sh tornado -Xmx16g -Xms16g example/MatrixMultiplication
```

```bash
# Example of running graal images
./run_nvidia_graalvm-jdk8.sh tornado example/MatrixMultiplication 2048
./run_nvidia_graalvm-jdk11.sh tornado example/MatrixMultiplication 2048 
```

# Intel Intergrated Graphics

### Prerequisites

The `tornado-intel-gpu` docker image Intel OpenCL driver for the integrated GPU installed.  More info here: [https://github.com/intel/compute-runtime](https://github.com/intel/compute-runtime).

### How to run?

#### 1) Pull the image

#### 1-A) Using TornadoVM with OpenJDK 8 for Intel Integrated GPUs:

```bash
# Docker Image for TornadoVM image and OpenJDK 8
$ docker pull beehivelab/tornado-intel-gpu:latest
```

#### 1-B) Using TornadoVM with GraalVM for Intel Integrated GPUs:

```bash
## GraalVM with JDK 8
$ docker pull beehivelab/tornado-intel-igpu-graalvm-jdk8:latest

## GraalVM with JDK 11
$ docker pull beehivelab/tornado-intel-igpu-graalvm-jdk11:latest
```

#### 2) Run an experiment

We provide a runner script that compiles and runs your Java programs with Tornado. Here's an example:

```bash
$ git clone https://github.com/beehive-lab/docker-tornado
$ cd docker-tornado

## Compile Matrix Multiplication - provided in the docker-tornado repository
$ ./run_intel.sh javac.py example/MatrixMultiplication.java

## Run an example using TornadoVM with OpenJDK 8 on the Intel Integrated GPU!
$ ./run_intel.sh tornado example/MatrixMultiplication 256   ## Running on Intel(R) Gen9 HD Graphics
Computing MxM of 256x256
	CPU Execution: 1.53 GFlops, Total time = 22 ms
	GPU Execution: 8.39 GFlops, Total Time = 4 ms
	Speedup: 5x

```

### Some options

```bash
# To see the generated OpenCL kernel
$ ./run_intel.sh tornado --printKernel example/MatrixMultiplication

# To check some runtime info about the kernel execution and device
$ ./run_intel.sh tornado --debug example/MatrixMultiplication
```

The `tornado` command is just an alias to the `java` command with all the parameters for TornadoVM execution. So you can pass any Java (OpenJDK or Hotspot) parameter.

```bash
$ ./run_intel.sh tornado -Xmx16g -Xms16g example/MatrixMultiplication
```

```bash
## Example of running graal images
$ ./run_intel_graalvm_jdk8.sh tornado example/MatrixMultiplication 256 
$ ./run_intel_graalvm_jdk11.sh tornado example/MatrixMultiplication 256
```

See our [docker-tornado](https://github.com/beehive-lab/docker-tornado) repository for more details.
