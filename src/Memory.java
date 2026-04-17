import java.io.*;
import java.util.*;
@SuppressWarnings("FieldMayBeFinal")
public class Memory {

    private String[][] memory; // memory[i][0] = name, memory[i][1] = value
    private int size;

    // Process tracking
    private Map<Integer, String> processState;      // pid -> state
    private Map<Integer, Integer> processPC;         // pid -> program counter
    private Map<Integer, int[]> processBounds;       // pid -> [start, end]
    private Map<Integer, Integer> processArrival;    // pid -> arrival clock
    private Map<Integer, Boolean> processInMemory;   // pid -> in memory?
    private Map<Integer, Boolean> processOnDisk;     // pid -> on disk?
    private Map<Integer, List<String>> processInstructions; // pid -> original code lines

    public Memory(int size) {
        this.size = size;
        this.memory = new String[size][2];

        processState = new LinkedHashMap<>();
        processPC = new LinkedHashMap<>();
        processBounds = new LinkedHashMap<>();
        processArrival = new LinkedHashMap<>();
        processInMemory = new LinkedHashMap<>();
        processOnDisk = new LinkedHashMap<>();
        processInstructions = new LinkedHashMap<>();
    }

    // ========== GETTERS ==========

    public int getSize() { return size; }
    public Map<Integer, String> getProcessState() { return processState; }
    public Map<Integer, Integer> getProcessPC() { return processPC; }
    public Map<Integer, int[]> getProcessBounds() { return processBounds; }
    public Map<Integer, Integer> getProcessArrival() { return processArrival; }
    public Map<Integer, Boolean> getProcessInMemory() { return processInMemory; }
    public Map<Integer, Boolean> getProcessOnDisk() { return processOnDisk; }
    public Map<Integer, List<String>> getProcessInstructions() { return processInstructions; }

    // ========== PROCESS REGISTRATION ==========

    public void registerProcess(int pid, List<String> instructions, int arrivalTime) {
        processInstructions.put(pid, instructions);
        processArrival.put(pid, arrivalTime);
        processState.put(pid, "NEW");
        processPC.put(pid, 0);
        processInMemory.put(pid, false);
        processOnDisk.put(pid, false);
    }

    // ========== LOAD PROCESS INTO MEMORY ==========

    public boolean loadProcess(int pid) {
        List<String> lines = processInstructions.get(pid);
        int needed = getMemoryNeeded(pid);
        int start = findFreeBlock(needed);

        if (start == -1) return false;

        int end = start + needed - 1;
        int idx = start;

        // PCB (4 words)
        memory[idx][0] = "P" + pid + "_ID";        memory[idx][1] = String.valueOf(pid); idx++;
        memory[idx][0] = "P" + pid + "_State";      memory[idx][1] = "READY";             idx++;
        memory[idx][0] = "P" + pid + "_PC";          memory[idx][1] = "0";                 idx++;
        memory[idx][0] = "P" + pid + "_MemBounds";   memory[idx][1] = start + "," + end;   idx++;

        // Code lines
        for (int i = 0; i < lines.size(); i++) {
            memory[idx][0] = "P" + pid + "_line" + i;
            memory[idx][1] = lines.get(i);
            idx++;
        }

        // 3 variable slots
        for (int i = 0; i < 3; i++) {
            memory[idx][0] = "P" + pid + "_var" + i;
            memory[idx][1] = "";
            idx++;
        }

        processBounds.put(pid, new int[]{start, end});
        processInMemory.put(pid, true);
        processOnDisk.put(pid, false);
        processState.put(pid, "READY");
        processPC.put(pid, 0);

        return true;
    }

    // ========== MEMORY NEEDED ==========

    public int getMemoryNeeded(int pid) {
        return 4 + processInstructions.get(pid).size() + 3;
    }

    // ========== FIND FREE BLOCK ==========

    public int findFreeBlock(int blockSize) {
        int count = 0, start = -1;
        for (int i = 0; i < size; i++) {
            if (memory[i][0] == null) {
                if (count == 0) start = i;
                count++;
                if (count == blockSize) return start;
            } else {
                count = 0;
                start = -1;
            }
        }
        return -1;
    }

    // ========== FREE MEMORY ==========

    public void freeMemory(int start, int end) {
        for (int i = start; i <= end; i++) {
            memory[i][0] = null;
            memory[i][1] = null;
        }
    }

    public void freeProcessMemory(int pid) {
        int[] bounds = processBounds.get(pid);
        if (bounds != null) {
            freeMemory(bounds[0], bounds[1]);
            processInMemory.put(pid, false);
        }
    }

    // ========== MAKE ROOM (swap/free until space available) ==========

    public void makeRoom(int excludePID, int spaceNeeded) {
        while (findFreeBlock(spaceNeeded) == -1) {
            boolean freed = false;

            // First: free terminated processes still in memory
            for (int pid : new ArrayList<>(processState.keySet())) {
                if ("TERMINATED".equals(processState.get(pid)) && processInMemory.get(pid)) {
                    System.out.println(">> Freeing terminated Process " + pid + " memory.");
                    freeProcessMemory(pid);
                    freed = true;
                    break;
                }
            }
            if (freed) continue;

            // Then: swap out a live process
            for (int pid : new ArrayList<>(processState.keySet())) {
                if (pid != excludePID && !"TERMINATED".equals(processState.get(pid)) && processInMemory.get(pid)) {
                    swapOut(pid);
                    freed = true;
                    break;
                }
            }

            if (!freed) {
                System.out.println("  [Memory] Error: No process available to swap out.");
                break;
            }
        }
    }

