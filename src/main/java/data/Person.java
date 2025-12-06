package data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class Person implements Record<Person> {
    // fixed field sizes in bytes
    private static final int NAME_SIZE = 15;
    private static final int SURNAME_SIZE = 14;
    private static final int DATE_OF_BIRTH_SIZE = 10;
    private static final int ID_SIZE = 10;

    // maximum number of PCR tests per person
    private static final int MAX_TESTS = 6;
    // size of one test code (int) in bytes
    private static final int TEST_CODE_SIZE = 4;

    private String name;
    private String surname;
    private LocalDate dateOfBirth;
    private String id;

    // list of PCR test codes belonging to this person
    private List<Integer> testCodes;

    public Person() {
        this.testCodes = new ArrayList<>();
    }

    public Person(String name, String surname, LocalDate dateOfBirth, String id) {
        this.name = name;
        this.surname = surname;
        this.dateOfBirth = dateOfBirth;
        this.id = id;
        this.testCodes = new ArrayList<>();
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
        return this.id != null ? this.id.hashCode() : 0;
    }

    /**
     * Returns the size of this record in bytes
     * Includes space for MAX_TESTS test codes
     */
    @Override
    public int getSize() {
        return (1 + NAME_SIZE) + (1 + SURNAME_SIZE) + (1 + DATE_OF_BIRTH_SIZE) +
                (1 + ID_SIZE) + 1 + (MAX_TESTS * TEST_CODE_SIZE);
    }

    /**
     * Serializes a person to a byte array with fixed-size fields
     * Includes serialization of test codes
     */
    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(this.getSize());

        this.putFixedString(buffer, this.name, NAME_SIZE);
        this.putFixedString(buffer, this.surname, SURNAME_SIZE);
        this.putFixedString(buffer, this.dateOfBirth != null ? this.dateOfBirth.toString() : "1900-01-01", DATE_OF_BIRTH_SIZE);
        this.putFixedString(buffer, this.id, ID_SIZE);

        // serialize test codes
        int testCount = Math.min(this.testCodes.size(), MAX_TESTS);
        buffer.put((byte) testCount);

        // write existing test codes
        for (int i = 0; i < testCount; i++) {
            buffer.putInt(this.testCodes.get(i));
        }

        // fill remaining slots with zeros
        for (int i = testCount; i < MAX_TESTS; i++) {
            buffer.putInt(0);
        }

        return buffer.array();
    }

    /**
     * Deserializes a person from byte array
     * Includes deserialization of test codes
     */
    @Override
    public void fromBytes(byte[] data) {
        if (data == null || data.length != this.getSize()) {
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

        // deserialize test codes
        this.testCodes = new ArrayList<>();
        int testCount = Byte.toUnsignedInt(buffer.get());

        for (int i = 0; i < testCount; i++) {
            int testCode = buffer.getInt();
            if (testCode != 0) {
                this.testCodes.add(testCode);
            }
        }

        // skip remaining test code slots
        buffer.position(buffer.position() + (MAX_TESTS - testCount) * TEST_CODE_SIZE);
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

    @Override
    public void setKey(String key) {
        this.id = key;
    }

    /**
     * Adds a test code to this person's list of tests
     * Returns true if successful, false if maximum test count reached
     */
    public boolean addTestCode(int testCode) {
        if (this.testCodes.size() >= MAX_TESTS) {
            return false;
        }
        this.testCodes.add(testCode);
        return true;
    }

    /**
     * Removes a test code from this person's list of tests
     * Returns true if the code was found and removed
     */
    public boolean removeTestCode(int testCode) {
        return this.testCodes.remove((Integer) testCode);
    }

    /**
     * Returns a copy of this person's test codes
     */
    public List<Integer> getTestCodes() {
        return new ArrayList<>(this.testCodes);
    }

    /**
     * Checks if this person can have more tests added
     * Returns true if test count is less than MAX_TESTS
     */
    public boolean canAddTest() {
        return this.testCodes.size() < MAX_TESTS;
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
        return String.format("%s %s (%s), ID: %s, Tests: %d",
                this.name != null ? this.name : "NULL",
                this.surname != null ? this.surname : "NULL",
                this.dateOfBirth != null ? this.dateOfBirth : "NULL",
                this.id != null ? this.id : "NULL",
                this.testCodes.size());
    }

    public String getName() {
        return this.name;
    }

    public String getSurname() {
        return this.surname;
    }

    public LocalDate getDateOfBirth() {
        return this.dateOfBirth;
    }

    public String getId() {
        return this.id;
    }
}