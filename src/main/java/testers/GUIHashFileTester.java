package testers;

import data.Person;
import hash.LinearHashing;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class GUIHashFileTester {
    private static LinearHashing<Person> hashFile;
    private static JTextArea area;
    private static HashFileTester hashFileTester;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                initializeApplication();
                createAndShowGUI();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Chyba pri inicializácii hash databázy: " + e.getMessage(),
                        "Chyba", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Initializes hash file and operation generator instances
     */
    private static void initializeApplication() throws IOException {
        hashFile = new LinearHashing<>("pacienti_hash.dat", 300, 200, new Person(), 2);
        hashFileTester = new HashFileTester(hashFile);
    }

    /**
     * Creates and displays the main application GUI
     */
    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Databáza pacientov - Lineárne Hešovanie");

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                try {
                    if (hashFile != null) {
                        hashFile.close();
                    }
                    frame.dispose();
                    System.exit(0);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Chyba pri ukladaní dát: " + ex.getMessage(),
                            "Chyba", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        frame.setSize(1000, 700);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        area = new JTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 11));
        area.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel buttonPanel = createButtonPanel();
        JPanel recordPanel = createRecordPanel();
        JPanel testPanel = createTestPanel();

        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        recordPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        testPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        southPanel.add(recordPanel);
        southPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        southPanel.add(buttonPanel);
        southPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        southPanel.add(testPanel);

        JPanel bottomWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomWrapper.add(southPanel);

        mainPanel.add(bottomWrapper, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    /**
     * Creates the header panel with application title
     */
    private static JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerPanel.setBackground(new Color(240, 240, 240));

        JLabel titleLabel = new JLabel("DATABÁZA PACIENTOV - LINEÁRNE HEŠOVANIE");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        return headerPanel;
    }

    /**
     * Creates panel for file-level operations
     */
    private static JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Operácie so súborom"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JButton showStatsBtn = new JButton("Zobraziť štatistiky");
        showStatsBtn.addActionListener(e -> displayStatistics());

        JButton showStructureBtn = new JButton("Zobraziť detaily o databáze");
        showStructureBtn.addActionListener(e -> displayFileStructure());

        JButton showAllBlocksBtn = new JButton("Zobraziť celý hash súbor");
        showAllBlocksBtn.addActionListener(e -> displayAllBlocks());

        panel.add(showStructureBtn);
        panel.add(showStatsBtn);
        panel.add(showAllBlocksBtn);

        return panel;
    }

    /**
     * Creates panel for individual record operations
     */
    private static JPanel createRecordPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Operácie so záznamami"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JButton insertBtn = new JButton("Pridať náhodný záznam");
        insertBtn.addActionListener(e -> insertTestRecord());

        JButton searchBtn = new JButton("Vyhľadať záznam");
        searchBtn.addActionListener(e -> searchRecord());

        JButton deleteBtn = new JButton("Zmazať záznam");
        deleteBtn.addActionListener(e -> deleteRecord());

        panel.add(insertBtn);
        panel.add(searchBtn);
        panel.add(deleteBtn);

        return panel;
    }

    /**
     * Creates panel for testing operations
     */
    private static JPanel createTestPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Testovacie operácie"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JButton randomOpsBtn = new JButton("Náhodné operácie");
        randomOpsBtn.addActionListener(e -> runRandomOperations());

        JButton insertOnlyBtn = new JButton("Hromadné vkladanie");
        insertOnlyBtn.addActionListener(e -> runInsertOnly());

        JButton clearBtn = new JButton("Vyčistiť databázu");
        clearBtn.addActionListener(e -> clearDatabase());

        panel.add(randomOpsBtn);
        panel.add(insertOnlyBtn);
        panel.add(clearBtn);

        return panel;
    }

    /**
     * Executes a series of random operations
     */
    private static void runRandomOperations() {
        String input = JOptionPane.showInputDialog("Zadajte počet operácií:", "1000");
        if (input == null || input.trim().isEmpty()) return;

        try {
            int operations = Integer.parseInt(input.trim());
            if (operations <= 0) {
                showError("Počet operácií musí byť kladné číslo");
                return;
            }

            new Thread(() -> {
                try {
                    String result = hashFileTester.runRandomOperations(operations, GUIHashFileTester::updateProgress);
                    SwingUtilities.invokeLater(() -> {
                        area.setText(result);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            showError("Chyba pri vykonávaní operácií: " + ex.getMessage()));
                }
            }).start();

        } catch (NumberFormatException ex) {
            showError("Neplatný formát čísla: " + input);
        }
    }

    /**
     * Performs insert operations for performance testing
     */
    private static void runInsertOnly() {
        String input = JOptionPane.showInputDialog("Zadajte počet záznamov pre vloženie:", "100");
        if (input == null || input.trim().isEmpty()) return;

        try {
            int records = Integer.parseInt(input.trim());
            if (records <= 0) {
                showError("Počet záznamov musí byť kladné číslo");
                return;
            }

            new Thread(() -> {
                try {
                    String result = hashFileTester.runInsertOnly(records, GUIHashFileTester::updateProgress);
                    SwingUtilities.invokeLater(() -> {
                        area.setText(result);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            showError("Chyba pri vkladaní: " + ex.getMessage()));
                }
            }).start();

        } catch (NumberFormatException ex) {
            showError("Neplatný formát čísla: " + input);
        }
    }

    /**
     * Callback for updating progress during long-running operations
     */
    private static void updateProgress(String progress) {
        SwingUtilities.invokeLater(() -> {
            area.setText(progress);
        });
    }

    /**
     * Clears the whole database
     */
    private static void clearDatabase() {
        int confirm = JOptionPane.showConfirmDialog(null,
                "Naozaj chcete vyčistiť celú hash databázu?",
                "Potvrdenie vymazania", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                String result = hashFileTester.clearDatabase();
                initializeApplication();
                area.setText(result);

            } catch (Exception ex) {
                showError("Chyba pri čistení databázy: " + ex.getMessage());
            }
        }
    }

    /**
     * Displays information about the database file structure
     */
    private static void displayFileStructure() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("DETAILY O HASH DATABÁZE\n\n");

            java.io.File dataFile = new java.io.File("pacienti_hash.dat");
            java.io.File overflowFile = new java.io.File("pacienti_hash.dat.overflow");
            java.io.File metaFile = new java.io.File("pacienti_hash.dat.meta");

            Person person = new Person();

            sb.append("• HLAVNÝ SÚBOR DÁT:\n");
            sb.append("\t- pacienti_hash.dat (Linear Hashing)\n");
            sb.append("\t- Veľkosť: ").append(dataFile.exists() ? dataFile.length() : 0).append(" bytes\n\n");

            sb.append("• OVERFLOW SÚBOR:\n");
            sb.append("\t- pacienti_hash.dat.overflow\n");
            sb.append("\t- Veľkosť: ").append(overflowFile.exists() ? overflowFile.length() : 0).append(" bytes\n\n");

            sb.append("• METADÁTA:\n");
            sb.append("\t- pacienti_hash.dat.meta\n");
            sb.append("\t- Veľkosť: ").append(metaFile.exists() ? metaFile.length() : 0).append(" bytes\n\n");

            sb.append("• ŠTRUKTÚRA BLOKOV:\n");
            sb.append("\t- Primárny blok: ").append(hashFile.getClusterSize()).append(" bytes\n");
            sb.append("\t- Overflow blok: ").append(hashFile.getOverflowFile().getClusterSize()).append(" bytes\n");
            sb.append("\t- Veľkosť záznamu: ").append(person.getSize()).append(" bytes\n");
            sb.append("\t- Záznamov v primárnom bloku: ").append(hashFile.getClusterSize() / person.getSize()).append("\n");
            sb.append("\t- Záznamov v overflow bloku: ").append(hashFile.getOverflowFile().getClusterSize() / person.getSize()).append("\n");

            area.setText(sb.toString());

        } catch (Exception ex) {
            showError("Chyba pri čítaní štruktúry: " + ex.getMessage());
        }
    }

    /**
     * Displays hash file statistics
     */
    private static void displayStatistics() {
        try {
            String stats = hashFileTester.getStatistics();
            area.setText(stats);
        } catch (Exception ex) {
            showError("Chyba pri získavaní štatistík: " + ex.getMessage());
        }
    }

    /**
     * Displays all blocks (primary and overflow) of the hash file
     */
    private static void displayAllBlocks() {
        try {
            String allBlocks = hashFileTester.displayAllBlocks();
            area.setText(allBlocks);
        } catch (Exception ex) {
            showError("Chyba pri zobrazovaní hash súboru: " + ex.getMessage());
        }
    }

    /**
     * Inserts a randomly generated test record into the database
     */
    private static void insertTestRecord() {
        try {
            Person newPerson = hashFileTester.generateRandomPatient();
            hashFile.insert(newPerson, newPerson.getKey());

            area.setText("PRIDANÝ NOVÝ ZÁZNAM:\n\n" +
                    "Meno: " + newPerson.getName() + " " + newPerson.getSurname() + "\n" +
                    "Dátum narodenia: " + newPerson.getDateOfBirth() + "\n" +
                    "ID: " + newPerson.getId() + "\n\n" +
                    "Záznam bol úspešne pridaný do hash databázy.");

        } catch (Exception ex) {
            showError("Chyba pri vkladaní záznamu: " + ex.getMessage());
        }
    }

    /**
     * Searches for a record by ID
     */
    private static void searchRecord() {
        String id = JOptionPane.showInputDialog("Zadajte ID pacienta pre vyhľadanie:");
        if (id != null && !id.trim().isEmpty()) {
            try {
                Person found = hashFile.get(id.trim());
                if (found != null) {
                    area.setText("ZÁZNAM NAJDENÝ:\n\n" +
                            "Meno: " + found.getName() + " " + found.getSurname() + "\n" +
                            "Dátum narodenia: " + found.getDateOfBirth() + "\n" +
                            "ID: " + found.getId() + "\n\n" +
                            "Vyhľadávanie prebehlo úspešne.");
                } else {
                    area.setText("ZÁZNAM S ID '" + id + "' NEBOL NAJDENÝ\n");
                }

            } catch (Exception ex) {
                showError("Chyba pri vyhľadávaní: " + ex.getMessage());
            }
        }
    }

    /**
     * Deletes a record by ID from the database
     */
    private static void deleteRecord() {
        String id = JOptionPane.showInputDialog("Zadajte ID pacienta pre zmazanie:");
        if (id != null && !id.trim().isEmpty()) {
            try {
                boolean deleted = hashFile.delete(id.trim());
                if (deleted) {
                    area.setText("ZÁZNAM S ID '" + id + "' BOL ÚSPEŠNE ZMAZANÝ\n\n" +
                            "Záznam bol odstránený z hash databázy.\n");
                } else {
                    area.setText("ZÁZNAM S ID '" + id + "' NEBOL NAJDENÝ\n\n" +
                            "Zmazanie nebolo vykonané.");
                }

            } catch (Exception ex) {
                showError("Chyba pri mazaní: " + ex.getMessage());
            }
        }
    }

    /**
     * Displays error message
     */
    private static void showError(String message) {
        area.setText("CHYBA: " + message);
        JOptionPane.showMessageDialog(null, message, "Chyba", JOptionPane.ERROR_MESSAGE);
    }
}