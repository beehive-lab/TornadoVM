.. _cloud:

Cloud Deployments
========================

TornadoVM can be executed on the cloud.
This document explains how to use TornadoVM for running on Amazons AWS instances that contain GPUs or FPGAs.

1. Running on AWS for CPUs and GPUs
-------------------------------------

The installation and execution instructions for running on AWS CPUs and
GPUs is identical to those for running locally.
See the general installation steps here: :ref:`installation`.

2. Running on AWS EC2 F1 Xilinx FPGAs
-------------------------------------

The following toolkit configuration comes with the AWS EC2 F1 instance:

-  FPGA DEV AMI: 1.12.2
-  Xilinx Vitis Tool: 2021.2

Pre-requisites:
~~~~~~~~~~~~~~~

-  You need to install ``python3`` (tested with Python 3.9.6) and ``OpenSSL 1.1.1``.

-  You need to have an S3 storage bucket and permissions to access it (s3_bucket, s3_dcp_key and s3_logs_key) for Step 3.

-  You need to clone the `aws-fpga repository <https://github.com/aws/aws-fpga>`__ (this is the tested `commit point <https://github.com/aws/aws-fpga/commit/863d963308231d0789a48f8840ceb1141368b34a>`_), as follows:

   .. code:: bash

      $ cd /home/centos
      $ git clone https://github.com/aws/aws-fpga.git $AWS_FPGA_REPO_DIR

1. Install TornadoVM with the OpenCL backend, as a CentOS user. The Xilinx FPGA is not exposed to simple users.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   $ git clone https://github.com/beehive-lab/TornadoVM.git
   $ cd TornadoVM
   $ ./bin/tornadovm-installer
   $ source setvars.env

2. Follow these steps to get access to the Xilinx FPGA.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

a. Enter a bash shell as root.

.. code:: bash

   $ sudo -E /bin/bash

**Note: If you face a failure regarding the generation of IP, try the
patch**\ `here <https://support.xilinx.com/s/article/76960?language=en_US>`__\ **.**

b. Load the environment variables for Xilinx HLS and runtime.

.. code:: bash

   $ source $AWS_FPGA_REPO_DIR/vitis_setup.sh
   $ systemctl is-active --quiet mpd || sudo systemctl start mpd

c. Load the environment variables of TornadoVM for root.

.. code:: bash

   $ cd /home/centos/TornadoVM
   $ source setvars.env

   $ tornado --devices

3. Update the the FPGA Conguration file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Update the ``$TORNADO_SDK/etc/xilinx-fpga.conf`` file or create your own
(e.g. ``$TORNADO_SDK/etc/aws-fpga.conf``), and append the necessary
information (i.e. FPGA plarform name (DEVICE_NAME), HLS compiler flags
(FLAGS), HLS directory ( DIRECTORY_BITSTREAM), and AWS S3 configuration
(s3_bucket, s3_dcp_key and s3_loogs_key)).

.. code:: bash

   $ vim $TORNADO_SDK/etc/aws-fpga.conf


Example of configuration file:


.. code:: bash

   [device]
   DEVICE_NAME = /home/centos/src/project_data/aws-fpga/Vitis/aws_platform/xilinx_aws-vu9p-f1_shell-v04261818_201920_3/xilinx_aws-vu9p-f1_shell-v04261818_201920_3.xpfm
   [options]
   COMPILER=v++
   FLAGS = -O3 -j12 # Configure the compilation flags. You can also pass the HLS configuration file (e.g. --config conf.cfg).
   DIRECTORY_BITSTREAM = fpga-source-comp/
   # If the FPGA is in AWS EC2 F1 Instance
   AWS_ENV = yes
   [AWS S3 configuration]
   AWS_S3_BUCKET = tornadovm-fpga-bucket
   AWS_S3_DCP_KEY = outputfolder
   AWS_S3_LOGS_KEY = logfolder

