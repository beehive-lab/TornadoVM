package uk.ac.manchester.tornado.compression;

import java.io.Serializable;

public class HuffmanNode implements Serializable {

    public static final int DEFAULT_FREQUENCY = -1;

    private static final long serialVersionUID = 1L;
    int frequency;
    int realData;

    public HuffmanNode() {
        this.frequency = DEFAULT_FREQUENCY;
    }

    HuffmanNode left;
    HuffmanNode right;

    @Override
    public String toString() {
        return "REAL DATA --> " + realData;
    }
}
