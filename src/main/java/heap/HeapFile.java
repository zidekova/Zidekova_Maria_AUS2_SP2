package heap;

import data.Record;

import java.io.*;
import java.util.*;

public class HeapFile<T extends Record<T>> {
    private final RandomAccessFile file;
    private final Block<T> templateBlock;
    private final LinkedList<Integer> partiallyFreeBlocks = new LinkedList<>();
    private final LinkedList<Integer> emptyBlocks = new LinkedList<>();
    private final String metadataFile; // to track block occupancy
    private boolean metadataChanged = false;

    public HeapFile(String filename, int clusterSize, T recordTemplate) throws IOException {
        if (clusterSize < recordTemplate.getSize()) {
            throw new IllegalArgumentException(
                    "Cluster size (" + clusterSize + " bytes) je menší ako veľkosť záznamu (" +
                            recordTemplate.getSize() + " bytes). Cluster musí byť aspoň " +
                            recordTemplate.getSize() + " bytes."
            );
        }

        this.templateBlock = new Block<>(0, clusterSize, recordTemplate);
        this.file = new RandomAccessFile(filename, "rw");
        this.metadataFile = filename + ".meta";

        System.out.println("HeapFile init: block=" + getClusterSize() +
                ", recSize=" + getRecordTemplate().getSize() +
                ", recPerBlock=" + getRecordsPerBlock());

        // load existing block occupancy metadata
        this.loadBlockLists();
        if (this.file.length() > 0 && this.partiallyFreeBlocks.isEmpty() && this.emptyBlocks.isEmpty()) {
            for (int i = 0; i < this.getBlockCount(); i++) {
                Block<T> b = readBlock(i);
                this.updateBlockLists(i, b);
            }
            this.metadataChanged = true;
        }
    }

