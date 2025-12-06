package overflow;

import heap.Block;
import data.Record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class OverflowBlock<T extends Record<T>> extends Block<T> {
    private int nextOverflowPointer = -1;  // pointer to next overflow block in chain

    public OverflowBlock(int address, int blockSize, T template) {
        super(address, blockSize, template);
    }

    /**
     * Points to the next overflow block in the chain
     */
    public int getNextOverflow() {
        return this.nextOverflowPointer;
    }

    /**
     * Sets the next overflow block pointer
     */
    public void setNextOverflow(int next) {
        this.nextOverflowPointer = next;
    }

    /**
     * Serializes the block to byte array
     */

    @Override
    public byte[] getBytes() {
        final int recordSize = this.getRecordTemplate().getSize();
        final int recordsPerBlock = this.getRecordsPerBlock();
        final int payloadBytes = recordSize * recordsPerBlock;
        final ByteBuffer buffer = ByteBuffer.allocate(8 + payloadBytes);

        buffer.putInt(this.validCount);
        buffer.putInt(this.nextOverflowPointer);

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
     */

    @Override
    public void fromBytes(byte[] data) throws IOException {
        if (data == null) throw new IOException("Null block data");

        final int recordSize = this.getRecordTemplate().getSize();
        final int recordsPerBlock = this.getRecordsPerBlock();
        final int expected = 8 + recordSize * recordsPerBlock;

        if (data.length < expected) {
            byte[] padded = new byte[expected];
            System.arraycopy(data, 0, padded, 0, data.length);
            data = padded;
        }

        final ByteBuffer buffer = ByteBuffer.wrap(data);

        int hdrValid = buffer.getInt();
        int hdrNext = buffer.getInt();

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
        this.nextOverflowPointer = (hdrNext == 0 && this.validCount == 0) ? -1 : hdrNext;
    }

    /**
     * Updates a record with matching key
     * Returns true if record was found and updated, false otherwise
     */
    public boolean updateRecord(String key, T updatedRecord) {
        for (int i = 0; i < this.records.length; i++) {
            T record = this.records[i];
            if (record.getKey().equals(key)) {
                this.records[i] = updatedRecord;
                return true;
            }
        }
        return false;
    }
}