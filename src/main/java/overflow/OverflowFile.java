package overflow;

import heap.Block;
import heap.HeapFile;
import data.Record;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class OverflowFile<T extends Record<T>> extends HeapFile<T> {
    private int usedOverflowBlocks;

    public OverflowFile(String filename, int blockSize, T template) throws IOException {
        super(filename, blockSize, template);
        this.usedOverflowBlocks = 0;
    }

    /**
     * Creates a new overflow block
     */
    @Override
    public OverflowBlock<T> createBlock(int blockIndex) {
        return new OverflowBlock<>(blockIndex, this.getClusterSize(), this.getRecordTemplate());
    }

    /**
     * Reads an overflow block from disk
     */
    public OverflowBlock<T> readOverflowBlock(int index) {
        try {
            Block<T> genericBlock = super.readBlock(index);
            OverflowBlock<T> overflowBlock = this.createBlock(index);
            overflowBlock.fromBytes(genericBlock.getBytes());
            return overflowBlock;
        } catch (Exception e) {
            OverflowBlock<T> empty = this.createBlock(index);
            empty.clearRecords();
            empty.setNextOverflow(-1);
            return empty;
        }
    }

    /**
     * Writes an overflow block to disk
     */
    public void writeOverflowBlock(OverflowBlock<T> block) throws IOException {
        super.writeBlock(block.getAddress(), block);
    }

    /**
     * Allocates a new overflow block in memory at the end of the file
     */
    public int allocateOverflowBlock() throws IOException {
        int newIndex = this.getBlockCount();
        OverflowBlock<T> newBlock = this.createBlock(newIndex);
        newBlock.clearRecords();
        newBlock.setNextOverflow(-1);
        this.usedOverflowBlocks++;
        return newIndex;
    }

    /**
     * Adds a record to overflow chain
     */
    public int[] addToChain(int firstOverflowIndex, T record) throws IOException {
        // if the chain is empty
        if (firstOverflowIndex == -1) {
            int newBlockIndex = this.allocateOverflowBlock();
            OverflowBlock<T> newBlock = this.createBlock(newBlockIndex);

            if (newBlock.addRecord(record) != -1) {
                this.writeOverflowBlock(newBlock);
                return new int[]{newBlockIndex, 1};
            }
            return null;
        }

        // read all chain to memory
        List<OverflowBlock<T>> chain = this.collectAllBlocksFromChain(firstOverflowIndex);
        int originalLength = chain.size();

        for (OverflowBlock<T> block : chain) {
            if (block.addRecord(record) != -1) {
                this.writeOverflowBlock(block);
                return new int[]{firstOverflowIndex, originalLength};
            }
        }

        // all blocks are full - add a new empty block at the end of the file
        int newBlockIndex = this.allocateOverflowBlock();
        OverflowBlock<T> newBlock = this.createBlock(newBlockIndex);

        if (newBlock.addRecord(record) != -1) {
            chain.getLast().setNextOverflow(newBlockIndex);

            // write both changed blocks to file
            this.writeOverflowBlock(chain.getLast());
            this.writeOverflowBlock(newBlock);

            return new int[]{firstOverflowIndex, originalLength + 1};
        }

        this.markOverflowBlockAsEmpty(newBlockIndex);
        return null;
    }

    /**
     * Deletes a record from an overflow chain
     */
    public int[] deleteFromChain(int[] firstOverflowIndexHolder, String key) throws IOException {
        int first = firstOverflowIndexHolder[0];

        // if the chain is empty, nothing to delete
        if (first == -1) {
            return new int[]{0, 0};
        }

        // read all chain to memory
        List<OverflowBlock<T>> chain = this.collectAllBlocksFromChain(first);

        // try to delete a record from every block in chain
        boolean anyDeleted = false;
        int deletedIndex = -1;

        for (int i = 0; i < chain.size(); i++) {
            OverflowBlock<T> b = chain.get(i);
            boolean deleted = b.deleteRecord(this.createPattern(key));
            if (deleted) {
                anyDeleted = true;
                deletedIndex = i;
                break;
            }
        }

        // if nothing deleted, nothing changed
        if (!anyDeleted) {
            return new int[]{0, chain.size()};
        }

        // record was deleted from block at deletedIndex
        OverflowBlock<T> deletedBlock = chain.get(deletedIndex);

        if (deletedBlock.isEmpty()) {
            int nextAddr = deletedBlock.getNextOverflow();

            if (deletedIndex == 0) {
                firstOverflowIndexHolder[0] = nextAddr;
                this.markOverflowBlockAsEmpty(deletedBlock.getAddress());
            } else {
                OverflowBlock<T> prevBlock = chain.get(deletedIndex - 1);
                prevBlock.setNextOverflow(nextAddr);
                this.writeOverflowBlock(prevBlock);
                this.markOverflowBlockAsEmpty(deletedBlock.getAddress());
            }

            int newLength = chain.size() - 1;
            return new int[]{1, newLength};

        } else {
            this.writeOverflowBlock(deletedBlock);
            return new int[]{1, chain.size()};
        }
    }

    /**
     * Marks an overflow block as empty
     */
    public void markOverflowBlockAsEmpty(int index) throws IOException {
        if (index >= this.getBlockCount()) {
            return;
        }

        if (this.usedOverflowBlocks > 0) {
            this.usedOverflowBlocks--;
        }

        OverflowBlock<T> emptyBlock = this.createBlock(index);
        emptyBlock.clearRecords();

        this.updateBlockLists(index, emptyBlock);
    }

    /**
     * Finds a record in an overflow chain by key
     */
    public T findInChain(int firstOverflowIndex, String key) {
        int currentIndex = firstOverflowIndex;
        T pattern = this.createPattern(key);

        while (currentIndex != -1) {
            OverflowBlock<T> block = this.readOverflowBlock(currentIndex);
            T foundRecord = block.findRecord(pattern);

            if (foundRecord != null) {
                return foundRecord;
            }

            currentIndex = block.getNextOverflow();
        }

        return null;
    }

    /**
     * Returns the number of currently used overflow blocks
     */
    public int getUsedOverflowBlocks() {
        return this.usedOverflowBlocks;
    }

    /**
     * Sets the number of currently used overflow blocks
     */
    public void setUsedOverflowBlocks(int usedOverflowBlocks) {
        this.usedOverflowBlocks = usedOverflowBlocks;
    }

    /**
     * Returns all blocks from chain from specified index
     */
    public List<OverflowBlock<T>> collectAllBlocksFromChain(int firstOverflowIndex)  {
        List<OverflowBlock<T>> allBlocks = new ArrayList<>();
        int currentIndex = firstOverflowIndex;

        while (currentIndex != -1) {
            OverflowBlock<T> block = this.readOverflowBlock(currentIndex);
            allBlocks.add(block);
            currentIndex = block.getNextOverflow();
        }

        return allBlocks;
    }

    /**
     * Removes empty blocks from the end of the file
     */
    @Override
    public void removeEmptyBlocksFromEnd() throws IOException {
        super.removeEmptyBlocksFromEnd();
    }

    /**
     * Updates a record in the overflow chain
     * Returns true if record was found and updated, false otherwise
     */
    public boolean updateInChain(int firstBlockAddress, String key, T updatedRecord) throws IOException {
        int currentAddress = firstBlockAddress;

        while (currentAddress != -1) {
            OverflowBlock<T> block = this.readOverflowBlock(currentAddress);

            if (block.updateRecord(key, updatedRecord)) {
                this.writeOverflowBlock(block);
                return true;
            }

            currentAddress = block.getNextOverflow();
        }

        return false;
    }

    /**
     * Creates a search pattern for record operations
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
}