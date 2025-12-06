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
    private int totalRecords = 0;
    private final int M;

    private static final double D_MAX = 0.8;
    private static final double D_MIN = 0.4;

    private final OverflowFile<T> overflowFile;
    private final String metadataFile;
    private boolean metadataChanged = false;

    public LinearHashing(String filename, int primaryBlockSize, int overflowBlockSize, T recordTemplate, int initialM) throws IOException {
        if (primaryBlockSize <= overflowBlockSize) {
            throw new IllegalArgumentException(
                    "Primary block size must be greater than overflow block size (primary="
                            + primaryBlockSize + ", overflow=" + overflowBlockSize + ")."
            );
        }
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
     * Loads metadata from disk including level, split pointer, record count and overflow block count
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

            try {
                int savedOverflowBlocks = dis.readInt();

                if (savedOverflowBlocks < 0 || savedOverflowBlocks > this.overflowFile.getBlockCount()) {
                    this.overflowFile.setUsedOverflowBlocks(0);
                    this.metadataChanged = true;
                } else {
                    this.overflowFile.setUsedOverflowBlocks(savedOverflowBlocks);
                }

            } catch (EOFException e) {
                this.overflowFile.setUsedOverflowBlocks(0);
                this.metadataChanged = true;
            }
        }
    }

    /**
     * Saves current metadata to disk
     */
    private void saveMetadata() throws IOException {
        if (!this.metadataChanged) return;

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(this.metadataFile))) {
            dos.writeInt(this.level);
            dos.writeInt(this.splitPointer);
            dos.writeInt(this.totalRecords);
            dos.writeInt(this.overflowFile.getUsedOverflowBlocks());
        }
        this.metadataChanged = false;
    }

    /**
     * Initializes a new hash file with M empty primary blocks
     */
    private void initializeFile() throws IOException {
        for (int i = 0; i < this.M; i++) {
            LHBlock<T> block = (LHBlock<T>) this.createBlock(i);
            block.clearRecords();
            block.setNextOverflow(-1);
            block.setOverflowRecordCount(0);
            block.setChainLength(0);
            this.writeBlock(i, block);
        }
        this.metadataChanged = true;
    }

    /**
     * Reads a primary block from disk
     * Returns empty block if index is out of bounds
     */
    public LHBlock<T> readPrimaryBlock(int index) throws IOException {
        if (index >= this.getBlockCount()) {
            LHBlock<T> empty = (LHBlock<T>) this.createBlock(index);
            empty.clearRecords();
            empty.setNextOverflow(-1);
            empty.setOverflowRecordCount(0);
            return empty;
        }

        Block<T> genericBlock = this.readBlock(index);
        LHBlock<T> lhBlock = (LHBlock<T>) this.createBlock(index);
        lhBlock.fromBytes(genericBlock.getBytes());

        int next = lhBlock.getNextOverflow();
        if (next == 65535 || next < 0) {
            lhBlock.setNextOverflow(-1);
        }
        return lhBlock;
    }

    @Override
    protected Block<T> createBlock(int blockIndex) {
        return new LHBlock<>(blockIndex, this.getClusterSize(), this.getRecordTemplate());
    }

    public OverflowFile<T> getOverflowFile() {
        return this.overflowFile;
    }

    /**
     * Calculates current number of primary blocks in the hash file
     * Formula: M * 2^level + splitPointer
     */
    private int primaryBlocksCount() {
        return this.M * (int) Math.pow(2, this.level) + this.splitPointer;
    }

    public int hash0(String key) {
        int mod = this.M * (int) Math.pow(2, this.level);
        return Math.abs(key.hashCode()) % mod;
    }

    public int hash1(String key) {
        int mod = this.M * (int) Math.pow(2, this.level + 1);
        return Math.abs(key.hashCode()) % mod;
    }

    public int getM() {
        return this.M;
    }

    /**
     * Updates an existing record by key
     * Returns true if record was found and updated, false otherwise
     */
    public boolean update(T updated) throws IOException {
        if (updated == null) return false;

        String key = updated.getKey();
        if (key == null || key.isBlank()) {
            return false;
        }

        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        boolean updatedInPrimary = primaryBlock.updateRecord(this.createPattern(key), updated);
        if (updatedInPrimary) {
            this.writeBlock(blockIndex, primaryBlock);
            return true;
        }

        int firstOverflow = primaryBlock.getNextOverflow();
        if (firstOverflow != -1) {
            return this.overflowFile.updateInChain(firstOverflow, key, updated);
        }

        return false;
    }

    /**
     * Inserts a new record into the hash file.
     */
    public void insert(T record, String key) throws IOException {
        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        boolean insertedInPrimary = (primaryBlock.addRecord(record) != -1);
        boolean actuallyInserted = insertedInPrimary;

        if (!insertedInPrimary) {
            int firstOverflow = primaryBlock.getNextOverflow();
            int[] result = this.overflowFile.addToChain(firstOverflow, record);
            if (result != null) {
                actuallyInserted = true;
                if (firstOverflow == -1) {
                    primaryBlock.setNextOverflow(result[0]);
                }
                primaryBlock.setOverflowRecordCount(primaryBlock.getOverflowRecordCount() + 1);

                primaryBlock.setChainLength(result[1]);
            }
        }

        if (actuallyInserted) {
            this.writeBlock(blockIndex, primaryBlock);
            this.totalRecords++;
            this.metadataChanged = true;
        }

        while (this.getLoadFactor() > D_MAX) {
            this.split();
        }
    }

    /**
     * Retrieves a record matching the record from specified block
     */
    public T get(String key) throws IOException {
        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        T result = primaryBlock.findRecord(this.createPattern(key));
        if (result != null) {
            return result;
        }

        int firstOverflow = primaryBlock.getNextOverflow();
        if (firstOverflow != -1) {
            return this.overflowFile.findInChain(firstOverflow, key);
        }

        return null;
    }

    /**
     * Deletes a record by key
     */
    public boolean delete(String key) throws IOException {
        int blockIndex = this.getTargetBlock(key);
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockIndex);

        boolean deleted = primaryBlock.deleteRecord(this.createPattern(key));

        if (deleted) {
            // record was in primary block
            this.writeBlock(blockIndex, primaryBlock);
            this.totalRecords--;
            this.metadataChanged = true;
        } else {
            // try to delete from overflow chain
            int firstOverflow = primaryBlock.getNextOverflow();
            if (firstOverflow != -1) {
                int[] overflowHolder = new int[]{firstOverflow};

                int[] deleteResult = this.overflowFile.deleteFromChain(overflowHolder, key);

                if (deleteResult != null && deleteResult[0] == 1) {
                    deleted = true;
                    primaryBlock.setNextOverflow(overflowHolder[0]);
                    primaryBlock.setOverflowRecordCount(Math.max(0, primaryBlock.getOverflowRecordCount() - 1));

                    primaryBlock.setChainLength(deleteResult[1]);

                    this.writeBlock(blockIndex, primaryBlock);
                    this.totalRecords--;
                    this.metadataChanged = true;
                }
            }
        }

        if (deleted) {
            boolean mergePerformed = false;

            while (this.primaryBlocksCount() > this.M && this.getLoadFactor() < D_MIN) {
                mergePerformed = true;
                this.merge();
            }

            if (!mergePerformed) this.compactBlock(blockIndex);
        }

        return deleted;
    }

    /**
     * Determines the target block index for a given key
     * Uses linear hashing algorithm: if h0(key) < splitPointer, use h1(key), else use h0(key)
     */
    public int getTargetBlock(String key) {
        int h0 = this.hash0(key);
        return (h0 < this.splitPointer) ? this.hash1(key) : h0;
    }

    /**
     * Algorithm:
     * 1. Read all records from primary block and its overflow chain
     * 2. Split records between old and new block based on h1
     * 3. Rebuild both blocks with minimum overflow usage
     * 4. Write changes and update structure
     */
    private void split() throws IOException {
        int blockToSplit = this.splitPointer;

        // read primary block + overflow chain
        LHBlock<T> primaryBlock = this.readPrimaryBlock(blockToSplit);
        List<OverflowBlock<T>> overflowBlocks = this.overflowFile.collectAllBlocksFromChain(primaryBlock.getNextOverflow());

        List<T> allRecords = new ArrayList<>(primaryBlock.getRecords());
        for (OverflowBlock<T> overflowBlock : overflowBlocks) {
            allRecords.addAll(overflowBlock.getRecords());
        }

        // create new primary block in memory
        int newBlockIndex = this.primaryBlocksCount();
        LHBlock<T> newBlock = (LHBlock<T>) this.createBlock(newBlockIndex);
        newBlock.clearRecords();
        newBlock.setNextOverflow(-1);
        newBlock.setOverflowRecordCount(0);

        // reset original primary block
        primaryBlock.clearRecords();
        primaryBlock.setNextOverflow(-1);
        primaryBlock.setOverflowRecordCount(0);

        // reset all overflow blocks
        for (OverflowBlock<T> overflowBlock : overflowBlocks) {
            overflowBlock.clearRecords();
            overflowBlock.setNextOverflow(-1);
        }

        // split records
        List<T> recordsForOldBlock = new ArrayList<>();
        List<T> recordsForNewBlock = new ArrayList<>();

        for (T record : allRecords) {
            int h1 = this.hash1(record.getKey());
            if (h1 == blockToSplit) {
                recordsForOldBlock.add(record);
            } else {
                recordsForNewBlock.add(record);
            }
        }

        // create overflow chains in memory
        int poolIdx = 0;

        OverflowBlock<T> tailOld = null;
        OverflowBlock<T> tailNew = null;

        // original block
        for (T record : recordsForOldBlock) {
            if (primaryBlock.addRecord(record) == -1) {
                if (tailOld == null) {
                    OverflowBlock<T> b = overflowBlocks.get(poolIdx++);
                    primaryBlock.setNextOverflow(b.getAddress());
                    tailOld = b;
                }

                // try to add to current tail
                int pos = tailOld.addRecord(record);
                if (pos == -1) {
                    OverflowBlock<T> nb = overflowBlocks.get(poolIdx++);
                    tailOld.setNextOverflow(nb.getAddress());
                    tailOld = nb;

                    pos = tailOld.addRecord(record);
                    if (pos == -1) {
                        throw new IllegalStateException("New overflow block didn't accept record.");
                    }
                }

                primaryBlock.setOverflowRecordCount(primaryBlock.getOverflowRecordCount() + 1);
            }
        }

        // new block
        for (T record : recordsForNewBlock) {
            if (newBlock.addRecord(record) == -1) {
                if (tailNew == null) {
                    OverflowBlock<T> b = overflowBlocks.get(poolIdx++);
                    newBlock.setNextOverflow(b.getAddress());
                    tailNew = b;
                }

                // try to add to current tail
                int pos = tailNew.addRecord(record);
                if (pos == -1) {
                    OverflowBlock<T> nb = overflowBlocks.get(poolIdx++);
                    tailNew.setNextOverflow(nb.getAddress());
                    tailNew = nb;

                    pos = tailNew.addRecord(record);
                    if (pos == -1) {
                        throw new IllegalStateException("New overflow block didn't accept record.");
                    }
                }

                newBlock.setOverflowRecordCount(newBlock.getOverflowRecordCount() + 1);
            }
        }

        int oldChainLength = 0;
        int newChainLength = 0;

        // tracking overflow blocks in old block
        int currentOverflow = primaryBlock.getNextOverflow();
        while (currentOverflow != -1) {
            oldChainLength++;
            for (OverflowBlock<T> block : overflowBlocks) {
                if (block.getAddress() == currentOverflow) {
                    currentOverflow = block.getNextOverflow();
                    break;
                }
            }
        }

        // tracking overflow blocks in new block
        currentOverflow = newBlock.getNextOverflow();
        while (currentOverflow != -1) {
            newChainLength++;
            for (OverflowBlock<T> block : overflowBlocks) {
                if (block.getAddress() == currentOverflow) {
                    currentOverflow = block.getNextOverflow();
                    break;
                }
            }
        }

        // set chain lengths
        primaryBlock.setChainLength(oldChainLength);
        newBlock.setChainLength(newChainLength);

        // mark unused overflow blocks as empty
        for (int i = poolIdx; i < overflowBlocks.size(); i++) {
            OverflowBlock<T> empty = overflowBlocks.get(i);
            this.overflowFile.markOverflowBlockAsEmpty(empty.getAddress());
        }

        // write changes to file
        this.writeBlock(blockToSplit, primaryBlock);
        this.writeBlock(newBlockIndex, newBlock);

        for (OverflowBlock<T> overflowBlock : overflowBlocks) {
            this.overflowFile.writeOverflowBlock(overflowBlock);
        }

        // update structure
        this.splitPointer++;
        this.metadataChanged = true;

        if (this.splitPointer >= this.M * (int) Math.pow(2, this.level)) {
            this.level++;
            this.splitPointer = 0;
        }

        this.overflowFile.removeEmptyBlocksFromEnd();
    }

    /**
     * Algorithm:
     * a) if S > 0: a = S + M*2^u - 1, b = S - 1, S := b
     * b) if S = 0 and u > 0: a = M*2^u - 1, b = M*2^(u-1) - 1, S := b, u := u - 1
     */
    private void merge() throws IOException {
        if (this.primaryBlocksCount() <= this.M) {
            return;
        }

        // determine indices a (last group) and b (target group)
        int base = this.M * (int) Math.pow(2, this.level);
        int a, b;
        if (this.splitPointer > 0) {
            a = this.splitPointer + base - 1;
            b = this.splitPointer - 1;
        } else if (this.splitPointer == 0 && this.level > 0) {
            a = base - 1;
            b = this.M * (int) Math.pow(2, this.level - 1) - 1;
        } else {
            return;
        }

        // read primary blocks and overflow chains
        LHBlock<T> blockA = this.readPrimaryBlock(a);
        LHBlock<T> blockB = this.readPrimaryBlock(b);

        List<OverflowBlock<T>> chainA = this.overflowFile.collectAllBlocksFromChain(blockA.getNextOverflow());
        List<OverflowBlock<T>> chainB = this.overflowFile.collectAllBlocksFromChain(blockB.getNextOverflow());

        List<T> combined = new ArrayList<>(blockB.getRecords());
        for (OverflowBlock<T> ob : chainB) {
            combined.addAll(ob.getRecords());
        }
        combined.addAll(blockA.getRecords());
        for (OverflowBlock<T> ob : chainA) {
            combined.addAll(ob.getRecords());
        }

        // reset target block b and prepare overflow block pool (b + a)
        blockB.clearRecords();
        blockB.setNextOverflow(-1);
        blockB.setOverflowRecordCount(0);

        List<OverflowBlock<T>> pool = new ArrayList<>(chainB.size() + chainA.size());
        pool.addAll(chainB);
        pool.addAll(chainA);
        for (OverflowBlock<T> ob : pool) {
            ob.clearRecords();
            ob.setNextOverflow(-1);
        }

        // fill b: primary first, then overflow via pool
        OverflowBlock<T> tail = null;
        int used = 0;

        for (T rec : combined) {
            int pos = blockB.addRecord(rec);
            if (pos == -1) {
                if (tail == null) {
                    // take first free from pool, otherwise allocate
                    overflow.OverflowBlock<T> nb;
                    if (used == pool.size()) {
                        int newIdx = this.overflowFile.allocateOverflowBlock();
                        nb = this.overflowFile.createBlock(newIdx);
                        nb.clearRecords();
                        nb.setNextOverflow(-1);
                        pool.add(nb);
                    }
                    tail = pool.get(used++);
                    blockB.setNextOverflow(tail.getAddress());
                }
                // try to add to tail
                pos = tail.addRecord(rec);
                if (pos == -1) {
                    overflow.OverflowBlock<T> nb;
                    if (used >= pool.size()) {
                        int newIdx = this.overflowFile.allocateOverflowBlock();
                        nb = this.overflowFile.createBlock(newIdx);
                        nb.clearRecords();
                        nb.setNextOverflow(-1);
                        pool.add(nb);
                    } else {
                        nb = pool.get(used++);
                    }
                    tail.setNextOverflow(nb.getAddress());
                    tail = nb;

                    pos = tail.addRecord(rec);
                    if (pos == -1) {
                        throw new IllegalStateException("Overflow block didn't accept record.");
                    }
                }
                blockB.setOverflowRecordCount(blockB.getOverflowRecordCount() + 1);
            }
        }

        // calculate chain length
        int chainLength = 0;
        int cur = blockB.getNextOverflow();
        while (cur != -1) {
            chainLength++;
            // find next block by address in pool
            int next = -1;
            for (overflow.OverflowBlock<T> p : pool) {
                if (p.getAddress() == cur) {
                    next = p.getNextOverflow();
                    break;
                }
            }
            cur = next;
        }
        blockB.setChainLength(chainLength);

        // write primary b + only used overflow blocks and mark unused as empty
        this.writeBlock(b, blockB);
        for (int i = 0; i < used; i++) {
            this.overflowFile.writeOverflowBlock(pool.get(i));
        }
        for (int i = used; i < pool.size(); i++) {
            this.overflowFile.markOverflowBlockAsEmpty(pool.get(i).getAddress());
        }

        // remove last group a, mark as empty and remove trailing empty block
        LHBlock<T> emptyA = (LHBlock<T>) this.createBlock(a);
        emptyA.clearRecords();
        emptyA.setNextOverflow(-1);
        emptyA.setOverflowRecordCount(0);
        emptyA.setChainLength(0);

        this.updateBlockLists(a, emptyA);

        this.removeEmptyBlocksFromEnd();
        this.overflowFile.removeEmptyBlocksFromEnd();

        // update structure
        if (this.splitPointer > 0) {
            this.splitPointer = b;
        } else {
            this.splitPointer = b;
            this.level = this.level - 1;
        }
        this.metadataChanged = true;
    }


    /**
     * Compaction of a single block
     * Performed only if at least 1 overflow block can be freed
     */
    private boolean compactBlock(int blockIndex) throws IOException {
        LHBlock<T> primary = this.readPrimaryBlock(blockIndex);
        int firstOverflow = primary.getNextOverflow();
        if (firstOverflow == -1) {
            return false;
        }

        List<OverflowBlock<T>> chain = this.overflowFile.collectAllBlocksFromChain(firstOverflow);
        int L = chain.size();
        if (L == 0) return false;

        List<T> allRecords = new ArrayList<>(primary.getRecords());
        for (OverflowBlock<T> ob : chain) {
            allRecords.addAll(ob.getRecords());
        }

        int primaryCapacity = this.getRecordsPerBlock();
        int overflowCapacity = this.overflowFile.getRecordsPerBlock();

        int capacityWithOneLess = primaryCapacity + (L - 1) * overflowCapacity;
        if (allRecords.size() > capacityWithOneLess) {
            return false;
        }

        primary.clearRecords();
        primary.setNextOverflow(-1);
        primary.setOverflowRecordCount(0);
        primary.setChainLength(0);

        for (OverflowBlock<T> ob : chain) {
            ob.clearRecords();
            ob.setNextOverflow(-1);
        }

        List<T> remaining = new ArrayList<>();
        for (T rec : allRecords) {
            if (primary.addRecord(rec) == -1) {
                remaining.add(rec);
            }
        }

        int overflowCount = 0;
        int chainLength = 0;
        OverflowBlock<T> currentOverflow = null;
        OverflowBlock<T> previousOverflow = null;

        for (int i = 0; i < chain.size() && !remaining.isEmpty(); i++) {
            currentOverflow = chain.get(i);
            chainLength++;

            List<T> toAdd = new ArrayList<>(remaining);
            remaining.clear();

            for (T rec : toAdd) {
                if (currentOverflow.addRecord(rec) == -1) {
                    remaining.add(rec);
                }
            }

            if (previousOverflow != null) {
                previousOverflow.setNextOverflow(currentOverflow.getAddress());
            } else {
                primary.setNextOverflow(currentOverflow.getAddress());
            }

            overflowCount += currentOverflow.getValidCount();
            previousOverflow = currentOverflow;

            if (remaining.isEmpty()) {
                currentOverflow.setNextOverflow(-1);
                break;
            }
        }

        if (!remaining.isEmpty()) {
            throw new IllegalStateException("Cannot fit records after compaction");
        }

        primary.setOverflowRecordCount(overflowCount);
        primary.setChainLength(chainLength);

        this.writeBlock(blockIndex, primary);

        for (int i = 0; i < chainLength; i++) {
            this.overflowFile.writeOverflowBlock(chain.get(i));
        }

        for (int i = chainLength; i < chain.size(); i++) {
            this.overflowFile.markOverflowBlockAsEmpty(chain.get(i).getAddress());
        }

        this.overflowFile.removeEmptyBlocksFromEnd();
        this.metadataChanged = true;

        return chainLength < L;
    }

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
     * Calculates the current load factor of the hash file
     * Load factor = total records / total capacity (primary + overflow)
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
     * Gets statistics about the hash file
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
     * Statistics container class for Linear Hashing
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
     * Closes the hash file
     */
    @Override
    public void close() throws IOException {
        this.saveMetadata();
        this.overflowFile.close();
        super.getFile().close();
    }

    /**
     * Displays the whole hashfile - all primary anf ovewflow blocks
     */
    public String displayAllBlocks(String title) throws IOException {
        StringBuilder sb = new StringBuilder();
        LinearHashingStats stats = this.getStats();

        if (title != null && !title.isBlank()) {
            sb.append(title).append("\n\n");
        }
        sb.append("ŠTATISTIKY:\n");
        sb.append("• Úroveň (level): ").append(stats.level).append("\n");
        sb.append("• Split pointer: ").append(stats.splitPointer).append("\n");
        sb.append("• Celkový počet záznamov: ").append(stats.totalRecords).append("\n");
        sb.append("• Primárne bloky: ").append(stats.totalBlocks).append("\n");
        sb.append("• Overflow bloky: ").append(stats.overflowBlocks).append("\n");
        sb.append("• Záznamy v overflow: ").append(stats.totalOverflowRecords).append("\n");
        sb.append("• Hustota: ").append(String.format("%.2f", stats.loadFactor)).append("\n\n");

        for (int i = 0; i < stats.totalBlocks; i++) {
            sb.append("════════════════════════════════════════════════════════════════════════════════\n");
            sb.append("PRIMÁRNY BLOK ").append(i).append("\n");
            sb.append("════════════════════════════════════════════════════════════════════════════════\n");

            LHBlock<T> primaryBlock = this.readPrimaryBlock(i);
            sb.append("Adresa: ").append(primaryBlock.getAddress()).append(" bytes\n");
            sb.append("Stav: ");
            if (primaryBlock.isEmpty()) sb.append("PRÁZDNY");
            else if (!primaryBlock.hasSpace()) sb.append("PLNÝ");
            else sb.append("ČIASTOČNE VOĽNÝ");
            sb.append(" | Záznamy: ").append(primaryBlock.getValidCount())
                    .append("/").append(primaryBlock.getRecordsPerBlock()).append("\n");
            sb.append("Overflow pointer: ").append(primaryBlock.getNextOverflow())
                    .append(" | Overflow záznamov: ").append(primaryBlock.getOverflowRecordCount())
                    .append(" | Dĺžka reťazca: ").append(primaryBlock.getChainLength()).append("\n\n");

            if (primaryBlock.isEmpty()) {
                sb.append(" Žiadne záznamy\n");
            } else {
                int recordNum = 1;
                for (T record : primaryBlock.getRecords()) {
                    if (record != null && record.getKey() != null && !record.getKey().trim().isEmpty()) {
                        sb.append(" ").append(recordNum).append(". ").append(record).append("\n");
                        recordNum++;
                    }
                }
            }

            // overflow chain
            int overflowPointer = primaryBlock.getNextOverflow();
            if (overflowPointer != -1) {
                sb.append("\n ┌─ OVERFLOW REŤAZEC ──────────────────────────────────────────────\n");
                displayOverflowChain(sb, overflowPointer, 1);
                sb.append(" └──────────────────────────────────────────────────────────────────\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void displayOverflowChain(StringBuilder sb, int overflowBlockIndex, int level) {
        if (overflowBlockIndex == -1) return;
        OverflowBlock<T> overflowBlock = this.getOverflowFile().readOverflowBlock(overflowBlockIndex);

        String indent = " " + " ".repeat(level);
        sb.append(indent).append("├─ OVERFLOW BLOK ").append(overflowBlockIndex).append("\n");
        sb.append(indent).append("│ Adresa: ").append(overflowBlock.getAddress()).append(" bytes\n");
        sb.append(indent).append("│ Záznamy: ").append(overflowBlock.getValidCount())
                .append("/").append(overflowBlock.getRecordsPerBlock()).append("\n");
        sb.append(indent).append("│ Ďalší overflow: ").append(overflowBlock.getNextOverflow()).append("\n");

        if (overflowBlock.isEmpty()) {
            sb.append(indent).append("│ Žiadne záznamy\n");
        } else {
            int recordNum = 1;
            for (T record : overflowBlock.getRecords()) {
                if (record != null && record.getKey() != null && !record.getKey().trim().isEmpty()) {
                    sb.append(indent).append("│ ").append(recordNum).append(". ").append(record).append("\n");
                    recordNum++;
                }
            }
        }

        int nextOverflow = overflowBlock.getNextOverflow();
        if (nextOverflow != -1) {
            sb.append(indent).append("│\n");
            displayOverflowChain(sb, nextOverflow, level + 1);
        }
    }

}