package testers;

import data.Person;
import heap.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class GUIHeapFileTester {
    private static HeapFile<Person> heap;
    private static JTextArea area;
    private static HeapFileTester heapFileTester;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                initializeApplication();
                createAndShowGUI();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Chyba pri inicializácii databázy: " + e.getMessage(),
                        "Chyba", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(null,
                        "Chyba konfigurácie: " + e.getMessage() +
                                "\n\nZáznamy nemôžu byť uložené v heap file.\n" +
                                "Skontrolujte veľkosť clusteru a záznamu.",
                        "Chyba konfigurácie", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Initializes heap file and operation generator instances
     */
    private static void initializeApplication() throws IOException {
        try {
            heap = new HeapFile<>("pacienti.dat", 256, new Person());
            heapFileTester = new HeapFileTester(heap);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Cluster size") && e.getMessage().contains("menší ako veľkosť záznamu")) {
                throw new IllegalArgumentException(e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Creates and displays the main application GUI
     */
    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Databáza pacientov - Heap File");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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

        JLabel titleLabel = new JLabel("DATABÁZA PACIENTOV");
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

        JButton showBlocksBtn = new JButton("Zobraziť databázu");
        showBlocksBtn.addActionListener(e -> displayBlockDetails());

        JButton showStructureBtn = new JButton("Zobraziť detaily o databáze");
        showStructureBtn.addActionListener(e -> displayFileStructure());

        panel.add(showStructureBtn);
        panel.add(showBlocksBtn);

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
                    String result = heapFileTester.runRandomOperations(operations, GUIHeapFileTester::updateProgress);
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
                    String result = heapFileTester.runInsertOnly(records, GUIHeapFileTester::updateProgress);
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
                "Naozaj chcete vyčistiť celú databázu?",
                "Potvrdenie vymazania", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                String result = heapFileTester.clearDatabase();
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
            sb.append("DETAILY O DATABÁZE\n\n");

            java.io.File dataFile = new java.io.File("pacienti.dat");
            Person person =  new Person();

            sb.append("• HLAVNÝ SÚBOR DÁT:\n");
            sb.append("\t- pacienti.dat (Heap File)\n");
            sb.append("\t- Veľkosť: ").append(dataFile.exists() ? dataFile.length() : 0).append(" bytes\n");
            sb.append("\t- Blokov: ").append(heap.getBlockCount()).append("\n");
            sb.append("\t- Cluster size: ").append(heap.getClusterSize()).append(" bytes\n\n");

            sb.append("• ŠTRUKTÚRA BLOKOV:\n");
            sb.append("\t- Každý blok: ").append(heap.getClusterSize()).append(" bytes\n");
            sb.append("\t- Záznamy: ").append(heap.getRecordsPerBlock()).append(" × ").append(person.getSize()).append(" bytes\n");
            sb.append("\t- Padding: ").append(heap.getClusterSize() - (heap.getRecordsPerBlock() * person.getSize())).append(" bytes\n\n");

            area.setText(sb.toString());

        } catch (IOException ex) {
            showError("Chyba pri čítaní štruktúry: " + ex.getMessage());
        }
    }

    /**
     * Displays information of all blocks and their contents
     */
    private static void displayBlockDetails() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("DATABÁZA PACIENTOV\n\n");

            int blockCount = heap.getBlockCount();
            int totalRecords = 0;

            for (int i = 0; i < blockCount; i++) {
                Block<Person> block = heap.readBlock(i);
                List<Person> records = block.getRecords();

                sb.append("── BLOK ").append(i).append(" ──────────────────────────────────────────────\n");
                sb.append(" Adresa na disku: ").append(block.getAddress()).append(" bytes\n");
                sb.append(" Stav: ");
                if (block.isEmpty()) sb.append("PRÁZDNY");
                else if (!block.hasSpace()) sb.append("PLNÝ");
                else sb.append("ČIASTOČNE VOĽNÝ");
                sb.append("  Platné záznamy: ").append(block.getValidCount()).append("/").append(block.getRecordsPerBlock()).append("\n");

                if (records.isEmpty()) {
                    sb.append(" Žiadne platné záznamy\n");
                } else {
                    for (int j = 0; j < records.size(); j++) {
                        sb.append(" ").append(j + 1).append(". ").append(records.get(j)).append("\n");
                    }
                }

                sb.append("──────────────────────────────────────────────────────────\n\n");
                totalRecords += block.getValidCount();
            }

            sb.append("CELKOVO: ").append(totalRecords).append(" záznamov v ").append(blockCount).append(" blokoch");

            area.setText(sb.toString());

        } catch (IOException ex) {
            showError("Chyba pri čítaní blokov: " + ex.getMessage());
        }
    }

    /**
     * Inserts a randomly generated test record into the database
     */
    private static void insertTestRecord() {
        try {
            Person newPerson = heapFileTester.generateRandomPatient();
            int blockIndex = heap.insert(newPerson);

            area.setText("PRIDANÝ NOVÝ ZÁZNAM:\n\n" +
                    "Meno: " + newPerson.getName() + " " + newPerson.getSurname() + "\n" +
                    "Dátum narodenia: " + newPerson.getDateOfBirth() + "\n" +
                    "ID: " + newPerson.getId() + "\n" +
                    "Uloženie do bloku: " + blockIndex + "\n\n" +
                    "Záznam bol úspešne pridaný do databázy.");


        } catch (IOException ex) {
            showError("Chyba pri vkladaní záznamu: " + ex.getMessage());
        }
    }

    /**
     * Searches for a record by ID across all blocks
     */
    private static void searchRecord() {
        String id = JOptionPane.showInputDialog("Zadajte ID pacienta pre vyhľadanie:");
        if (id != null && !id.trim().isEmpty()) {
            try {
                Person pattern = new Person("", "", LocalDate.now(), id.trim());
                boolean found = false;
                int blockCount = heap.getBlockCount();

                for (int block = 0; block < blockCount; block++) {
                    Person result = heap.get(block, pattern);
                    if (result != null) {
                        area.setText("ZÁZNAM NAJDENÝ:\n\n" +
                                "Meno: " + result.getName() + " " + result.getSurname() + "\n" +
                                "Dátum narodenia: " + result.getDateOfBirth() + "\n" +
                                "ID: " + result.getId() + "\n" +
                                "Nájdený v bloku: " + block + "\n\n" +
                                "Vyhľadávanie prebehlo úspešne.");
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    area.setText("ZÁZNAM S ID '" + id + "' NEBOL NAJDENÝ\n");
                }

            } catch (IOException ex) {
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
                Person pattern = new Person("", "", LocalDate.now(), id.trim());
                boolean deleted = false;
                int blockCount = heap.getBlockCount();

                for (int block = 0; block < blockCount; block++) {
                    if (heap.delete(block, pattern)) {
                        area.setText("ZÁZNAM S ID '" + id + "' BOL ÚSPEŠNE ZMAZANÝ\n\n" +
                                "Záznam bol odstránený z bloku " + block + ".\n");
                        deleted = true;
                        break;
                    }
                }

                if (!deleted) {
                    area.setText("ZÁZNAM S ID '" + id + "' NEBOL NAJDENÝ\n\n" +
                            "Zmazanie nebolo vykonané.");
                }

            } catch (IOException ex) {
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