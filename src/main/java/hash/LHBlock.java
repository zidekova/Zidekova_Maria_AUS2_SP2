package hash;

import heap.Block;
import data.Record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class LHBlock<T extends Record<T>> extends Block<T> {
    private int nextBlockPointer = -1;
    private int overflowRecordCount = 0;
    private int chainLength = 0;

    public LHBlock(int address, int blockSize, T template) {
        super(address, blockSize, template);
    }

    /**
     * Points to the next overflow block in the chain
     */
    public int getNextOverflow() {
        return this.nextBlockPointer;
    }

    /**
     * Sets the next overflow pointer
     */
    public void setNextOverflow(int next) {
        this.nextBlockPointer = next;
    }

    /**
     * Gets the number of records stored in overflow chain
     */
    public int getOverflowRecordCount() {
        return this.overflowRecordCount;
    }

    /**
     * Sets the number of records in overflow chain
     */
    public void setOverflowRecordCount(int count) {
        this.overflowRecordCount = count;
    }

    /**
     * Gets the length of the overflow chain
     */
    public int getChainLength() {
        return this.chainLength;
    }

    /**
     * Sets the length of the overflow chain
     */
    public void setChainLength(int length) {
        this.chainLength = length;
    }

    /**
     * Checks if the block is empty
     */
    @Override
    public boolean isEmpty() {
        return this.getValidCount() == 0 && this.overflowRecordCount == 0;
    }

    /**
     * Serializes the block to byte array
     */
    @Override
    public byte[] getBytes() {
        final int recordSize = this.getRecordTemplate().getSize();
        final int recordsPerBlock = this.getRecordsPerBlock();
        final int payloadBytes = recordSize * recordsPerBlock;
        final ByteBuffer buffer = ByteBuffer.allocate(16 + payloadBytes);

        buffer.putInt(this.validCount);
        buffer.putInt(this.nextBlockPointer);
        buffer.putInt(this.overflowRecordCount);
        buffer.putInt(this.chainLength);

        for (int i = 0; i < recordsPerBlock; i++) {
            T rec = (this.records != null && i < this.records.length) ? this.records[i] : null;
            byte[] rb;
            if (rec != null && !isEmptyRecord(rec)) {
                rb = rec.getBytes();
                if (rb.length != recordSize) {
                    byte[] tmp = new byte[recordSize];
                    System.arraycopy(rb, 0, tmp, 0, Math.min(rb.length, recordSize));
                    rb = tmp;
                }
            } else {
                rb = new byte[recordSize];
            }
            buffer.put(rb);
        }
        return buffer.array();
    }

    /**
     * Deserializes the block from byte array
     * Reconstructs header fields and record data based on block type
     */

    public void fromBytes(byte[] data) throws IOException {
        if (data == null) throw new IOException("Null block data");

        final int recordSize = this.getRecordTemplate().getSize();
        final int recordsPerBlock = this.getRecordsPerBlock();
        final int expected = 16 + recordSize * recordsPerBlock;

        if (data.length < expected) {
            byte[] padded = new byte[expected];
            System.arraycopy(data, 0, padded, 0, data.length);
            data = padded;
        }

        final ByteBuffer buffer = ByteBuffer.wrap(data);

        int hdrValid     = buffer.getInt();
        int hdrNext      = buffer.getInt();
        int hdrOvCount   = buffer.getInt();
        int hdrChainLen  = buffer.getInt();

        this.clearRecords();
        int actualValid = 0;
        for (int i = 0; i < recordsPerBlock; i++) {
            byte[] recData = new byte[recordSize];
            buffer.get(recData);
            if (!this.isEmptySlot(recData)) {
                try {
                    T rec = this.getRecordTemplate().createClass();
                    rec.fromBytes(recData);
                    if (!isEmptyRecord(rec)) {
                        this.records[i] = rec;
                        actualValid++;
                    } else {
                        this.records[i] = null;
                    }
                } catch (Exception e) {
                    throw new IOException("Failed to deserialize record", e);
                }
            } else {
                this.records[i] = null;
            }
        }

        this.validCount = actualValid;
        this.overflowRecordCount = Math.max(0, hdrOvCount);
        this.chainLength = Math.max(0, hdrChainLen);

        if (hdrNext == 0 && this.overflowRecordCount == 0 && this.chainLength == 0) {
            this.nextBlockPointer = -1;
        } else {
            this.nextBlockPointer = hdrNext;
        }
    }

    /**
     * Updates a record that matches the pattern with new data
     * Returns true if record was found and updated, false otherwise
     */
    public boolean updateRecord(T pattern, T updatedRecord) {
        for (int i = 0; i < this.records.length; i++) {
            T record = this.records[i];
            if (record.equals(pattern)) {
                this.records[i] = updatedRecord;
                return true;
            }
        }
        return false;
    }
}