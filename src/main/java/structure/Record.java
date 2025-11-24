package structure;

public interface Record<T> {
    boolean equals(Object other);
    int getSize();
    byte[] getBytes();
    void fromBytes(byte[] data);
    T createClass();
}
