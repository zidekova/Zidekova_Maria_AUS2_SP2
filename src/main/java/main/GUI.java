package main;

import data.Person;
import data.PCRTest;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GUI {
    private final App app;
    private JFrame mainFrame;
    private JTextArea resultArea;

    private Person currentPersonForEdit;
    private PCRTest currentTestForEdit;

    private JTextField patientIdField;
    private JTextField nameField;
    private JTextField surnameField;
    private JTextField birthDateField;
    private JTextField dateTimeField;
    private JTextField resultField;
    private JTextField valueField;
    private JTextField noteField;
    private JTextField numPersonsField;
    private JTextField numTestsField;

    public GUI(App app) {
        this.app = app;
        createGUI();
    }

    private void createGUI() {
        mainFrame = new JFrame("Databáza PCR testov");
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
                    app.closeDatabase();
                } catch (Exception ex) {
                    showError("Chyba pri zatváraní databázy: " + ex.getMessage());
                } finally {
                    mainFrame.dispose();
                    System.exit(0);
                }
            }
        });

        mainFrame.setSize(900, 700);
        mainFrame.setLayout(new BorderLayout());

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Správa DBS", createDbsManagerPanel());
        tabbedPane.addTab("1. Vloženie PCR testu", createInsertTestPanel());
        tabbedPane.addTab("2. Vyhľadanie pacienta", createFindPersonPanel());
        tabbedPane.addTab("3. Vyhľadanie PCR testu", createFindTestPanel());
        tabbedPane.addTab("4. Vloženie pacienta", createInsertPersonPanel());
        tabbedPane.addTab("5. Zmazanie PCR testu", createDeleteTestPanel());
        tabbedPane.addTab("6. Zmazanie pacienta", createDeletePersonPanel());
        tabbedPane.addTab("7. Úprava pacienta", createEditPersonPanel());
        tabbedPane.addTab("8. Úprava PCR testu", createEditTestPanel());
        tabbedPane.addTab("Naplnenie databázy", createFillDatabasePanel());
        tabbedPane.addTab("Štatistiky", createStatisticsPanel());

        tabbedPane.setMinimumSize(new Dimension(0, 120));
        scrollPane.setMinimumSize(new Dimension(0, 120));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, scrollPane);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerSize(8);
        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.0);

        SwingUtilities.invokeLater(() -> {
            int topPref = tabbedPane.getPreferredSize().height;
            splitPane.setDividerLocation(topPref);
        });

        mainFrame.add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createInsertTestPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Dátum a čas (yyyy-MM-dd HH:mm:ss):"), gbc);
        gbc.gridx = 1;
        dateTimeField = new JTextField(20);
        dateTimeField.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        panel.add(dateTimeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("ID pacienta:"), gbc);
        gbc.gridx = 1;
        patientIdField = new JTextField(20);
        panel.add(patientIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Výsledok (true/false):"), gbc);
        gbc.gridx = 1;
        resultField = new JTextField(20);
        resultField.setText("false");
        panel.add(resultField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Hodnota:"), gbc);
        gbc.gridx = 1;
        valueField = new JTextField(20);
        valueField.setText("0.0");
        panel.add(valueField, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(new JLabel("Poznámka:"), gbc);
        gbc.gridx = 1;
        noteField = new JTextField(20);
        noteField.setText("PCR test");
        panel.add(noteField, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton insertButton = new JButton("Vložiť PCR test");
        insertButton.addActionListener(e -> {
            app.insertPCRTest(
                    dateTimeField.getText(),
                    patientIdField.getText(),
                    resultField.getText(),
                    valueField.getText(),
                    noteField.getText()
            );
        });
        panel.add(insertButton, gbc);

        return panel;
    }

    private JPanel createFindPersonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("ID pacienta:"), gbc);
        gbc.gridx = 1;
        JTextField findPatientIdField = new JTextField(20);
        panel.add(findPatientIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton findButton = new JButton("Vyhľadať pacienta");
        findButton.addActionListener(e -> {
            app.findPersonWithTests(findPatientIdField.getText());
        });
        panel.add(findButton, gbc);

        return panel;
    }

    private JPanel createFindTestPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Kód testu:"), gbc);
        gbc.gridx = 1;
        JTextField findTestCodeField = new JTextField(20);
        panel.add(findTestCodeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton findButton = new JButton("Vyhľadať PCR test");
        findButton.addActionListener(e -> {
            app.findTestWithPatient(findTestCodeField.getText());
        });
        panel.add(findButton, gbc);

        return panel;
    }

    private JPanel createInsertPersonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Meno:"), gbc);
        gbc.gridx = 1;
        nameField = new JTextField(20);
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Priezvisko:"), gbc);
        gbc.gridx = 1;
        surnameField = new JTextField(20);
        panel.add(surnameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Dátum narodenia (yyyy-MM-dd):"), gbc);
        gbc.gridx = 1;
        birthDateField = new JTextField(20);
        birthDateField.setText("2003-01-01");
        panel.add(birthDateField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton insertButton = new JButton("Vložiť pacienta");
        insertButton.addActionListener(e -> {
            app.insertPerson(
                    nameField.getText(),
                    surnameField.getText(),
                    birthDateField.getText()
            );
        });
        panel.add(insertButton, gbc);

        return panel;
    }

    private JPanel createDeleteTestPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Kód PCR testu na zmazanie:"), gbc);
        gbc.gridx = 1;
        JTextField deleteTestCodeField = new JTextField(20);
        panel.add(deleteTestCodeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JButton deleteBtn = new JButton("Zmazať PCR test");
        deleteBtn.addActionListener(e -> app.deletePCRTest(deleteTestCodeField.getText()));
        panel.add(deleteBtn, gbc);

        return panel;
    }

    private JPanel createDeletePersonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("ID pacienta na zmazanie:"), gbc);
        gbc.gridx = 1;
        JTextField deletePatientIdField = new JTextField(20);
        panel.add(deletePatientIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JButton deleteBtn = new JButton("Zmazať pacienta (vrátane testov)");
        deleteBtn.addActionListener(e -> app.deletePersonWithTests(deletePatientIdField.getText()));
        panel.add(deleteBtn, gbc);

        return panel;
    }

    private JPanel createEditPersonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("ID pacienta na úpravu:"), gbc);
        gbc.gridx = 1;
        JTextField editPatientIdField = new JTextField(20);
        panel.add(editPatientIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton findButton = new JButton("Vyhľadať pacienta na úpravu");
        findButton.addActionListener(e -> {
            app.findPersonForEdit(editPatientIdField.getText());
        });
        panel.add(findButton, gbc);

        return panel;
    }

    private JPanel createEditTestPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Kód PCR testu na úpravu:"), gbc);
        gbc.gridx = 1;
        JTextField editTestCodeField = new JTextField(20);
        panel.add(editTestCodeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton findButton = new JButton("Vyhľadať PCR test na úpravu");
        findButton.addActionListener(e -> {
            app.findTestForEdit(editTestCodeField.getText());
        });
        panel.add(findButton, gbc);

        return panel;
    }

    private JPanel createFillDatabasePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Počet pacientov:"), gbc);
        gbc.gridx = 1;
        numPersonsField = new JTextField(20);
        numPersonsField.setText("100");
        panel.add(numPersonsField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Počet testov:"), gbc);
        gbc.gridx = 1;
        numTestsField = new JTextField(20);
        numTestsField.setText("300");
        panel.add(numTestsField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton fillButton = new JButton("Naplniť databázu");
        fillButton.addActionListener(e -> {
            app.fillDatabase(numPersonsField.getText(), numTestsField.getText());
        });
        panel.add(fillButton, gbc);

        return panel;
    }

    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        JButton dumpPersonsBtn = new JButton("Vypísať všetkých pacientov");
        dumpPersonsBtn.addActionListener(e -> app.showPersons());
        panel.add(dumpPersonsBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JButton dumpTestsBtn = new JButton("Vypísať všetky PCR testy");
        dumpTestsBtn.addActionListener(e -> app.showTests());
        panel.add(dumpTestsBtn, gbc);

        return panel;
    }



    private JPanel createDbsManagerPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2;
        panel.add(new JLabel("Vytvoriť novú DBS (priečinok musí byť prázdny):"), gbc);

        gbc.gridwidth=1;
        gbc.gridx=0; gbc.gridy=1; panel.add(new JLabel("Počiatočné M:"), gbc);
        gbc.gridx=1; JTextField mField = new JTextField("4", 20); panel.add(mField, gbc);

        gbc.gridx=0; gbc.gridy=2; panel.add(new JLabel("Veľkosť bloku (Persons):"), gbc);
        gbc.gridx=1; JTextField pBlockField = new JTextField("1024", 20); panel.add(pBlockField, gbc);

        gbc.gridx=0; gbc.gridy=3; panel.add(new JLabel("Veľkosť bloku (Tests):"), gbc);
        gbc.gridx=1; JTextField tBlockField = new JTextField("2048", 20); panel.add(tBlockField, gbc);

        gbc.gridx=0; gbc.gridy=4; panel.add(new JLabel("Veľkosť bloku (Overflow – Persons):"), gbc);
        gbc.gridx=1; JTextField oBlockPersonsField = new JTextField("512", 20); panel.add(oBlockPersonsField, gbc);

        gbc.gridx=0; gbc.gridy=5; panel.add(new JLabel("Veľkosť bloku (Overflow – Tests):"), gbc);
        gbc.gridx=1; JTextField oBlockTestsField = new JTextField("512", 20); panel.add(oBlockTestsField, gbc);

        gbc.gridx=0; gbc.gridy=6;
        JButton pickEmptyDirBtn = new JButton("Vybrať prázdny priečinok");
        JTextField createPathField = new JTextField(20);
        createPathField.setEditable(false);
        pickEmptyDirBtn.addActionListener(e -> {
            String chosen = chooseDirectory(panel);
            if (chosen != null) createPathField.setText(chosen);
        });
        panel.add(pickEmptyDirBtn, gbc);
        gbc.gridx=1; panel.add(createPathField, gbc);

        gbc.gridx=0; gbc.gridy=7; gbc.gridwidth=2; gbc.fill = GridBagConstraints.NONE;
        JButton createBtn = new JButton("Vytvoriť DBS");
        createBtn.addActionListener(e -> {
            try {
                String path = createPathField.getText();
                if (path == null || path.isBlank()) {
                    showError("Najprv vyber prázdny priečinok.");
                    return;
                }
                app.createNewDatabase(
                        path,
                        Integer.parseInt(mField.getText()),
                        Integer.parseInt(pBlockField.getText()),
                        Integer.parseInt(tBlockField.getText()),
                        Integer.parseInt(oBlockPersonsField.getText()),
                        Integer.parseInt(oBlockTestsField.getText())

                );
            } catch (Exception ex) {
                showError("Zlé vstupy: " + ex.getMessage());
            }
        });
        panel.add(createBtn, gbc);

        gbc.gridx=0; gbc.gridy=8; gbc.gridwidth=2; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("Otvoriť existujúcu DBS (vyber len priečinok):"), gbc);

        gbc.gridwidth=1;
        gbc.gridx=0; gbc.gridy=9;
        JButton pickOpenDirBtn = new JButton("Vybrať priečinok");
        JTextField openPathField = new JTextField(20);
        openPathField.setEditable(false);
        pickOpenDirBtn.addActionListener(e -> {
            String chosen = chooseDirectory(panel);
            if (chosen != null) openPathField.setText(chosen);
        });
        panel.add(pickOpenDirBtn, gbc);
        gbc.gridx=1; panel.add(openPathField, gbc);

        gbc.gridx=0; gbc.gridy=10; gbc.gridwidth=2; gbc.fill = GridBagConstraints.NONE;
        JButton openBtn = new JButton("Otvoriť DBS");
        openBtn.addActionListener(e -> {
            try {
                String path = openPathField.getText();
                if (path == null || path.isBlank()) {
                    showError("Najprv vyber priečinok s DBS.");
                    return;
                }
                app.openExistingDatabase(path);
            } catch (Exception ex) {
                showError("Chyba: " + ex.getMessage());
            }
        });
        panel.add(openBtn, gbc);

        gbc.gridx=0; gbc.gridy=11; gbc.gridwidth=2;
        JButton closeBtn = new JButton("Zavrieť DBS");
        closeBtn.addActionListener(e -> app.closeDatabase());
        panel.add(closeBtn, gbc);

        return panel;
    }

    private String chooseDirectory(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Vyber priečinok DBS");
        int res = chooser.showOpenDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            return dir.getAbsolutePath();
        }
        return null;
    }

    public void showEditPersonForm() {
        if (currentPersonForEdit == null) return;

        JDialog editDialog = new JDialog(mainFrame, "Úprava pacienta", true);
        editDialog.setSize(400, 300);
        editDialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        editDialog.add(new JLabel("Meno:"), gbc);
        gbc.gridx = 1;
        JTextField editNameField = new JTextField(20);
        editNameField.setText(currentPersonForEdit.getName());
        editDialog.add(editNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        editDialog.add(new JLabel("Priezvisko:"), gbc);
        gbc.gridx = 1;
        JTextField editSurnameField = new JTextField(20);
        editSurnameField.setText(currentPersonForEdit.getSurname());
        editDialog.add(editSurnameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        editDialog.add(new JLabel("Dátum narodenia:"), gbc);
        gbc.gridx = 1;
        JTextField editBirthDateField = new JTextField(20);
        editBirthDateField.setText(currentPersonForEdit.getDateOfBirth().toString());
        editDialog.add(editBirthDateField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton updateButton = new JButton("Upraviť pacienta");
        updateButton.addActionListener(e -> {
            try {
                Person updated = new Person(
                        editNameField.getText(),
                        editSurnameField.getText(),
                        LocalDate.parse(editBirthDateField.getText()),
                        currentPersonForEdit.getId()
                );

                for (int testCode : currentPersonForEdit.getTestCodes()) {
                    updated.addTestCode(testCode);
                }

                app.updatePerson(updated);
                editDialog.dispose();
            } catch (Exception ex) {
                showError("Chyba pri úprave pacienta: " + ex.getMessage());
            }
        });
        editDialog.add(updateButton, gbc);

        gbc.gridy = 4;
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> editDialog.dispose());
        editDialog.add(cancelButton, gbc);

        editDialog.setLocationRelativeTo(mainFrame);
        editDialog.setVisible(true);
    }

    public void showEditTestForm() {
        if (currentTestForEdit == null) return;

        JDialog editDialog = new JDialog(mainFrame, "Úprava PCR testu", true);
        editDialog.setSize(400, 350);
        editDialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        editDialog.add(new JLabel("Dátum a čas"), gbc);
        gbc.gridx = 1;
        JTextField editDateTimeField = new JTextField(20);
        editDateTimeField.setText(currentTestForEdit.getDateTime().toString());
        editDialog.add(editDateTimeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        editDialog.add(new JLabel("ID pacienta:"), gbc);
        gbc.gridx = 1;
        JTextField editPatientIdField = new JTextField(20);
        editPatientIdField.setText(currentTestForEdit.getPatientId());
        editDialog.add(editPatientIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        editDialog.add(new JLabel("Výsledok:"), gbc);
        gbc.gridx = 1;
        JTextField editResultField = new JTextField(20);
        editResultField.setText(String.valueOf(currentTestForEdit.getResult()));
        editDialog.add(editResultField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        editDialog.add(new JLabel("Hodnota:"), gbc);
        gbc.gridx = 1;
        JTextField editValueField = new JTextField(20);
        editValueField.setText(String.valueOf(currentTestForEdit.getValue()));
        editDialog.add(editValueField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        editDialog.add(new JLabel("Poznámka:"), gbc);
        gbc.gridx = 1;
        JTextField editNoteField = new JTextField(20);
        editNoteField.setText(currentTestForEdit.getNote());
        editDialog.add(editNoteField, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        JButton updateButton = new JButton("Upraviť PCR test");
        updateButton.addActionListener(e -> {
            try {
                PCRTest updated = new PCRTest(
                        LocalDateTime.parse(editDateTimeField.getText().replace(" ", "T")),
                        editPatientIdField.getText(),
                        currentTestForEdit.getTestCode(),
                        Boolean.parseBoolean(editResultField.getText()),
                        Double.parseDouble(editValueField.getText()),
                        editNoteField.getText()
                );

                app.updatePCRTest(updated);
                editDialog.dispose();
            } catch (Exception ex) {
                showError("Chyba pri úprave testu: " + ex.getMessage());
            }
        });
        editDialog.add(updateButton, gbc);

        gbc.gridy = 5;
        JButton cancelButton = new JButton("Zrušiť");
        cancelButton.addActionListener(e -> editDialog.dispose());
        editDialog.add(cancelButton, gbc);

        editDialog.setLocationRelativeTo(mainFrame);
        editDialog.setVisible(true);
    }

    public void show() {
        mainFrame.setVisible(true);
    }

    public void displayResult(String text) {
        resultArea.setText(text);
    }

    public void showMessage(String message) {
        JOptionPane.showMessageDialog(mainFrame, message);
    }

    public void showError(String error) {
        JOptionPane.showMessageDialog(mainFrame, error, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void setCurrentPersonForEdit(Person person) {
        this.currentPersonForEdit = person;
    }

    public void setCurrentTestForEdit(PCRTest test) {
        this.currentTestForEdit = test;
    }
}