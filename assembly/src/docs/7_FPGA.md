# TornadoVM FPGA Support

TornadoVM supports execution and prototyping with OpenCL compatible Intel and Xilinx FPGAs. For debugging you can use common IDEs from Java ecosystem.

**IMPORTANT NOTE:** The minimum input size to run on the FPGA is 64 elements (which corresponds internally with the local work size in OpenCL).

This [document](17_AWS.md) shows a full guideline for running TornadoVM on Amazon AWS F1 with Xilinx FPGAs.


### Pre-requisites

We have currently tested with an Intel Nallatech-A385 FPGA (Intel Arria 10 GT1150) and a Xilinx KCU1500 FPGA card.
We have also tested it on the AWS EC2 F1 instance with `xilinx_aws-vu9p-f1-04261818_dynamic_5_0` device.

* HLS Versions: Intel Quartus 17.1.0 Build 240, Xilinx SDAccel 2018.2, Xilinx SDAccel 2018.3, Xilinx Vitis 2020.2
* TornadoVM Version: >= 0.9
* AWS AMI Version: 1.6.0

If the OpenCL ICD loaders are installed correctly, the output of the ```clinfo``` it should be the following:  

```bash
$ clinfo
   Number of platforms                               1
   Platform Name                                   Intel(R) FPGA SDK for OpenCL(TM)
   Platform Vendor                                 Intel(R) Corporation
   Platform Version                                OpenCL 1.0 Intel(R) FPGA SDK for OpenCL(TM), Version 17.1
   Platform Profile                                EMBEDDED_PROFILE
   Platform Extensions                             cl_khr_byte_addressable_store cles_khr_int64 cl_intelfpga_live_object_tracking cl_intelfpga_compiler_mode cl_khr_icd cl_khr_3d_image_writes
   Platform Extensions function suffix             IntelFPGA

   Platform Name                                   Intel(R) FPGA SDK for OpenCL(TM)
   Number of devices                                 1
   Device Name                                     p385a_sch_ax115 : nalla_pcie (aclnalla_pcie0)
   Device Vendor                                   Nallatech ltd
   Device Vendor ID                                0x1172
   Device Version                                  OpenCL 1.0 Intel(R) FPGA SDK for OpenCL(TM), Version 17.1
   Driver Version                                  17.1
   Device OpenCL C Version                         OpenCL C 1.0
   Device Type                                     Accelerator
```

## Step 1: Update/Create the FPGA's configuration file

