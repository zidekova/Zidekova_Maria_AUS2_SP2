package generator;

import structure.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

public class OperationGenerator {
    private static final String[] NAMES = {"Anna","Peter","Maria","Jozef","Eva","Michal","Katarina"};
    private static final String[] SURNAMES = {"Novak","Horak","Kral","Bielik","Farkas","Kovac","Urban"};
    private static final Random random = new Random();

    private HeapFile<Person> heap;
    private final List<Person> insertedPersons;

    public OperationGenerator(HeapFile<Person> heap) {
        this.heap = heap;
        this.insertedPersons = new ArrayList<>();
    }

    /**
     * Runs a specified number of random operations (INSERT, FIND, DELETE) with progress updates
     */
    public String runRandomOperations(int operations, Consumer<String> progressCallback) throws IOException {
        int insertCount = 0;
        int findCount = 0;
        int deleteCount = 0;
        int errors = 0;
        int validationErrors = 0;

        StringBuilder validationLog = new StringBuilder();

        if (progressCallback != null) {
            progressCallback.accept("NÁHODNÉ OPERÁCIE SPUSTENÉ\n\n" +
                    "Počet operácií: " + operations + "\n" +
                    "Generovanie operácií...\n");
        }

        for (int i = 0; i < operations; i++) {
            int op = random.nextInt(3);

            try {
                if (i % 100 == 0 && progressCallback != null) {
                    progressCallback.accept("NÁHODNÉ OPERÁCIE PREBIEHAJÚ...\n\n" +
                            "Spracovaných: " + i + "/" + operations + " operácií\n" +
                            "Insert: " + insertCount + "\n" +
                            "Find: " + findCount + "\n" +
                            "Delete: " + deleteCount + "\n" +
                            "Chyby: " + errors + "\n" +
                            "Validačné chyby: " + validationErrors);
                }

                switch (op) {
                    case 0 -> { // insert
                        Person newPerson = this.generateRandomPatient();
                        int blockIndexBefore = heap.getBlockCount();

                        boolean hadPartiallyFree = this.hasPartiallyFreeBlocks();
                        boolean hadEmpty = this.hasEmptyBlocks();

                        int insertedBlock = this.heap.insert(newPerson);
                        this.insertedPersons.add(newPerson);
                        insertCount++;

                        String insertValidation = this.validateInsertStrategy(hadPartiallyFree, hadEmpty, blockIndexBefore, insertedBlock);
                        if (!insertValidation.isEmpty()) {
                            validationErrors++;
                            validationLog.append("CHYBA VKLADANIA pri operácii ").append(i).append(": ").append(insertValidation).append("\n");
                        }
                    }

                    case 1 -> { // find
                        if (this.insertedPersons.isEmpty()) {
                            break;
                        }

                        Person toFind = this.getRandomElement(this.insertedPersons);

                        int maxBlocks = Math.max(1, this.heap.getBlockCount());
                        for (int block = 0; block < maxBlocks; block++) {
                            try {
                                Person result = this.heap.get(block, toFind);
                                if (result != null && result.equals(toFind)) {
                                    break;
                                }
                            } catch (IllegalArgumentException e) {
                                break;
                            }
                        }

                        findCount++;
                    }

                    case 2 -> { // delete
                        if (this.insertedPersons.isEmpty()) {
                            break;
                        }

                        Person toDelete = this.getRandomElement(this.insertedPersons);
                        boolean deleted = this.tryDeleteFromBlocks(toDelete);

                        deleteCount++;
                        if (deleted) {
                            this.insertedPersons.remove(toDelete);
                        } else {
                            errors++;
                        }
                    }
                }

                if (i % 100 == 0) {
                    String validationResult = this.validateHeapFile();
                    if (!validationResult.isEmpty()) {
                        validationErrors++;
                        validationLog.append("CHYBA po operácii ").append(i).append(": ").append(validationResult).append("\n");
                    }
                }

            } catch (Exception ex) {
                errors++;
                validationLog.append("EXCEPTION pri operácii ").append(i).append(": ").append(ex.getMessage()).append("\n");
            }
        }

        if (progressCallback != null) {
            progressCallback.accept("NÁHODNÉ OPERÁCIE DOKONČENÉ\n\n" +
                    "Spracúvajú sa výsledky...\n");
        }

        String finalValidation = this.validateHeapFile();
        if (!finalValidation.isEmpty()) {
            validationErrors++;
            validationLog.append("CHYBA pri finálnej validácii: ").append(finalValidation).append("\n");
        }

        // final results
        StringBuilder result = new StringBuilder();
        result.append("OPERÁCIE DOKONČENÉ\n\n");
        result.append("• Celkový počet operácií: ").append(operations).append("\n");
        result.append("• Insert operácie: ").append(insertCount).append("\n");
        result.append("• Find operácie: ").append(findCount).append("\n");
        result.append("• Delete operácie: ").append(deleteCount).append("\n");
        result.append("• Chyby: ").append(errors).append("\n");
        result.append("• Chyby pri validácií: ").append(validationErrors).append("\n");
        result.append("• Blokov v databáze: ").append(this.heap.getBlockCount()).append("\n\n");

        if (validationErrors > 0) {
            result.append("DETEKOVANÉ CHYBY PRI VALIDÁCII:\n");
            result.append(validationLog.append("\n"));
        } else {
            result.append("Validácia prebehla úspešne\n");
        }

        return result.toString();
    }

