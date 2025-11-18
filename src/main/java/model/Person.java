package model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class Person implements Record<Person> {
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Person person = (Person) other;

        if (this.id == null || person.id == null) return false;
        return this.id.equals(person.id);
    }

    @Override
    public int getSize() {
        return NAME_SIZE + SURNAME_SIZE + DATE_OF_BIRTH_SIZE + ID_SIZE;
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getSize());

        putFixedString(buffer, name, NAME_SIZE);
        putFixedString(buffer, surname, SURNAME_SIZE);
        putFixedString(buffer, dateOfBirth != null ? dateOfBirth.toString() : "1900-01-01", DATE_OF_BIRTH_SIZE);
        putFixedString(buffer, id, ID_SIZE);

        return buffer.array();
    }

    @Override
    public void fromBytes(byte[] data) {
        if (data == null || data.length != getSize()) {
            System.err.println("Error: Invalid data size for deserialization");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.name = getFixedString(buffer, NAME_SIZE);
        this.surname = getFixedString(buffer, SURNAME_SIZE);

        String dateStr = getFixedString(buffer, DATE_OF_BIRTH_SIZE);
        try {
            this.dateOfBirth = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            System.err.println("Error with parsing date: '" + dateStr + "'");
            this.dateOfBirth = LocalDate.of(1900, 1, 1);
        }

        this.id = getFixedString(buffer, ID_SIZE);
    }

    @Override
    public Person createClass() {
        return new Person();
    }

    private void putFixedString(ByteBuffer buffer, String value, int length) {
        if (value == null) value = "";

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int bytesToWrite = Math.min(bytes.length, length);

        buffer.put(bytes, 0, bytesToWrite);

        for (int i = bytesToWrite; i < length; i++) {
            buffer.put((byte) 0);
        }
    }

    private String getFixedString(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);

        int actualLength = 0;
        for (int i = 0; i < length; i++) {
            if (bytes[i] == 0) break;
            actualLength++;
        }

        if (actualLength == 0) return "";

        return new String(bytes, 0, actualLength, StandardCharsets.UTF_8);
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
    public void setId(String id) { this.id = id; }
}