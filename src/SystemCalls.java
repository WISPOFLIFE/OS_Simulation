import java.io.*;
import java.util.Scanner;
@SuppressWarnings("FieldMayBeFinal")
public class SystemCalls {

    private Memory memory;
    private Scanner scanner;

    public SystemCalls(Memory memory) {
        this.memory = memory;
        this.scanner = new Scanner(System.in);
    }

    // System Call 1: Read file from disk
    public String readFile(String filename) {
        System.out.println("  [SysCall] readFile: " + filename);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (content.length() > 0) content.append("\n");
                content.append(line);
            }
        } catch (IOException e) {
            System.out.println("  [SysCall] Error reading file: " + e.getMessage());
        }
        return content.toString();
    }

    // System Call 2: Write to file on disk
    public void writeFile(String filename, String data) {
        System.out.println("  [SysCall] writeFile: " + filename + " -> " + data);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.print(data);
        } catch (IOException e) {
            System.out.println("  [SysCall] Error writing file: " + e.getMessage());
        }
    }

    // System Call 3: Print to screen
    public void print(String data) {
        System.out.println("  [OUTPUT] " + data);
    }

    // System Call 4: Take input from user
    public String input() {
        System.out.println("  [OUTPUT] Please enter a value:");
        System.out.print("  [INPUT] > ");
        return scanner.nextLine().trim();
    }

    // System Call 5: Read from memory
    public String readMem(int pid, String varName) {
        return memory.readVar(pid, varName);
    }

    // System Call 6: Write to memory
    public void writeMem(int pid, String varName, String value) {
        memory.writeVar(pid, varName, value);
    }
}