Transitioning from v0.15 to v0.16
==================================
Starting from v0.16, TornadoVM is providing its custom off-heap data types. Below is a list of the new types, with an arrow pointing to their on-heap primitive array equivalent.

* IntArray -> int[]
* FloatArray -> float[]
* DoubleArray -> double[]
* LongArray -> long[]
* CharArray -> char[]
* ShortArray -> short[]
* ByteArray -> byte[]

The existing Matrix and Vector collection types that TornadoVM offers (e.g., ``VectorFloat``, ``Matrix2DDouble``, etc.)  have been refactored to use internally these off-heap data types instead of primitive arrays.

1. Off-heap types API
-------------------------
The new off-heap types encapsulate a `Memory Segment <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/MemorySegment.html>`_. The main methods that are exposed to manage the Memory Segment of each type are the following (in each signature, ``X`` is the equivalent primitive type, e.g., for the methods of the IntArray class, ``X`` is ``int``):

* ``public void set(int index, X value) // sets a value at a specific index``
* ``public X get(int index) // returns the value of a specific index``
* ``public void clear() // sets the values of the segment to 0``
* ``public void init() // initializes the segment with a specific value``
* ``public int getSize() // returns the number of elements in the segment``

**NOTE:** The methods ``init()`` and ``clear()`` are essential because, contrary to their counterpart primitive arrays which are initialized by default with 0, the new types contain garbage values when first created.

Furthermore, each type offers two contructors. In the first, the only argument is the number of elements that the Memory Segment will contain. In the second, a set of values can be passed for direct initialization of the segment.

E.g.:

.. code:: java

   // allocate an off-heap memory segment that will contain 16 int values
   IntArray intArray = new IntArray(16);

   // allocate an off-heap memory segment that is initialized with the four float values in the constructor
   FloatArray floatArray = new FloatArray(0.0f, 0.1f, 0.2.f, 0.3f);

2. Example: Transforming an existing TornadoVM program
-------------------------------------------------------

Below is an example of a TornadoVM program that uses primitive arrays:

.. code:: java

    public static void main(String[] args) {
        int[] input = new int[numElements];

        Arrays.fill(input, 10);

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
                .task("t", Example::add, input, 1)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, input);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutor(immutableTaskGraph);
        executor.execute();
    }

    public static void add(int[] input, int value) {
        for (@Parallel int i = 0; i < input.length; i++) {
            input[i] = input[i] + value;
        }
    }

Here is how the code above would need to be transformed to use the new data types (the changes are highlighted):

.. code-block:: java
   :emphasize-lines: 2,4,16,18

    public static void main(String[] args) {
        IntArray input = new IntArray(numElements); // create a new off heap segment of int values

        input.init(10); // initialize all the values of the input to be 10

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
                .task("t", Example::add, input, 1)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, input);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutor(immutableTaskGraph);
        executor.execute();
    }

    public static void acc(IntArray input, int value) { // Pass the IntArray as a parameter
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            input.set(i, input.get(i) + value);  // Use the set and get functions access data
        }
    }
