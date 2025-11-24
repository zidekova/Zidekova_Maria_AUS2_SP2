package structure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class Block<T extends Record<T>> {
    private final int address;
    private final int blockSize;
    private final T recordTemplate;
    private final int recordsPerBlock; // maximum number of records that fit in the block
    private final T[] records;
    private final BitSet valid; // bitmap tracking which record slots are valid

    @SuppressWarnings("unchecked")
    public Block(int address, int blockSize, T recordTemplate) {
        this.address = address;
        this.blockSize = blockSize;
        this.recordTemplate = recordTemplate;
        this.recordsPerBlock = blockSize / recordTemplate.getSize();
        this.records = (T[]) new Record[this.recordsPerBlock];
        this.valid = new BitSet(this.recordsPerBlock);
    }

    public int getAddress() { return this.address; }

    public int getRecordsPerBlock() {
        return this.recordsPerBlock;
    }

    /**
     * Returns the number of currently valid records in the block
     */
    public int getValidCount() {
        return this.valid.cardinality();
    }

    public int getBlockSize() { return blockSize; }

    public T getRecordTemplate() { return recordTemplate; }

    /**
     * Checks if there is space for at least one more record
     */
    public boolean hasSpace() {
        return this.getValidCount() < this.recordsPerBlock;
    }

    /**
     * Checks if the block contains no valid records
     */
    public boolean isEmpty() {
        return this.getValidCount() == 0;
    }

    /**
     * Adds a record to the first available slot in the block
     * @return index where record was added, or -1 if no space is available
     */
    public int addRecord(T record) {
        if (!this.hasSpace()) return -1;
        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (!this.valid.get(i)) {
                this.records[i] = record;
                this.valid.set(i);
                return i;
            }
        }
        return -1;
    }

    /**
     * Deletes a record that matches the given record
     * @return true if record was found and deleted, false otherwise
     */
    public boolean deleteRecord(T record) {
        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (this.valid.get(i) && this.records[i] != null && this.records[i].equals(record)) {
                this.valid.clear(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a record that matches the given record
     * @return found record or null if record was not found
     */
    public T findRecord(T record) {
        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (this.valid.get(i) && this.records[i] != null && this.records[i].equals(record)) {
                return this.records[i];
            }
        }
        return null;
    }

    /**
     * Returns a list of all valid records in the block
     */
    public List<T> getRecords() {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (this.valid.get(i) && this.records[i] != null) list.add(this.records[i]);
        }
        return list;
    }

    /**
     * Creates a visual representation of the block's bitmap
     */
    public String getBitmapVisualization() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < this.recordsPerBlock; i++) {
            sb.append(this.valid.get(i) ? "1" : "0");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Serializes the entire block to byte array
     */
    public byte[] getBytes() {
        int recSize = this.recordTemplate.getSize();
        ByteBuffer buffer = ByteBuffer.allocate(this.blockSize);

        byte[] bitmap = this.toBitmapBytes();
        buffer.put(bitmap);

        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (this.valid.get(i) && this.records[i] != null) {
                byte[] rb = this.records[i].getBytes();
                if (rb.length != recSize) {
                    // handle size mismatch
                    byte[] tmp = new byte[recSize];
                    System.arraycopy(rb, 0, tmp, 0, Math.min(rb.length, recSize));
                    // space-padding
                    for (int j = rb.length; j < recSize; j++) {
                        tmp[j] = (byte) ' ';
                    }
                    buffer.put(tmp);
                } else {
                    buffer.put(rb);
                }
            } else {
                // space-padding
                for (int k = 0; k < recSize; k++) {
                    buffer.put((byte) ' ');
                }
            }
        }

        return buffer.array();
    }

    /**
     * Deserializes the block from byte array
     */
    public void fromBytes(byte[] data) throws IOException {
        if (data == null) throw new IOException("Block data null");
        int recSize = this.recordTemplate.getSize();
        int bitmapBytes = bitmapByteLength();

        // handle potentially short data
        if (data.length < this.blockSize) {
            byte[] padded = new byte[this.blockSize];
            System.arraycopy(data, 0, padded, 0, data.length);
            // space-padding
            for (int i = data.length; i < this.blockSize; i++) {
                padded[i] = (byte) ' ';
            }
            data = padded;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);

        byte[] bitmap = new byte[bitmapBytes];
        buffer.get(bitmap);
        this.fromBitmapBytes(bitmap);

        for (int i = 0; i < this.recordsPerBlock; i++) {
            byte[] recData = new byte[recSize];
            buffer.get(recData);

            if (this.valid.get(i)) {
                T rec = this.recordTemplate.createClass();
                rec.fromBytes(recData);
                this.records[i] = rec;
            }
        }
    }

    /**
     * Returns how many bytes are needed to store the bitmap
     */
    private int bitmapByteLength() {
        return (this.recordsPerBlock + 7) / 8;
    }

    /**
     * Converts the BitSet to compact byte array representation
     */
    private byte[] toBitmapBytes() {
        int len = this.bitmapByteLength();
        byte[] out = new byte[len];

        for (int bit = 0; bit < this.recordsPerBlock; bit++) {
            if (this.valid.get(bit)) {
                int byteIndex = bit / 8;
                int bitIndex = bit % 8;
                // set the bit, | is OR operation
                out[byteIndex] |= (byte) (1 << bitIndex);
            }
        }

        return out;
    }

    /**
     * Reconstructs the BitSet from byte array representation
     */
    private void fromBitmapBytes(byte[] bytes) {
        this.valid.clear();
        for (int bit = 0; bit < this.recordsPerBlock; bit++) {
            int byteIndex = bit / 8;
            int bitIndex = bit % 8;
            if (byteIndex < bytes.length) {
                if ((bytes[byteIndex] & (1 << bitIndex)) != 0) this.valid.set(bit);
            }
        }
    }
}