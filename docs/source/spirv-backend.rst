\SPIR-V Devices
====================================

SPIR-V makes use of the `Intel Level Zero API <https://spec.oneapi.io/level-zero/latest/index.html>`__.

**Disclaimer:** The SPIR-V backend with the Intel Level-Zero dispatcher is a new project within TornadoVM. Currently, we offer a preview and an
initial implementation.

Install Intel oneAPI Level Zero Compute Runtime
--------------------------------------------------

In order to use Intel Level Zero from oneAPI, you need to install the Intel driver for the Intel HD Graphics.

All drivers are available here: `https://github.com/intel/compute-runtime/releases <https://github.com/intel/compute-runtime/releases>`_.

Install TornadoVM for SPIR-V
-----------------------------

Install TornadoVM following the instructions in :ref:`installation`.

To build the SPIR-V Backend, enable the backend as follows:

.. code:: bash

   $ cd <tornadovm-directory>
   $ ./bin/tornadovm-installer
   $ Select the backend(s) to install:
   $   1. opencl
   $   2. spirv
   $   3. ptx
   $ You can select more than one by typing the numbers separated by commas (e.g., 1, 2, 3).
   $ Your selection: 2
   $ . setvars.sh

Running examples with the SPIR-V backend
------------------------------------------

Running DFT from the unit-test suite
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code:: bash

   $ tornado-test -V --fast --threadInfo --debug uk.ac.manchester.tornado.unittests.compute.ComputeTests#testDFT

   SPIRV-File : /tmp/tornadoVM-spirv/8442884346950-s0.t0computeDFT.spv
   Set entry point: computeDFT
   Task info: s0.t0
       Backend           : SPIRV
       Device            : SPIRV LevelZero - Intel(R) UHD Graphics [0x9bc4] GPU
       Dims              : 1
       Global work offset: [0]
       Global work size  : [4096]
       Local  work size  : [256, 1, 1]
       Number of workgroups  : [16]

   Test: class uk.ac.manchester.tornado.unittests.compute.ComputeTests#testDFT
       Running test: testDFT                    ................  [PASS]

In this execution, the SPIR-V Binary is stored in ``/tmp/tornadoVM-spirv/8442884346950-s0.t0computeDFT.spv``.
We can disassemble the binary with ``spirv-dis`` `from Khronos <https://github.com/KhronosGroup/SPIRV-Tools>`__

Note: Usually, ``spirv-dis`` can be installed from the common OS repositories (e.g., Fedora, Ubuntu repositories):

.. code:: bash

   ## Fedora OS
   sudo dnf install spirv-tools

   ## Ubuntu OS:
   sudo apt-get install spirv-tools


TornadoVM/Java Options for SPIR-V:
''''''''''''''''''''''''''''''

- ``-Dtornado.spirv.version=1.2``: Modify the minimum version supported. By default is 1.2. However, developers can change this value. Note that the generated code might not work, as TornadoVM requires at least 1.2.

- ``-Dtornado.spirv.runtimes=opencl,levelzero``: It sets the list of supported runtimes to dispatch SPIR-V. Allowed values are: ``opencl`` and ``levelzero``. They are separated by a comma, and the first in the list is taken as default. 

- ``-Dtornado.spirv.levelzero.extended.memory=True``: It uses Level Zero extended memory mode. It is set to ``true`` by default.



Disassemble the SPIR-V binary:
''''''''''''''''''''''''''''''

