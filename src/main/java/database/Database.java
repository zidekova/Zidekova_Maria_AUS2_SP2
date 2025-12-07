package database;

import hash.LinearHashing;
import data.Person;
import data.PCRTest;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private final LinearHashing<Person> persons;
    private final LinearHashing<PCRTest> tests;

    private int nextTestCounter;
    private int nextPatientCounter;
    private String lastError = null;

    private final String basePath;
    private final String personsFilePath;
    private final String testsFilePath;

    // for creating new database
    public Database(String basePath, int initialM, int personBlockSize, int testBlockSize, int personOverflowBlockSize, int testOverflowBlockSize) throws IOException {
        this.basePath = basePath;
        this.personsFilePath = basePath + "/persons.dat";
        this.testsFilePath   = basePath + "/tests.dat";

        Person personTemplate = new Person();
        PCRTest testTemplate  = new PCRTest();

        this.persons = new LinearHashing<>(
                this.personsFilePath,
                personBlockSize,
                personOverflowBlockSize,
                personTemplate,
                initialM
        );
        this.tests = new LinearHashing<>(
                this.testsFilePath,
                testBlockSize,
                testOverflowBlockSize,
                testTemplate,
                initialM
        );

        this.nextPatientCounter = 1;
        this.nextTestCounter = 1;

        writeConfig(initialM, personBlockSize, testBlockSize, personOverflowBlockSize, testOverflowBlockSize);
    }

    // for opening existing database
    public Database(String basePath) throws IOException {
        this.basePath = basePath;
        this.personsFilePath = basePath + "/persons.dat";
        this.testsFilePath = basePath + "/tests.dat";

        int initialM = 4, personBlockSize = 1024, testBlockSize = 2048, personOverflowBlockSize = 512, testOverflowBlockSize = 512;
        Integer loadedNextPatient = null;
        Integer loadedNextTest = null;

        File cfg = new File(basePath, "dbs.config");
        if (!cfg.exists()) throw new IOException("Konfiguračný súbor dbs.config sa nenašiel v: " + basePath);

        try (BufferedReader br = new BufferedReader(new FileReader(cfg))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("initialM=")) initialM = Integer.parseInt(line.split("=", 2)[1]);
                else if (line.startsWith("personBlockSize=")) personBlockSize = Integer.parseInt(line.split("=", 2)[1]);
                else if (line.startsWith("testBlockSize=")) testBlockSize = Integer.parseInt(line.split("=", 2)[1]);
                else if (line.startsWith("personOverflowBlockSize="))
                    personOverflowBlockSize = Integer.parseInt(line.split("=",2)[1]);
                else if (line.startsWith("testOverflowBlockSize="))
                    testOverflowBlockSize = Integer.parseInt(line.split("=",2)[1]);
                else if (line.startsWith("nextPatientCounter="))
                    loadedNextPatient = Integer.parseInt(line.split("=", 2)[1]);
                else if (line.startsWith("nextTestCounter=")) loadedNextTest = Integer.parseInt(line.split("=", 2)[1]);
            }
        }

        Person personTemplate = new Person();
        PCRTest testTemplate = new PCRTest();
        this.persons = new LinearHashing<>(
                this.personsFilePath,
                personBlockSize,
                personOverflowBlockSize,
                personTemplate,
                initialM
        );
        this.tests = new LinearHashing<>(
                this.testsFilePath,
                testBlockSize,
                testOverflowBlockSize,
                testTemplate,
                initialM
        );

        if (loadedNextPatient != null && loadedNextTest != null) {
            this.nextPatientCounter = loadedNextPatient;
            this.nextTestCounter = loadedNextTest;
        } else {
            this.nextPatientCounter = 1;
            this.nextTestCounter = 1;
            writeConfig(initialM, personBlockSize, testBlockSize, personOverflowBlockSize, testOverflowBlockSize);
        }
    }


    public void writeConfig(int initialM, int personBlockSize, int testBlockSize, int personOverflowBlockSize, int testOverflowBlockSize) {
        try (PrintWriter pw = new PrintWriter(new File(this.basePath, "dbs.config"))) {
            pw.println("initialM=" + initialM);
            pw.println("personBlockSize=" + personBlockSize);
            pw.println("testBlockSize=" + testBlockSize);
            pw.println("personOverflowBlockSize=" + personOverflowBlockSize);
            pw.println("testOverflowBlockSize=" + testOverflowBlockSize);
            pw.println("nextPatientCounter=" + this.nextPatientCounter);
            pw.println("nextTestCounter=" + this.nextTestCounter);
        } catch (Exception e) {
            throw new RuntimeException("Nepodarilo sa zapísať dbs.config: " + e.getMessage(), e);
        }
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * 1) Insert PCR test result
     */
    public PCRTest insertPCRTest(PCRTest test) throws IOException {
        Person patient = this.persons.get(test.getPatientId());
        if (patient == null) {
            lastError = "Pacient s ID " + test.getPatientId() + " neexistuje.";
            return null;

        }

        if (!patient.canAddTest()) {
            lastError = "Pacient už má maximálny počet testov (6).";
            return null;
        }

        this.tests.insert(test, String.valueOf(test.getTestCode()));

        patient.addTestCode(test.getTestCode());
        this.persons.update(patient);

        lastError = null;
        return test;
    }

    /**
     * 2) Find person with all tests
     */
    public List<PCRTest> getTestsForPatient(Person person) throws IOException {
        if (person == null) {
            return new ArrayList<>();
        }

        List<PCRTest> result = new ArrayList<>();
        for (int testCode : person.getTestCodes()) {
            PCRTest test = this.tests.get(String.valueOf(testCode));
            if (test != null) {
                result.add(test);
            }
        }
        return result;
    }

    /**
     * 3) Find PCR test by code with patient data
     */
    public PCRTest findPCRTest(int testId) throws IOException {
        return this.tests.get(String.valueOf(testId));
    }

    /**
     * 4) Insert person into system
     */
    public Person insertPerson(Person person) throws IOException {
        Person existing = this.persons.get(person.getId());
        if (existing != null) {
            return null;
        }

        this.persons.insert(person, person.getId());

        return person;
    }

    /**
     * 5) Delete PCR test result
     */
    public boolean deletePCRTest(int testCode) throws IOException {
        PCRTest test = this.tests.get(String.valueOf(testCode));
        if (test == null) {
            return false;
        }

        String patientId = test.getPatientId();

        Person patient = this.persons.get(patientId);
        if (patient != null) {
            patient.removeTestCode(testCode);
            this.persons.update(patient);
        }

        return this.tests.delete(String.valueOf(testCode));
    }

    /**
     * 6) Delete person with all test results
     */
    public boolean deletePersonWithTests(String patientId) throws IOException {
        Person person = this.persons.get(patientId);
        if (person == null) {
            return false;
        }

        for (int testCode : person.getTestCodes()) {
            this.tests.delete(String.valueOf(testCode));
        }

        return this.persons.delete(patientId);
    }

    /**
     * 7) Find person for editing
     */
    public Person updatePerson(Person updatedPerson) throws IOException {
        Person existing = this.persons.get(updatedPerson.getId());
        if (existing == null) {
            return null;
        }

        if (!this.persons.update(updatedPerson)) return null;

        return updatedPerson;
    }

    /**
     * 8) Find PCR test for editing
     */
    public PCRTest updatePCRTest(PCRTest updatedTest) throws IOException {
        int code = updatedTest.getTestCode();
        PCRTest existing = this.tests.get(String.valueOf(code));
        if (existing == null) {
            return null;
        }

        String oldPid = existing.getPatientId();
        String newPid = updatedTest.getPatientId();

        if (oldPid.equals(newPid)) {
            if (!this.tests.update(updatedTest)) return null;
            return updatedTest;
        }

        Person newP = this.persons.get(newPid);
        if (newP == null) {
            return null;
        }

        Person oldP = this.persons.get(oldPid);
        if (oldP == null) {
            return null;
        }

        if (!newP.canAddTest()) {
            return null;
        }

        oldP.removeTestCode(code);
        this.persons.update(oldP);

        newP.addTestCode(code);
        this.persons.update(newP);

        if (!this.tests.update(updatedTest)) return null;

        return updatedTest;
    }

    public Person findPerson(String patientId) throws IOException {
        return this.persons.get(patientId);
    }

    public String nextPatientId() {
        return String.valueOf(nextPatientCounter++);
    }

    public int nextTestCode() {
        return nextTestCounter++;
    }


    /**
     * Displays all blocks of a hashfile PERSONS
     */
    public String displayPersons() throws IOException {
        return this.persons.displayAllBlocks("PACIENTI");
    }

    /**
     * Displays all blocks of a hashfile TESTS
     */
    public String displayTests() throws IOException {
        return this.tests.displayAllBlocks("PCR TESTY");
    }

    /**
     * Close database
     */
    public void close() throws IOException {
        if (this.persons != null) this.persons.close();
        if (this.tests != null) this.tests.close();

        try {
            writeConfig(this.persons.getM(),
                    this.persons.getClusterSize(),
                    this.tests.getClusterSize(),
                    this.persons.getOverflowFile().getClusterSize(),
                    this.tests.getOverflowFile().getClusterSize());
        } catch (Exception e) {
            System.err.println("Nepodarilo sa zapísať počítadlá do dbs.config: " + e.getMessage());
        }

    }
}