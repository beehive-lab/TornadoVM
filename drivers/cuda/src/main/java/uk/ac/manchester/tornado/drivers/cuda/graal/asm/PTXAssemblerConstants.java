package uk.ac.manchester.tornado.drivers.cuda.graal.asm;

public class PTXAssemblerConstants {

    public static final String HEAP_PTR_NAME = "heap_pointer";
    public static final String STACK_PTR_NAME = "stack_pointer";
    public static final String ARG_START = "arg_start";
    public static final String GLOBAL_MEM_MODIFIER = "global";

    public static final String TAB = "\t";
    public static final String COMMA = ",";
    public static final String STMT_DELIMITER = ";";
    public static final String EOL = "\n";

    public static final String ASSIGN = "=";

    public static final String SQUARE_BRACKETS_OPEN = "[";
    public static final String SQUARE_BRACKETS_CLOSE = "]";

    public static final int STACK_BASE_OFFSET = 6;
    public static final String OP_GUARD = "@";
    public static final String NEGATION = "!";
    public static final String COMPUTE_VERSION = ".version";
    public static final String TARGET_ARCH = ".target";
    public static final String ADDRESS_HEADER = ".address_size";
    public static final String EXTERNALLY_VISIBLE = ".visible";
    public static final String KERNEL_ENTRYPOINT = ".entry";
}
