package util;

import model.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Random;

public class OperationGenerator {
    private static final String[] NAMES = {"Anna", "Peter", "Marek", "Lucia", "Ján", "Eva", "Milan", "Zuzana"};
    private static final String[] SURNAMES = {"Novak", "Kral", "Hrasko", "Mala", "Urban", "Velky", "Stary", "Novy"};
    private static final Random random = new Random();

    public static void main(String[] args) {
        try {
            // Clean old files
            new java.io.File("pacienti.dat").delete();
            new java.io.File("pacienti.dat.meta").delete();

            HeapFile<Person> heap = new HeapFile<>("pacienti.dat", 256, new Person());

            System.out.println("=== INSERT OPERATIONS ===");
            Person[] insertedPersons = new Person[10];

            // Insert records
            for (int i = 0; i < 10; i++) {
                Person p = randomPatient();
                insertedPersons[i] = p;
                int blockIndex = heap.insert(p);
                System.out.println("✓ Inserted: " + p + " into block " + blockIndex);
            }

            System.out.println("\n=== READ OPERATIONS ===");
            // Test reading all inserted records
            for (int i = 0; i < insertedPersons.length; i++) {
                Person original = insertedPersons[i];
                // Try to find in possible blocks
                boolean found = false;
                int maxBlocks = Math.max(1, heap.getBlockCount());
                for (int block = 0; block < maxBlocks; block++) {
                    try {
                        Person result = heap.get(block, original);
                        if (result != null && result.equals(original)) {
                            System.out.println("✓ Found in block " + block + ": " + result);
                            found = true;
                            break;
                        }
                    } catch (IllegalArgumentException e) {
                        // Block doesn't exist, continue to next
                        break;
                    }
                }
                if (!found) {
                    System.out.println("✗ ERROR: Record " + original.getId() + " not found!");
                }
            }

            System.out.println("\n=== DELETE OPERATIONS ===");
            // Delete some records
            if (insertedPersons.length > 3) {
                Person toDelete1 = insertedPersons[1];
                Person toDelete2 = insertedPersons[3];
                Person toDelete3 = insertedPersons[5];

                System.out.println("Attempting to delete records:");
                System.out.println(" - " + toDelete1.getId());
                System.out.println(" - " + toDelete2.getId());
                System.out.println(" - " + toDelete3.getId());

                // Try to delete from possible blocks
                boolean deleted1 = tryDeleteFromBlocks(heap, toDelete1);
                boolean deleted2 = tryDeleteFromBlocks(heap, toDelete2);
                boolean deleted3 = tryDeleteFromBlocks(heap, toDelete3);

                System.out.println("Deletion results:");
                System.out.println(" - " + toDelete1.getId() + ": " + (deleted1 ? "✓ SUCCESS" : "✗ FAILED"));
                System.out.println(" - " + toDelete2.getId() + ": " + (deleted2 ? "✓ SUCCESS" : "✗ FAILED"));
                System.out.println(" - " + toDelete3.getId() + ": " + (deleted3 ? "✓ SUCCESS" : "✗ FAILED"));

                // Verify deletions
                System.out.println("\nVerification:");
                verifyDeletion(heap, toDelete1, "toDelete1");
                verifyDeletion(heap, toDelete2, "toDelete2");
                verifyDeletion(heap, toDelete3, "toDelete3");
            }

            System.out.println("\n=== INSERT AFTER DELETION ===");
            // Insert new records after deletion (testing slot reuse)
            Person newPerson1 = new Person("New", "Person", LocalDate.of(2000, 1, 1), "P9999");
            Person newPerson2 = new Person("Test", "Patient", LocalDate.of(1995, 6, 15), "P8888");
            Person newPerson3 = new Person("Reuse", "Slot", LocalDate.of(1985, 3, 10), "P7777");

            int block1 = heap.insert(newPerson1);
            int block2 = heap.insert(newPerson2);
            int block3 = heap.insert(newPerson3);

            System.out.println("✓ New record inserted into block " + block1 + ": " + newPerson1);
            System.out.println("✓ New record inserted into block " + block2 + ": " + newPerson2);
            System.out.println("✓ New record inserted into block " + block3 + ": " + newPerson3);

            // Verify new records can be found
            System.out.println("\nVerifying new records:");
            verifyRecord(heap, newPerson1, "newPerson1");
            verifyRecord(heap, newPerson2, "newPerson2");
            verifyRecord(heap, newPerson3, "newPerson3");

            System.out.println("\n=== BITMAP FUNCTIONALITY TEST ===");
            testBitmapFunctionality(heap);

            System.out.println("\n=== COMPLETE FILE CONTENT ===");
            String content = heap.displayAll();
            System.out.println(content);

            heap.close();

            System.out.println("=== OPERATION GENERATOR COMPLETED ===");

        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean tryDeleteFromBlocks(HeapFile<Person> heap, Person person) throws IOException {
        int maxBlocks = Math.max(1, heap.getBlockCount());
        for (int block = 0; block < maxBlocks; block++) {
            try {
                if (heap.delete(block, person)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Block doesn't exist, stop searching
                break;
            }
        }
        return false;
    }

    private static void verifyDeletion(HeapFile<Person> heap, Person person, String label) throws IOException {
        boolean stillExists = false;
        int maxBlocks = Math.max(1, heap.getBlockCount());
        for (int block = 0; block < maxBlocks; block++) {
            try {
                Person found = heap.get(block, person);
                if (found != null) {
                    stillExists = true;
                    System.out.println("Found " + person.getId() + " in block " + block);
                    break;
                }
            } catch (IllegalArgumentException e) {
                // Block doesn't exist, continue to next
            }
        }
        if (stillExists) {
            System.out.println("✗ " + label + " (" + person.getId() + ") still exists!");
        } else {
            System.out.println("✓ " + label + " (" + person.getId() + ") successfully deleted");
        }
    }

    private static void verifyRecord(HeapFile<Person> heap, Person person, String label) throws IOException {
        boolean found = false;
        int maxBlocks = Math.max(1, heap.getBlockCount());
        for (int block = 0; block < maxBlocks; block++) {
            try {
                Person result = heap.get(block, person);
                if (result != null && result.equals(person)) {
                    found = true;
                    System.out.println("✓ " + label + " (" + person.getId() + ") found in block " + block);
                    break;
                }
            } catch (IllegalArgumentException e) {
                // Block doesn't exist, continue to next
            }
        }
        if (!found) {
            System.out.println("✗ " + label + " (" + person.getId() + ") NOT FOUND!");
        }
    }

    private static void testBitmapFunctionality(HeapFile<Person> heap) throws IOException {
        System.out.println("Testing bitmap slot reuse...");

        // Insert some test records
        Person test1 = new Person("Bitmap", "Test1", LocalDate.of(2000, 1, 1), "BT001");
        Person test2 = new Person("Bitmap", "Test2", LocalDate.of(2000, 1, 1), "BT002");

        int block1 = heap.insert(test1);
        int block2 = heap.insert(test2);
        System.out.println("Inserted test records in blocks " + block1 + " and " + block2);

        // Delete them
        boolean del1 = tryDeleteFromBlocks(heap, test1);
        boolean del2 = tryDeleteFromBlocks(heap, test2);
        System.out.println("Deleted test records: " + del1 + ", " + del2);

        // Insert new records - they should reuse the slots
        Person test3 = new Person("Reused", "Slot1", LocalDate.of(2000, 1, 1), "RS001");
        Person test4 = new Person("Reused", "Slot2", LocalDate.of(2000, 1, 1), "RS002");

        int block3 = heap.insert(test3);
        int block4 = heap.insert(test4);
        System.out.println("Inserted into reused slots in blocks " + block3 + " and " + block4);

        // Verify the new records
        verifyRecord(heap, test3, "reusedSlot1");
        verifyRecord(heap, test4, "reusedSlot2");
    }

    private static Person randomPatient() {
        String name = NAMES[random.nextInt(NAMES.length)];
        String surname = SURNAMES[random.nextInt(SURNAMES.length)];
        LocalDate date = LocalDate.of(1970 + random.nextInt(40), 1 + random.nextInt(12), 1 + random.nextInt(28));
        String id = "P" + (1000 + random.nextInt(9000));
        return new Person(name, surname, date, id);
    }
}