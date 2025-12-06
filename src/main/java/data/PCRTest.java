package data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class PCRTest implements Record<PCRTest> {
    private static final int DATE_TIME_SIZE = 19; // yyyy-MM-dd HH:mm:ss
    private static final int PATIENT_ID_SIZE = 10;
    private static final int NOTE_SIZE = 11;

    private LocalDateTime dateTime;
    private String patientId;
    private int testCode;
    private boolean result;
    private double value;
    private String note;

    public PCRTest() {}

    public PCRTest(LocalDateTime dateTime, String patientId, int testCode,
                   boolean result, double value, String note) {
        this.dateTime = dateTime;
        this.patientId = patientId;
        this.testCode = testCode;
        this.result = result;
        this.value = value;
        this.note = note;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PCRTest test = (PCRTest) other;
        return this.testCode == test.testCode;
    }

    @Override
    public int hashCode() {
        return this.testCode;
    }

    /**
     * Calculates the fixed size of a serialized PCR test record
     */
    @Override
    public int getSize() {
        return (1 + DATE_TIME_SIZE) + (1 + PATIENT_ID_SIZE) + 4 + 1 + 8 + (1 + NOTE_SIZE);
    }

    /**
     * Serializes the PCR test record to a byte array
     * Format: [DateTime][PatientID][TestCode][Result][Value][Note]
     */
    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getSize());

        String dateStr = this.dateTime != null ?
                this.dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) :
                "1900-01-01 00:00:00";
        this.putFixedString(buffer, dateStr, DATE_TIME_SIZE);
        this.putFixedString(buffer, this.patientId, PATIENT_ID_SIZE);
        buffer.putInt(this.testCode);
        buffer.put((byte) (this.result ? 1 : 0));
        buffer.putDouble(this.value);
        this.putFixedString(buffer, this.note, NOTE_SIZE);

        return buffer.array();
    }

    /**
     * Deserializes a PCR test record from a byte array
     */
    @Override
    public void fromBytes(byte[] data) {
        if (data == null || data.length != getSize()) {
            throw new IllegalArgumentException("Invalid data size for PCRTest");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        String dateStr = this.getFixedString(buffer, DATE_TIME_SIZE);
        try {
            this.dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            this.dateTime = LocalDateTime.of(1900, 1, 1, 0, 0);
        }
        this.patientId = this.getFixedString(buffer, PATIENT_ID_SIZE);
        this.testCode = buffer.getInt();
        this.result = buffer.get() == 1;
        this.value = buffer.getDouble();
        this.note = this.getFixedString(buffer, NOTE_SIZE);
    }

    @Override
    public PCRTest createClass() {
        return new PCRTest();
    }

    /**
     * Returns the key (test code) as a string for hashing operations.
     */
    @Override
    public String getKey() {
        return String.valueOf(this.testCode);
    }

    /**
     * Sets the key (test code) from a string value
     */
    @Override
    public void setKey(String key) {
        try {
            this.testCode = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid test code: " + key);
        }
    }

    private void putFixedString(ByteBuffer buffer, String value, int length) {
        if (value == null) value = "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int validChars = Math.min(bytes.length, length);
        buffer.put((byte) validChars);
        buffer.put(bytes, 0, validChars);
        for (int i = validChars; i < length; i++) {
            buffer.put((byte) ' ');
        }
    }

    private String getFixedString(ByteBuffer buffer, int length) {
        int validChars = buffer.get();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, 0, Math.min(validChars, length), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "Test " + testCode +
                ": vykonaný " + dateTime +
                ", ID pacienta = " + patientId +
                ", výsledok =" + result +
                ", hodnota =" + value +
                ", poznámka ='" + note + '\'';
    }

    public LocalDateTime getDateTime() { return this.dateTime; }
    public String getPatientId() { return this.patientId; }
    public int getTestCode() { return this.testCode; }
    public boolean getResult() { return this.result; }
    public double getValue() { return this.value; }
    public String getNote() { return this.note; }
}