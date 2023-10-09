package uk.ac.manchester.tornado.api.data.nativetypes;

public class TornadoArray {
    public static final long ARRAY_HEADER = Long.parseLong(System.getProperty("tornado.panama.objectHeader", "24"));
    public static final int HEADER_ELEMENT = 4;

    public static final int ARRAY_SIZE_HEADER_POSITION = (int) (ARRAY_HEADER / HEADER_ELEMENT) - 1;

    public static final int BASE_INDEX = (int) (ARRAY_HEADER / HEADER_ELEMENT);

}
