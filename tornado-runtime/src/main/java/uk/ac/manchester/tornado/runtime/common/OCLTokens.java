/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.util.HashSet;

public class OCLTokens {
    public static HashSet<String> openCLTokens = new HashSet<>();
    static {
        // FIXME: To be completed
        openCLTokens.add("kernel");
        openCLTokens.add("__kernel");
        openCLTokens.add("__global");
        openCLTokens.add("global");
        openCLTokens.add("local");
        openCLTokens.add("__local");
        openCLTokens.add("private");
        openCLTokens.add("__private");
        openCLTokens.add("half");
        openCLTokens.add("dot");
        openCLTokens.add("uniform");
        openCLTokens.add("pipe");
        openCLTokens.add("auto");
        openCLTokens.add("cross");
        openCLTokens.add("distance");
        openCLTokens.add("normalize");
        openCLTokens.add("complex");
        openCLTokens.add("channel");

        // Atomics
        openCLTokens.add("atomic_add");
        openCLTokens.add("atomic_sub");
        openCLTokens.add("atomic_xchg");
        openCLTokens.add("atomic_inc");
        openCLTokens.add("atomic_min");
        openCLTokens.add("atomic_max");
        openCLTokens.add("atomic_and");
        openCLTokens.add("atomic_or");
        openCLTokens.add("atomic_xor");
        openCLTokens.add("atomic_dec");
        openCLTokens.add("atomic_cmpxchg");
        openCLTokens.add("printf");
        openCLTokens.add("read_pipe");
        openCLTokens.add("write_pipe");

        // Pipes
        openCLTokens.add("is_valid_reserve_id");
        openCLTokens.add("reserve_read_pipe");
        openCLTokens.add("reserve_write_pipe");
        openCLTokens.add("commit_read_pipe");
        openCLTokens.add("commit_write_pipe");
        openCLTokens.add("get_pipe_max_packets");
        openCLTokens.add("get_pipe_num_packets");
        openCLTokens.add("work_group_commit_read_pipe");
        openCLTokens.add("work_group_commit_write_pipe");
        openCLTokens.add("sub_group_commit_read_pipe");
        openCLTokens.add("sub_group_commit_write_pipe");
        openCLTokens.add("work_group_reserve_read_pipe");
        openCLTokens.add("work_group_reserve_write_pipe");
        openCLTokens.add("sub_group_reserve_read_pipe");
        openCLTokens.add("sub_group_reserve_write_pipe");

        // Enqueue kernel
        openCLTokens.add("enqueue_kernel");
        openCLTokens.add("get_kernel_work_group_size");
        openCLTokens.add("get_kernel_preferred_work_group_size_multiple");
        openCLTokens.add("enqueue_marker");
        openCLTokens.add("get_kernel_sub_group_count_for_ndrange");
        openCLTokens.add("get_kernel_max_sub_group_size_for_ndrange");

        // Builtin functions
        openCLTokens.add("retain_event");
        openCLTokens.add("release_event");
        openCLTokens.add("create_user_event");
        openCLTokens.add("is_valid_event");
        openCLTokens.add("set_user_event_status");
        openCLTokens.add("capture_event_profiling_info");
        openCLTokens.add("get_default_queue");
        openCLTokens.add("ndrange_1D");
        openCLTokens.add("ndrange_nD");
    }
}
