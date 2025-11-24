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
     * Runs a specified number of random operations (INSERT, FIND, DELETE)
     */
    public String runRandomOperations(int operations) throws IOException {
        int insertCount = 0;
        int findCount = 0;
        int deleteCount = 0;
        int errors = 0;

        for (int i = 0; i < operations; i++) {
            int op = random.nextInt(3);

            try {
                switch (op) {
                    case 0 -> { // insert
                        Person newPerson = this.generateRandomPatient();
                        this.heap.insert(newPerson);
                        this.insertedPersons.add(newPerson);
                        insertCount++;
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

            } catch (Exception ex) {
                errors++;
            }
        }

        // final results
        StringBuilder result = new StringBuilder();
        result.append("OPERÁCIE DOKONČENÉ\n\n");
        result.append("• Celkový počet operácií: ").append(operations).append("\n");
        result.append("• Insert operácie: ").append(insertCount).append("\n");
        result.append("• Find operácie: ").append(findCount).append("\n");
        result.append("• Delete operácie: ").append(deleteCount).append("\n");
        result.append("• Chyby: ").append(errors).append("\n");
        result.append("• Blokov v databáze: ").append(this.heap.getBlockCount()).append("\n\n");

        return result.toString();
    }

    /**
     * Runs a specified number of insert operations
     */
    public String runInsertOnly(int records, Consumer<String> progressCallback) throws IOException {
        progressCallback.accept("HROMADNÉ VKLADANIE SPUSTENÉ\n\n" +
                "Vkladá sa " + records + " záznamov...\n");

        int successfulInserts = 0;
        int failedInserts = 0;

        for (int i = 0; i < records; i++) {
            try {
                Person newPerson = this.generateRandomPatient();
                this.heap.insert(newPerson);
                this.insertedPersons.add(newPerson);
                successfulInserts++;

            } catch (Exception ex) {
                failedInserts++;
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("HROMADNÉ VKLADANIE DOKONČENÉ\n\n");
        result.append("VÝSLEDKY:\n");
        result.append("• Pokusy o vloženie: ").append(records).append("\n");
        result.append("• Úspešné vloženia: ").append(successfulInserts).append("\n");
        result.append("• Neúspešné vloženia: ").append(failedInserts).append("\n");
        result.append("• Úspešnosť: ").append(String.format("%.1f", (successfulInserts * 100.0 / records))).append("%\n");
        result.append("• Celkový počet blokov: ").append(this.heap.getBlockCount()).append("\n");

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

        this.heap = new HeapFile<>("pacienti.dat", 256, new Person());
        this.insertedPersons.clear();

        return "DATABÁZA VYČISTENÁ\n\n" +
                "Všetky dáta boli úspešne odstránené.\n";
    }

    /**
     * Attempts to delete a person from any block in the heap file
     */
    private boolean tryDeleteFromBlocks(Person person) throws IOException {
        int maxBlocks = Math.max(1, heap.getBlockCount());
        for (int block = 0; block < maxBlocks; block++) {
            try {
                if (heap.delete(block, person)) {
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