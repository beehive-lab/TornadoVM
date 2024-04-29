Migrating from v0.15.2 to v1.0
==================================
Starting from v1.0, TornadoVM is providing its custom off-heap data types. Below is a list of the new types, with an arrow pointing from the on-heap primitive array types to their off-heap equivalent.

* ``int[]`` -> ``IntArray``
* ``float[]`` -> ``FloatArray``
* ``double[]`` -> ``DoubleArray``
* ``long[]`` -> ``LongArray``
* ``char[]`` -> ``CharArray``
* ``short[]`` -> ``ShortArray``
* ``byte[]`` -> ``ByteArray``

The existing Matrix and Vector collection types that TornadoVM offers (e.g., ``VectorFloat``, ``Matrix2DDouble``, etc.)  have been refactored to use internally these off-heap data types instead of primitive arrays.

1. Off-heap types API
-------------------------
The new off-heap types encapsulate a `Memory Segment <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/MemorySegment.html>`_, a contiguous region of memory outside the Java heap. To allocate off-heap memory using the TornadoVM API, each type offers a constructor with one argument that indicates the number of elements that the Memory Segment will contain.

E.g.:

.. code:: java

   // allocate an off-heap memory segment that will contain 16 int values
   IntArray intArray = new IntArray(16);

Additionally, developers can create an instance of a TornadoVM native array by invoking factory methods for different data representations. In the following examples we will demonstrate the API functions for the ``FloatArray`` type, but the same methods apply for all support native array types. 

.. code:: java

   // from on-heap array to TornadoVM native array
   public static FloatArray fromArray(float[] values);
   // from individual items to TornadoVM native array
   public static FloatArray fromElements(float... values);
   // from Memory Segment to TornadoVM native array
   public static FloatArray fromSegment(MemorySegment segment); 

The main methods that the off-heap types expose to manage the Memory Segment of each type are presented in the list below. 

.. code:: java

   public MemorySegment getSegment() // returns the memory segment without the Tornado Array Header as slice
   public MemorySegment getSegmentWithHeader() //  returns the Memory Segment with the Tornado Array Header
   public void set(int index, float value) // sets a value at a specific index
      E.g.:
          FloatArray floatArray = new FloatArray(16);
          floatArray.set(0, 10.0f); // at index 0 the value is 10.0f
   public float get(int index) // returns the value of a specific index
      E.g.:
          FloatArray floatArray = FloatArray.fromArray(new float[] {2.0f, 1.0f, 2.0f, 5.0f});
          float floatValue = floatArray.get(3); // returns 5.0f
   public void clear() // sets the values of the segment to 0
      E.g.:
          FloatArray floatArray = new FloatArray(1024);
          floatArray.clear(); // the floatArray contains 1024 zeros
   public void init(float value) // initializes the segment with a specific value
      E.g.:
   	  FloatArray floatArray = new FloatArray(1024);
          floatArray.init(1.0f); // the floatArray contains 1024 ones
   public int getSize() // returns the number of elements in the segment
      E.g.:
          FloatArray floatArray = new FloatArray(16);
          int size = floatArray.getSize(); // returns 16
   public float[] toHeapArray(); // Converts the data from off-heap to on-heap
   public long getNumBytesOfSegmentWithHeader(); // Returns the total number of bytes the underlying Memory Segment occupies, including the header bytes
   public long getNumBytesOfSegment(); // Returns the total number of bytes the underlying Memory Segment occupies, excluding the header bytes
   
**NOTE:** The methods ``init()`` and ``clear()`` are essential because, contrary to their counterpart primitive arrays which are initialized by default with 0, the new types contain garbage values when first created.

2. Example: Migrating TornadoVM applications from <= 0.15.2 to 1.0
-------------------------------------------------------------------

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
