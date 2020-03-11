
# Using Docker


_We have tested our docker images for CentOS >= 7.4 and Ubuntu >= 16.04._ We currently have docker images for NVIDIA and Intel Integrated GPUs using OpenJDK 8 and GraalVM for JDK 8 and 11:
* TornadoVM docker images for NVIDIA GPUs
* TornadoVM docker images for Intel Integrated Graphics

##### TornadoVM Docker for NVIDIA GPUs:

Note that this requires the [nvidia-docker](https://github.com/NVIDIA/nvidia-docker) runtime.

Using TornadoVM with OpenJDK 8 for NVIDIA GPUs:

```bash
## Docker image for TornadoVM and OpenJDK 8
$ docker pull beehivelab/tornado-gpu:latest
$ git clone https://github.com/beehive-lab/docker-tornado
$ cd docker-tornado
$ ./run_nvidia.sh javac.py example/MatrixMultiplication.java
$ ./run_nvidia.sh tornado example/MatrixMultiplication
```

Using TornadoVM with GraalVM for NVIDIA GPUs:

```bash
## GraalVM with JDK 8
$ docker pull beehivelab/tornado-gpu-graalvm-jdk8:latest

## GraalVM with JDK 11
$ docker pull beehivelab/tornado-gpu-graalvm-jdk11:latest
```

In our [docker-tornado](https://github.com/beehive-lab/docker-tornado) repository we have all runner scripts for each configuration.

```bash
# example of running graal images
./run_nvidia_graalvm-jdk8.sh tornado ...
./run_nvidia_graalvm-jdk11.sh tornado ...
```

Example for running the TornadoVM with OpenJDK 8:

```bash
## Run with TornadoVM on an NVIDIA GPU using OpenJDK 8
$ ./run_nvidia.sh tornado example/MatrixMultiplication 2048   ## Running on NVIDIA GP100
Computing MxM of 2048x2048
	CPU Execution: 0.36 GFlops, Total time = 48254 ms
	GPU Execution: 277.09 GFlops, Total Time = 62 ms
	Speedup: 778x
```

##### TornadoVM Docker for Intel Integrated GPUs:

Using TornadoVM with OpenJDK 8 for Intel Integrated Graphics:

```bash
# TornadoVM image with OpenJDK 8
$ docker pull beehivelab/tornado-intel-gpu:latest
$ git clone https://github.com/beehive-lab/docker-tornado
$ cd docker-tornado
$ ./run_intel.sh javac.py example/MatrixMultiplication.java
$ ./run_intel.sh tornado example/MatrixMultiplication
```

Using TornadoVM with GraalVM for Intel Integrated Graphics:

```bash
## GraalVM with JDK 8
$ docker pull beehivelab/tornado-intel-igpu-graalvm-jdk8:latest
$ run_intel_graalvm_jdk8.sh tornado ...
## GraalVM with JDK 11
$ docker pull beehivelab/tornado-intel-igpu-graalvm-jdk11:latest
$ run_intel_graalvm_jdk11.sh tornado ...
```

See our [docker-tornado](https://github.com/beehive-lab/docker-tornado) repository for more details.
