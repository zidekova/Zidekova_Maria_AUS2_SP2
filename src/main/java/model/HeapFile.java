package model;

import java.io.*;
import java.util.*;

public class HeapFile<T extends Record<T>> {
    private final RandomAccessFile file;
    private final int clusterSize;
    private final T recordTemplate;
    private final LinkedList<Integer> partiallyFreeBlocks = new LinkedList<>();
    private final LinkedList<Integer> emptyBlocks = new LinkedList<>();
    private final String metadataFile;
    private final String filename;
    private final int recordsPerBlock;

    public HeapFile(String filename, int clusterSize, T recordTemplate) throws IOException {
        this.filename = filename;
        this.clusterSize = clusterSize;
        this.recordTemplate = recordTemplate;
        this.recordsPerBlock = clusterSize / recordTemplate.getSize();
        if (recordsPerBlock <= 0) throw new IllegalArgumentException("Block too small for record size");
        this.file = new RandomAccessFile(filename, "rw");
        this.metadataFile = filename + ".meta";

        System.out.println("HeapFile init: block=" + clusterSize + ", recSize=" + recordTemplate.getSize() + ", recPerBlock=" + recordsPerBlock);

        loadBlockLists();
        if (file.length() > 0 && partiallyFreeBlocks.isEmpty() && emptyBlocks.isEmpty()) {
            initializeBlockLists();
        }
    }

