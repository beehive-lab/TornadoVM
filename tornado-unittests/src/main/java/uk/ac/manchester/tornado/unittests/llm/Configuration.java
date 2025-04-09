package uk.ac.manchester.tornado.unittests.llm;

public  final class Configuration {
    public final int dim; // transformer dimension
    public final int hiddenDim; // for ffn layers
    public final int numberOfLayers; // number of layers
    public final int numberOfHeads; // number of query heads
    public final int numberOfKeyValueHeads; // number of key/value heads (can be < query heads because of multiquery)
    public final int vocabularySize; // vocabulary size, usually 256 (byte-level)
    public final int contextLength; // max sequence length
    public final float rmsNormEps;
    public final float ropeTheta;
    public final int headSize;

    public Configuration withContextLength(int newContextLength) {
        if (newContextLength < 0) {
            return this; // no change
        }
        return new Configuration(this.dim, this.hiddenDim, this.numberOfLayers, this.numberOfHeads, this.numberOfKeyValueHeads, this.vocabularySize, newContextLength, this.rmsNormEps, this.ropeTheta);
    }

    public Configuration(int dim, int hiddenDim, int numberOfLayers, int numberOfHeads, int numberOfKeyValueHeads, int vocabularySize, int contextLength, float rmsNormEps, float ropeTheta) {
        this.dim = dim;
        this.hiddenDim = hiddenDim;
        this.numberOfLayers = numberOfLayers;
        this.numberOfHeads = numberOfHeads;
        this.numberOfKeyValueHeads = numberOfKeyValueHeads;
        this.vocabularySize = vocabularySize;
        this.contextLength = contextLength;
        this.rmsNormEps = rmsNormEps;
        this.ropeTheta = ropeTheta;
        this.headSize = dim / numberOfHeads;
    }
}
