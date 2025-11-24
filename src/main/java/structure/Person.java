package structure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class Person implements Record<Person> {
    // fixed field sizes in bytes
    private static final int NAME_SIZE = 15;
    private static final int SURNAME_SIZE = 14;
    private static final int DATE_OF_BIRTH_SIZE = 10;
    private static final int ID_SIZE = 10;

    private String name;
    private String surname;
    private LocalDate dateOfBirth;
    private String id;

    public Person() {}

    public Person(String name, String surname, LocalDate dateOfBirth, String id) {
        this.name = name;
        this.surname = surname;
        this.dateOfBirth = dateOfBirth;
        this.id = id;
    }

    /**
     * Two people are equal if they have the same non-null ID
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Person person = (Person) other;

        if (this.id == null || person.id == null) return false;
        return this.id.equals(person.id);
    }

    /**
     * Returns the size of this record in bytes
     */
    @Override
    public int getSize() {
        return NAME_SIZE + SURNAME_SIZE + DATE_OF_BIRTH_SIZE + ID_SIZE;
    }

    /**
     * Serializes a person to a byte array with fixed-size fields
     */
    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getSize());

        this.putFixedString(buffer, this.name, NAME_SIZE);
        this.putFixedString(buffer, this.surname, SURNAME_SIZE);
        this.putFixedString(buffer, this.dateOfBirth != null ? this.dateOfBirth.toString() : "1900-01-01", DATE_OF_BIRTH_SIZE);
        this.putFixedString(buffer, this.id, ID_SIZE);

        return buffer.array();
    }

    /**
     * Deserializes a person from byte array
     */
    @Override
    public void fromBytes(byte[] data) {
        if (data == null || data.length != getSize()) {
            System.err.println("Error: Invalid data size for deserialization");
            return;
        }

        // wrap data in buffer for reading
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.name = this.getFixedString(buffer, NAME_SIZE);
        this.surname = this.getFixedString(buffer, SURNAME_SIZE);

        String dateStr = this.getFixedString(buffer, DATE_OF_BIRTH_SIZE);
        try {
            this.dateOfBirth = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            System.err.println("Error with parsing date: '" + dateStr + "'");
            this.dateOfBirth = LocalDate.of(1900, 1, 1);
        }

        this.id = this.getFixedString(buffer, ID_SIZE);
    }

    /**
     * Creates a new Person instance
     */
    @Override
    public Person createClass() {
        return new Person();
    }

    /**
     * Writes a String to ByteBuffer with fixed length
     * Pads with space if String is shorter than specified length
     * Truncates if String is longer than specified length
     */
    private void putFixedString(ByteBuffer buffer, String value, int length) {
        if (value == null) value = "";

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int bytesToWrite = Math.min(bytes.length, length);

        buffer.put(bytes, 0, bytesToWrite);

        // space-padding
        for (int i = bytesToWrite; i < length; i++) {
            buffer.put((byte) ' ');
        }
    }

    /**
     * Reads a fixed-length String from ByteBuffer
     * Stops at first space in the data
     */
    private String getFixedString(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);

        // trim trailing spaces
        int actualLength = length;
        while (actualLength > 0 && bytes[actualLength - 1] == ' ') {
            actualLength--;
        }

        return actualLength > 0 ? new String(bytes, 0, actualLength, StandardCharsets.UTF_8) : "";
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s), ID: %s",
                name != null ? name : "NULL",
                surname != null ? surname : "NULL",
                dateOfBirth != null ? dateOfBirth : "NULL",
                id != null ? id : "NULL");
    }

    public String getName() { return name; }
    public String getSurname() { return surname; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getId() { return id; }
}