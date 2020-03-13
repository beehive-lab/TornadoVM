# Running TornadoVM in AWS

# 1. Running on CPUs and GPUs

The installation and execution instructions for running on AWS CPUs and GPUs is identical to those for [running locally](1_INSTALL.md).

# 2. Running on AWS EC2 F1 Xilinx FPGAs

### Pre-requisites:
  * You need to have a storage bucket with: (s3_bucket, s3_dcp_key and s3_loogs_key) for Step 4.

The following come with the AWS EC2 F1 instance:
  * FPGA DEV AMI: 1.6.0
  * Xilinx SDx Tools: 2018.3

### 1. Install TornadoVM as a CentOS user. The Xilinx FPGA is not exposed to simple users.

```bash
$ cd TornadoVM
$ source etc/sources.env
$ make
$ tornado --devices
```

### 2. Follow these steps to get access to the Xilinx FPGA.
a. Enter a bash shell as root.

```bash
$ sudo -E /bin/bash
```

b. Load the environment variables for Xilinx HLS and runtime.

```bash
$ cd $AWS_FPGA_REPO_DIR
$ source sdaccel_setup.sh
$ source /opt/Xilinx/SDx/2018.3.op2405991/settings64.sh
$ source /opt/Xilinx/Vivado/2018.3.op2405991/.settings64-Vivado.sh
```
c. Load the environment variables for TornadoVM

```bash
$ cd /home/centos/repositories/TornadoVM
$ source etc/sources.env

$ tornado --devices
```
### 3. Update the the FPGA Conguration file

