package hash;

import heap.Block;
import heap.HeapFile;
import data.Record;
import overflow.OverflowBlock;
import overflow.OverflowFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LinearHashing<T extends Record<T>> extends HeapFile<T> {
    private int level = 0;
    private int splitPointer = 0;
    // total number of valid records in the file
    private int totalRecords = 0;
    // initial number of blocks
    private final int M;

    private static final double D_MAX = 0.8;
    private static final double D_MIN = 0.4;

    private final OverflowFile<T> overflowFile;
    private final String metadataFile;
    private boolean metadataChanged = false;

    public LinearHashing(String filename, int primaryBlockSize, int overflowBlockSize, T recordTemplate, int initialM) throws IOException {
        super(filename, primaryBlockSize, recordTemplate);
        this.M = initialM;
        this.metadataFile = filename + ".meta";
        this.overflowFile = new OverflowFile<>(filename + ".overflow", overflowBlockSize, recordTemplate);
        this.loadMetadata();
        if (this.getFile().length() == 0) {
            this.initializeFile();
        }
    }

    /**
     * Loads metadata from the metadata file.
     */
    private void loadMetadata() throws IOException {
        File metadata = new File(this.metadataFile);
        if (!metadata.exists()) {
            this.metadataChanged = true;
            return;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(metadata))) {
            this.level = dis.readInt();
            this.splitPointer = dis.readInt();
            this.totalRecords = dis.readInt();
        }
    }

    /**
     * Saves current metadata to the metadata file.
     */
    private void saveMetadata() throws IOException {
        if (!this.metadataChanged) return;

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(this.metadataFile))) {
            dos.writeInt(this.level);
            dos.writeInt(this.splitPointer);
            dos.writeInt(this.totalRecords);
        }
        this.metadataChanged = false;
    }

    /**
     * Initializes the hash file with initial empty blocks.
     */
    private void initializeFile() throws IOException {
        for (int i = 0; i < this.M; i++) {
            LHBlock<T> block = (LHBlock<T>) this.createBlock(i);
            this.writeBlock(i, block);
        }
        this.metadataChanged = true;
    }

    /**
     * Gets the overflow file instance.
     */
    public OverflowFile<T> getOverflowFile() {
        return this.overflowFile;
    }

    /**
     * Calculates the current number of primary blocks.
     */
    private int primaryBlocksCount() {
        return this.M * (int) Math.pow(2, this.level) + this.splitPointer;
    }

    /**
     * First hash function (h_i) using current level.
     */
    public int hash0(String key) {
        int mod = this.M * (int) Math.pow(2, this.level);
        return Math.abs(key.hashCode()) % mod;
    }

    /**
     * Second hash function (h_{i+1}) using next level.
     */
    public int hash1(String key) {
        int mod = this.M * (int) Math.pow(2, this.level + 1);
        return Math.abs(key.hashCode()) % mod;
    }

    /**
     * Gets the number of initial blocks.
     */
    public int getM() {
        return this.M;
    }

    /**
     * Inserts a record into the hash file.
     */
    public void insert(T record, String key) throws IOException {
        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        boolean insertedInPrimary = (primaryBlock.addRecord(record) != -1);

        if (!insertedInPrimary) {
            // insert into overflow chain
            int firstOverflow = primaryBlock.getNextOverflow();
            int newFirstOverflow = this.overflowFile.addToChain(firstOverflow, record);

            if (firstOverflow == -1) {
                primaryBlock.setNextOverflow(newFirstOverflow);
            }

            this.updateOverflowCount(primaryBlock);
        }
        this.writeBlock(blockIndex, primaryBlock);
        this.totalRecords++;
        this.metadataChanged = true;

        // check and perform splits based on load factor
        while (this.getLoadFactor() > D_MAX) {
            this.split();
        }
    }

    /**
     * Finds a record by key.
     */
    public T get(String key) throws IOException {
        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        // search in primary block
        T result = primaryBlock.findRecord(this.createPattern(key));
        if (result != null) {
            return result;
        }

        // search in overflow chain
        int firstOverflow = primaryBlock.getNextOverflow();
        if (firstOverflow != -1) {
            return this.overflowFile.findInChain(firstOverflow, key);
        }

        return null;
    }

    /**
     * Deletes a record by key.
     */
    public boolean delete(String key) throws IOException {
        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        boolean deleted = primaryBlock.deleteRecord(this.createPattern(key));

        if (deleted) {
            this.writeBlock(blockIndex, primaryBlock);
            this.totalRecords--;
            this.metadataChanged = true;

            this.compactBlock(blockIndex);
        } else {
            // delete from overflow chain
            int firstOverflow = primaryBlock.getNextOverflow();
            if (firstOverflow != -1) {
                // pass-by-reference
                int[] overflowHolder = new int[]{firstOverflow};
                int overflowRecordsBefore = primaryBlock.getOverflowRecordCount();

                deleted = this.overflowFile.deleteFromChain(overflowHolder, key);

                if (deleted) {
                    primaryBlock.setNextOverflow(overflowHolder[0]);

                    int chainLength = this.calculateChainLength(primaryBlock.getNextOverflow());

                    int actualOverflowRecords = 0;
                    int currentOverflow = primaryBlock.getNextOverflow();
                    while (currentOverflow != -1) {
                        OverflowBlock<T> overflowBlock = this.overflowFile.readOverflowBlock(currentOverflow);
                        actualOverflowRecords += overflowBlock.getValidCount();
                        currentOverflow = overflowBlock.getNextOverflow();
                    }

                    primaryBlock.setOverflowRecordCount(actualOverflowRecords);
                    primaryBlock.setChainLength(chainLength);

                    this.writeBlock(blockIndex, primaryBlock);
                    this.totalRecords--;
                    this.metadataChanged = true;

                    this.compactBlock(blockIndex);
                }
            }
        }

        if (deleted) {
            // check and perform merge based on load factor
            while (this.primaryBlocksCount() > this.M && this.getLoadFactor() < D_MIN) {
                this.merge();
            }
        }

        return deleted;
    }

    /**
     * Determines the target block for a given key.
     */
    public int getTargetBlock(String key) {
        int h0 = this.hash0(key);
        // if h0 < splitPointer, use h1 (block has been split)
        return (h0 < this.splitPointer) ? this.hash1(key) : h0;
    }

    /**
     * Splits the block pointed to by splitPointer.
     */
    private void split() throws IOException {
        int newBlockIndex = this.primaryBlocksCount();
        LHBlock<T> newBlock = (LHBlock<T>) this.createBlock(newBlockIndex);

        int blockToSplit = this.splitPointer;
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockToSplit);

        // get all records (primary + overflow)
        List<T> allRecords = new ArrayList<>(primaryBlock.getRecords());
        int firstOverflow = primaryBlock.getNextOverflow();
        if (firstOverflow != -1) {
            allRecords.addAll(this.overflowFile.collectAllFromChain(firstOverflow));
            this.overflowFile.clearChain(firstOverflow);
        }

        // clear the original block
        primaryBlock.clearRecords();
        primaryBlock.setNextOverflow(-1);
        primaryBlock.setOverflowRecordCount(0);
        primaryBlock.setChainLength(0);

        for (T record : allRecords) {
            int h1 = this.hash1(record.getKey());

            // if h1 == index of split block -> stays in original
            if (h1 == blockToSplit) {
                if (primaryBlock.addRecord(record) == -1) {
                    int fo = primaryBlock.getNextOverflow();
                    int nf = this.overflowFile.addToChain(fo, record);
                    if (fo == -1) {
                        primaryBlock.setNextOverflow(nf);
                    }
                }
            } else {
                // otherwise -> goes to new block
                if (newBlock.addRecord(record) == -1) {
                    int fo = newBlock.getNextOverflow();
                    int nf = this.overflowFile.addToChain(fo, record);
                    if (fo == -1) {
                        newBlock.setNextOverflow(nf);
                    }
                }
            }
        }

        this.updateOverflowCount(primaryBlock);
        this.updateOverflowCount(newBlock);

        this.writeBlock(blockToSplit, primaryBlock);
        this.writeBlock(newBlockIndex, newBlock);

        this.splitPointer++;
        this.metadataChanged = true;

        // check if it's full expansion
        if (this.splitPointer >= this.M * (int) Math.pow(2, this.level)) {
            this.level++;
            this.splitPointer = 0;
        }
    }

    /**
     * Reduces file size when load factor is too low.
     */
    private void merge() throws IOException {
        int sourceBlockIndex, targetBlockIndex;

        if (this.splitPointer == 0) {
            sourceBlockIndex = this.M * (int) Math.pow(2, this.level) - 1;
            targetBlockIndex = this.M * (int) Math.pow(2, this.level - 1) - 1;
        } else {
            sourceBlockIndex = this.splitPointer + this.M * (int) Math.pow(2, this.level) - 1;
            targetBlockIndex = this.splitPointer - 1;
        }

        // move records from source to target block
        this.moveRecords(sourceBlockIndex, targetBlockIndex);

        LHBlock<T> block = this.readPrimaryBlock(sourceBlockIndex);
        block.clearRecords();
        block.setNextOverflow(-1);
        block.setOverflowRecordCount(0);
        block.setChainLength(0);
        this.writeBlock(sourceBlockIndex, block);

        if (this.splitPointer == 0) this.level--;
        this.splitPointer = targetBlockIndex;
        this.metadataChanged = true;

        LHBlock<T> targetBlock = this.readPrimaryBlock(targetBlockIndex);
        this.updateOverflowCount(targetBlock);
        this.writeBlock(targetBlockIndex, targetBlock);

        this.removeEmptyBlocksFromEnd();
        this.metadataChanged = true;
    }

    /**
     * Updates overflowRecordCount in block
     */
    private void updateOverflowCount(LHBlock<T> block) throws IOException {
        int actualOverflowRecords = 0;
        int currentOverflow = block.getNextOverflow();
        int overflowBlockCount = this.overflowFile.getBlockCount();

        while (currentOverflow != -1) {
            if (currentOverflow < 0 || currentOverflow >= overflowBlockCount) {
                break;
            }

            OverflowBlock<T> overflowBlock = this.overflowFile.readOverflowBlock(currentOverflow);
            actualOverflowRecords += overflowBlock.getValidCount();
            currentOverflow = overflowBlock.getNextOverflow();
        }
        block.setOverflowRecordCount(actualOverflowRecords);
        block.setChainLength(this.calculateChainLength(block.getNextOverflow()));
    }


    /**
     * Tries to free as many blocks as possible
     */
    private void compactBlock(int primaryBlockIndex) throws IOException {
        LHBlock<T> primaryBlock = this.readPrimaryBlock(primaryBlockIndex);
        int firstOverflow = primaryBlock.getNextOverflow();
        int freeSpace = primaryBlock.getBlockSize() - primaryBlock.getValidCount();

        if (firstOverflow == -1 || freeSpace <= 0) {
            return;
        }

        List<T> overflowRecords = this.overflowFile.collectAllFromChain(firstOverflow);

        this.overflowFile.clearChain(firstOverflow);
        primaryBlock.setNextOverflow(-1);
        primaryBlock.setOverflowRecordCount(0);
        primaryBlock.setChainLength(0);

        List<T> allRecords = new ArrayList<>(primaryBlock.getRecords());
        primaryBlock.clearRecords();

        allRecords.addAll(overflowRecords);

        for (T record : allRecords) {
            if (primaryBlock.addRecord(record) == -1) {
                int fo = primaryBlock.getNextOverflow();
                int nf = this.overflowFile.addToChain(fo, record);
                if (fo == -1) {
                    primaryBlock.setNextOverflow(nf);
                }
            }
        }

        this.updateOverflowCount(primaryBlock);

        this.writeBlock(primaryBlockIndex, primaryBlock);
    }

    /**
     * Deletes empty blocks from the end of the hashfile
     */
    private void trimPrimaryBlocks() throws IOException {
        int blockCount = this.primaryBlocksCount();
        int totalBlocks = this.getBlockCount();

        if (totalBlocks > blockCount) {
            boolean allEmpty = true;
            for (int i = blockCount; i < totalBlocks; i++) {
                try {
                    Block<T> block = this.readBlock(i);
                    if (!block.isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            if (allEmpty) {
                this.removeEmptyBlocksFromEnd();
                this.metadataChanged = true;
            }
        }
    }

    /**
     * Moves all records from source block to target block.
     */
    private void moveRecords(int sourceIndex, int targetIndex) throws IOException {
        LHBlock<T> sourceBlock = this.readPrimaryBlock(sourceIndex);
        LHBlock<T> targetBlock = this.readPrimaryBlock(targetIndex);

        List<T> sourceRecords = new ArrayList<>(sourceBlock.getRecords());
        int sourceOverflow = sourceBlock.getNextOverflow();
        if (sourceOverflow != -1) {
            sourceRecords.addAll(this.overflowFile.collectAllFromChain(sourceOverflow));
            this.overflowFile.clearChain(sourceOverflow);
        }

        for (T record : sourceRecords) {
            if (targetBlock.addRecord(record) == -1) {
                // move to target block's overflow
                int firstOverflow = targetBlock.getNextOverflow();
                int newFirstOverflow = this.overflowFile.addToChain(firstOverflow, record);
                if (firstOverflow == -1) {
                    targetBlock.setNextOverflow(newFirstOverflow);
                }
            }
        }
        this.updateOverflowCount(targetBlock);
        this.writeBlock(targetIndex, targetBlock);
    }

    /**
     * Calculates the length of an overflow chain.
     */
    private int calculateChainLength(int firstOverflowIndex) throws IOException {
        if (firstOverflowIndex == -1) {
            return 0;
        }

        int length = 0;
        int currentIndex = firstOverflowIndex;

        while (currentIndex != -1) {
            length++;
            OverflowBlock<T> block = this.overflowFile.readOverflowBlock(currentIndex);
            currentIndex = block.getNextOverflow();
        }

        return length;
    }

    /**
     * Reads a primary block, creating it if it doesn't exist.
     */
    public LHBlock<T> readPrimaryBlock(int index) throws IOException {
        if (index >= this.getBlockCount()) {
            LHBlock<T> empty = (LHBlock<T>) this.createBlock(index);
            empty.clearRecords();
            empty.setNextOverflow(-1);
            empty.setOverflowRecordCount(0);
            return empty;
        }

        try {
            Block<T> genericBlock = this.readBlock(index);
            LHBlock<T> lhBlock = (LHBlock<T>) this.createBlock(index);
            lhBlock.fromBytes(genericBlock.getBytes());
            return lhBlock;
        } catch (Exception e) {
            LHBlock<T> empty = (LHBlock<T>) this.createBlock(index);
            empty.clearRecords();
            empty.setNextOverflow(-1);
            empty.setOverflowRecordCount(0);
            return empty;
        }
    }

    /**
     * Creates a new LHBlock.
     */
    @Override
    protected Block<T> createBlock(int blockIndex) {
        return new LHBlock<>(blockIndex, this.getClusterSize(), this.getRecordTemplate());
    }

    /**
     * Creates a search pattern for record operations.
     */
    private T createPattern(String key) {
        try {
            T pattern = this.getRecordTemplate().createClass();
            pattern.setKey(key);
            return pattern;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create search pattern", e);
        }
    }

    /**
     * Calculates the current load factor of the hash file.
     * Load factor = total records / total capacity (primary + overflow).
     */
    private double getLoadFactor() {
        int primaryBlocks = this.primaryBlocksCount();
        int primaryCapacity = primaryBlocks * this.getRecordsPerBlock();

        int overflowBlocks = this.overflowFile.getUsedOverflowBlocks();
        int overflowCapacity = overflowBlocks * this.overflowFile.getRecordsPerBlock();

        int totalCapacity = primaryCapacity + overflowCapacity;

        return totalCapacity == 0 ? 0 : (double) this.totalRecords / totalCapacity;
    }

    /**
     * Gets statistics about the hash file.
     */
    public LinearHashingStats getStats() throws IOException {
        LinearHashingStats stats = new LinearHashingStats();
        stats.level = this.level;
        stats.splitPointer = this.splitPointer;
        stats.totalRecords = this.totalRecords;
        stats.totalBlocks = this.primaryBlocksCount();
        stats.loadFactor = this.getLoadFactor();
        stats.overflowBlocks = this.overflowFile.getUsedOverflowBlocks();

        for (int i = 0; i < stats.totalBlocks; i++) {
            LHBlock<T> block = this.readPrimaryBlock(i);
            if (!block.isEmpty()) {
                stats.nonEmptyBlocks++;
                stats.totalOverflowRecords += block.getOverflowRecordCount();
            }
        }

        return stats;
    }

    /**
     * Statistics container class for Linear Hashing.
     */
    public static class LinearHashingStats {
        public int level;
        public int splitPointer;
        public int totalRecords;
        public int totalBlocks;
        public int nonEmptyBlocks;
        public int overflowBlocks;
        public int totalOverflowRecords;
        public double loadFactor;
    }

    /**
     * Closes the hash file.
     */
    @Override
    public void close() throws IOException {
        this.saveMetadata();
        if (this.overflowFile != null) this.overflowFile.close();
        super.getFile().close();
    }
}