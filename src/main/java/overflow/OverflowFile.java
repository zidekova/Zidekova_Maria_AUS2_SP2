package overflow;

import heap.Block;
import heap.HeapFile;
import data.Record;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class OverflowFile<T extends Record<T>> extends HeapFile<T> {
    private final T template;
    private int usedOverflowBlocks;

    public OverflowFile(String filename, int blockSize, T template) throws IOException {
        super(filename, blockSize, template);
        this.template = template;
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
     * Adds a record to overflow chain
     */
    public int[] addToChain(int firstOverflowIndex, T record) throws IOException {
        // if the chain is empty
        if (firstOverflowIndex == -1) {
            int newBlockIndex = this.getBlockCount();
            OverflowBlock<T> newBlock = this.createBlock(newBlockIndex);

            if (newBlock.addRecord(record) != -1) {
                this.writeOverflowBlock(newBlock);
                this.usedOverflowBlocks++;
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
        int newBlockIndex = this.getBlockCount();
        OverflowBlock<T> newBlock = this.createBlock(newBlockIndex);

        if (newBlock.addRecord(record) != -1) {
            chain.getLast().setNextOverflow(newBlockIndex);

            // write both changed blocks to file
            this.writeOverflowBlock(chain.getLast());
            this.writeOverflowBlock(newBlock);
            this.usedOverflowBlocks++;
            return new int[]{firstOverflowIndex, originalLength + 1};
        }

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
        List<OverflowBlock<T>> kept = new ArrayList<>();

        for (OverflowBlock<T> b : chain) {
            boolean deleted = b.deleteRecord(this.createPattern(key));
            if (deleted) {
                anyDeleted = true;
            }

            if (!b.isEmpty()) {
                kept.add(b);
            }
        }

        // if nothing deleted, nothing changed
        if (!anyDeleted) {
            return new int[]{0, chain.size()};
        }

        // update nextOverflow for all kept blocks
        int newFirst = -1;
        for (int i = 0; i < kept.size(); i++) {
            OverflowBlock<T> b = kept.get(i);
            int nextAddr = (i + 1 < kept.size()) ? kept.get(i + 1).getAddress() : -1;
            b.setNextOverflow(nextAddr);
            this.writeOverflowBlock(b);
            if (i == 0) newFirst = b.getAddress();
        }

        // all blocks, that are not kept, mark as empty
        for (OverflowBlock<T> b : chain) {
            if (!kept.contains(b)) {
                this.markOverflowBlockAsEmpty(b.getAddress());
            }
        }

        firstOverflowIndexHolder[0] = newFirst;

        this.removeEmptyBlocksFromEnd();

        int newLen = (newFirst == -1) ? 0 : kept.size();
        return new int[]{1, newLen};
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
            T pattern = this.template.createClass();
            pattern.setKey(key);
            return pattern;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create search pattern", e);
        }
    }
}