package database;

import data.Person;
import data.PCRTest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DatabaseGenerator {
    private static final String[] FIRST_NAMES = {"Anna", "Peter", "Maria", "Jozef", "Eva", "Michal", "Katarina"};
    private static final String[] LAST_NAMES = {"Novak", "Horak", "Kral", "Bielik", "Farkas", "Kovac", "Urban"};
    private static final Random RAND = new Random();

    private final Database database;

    public DatabaseGenerator(Database database) {
        this.database = database;
    }

    public static Person generatePerson(String patientId) {
        String firstName = FIRST_NAMES[RAND.nextInt(FIRST_NAMES.length)];
        String lastName  = LAST_NAMES[RAND.nextInt(LAST_NAMES.length)];
        LocalDate birthDate = LocalDate.of(1950 + RAND.nextInt(70), 1 + RAND.nextInt(12), 1 + RAND.nextInt(28));
        return new Person(firstName, lastName, birthDate, patientId);
    }

    public static PCRTest generatePCRTest(int testId, String patientId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusYears(2);
        LocalDateTime endDate = now;

        long startEpoch = startDate.toEpochSecond(java.time.ZoneOffset.UTC);
        long endEpoch = endDate.toEpochSecond(java.time.ZoneOffset.UTC);
        long randomEpoch = startEpoch + (long)(RAND.nextDouble() * (endEpoch - startEpoch));
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(randomEpoch, 0, java.time.ZoneOffset.UTC);

        boolean positive = RAND.nextDouble() < 0.2;
        double value = positive ? 20 + RAND.nextDouble() * 80 : RAND.nextDouble() * 20;
        String note = String.format("TEST%d", testId);

        return new PCRTest(dateTime, patientId, testId, positive, value, note);
    }


    public void fillDatabase(int numPersons, int numTests) throws IOException {
        int successfulTests = 0;

        List<String> allPatientIds = new ArrayList<>(numPersons);
        for (int i = 1; i <= numPersons; i++) {
            String newId = database.nextPatientId();
            Person p = generatePerson(newId);
            if (this.database.insertPerson(p) != null) {
                allPatientIds.add(newId);
            }
        }

        List<String> eligible = new ArrayList<>(allPatientIds.size());
        for (String pid : allPatientIds) {
            Person patient = this.database.findPerson(pid);
            if (patient != null && patient.canAddTest()) {
                eligible.add(pid);
            }
        }

        Collections.shuffle(eligible);

        int idx = 0;
        while (successfulTests < numTests && !eligible.isEmpty()) {
            String pid = eligible.get(idx);

            Person patient = this.database.findPerson(pid);
            if (patient == null) {
                eligible.remove(idx);
                if (eligible.isEmpty()) break;
                idx = idx % eligible.size();
                continue;
            }

            if (!patient.canAddTest()) {
                eligible.remove(idx);
                if (eligible.isEmpty()) break;
                idx = idx % eligible.size();
                continue;
            }

            int newCode = database.nextTestCode();
            PCRTest t = generatePCRTest(newCode, pid);

            PCRTest inserted = this.database.insertPCRTest(t);
            if (inserted != null) {
                successfulTests++;
            } else {
                patient = this.database.findPerson(pid);
                if (patient == null || !patient.canAddTest()) {
                    eligible.remove(idx);
                    if (eligible.isEmpty()) break;
                    idx = idx % eligible.size();
                    continue;
                }
            }

            idx = (idx + 1) % eligible.size();
        }
    }
}