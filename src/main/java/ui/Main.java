package ui;

import model.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class Main {
    private static HeapFile<Person> heap;
    private static JTextArea area;
    private static JLabel statusLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                initializeApplication();
                createAndShowGUI();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Chyba pri inicializácii databázy: " + e.getMessage(),
                        "Chyba", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void initializeApplication() throws IOException {
        // Inicializácia HeapFile s optimálnou veľkosťou bloku
        heap = new HeapFile<>("pacienti.dat", 256, new Person());
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Databáza pacientov - Heap File System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);

        // Hlavný panel
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Status bar
        statusLabel = new JLabel("Databáza inicializovaná");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());

        // Text area pre výpis
        area = new JTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 11));
        area.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(area);

        // Panel s tlačidlami
        JPanel buttonPanel = createButtonPanel();

        // Panel pre operácie so záznamami
        JPanel recordPanel = createRecordPanel();

        // Layout
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(recordPanel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.CENTER);
        southPanel.add(statusLabel, BorderLayout.SOUTH);

        mainPanel.add(southPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        updateStatus("Aplikácia pripravená");
    }

    private static JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerPanel.setBackground(new Color(240, 240, 240));

        JLabel titleLabel = new JLabel("HEAP FILE DATABÁZA PACIENTOV");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel infoLabel = new JLabel("Mobilná aplikácia s efektívnym manažmentom pamäte - všetky dáta uložené na disku");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        headerPanel.add(infoLabel);

        return headerPanel;
    }

    private static JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Operácie so súborom"));

        JButton showAllBtn = new JButton("Zobraziť celý obsah databázy");
        showAllBtn.addActionListener(e -> displayAllContent());

        JButton showStructureBtn = new JButton("Zobraziť štruktúru súborov");
        showStructureBtn.addActionListener(e -> displayFileStructure());

        JButton showBlocksBtn = new JButton("Detailný prehľad blokov");
        showBlocksBtn.addActionListener(e -> displayBlockDetails());

        JButton showFreeSpaceBtn = new JButton("Manažment voľného miesta");
        showFreeSpaceBtn.addActionListener(e -> displayFreeSpaceManagement());

        JButton clearBtn = new JButton("Vyčistiť výpis");
        clearBtn.addActionListener(e -> area.setText(""));

        panel.add(showAllBtn);
        panel.add(showStructureBtn);
        panel.add(showBlocksBtn);
        panel.add(showFreeSpaceBtn);
        panel.add(clearBtn);

        return panel;
    }

    private static JPanel createRecordPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Operácie so záznamami"));

        JButton insertBtn = new JButton("Pridať testovací záznam");
        insertBtn.addActionListener(e -> insertTestRecord());

        JButton searchBtn = new JButton("Vyhľadať záznam");
        searchBtn.addActionListener(e -> searchRecord());

        JButton deleteBtn = new JButton("Zmazať záznam");
        deleteBtn.addActionListener(e -> deleteRecord());

        JButton performanceBtn = new JButton("Test výkonnosti");
        performanceBtn.addActionListener(e -> runPerformanceTest());

        panel.add(insertBtn);
        panel.add(searchBtn);
        panel.add(deleteBtn);
        panel.add(performanceBtn);

        return panel;
    }

    private static void displayAllContent() {
        try {
            String content = heap.displayAll();
            area.setText("=== CELKOVÝ OBSAH DATABÁZY ===\n\n" + content);
            updateStatus("Zobrazený celý obsah databázy");
        } catch (IOException ex) {
            showError("Chyba pri čítaní databázy: " + ex.getMessage());
        }
    }

    private static void displayFileStructure() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ŠTRUKTÚRA SÚBOROV APLIKÁCIE ===\n\n");

            java.io.File dataFile = new java.io.File("pacienti.dat");
            java.io.File metaFile = new java.io.File("pacienti.dat.meta");

            sb.append("1. HLAVNÝ SÚBOR DÁT:\n");
            sb.append("   - pacienti.dat (Heap File)\n");
            sb.append("   - Veľkosť: ").append(dataFile.exists() ? dataFile.length() : 0).append(" bytes\n");
            sb.append("   - Blokov: ").append(heap.getBlockCount()).append("\n");
            sb.append("   - Cluster size: 256 bytes\n\n");

            sb.append("2. METADATA SÚBOR:\n");
            sb.append("   - pacienti.dat.meta\n");
            sb.append("   - Veľkosť: ").append(metaFile.exists() ? metaFile.length() : 0).append(" bytes\n");
            sb.append("   - Uchováva: zoznamy voľných blokov, konfiguráciu\n\n");

            sb.append("3. INTERNÁ ŠTRUKTÚRA BLOKOV:\n");
            sb.append("   - Každý blok: 256 bytes\n");
            sb.append("   - Záznamy: ").append(heap.getRecordsPerBlock()).append(" × 49 bytes\n");
            sb.append("   - Padding: ").append(256 - (heap.getRecordsPerBlock() * 49)).append(" bytes\n\n");

            sb.append("4. MANAŽMENT PAMÄTE:\n");
            sb.append("   - Všetky dáta primárne na disku\n");
            sb.append("   - Minimálna RAM réžia\n");
            sb.append("   - Efektívne využitie priestoru bitmapou\n");

            area.setText(sb.toString());
            updateStatus("Zobrazená štruktúra súborov");

        } catch (IOException ex) {
            showError("Chyba pri čítaní štruktúry: " + ex.getMessage());
        }
    }

    private static void displayBlockDetails() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== DETAILNÝ PREHLAD BLOKOV ===\n\n");

            int blockCount = heap.getBlockCount();
            int totalRecords = 0;

            for (int i = 0; i < blockCount; i++) {
                Block<Person> block = heap.readBlock(i);
                List<Person> records = block.getRecords();

                sb.append("┌── BLOK ").append(i).append(" ──────────────────────────────────────────\n");
                sb.append("│ Adresa na disku: ").append(i * 256).append(" bytes\n");
                sb.append("│ Stav: ");
                if (block.isEmpty()) sb.append("PRÁZDNY");
                else if (!block.hasSpace()) sb.append("PLNÝ");
                else sb.append("ČIASTOČNE VOĽNÝ");
                sb.append(" | Platné záznamy: ").append(block.getValidCount()).append("/").append(block.getRecordsPerBlock()).append("\n");
                sb.append("│ Bitmapa: ").append(block.getBitmapVisualization()).append("\n");

                // Záznamy v bloku
                if (records.isEmpty()) {
                    sb.append("│ Žiadne platné záznamy\n");
                } else {
                    for (int j = 0; j < records.size(); j++) {
                        sb.append("│ ").append(j + 1).append(". ").append(records.get(j)).append("\n");
                    }
                }

                // Detailný stav slotov
                sb.append("│ Stav slotov:\n");
                for (int slot = 0; slot < block.getRecordsPerBlock(); slot++) {
                    sb.append("│   ").append(slot).append(": ").append(block.getSlotStatus(slot)).append("\n");
                }

                // Voľné miesto
                int freeSlots = block.getRecordsPerBlock() - block.getValidCount();
                if (freeSlots > 0) {
                    sb.append("│ Voľné sloty: ").append(freeSlots).append("\n");
                }

                sb.append("└──────────────────────────────────────────────────────────\n\n");
                totalRecords += block.getValidCount();
            }

            sb.append("ŠTATISTIKY: ").append(totalRecords).append(" záznamov v ").append(blockCount).append(" blokoch");

            area.setText(sb.toString());
            updateStatus("Zobrazené detaily " + blockCount + " blokov");

        } catch (IOException ex) {
            showError("Chyba pri čítaní blokov: " + ex.getMessage());
        }
    }

    private static void displayFreeSpaceManagement() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MANAŽMENT VOĽNÉHO MIESTA ===\n\n");

        sb.append("AKTUALIZOVANÉ ZOZNAMY VOĽNÝCH BLOKOV:\n");
        sb.append("─────────────────────────────────────\n");
        sb.append("Čiastočne voľné bloky: ").append(heap.getPartiallyFreeBlocks()).append("\n");
        sb.append("Úplne voľné bloky: ").append(heap.getEmptyBlocks()).append("\n\n");

        sb.append("STRATÉGIA VYBERANIA BLOKOV PRE VKLADANIE:\n");
        sb.append("─────────────────────────────────────────\n");
        sb.append("1. PRVÁ VOĽNOSŤ: Čiastočne voľné bloky (priorita)\n");
        sb.append("2. DRUHÁ VOĽNOSŤ: Úplne voľné bloky\n");
        sb.append("3. NOVÝ BLOK: Ak žiadne voľné bloky neexistujú\n\n");

        sb.append("VÝHODY BITMAPOVÉHO MANAŽMENTU:\n");
        sb.append("──────────────────────────────\n");
        sb.append("• Rýchle hľadanie voľných slotov\n");
        sb.append("• Okamžité opätovné použitie miesta\n");
        sb.append("• Žiadna fragmentácia dát\n");
        sb.append("• Minimálna réžia v RAM\n");

        area.setText(sb.toString());
        updateStatus("Zobrazený manažment voľného miesta");
    }

    private static void insertTestRecord() {
        try {
            // Generovanie náhodného pacienta
            String[] names = {"Anna", "Peter", "Marek", "Lucia", "Ján", "Eva", "Milan", "Zuzana"};
            String[] surnames = {"Novak", "Kral", "Hrasko", "Mala", "Urban", "Velky", "Stary", "Novy"};

            String name = names[(int)(Math.random() * names.length)];
            String surname = surnames[(int)(Math.random() * surnames.length)];
            LocalDate birthDate = LocalDate.of(1970 + (int)(Math.random() * 40),
                    1 + (int)(Math.random() * 12),
                    1 + (int)(Math.random() * 28));
            String id = "P" + (1000 + (int)(Math.random() * 9000));

            Person newPerson = new Person(name, surname, birthDate, id);
            int blockIndex = heap.insert(newPerson);

            area.setText("✓ PRIDANÝ NOVÝ ZÁZNAM:\n" +
                    "Meno: " + name + " " + surname + "\n" +
                    "Dátum narodenia: " + birthDate + "\n" +
                    "ID: " + id + "\n" +
                    "Uložený v bloku: " + blockIndex + "\n\n" +
                    "Záznam bol úspešne pridaný do databázy.");

            updateStatus("Pridaný nový záznam do bloku " + blockIndex);

        } catch (IOException ex) {
            showError("Chyba pri vkladaní záznamu: " + ex.getMessage());
        }
    }

    private static void searchRecord() {
        String id = JOptionPane.showInputDialog("Zadajte ID pacienta pre vyhľadanie (napr. P1234):");
        if (id != null && !id.trim().isEmpty()) {
            try {
                Person pattern = new Person("", "", LocalDate.now(), id.trim());
                boolean found = false;
                int blockCount = heap.getBlockCount();

                for (int block = 0; block < blockCount; block++) {
                    Person result = heap.get(block, pattern);
                    if (result != null) {
                        area.setText("✓ ZÁZNAM NAJDENÝ:\n" +
                                "Meno: " + result.getName() + " " + result.getSurname() + "\n" +
                                "Dátum narodenia: " + result.getDateOfBirth() + "\n" +
                                "ID: " + result.getId() + "\n" +
                                "Nájdený v bloku: " + block + "\n\n" +
                                "Vyhľadávanie prebehlo úspešne.");
                        found = true;
                        updateStatus("Záznam " + id + " nájdený v bloku " + block);
                        break;
                    }
                }

                if (!found) {
                    area.setText("✗ ZÁZNAM S ID '" + id + "' NEBOL NAJDENÝ\n\n" +
                            "Skontrolujte správnosť ID alebo pridajte nový záznam.");
                    updateStatus("Záznam " + id + " nebol nájdený");
                }

            } catch (IOException ex) {
                showError("Chyba pri vyhľadávaní: " + ex.getMessage());
            }
        }
    }

    private static void deleteRecord() {
        String id = JOptionPane.showInputDialog("Zadajte ID pacienta pre zmazanie (napr. P1234):");
        if (id != null && !id.trim().isEmpty()) {
            try {
                Person pattern = new Person("", "", LocalDate.now(), id.trim());
                boolean deleted = false;
                int blockCount = heap.getBlockCount();

                for (int block = 0; block < blockCount; block++) {
                    if (heap.delete(block, pattern)) {
                        area.setText("✓ ZÁZNAM S ID '" + id + "' BOL ÚSPEŠNE ZMAZANÝ\n\n" +
                                "Záznam bol odstránený z bloku " + block + ".\n" +
                                "Uvoľnené miesto bude automaticky použité pre nové záznamy.");
                        deleted = true;
                        updateStatus("Záznam " + id + " zmazaný z bloku " + block);
                        break;
                    }
                }

                if (!deleted) {
                    area.setText("✗ ZÁZNAM S ID '" + id + "' NEBOL NAJDENÝ\n\n" +
                            "Zmazanie nebolo vykonané.");
                    updateStatus("Záznam " + id + " nebol nájdený pre zmazanie");
                }

            } catch (IOException ex) {
                showError("Chyba pri mazaní: " + ex.getMessage());
            }
        }
    }

    private static void runPerformanceTest() {
        try {
            area.setText("Spúšťam test výkonnosti...\n\n");

            long startTime = System.currentTimeMillis();

            // Test vkladania
            area.append("1. TEST VKLADANIA:\n");
            for (int i = 0; i < 5; i++) {
                Person p = new Person("Test" + i, "Performance", LocalDate.now(), "T" + (1000 + i));
                int block = heap.insert(p);
                area.append("   ✓ Vložený záznam T" + (1000 + i) + " do bloku " + block + "\n");
            }

            // Test vyhľadávania
            area.append("\n2. TEST VYHĽADÁVANIA:\n");
            for (int i = 0; i < 3; i++) {
                Person pattern = new Person("", "", LocalDate.now(), "T" + (1000 + i));
                boolean found = false;
                int blockCount = heap.getBlockCount();
                for (int block = 0; block < blockCount; block++) {
                    if (heap.get(block, pattern) != null) {
                        area.append("   ✓ Záznam T" + (1000 + i) + " nájdený v bloku " + block + "\n");
                        found = true;
                        break;
                    }
                }
                if (!found) area.append("   ✗ Záznam T" + (1000 + i) + " nenájdený\n");
            }

            long endTime = System.currentTimeMillis();

            area.append("\n3. VÝSLEDKY:\n");
            area.append("   Čas vykonania: " + (endTime - startTime) + " ms\n");
            area.append("   Celkový počet blokov: " + heap.getBlockCount() + "\n");

            java.io.File dataFile = new java.io.File("pacienti.dat");
            area.append("   Veľkosť súboru: " + (dataFile.exists() ? dataFile.length() : 0) + " bytes\n");

            updateStatus("Test výkonnosti dokončený za " + (endTime - startTime) + " ms");

        } catch (IOException ex) {
            showError("Chyba pri teste výkonnosti: " + ex.getMessage());
        }
    }

    private static void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message + " | " + java.time.LocalTime.now().toString().substring(0, 8));
        }
    }

    private static void showError(String message) {
        area.setText("CHYBA: " + message);
        JOptionPane.showMessageDialog(null, message, "Chyba", JOptionPane.ERROR_MESSAGE);
        updateStatus("Chyba: " + message);
    }
}