.. code:: none

   $ spirv-dis /tmp/tornadoVM-spirv/8442884346950-s0.t0computeDFT.spv
   ; SPIR-V
   ; Version: 1.2
   ; Generator: Khronos; 32
   ; Bound: 227
   ; Schema: 0
                  OpCapability Addresses
                  OpCapability Linkage
                  OpCapability Kernel
                  OpCapability Int64
                  OpCapability Int8
                  OpCapability Float64
             %1 = OpExtInstImport "OpenCL.std"
                  OpMemoryModel Physical64 OpenCL
                  OpEntryPoint Kernel %56 "computeDFT" %spirv_BuiltInGlobalInvocationId %spirv_BuiltInGlobalSize
                  OpExecutionMode %56 ContractionOff
                  OpSource OpenCL_C 300000
                  OpName %spirv_BuiltInGlobalInvocationId "spirv_BuiltInGlobalInvocationId"
                  OpName %spirv_BuiltInGlobalSize "spirv_BuiltInGlobalSize"
                  OpName %spirv_l_16F0 "spirv_l_16F0"
                  OpName %spirv_l_12F0 "spirv_l_12F0"
                  OpName %spirv_l_44F0 "spirv_l_44F0"
                  OpName %spirv_l_13F0 "spirv_l_13F0"
                  OpName %spirv_l_45F0 "spirv_l_45F0"
                  OpName %spirv_l_14F0 "spirv_l_14F0"
                  OpName %spirv_l_46F0 "spirv_l_46F0"
                  OpName %spirv_l_42F0 "spirv_l_42F0"
                  OpName %spirv_l_11F0 "spirv_l_11F0"
                  OpName %spirv_l_43F0 "spirv_l_43F0"
                  OpName %spirv_l_0F0 "spirv_l_0F0"
                  OpName %spirv_l_1F0 "spirv_l_1F0"
                  OpName %spirv_l_2F0 "spirv_l_2F0"
                  OpName %spirv_l_3F0 "spirv_l_3F0"
                  OpName %spirv_i_5F0 "spirv_i_5F0"
                  OpName %spirv_i_4F0 "spirv_i_4F0"
                  OpName %spirv_i_48F0 "spirv_i_48F0"
                  OpName %spirv_i_47F0 "spirv_i_47F0"
                  OpName %spirv_i_9F0 "spirv_i_9F0"
                  OpName %spirv_i_41F0 "spirv_i_41F0"
                  OpName %spirv_f_15F0 "spirv_f_15F0"
                  OpName %spirv_f_17F0 "spirv_f_17F0"
                  OpName %spirv_f_34F0 "spirv_f_34F0"
                  OpName %spirv_f_7F0 "spirv_f_7F0"
                  OpName %spirv_f_23F0 "spirv_f_23F0"
                  OpName %spirv_f_8F0 "spirv_f_8F0"
                  OpName %spirv_f_40F0 "spirv_f_40F0"
                  OpName %spirv_f_26F0 "spirv_f_26F0"
                  OpName %spirv_d_20F0 "spirv_d_20F0"
                  OpName %spirv_d_19F0 "spirv_d_19F0"
                  OpName %spirv_d_22F0 "spirv_d_22F0"
                  OpName %spirv_d_21F0 "spirv_d_21F0"
                  OpName %spirv_d_24F0 "spirv_d_24F0"
                  OpName %spirv_d_25F0 "spirv_d_25F0"
                  OpName %spirv_d_28F0 "spirv_d_28F0"
                  OpName %spirv_d_27F0 "spirv_d_27F0"
                  OpName %spirv_d_30F0 "spirv_d_30F0"
                  OpName %spirv_d_29F0 "spirv_d_29F0"
                  OpName %spirv_d_32F0 "spirv_d_32F0"
                  OpName %spirv_d_31F0 "spirv_d_31F0"
                  OpName %spirv_d_33F0 "spirv_d_33F0"
                  OpName %spirv_d_36F0 "spirv_d_36F0"
                  OpName %spirv_d_35F0 "spirv_d_35F0"
                  OpName %spirv_d_38F0 "spirv_d_38F0"
                  OpName %spirv_d_37F0 "spirv_d_37F0"
                  OpName %spirv_d_39F0 "spirv_d_39F0"
                  OpName %spirv_d_18F0 "spirv_d_18F0"
                  OpName %spirv_z_10F0 "spirv_z_10F0"
                  OpName %spirv_z_6F0 "spirv_z_6F0"
                  OpName %heapBaseAddr "heapBaseAddr"
                  OpName %frameBaseAddr "frameBaseAddr"
                  OpName %frame "frame"
                  OpName %B0F0 "B0F0"
                  OpName %B1F0 "B1F0"
                  OpName %B2F0 "B2F0"
                  OpName %B6F0 "B6F0"
                  OpName %B3F0 "B3F0"
                  OpName %B4F0 "B4F0"
                  OpName %B5F0 "B5F0"
                  OpName %returnF0 "returnF0"
                  OpDecorate %spirv_BuiltInGlobalInvocationId BuiltIn GlobalInvocationId
                  OpDecorate %spirv_BuiltInGlobalInvocationId Constant
                  OpDecorate %spirv_BuiltInGlobalInvocationId LinkageAttributes "spirv_BuiltInGlobalInvocationId" Import
                  OpDecorate %spirv_BuiltInGlobalSize BuiltIn GlobalSize
                  OpDecorate %spirv_BuiltInGlobalSize Constant
                  OpDecorate %spirv_BuiltInGlobalSize LinkageAttributes "spirv_BuiltInGlobalSize" Import
                  OpDecorate %heapBaseAddr Alignment 8
                  OpDecorate %frameBaseAddr Alignment 8
                  OpDecorate %frame Alignment 8
                  OpDecorate %spirv_l_16F0 Alignment 8
                  OpDecorate %spirv_l_12F0 Alignment 8
                  OpDecorate %spirv_l_44F0 Alignment 8
                  OpDecorate %spirv_l_13F0 Alignment 8
                  OpDecorate %spirv_l_45F0 Alignment 8
                  OpDecorate %spirv_l_14F0 Alignment 8
                  OpDecorate %spirv_l_46F0 Alignment 8
                  OpDecorate %spirv_l_42F0 Alignment 8
                  OpDecorate %spirv_l_11F0 Alignment 8
                  OpDecorate %spirv_l_43F0 Alignment 8
                  OpDecorate %spirv_l_0F0 Alignment 8
                  OpDecorate %spirv_l_1F0 Alignment 8
                  OpDecorate %spirv_l_2F0 Alignment 8
                  OpDecorate %spirv_l_3F0 Alignment 8
                  OpDecorate %spirv_i_5F0 Alignment 4
                  OpDecorate %spirv_i_4F0 Alignment 4
                  OpDecorate %spirv_i_48F0 Alignment 4
                  OpDecorate %spirv_i_47F0 Alignment 4
                  OpDecorate %spirv_i_9F0 Alignment 4
                  OpDecorate %spirv_i_41F0 Alignment 4
                  OpDecorate %spirv_f_15F0 Alignment 4
                  OpDecorate %spirv_f_17F0 Alignment 4
                  OpDecorate %spirv_f_34F0 Alignment 4
                  OpDecorate %spirv_f_7F0 Alignment 4
                  OpDecorate %spirv_f_23F0 Alignment 4
                  OpDecorate %spirv_f_8F0 Alignment 4
                  OpDecorate %spirv_f_40F0 Alignment 4
                  OpDecorate %spirv_f_26F0 Alignment 4
                  OpDecorate %spirv_d_20F0 Alignment 8
                  OpDecorate %spirv_d_19F0 Alignment 8
                  OpDecorate %spirv_d_22F0 Alignment 8
                  OpDecorate %spirv_d_21F0 Alignment 8
                  OpDecorate %spirv_d_24F0 Alignment 8
                  OpDecorate %spirv_d_25F0 Alignment 8
                  OpDecorate %spirv_d_28F0 Alignment 8
                  OpDecorate %spirv_d_27F0 Alignment 8
                  OpDecorate %spirv_d_30F0 Alignment 8
                  OpDecorate %spirv_d_29F0 Alignment 8
                  OpDecorate %spirv_d_32F0 Alignment 8
                  OpDecorate %spirv_d_31F0 Alignment 8
                  OpDecorate %spirv_d_33F0 Alignment 8
                  OpDecorate %spirv_d_36F0 Alignment 8
                  OpDecorate %spirv_d_35F0 Alignment 8
                  OpDecorate %spirv_d_38F0 Alignment 8
                  OpDecorate %spirv_d_37F0 Alignment 8
                  OpDecorate %spirv_d_39F0 Alignment 8
                  OpDecorate %spirv_d_18F0 Alignment 8
                  OpDecorate %spirv_z_10F0 Alignment 1
                  OpDecorate %spirv_z_6F0 Alignment 1
         %uchar = OpTypeInt 8 0
         %ulong = OpTypeInt 64 0
          %uint = OpTypeInt 32 0
         %float = OpTypeFloat 32
        %double = OpTypeFloat 64
          %bool = OpTypeBool
        %uint_3 = OpConstant %uint 3
      %ulong_24 = OpConstant %ulong 24
        %uint_2 = OpConstant %uint 2
   %double_4096 = OpConstant %double 4096
     %uint_4096 = OpConstant %uint 4096
        %uint_1 = OpConstant %uint 1
   %double_6_2831853071795862 = OpConstant %double 6.2831853071795862
       %float_0 = OpConstant %float 0
        %uint_0 = OpConstant %uint 0
          %void = OpTypeVoid
   %_ptr_CrossWorkgroup_uchar = OpTypePointer CrossWorkgroup %uchar
            %74 = OpTypeFunction %void %_ptr_CrossWorkgroup_uchar %ulong
   %_ptr_Function__ptr_CrossWorkgroup_uchar = OpTypePointer Function %_ptr_CrossWorkgroup_uchar
   %_ptr_CrossWorkgroup_ulong = OpTypePointer CrossWorkgroup %ulong
   %_ptr_Function_ulong = OpTypePointer Function %ulong
   %_ptr_Function__ptr_CrossWorkgroup_ulong = OpTypePointer Function %_ptr_CrossWorkgroup_ulong
       %v3ulong = OpTypeVector %ulong 3
   %_ptr_Input_v3ulong = OpTypePointer Input %v3ulong
   %spirv_BuiltInGlobalSize = OpVariable %_ptr_Input_v3ulong Input
   %spirv_BuiltInGlobalInvocationId = OpVariable %_ptr_Input_v3ulong Input
   %_ptr_Function_uint = OpTypePointer Function %uint
   %_ptr_Function_float = OpTypePointer Function %float
   %_ptr_Function_double = OpTypePointer Function %double
   %_ptr_Function_bool = OpTypePointer Function %bool
        %uint_4 = OpConstant %uint 4
        %uint_5 = OpConstant %uint 5
        %uint_6 = OpConstant %uint 6
       %ulong_2 = OpConstant %ulong 2
   %_ptr_CrossWorkgroup_float = OpTypePointer CrossWorkgroup %float
            %56 = OpFunction %void DontInline %74
            %81 = OpFunctionParameter %_ptr_CrossWorkgroup_uchar
            %82 = OpFunctionParameter %ulong
          %B0F0 = OpLabel
   %heapBaseAddr = OpVariable %_ptr_Function__ptr_CrossWorkgroup_uchar Function
   %frameBaseAddr = OpVariable %_ptr_Function_ulong Function
   %spirv_l_16F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_12F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_44F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_13F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_45F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_14F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_46F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_42F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_11F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_43F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_0F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_1F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_2F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_l_3F0 = OpVariable %_ptr_Function_ulong Function
   %spirv_i_5F0 = OpVariable %_ptr_Function_uint Function
   %spirv_i_4F0 = OpVariable %_ptr_Function_uint Function
   %spirv_i_48F0 = OpVariable %_ptr_Function_uint Function
   %spirv_i_47F0 = OpVariable %_ptr_Function_uint Function
   %spirv_i_9F0 = OpVariable %_ptr_Function_uint Function
   %spirv_i_41F0 = OpVariable %_ptr_Function_uint Function
   %spirv_f_15F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_17F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_34F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_7F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_23F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_8F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_40F0 = OpVariable %_ptr_Function_float Function
   %spirv_f_26F0 = OpVariable %_ptr_Function_float Function
   %spirv_d_20F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_19F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_22F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_21F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_24F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_25F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_28F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_27F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_30F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_29F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_32F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_31F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_33F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_36F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_35F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_38F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_37F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_39F0 = OpVariable %_ptr_Function_double Function
   %spirv_d_18F0 = OpVariable %_ptr_Function_double Function
   %spirv_z_10F0 = OpVariable %_ptr_Function_bool Function
   %spirv_z_6F0 = OpVariable %_ptr_Function_bool Function
         %frame = OpVariable %_ptr_Function__ptr_CrossWorkgroup_ulong Function
                  OpStore %heapBaseAddr %81 Aligned 8
                  OpStore %frameBaseAddr %82 Aligned 8
            %88 = OpLoad %_ptr_CrossWorkgroup_uchar %heapBaseAddr Aligned 8
            %89 = OpLoad %ulong %frameBaseAddr Aligned 8
            %90 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_uchar %88 %89
            %91 = OpBitcast %_ptr_CrossWorkgroup_ulong %90
                  OpStore %frame %91 Aligned 8
            %92 = OpLoad %_ptr_CrossWorkgroup_ulong %frame Aligned 8
            %93 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_ulong %92 %uint_3
            %94 = OpLoad %ulong %93 Aligned 8
                  OpStore %spirv_l_0F0 %94 Aligned 8
            %95 = OpLoad %_ptr_CrossWorkgroup_ulong %frame Aligned 8
            %97 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_ulong %95 %uint_4
            %98 = OpLoad %ulong %97 Aligned 8
                  OpStore %spirv_l_1F0 %98 Aligned 8
            %99 = OpLoad %_ptr_CrossWorkgroup_ulong %frame Aligned 8
           %101 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_ulong %99 %uint_5
           %102 = OpLoad %ulong %101 Aligned 8
                  OpStore %spirv_l_2F0 %102 Aligned 8
           %103 = OpLoad %_ptr_CrossWorkgroup_ulong %frame Aligned 8
           %105 = OpInBoundsPtrAccessChain %_ptr_CrossWorkgroup_ulong %103 %uint_6
           %106 = OpLoad %ulong %105 Aligned 8
                  OpStore %spirv_l_3F0 %106 Aligned 8
           %107 = OpLoad %v3ulong %spirv_BuiltInGlobalInvocationId Aligned 32
           %108 = OpCompositeExtract %ulong %107 0
           %109 = OpUConvert %uint %108
                  OpStore %spirv_i_4F0 %109 Aligned 4
           %110 = OpLoad %uint %spirv_i_4F0 Aligned 4
                  OpStore %spirv_i_5F0 %110 Aligned 4
                  OpBranch %B1F0
          %B1F0 = OpLabel
           %112 = OpLoad %uint %spirv_i_5F0 Aligned 4
           %113 = OpSLessThan %bool %112 %uint_4096
                  OpBranchConditional %113 %B2F0 %B6F0
          %B2F0 = OpLabel
                  OpStore %spirv_f_7F0 %float_0 Aligned 4
                  OpStore %spirv_f_8F0 %float_0 Aligned 4
                  OpStore %spirv_i_9F0 %uint_0 Aligned 4
                  OpBranch %B3F0
          %B3F0 = OpLabel
           %117 = OpLoad %uint %spirv_i_9F0 Aligned 4
           %118 = OpSLessThan %bool %117 %uint_4096
                  OpBranchConditional %118 %B4F0 %B5F0
          %B4F0 = OpLabel
           %121 = OpLoad %uint %spirv_i_9F0 Aligned 4
           %122 = OpSConvert %ulong %121
                  OpStore %spirv_l_11F0 %122 Aligned 8
           %123 = OpLoad %ulong %spirv_l_11F0 Aligned 8
           %125 = OpShiftLeftLogical %ulong %123 %ulong_2
                  OpStore %spirv_l_12F0 %125 Aligned 8
           %126 = OpLoad %ulong %spirv_l_12F0 Aligned 8
           %127 = OpIAdd %ulong %126 %ulong_24
                  OpStore %spirv_l_13F0 %127 Aligned 8
           %128 = OpLoad %ulong %spirv_l_0F0 Aligned 8
           %129 = OpLoad %ulong %spirv_l_13F0 Aligned 8
           %130 = OpIAdd %ulong %128 %129
                  OpStore %spirv_l_14F0 %130 Aligned 8
           %131 = OpLoad %ulong %spirv_l_14F0 Aligned 8
           %133 = OpConvertUToPtr %_ptr_CrossWorkgroup_float %131
           %134 = OpLoad %float %133 Aligned 4
                  OpStore %spirv_f_15F0 %134 Aligned 4
           %135 = OpLoad %ulong %spirv_l_1F0 Aligned 8
           %136 = OpLoad %ulong %spirv_l_13F0 Aligned 8
           %137 = OpIAdd %ulong %135 %136
                  OpStore %spirv_l_16F0 %137 Aligned 8
           %138 = OpLoad %ulong %spirv_l_16F0 Aligned 8
           %139 = OpConvertUToPtr %_ptr_CrossWorkgroup_float %138
           %140 = OpLoad %float %139 Aligned 4
                  OpStore %spirv_f_17F0 %140 Aligned 4
           %141 = OpLoad %uint %spirv_i_9F0 Aligned 8
           %142 = OpConvertSToF %double %141
                  OpStore %spirv_d_18F0 %142 Aligned 8
           %143 = OpLoad %double %spirv_d_18F0 Aligned 8
           %144 = OpFMul %double %143 %double_6_2831853071795862
                  OpStore %spirv_d_19F0 %144 Aligned 8
           %145 = OpLoad %uint %spirv_i_5F0 Aligned 8
           %146 = OpConvertSToF %double %145
                  OpStore %spirv_d_20F0 %146 Aligned 8
           %147 = OpLoad %double %spirv_d_19F0 Aligned 8
           %148 = OpLoad %double %spirv_d_20F0 Aligned 8
           %149 = OpFMul %double %147 %148
                  OpStore %spirv_d_21F0 %149 Aligned 8
           %150 = OpLoad %double %spirv_d_21F0 Aligned 8
           %151 = OpFDiv %double %150 %double_4096
                  OpStore %spirv_d_22F0 %151 Aligned 8
           %152 = OpLoad %double %spirv_d_22F0 Aligned 4
           %153 = OpFConvert %float %152
                  OpStore %spirv_f_23F0 %153 Aligned 4
           %154 = OpLoad %float %spirv_f_23F0 Aligned 8
           %155 = OpFConvert %double %154
                  OpStore %spirv_d_24F0 %155 Aligned 8
           %156 = OpLoad %double %spirv_d_24F0 Aligned 8
           %157 = OpExtInst %double %1 sin %156
                  OpStore %spirv_d_25F0 %157 Aligned 8
           %158 = OpLoad %float %spirv_f_15F0 Aligned 4
           %159 = OpFNegate %float %158
                  OpStore %spirv_f_26F0 %159 Aligned 4
           %160 = OpLoad %float %spirv_f_26F0 Aligned 8
           %161 = OpFConvert %double %160
                  OpStore %spirv_d_27F0 %161 Aligned 8
           %162 = OpLoad %double %spirv_d_24F0 Aligned 8
           %163 = OpExtInst %double %1 native_cos %162
                  OpStore %spirv_d_28F0 %163 Aligned 8
           %164 = OpLoad %float %spirv_f_17F0 Aligned 8
           %165 = OpFConvert %double %164
                  OpStore %spirv_d_29F0 %165 Aligned 8
           %166 = OpLoad %double %spirv_d_28F0 Aligned 8
           %167 = OpLoad %double %spirv_d_29F0 Aligned 8
           %168 = OpFMul %double %166 %167
                  OpStore %spirv_d_30F0 %168 Aligned 8
           %169 = OpLoad %double %spirv_d_25F0 Aligned 8
           %170 = OpLoad %double %spirv_d_27F0 Aligned 8
           %171 = OpLoad %double %spirv_d_30F0 Aligned 8
           %172 = OpExtInst %double %1 fma %169 %170 %171
                  OpStore %spirv_d_31F0 %172 Aligned 8
           %173 = OpLoad %float %spirv_f_8F0 Aligned 8
           %174 = OpFConvert %double %173
                  OpStore %spirv_d_32F0 %174 Aligned 8
           %175 = OpLoad %double %spirv_d_31F0 Aligned 8
           %176 = OpLoad %double %spirv_d_32F0 Aligned 8
           %177 = OpFAdd %double %175 %176
                  OpStore %spirv_d_33F0 %177 Aligned 8
           %178 = OpLoad %double %spirv_d_33F0 Aligned 4
           %179 = OpFConvert %float %178
                  OpStore %spirv_f_34F0 %179 Aligned 4
           %180 = OpLoad %float %spirv_f_15F0 Aligned 8
           %181 = OpFConvert %double %180
                  OpStore %spirv_d_35F0 %181 Aligned 8
           %182 = OpLoad %double %spirv_d_25F0 Aligned 8
           %183 = OpLoad %double %spirv_d_29F0 Aligned 8
           %184 = OpFMul %double %182 %183
                  OpStore %spirv_d_36F0 %184 Aligned 8
           %185 = OpLoad %double %spirv_d_28F0 Aligned 8
           %186 = OpLoad %double %spirv_d_35F0 Aligned 8
           %187 = OpLoad %double %spirv_d_36F0 Aligned 8
           %188 = OpExtInst %double %1 fma %185 %186 %187
                  OpStore %spirv_d_37F0 %188 Aligned 8
           %189 = OpLoad %float %spirv_f_7F0 Aligned 8
           %190 = OpFConvert %double %189
                  OpStore %spirv_d_38F0 %190 Aligned 8
           %191 = OpLoad %double %spirv_d_37F0 Aligned 8
           %192 = OpLoad %double %spirv_d_38F0 Aligned 8
           %193 = OpFAdd %double %191 %192
                  OpStore %spirv_d_39F0 %193 Aligned 8
           %194 = OpLoad %double %spirv_d_39F0 Aligned 4
           %195 = OpFConvert %float %194
                  OpStore %spirv_f_40F0 %195 Aligned 4
           %196 = OpLoad %uint %spirv_i_9F0 Aligned 4
           %197 = OpIAdd %uint %196 %uint_1
                  OpStore %spirv_i_41F0 %197 Aligned 4
           %198 = OpLoad %float %spirv_f_40F0 Aligned 4
                  OpStore %spirv_f_7F0 %198 Aligned 4
           %199 = OpLoad %float %spirv_f_34F0 Aligned 4
                  OpStore %spirv_f_8F0 %199 Aligned 4
           %200 = OpLoad %uint %spirv_i_41F0 Aligned 4
                  OpStore %spirv_i_9F0 %200 Aligned 4
                  OpBranch %B3F0
          %B5F0 = OpLabel
           %201 = OpLoad %uint %spirv_i_5F0 Aligned 4
           %202 = OpSConvert %ulong %201
                  OpStore %spirv_l_42F0 %202 Aligned 8
           %203 = OpLoad %ulong %spirv_l_42F0 Aligned 8
           %204 = OpShiftLeftLogical %ulong %203 %ulong_2
                  OpStore %spirv_l_43F0 %204 Aligned 8
           %205 = OpLoad %ulong %spirv_l_43F0 Aligned 8
           %206 = OpIAdd %ulong %205 %ulong_24
                  OpStore %spirv_l_44F0 %206 Aligned 8
           %207 = OpLoad %ulong %spirv_l_2F0 Aligned 8
           %208 = OpLoad %ulong %spirv_l_44F0 Aligned 8
           %209 = OpIAdd %ulong %207 %208
                  OpStore %spirv_l_45F0 %209 Aligned 8
           %210 = OpLoad %ulong %spirv_l_45F0 Aligned 8
           %211 = OpConvertUToPtr %_ptr_CrossWorkgroup_float %210
           %212 = OpLoad %float %spirv_f_7F0 Aligned 4
                  OpStore %211 %212 Aligned 4
           %213 = OpLoad %ulong %spirv_l_3F0 Aligned 8
           %214 = OpLoad %ulong %spirv_l_44F0 Aligned 8
           %215 = OpIAdd %ulong %213 %214
                  OpStore %spirv_l_46F0 %215 Aligned 8
           %216 = OpLoad %ulong %spirv_l_46F0 Aligned 8
           %217 = OpConvertUToPtr %_ptr_CrossWorkgroup_float %216
           %218 = OpLoad %float %spirv_f_8F0 Aligned 4
                  OpStore %217 %218 Aligned 4
           %219 = OpLoad %v3ulong %spirv_BuiltInGlobalSize Aligned 32
           %220 = OpCompositeExtract %ulong %219 0
           %221 = OpUConvert %uint %220
                  OpStore %spirv_i_47F0 %221 Aligned 4
           %222 = OpLoad %uint %spirv_i_47F0 Aligned 4
           %223 = OpLoad %uint %spirv_i_5F0 Aligned 4
           %224 = OpIAdd %uint %222 %223
                  OpStore %spirv_i_48F0 %224 Aligned 4
           %225 = OpLoad %uint %spirv_i_48F0 Aligned 4
                  OpStore %spirv_i_5F0 %225 Aligned 4
                  OpBranch %B1F0
          %B6F0 = OpLabel
                  OpBranch %returnF0
      %returnF0 = OpLabel
                  OpReturn
                  OpFunctionEnd
