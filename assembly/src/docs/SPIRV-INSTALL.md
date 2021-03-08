### Install TornadoVM for SPIR-V

SPIR-V makes use of the Intel Level Zero API. This branch needs level zero built in the system.

##### 1. Install Level Zero

Use GCC >= 9:

On CentOS:

```
$ scl enable devtoolset-9 bash # Only for CentOS
```

Using commit id `0d30b1fa712253c68bfdfa3863d380df4301b8a4` from Level Zero.

```bash
$ git clone https://github.com/oneapi-src/level-zero
$ mkdir build
$ cd build
$ cmake ..
$ cmake --build . --config Release
$ cmake --build . --config Release --target package
```

Export `ZE_SHARED_LOADER` pointing to `libze_loader.so`:

Example:

```bash 
export ZE_SHARED_LOADER="/home/juan/manchester/SPIRV/level-zero/build/lib/libze_loader.so"
```

Then compile TornadoVM as usual:

```
make 
```

Note: Currently SPIRV is part of the OpenCL backend







