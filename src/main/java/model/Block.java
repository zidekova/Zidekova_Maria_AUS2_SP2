package model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class Block<T extends Record<T>> {
    private final int address;
    private final int blockSize;
    private final T recordTemplate;
    private final int recordsPerBlock;
    private final T[] records;
    private final BitSet valid;
    private int validCount;

    @SuppressWarnings("unchecked")
    public Block(int address, int blockSize, T recordTemplate) {
        this.address = address;
        this.blockSize = blockSize;
        this.recordTemplate = recordTemplate;
        this.recordsPerBlock = blockSize / recordTemplate.getSize();
        this.records = (T[]) new Record[recordsPerBlock];
        this.valid = new BitSet(recordsPerBlock);
        this.validCount = 0;
    }

    public int getAddress() {
        return address;
    }

    public int getRecordsPerBlock() {
        return recordsPerBlock;
    }

    public int getValidCount() {
        return validCount;
    }

    public boolean hasSpace() {
        return validCount < recordsPerBlock;
    }

    public boolean isEmpty() {
        return validCount == 0;
    }

    public int addRecord(T record) {
        if (!hasSpace()) return -1;
        for (int i = 0; i < recordsPerBlock; i++) {
            if (!valid.get(i)) {
                records[i] = record;
                valid.set(i);
                validCount++;
                return i;
            }
        }
        return -1;
    }

    public boolean deleteRecord(T pattern) {
        for (int i = 0; i < recordsPerBlock; i++) {
            if (valid.get(i) && records[i] != null && records[i].equals(pattern)) {
                valid.clear(i);
                validCount--;
                return true;
            }
        }
        return false;
    }

    public T findRecord(T pattern) {
        for (int i = 0; i < recordsPerBlock; i++) {
            if (valid.get(i) && records[i] != null && records[i].equals(pattern)) {
                return records[i];
            }
        }
        return null;
    }

    public List<T> getRecords() {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < recordsPerBlock; i++) {
            if (valid.get(i) && records[i] != null) list.add(records[i]);
        }
        return list;
    }

    // NOVÁ METÓDA: Vizualizácia BitSetu
    public String getBitmapVisualization() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < recordsPerBlock; i++) {
            sb.append(valid.get(i) ? "1" : "0");
            if (i < recordsPerBlock - 1) {
                sb.append("");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // NOVÁ METÓDA: Získanie stavu konkrétneho slotu
    public String getSlotStatus(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= recordsPerBlock) {
            return "INVALID";
        }
        if (valid.get(slotIndex)) {
            return "OBSADENÝ - " + (records[slotIndex] != null ? records[slotIndex].toString() : "NULL");
        } else {
            return "VOĽNÝ";
        }
    }

    public byte[] getBytes() {
        int recSize = recordTemplate.getSize();
        int bitmapBytes = bitmapByteLength();
        ByteBuffer buffer = ByteBuffer.allocate(blockSize);

        byte[] bitmap = toBitmapBytes();
        buffer.put(bitmap);

        for (int i = 0; i < recordsPerBlock; i++) {
            if (valid.get(i) && records[i] != null) {
                byte[] rb = records[i].getBytes();
                if (rb.length != recSize) {
                    byte[] tmp = new byte[recSize];
                    System.arraycopy(rb, 0, tmp, 0, Math.min(rb.length, recSize));
                    buffer.put(tmp);
                } else {
                    buffer.put(rb);
                }
            } else {
                for (int k = 0; k < recSize; k++) buffer.put((byte) 0);
            }
        }

        return buffer.array();
    }

    public void fromBytes(byte[] data) throws IOException {
        if (data == null) throw new IOException("Block data null");
        int recSize = recordTemplate.getSize();
        int bitmapBytes = bitmapByteLength();
        if (data.length < blockSize) {
            byte[] padded = new byte[blockSize];
            System.arraycopy(data, 0, padded, 0, data.length);
            data = padded;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);

        byte[] bitmap = new byte[bitmapBytes];
        buffer.get(bitmap);
        fromBitmapBytes(bitmap);

        for (int i = 0; i < recordsPerBlock; i++) {
            byte[] recData = new byte[recSize];
            buffer.get(recData);
            if (valid.get(i)) {
                T rec = recordTemplate.createClass();
                rec.fromBytes(recData);
                records[i] = rec;
            }
        }

        this.validCount = valid.cardinality();
    }

    private int bitmapByteLength() {
        return (recordsPerBlock + 7) / 8;
    }

    private byte[] toBitmapBytes() {
        int len = bitmapByteLength();
        byte[] out = new byte[len];
        for (int bit = 0; bit < recordsPerBlock; bit++) {
            if (valid.get(bit)) {
                int byteIndex = bit / 8;
                int bitIndex = bit % 8;
                out[byteIndex] |= (byte) (1 << bitIndex);
            }
        }
        return out;
    }

    private void fromBitmapBytes(byte[] bytes) {
        valid.clear();
        for (int i = 0; i < recordsPerBlock; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            if (byteIndex < bytes.length) {
                if ((bytes[byteIndex] & (1 << bitIndex)) != 0) valid.set(i);
            }
        }
    }

    @Override
    public String toString() {
        return "Block@" + address + " [valid=" + validCount + "/" + recordsPerBlock + "] bitmap=" + getBitmapVisualization();
    }

    // NOVÁ METÓDA: Detailný výpis pre GUI
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Block@").append(address).append(" [").append(validCount).append("/").append(recordsPerBlock).append("]\n");
        sb.append("Bitmap: ").append(getBitmapVisualization()).append("\n");
        sb.append("Sloty:\n");

        for (int i = 0; i < recordsPerBlock; i++) {
            sb.append("  ").append(i).append(": ");
            if (valid.get(i)) {
                if (records[i] != null) {
                    sb.append("✓ ").append(records[i].toString());
                } else {
                    sb.append("✓ [CHYBA: NULL ZÁZNAM]");
                }
            } else {
                sb.append("✗ VOĽNÝ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}