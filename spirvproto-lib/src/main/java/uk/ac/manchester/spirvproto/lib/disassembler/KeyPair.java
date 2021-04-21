package uk.ac.manchester.spirvproto.lib.disassembler;

class KeyPair<K, V> {
    private final K key;
    private final V value;

    KeyPair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public V getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KeyPair) return key.equals(((KeyPair<?, ?>) obj).key);
        else return super.equals(obj);
    }
}