    /**
     * Saves current block occupancy lists to metadata file
     */
    private void saveBlockLists() throws IOException {
        if (!this.metadataChanged) return;

        int blockCount = this.getBlockCount();
        this.partiallyFreeBlocks.removeIf(idx -> idx < 0 || idx >= blockCount);
        this.emptyBlocks.removeIf(idx -> idx < 0 || idx >= blockCount);

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(this.metadataFile))) {
            dos.writeInt(this.getClusterSize());
            dos.writeInt(this.getRecordsPerBlock());

            dos.writeInt(this.partiallyFreeBlocks.size());
            for (int b : this.partiallyFreeBlocks) dos.writeInt(b);

            dos.writeInt(this.emptyBlocks.size());
            for (int b : this.emptyBlocks) dos.writeInt(b);
        }
        this.metadataChanged = false;
    }

    /**
     * Loads block occupancy lists from metadata file
     */
    private void loadBlockLists() throws IOException {
        File meta = new File(this.metadataFile);
        if (!meta.exists()) {
            this.metadataChanged = true;
            return;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(meta))) {
            int savedCluster = dis.readInt();
            int savedRecordsPerBlock = dis.readInt();

            if (savedCluster != this.getClusterSize() || savedRecordsPerBlock != this.getRecordsPerBlock()) {
                this.partiallyFreeBlocks.clear();
                this.emptyBlocks.clear();
                this.metadataChanged = true;
                return;
            }

            this.partiallyFreeBlocks.clear();
            int pSize = dis.readInt();
            for (int i = 0; i < pSize; i++) {
                this.partiallyFreeBlocks.add(dis.readInt());
            }

            this.emptyBlocks.clear();
            int eSize = dis.readInt();
            for (int i = 0; i < eSize; i++) {
                this.emptyBlocks.add(dis.readInt());
            }

            int blockCount = this.getBlockCount();
            this.partiallyFreeBlocks.removeIf(idx -> idx < 0 || idx >= blockCount);
            this.emptyBlocks.removeIf(idx -> idx < 0 || idx >= blockCount);

            Collections.sort(this.partiallyFreeBlocks);
            Collections.sort(this.emptyBlocks);

        } catch (EOFException e) {
            this.partiallyFreeBlocks.clear();
            this.emptyBlocks.clear();
            this.metadataChanged = true;
        } catch (Exception e) {
            System.out.println("Error loading metadata: " + e.getMessage() + "; recreating");
            this.partiallyFreeBlocks.clear();
            this.emptyBlocks.clear();
            this.metadataChanged = true;
        }
    }

    /**
     * Updates block occupancy lists based on a blocks current state
     */
    private void updateBlockLists(int blockIndex, Block<T> block) {
        boolean isEmpty = block.isEmpty();
        boolean hasSpace = block.hasSpace() && !isEmpty;

        this.emptyBlocks.remove((Integer) blockIndex);
        this.partiallyFreeBlocks.remove((Integer) blockIndex);

        if (isEmpty) {
            if (!this.emptyBlocks.contains(blockIndex)) this.emptyBlocks.add(blockIndex);
        } else if (hasSpace) {
            if (!this.partiallyFreeBlocks.contains(blockIndex)) this.partiallyFreeBlocks.add(blockIndex);
        }
        Collections.sort(this.emptyBlocks);
        Collections.sort(this.partiallyFreeBlocks);

        this.metadataChanged = true;
    }

    /**
     * Inserts a record into the heap file
     * @return block index where record was inserted
     */
    public int insert(T record) throws IOException {
        int blockIndex = this.findBestBlockForInsert();
        Block<T> block = this.readBlock(blockIndex);
        int slot = block.addRecord(record);
        if (slot < 0) {
            blockIndex = this.getBlockCount();
            block = this.createBlock(blockIndex);
            block.addRecord(record);
        }
        this.writeBlock(blockIndex, block);
        this.updateBlockLists(blockIndex, block);
        return blockIndex;
    }

    /**
     * Retrieves a record matching the record from specified block
     * @return matching record or null if not found
     */
    public T get(int blockIndex, T record) throws IOException {
        this.checkBlockIndex(blockIndex);
        Block<T> block = this.readBlock(blockIndex);
        return block.findRecord(record);
    }

    /**
     * Deletes a record matching the pattern from specified block
     * Automatically trims empty blocks from end of file and updates metadata
     * @return true if record was found and deleted, false otherwise
     */
    public boolean delete(int blockIndex, T pattern) throws IOException {
        this.checkBlockIndex(blockIndex);
        Block<T> block = this.readBlock(blockIndex);
        boolean removed = block.deleteRecord(pattern);

        if (removed) {
            this.writeBlock(blockIndex, block);
            this.updateBlockLists(blockIndex, block);

            // if last block became empty, trim empty blocks from end of file
            if (block.isEmpty() && isLastBlock(blockIndex)) {
                this.removeEmptyBlocksFromEnd();
            }
        }
        return removed;
    }

    /**
     * Removes empty blocks from the end of the file
     */
    protected void removeEmptyBlocksFromEnd() throws IOException {
        int blockCount = this.getBlockCount();
        if (blockCount == 0) return;

        int lastNonEmptyBlock = -1;
        for (int i = blockCount - 1; i >= 0; i--) {
            Block<T> block = this.readBlock(i);
            if (!block.isEmpty()) {
                lastNonEmptyBlock = i;
                break;
            }
        }

        int newBlockCount;
        if (lastNonEmptyBlock == -1) {
            // whole file is empty
            newBlockCount = 0;
        } else {
            newBlockCount = lastNonEmptyBlock + 1;
        }

        // truncate file if we can remove empty blocks from end
        if (newBlockCount < blockCount) {
            long newLength = (long) newBlockCount * this.getClusterSize();
            this.file.setLength(newLength);

            this.emptyBlocks.removeIf(index -> index >= newBlockCount);
            this.partiallyFreeBlocks.removeIf(index -> index >= newBlockCount);

            this.metadataChanged = true;

            // remove metadata file if entire file is empty
            if (newBlockCount == 0) {
                new File(this.metadataFile).delete();
            }
        }
    }

    /**
     * Checks if block is the last one in the file
     */
    private boolean isLastBlock(int blockIndex) throws IOException {
        return blockIndex == this.getBlockCount() - 1;
    }

    /**
     * Reads a block from disk at specified index
     */
    public Block<T> readBlock(int blockIndex) throws IOException {
        long pos = (long) blockIndex * this.getClusterSize();

        if (pos >= this.file.length()) {
            return this.createBlock(blockIndex);
        }

        this.file.seek(pos);
        byte[] data = new byte[this.getClusterSize()];
        int read = this.file.read(data);

        if (read < data.length) {
            for (int i = read; i < data.length; i++) data[i] = ' ';
        }

        Block<T> block = this.createBlock(blockIndex);
        block.fromBytes(data);

        return block;
    }

    /**
     * Writes a block to disk at specified index
     */
    public void writeBlock(int blockIndex, Block<T> block) throws IOException {
        long pos = (long) blockIndex * this.getClusterSize();

        if (pos + this.getClusterSize() > this.file.length()) {
            // extend file
            this.file.setLength(pos + this.getClusterSize());
        }

        this.file.seek(pos);
        this.file.write(block.getBytes());
    }

    /**
     * Creates new blocks
     */
    protected Block<T> createBlock(int blockIndex) {
        return new Block<>(blockIndex, this.getClusterSize(), this.getRecordTemplate());
    }

    /**
     * Finds the optimal block for insertion using occupancy strategy:
     * 1. Partially free blocks first
     * 2. Empty blocks second
     * 3. New block at end of file
     */
    private int findBestBlockForInsert() throws IOException {
        int blockCount = this.getBlockCount();

        Iterator<Integer> itPart = this.partiallyFreeBlocks.iterator();
        while (itPart.hasNext()) {
            int idx = itPart.next();
            if (idx < 0 || idx >= blockCount) {
                itPart.remove();
                continue;
            }
            Block<T> block = this.readBlock(idx);
            if (block.hasSpace() && !block.isEmpty()) {
                return idx;
            } else {
                itPart.remove();
            }
        }

        Iterator<Integer> itEmpty = this.emptyBlocks.iterator();
        while (itEmpty.hasNext()) {
            int idx = itEmpty.next();
            if (idx < 0 || idx >= blockCount) {
                itEmpty.remove();
                continue;
            }
            Block<T> block = this.readBlock(idx);
            if (block.isEmpty()) {
                return idx;
            } else {
                itEmpty.remove();
            }
        }

        return blockCount;
    }

    /**
     * Returns count of blocks currently allocated in the file
     */
    public int getBlockCount() throws IOException {
        return (int) (this.file.length() / this.getClusterSize());
    }

    /**
     * Validates that block index is within current file bounds
     */
    private void checkBlockIndex(int idx) throws IOException {
        int bc = this.getBlockCount();
        if (idx < 0 || idx >= Math.max(bc, 1)) {
            if (idx >= bc) throw new IllegalArgumentException("Invalid block index: " + idx);
        }
    }

    /**
     * Closes the file and persists metadata
     */
    public void close() throws IOException {
        this.saveBlockLists();
        this.file.close();
    }

    public int getRecordsPerBlock() {
        return this.templateBlock.getRecordsPerBlock();
    }

    public int getClusterSize() {
        return this.templateBlock.getBlockSize();
    }

    protected T getRecordTemplate() {
        return this.templateBlock.getRecordTemplate();
    }

    protected RandomAccessFile getFile() {
        return file;
    }
}