package uk.ac.manchester.tornado.compression;

import java.util.Comparator;

public class HuffmanTreeComparator implements Comparator<HuffmanNode> {
    public int compare(HuffmanNode x, HuffmanNode y) {
        return (x.frequency - y.frequency);
    }
}