    // ========== SWAP OUT TO DISK ==========

    public void swapOut(int pid) {
        int[] bounds = processBounds.get(pid);
        String fileName = "disk_process_" + pid + ".txt";

        System.out.println(">>> SWAP OUT: Process " + pid + " saved to disk: " + fileName);
        System.out.println("--- Disk Format ---");

        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            for (int i = bounds[0]; i <= bounds[1]; i++) {
                writer.println(memory[i][0] + "=" + memory[i][1]);
                System.out.println("  " + memory[i][0] + " = " + memory[i][1]);
            }
        } catch (IOException e) {
            System.out.println("  Error writing to disk: " + e.getMessage());
        }

        System.out.println("-------------------");
        freeMemory(bounds[0], bounds[1]);
        processInMemory.put(pid, false);
        processOnDisk.put(pid, true);
    }

    // ========== SWAP IN FROM DISK ==========

    public void swapIn(int pid) {
        String fileName = "disk_process_" + pid + ".txt";
        List<String[]> data = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                data.add(parts);
            }
        } catch (IOException e) {
            System.out.println("  Error reading from disk: " + e.getMessage());
            return;
        }

        int needed = data.size();
        makeRoom(pid, needed);

        int start = findFreeBlock(needed);
        if (start == -1) {
            System.out.println("  [Memory] Error: Cannot swap in Process " + pid);
            return;
        }

        for (int i = 0; i < data.size(); i++) {
            memory[start + i][0] = data.get(i)[0];
            memory[start + i][1] = data.get(i).length > 1 ? data.get(i)[1] : "";
        }

        int end = start + needed - 1;
        memory[start + 3][1] = start + "," + end;

        processBounds.put(pid, new int[]{start, end});
        processInMemory.put(pid, true);
        processOnDisk.put(pid, false);

        new File(fileName).delete();
        System.out.println(">>> SWAP IN: Process " + pid + " loaded back from disk.");
    }

    // ========== ENSURE PROCESS IS IN MEMORY ==========

    public void ensureInMemory(int pid) {
        if (!processInMemory.get(pid) && processOnDisk.get(pid)) {
            System.out.println(">> Process " + pid + " is on disk. Swapping in...");
            swapIn(pid);
        }
    }

    // ========== VARIABLE READ/WRITE ==========

    public String readVar(int pid, String varName) {
        int[] bounds = processBounds.get(pid);
        for (int i = bounds[0]; i <= bounds[1]; i++) {
            if (memory[i][0] != null && memory[i][0].equals("P" + pid + "_" + varName)) {
                return memory[i][1];
            }
        }
        return null;
    }

    public void writeVar(int pid, String varName, String value) {
        int[] bounds = processBounds.get(pid);

        // Check if variable already exists
        for (int i = bounds[0]; i <= bounds[1]; i++) {
            if (memory[i][0] != null && memory[i][0].equals("P" + pid + "_" + varName)) {
                memory[i][1] = value;
                return;
            }
        }

        // Find empty variable slot
        for (int i = bounds[0]; i <= bounds[1]; i++) {
            if (memory[i][0] != null && memory[i][0].startsWith("P" + pid + "_var")
                    && (memory[i][1] == null || memory[i][1].isEmpty())) {
                memory[i][0] = "P" + pid + "_" + varName;
                memory[i][1] = value;
                return;
            }
        }
        System.out.println("  [Memory] Error: No variable slot for P" + pid);
    }

    // ========== INSTRUCTION ACCESS ==========

    public String getInstruction(int pid, int pc) {
        int[] bounds = processBounds.get(pid);
        String lineName = "P" + pid + "_line" + pc;
        for (int i = bounds[0]; i <= bounds[1]; i++) {
            if (memory[i][0] != null && memory[i][0].equals(lineName)) {
                return memory[i][1];
            }
        }
        return null;
    }

    public int getInstructionCount(int pid) {
        return processInstructions.get(pid).size();
    }

    // ========== UPDATE PCB IN MEMORY ==========

    public void updatePCB(int pid) {
        if (!processInMemory.get(pid)) return;
        int[] bounds = processBounds.get(pid);
        memory[bounds[0] + 1][1] = processState.get(pid);
        memory[bounds[0] + 2][1] = String.valueOf(processPC.get(pid));
    }

    // ========== CHECK ALL TERMINATED ==========

    public boolean allTerminated() {
        if (processState.isEmpty()) return false;
        for (String state : processState.values()) {
            if (!"TERMINATED".equals(state)) return false;
        }
        return true;
    }

    // ========== PRINT MEMORY ==========

    public void printMemory(int clock) {
        System.out.println("\n=== Memory State at Clock Cycle " + clock + " ===");
        System.out.printf("%-7s | %-25s | %-30s%n", "Index", "Name", "Value");
        System.out.println("--------|---------------------------|-------------------------------");
        for (int i = 0; i < size; i++) {
            String name = memory[i][0] != null ? memory[i][0] : "(empty)";
            String value = memory[i][1] != null ? memory[i][1] : "(empty)";
            System.out.printf("%-7d | %-25s | %-30s%n", i, name, value);
        }
        System.out.println("=========================================================\n");
    }
}