package util;

import model.*;
import java.time.LocalDate;

public class SimpleTest {
    public static void main(String[] args) {
        try {
            // Vymaž staré súbory
            new java.io.File("pacienti.dat").delete();
            new java.io.File("pacienti.dat.meta").delete();

            // Test serializácie
            System.out.println("=== TEST SERIALIZÁCIE ===");
            Person testPerson = new Person("Ján", "Novak", LocalDate.of(1990, 5, 15), "P1234");
            System.out.println("Pôvodný: " + testPerson);

            byte[] bytes = testPerson.getBytes();
            System.out.println("Serializovaných bytes: " + bytes.length);

            Person testPerson2 = new Person();
            testPerson2.fromBytes(bytes);
            System.out.println("Deserializovaný: " + testPerson2);

            System.out.println("Rovnajú sa: " + testPerson.equals(testPerson2));

            // Test HeapFile
            System.out.println("\n=== TEST HEAPFILE ===");
            HeapFile<Person> heap = new HeapFile<>("pacienti.dat", 256, new Person());

            // Vlož pár záznamov
            Person p1 = new Person("Anna", "Kováčová", LocalDate.of(1985, 3, 20), "P1001");
            Person p2 = new Person("Peter", "Novák", LocalDate.of(1992, 7, 12), "P1002");

            int block1 = heap.insert(p1);
            int block2 = heap.insert(p2);

            // Načítaj a skontroluj
            Person found1 = heap.get(block1, p1);
            Person found2 = heap.get(block2, p2);

            System.out.println("P1 nájdený: " + (found1 != null ? found1 : "NULL"));
            System.out.println("P2 nájdený: " + (found2 != null ? found2 : "NULL"));

            // Zobraziť obsah
            System.out.println("\n" + heap.displayAll());

            heap.close();

        } catch (Exception e) {
            System.err.println("Chyba: " + e.getMessage());
            e.printStackTrace();
        }
    }
}