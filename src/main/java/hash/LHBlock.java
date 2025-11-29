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
     * Points to the next overflow block in the chain.
     */
    public int getNextOverflow() {
        return this.nextBlockPointer;
    }

    /**
     * Sets the next overflow pointer.
     */
    public void setNextOverflow(int next) {
        this.nextBlockPointer = next;
    }

    /**
     * Gets the number of records stored in overflow chain.
     */
    public int getOverflowRecordCount() {
        return this.overflowRecordCount;
    }

    /**
     * Sets the number of records in overflow chain.
     */
    public void setOverflowRecordCount(int count) {
        this.overflowRecordCount = count;
    }

    /**
     * Gets the length of the overflow chain.
     */
    public int getChainLength() {
        return this.chainLength;
    }

    /**
     * Checks if the block is empty.
     */
    @Override
    public boolean isEmpty() {
        return this.getValidCount() == 0 && this.overflowRecordCount == 0;
    }

    /**
     * Serializes the block to byte array.
     */
    @Override
    public byte[] getBytes() {
        int recordSize = this.getRecordTemplate().getSize();
        int recordsPerBlock = this.getRecordsPerBlock();

        ByteBuffer buffer = ByteBuffer.allocate(16 + recordSize * recordsPerBlock);

        // validCount + nextBlockPointer + overflowRecordCount + chainLength
        buffer.putInt(this.validCount);
        buffer.putInt(this.nextBlockPointer);
        buffer.putInt(this.overflowRecordCount);
        buffer.putInt(this.chainLength);


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
     * Reconstructs header fields and record data based on block type.
     */
    @Override
    public void fromBytes(byte[] data) throws IOException {
        if (data == null) {
            throw new IOException("Null block data");
        }

        int recordSize = this.getRecordTemplate().getSize();
        int recordsPerBlock = this.getRecordsPerBlock();
        int expectedSize = 16 + recordSize * recordsPerBlock;

        if (data.length < expectedSize) {
            byte[] paddedData = new byte[expectedSize];
            System.arraycopy(data, 0, paddedData, 0, data.length);
            data = paddedData;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        this.clearRecords();

        this.validCount = buffer.getInt();
        this.nextBlockPointer = buffer.getInt();
        this.overflowRecordCount = buffer.getInt();
        this.chainLength = buffer.getInt();

        // read records
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