    /**
     * Validates heap file insertion strategy
     */
    private String validateInsertStrategy(boolean hadPartiallyFree, boolean hadEmpty, int blockCountBefore, int insertedBlock) throws IOException {
        StringBuilder error = new StringBuilder();

        if (hadPartiallyFree) {
            if (insertedBlock >= blockCountBefore) {
                error.append("Vložené do nového bloku, ale existovali čiastočne voľné bloky. ");
            }
        } else if (hadEmpty) {
            if (insertedBlock >= blockCountBefore) {
                error.append("Vložené do nového bloku, ale existovali prázdne bloky. ");
            }
        } else {
            if (insertedBlock != blockCountBefore) {
                error.append("Vložené do bloku ").append(insertedBlock).append(", ale očakávaný nový blok bol ").append(blockCountBefore).append(". ");
            }
        }

        if (insertedBlock >= this.heap.getBlockCount()) {
            error.append("Vložené do neexistujúceho bloku ").append(insertedBlock).append(". ");
        }

        return error.toString();
    }

    /**
     * Checks if there are partially free blocks
     */
    private boolean hasPartiallyFreeBlocks() throws IOException {
        int blockCount = this.heap.getBlockCount();
        for (int i = 0; i < blockCount; i++) {
            Block<Person> block = this.heap.readBlock(i);
            if (block.hasSpace() && !block.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if there are empty blocks
     */
    private boolean hasEmptyBlocks() throws IOException {
        int blockCount = this.heap.getBlockCount();
        for (int i = 0; i < blockCount; i++) {
            Block<Person> block = this.heap.readBlock(i);
            if (block.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     *  HeapFile validation
     */
    private String validateHeapFile() throws IOException {
        StringBuilder errors = new StringBuilder();
        int blockCount = this.heap.getBlockCount();

        for (int i = 0; i < blockCount; i++) {
            Block<Person> block = this.heap.readBlock(i);

            int validCount = block.getValidCount();
            List<Person> records = block.getRecords();
            int actualValid = 0;

            for (Person record : records) {
                if (record != null && !record.getId().trim().isEmpty()) {
                    actualValid++;
                }
            }

            if (validCount != actualValid) {
                errors.append("CHYBA v bloku ").append(i).append(": validCount=").append(validCount).append(", actualValid=").append(actualValid).append(". ");
            }

            boolean shouldBeEmpty = validCount == 0;
            boolean shouldHaveSpace = validCount < this.heap.getRecordsPerBlock();

            if (shouldBeEmpty != block.isEmpty()) {
                errors.append("CHYBA isEmpty v bloku ").append(i).append(". ");
            }
            if (shouldHaveSpace != block.hasSpace()) {
                errors.append("CHYBA hasSpace v bloku ").append(i).append(". ");
            }
        }

        if (blockCount > 1) {
            Block<Person> lastBlock = this.heap.readBlock(blockCount - 1);
            if (lastBlock.isEmpty()) {
                errors.append("Posledný blok je prázdny, mal by byť orezaný. ");
            }
        }

        Set<String> allIds = new HashSet<>();
        for (int i = 0; i < blockCount; i++) {
            Block<Person> block = this.heap.readBlock(i);
            for (Person record : block.getRecords()) {
                if (record != null && !record.getId().trim().isEmpty()) {
                    String id = record.getId();
                    if (allIds.contains(id)) {
                        errors.append("Duplicitné ID ").append(id).append(" v bloku ").append(i).append(". ");
                    }
                    allIds.add(id);
                }
            }
        }

        return errors.toString();
    }

    /**
     * Runs a specified number of insert operations
     */
    public String runInsertOnly(int records, Consumer<String> progressCallback) throws IOException {
        if (progressCallback != null) {
            progressCallback.accept("HROMADNÉ VKLADANIE SPUSTENÉ\n\n" +
                    "Počet záznamov: " + records + "\n" +
                    "Začína vkladanie záznamov...\n");
        }

        int successfulInserts = 0;
        int failedInserts = 0;
        int validationErrors = 0;
        StringBuilder validationLog = new StringBuilder();

        for (int i = 0; i < records; i++) {
            try {
                Person newPerson = this.generateRandomPatient();
                int blockIndexBefore = this.heap.getBlockCount();
                boolean hadPartiallyFree = this.hasPartiallyFreeBlocks();
                boolean hadEmpty = this.hasEmptyBlocks();

                int insertedBlock = this.heap.insert(newPerson);
                this.insertedPersons.add(newPerson);
                successfulInserts++;

                String validation = this.validateInsertStrategy(hadPartiallyFree, hadEmpty, blockIndexBefore, insertedBlock);
                if (!validation.isEmpty()) {
                    validationErrors++;
                    validationLog.append("CHYBA VKLADANIA pri operácii ").append(i).append(": ").append(validation).append("\n");
                }

                if (i % 50 == 0 && progressCallback != null) {
                    progressCallback.accept("HROMADNÉ VKLADANIE PREBIEHA...\n\n" +
                            "Spracovaných: " + i + "/" + records + " záznamov\n" +
                            "Úspešné: " + successfulInserts + "\n" +
                            "Neúspešné: " + failedInserts + "\n" +
                            "Validačné chyby: " + validationErrors + "\n" +
                            "Úspešnosť: " + String.format("%.1f", (successfulInserts * 100.0 / (i + 1))) + "%");
                }

            } catch (Exception ex) {
                failedInserts++;
            }
        }

        if (progressCallback != null) {
            progressCallback.accept("HROMADNÉ VKLADANIE DOKONČENÉ\n\n" +
                    "Spracúvajú sa výsledky...\n");
        }

        String finalValidation = this.validateHeapFile();
        if (!finalValidation.isEmpty()) {
            validationErrors++;
            validationLog.append("CHYBA pri finálnej validácii: ").append(finalValidation).append("\n");
        }

        StringBuilder result = new StringBuilder();
        result.append("HROMADNÉ VKLADANIE DOKONČENÉ\n\n");
        result.append("VÝSLEDKY:\n");
        result.append("• Pokusy o vloženie: ").append(records).append("\n");
        result.append("• Úspešné vloženia: ").append(successfulInserts).append("\n");
        result.append("• Neúspešné vloženia: ").append(failedInserts).append("\n");
        result.append("• Chyby pri validácií: ").append(validationErrors).append("\n");
        result.append("• Úspešnosť: ").append(String.format("%.1f", (successfulInserts * 100.0 / records))).append("%\n");
        result.append("• Celkový počet blokov: ").append(this.heap.getBlockCount()).append("\n");

        if (validationErrors > 0) {
            result.append("\nDETEKOVANÉ CHYBY PRI VALIDÁCII:\n");
            result.append(validationLog);
        } else {
            result.append("\nValidácia prebehla úspešne\n");
        }

        return result.toString();
    }

    /**
     * Clears the database by deleting all data files and recreating empty structures
     */
    public String clearDatabase() throws IOException {
        if (this.heap != null) {
            this.heap.close();
        }

        new java.io.File("pacienti.dat").delete();
        new java.io.File("pacienti.dat.meta").delete();

        this.heap = null;
        this.insertedPersons.clear();

        return "DATABÁZA VYČISTENÁ\n\nVšetky dáta boli úspešne odstránené.\n";
    }

    /**
     * Attempts to delete a person from any block in the heap file
     */
    private boolean tryDeleteFromBlocks(Person person) throws IOException {
        int maxBlocks = Math.max(1, this.heap.getBlockCount());
        for (int block = 0; block < maxBlocks; block++) {
            try {
                if (this.heap.delete(block, person)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        return false;
    }

    /**
     * Selects a random element from a list
     */
    private Person getRandomElement(List<Person> list) {
        int index = random.nextInt(list.size());
        return list.get(index);
    }

    /**
     * Generates a random Person record
     */
    public Person generateRandomPatient() {
        String name = NAMES[random.nextInt(NAMES.length)];
        String surname = SURNAMES[random.nextInt(SURNAMES.length)];
        LocalDate date = LocalDate.of(1970 + random.nextInt(40), 1 + random.nextInt(12), 1 + random.nextInt(28));
        String id = "P" + (10000 + random.nextInt(90000));
        return new Person(name, surname, date, id);
    }
}