    private void saveBlockLists() throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(metadataFile))) {
            dos.writeInt(clusterSize);
            dos.writeInt(recordsPerBlock);

            dos.writeInt(partiallyFreeBlocks.size());
            for (int b : partiallyFreeBlocks) dos.writeInt(b);

            dos.writeInt(emptyBlocks.size());
            for (int b : emptyBlocks) dos.writeInt(b);
        }
    }

    private void loadBlockLists() throws IOException {
        File meta = new File(metadataFile);
        if (!meta.exists()) return;
        try (DataInputStream dis = new DataInputStream(new FileInputStream(meta))) {
            int savedCluster = dis.readInt();
            int savedRecordsPerBlock = dis.readInt();
            if (savedCluster != clusterSize || savedRecordsPerBlock != recordsPerBlock) {
                System.out.println("Warning: metadata differs from current configuration");
            }
            partiallyFreeBlocks.clear();
            emptyBlocks.clear();
            int psize = dis.readInt();
            for (int i = 0; i < psize; i++) partiallyFreeBlocks.add(dis.readInt());
            int esize = dis.readInt();
            for (int i = 0; i < esize; i++) emptyBlocks.add(dis.readInt());
            System.out.println("Loaded metadata: partial=" + partiallyFreeBlocks + ", empty=" + emptyBlocks);
        } catch (EOFException e) {
            System.out.println("Metadata corrupted; ignoring");
        }
    }

    private void initializeBlockLists() throws IOException {
        int blockCount = getBlockCount();
        for (int i = 0; i < blockCount; i++) {
            Block<T> b = readBlock(i);
            updateBlockLists(i, b);
        }
    }

    private void updateBlockLists(int blockIndex, Block<T> block) {
        partiallyFreeBlocks.remove(Integer.valueOf(blockIndex));
        emptyBlocks.remove(Integer.valueOf(blockIndex));
        if (block.isEmpty()) {
            emptyBlocks.add(blockIndex);
        } else if (block.hasSpace()) {
            partiallyFreeBlocks.add(blockIndex);
        }
        Collections.sort(partiallyFreeBlocks);
        Collections.sort(emptyBlocks);
    }

    public int insert(T record) throws IOException {
        int blockIndex = findBestBlockForInsert();
        Block<T> block = readBlock(blockIndex);
        int slot = block.addRecord(record);
        if (slot < 0) {
            blockIndex = getBlockCount();
            block = new Block<>(blockIndex, clusterSize, recordTemplate);
            block.addRecord(record);
        }
        writeBlock(blockIndex, block);
        updateBlockLists(blockIndex, block);
        saveBlockLists();
        return blockIndex;
    }

    public T get(int blockIndex, T pattern) throws IOException {
        checkBlockIndex(blockIndex);
        Block<T> block = readBlock(blockIndex);
        return block.findRecord(pattern);
    }

    public boolean delete(int blockIndex, T pattern) throws IOException {
        checkBlockIndex(blockIndex);
        Block<T> block = readBlock(blockIndex);
        int before = block.getValidCount();
        boolean removed = block.deleteRecord(pattern);

        if (removed) {
            writeBlock(blockIndex, block);
            updateBlockLists(blockIndex, block);

            // NOVÉ: Skontrolovať či sa má odstrániť prázdny blok z konca
            if (block.isEmpty() && isLastBlock(blockIndex)) {
                removeEmptyBlocksFromEnd();
            }

            saveBlockLists();
            System.out.println("Deleted record from block " + blockIndex + " (" + before + " -> " + block.getValidCount() + ")");
        }
        return removed;
    }

    // NOVÁ METÓDA: Odstránenie VŠETKÝCH prázdnych blokov z konca súboru
    private void removeEmptyBlocksFromEnd() throws IOException {
        int blockCount = getBlockCount();
        if (blockCount == 0) return;

        // Nájdeme posledný neprázdny blok
        int lastNonEmptyBlock = -1;
        for (int i = blockCount - 1; i >= 0; i--) {
            Block<T> block = readBlock(i);
            if (!block.isEmpty()) {
                lastNonEmptyBlock = i;
                break;
            }
        }

        // Určíme novú veľkosť súboru
        int newBlockCount;
        if (lastNonEmptyBlock == -1) {
            // Všetky bloky sú prázdne - ponecháme úplne prázdny súbor (0 blokov)
            newBlockCount = 0;
        } else {
            // Ponecháme všetky bloky až po posledný neprázdny
            newBlockCount = lastNonEmptyBlock + 1;
        }

        // Zmenšíme súbor na potrebnú veľkosť
        if (newBlockCount < blockCount) {
            long newLength = (long) newBlockCount * clusterSize;
            file.setLength(newLength);
            System.out.println("Trimmed file from " + blockCount + " to " + newBlockCount + " blocks");

            // Aktualizujeme zoznamy voľných blokov
            emptyBlocks.clear();
            partiallyFreeBlocks.removeIf(index -> index >= newBlockCount);

            // Ak je súbor úplne prázdny, vymažeme aj metadata
            if (newBlockCount == 0) {
                new File(metadataFile).delete();
            }
        }
    }

    // NOVÁ METÓDA: Kontrola či je blok posledný v súbore
    private boolean isLastBlock(int blockIndex) throws IOException {
        return blockIndex == getBlockCount() - 1;
    }

    // NOVÁ METÓDA: Vynútené trimovanie prázdnych blokov (pre GUI)
    public void trimEmptyBlocks() throws IOException {
        removeEmptyBlocksFromEnd();
        saveBlockLists();
    }

    public Block<T> readBlock(int blockIndex) throws IOException {
        long pos = (long) blockIndex * clusterSize;
        if (pos >= file.length()) {
            return new Block<>(blockIndex, clusterSize, recordTemplate);
        }
        file.seek(pos);
        byte[] data = new byte[clusterSize];
        int read = file.read(data);
        if (read < clusterSize) {
            for (int i = read; i < data.length; i++) data[i] = 0;
        }
        Block<T> block = new Block<>(blockIndex, clusterSize, recordTemplate);
        block.fromBytes(data);
        return block;
    }

    private void writeBlock(int blockIndex, Block<T> block) throws IOException {
        long pos = (long) blockIndex * clusterSize;
        if (pos + clusterSize > file.length()) file.setLength(pos + clusterSize);
        file.seek(pos);
        file.write(block.getBytes());
    }

    private int findBestBlockForInsert() throws IOException {
        if (!partiallyFreeBlocks.isEmpty()) {
            return partiallyFreeBlocks.getFirst();
        }
        if (!emptyBlocks.isEmpty()) {
            return emptyBlocks.removeFirst();
        }
        return getBlockCount();
    }

    public int getBlockCount() throws IOException {
        return (int) (file.length() / clusterSize);
    }

    private void checkBlockIndex(int idx) throws IOException {
        int bc = getBlockCount();
        if (idx < 0 || idx >= Math.max(bc, 1)) {
            if (idx >= bc) throw new IllegalArgumentException("Invalid block index: " + idx);
        }
    }

    public String displayAll() throws IOException {
        StringBuilder sb = new StringBuilder();
        int bc = getBlockCount();
        sb.append("File: ").append(filename).append("\nBlocks: ").append(bc).append("\n");
        for (int i = 0; i < bc; i++) {
            Block<T> b = readBlock(i);
            sb.append("Block ").append(i).append(": valid=").append(b.getValidCount())
                    .append("/").append(b.getRecordsPerBlock()).append("\n");
            List<T> recs = b.getRecords();
            for (int s = 0; s < recs.size(); s++) sb.append("  ").append(recs.get(s)).append("\n");
        }
        sb.append("Partially free: ").append(partiallyFreeBlocks).append("\n");
        sb.append("Empty blocks: ").append(emptyBlocks).append("\n");
        return sb.toString();
    }

    public void close() throws IOException {
        saveBlockLists();
        file.close();
    }

    public int getRecordsPerBlock() {
        return recordsPerBlock;
    }

    public List<Integer> getPartiallyFreeBlocks() {
        return new ArrayList<>(partiallyFreeBlocks);
    }

    public List<Integer> getEmptyBlocks() {
        return new ArrayList<>(emptyBlocks);
    }

    public String getBitmapVisualization(int blockIndex) throws IOException {
        Block<T> block = readBlock(blockIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < block.getRecordsPerBlock(); i++) {
            sb.append(i < block.getValidCount() ? "1" : "0");
            if (i < block.getRecordsPerBlock() - 1) sb.append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
}