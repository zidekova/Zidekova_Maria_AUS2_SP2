package data;

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

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    /**
     * Returns the size of this record in bytes
     */
    @Override
    public int getSize() {
        return (1 + NAME_SIZE) + (1 + SURNAME_SIZE) + (1 + DATE_OF_BIRTH_SIZE) + (1 + ID_SIZE);
    }

    /**
     * Serializes a person to a byte array with fixed-size fields
     */
    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(this.getSize());

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
            throw new IllegalArgumentException("Invalid data size for Person");
        }

        // wrap data in buffer for reading
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.name = this.getFixedString(buffer, NAME_SIZE);
        this.surname = this.getFixedString(buffer, SURNAME_SIZE);

        String dateStr = this.getFixedString(buffer, DATE_OF_BIRTH_SIZE);
        try {
            this.dateOfBirth = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
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

    @Override
    public String getKey() {
        return this.id;
    }

    // Pridaná metóda pre nastavenie kľúča
    public void setKey(String key) {
        this.id = key;
    }

    /**
     * Writes a String to ByteBuffer with fixed length
     */
    private void putFixedString(ByteBuffer buffer, String value, int length) {
        if (value == null) value = "";

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int validCharsCount = Math.min(bytes.length, length);

        buffer.put((byte) validCharsCount);
        buffer.put(bytes, 0, validCharsCount);

        // space-padding
        for (int i = validCharsCount; i < length; i++) {
            buffer.put((byte) ' ');
        }
    }

    /**
     * Reads a fixed-length String from ByteBuffer
     */
    private String getFixedString(ByteBuffer buffer, int length) {
        int validCharsCount = Byte.toUnsignedInt(buffer.get());

        byte[] bytes = new byte[length];
        buffer.get(bytes);

        if (validCharsCount > length) validCharsCount = length;

        return new String(bytes, 0, validCharsCount, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s), ID: %s",
                name != null ? name : "NULL",
                surname != null ? surname : "NULL",
                dateOfBirth != null ? dateOfBirth : "NULL",
                id != null ? id : "NULL");
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}