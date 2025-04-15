package uk.ac.manchester.tornado.unittests.llm;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

import java.nio.FloatBuffer;

public final class Weights {
    // token embedding table
    public final FloatTensor token_embedding_table; // (vocab_size, dim)
    // weights for rmsnorms
    public final FloatBuffer[] rms_att_weight; // (layer, dim) rmsnorm weights
    // weights for matmuls
    public final FloatTensor[] wq; // (layer, n_heads * head_size)
    public final FloatTensor[] wk; // (layer, n_kv_heads, head_size)
    public final FloatTensor[] wv; // (layer, n_kv_heads * head_size)
    public final FloatTensor[] wo; // (layer, n_heads * head_size, dim)
    public final FloatBuffer[] rms_ffn_weight; // (layer, dim)

    // Flatten Structure
    public final FloatArray rms_att_weightFlat; // (layer, dim) rmsnorm weights
    public final FloatArray wqFlat; // (layer, n_heads * head_size)
    public final FloatArray wkFlat; // (layer, n_kv_heads, head_size)
    public final FloatArray wvFlat; // (layer, n_kv_heads * head_size)
    public final FloatArray woFlat; // (layer, n_heads * head_size, dim)
    public final FloatArray rms_ffn_weightFlat; // (layer, dim)


    public final FloatArray w1Flat; // (layer, hidden_dim, dim)
    public final FloatArray w2Flat; // (layer, dim, hidden_dim)
    public final FloatArray w3Flat; // (layer, hidden_dim, dim)

    public final FloatArray freq_cis_realFlat; // (seq_len, head_size/2)
    public final FloatArray freq_cis_imagFlat; // (seq_len, head_size/2)


    // weights for ffn
    public final FloatTensor[] w1; // (layer, hidden_dim, dim)
    public final FloatTensor[] w2; // (layer, dim, hidden_dim)
    public final FloatTensor[] w3; // (layer, hidden_dim, dim)
    // public final rmsnorm
    public final FloatBuffer rms_final_weight; // (dim,)
    // freq_cis for RoPE relatively positional embeddings
    public final FloatBuffer freq_cis_real; // (seq_len, head_size/2)
    public final FloatBuffer freq_cis_imag; // (seq_len, head_size/2)
    // (optional) classifier weights for the logits, on the last layer
    public final FloatTensor wcls; // (vocab_size, dim)

    //    public final TensorQ8 wclsTornadoQ8;
    public final ByteArray wclsByteArray;
    public final FloatArray rms_final_weight_as_floatArray;

    // wo -> FloatArray
    // w1 -> FloatArray
    // rms_ffn_weights[l] -> FloatBuffer

    public final FloatArray[] woAsFloatArray;
    public final FloatArray[] rms_ffn_weight_as_floatArray;
    public final FloatArray[] w1AsFloatArray;
    public final FloatArray[] w2AFloatArray;
    public final FloatArray[] w3AFloatArray;
    public final HalfFloatArray halfFloat;

