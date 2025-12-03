package overflow;

import heap.Block;
import heap.HeapFile;
import data.Record;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class OverflowFile<T extends Record<T>> extends HeapFile<T> {
    private final T template;
    private int usedOverflowBlocks = 0;

    public OverflowFile(String filename, int blockSize, T template) throws IOException {
        super(filename, blockSize, template);
        this.template = template;

        // initialize the count of used overflow blocks
        for (int i = 0; i < this.getBlockCount(); i++) {
            OverflowBlock<T> block = this.readOverflowBlock(i);
            if (!block.isEmpty()) {
                this.usedOverflowBlocks++;
            }
        }
    }

    /**
     * Creates a new overflow block.
     */
    @Override
    protected OverflowBlock<T> createBlock(int blockIndex) {
        return new OverflowBlock<>(blockIndex, this.getClusterSize(), this.getRecordTemplate());
    }

    /**
     * Reads an overflow block from disk.
     * If the block doesn't exist, creates a new empty block and writes it to disk.
     */
    public OverflowBlock<T> readOverflowBlock(int index) throws IOException {
        if (index >= this.getBlockCount()) {
            OverflowBlock<T> empty = this.createBlock(index);
            empty.clearRecords();
            empty.setNextOverflow(-1);
            return empty;
        }

        try {
            Block<T> genericBlock = super.readBlock(index);
            OverflowBlock<T> overflowBlock = this.createBlock(index);
            overflowBlock.fromBytes(genericBlock.getBytes());
            return overflowBlock;
        } catch (Exception e) {
            return this.createBlock(index);
        }
    }

    /**
     * Writes an overflow block to disk using parent's writeBlock method.
     */
    public void writeOverflowBlock(OverflowBlock<T> block) throws IOException {
        super.writeBlock(block.getAddress(), block);
    }

    /**
     * Allocates a new overflow block at the end of the file.
     */
    private int allocateOverflowBlock() throws IOException {
        int newIndex = this.getBlockCount();
        OverflowBlock<T> newBlock = this.createBlock(newIndex);
        super.writeBlock(newIndex, newBlock);
        this.usedOverflowBlocks++;
        return newIndex;
    }

    /**
     * Marks an overflow block as empty.
     */
    private void markOverflowBlockAsEmpty(int index) throws IOException {
        OverflowBlock<T> block = this.readOverflowBlock(index);
        block.clearRecords();
        block.setNextOverflow(-1);
        this.writeOverflowBlock(block);
        this.usedOverflowBlocks--;

        if (index == this.getBlockCount() - 1) {
            this.removeEmptyBlocksFromEnd();
        }
    }

    /**
     * Adds a record to overflow chain. If the chain doesn't exist, creates a new chain.
     */
    public int addToChain(int firstOverflowIndex, T record) throws IOException {
        if (firstOverflowIndex == -1) {
            // next block doesn't exist
            int newBlockIndex = this.allocateOverflowBlock();
            OverflowBlock<T> newBlock = this.readOverflowBlock(newBlockIndex);

            if (newBlock.addRecord(record) != -1) {
                this.writeOverflowBlock(newBlock);
                return newBlockIndex;
            } else {
                // if failed to add, mark as empty
                this.markOverflowBlockAsEmpty(newBlockIndex);
                throw new IOException("Failed to add record to new overflow block");
            }
        }

        // if chain exists, find last block in chain and try to add record
        int currentIndex = firstOverflowIndex;
        OverflowBlock<T> currentBlock = null;
        OverflowBlock<T> lastBlock = null;

        while (currentIndex != -1) {
            currentBlock = this.readOverflowBlock(currentIndex);

            if (currentBlock.addRecord(record) != -1) {
                this.writeOverflowBlock(currentBlock);
                return firstOverflowIndex;
            }

            lastBlock = currentBlock;
            currentIndex = currentBlock.getNextOverflow();
        }

        // if all blocks are full, add new block to end of chain
        int newBlockIndex = this.allocateOverflowBlock();
        OverflowBlock<T> newBlock = this.readOverflowBlock(newBlockIndex);

        if (newBlock.addRecord(record) != -1) {
            lastBlock.setNextOverflow(newBlockIndex);
            this.writeOverflowBlock(lastBlock);
            this.writeOverflowBlock(newBlock);
            return firstOverflowIndex;
        } else {
            // if failed to add, mark as empty
            this.markOverflowBlockAsEmpty(newBlockIndex);
            throw new IOException("Failed to add record to new overflow block");
        }
    }

    /**
     * Finds a record in an overflow chain by key.
     */
    public T findInChain(int firstOverflowIndex, String key) throws IOException {
        int currentIndex = firstOverflowIndex;

        while (currentIndex != -1) {
            OverflowBlock<T> block = this.readOverflowBlock(currentIndex);
            T foundRecord = block.findRecord(this.createPattern(key));

            if (foundRecord != null) {
                return foundRecord;
            }

            currentIndex = block.getNextOverflow();
        }

        return null;
    }

    /**
     * Deletes a record from an overflow chain.
     * If a block becomes empty, it's removed from the chain but kept in the file.
     */
    public boolean deleteFromChain(int[] firstOverflowIndexHolder, String key) throws IOException {
        int currentIndex = firstOverflowIndexHolder[0];
        int previousIndex = -1;
        boolean deleted = false;

        while (currentIndex != -1) {
            OverflowBlock<T> currentBlock = this.readOverflowBlock(currentIndex);
            deleted = currentBlock.deleteRecord(this.createPattern(key));

            if (deleted) {
                // if block became completely empty, remove it from chain
                if (currentBlock.isEmpty()) {
                    int nextIndex = currentBlock.getNextOverflow();

                    if (previousIndex == -1) {
                        // removing the head of the chain
                        firstOverflowIndexHolder[0] = nextIndex;
                    } else {
                        // link previous block to the next one
                        OverflowBlock<T> previousBlock = this.readOverflowBlock(previousIndex);
                        previousBlock.setNextOverflow(nextIndex);
                        this.writeOverflowBlock(previousBlock);
                    }

                    this.markOverflowBlockAsEmpty(currentIndex);
                } else {
                    this.writeOverflowBlock(currentBlock);
                }
                break;
            }

            previousIndex = currentIndex;
            currentIndex = currentBlock.getNextOverflow();
        }

        return deleted;
    }

    /**
     * Returns the number of currently used overflow blocks.
     */
    public int getUsedOverflowBlocks() {
        return this.usedOverflowBlocks;
    }

    /**
     * Collects all records from an overflow chain.
     */
    public List<T> collectAllFromChain(int firstOverflowIndex) throws IOException {
        List<T> allRecords = new ArrayList<>();
        int currentIndex = firstOverflowIndex;

        while (currentIndex != -1) {
            OverflowBlock<T> block = this.readOverflowBlock(currentIndex);
            allRecords.addAll(block.getRecords());
            currentIndex = block.getNextOverflow();
        }

        return allRecords;
    }

    /**.
     * Marks all blocks in the chain as empty.
     */
    public void clearChain(int firstOverflowIndex) throws IOException {
        int currentIndex = firstOverflowIndex;

        while (currentIndex != -1) {
            OverflowBlock<T> block = this.readOverflowBlock(currentIndex);
            int nextIndex = block.getNextOverflow();

            this.markOverflowBlockAsEmpty(currentIndex);
            currentIndex = nextIndex;
        }
    }

    /**
     * Creates a search pattern for record operations.
     */
    private T createPattern(String key) {
        try {
            T pattern = this.template.createClass();
            pattern.setKey(key);
            return pattern;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create search pattern", e);
        }
    }
}