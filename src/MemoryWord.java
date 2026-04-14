public class MemoryWord {
    private String name;
    private String value;

    public MemoryWord(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public MemoryWord() {
        this.name = null;
        this.value = null;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public boolean isEmpty() {
        return name == null && value == null;
    }

    public void clear() {
        this.name = null;
        this.value = null;
    }

    @Override
    public String toString() {
        return "[" + (name != null ? name : "---") + " = " + (value != null ? value : "---") + "]";
    }
}