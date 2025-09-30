package uk.ac.manchester.tornado.runtime.common;

import java.util.HashSet;

public class MetalTokens {
    public static HashSet<String> metalTokens = new HashSet<>();
    static {
        metalTokens.add("kernel");

        // Address spaces
        metalTokens.add("device");
        metalTokens.add("threadgroup");
        metalTokens.add("constant");
        metalTokens.add("thread");

        // Types
        metalTokens.add("half");
        metalTokens.add("float");
        metalTokens.add("int");
        metalTokens.add("uint");
        metalTokens.add("bool");

        // Vector math builtins
        metalTokens.add("dot");
        metalTokens.add("cross");
        metalTokens.add("distance");
        metalTokens.add("normalize");
        metalTokens.add("length");

        metalTokens.add("buffer");
        metalTokens.add("texture");
        metalTokens.add("sampler");
        metalTokens.add("thread_position_in_grid");
        metalTokens.add("thread_index_in_threadgroup");
        metalTokens.add("threads_per_threadgroup");
        metalTokens.add("threadgroup_position_in_grid");

        // Atomics
        metalTokens.add("atomic_int");
        metalTokens.add("atomic_uint");
        metalTokens.add("atomic_fetch_add_explicit");
        metalTokens.add("atomic_fetch_sub_explicit");
        metalTokens.add("atomic_store_explicit");
        metalTokens.add("atomic_load_explicit");
        metalTokens.add("atomic_exchange_explicit");
        metalTokens.add("atomic_compare_exchange_weak_explicit");
        metalTokens.add("atomic_compare_exchange_strong_explicit");

        // Other keywords
        metalTokens.add("constexpr");
        metalTokens.add("typedef");
        metalTokens.add("using");
        metalTokens.add("namespace");
    }
}
