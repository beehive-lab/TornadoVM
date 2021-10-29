# LevelZero JNI 

Baremetal GPU and FPGA programming for Java using the [LevelZero API](https://spec.oneapi.io/level-zero/latest/index.html). 


This project is a Java Native Interface (JNI) binding for Intel's Level Zero. This library is as designed to be as closed as possible to the LevelZero API for C++. 


Subset of LevelZero 1.2.2 supported (LevelZero Feb 2021 version)


## Compilation & Configuration of the JNI Level-Zero API 

### 1) Install Level-Zero


Note: Using commit `551dd5810a3cea7a7e26ac4441da31878e804b53` from `level-zero` 


```bash
$ git clone https://github.com/oneapi-src/level-zero
$ mkdir build
$ cd build
$ cmake ..
$ cmake --build . --config Release
$ cmake --build . --config Release --target package
```


### 2) Compile JNI Native Code 

Set the paths to the directory of Level-Zero installation. Here's an example:

```bash
$ scl enable devtoolset-9 bash # Only for CentOS
$ export CPLUS_INCLUDE_PATH=/home/juan/manchester/SPIRV/level-zero/include:$CPLUS_INCLUDE_PATH
$ export LD_LIBRARY_PATH=/home/juan/manchester/SPIRV/level-zero/build/lib:$LD_LIBRARY_PATH 
$ export ZE_SHARED_LOADER="/home/juan/manchester/SPIRV/level-zero/build/lib/libze_loader.so"
$ cd levelZeroLib
$ mkdir build
$ cd build
$ cmake .. 
$ make 
```

### 3) Compile & Run a Java test


```bash
$ ./scripts/compileAndRun.sh
```


Note: For running with SPIR-V (example by default), the test program reads a SPIR-V kernel from `/tmp/example.spv`.


The OpenCL kernel is as follows:


```c
__kernel void copydata(__global int* input, __global int* output) {
	uint idx = get_global_id(0);
	output[idx] = input[idx];
}
```

To compile to SPIR-V:

```bash
$ clang -cc1 -triple spir copy_data.cl -O0 -finclude-default-header -emit-llvm-bc -o opencl-copy.bc
$ llvm-spirv opencl-copy.bc -o opencl-copy.spv
$ mv opencl-copy.spv /tmp/opencl-copy.spv
```


The clang implementation I'm currently using is the `intel/llvm`: https://github.com/intel/llvm 


To run:

```bash
$ java -Djava.library.path=./levelZeroLib/build -cp target/levelzero-1.0-SNAPSHOT.jar uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLevelZero
```

## License

This project is developed at [The University of Manchester](https://www.manchester.ac.uk/), and it is fully open source under the [MIT](https://github.com/beehive-lab/levelzero-jni/blob/master/LICENSE) license.


## Acknowledgments

The work was partially funded by the EU Horizon 2020 [Elegant 957286](https://www.elegant-h2020.eu/) project, and Intel Coorporation (https://www.intel.it/content/www/it/it/homepage.html).