    public Weights(FloatTensor token_embedding_table, FloatBuffer[] rms_att_weight, FloatTensor[] wq, FloatTensor[] wk, FloatTensor[] wv, FloatTensor[] wo, FloatBuffer[] rms_ffn_weight, FloatTensor[] w1, FloatTensor[] w2, FloatTensor[] w3, FloatBuffer rms_final_weight, FloatBuffer freq_cis_real, FloatBuffer freq_cis_imag, FloatTensor wcls) {
        this.token_embedding_table = token_embedding_table;
        this.rms_att_weight = rms_att_weight;
        this.wq = wq;
        this.wk = wk;
        this.wv = wv;
        this.wo = wo;
        this.rms_ffn_weight = rms_ffn_weight;
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
        this.rms_final_weight = rms_final_weight;
        this.freq_cis_real = freq_cis_real;
        this.freq_cis_imag = freq_cis_imag;
        this.wcls = wcls;

        this.rms_att_weightFlat = loadToSingleFloatArray(rms_att_weight); // (layer, dim) rmsnorm weights
        this.wqFlat = loadToContinuesFloatArray(wq); // (layer, n_heads * head_size)
        this.wkFlat = loadToContinuesFloatArray(wk); // (layer, n_kv_heads, head_size)
        this.wvFlat = loadToContinuesFloatArray(wv);; // (layer, n_kv_heads * head_size)
        this.woFlat = loadToContinuesFloatArray(wo);; // (layer, n_heads * head_size, dim)
        this.rms_ffn_weightFlat = loadToSingleFloatArray(rms_ffn_weight); // (layer, dim)

        this.w1Flat = loadToContinuesFloatArray(w1); // (layer, hidden_dim, dim)
        this.w2Flat = loadToContinuesFloatArray(w2); // (layer, dim, hidden_dim)
        this.w3Flat = loadToContinuesFloatArray(w3);; // (layer, hidden_dim, dim)

        this.freq_cis_imagFlat = loadToSingleFloatArray(freq_cis_imag);
        this.freq_cis_realFlat = loadToSingleFloatArray(freq_cis_real);


        // Store read-only weight as a ByteArray in TornadoVM
        this.wclsByteArray = ByteArray.fromSegment(wcls.asMemorySegment());
        this.rms_final_weight_as_floatArray = FloatArray.fromFloatBuffer(rms_final_weight);

        this.woAsFloatArray = loadToFloatArray(wo);
        this.w1AsFloatArray = loadToFloatArray(w1);
        this.w2AFloatArray = loadToFloatArray(w2);
        this.w3AFloatArray = loadToFloatArray(w3);

        this.halfFloat = loadToHalfFloatArray(wcls);

        this.rms_ffn_weight_as_floatArray= loadToFloatArray(rms_ffn_weight);
    }

    private static FloatArray loadToContinuesFloatArray(FloatTensor[] input) {
        FloatArray all = new FloatArray(input.length * input[0].size());

        int index = 0;
        for (FloatTensor tensor : input) {
            for (int i = 0; i < tensor.size(); i++) {
                all.set(index++, tensor.getFloat(i));
            }
        }

        return all;
    }

    private static FloatArray[] loadToFloatArray(FloatTensor[] array) {
        FloatArray[] floatArrays = new FloatArray[array.length];
        for (int i = 0; i < array.length; i++) {
            floatArrays[i] = FloatArray.fromSegment(array[i].asMemorySegment());
        }
        return floatArrays;
    }

    private static FloatArray[] loadToFloatArray(FloatBuffer[] array) {
        FloatArray[] floatArrays = new FloatArray[array.length];
        for (int i = 0; i < array.length; i++) {
            floatArrays[i] = FloatArray.fromFloatBuffer(array[i]);
        }
        return floatArrays;
    }


    private static FloatArray loadToSingleFloatArray(FloatBuffer[] array) {
        int totalSize = 0;
        for (FloatBuffer buffer : array) {
            totalSize += buffer.remaining();
        }

        FloatArray result = new FloatArray(totalSize);
        int index = 0;
        for (FloatBuffer buffer : array) {
            while (buffer.hasRemaining()) {
                result.set(index++, buffer.get());
            }
        }

        return result;
    }

    public HalfFloatArray loadToHalfFloatArray(FloatTensor input) {
        HalfFloatArray halfFloatArray = new HalfFloatArray(input.size());

        for (int i = 0; i < input.size(); i++) {
            halfFloatArray.set(i, new HalfFloat(input.getFloat(i)));
        }

        return halfFloatArray;
    }

    private static FloatArray loadToSingleFloatArray(FloatBuffer input) {
        FloatBuffer copy = input.duplicate(); // Prevent modifying the original buffer
        int totalSize = copy.remaining();

        FloatArray result = new FloatArray(totalSize);

        int index = 0;
        while (copy.hasRemaining()) {
            result.set(index++, copy.get());
        }

        return result;
    }


}
