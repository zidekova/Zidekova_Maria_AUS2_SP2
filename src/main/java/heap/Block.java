package heap;

import data.Record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Block<T extends data.Record<T>> {
    private final int address;
    private final int blockSize;
    private final T recordTemplate;
    private final int recordsPerBlock; // maximum number of records that fit in the block
    protected T[] records;
    protected int validCount;

    @SuppressWarnings("unchecked")
    public Block(int index, int blockSize, T recordTemplate) {
        this.address = index;
        this.blockSize = blockSize;
        this.recordTemplate = recordTemplate;
        this.recordsPerBlock = blockSize / recordTemplate.getSize();
        this.records = (T[]) new Record[this.recordsPerBlock];
        this.validCount = 0;
    }

    public int getAddress() { return this.address; }

    public int getRecordsPerBlock() {
        return this.recordsPerBlock;
    }

    /**
     * Returns the number of currently valid records in the block
     */
    public int getValidCount() {
        return this.validCount;
    }

    public int getBlockSize() { return blockSize; }

    public T getRecordTemplate() { return recordTemplate; }

    /**
     * Checks if there is space for at least one more record
     */
    public boolean hasSpace() {
        return this.validCount < this.recordsPerBlock;
    }

    /**
     * Checks if the block contains no valid records
     */
    public boolean isEmpty() {
        return this.validCount == 0;
    }

    /**
     * Adds a record to the first available slot in the block
     * @return index where record was added, or -1 if no space is available
     */
    public int addRecord(T record) {
        if (!this.hasSpace()) {
            return -1;
        }

        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (this.records[i] == null || isEmptyRecord(this.records[i])) {
                this.records[i] = record;
                this.validCount++;
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
            if (records[i] != null && records[i].getKey() != null &&
                    records[i].getKey().equals(record.getKey())) {

                this.records[i] = null;
                this.validCount--;
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
            if (this.records[i] != null && this.records[i].getKey() != null &&
                    this.records[i].getKey().equals(record.getKey())) {
                return this.records[i];
            }
        }

        return null;
    }

    /**
     * Returns a list of all valid records in the block
     */
    public List<T> getRecords() {
        List<T> result = new ArrayList<>();
        if (records != null) {
            for (T record : records) {
                if (record != null && !isEmptyRecord(record)) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    public void clearRecords() {
        if (records != null) {
            Arrays.fill(records, null);
        }
        validCount = 0;
    }

    /**
     * Serializes the entire block to byte array
     */
    public byte[] getBytes() {
        int recSize = this.recordTemplate.getSize();
        ByteBuffer buffer = ByteBuffer.allocate(4 + recSize * this.recordsPerBlock);

        buffer.putInt(this.validCount);

        for (int i = 0; i < this.recordsPerBlock; i++) {
            if (this.records[i] != null) {
                byte[] rb = this.records[i].getBytes();
                if (rb.length != recSize) {
                    byte[] tmp = new byte[recSize];
                    System.arraycopy(rb, 0, tmp, 0, Math.min(rb.length, recSize));

                    for (int j = rb.length; j < recSize; j++) {
                        tmp[j] = (byte) ' ';
                    }
                    buffer.put(tmp);
                } else {
                    buffer.put(rb);
                }
            } else {
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
        int expectedSize = 4 + recSize * this.recordsPerBlock;

        if (data.length < expectedSize) {
            byte[] padded = new byte[expectedSize];
            System.arraycopy(data, 0, padded, 0, data.length);
            for (int i = data.length; i < expectedSize; i++) {
                padded[i] = (byte) ' ';
            }
            data = padded;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.validCount = buffer.getInt();

        for (int i = 0; i < this.recordsPerBlock; i++) {
            byte[] recData = new byte[recSize];
            buffer.get(recData);

            if (isEmptySlot(recData)) {
                this.records[i] = null;
            } else {
                T rec = this.recordTemplate.createClass();
                rec.fromBytes(recData);
                this.records[i] = rec;
            }
        }
    }

    protected boolean isEmptyRecord(T record) {
        if (record == null) return true;
        try {
            byte[] recordBytes = record.getBytes();
            return isEmptySlot(recordBytes);
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean isEmptySlot(byte[] data) {
        for (byte b : data) {
            if (b != 0 && b != ' ') {
                return false;
            }
        }
        return true;
    }
}