import java.io.*;
import java.util.*;

public class Memory {
    private MemoryWord[] memory;
    private int size;

    public Memory(int size) {
        this.size = size;
        this.memory = new MemoryWord[size];
        for (int i = 0; i < size; i++) {
            memory[i] = new MemoryWord();
        }
    }

    public int getSize() { return size; }

    public MemoryWord getWord(int index) {
        if (index >= 0 && index < size) return memory[index];
        return null;
    }

    public void setWord(int index, String name, String value) {
        if (index >= 0 && index < size) {
            memory[index].setName(name);
            memory[index].setValue(value);
        }
    }

    public void clearWord(int index) {
        if (index >= 0 && index < size) {
            memory[index].clear();
        }
    }

    // Find contiguous free block of given size
    public int findFreeBlock(int blockSize) {
        int count = 0;
        int startIndex = -1;
        for (int i = 0; i < size; i++) {
            if (memory[i].isEmpty()) {
                if (count == 0) startIndex = i;
                count++;
                if (count == blockSize) return startIndex;
            } else {
                count = 0;
                startIndex = -1;
            }
        }
        return -1; // no space
    }

    public int getFreeSpace() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (memory[i].isEmpty()) count++;
        }
        return count;
    }

    // Load a process into memory
    public int loadProcess(Process process, int processID) {
        int needed = process.getMemoryNeeded();
        int startIndex = findFreeBlock(needed);
        if (startIndex == -1) return -1;

        int idx = startIndex;
        int endIndex = startIndex + needed - 1;

        // PCB: 4 words
        setWord(idx++, "P" + processID + "_ID", String.valueOf(processID));
        setWord(idx++, "P" + processID + "_State", "READY");
        setWord(idx++, "P" + processID + "_PC", "0");
        setWord(idx++, "P" + processID + "_MemBounds", startIndex + "," + endIndex);

        // Code lines
        List<String> instructions = process.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            setWord(idx++, "P" + processID + "_line" + i, instructions.get(i));
        }

        // 3 variable slots
        for (int i = 0; i < 3; i++) {
            setWord(idx++, "P" + processID + "_var" + i, "");
        }

        process.setInMemory(true);
        return startIndex;
    }

    // Free memory block for a process
    public void freeProcessMemory(int memStart, int memEnd) {
        for (int i = memStart; i <= memEnd; i++) {
            clearWord(i);
        }
    }

    // Update PCB state in memory
    public void updatePCBState(int memStart, int processID, String state) {
        setWord(memStart + 1, "P" + processID + "_State", state);
    }

    // Update program counter in memory
    public void updatePCBProgramCounter(int memStart, int processID, int pc) {
        setWord(memStart + 2, "P" + processID + "_PC", String.valueOf(pc));
    }

    // Read variable from process memory block
    public String readVariable(int memStart, int memEnd, String varName, int processID) {
        for (int i = memStart; i <= memEnd; i++) {
            if (memory[i].getName() != null && memory[i].getName().equals("P" + processID + "_" + varName)) {
                return memory[i].getValue();
            }
        }
        return null;
    }

    // Write variable to process memory block
    public boolean writeVariable(int memStart, int memEnd, String varName, String value, int processID) {
        // First check if variable already exists
        for (int i = memStart; i <= memEnd; i++) {
            if (memory[i].getName() != null && memory[i].getName().equals("P" + processID + "_" + varName)) {
                memory[i].setValue(value);
                return true;
            }
        }
        // Find an empty variable slot
        for (int i = memStart; i <= memEnd; i++) {
            if (memory[i].getName() != null && memory[i].getName().startsWith("P" + processID + "_var")
                    && (memory[i].getValue() == null || memory[i].getValue().isEmpty())) {
                memory[i].setName("P" + processID + "_" + varName);
                memory[i].setValue(value);
                return true;
            }
        }
        return false; // no space
    }

    // Get instruction at given program counter
    public String getInstruction(int memStart, int processID, int pc) {
        String lineName = "P" + processID + "_line" + pc;
        for (int i = memStart; i <= memStart + 3 + pc + 1; i++) {
            if (i < size && memory[i].getName() != null && memory[i].getName().equals(lineName)) {
                return memory[i].getValue();
            }
        }
        return null;
    }

    // Get total number of instructions for a process
    public int getInstructionCount(int memStart, int memEnd, int processID) {
        int count = 0;
        for (int i = memStart; i <= memEnd; i++) {
            if (memory[i].getName() != null && memory[i].getName().startsWith("P" + processID + "_line")) {
                count++;
            }
        }
        return count;
    }

    // Swap process out to disk
    public void swapOutToDisk(int memStart, int memEnd, int processID) {
        String fileName = "disk_process_" + processID + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            for (int i = memStart; i <= memEnd; i++) {
                writer.println(memory[i].getName() + "=" + memory[i].getValue());
            }
            System.out.println(">>> SWAP OUT: Process " + processID + " saved to disk file: " + fileName);
            System.out.println("--- Disk Format ---");
            for (int i = memStart; i <= memEnd; i++) {
                System.out.println("  " + memory[i].getName() + " = " + memory[i].getValue());
            }
            System.out.println("-------------------");
        } catch (IOException e) {
            System.out.println("Error writing process to disk: " + e.getMessage());
        }
        freeProcessMemory(memStart, memEnd);
    }

    // Swap process in from disk
    public int swapInFromDisk(int processID) {
        String fileName = "disk_process_" + processID + ".txt";
        List<String[]> data = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                data.add(parts);
            }
        } catch (IOException e) {
            System.out.println("Error reading process from disk: " + e.getMessage());
            return -1;
        }

        int needed = data.size();
        int startIndex = findFreeBlock(needed);
        if (startIndex == -1) return -1;

        for (int i = 0; i < data.size(); i++) {
            setWord(startIndex + i, data.get(i)[0], data.get(i).length > 1 ? data.get(i)[1] : "");
        }

        // Update memory bounds in PCB
        int endIndex = startIndex + needed - 1;
        setWord(startIndex + 3, "P" + processID + "_MemBounds", startIndex + "," + endIndex);

        // Delete disk file
        new File(fileName).delete();
        System.out.println(">>> SWAP IN: Process " + processID + " loaded back from disk into memory.");
        return startIndex;
    }

    // Print memory state
    public void printMemory(int clock) {
        System.out.println("\n=== Memory State at Clock Cycle " + clock + " ===");
        System.out.printf("%-7s | %-25s | %-30s%n", "Index", "Name", "Value");
        System.out.println("--------|---------------------------|-------------------------------");
        for (int i = 0; i < size; i++) {
            String name = memory[i].getName() != null ? memory[i].getName() : "(empty)";
            String value = memory[i].getValue() != null ? memory[i].getValue() : "(empty)";
            System.out.printf("%-7d | %-25s | %-30s%n", i, name, value);
        }
        System.out.println("=========================================================\n");
    }
}