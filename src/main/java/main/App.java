package main;

import database.Database;
import database.DatabaseGenerator;
import data.Person;
import data.PCRTest;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class App {
    private Database database;
    private DatabaseGenerator generator;
    private final GUI gui;

    public App() {
        this.gui = new GUI(this);
    }

    /**
     * 1) Insert PCR test
     */
    public void insertPCRTest(String dateTimeStr, String patientId,
                              String resultStr, String valueStr, String note) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            int testCode = database.nextTestCode();
            boolean result = Boolean.parseBoolean(resultStr);
            double value = Double.parseDouble(valueStr);

            // Create test object
            PCRTest test = new PCRTest(dateTime, patientId, testCode, result, value, note);

            // Insert into database
            PCRTest insertedTest = database.insertPCRTest(test);

            if (insertedTest != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("NOVÝ PCR TEST\n\n");
                sb.append("Kód testu: ").append(insertedTest.getTestCode()).append("\n");
                sb.append("Dátum a čas: ").append(insertedTest.getDateTime()).append("\n");
                sb.append("Výsledok: ").append(insertedTest.getResult() ? "POZITÍVNY" : "NEGATÍVNY").append("\n");
                sb.append("Hodnota: ").append(insertedTest.getValue()).append("\n");
                sb.append("Poznámka: ").append(insertedTest.getNote()).append("\n\n");

                gui.displayResult(sb.toString());
                gui.showMessage("Test bol úspešne vložený!");
            } else {
                String reason = database.getLastError();
                if (reason == null || reason.isBlank()) {
                    reason = "Neznáma chyba pri vkladaní testu.";
                }
                gui.showError("Vloženie PCR testu zlyhalo: " + reason);
            }

        } catch (Exception e) {
            gui.showError("Chyba pri vkladaní testu: " + e.getMessage());
        }
    }

    /**
     * 2) Find person with tests
     */
    public void findPersonWithTests(String patientId) {
        try {
            Person person = database.findPerson(patientId);
            if (person != null) {
                List<PCRTest> tests = database.getTestsForPatient(person);

                StringBuilder result = new StringBuilder();
                result.append("DETAILY O PACIENTOVI\n\n");
                result.append("ID: ").append(person.getId()).append("\n");
                result.append("Meno a prizvisko: ").append(person.getName()).append(" ").append(person.getSurname()).append("\n");
                result.append("Dátum narodenia: ").append(person.getDateOfBirth()).append("\n");
                result.append("Počet PCR testov: ").append(tests.size()).append("\n\n");

                if (!tests.isEmpty()) {
                    result.append("PCR TESTY\n\n");
                    for (PCRTest test : tests) {
                        result.append("Kód: ").append(test.getTestCode()).append("\n");
                        result.append("Dátum a čas vykonania: ").append(test.getDateTime()).append("\n");
                        result.append("Výsledok: ").append(test.getResult() ? "POZITÍVNY" : "NEGATÍVNY").append("\n");
                        result.append("Hodnota: ").append(test.getValue()).append("\n");
                        result.append("Poznámka: ").append(test.getNote()).append("\n");
                        result.append("---\n");
                    }
                }

                gui.displayResult(result.toString());
            } else {
                gui.showMessage("Pacient nebol nájdený!");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri vyhľadávaní pacienta: " + e.getMessage());
        }
    }

    /**
     * 3) Find test with patient
     */
    public void findTestWithPatient(String testCodeStr) {
        try {
            int testCode = Integer.parseInt(testCodeStr);
            PCRTest test = database.findPCRTest(testCode);

            if (test != null) {
                Person patient = database.findPerson(test.getPatientId());

                StringBuilder result = new StringBuilder();
                result.append("DETAILY O PCR TESTE\n\n");
                result.append("Kód: ").append(test.getTestCode()).append("\n");
                result.append("ID pacienta: ").append(test.getPatientId()).append("\n");
                result.append("Dátum a čas vykonania: ").append(test.getDateTime()).append("\n");
                result.append("Výsledok: ").append(test.getResult() ? "POZITÍVNY" : "NEGATÍVNY").append("\n");
                result.append("Hodnota: ").append(test.getValue()).append("\n");
                result.append("Poznámka: ").append(test.getNote()).append("\n\n");

                if (patient != null) {
                    result.append("DETAILY O PACIENTOVI\n\n");
                    result.append("ID: ").append(patient.getId()).append("\n");
                    result.append("Meno a prizvisko: ").append(patient.getName()).append(" ").append(patient.getSurname()).append("\n");
                    result.append("Dátum narodenia: ").append(patient.getDateOfBirth()).append("\n");
                    result.append("Počet PCR testov: ").append(patient.getTestCodes().size()).append("\n\n");
                }

                gui.displayResult(result.toString());
            } else {
                gui.showMessage("Test sa nenašiel!");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri vyhľadávaní testu: " + e.getMessage());
        }
    }

    /**
     * 4) Insert person
     */
    public void insertPerson(String name, String surname, String birthDateStr) {
        try {
            LocalDate birthDate = LocalDate.parse(birthDateStr);
            String patientId = database.nextPatientId();
            Person person = new Person(name, surname, birthDate, patientId);

            Person insertedPerson = database.insertPerson(person);

            if (insertedPerson != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("NOVÝ PACIENT\n\n");
                sb.append("ID: ").append(insertedPerson.getId()).append("\n");
                sb.append("Meno a priezvisko: ").append(insertedPerson.getName()).append(" ").append(insertedPerson.getSurname()).append("\n");
                sb.append("Dátum narodenia: ").append(insertedPerson.getDateOfBirth()).append("\n");
                sb.append("Počet PCR testov: ").append(insertedPerson.getTestCodes().size()).append("\n");

                gui.displayResult(sb.toString());
                gui.showMessage("Pacient bol úspešne vložený!");

            }
        } catch (Exception e) {
            gui.showError("Chyba pri vkladaní pacienta: " + e.getMessage());
        }
    }


    /**
     * 5) Delete PCR test
     */
    public void deletePCRTest(String testCodeStr) {
        try {
            int testCode = Integer.parseInt(testCodeStr);
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Naozaj chceš vymazať PCR test s kódom " + testCode + "?",
                    "Potvrdenie mazania",
                    JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            boolean deleted = database.deletePCRTest(testCode);
            if (deleted) {
                gui.showMessage("Test " + testCode + " bol vymazaný.");
                gui.displayResult("Test " + testCode + " bol vymazaný.\n");
            } else {
                gui.showError("Test " + testCode + " sa nepodarilo vymazať (nemožno nájsť alebo iná chyba).");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri mazaní testu: " + e.getMessage());
        }
    }

    /**
     * 6) Delete person with tests
     */
    public void deletePersonWithTests(String patientId) {
        try {
            int confirm = JOptionPane.showConfirmDialog(null,
                    "Naozaj chceš vymazať pacienta " + patientId + " vrátane všetkých jeho testov?",
                    "Potvrdenie mazania",
                    JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            boolean deleted = database.deletePersonWithTests(patientId);
            if (deleted) {
                gui.showMessage("Pacient " + patientId + " bol vymazaný.");
                gui.displayResult("Pacient " + patientId + " bol vymazaný spolu so všetkými testami.\n");
            } else {
                gui.showError("Pacienta " + patientId + " sa nepodarilo vymazať (nemožno nájsť alebo iná chyba).");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri mazaní pacienta: " + e.getMessage());
        }
    }

    /**
     * 7) Find person for editing
     */
    public void findPersonForEdit(String patientId) {
        try {
            Person person = database.findPerson(patientId);
            if (person != null) {
                StringBuilder result = new StringBuilder();
                result.append("ÚPRAVA PACIENTA\n");
                result.append("Aktuálne hodnoty:\n");
                result.append("ID: ").append(person.getId()).append("\n");
                result.append("Meno: ").append(person.getName()).append("\n");
                result.append("Priezvisko: ").append(person.getSurname()).append("\n");
                result.append("Dátum narodenia: ").append(person.getDateOfBirth()).append("\n");

                gui.setCurrentPersonForEdit(person);
                gui.displayResult(result.toString());
                gui.showEditPersonForm();
            } else {
                gui.showMessage("Pacient nebol nájdený!");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri vyhľadávaní pacienta: " + e.getMessage());
        }
    }

    /**
     * 8) Find test for editing
     */
    public void findTestForEdit(String testCodeStr) {
        try {
            int testCode = Integer.parseInt(testCodeStr);
            PCRTest test = database.findPCRTest(testCode);

            if (test != null) {
                StringBuilder result = new StringBuilder();
                result.append("ÚPRAVA PCR TESTU\n");
                result.append("Aktuálne hodnoty:\n");
                result.append("Kód: ").append(test.getTestCode()).append("\n");
                result.append("ID pacienta: ").append(test.getPatientId()).append("\n");
                result.append("Dátum a čas vykonania: ").append(test.getDateTime()).append("\n");
                result.append("Výsledok: ").append(test.getResult() ? "POZITÍVNY" : "NEGATÍVNY").append("\n");
                result.append("Hodnota: ").append(test.getValue()).append("\n");
                result.append("Poznámka: ").append(test.getNote()).append("\n");

                gui.setCurrentTestForEdit(test);
                gui.displayResult(result.toString());
                gui.showEditTestForm();
            } else {
                gui.showMessage("Test nebol nájdený!");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri vyhľadávaní testu: " + e.getMessage());
        }
    }

    /**
     * Fill database with generated data
     */
    public void fillDatabase(String personsStr, String testsStr) {
        try {
            int numPersons = Integer.parseInt(personsStr);
            int numTests = Integer.parseInt(testsStr);

            generator.fillDatabase(numPersons, numTests);

            gui.showMessage("Databáza bola naplnená " + numPersons + " pacientami a " + numTests + " testami!");


        } catch (Exception e) {
            gui.showError("Chyba pri napĺňaní databázy: " + e.getMessage());
        }
    }

    /**
     * Update person after editing
     */
    public void updatePerson(Person updatedPerson) {
        try {
            Person person = database.updatePerson(updatedPerson);
            if (person != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("PACIENT AKTUALIZOVANÝ\n\n");
                sb.append("ID: ").append(updatedPerson.getId()).append("\n");
                sb.append("Meno a priezvisko: ").append(updatedPerson.getName()).append(" ").append(updatedPerson.getSurname()).append("\n");
                sb.append("Dátum narodenia: ").append(updatedPerson.getDateOfBirth()).append("\n");
                sb.append("Počet PCR testov: ").append(updatedPerson.getTestCodes().size()).append("\n");
                gui.displayResult(sb.toString());
                gui.showMessage("Pacient bol úspešne upravený.");
            } else {
                gui.showError("Úprava pacienta zlyhala.");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri úprave pacienta: " + e.getMessage());
        }
    }

    /**
     * Update test after editing
     */
    public void updatePCRTest(PCRTest updatedTest) {
        try {
            if (database.updatePCRTest(updatedTest) != null) {
                Person p = database.findPerson(updatedTest.getPatientId());
                StringBuilder sb = new StringBuilder();
                sb.append("PCR TEST AKTUALIZOVANÝ\n\n");
                sb.append("Kód: ").append(updatedTest.getTestCode()).append("\n");
                sb.append("ID pacienta: ").append(updatedTest.getPatientId()).append("\n");
                sb.append("Dátum a čas: ").append(updatedTest.getDateTime()).append("\n");
                sb.append("Výsledok: ").append(updatedTest.getResult() ? "POZITÍVNY" : "NEGATÍVNY").append("\n");
                sb.append("Hodnota: ").append(updatedTest.getValue()).append("\n");
                sb.append("Poznámka: ").append(updatedTest.getNote()).append("\n\n");
                if (p != null) {
                    sb.append("PACIENT\n");
                    sb.append("ID: ").append(p.getId()).append("\n");
                    sb.append("Meno a priezvisko: ").append(p.getName()).append(" ").append(p.getSurname()).append("\n");
                    sb.append("Dátum narodenia: ").append(p.getDateOfBirth()).append("\n");
                    sb.append("Počet PCR testov: ").append(p.getTestCodes().size()).append("\n");
                }
                gui.displayResult(sb.toString());
                gui.showMessage("PCR test bol úspešne upravený.");
            } else {
                gui.showError("Úprava PCR testu zlyhala.");
            }
        } catch (Exception e) {
            gui.showError("Chyba pri úprave PCR testu: " + e.getMessage());
        }
    }

    public void showPersons() {
        try {
            gui.displayResult(database.displayPersons());
        } catch (Exception e) {
            gui.showError("Chyba pri výpise pacientov: " + e.getMessage());
        }
    }

    public void showTests() {
        try {
            gui.displayResult(database.displayTests());
        } catch (Exception e) {
            gui.showError("Chyba pri výpise PCR testov: " + e.getMessage());
        }
    }

    private boolean ensureEmptyOrCreateDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            return dir.mkdirs();
        }
        if (!dir.isDirectory()) {
            gui.showError("Zadaná cesta nie je priečinok.");
            return false;
        }
        String[] list = dir.list();
        if (list == null) {
            gui.showError("Priečinok sa nedá čítať.");
            return false;
        }
        if (list.length > 0) {
            gui.showError("Priečinok musí byť prázdny. Vyber prázdny priečinok alebo vytvor nový.");
            return false;
        }
        return true;
    }

    public boolean createNewDatabase(String path, int initialM, int personBlock, int testBlock, int personOverflowBlock, int testOverflowBlock) {
        try {
            if (this.database != null) this.database.close();

            if (!ensureEmptyOrCreateDirectory(path)) return false;

            this.database = new Database(path, initialM, personBlock, testBlock, personOverflowBlock, testOverflowBlock);

            this.database.writeConfig(initialM, personBlock, testBlock, personOverflowBlock, testOverflowBlock);

            this.generator = new DatabaseGenerator(database);
            gui.showMessage("Nová databáza vytvorená v: " + path);
            return true;
        } catch (IOException e) {
            gui.showError("Chyba pri tvorbe databázy: " + e.getMessage());
            return false;
        }
    }

    public boolean openExistingDatabase(String path) {
        try {
            if (this.database != null) this.database.close();
            this.database = new Database(path);
            this.generator = new DatabaseGenerator(database);
            gui.showMessage("Databáza otvorená z: " + path);
            return true;
        } catch (IOException e) {
            gui.showError("Chyba pri otvorení databázy: " + e.getMessage());
            return false;
        }
    }

    /**
     * Close database
     */
    public void closeDatabase() {
        try {
            if (database != null) {
                database.close();
                gui.showMessage("Databáza bola úspešne uložená!");
            }
        } catch (IOException e) {
            gui.showError("Chyba pri zatváraní databázy: " + e.getMessage());
        }
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.gui.show();
        });
    }
}