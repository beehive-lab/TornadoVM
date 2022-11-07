## Execution of TornadoVM on ARM Mali GPUs


#### Installation
The installation of TornadoVM to run on ARM Mali GPUs requires JDK11 with GraalVM. See the [INSTALL WITH GRAALVM](10_INSTALL_WITH_GRAALVM.md) document for details about the installation.


The OpenCL driver for Mali GPUs on Linux that has been tested is:

* OpenCL C 2.0 v1.r9p0-01rel0.37c12a13c46b4c2d9d736e0d5ace2e5e: [link](https://developer.arm.com/tools-and-software/graphics-and-gaming/mali-drivers/bifrost-kernel)


#### Testing

We have tested TornadoVM on the following ARM Mali GPUs:

* Mali-G71, which implements the Bifrost architecture: [link](https://developer.arm.com/ip-products/graphics-and-multimedia/mali-gpus/mali-g71-gpu)


Some of the unittests in TornadoVM run with `double` data types. To enable double support, TornadoVM includes the following extension in the generated OpenCL code:

```c
cl_khr_fp64
```

However, this extension is not available on Bifrost GPUs.


The rest of the unittests should pass.