Update the "$TORNADO_SDK/etc/vendor-fpga.conf" file with the necessary information (i.e. fpga platform name (DEVICE_NAME), HLS compiler flags (FLAGS), HLS directory (DIRECTORY_BITSTREAM). TornadoVM will automatically load the user-defined configurations according to the vendor of the underlying FPGA device.  You can also run TornadoVM with your configuration file, by using the `-Dtornado.fpga.conf.file=FILE` flag.

### Example of configuration file for Intel FPGAs (Emulation mode) with the [TornadoVM Docker image](https://github.com/beehive-lab/docker-tornado#intel-integrated-graphics):

Edit/create the configuration file fo the FPGA:

```bash
$ vim $TORNADO_SDK/etc/intel-docker-fpga.conf
```

```conf
# Configure the fields for FPGA compilation & execution
# [device]
DEVICE_NAME = fpga_fast_emu
# [compiler]
COMPILER = aocl-ioc64
# [options]
DIRECTORY_BITSTREAM = fpga-source-comp/ # Specify the directory
```

### Example of configuration file for Intel Nallatech-A385 FPGA (Intel Arria 10 GT1150):

Edit/create the configuration file fo the FPGA:

```bash
$ vim $TORNADO_SDK/etc/intel-fpga.conf
```

```conf
# Configure the fields for FPGA compilation & execution
# [device]
DEVICE_NAME = p385a_sch_ax115
# [compiler]
COMPILER = aoc
# [options]
FLAGS = -v -report # Configure the compilation flags
DIRECTORY_BITSTREAM = fpga-source-comp/ # Specify the directory
```

### Example of configuration file for Xilinx KCU1500:

```bash
$ vim $TORNADO_SDK/etc/xilinx-fpga.conf
```

```conf
# Configure the fields for FPGA compilation & execution
# [device]
DEVICE_NAME = xilinx_kcu1500_dynamic_5_0
# [compiler]
COMPILER = xocc
# [options]
FLAGS = -O3 -j12 # Configure the compilation flags
DIRECTORY_BITSTREAM = fpga-source-comp/ # Specify the directory
```
In order to use the Xilinx Toolchain, it is required to initialize the env variables of the SDAccel toolchain as follows:

```bash
source /opt/Xilinx/SDx/2018.2/settings64.sh
```

### Example of configuration file for Xilinx Alveo U50:

```bash
$ vim etc/xilinx-fpga.conf
```

```conf
# Configure the fields for FPGA compilation & execution
# [device]
DEVICE_NAME = xilinx_u50_gen3x16_xdma_201920_3
# [compiler]
COMPILER = v++
# [options]
FLAGS = -O3 -j12 # Configure the compilation flags
DIRECTORY_BITSTREAM = fpga-source-comp/ # Specify the directory
```

In order to use the Xilinx Toolchain, it is required to initialize the env variables of the Vitis toolchain as follows:
```bash
source /opt/Xilinx/Vitis/2020.2/settings64.sh
source /opt/xilinx/xrt/setup.sh
```

### Example of configuration file for AWS xilinx_aws-vu9p-f1-04261818_dynamic_5_0:

```bash
$ vim $TORNADO_SDK/etc/xilinx-fpga.conf
```

```conf
# Configure the fields for FPGA compilation & execution
# [device]
DEVICE_NAME = /home/centos/src/project_data/aws-fpga/SDAccel/aws_platform/xilinx_aws-vu9p-f1-04261818_dynamic_5_0/xilinx_aws-vu9p-f1-04261818_dynamic_5_0.xpfm
# [compiler]
COMPILER = xocc
# [options]
FLAGS = -O3 -j12 # Configure the compilation flags
DIRECTORY_BITSTREAM = fpga-source-comp/ # Specify the directory
```

## Step 2: Select one of the three FPGA Execution Modes  

### 1. Full JIT Mode  

This mode allows the compilation and execution of a given task for the FPGA. As it provides full end-to-end execution, the compilation is expected to take up to 2 hours due HLS bistream generation process.

The log dumps from the HLS compilation are written in the `output.log` file, and potential emerging errors in the `error.log` file.
The compilation dumps along with the generated FPGA bitstream and the generated OpenCL code can be found in the `fpga-source-comp/` directory which is defined in the FPGA configuration file (Step 1).

Example:

```bash
tornado --jvm="-Ds0.t0.device=0:1" -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic --params="1024 normal 1"
```

Note: The Full JIT mode on the Alveo U50 presents some constraints regarding the maximum allocated space on the device memory. Although the Xilinx driver reports 1GB as the maximum allocation space, the XRT layer throws an error (`[XRT] ERROR: std::bad_alloc`) when the heap size is larger than 64MB. This issue is reported to Xilinx, and it is anticipated to be fixed soon. For applications that do not require more than 64MB of heap size, the following flag can be used `-Dtornado.device.memory=64MB`.

```bash
tornado --jvm="-Ds0.t0.device=0:1 -Dtornado.device.memory=64MB" -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic --params="1024 normal 1"
```

### 2. Ahead of Time Execution Mode

Ahead of time execution mode allows the user to use a pre-generated bitstream of the Tornado tasks and then load it in a separated execution. The path of the FPGA bitstream file should be given via the `-Dtornado.precompiled.binary` flag, and the file should be named as `lookupBufferAddress`.

Example:  

```bash
tornado --jvm="-Ds0.t0.device=0:1 -Ds0.t0.global.dims=1024 -Ds0.t0.local.dims=64 \
    -Dtornado.precompiled.binary=/path/to/lookupBufferAddress,s0.t0.device=0:1 "
    -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic \
    --params="1024 normal 10"
```

Note: The Ahead of Time mode on the Alveo U50 presents some constraints regarding the maximum allocated space on the device memory. Although the Xilinx driver reports 1GB as the maximum allocation space, the XRT layer throws an error (`[XRT] ERROR: std::bad_alloc`) when the heap size is larger than 64MB. This issue is reported to Xilinx, and it is anticipated to be fixed soon. For applications that do not require more than 64MB of heap size, the following flag can be used `-Dtornado.device.memory=64MB`.

```bash
tornado --jvm="-Ds0.t0.device=0:1 -Dtornado.device.memory=64MB "\
    -m tornadoexamples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic \
    --params="1024 normal 1"
```

### 3. Emulation Mode

Emulation mode can be used for fast-prototyping and ensuring program functional correctness before going through the full JIT process (HLS).

Before executing the TornadoVM program, the following steps needs to be executed based on the FPGA vendors' toolchain:  

#### A) Emulation of an Intel platform:

You can run in Emulation mode either by using a Docker container or locally:

- Using a Docker Image:

If you use a Docker image provided by [Intel OneAPI](https://hub.docker.com/r/intel/oneapi), please set up the following variable:
```bash 
$ export DOCKER_FPGA_EMULATION=1
```
In case you use the [TornadoVM Docker image](https://github.com/beehive-lab/docker-tornado#intel-integrated-graphics), this variable is configured by default.
Therefore you can run the following example in which the FPGA device uses the identifier `1:0`.

Example:
```bash
./run_intel_openjdk.sh tornado \
    --jvm="-Ds0.t0.device=1:0 "
    -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic --params="1024 default 10"
```

- Local execution:

Set the `CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA` env variable to `1`, so as to enable the execution on the emulated device. 
```bash
$ export CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1
```

Example:  

```bash
env CL_CONTEXT_EMULATOR_DEVICE_INTELFPGA=1 tornado \
    --jvm="-Ds0.t0.device=0:1" \
    -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic \
    --params="1024 normal 10"
```

#### B) Emulation of a Xilinx platform (using Vitis):

- Configure the device characteristics (e.g. which platform, number of devices) with the [Xilinx Emulation Configuration Utility (emconfigutil)](https://www.xilinx.com/html_docs/xilinx2020_2/vitis_doc/nrj1570599837825.html).  Then you can use the TornadoVM Makefile and pass the configuration parameters as variables (e.g. `make xilinx_emulation FPGA_PLATFORM=<platform_name> NUM_OF_FPGA_DEVICES=<number_of_devices>`). *Be aware that the platform name must be the same with the device name in Step 1.* The default options configure one `xilinx_u50_gen3x16_xdma_201920_3` device. For example:
```bash
make xilinx_emulation FPGA_PLATFORM=xilinx_u50_gen3x16_xdma_201920_3 NUM_OF_FPGA_DEVICES=1
```

- Set the `XCL_EMULATION_MODE` env variable to `sw_emu`, so as to enable the execution on the emulated device. 
```bash
$ export XCL_EMULATION_MODE=sw_emu
```

Example:  

```bash
tornado \
    --jvm="-Ds0.t0.device=0:1" \
    -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTDynamic \
    --params="1024 normal 10"
```

Note: The emulation mode through SDAccel results in wrong results. However when we run in the Full JIT or the Ahead of Time modes the kernels return correct results. 
