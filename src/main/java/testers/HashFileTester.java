package testers;

import data.Person;
import hash.LinearHashing;
import hash.LHBlock;
import overflow.OverflowBlock;
import overflow.OverflowFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

public class HashFileTester {
    private static final String[] NAMES = {"Anna", "Peter", "Maria", "Jozef", "Eva", "Michal", "Katarina"};
    private static final String[] SURNAMES = {"Novak", "Horak", "Kral", "Bielik", "Farkas", "Kovac", "Urban"};
    private static final Random random = new Random();
    private int patientCounter = 1;

    private LinearHashing<Person> hashFile;
    private final List<Person> insertedPersons;

    public HashFileTester(LinearHashing<Person> hashFile) {
        this.hashFile = hashFile;
        this.insertedPersons = new ArrayList<>();
    }

    /**
     * Runs a specified number of random operations (INSERT, FIND, DELETE)
     */
    public String runRandomOperations(int operations, Consumer<String> progressCallback) throws IOException {
        int insertCount = 0;
        int findCount = 0;
        int deleteCount = 0;
        int validationErrors = 0;

        StringBuilder validationLog = new StringBuilder();

        this.synchronizeWithDatabase();

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
                            "Validačné chyby: " + validationErrors);
                }

                switch (op) {
                    case 0 -> { // insert
                        insertCount++;
                        Person newPerson = this.generateRandomPatient();

                        int originalSize = this.insertedPersons.size();

                        hashFile.insert(newPerson, newPerson.getKey());
                        this.insertedPersons.add(newPerson);

                        String structureComparison = this.compareDatabaseStructure();
                        if (!structureComparison.isEmpty()) {
                            validationErrors++;
                            validationLog.append("NESÚLAD ŠTRUKTÚRY po INSERT v operácii ").append(i)
                                    .append(" (záznam ").append(newPerson.getKey()).append("):\n")
                                    .append(structureComparison).append("\n");
                        } else if (this.insertedPersons.size() != originalSize + 1) {
                            validationErrors++;
                            validationLog.append("NESÚLAD VEĽKOSTI po INSERT v operácii ").append(i)
                                    .append(": pôvodná veľkosť=").append(originalSize)
                                    .append(", nová veľkosť=").append(this.insertedPersons.size()).append("\n");
                        }
                    }

                    case 1 -> { // find
                        if (this.insertedPersons.isEmpty()) {
                            break;
                        }
                        findCount++;

                        Person toFind = this.getRandomElement(this.insertedPersons);
                        Person found = hashFile.get(toFind.getKey());

                        if (found == null) {
                            validationLog.append("CHYBA VYHĽADÁVANIA pri operácii ").append(i)
                                    .append(": Nenašiel sa záznam ").append(toFind.getKey()).append("\n");

                            String structureComparison = this.compareDatabaseStructure();
                            if (!structureComparison.isEmpty()) {
                                validationErrors++;
                                validationLog.append("NESÚLAD ŠTRUKTÚRY po chybnom FIND v operácii ").append(i)
                                        .append(" (záznam ").append(toFind.getKey()).append("):\n")
                                        .append(structureComparison).append("\n");
                            }
                        } else if (!found.equals(toFind)) {
                            validationErrors++;
                            validationLog.append("NEKONZISTENTNÝ ZÁZNAM pri FIND v operácii ").append(i)
                                    .append(": očakávaný ").append(toFind)
                                    .append(", nájdený ").append(found).append("\n");
                        }
                    }

                    case 2 -> { // delete
                        if (this.insertedPersons.isEmpty()) {
                            break;
                        }
                        deleteCount++;

                        Person toDelete = this.getRandomElement(this.insertedPersons);
                        boolean deleted = hashFile.delete(toDelete.getKey());

                        if (deleted) {
                            this.insertedPersons.remove(toDelete);

                            String structureComparison = this.compareDatabaseStructure();
                            if (!structureComparison.isEmpty()) {
                                validationErrors++;
                                validationLog.append("NESÚLAD ŠTRUKTÚRY po úspešnom DELETE v operácii ").append(i)
                                        .append(" (záznam ").append(toDelete.getKey()).append("):\n")
                                        .append(structureComparison).append("\n");
                            }
                        } else {
                            String structureComparison = this.compareDatabaseStructure();
                            if (!structureComparison.isEmpty()) {
                                validationErrors++;
                                validationLog.append("NESÚLAD ŠTRUKTÚRY po neúspešnom DELETE v operácii ").append(i)
                                        .append(" (záznam ").append(toDelete.getKey()).append("):\n")
                                        .append(structureComparison).append("\n");
                            } else {
                                validationLog.append("CHYBA MAZANIA pri operácii ").append(i)
                                        .append(": Neodstránil sa záznam ").append(toDelete.getKey()).append("\n");
                            }
                        }
                    }
                }

                if (i % 100 == 0) {
                    String validationResult = this.validateHashFile();
                    if (!validationResult.isEmpty()) {
                        validationErrors++;
                        validationLog.append("CHYBA po operácii ").append(i).append(": ").append(validationResult).append("\n");
                    }
                }

            } catch (Exception ex) {
                validationLog.append("EXCEPTION pri operácii ").append(i).append(": ").append(ex.getMessage()).append("\n");
            }
        }

        if (progressCallback != null) {
            progressCallback.accept("NÁHODNÉ OPERÁCIE DOKONČENÉ\n\n" +
                    "Spracúvajú sa výsledky...\n");
        }

        String finalValidation = this.validateHashFile();
        if (!finalValidation.isEmpty()) {
            validationErrors++;
            validationLog.append("CHYBA pri finálnej validácii: ").append(finalValidation).append("\n");
        }

        StringBuilder result = new StringBuilder();
        result.append("OPERÁCIE DOKONČENÉ\n\n");
        result.append("• Celkový počet operácií: ").append(operations).append("\n");
        result.append("• Insert operácie: ").append(insertCount).append("\n");
        result.append("• Find operácie: ").append(findCount).append("\n");
        result.append("• Delete operácie: ").append(deleteCount).append("\n");
        result.append("• Chyby pri validácií: ").append(validationErrors).append("\n");

        try {
            LinearHashing.LinearHashingStats stats = hashFile.getStats();
            result.append("• Úroveň (level): ").append(stats.level).append("\n");
            result.append("• Split pointer: ").append(stats.splitPointer).append("\n");
            result.append("• Blokov v databáze: ").append(stats.totalBlocks).append("\n");
            result.append("• Overflow blokov: ").append(stats.overflowBlocks).append("\n");
            result.append("• Hustota (load factor): ").append(String.format("%.2f", stats.loadFactor)).append("\n\n");
        } catch (IOException e) {
            result.append("• Štatistiky: nedostupné\n\n");
        }

        if (validationErrors > 0) {
            result.append("DETEKOVANÉ CHYBY PRI VALIDÁCII:\n");
            result.append(validationLog.append("\n"));
        } else {
            result.append("Validácia prebehla úspešne\n");
        }

        return result.toString();
    }

    /**
     * Compares the actual database structure with the list
     */
    private String compareDatabaseStructure() throws IOException {
        StringBuilder differences = new StringBuilder();

        List<Person> databaseRecords = new ArrayList<>();

        LinearHashing.LinearHashingStats stats = hashFile.getStats();
        for (int i = 0; i < stats.totalBlocks; i++) {
            LHBlock<Person> primaryBlock = hashFile.readPrimaryBlock(i);

            for (Person record : primaryBlock.getRecords()) {
                if (record != null && !record.getKey().trim().isEmpty()) {
                    databaseRecords.add(record);
                }
            }

            int overflowPointer = primaryBlock.getNextOverflow();
            while (overflowPointer != -1) {
                OverflowFile<Person> overflowFile = hashFile.getOverflowFile();
                OverflowBlock<Person> overflowBlock = overflowFile.readOverflowBlock(overflowPointer);

                for (Person record : overflowBlock.getRecords()) {
                    if (record != null && !record.getKey().trim().isEmpty()) {
                        databaseRecords.add(record);
                    }
                }

                overflowPointer = overflowBlock.getNextOverflow();
            }
        }

        Set<Person> databaseSet = new HashSet<>(databaseRecords);
        Set<Person> insertedSet = new HashSet<>(insertedPersons);

        for (Person person : insertedPersons) {
            if (!databaseSet.contains(person)) {
                differences.append("Záznam chýba v databáze: ").append(person.getKey()).append(" - ").append(person).append("\n");
            }
        }

        for (Person person : databaseRecords) {
            if (!insertedSet.contains(person)) {
                differences.append("Tento záznam nemal byť v databáze: ").append(person.getKey()).append(" - ").append(person).append("\n");
            }
        }

        // Check counts
        if (insertedPersons.size() != databaseRecords.size()) {
            differences.append("Nesúlad v počte záznamov: insertedPersons=")
                    .append(insertedPersons.size())
                    .append(", databáza=")
                    .append(databaseRecords.size())
                    .append("\n");
        }

        return differences.toString();
    }

    /**
     * Synchronizes the internal list with actual database state
     */
    public void synchronizeWithDatabase() throws IOException {
        this.insertedPersons.clear();

        LinearHashing.LinearHashingStats stats = hashFile.getStats();
        for (int i = 0; i < stats.totalBlocks; i++) {
            LHBlock<Person> primaryBlock = hashFile.readPrimaryBlock(i);

            for (Person record : primaryBlock.getRecords()) {
                if (record != null && !record.getKey().trim().isEmpty()) {
                    this.insertedPersons.add(record);
                }
            }

            int overflowPointer = primaryBlock.getNextOverflow();
            while (overflowPointer != -1) {
                OverflowFile<Person> overflowFile = hashFile.getOverflowFile();
                OverflowBlock<Person> overflowBlock = overflowFile.readOverflowBlock(overflowPointer);

                for (Person record : overflowBlock.getRecords()) {
                    if (record != null && !record.getKey().trim().isEmpty()) {
                        this.insertedPersons.add(record);
                    }
                }

                overflowPointer = overflowBlock.getNextOverflow();
            }
        }

        Set<Person> uniquePersons = new LinkedHashSet<>(this.insertedPersons);
        this.insertedPersons.clear();
        this.insertedPersons.addAll(uniquePersons);
    }

    /**
     * Validtion of hashfile
     */
    private String validateHashFile() throws IOException {
        StringBuilder errors = new StringBuilder();

        try {
            LinearHashing.LinearHashingStats stats = hashFile.getStats();

            // all inserted record should be in hashfile
            for (Person person : insertedPersons) {
                Person found = hashFile.get(person.getKey());
                if (found == null) {
                    errors.append("Chýbajúci záznam: ").append(person.getKey()).append(". ");
                } else if (!found.equals(person)) {
                    errors.append("Nekonzistentný záznam: ").append(person.getKey()).append(". ");
                }
            }

            // each record should be in the correct block according to hash functions
            for (Person person : insertedPersons) {
                String key = person.getKey();
                int expectedBlock = hashFile.getTargetBlock(key);

                boolean foundInCorrectBlock = isRecordInCorrectBlock(expectedBlock, key);
                if (!foundInCorrectBlock) {
                    errors.append("Záznam ").append(key).append(" nie je v správnom bloku ").append(expectedBlock).append(". ");
                }
            }

            // checks if split pointer and level are correct
            int expectedTotalBlocks = hashFile.getM() * (int) Math.pow(2, stats.level) + stats.splitPointer;
            if (stats.totalBlocks != expectedTotalBlocks) {
                errors.append("Nesúlad v počte blokov: očakávané ").append(expectedTotalBlocks)
                        .append(", skutočné ").append(stats.totalBlocks)
                        .append(" (M=").append(hashFile.getM())
                        .append(", level=").append(stats.level)
                        .append(", splitPointer=").append(stats.splitPointer).append("). ");
            }

            // overflow chains integrity check
            validateOverflowChains(errors);

        } catch (Exception e) {
            errors.append("Chyba pri validácii: ").append(e.getMessage()).append(". ");
        }

        return errors.toString();
    }

    /**
     * Checks if a record is in the correct block
     */
    private boolean isRecordInCorrectBlock(int expectedBlockIndex, String key) throws IOException {
        LHBlock<Person> block = hashFile.readPrimaryBlock(expectedBlockIndex);
        if (containsRecord(block, key)) {
            return true;
        }

        int overflowPointer = block.getNextOverflow();
        while (overflowPointer != -1) {
            OverflowFile<Person> overflowFile = hashFile.getOverflowFile();
            OverflowBlock<Person> overflowBlock = overflowFile.readOverflowBlock(overflowPointer);
            if (containsRecord(overflowBlock, key)) {
                return true;
            }
            overflowPointer = overflowBlock.getNextOverflow();
        }

        return false;
    }

    /**
     * Checks if a block contains a record with the given key
     */
    private boolean containsRecord(heap.Block<Person> block, String key) {
        for (Person record : block.getRecords()) {
            if (record != null && record.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates the integrity of overflow chains
     */
    private void validateOverflowChains(StringBuilder errors) throws IOException {
        LinearHashing.LinearHashingStats stats = hashFile.getStats();

        for (int i = 0; i < stats.totalBlocks; i++) {
            LHBlock<Person> primaryBlock = hashFile.readPrimaryBlock(i);
            int overflowPointer = primaryBlock.getNextOverflow();

            Set<Integer> visitedBlocks = new HashSet<>();
            int currentPointer = overflowPointer;
            int actualOverflowCount = 0;

            while (currentPointer != -1) {
                if (visitedBlocks.contains(currentPointer)) {
                    errors.append("Cyklus v overflow reťazci bloku ").append(i).append(". ");
                    break;
                }
                visitedBlocks.add(currentPointer);

                OverflowFile<Person> overflowFile = hashFile.getOverflowFile();
                OverflowBlock<Person> overflowBlock = overflowFile.readOverflowBlock(currentPointer);

                for (Person record : overflowBlock.getRecords()) {
                    if (record != null && !record.getKey().trim().isEmpty()) {
                        actualOverflowCount++;
                    }
                }

                currentPointer = overflowBlock.getNextOverflow();
            }

            if (primaryBlock.getOverflowRecordCount() != actualOverflowCount) {
                errors.append("Nesúlad overflow počtu v bloku ").append(i)
                        .append(": štatistika=").append(primaryBlock.getOverflowRecordCount())
                        .append(", skutočnosť=").append(actualOverflowCount).append(". ");
            }
        }
    }

    /**
     * Runs a specified number of insert operations
     */
    public String runInsertOnly(int records, Consumer<String> progressCallback) throws IOException {
        this.synchronizeWithDatabase();
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

                int originalSize = this.insertedPersons.size();

                hashFile.insert(newPerson, newPerson.getKey());
                this.insertedPersons.add(newPerson);
                successfulInserts++;

                String structureComparison = this.compareDatabaseStructure();
                if (!structureComparison.isEmpty()) {
                    validationErrors++;
                    validationLog.append("NESÚLAD ŠTRUKTÚRY po INSERT záznamu ").append(i)
                            .append(" (ID: ").append(newPerson.getKey()).append("):\n")
                            .append(structureComparison).append("\n");
                } else if (this.insertedPersons.size() != originalSize + 1) {
                    validationErrors++;
                    validationLog.append("NESÚLAD VEĽKOSTI po INSERT záznamu ").append(i)
                            .append(": pôvodná veľkosť=").append(originalSize)
                            .append(", nová veľkosť=").append(this.insertedPersons.size()).append("\n");
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
                validationLog.append("CHYBA pri vkladaní záznamu ").append(i)
                        .append(" (ID: ").append(ex.getMessage()).append("): ").append(ex.getMessage()).append("\n");
            }
        }

        if (progressCallback != null) {
            progressCallback.accept("HROMADNÉ VKLADANIE DOKONČENÉ\n\n" +
                    "Spracúvajú sa výsledky...\n");
        }

        String finalStructureCheck = this.compareDatabaseStructure();
        if (!finalStructureCheck.isEmpty()) {
            validationErrors++;
            validationLog.append("NESÚLAD ŠTRUKTÚRY po dokončení všetkých INSERT operácií:\n")
                    .append(finalStructureCheck).append("\n");
        }

        String finalValidation = this.validateHashFile();
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

        try {
            LinearHashing.LinearHashingStats stats = hashFile.getStats();
            result.append("• Celkový počet blokov: ").append(stats.totalBlocks).append("\n");
            result.append("• Hustota (load factor): ").append(String.format("%.2f", stats.loadFactor)).append("\n");
            result.append("• Overflow záznamov: ").append(stats.totalOverflowRecords).append("\n");
        } catch (IOException e) {
            result.append("• Štatistiky: nedostupné\n");
        }

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
        if (this.hashFile != null) {
            this.hashFile.close();
        }

        new java.io.File("pacienti_hash.dat").delete();
        new java.io.File("pacienti_hash.dat.meta").delete();
        new java.io.File("pacienti_hash.dat.overflow").delete();
        new java.io.File("pacienti_hash.dat.overflow.meta").delete();

        this.hashFile = null;
        this.insertedPersons.clear();
        this.patientCounter = 1;

        return "HASH DATABÁZA VYČISTENÁ\n\nVšetky dáta boli úspešne odstránené.\n";
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
        String id = String.format("%06d", patientCounter++);
        return new Person(name, surname, date, id);
    }

    /**
     * Gets statistics about the hash file
     */
    public String getStatistics() {
        try {
            LinearHashing.LinearHashingStats stats = hashFile.getStats();

            StringBuilder sb = new StringBuilder();
            sb.append("ŠTATISTIKY LINEÁRNEHO HEŠOVANIA\n\n");
            sb.append("• Úroveň (level): ").append(stats.level).append("\n");
            sb.append("• Split pointer: ").append(stats.splitPointer).append("\n");
            sb.append("• Celkový počet záznamov: ").append(stats.totalRecords).append("\n");
            sb.append("• Celkový počet blokov: ").append(stats.totalBlocks).append("\n");
            sb.append("• Neprázdne bloky: ").append(stats.nonEmptyBlocks).append("\n");
            sb.append("• Overflow bloky: ").append(stats.overflowBlocks).append("\n");
            sb.append("• Záznamy v overflow: ").append(stats.totalOverflowRecords).append("\n");
            sb.append("• Hustota (load factor): ").append(String.format("%.2f", stats.loadFactor)).append("\n");

            return sb.toString();

        } catch (Exception e) {
            return "Chyba pri získavaní štatistík: " + e.getMessage();
        }
    }

    /**
     * Displays all blocks (primary and overflow) with their contents
     */
    public String displayAllBlocks() throws IOException {
        return this.hashFile.displayAllBlocks("PACIENTI");
    }
}