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
     * Points to the next overflow block in the chain.
     */
    public int getNextOverflow() {
        return this.nextOverflowPointer;
    }

    /**
     * Sets the next overflow block pointer.
     */
    public void setNextOverflow(int next) {
        this.nextOverflowPointer = next;
    }

    /**
     * Serializes the block to byte array.
     */
    @Override
    public byte[] getBytes() {
        int recordSize = this.getRecordTemplate().getSize();
        int recordsPerBlock = this.getRecordsPerBlock();

        ByteBuffer buffer = ByteBuffer.allocate(8 + recordSize * recordsPerBlock);

        buffer.putInt(this.validCount);
        buffer.putInt(this.nextOverflowPointer);

        // serialize records
        List<T> records = this.getRecords();
        for (int i = 0; i < recordsPerBlock; i++) {
            if (i < records.size() && records.get(i) != null) {
                byte[] recordBytes = records.get(i).getBytes();
                if (recordBytes.length != recordSize) {
                    byte[] padded = new byte[recordSize];
                    System.arraycopy(recordBytes, 0, padded, 0, Math.min(recordBytes.length, recordSize));
                    buffer.put(padded);
                } else {
                    buffer.put(recordBytes);
                }
            } else {
                buffer.put(new byte[recordSize]);
            }
        }

        return buffer.array();
    }

    /**
     * Deserializes the block from byte array.
     */
    @Override
    public void fromBytes(byte[] data) throws IOException {
        if (data == null) {
            throw new IOException("Null block data");
        }

        int recordSize = this.getRecordTemplate().getSize();
        int recordsPerBlock = this.getRecordsPerBlock();
        int expectedSize = 8 + recordSize * recordsPerBlock;

        if (data.length < expectedSize) {
            byte[] paddedData = new byte[expectedSize];
            System.arraycopy(data, 0, paddedData, 0, data.length);
            data = paddedData;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        this.clearRecords();

        this.validCount = buffer.getInt();
        this.nextOverflowPointer = buffer.getInt();

        for (int i = 0; i < recordsPerBlock; i++) {
            byte[] recordData = new byte[recordSize];
            buffer.get(recordData);

            if (!this.isEmptySlot(recordData)) {
                try {
                    T record = this.getRecordTemplate().createClass();
                    record.fromBytes(recordData);
                    this.records[i] = record;
                } catch (Exception e) {
                    throw new IOException("Failed to deserialize record", e);
                }
            } else {
                this.records[i] = null;
            }
        }
    }
}