You can run TornadoVM with your configuration file, by using the
``-Dtornado.fpga.conf.file=FILE`` flag. If this flag is not used, the
default configuration file is the ``$TORNADO_SDK/etc/xilinx-fpga.conf``.

4. Run a program that offloads a task on the FPGA.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. figure:: https://user-images.githubusercontent.com/34061419/120612886-519ac700-c45e-11eb-9d6f-45f2aed99d7f.png
   :alt: image

   image

The following example uses a custom configuration file
(``aws-fpga.conf``) to execute the DFT on the AWS F1 FPGA:

.. code:: bash

   $ tornado --jvm "-Ds0.t0.device=0:0 -Dtornado.fpga.conf.file=/home/centos/TornadoVM/bin/sdk/etc/aws-fpga.conf -Xmx20g -Xms20g" --printKernel --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTMT --params="256 default 1" >> output.log
   $ Ctrl-Z (^Z)
   $ bg
   $ disown

This command will trigger TornadoVM to automatically compile Java to
OpenCL and use the AWS FPGA Hardware Development Kit (HDK) to generate a
bitstream. You can also redirect the output from Standard OUT to a file
(``output.log``) as the compilation may take a few hours and the
connection may be terminated with a broken pipe (e.g. packet_write_wait:
Connection to 174.129.48.160 port 22: Broken pipe).

Read the ``output.log`` file in order to monitor the outcome of the
TornadoVM execution. To monitor the outcome of the HLS compilation, read
the ``outputFPGA.log`` file, which is automatically generated in the
``DIRECTORY_BITSTREAM`` ( e.g. ``fpga-source-comp``). After the
bitstream generation, TornadoVM will automatically invoke the creation
of an Amazon FPGA Image (AFI) and upload a file related to the kernel to
the Amazon S3 bucket (configured in the Step 3). The execution of the
program will end up with an error as the bitstream is forwarded to be
used, while the AFI image is not ready yet. E.g.:

.. code:: bash

   [TornadoVM-OCL-JNI] ERROR : clCreateProgramWithBinary -> Returned: -44

5. You can monitor the status of your Amazon FPGA Image.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Instructions are given in ``outputFPGA.log``. Ensure that you use the
correct ``FPGAImageId`` (e.g. ``afi-0c1bb6821ccc766fe``) .

.. code:: bash

   $ cat fpga-source-comp/outputFPGA.log
   $ aws ec2 describe-fpga-images --fpga-image-ids afi-0c1bb6821ccc766fe

This command will return the following message:

.. code:: json

   {
     "FpgaImages": [
       {
         "UpdateTime": "2021-05-27T23:55:15.000Z",
         "Name": "lookupBufferAddress",
         "Tags": [],
         "PciId": {
           "SubsystemVendorId": "0xfedd",
           "VendorId": "0x1d0f",
           "DeviceId": "0xf010",
           "SubsystemId": "0x1d51"
         },
         "FpgaImageGlobalId": "agfi-045c5d8825f920edc",
         "Public": false,
         "State": {
           "Code": "pending"
         },
         "ShellVersion": "0x04261818",
         "OwnerId": "813381863415",
         "FpgaImageId": "afi-0c1bb6821ccc766fe",
         "CreateTime": "2021-05-27T23:15:21.000Z",
         "Description": "lookupBufferAddress"
       }
     ]
   }

When the state changes from ``pending`` to ``available``, the
``awsxlcbin`` binary code can be executed via TornadoVM to the AWS FPGA.

6. Now that the AFI is available, you can execute the program and run the OpenCL kernel on the AWS FPGA.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you have logged out, ensure that you run (Steps 2 and 4).

.. code:: bash

   $ tornado --jvm="-Ds0.t0.device=0:0 -Dtornado.fpga.conf.file=/home/centos/TornadoVM/etc/aws-fpga.conf -Xmx20g -Xms20g" --debug --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTMT --params="256 default 1" >> output.log

