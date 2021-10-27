/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class Ze_Structure_Type {

    public static final int ZE_STRUCTURE_TYPE_DRIVER_PROPERTIES = 0x1; /// < ::ze_driver_properties_t
    public static final int ZE_STRUCTURE_TYPE_DRIVER_IPC_PROPERTIES = 0x2; /// < ::ze_driver_ipc_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES = 0x3; /// < ::ze_device_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_COMPUTE_PROPERTIES = 0x4; /// < ::ze_device_compute_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_MODULE_PROPERTIES = 0x5; /// < ::ze_device_module_properties_t
    public static final int ZE_STRUCTURE_TYPE_COMMAND_QUEUE_GROUP_PROPERTIES = 0x6; /// < ::ze_command_queue_group_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_MEMORY_PROPERTIES = 0x7; /// < ::ze_device_memory_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_MEMORY_ACCESS_PROPERTIES = 0x8;/// < ::ze_device_memory_access_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_CACHE_PROPERTIES = 0x9;/// < ::ze_device_cache_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_IMAGE_PROPERTIES = 0xa;/// < ::ze_device_image_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_P2P_PROPERTIES = 0xb; /// < ::ze_device_p2p_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_EXTERNAL_MEMORY_PROPERTIES = 0xc; /// < ::ze_device_external_memory_properties_t
    public static final int ZE_STRUCTURE_TYPE_CONTEXT_DESC = 0xd; /// < ::ze_context_desc_t
    public static final int ZE_STRUCTURE_TYPE_COMMAND_QUEUE_DESC = 0xe; /// < ::ze_command_queue_desc_t
    public static final int ZE_STRUCTURE_TYPE_COMMAND_LIST_DESC = 0xf; /// < ::ze_command_list_desc_t
    public static final int ZE_STRUCTURE_TYPE_EVENT_POOL_DESC = 0x10; /// < ::ze_event_pool_desc_t
    public static final int ZE_STRUCTURE_TYPE_EVENT_DESC = 0x11; /// < ::ze_event_desc_t
    public static final int ZE_STRUCTURE_TYPE_FENCE_DESC = 0x12; /// < ::ze_fence_desc_t
    public static final int ZE_STRUCTURE_TYPE_IMAGE_DESC = 0x13; /// < ::ze_image_desc_t
    public static final int ZE_STRUCTURE_TYPE_IMAGE_PROPERTIES = 0x14; /// < ::ze_image_properties_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_MEM_ALLOC_DESC = 0x15; /// < ::ze_device_mem_alloc_desc_t
    public static final int ZE_STRUCTURE_TYPE_HOST_MEM_ALLOC_DESC = 0x16; /// < ::ze_host_mem_alloc_desc_t
    public static final int ZE_STRUCTURE_TYPE_MEMORY_ALLOCATION_PROPERTIES = 0x17; /// < ::ze_memory_allocation_properties_t
    public static final int ZE_STRUCTURE_TYPE_EXTERNAL_MEMORY_EXPORT_DESC = 0x18; /// < ::ze_external_memory_export_desc_t
    public static final int ZE_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMPORT_FD = 0x19; /// < ::ze_external_memory_import_fd_t
    public static final int ZE_STRUCTURE_TYPE_EXTERNAL_MEMORY_EXPORT_FD = 0x1a; /// < ::ze_external_memory_export_fd_t
    public static final int ZE_STRUCTURE_TYPE_MODULE_DESC = 0x1b; /// < ::ze_module_desc_t
    public static final int ZE_STRUCTURE_TYPE_MODULE_PROPERTIES = 0x1c; /// < ::ze_module_properties_t
    public static final int ZE_STRUCTURE_TYPE_KERNEL_DESC = 0x1d; /// < ::ze_kernel_desc_t
    public static final int ZE_STRUCTURE_TYPE_KERNEL_PROPERTIES = 0x1e; /// < ::ze_kernel_properties_t
    public static final int ZE_STRUCTURE_TYPE_SAMPLER_DESC = 0x1f; /// < ::ze_sampler_desc_t
    public static final int ZE_STRUCTURE_TYPE_PHYSICAL_MEM_DESC = 0x20; /// < ::ze_physical_mem_desc_t
    public static final int ZE_STRUCTURE_TYPE_KERNEL_PREFERRED_GROUP_SIZE_PROPERTIES = 0x21;/// < ::ze_kernel_preferred_group_size_properties_t
    public static final int ZE_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMPORT_WIN32 = 0x22; /// < ::ze_external_memory_import_win32_handle_t
    public static final int ZE_STRUCTURE_TYPE_EXTERNAL_MEMORY_EXPORT_WIN32 = 0x23; /// < ::ze_external_memory_export_win32_handle_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_RAYTRACING_EXT_PROPERTIES = 0x00010001;/// < ::ze_device_raytracing_ext_properties_t
    public static final int ZE_STRUCTURE_TYPE_RAYTRACING_MEM_ALLOC_EXT_DESC = 0x10002; /// < ::ze_raytracing_mem_alloc_ext_desc_t
    public static final int ZE_STRUCTURE_TYPE_FLOAT_ATOMIC_EXT_PROPERTIES = 0x10003;/// < ::ze_float_atomic_ext_properties_t
    public static final int ZE_STRUCTURE_TYPE_CACHE_RESERVATION_EXT_DESC = 0x10004; /// < ::ze_cache_reservation_ext_desc_t
    public static final int ZE_STRUCTURE_TYPE_RELAXED_ALLOCATION_LIMITS_EXP_DESC = 0x00020001; /// < ::ze_relaxed_allocation_limits_exp_desc_t
    public static final int ZE_STRUCTURE_TYPE_MODULE_PROGRAM_EXP_DESC = 0x00020002; /// < ::ze_module_program_exp_desc_t
    public static final int ZE_STRUCTURE_TYPE_SCHEDULING_HINT_EXP_PROPERTIES = 0x00020003; /// < ::ze_scheduling_hint_exp_properties_t
    public static final int ZE_STRUCTURE_TYPE_SCHEDULING_HINT_EXP_DESC = 0x00020004;/// < ::ze_scheduling_hint_exp_desc_t
    public static final int ZE_STRUCTURE_TYPE_IMAGE_VIEW_PLANAR_EXP_DESC = 0x00020005; /// < ::ze_image_view_planar_exp_desc_t
    public static final int ZE_STRUCTURE_TYPE_DEVICE_PROPERTIES_1_2 = 0x20006; /// < ::ze_device_properties_t
    public static final int ZE_STRUCTURE_TYPE_FORCE_UINT32 = 0x7fffffff;
}