Update the `etc/xilinx_fpga.conf` file with the necessary information (i.e. fpga plarform name (DEVICE_NAME), HLS compiler flags (FLAGS), HLS directory (DIRECTORY_BITSTREAM).

```bash
$ vim etc/aws_fpga.conf
```

## Example of configuration file:

```bash
[device]
DEVICE_NAME=/home/centos/src/project_data/aws-fpga/SDAccel/aws_platform/xilinx_aws-vu9p-f1-04261818_dynamic_5_0/xilinx_aws-vu9p-f1-04261818_dynamic_5_0.xpfm
[options]
FLAGS=-O3 -j12
DIRECTORY_BITSTREAM=fpga-source-comp/
```
You can also run TornadoVM with your configuration file, by using the `-Dtornado.fpga.conf.file=FILE` flag.

## Run a program that offloads a task on the FPGA. Be aware to log the terminal output to a file (*_output.log_*),
as the compilation may take a few hours and the connection may be terminated with a broken pipe
(e.g. packet_write_wait: Connection to 174.129.48.160 port 22: Broken pipe).

```bash
$ tornado -Ds0.t0.device=0:0 -Dtornado.fpga.conf.file=/home/centos/TornadoVM/etc/aws_fpga.conf --debug -Xmx20g -Xms20g --printKernel -Dtornado.opencl.accelerator.fpga=true -Dtornado.opencl.userelative=True uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 512 default 1 >> output.log
$ Ctrl-Z (^Z)
$ bg
$ disown
```
Read the *_output.log_* file in order to monitor the outcome of the compilation. At some point the Xilinx binary file will be generated and try to execute with no success as the binary is not registered as an Amazon FPGA Image yet.
(e.g. ERROR: Failed to load xclbin.)

### 4. Exit from root user and return to the centos shell. The following script will start the generation of an Amazon FPGA Image (AFI) associated with the Xilinx binary. This step may take 30 minutes.
Be aware you need to clear the *_to_aws/_* directory.

Follow again steps 2b and 2c this time as centos user, prior to the generation of AFI.

```bash
$ source aws_post_processing.sh
```

You can use the following command to check the state of your Amazon FPGA image.

```bash
$ aws ec2 describe-fpga-images --fpga-image-ids <FPGAImageId>
```

This command will return the following message:
```json
{
    "FpgaImages": [
        {
            "UpdateTime": "2019-09-23T13:06:54.000Z",
            "Name": "vector_addition.hw.xilinx_aws-vu9p-f1-04261818_dynamic_5_0",
            "Tags": [],
            "FpgaImageGlobalId": "agfi-0353e0d0ddf14970c",
            "Public": false,
            "State": {
                "Code": "pending"
            },
            "OwnerId": "305492597385",
            "FpgaImageId": "afi-0011ed541fb695fdd",
            "CreateTime": "2019-09-23T13:06:54.000Z",
            "Description": "vector_addition.hw.xilinx_aws-vu9p-f1-04261818_dynamic_5_0"
        }
    ]
}
```
When the state change from pending to available, the awsxlcbin binary code can be executed via TornadoVM to the AWS FPGA.

### 5. Now that the AFI is available, enter as the root user to a bash shell, and run the program.

```bash
$ sudo -E /bin/bash
$ source etc/sources.env
$ tornado -Ds0.t0.device=0:0 -Dtornado.fpga.conf.file=/home/centos/aws_fpga.conf --debug -Xmx20g -Xms20g --printKernel -Dtornado.opencl.accelerator.fpga=true -Dtornado.opencl.userelative=True uk.ac.manchester.tornado.examples.dynamic.DFTDynamic 512 default 1
```

The output should be like this:

```OpenCL
xclProbe found 1 FPGA slots with xocl driver running
__kernel void lookupBufferAddress(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  _frame[0]  =  (ulong) _heap_base;
}

Initialization time:  1166006276 ns

[DEBUG] JIT compilation for the FPGA
__attribute__((reqd_work_group_size(64,1,1)))
__kernel void computeDft(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  int i_8, i_9, i_38, i_4, i_5, i_37;
  ulong ul_3, ul_19, ul_35, ul_36, ul_23, ul_0, ul_1, ul_2;
  float f_14, f_13, f_12, f_11, f_10, f_7, f_6, f_31, f_30, f_29, f_28, f_27, f_26, f_25, f_24, f_22, f_21, f_20, f_15;
  long l_16, l_32, l_17, l_33, l_18, l_34;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];
  ul_2  =  (ulong) _frame[8];
  ul_3  =  (ulong) _frame[9];
  i_4  =  get_global_id(0);
  // BLOCK 1 MERGES [0 5 ]
  i_5  =  i_4;
  // BLOCK 2
  // BLOCK 3 MERGES [2 4 ]
  f_6  =  0.0F;
  f_7  =  0.0F;
  i_8  =  0;
  #pragma unroll 4
  for(;i_8 < 512;)  {
    // BLOCK 4
    i_9  =  i_8 + 1;
    f_10  =  (float) i_8;
    f_11  =  f_10 * 6.2831855F;
    f_12  =  (float) i_5;
    f_13  =  f_11 * f_12;
    f_14  =  f_13 / 512.0F;
    f_15  =  sin(f_14);
    l_16  =  (long) i_8;
    l_17  =  l_16 << 2;
    l_18  =  l_17 + 24L;
    ul_19  =  ul_0 + l_18;
    f_20  =  *((__global float *) &_heap_base[ul_19]);
    f_21  =  f_15 * f_20;
    f_22  =  cos(f_14);
    ul_23  =  ul_1 + l_18;
    f_24  =  *((__global float *) &_heap_base[ul_23]);
    f_25  =  f_22 * f_24;
    f_26  =  f_21 + f_25;
    f_27  =  f_7 - f_26;
    f_28  =  f_22 * f_20;
    f_29  =  f_15 * f_24;
    f_30  =  f_28 + f_29;
    f_31  =  f_6 + f_30;
    f_6  =  f_31;
    f_7  =  f_27;
    i_8  =  i_9;
  }
  // BLOCK 5
  l_32  =  (long) i_5;
  l_33  =  l_32 << 2;
  l_34  =  l_33 + 24L;
  ul_35  =  ul_2 + l_34;
  *((__global float *) &_heap_base[ul_35])  =  f_6;
  ul_36  =  ul_3 + l_34;
  *((__global float *) &_heap_base[ul_36])  =  f_7;
  i_37  =  get_global_size(0);
  i_38  =  i_37 + i_5;
  i_5  =  i_38;
  // BLOCK 6
  return;
}

INFO: Could not load AFI for data retention, code: 18 - Loading in classic mode.
IOCTL DRM_IOCTL_XOCL_READ_AXLF Failed: 76
ERROR: Failed to load xclbin.
__attribute__((reqd_work_group_size(64,1,1)))
__kernel void computeDft(__global uchar *_heap_base, ulong _frame_base, __constant uchar *_constant_region, __local uchar *_local_region, __global uchar *_private_region)
{
  int i_8, i_9, i_38, i_4, i_5, i_37;
  ulong ul_3, ul_19, ul_35, ul_36, ul_23, ul_0, ul_1, ul_2;
  float f_14, f_13, f_12, f_11, f_10, f_7, f_6, f_31, f_30, f_29, f_28, f_27, f_26, f_25, f_24, f_22, f_21, f_20, f_15;
  long l_16, l_32, l_17, l_33, l_18, l_34;

  __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];


  // BLOCK 0
  ul_0  =  (ulong) _frame[6];
  ul_1  =  (ulong) _frame[7];
  ul_2  =  (ulong) _frame[8];
  ul_3  =  (ulong) _frame[9];
  i_4  =  get_global_id(0);
  // BLOCK 1 MERGES [0 5 ]
  i_5  =  i_4;
  // BLOCK 2
  // BLOCK 3 MERGES [2 4 ]
  f_6  =  0.0F;
  f_7  =  0.0F;
  i_8  =  0;
  #pragma unroll 4
  for(;i_8 < 512;)  {
    // BLOCK 4
    i_9  =  i_8 + 1;
    f_10  =  (float) i_8;
    f_11  =  f_10 * 6.2831855F;
    f_12  =  (float) i_5;
    f_13  =  f_11 * f_12;
    f_14  =  f_13 / 512.0F;
    f_15  =  sin(f_14);
    l_16  =  (long) i_8;
    l_17  =  l_16 << 2;
    l_18  =  l_17 + 24L;
    ul_19  =  ul_0 + l_18;
    f_20  =  *((__global float *) &_heap_base[ul_19]);
    f_21  =  f_15 * f_20;
    f_22  =  cos(f_14);
    ul_23  =  ul_1 + l_18;
    f_24  =  *((__global float *) &_heap_base[ul_23]);
    f_25  =  f_22 * f_24;
    f_26  =  f_21 + f_25;
    f_27  =  f_7 - f_26;
    f_28  =  f_22 * f_20;
    f_29  =  f_15 * f_24;
    f_30  =  f_28 + f_29;
    f_31  =  f_6 + f_30;
    f_6  =  f_31;
    f_7  =  f_27;
    i_8  =  i_9;
  }
  // BLOCK 5
  l_32  =  (long) i_5;
  l_33  =  l_32 << 2;
  l_34  =  l_33 + 24L;
  ul_35  =  ul_2 + l_34;
  *((__global float *) &_heap_base[ul_35])  =  f_6;
  ul_36  =  ul_3 + l_34;
  *((__global float *) &_heap_base[ul_36])  =  f_7;
  i_37  =  get_global_size(0);
  i_38  =  i_37 + i_5;
  i_5  =  i_38;
  // BLOCK 6
  return;
}

INFO: Could not load AFI for data retention, code: 18 - Loading in classic mode.
AFI load complete.
task info: s0.t0
	platform          : Xilinx
	device            : xilinx_aws-vu9p-f1-04261818_dynamic_5_0 CL_DEVICE_TYPE_ACCELERATOR (available)
	dims              : 1
	global work offset: [0]
	global work size  : [512]
	local  work size  : [64]
Total time:  5242666512 ns

Is valid?: true

Validation: SUCCESS
```