The result is the following:

.. code:: bash

   tornado --jvm="-Ds0.t0.device=0:0 -Dtornado.fpga.conf.file=/home/centos/TornadoVM-Internal-feat-removeBufferCache/etc/aws-fpga.conf --threadInfo -Xmx20g -Xms20g" --printKernel -m tornado.examples/uk.ac.manchester.tornado.examples.dynamic.DFTMT --parms "256 default 1"
   Initialization time:  705795966 ns

.. code:: c

   __attribute__((reqd_work_group_size(64, 1, 1)))
   __kernel void computeDft(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global int *_atomics, __global uchar *inreal, __global uchar *inimag, __global uchar *outreal, __global uchar *outimag, __global uchar *inputSize)
   {
     int i_8, i_29, i_35, i_5, i_4, i_36;
     float f_6, f_7, f_24, f_25, f_26, f_27, f_28, f_16, f_17, f_18, f_19, f_20, f_21, f_22, f_23, f_13, f_15;
     ulong ul_12, ul_3, ul_2, ul_34, ul_14, ul_1, ul_33, ul_0;
     long l_9, l_10, l_11, l_30, l_31, l_32;

     // BLOCK 0
     ul_0  =  (ulong) inreal;
     ul_1  =  (ulong) inimag;
     ul_2  =  (ulong) outreal;
     ul_3  =  (ulong) outimag;
     i_4  =  get_global_id(0);
     // BLOCK 1 MERGES [0 5 ]
     i_5  =  i_4;
     // BLOCK 2
     // BLOCK 3 MERGES [2 4 ]
     f_6  =  0.0F;
     f_7  =  0.0F;
     i_8  =  0;
     __attribute__((xcl_pipeline_loop(1)))
     for(;i_8 < 256;)
     {
       // BLOCK 4
       l_9  =  (long) i_8;
       l_10  =  l_9 << 2;
       l_11  =  l_10 + 24L;
       ul_12  =  ul_0 + l_11;
       f_13  =  *((__global float *) ul_12);
       ul_14  =  ul_1 + l_11;
       f_15  =  *((__global float *) ul_14);
       f_16  =  (float) i_8;
       f_17  =  f_16 * 6.2831855F;
       f_18  =  (float) i_5;
       f_19  =  f_17 * f_18;
       f_20  =  f_19 / 256.0F;
       f_21  =  native_sin(f_20);
       f_22  =  native_cos(f_20);
       f_23  =  f_22 * f_15;
       f_24  =  fma(f_21, f_13, f_23);
       f_25  =  f_7 - f_24;
       f_26  =  f_21 * f_15;
       f_27  =  fma(f_22, f_13, f_26);
       f_28  =  f_6 + f_27;
       i_29  =  i_8 + 1;
       f_6  =  f_28;
       f_7  =  f_25;
       i_8  =  i_29;
     }  // B4
     // BLOCK 5
     l_30  =  (long) i_5;
     l_31  =  l_30 << 2;
     l_32  =  l_31 + 24L;
     ul_33  =  ul_2 + l_32;
     *((__global float *) ul_33)  =  f_6;
     ul_34  =  ul_3 + l_32;
     *((__global float *) ul_34)  =  f_7;
     i_35  =  get_global_size(0);
     i_36  =  i_35 + i_5;
     i_5  =  i_36;
     // BLOCK 6
     return;
   }  //  kernel

   Task info: s0.t0
       Backend           : OPENCL
       Device            : xilinx_aws-vu9p-f1_shell-v04261818_201920_2 CL_DEVICE_TYPE_ACCELERATOR (available)
       Dims              : 1
       Global work offset: [0]
       Global work size  : [256]
       Local  work size  : [64, 1, 1]
       Number of workgroups  : [4]

   Total time:  4532676526 ns

   Is valid?: true

   Validation: SUCCESS
