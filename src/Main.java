import java.io.*;
import java.util.*;
@SuppressWarnings("FieldMayBeFinal")
public class Main {

    private static Memory memory;
    private static SystemCalls sysCalls;
    private static Mutexes mutexes;
    private static Scheduler scheduler;
    private static Interpreter interpreter;

    private static Map<Integer, String> arrivals = new TreeMap<>();
    private static int nextPID = 1;

    public static void main(String[] args) {

        // ===== CONFIGURABLE — change for evaluation =====
        String algorithm = "RR";    // "RR", "HRRN", or "MLFQ"
        int timeSlice = 2;          // instructions per time slice
        int memorySize = 40;

        // ===== INITIALIZE COMPONENTS =====
        memory = new Memory(memorySize);
        sysCalls = new SystemCalls(memory);
        mutexes = new Mutexes();
        scheduler = new Scheduler(algorithm, timeSlice, memory);
        interpreter = new Interpreter(memory, sysCalls, mutexes, scheduler);

        // ===== ARRIVAL TIMES — change for evaluation =====
        arrivals.put(0, "Program_1.txt");
        arrivals.put(1, "Program_2.txt");
        arrivals.put(4, "Program_3.txt");

        // ===== RUN =====
        run();
    }

    private static void run() {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║          OS SIMULATOR STARTED                    ║");
        System.out.println("║  Algorithm: " + scheduler.getAlgorithm()
                + "  |  Time Slice: " + scheduler.getTimeSlice() + "              ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        while (!memory.allTerminated() || !arrivals.isEmpty()) {
            int clock = scheduler.getClock();

            System.out.println("========================================");
            System.out.println("         CLOCK CYCLE: " + clock);
            System.out.println("========================================");

            checkArrivals(clock);

            if (scheduler.hasReadyProcesses()) {
                int pid = scheduler.pickNextProcess();
                if (pid != -1) executeProcess(pid);
            } else if (!scheduler.getBlockedQueue().isEmpty()) {
                System.out.println("  All processes are blocked. Waiting...");
            }

            memory.printMemory(clock);
            scheduler.incrementClock();

            if (clock > 200) {
                System.out.println("  [OS] Max clock cycles reached.");
                break;
            }
        }

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║          ALL PROCESSES TERMINATED                ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    private static void checkArrivals(int clock) {
        if (!arrivals.containsKey(clock)) return;

        String programFile = arrivals.remove(clock);
        System.out.println(">> Process arriving at clock " + clock + ": " + programFile);

        List<String> lines = readProgramFile(programFile);
        if (lines == null || lines.isEmpty()) {
            System.out.println("  [OS] Error: Could not read " + programFile);
            return;
        }

        int pid = nextPID++;

        // Register process
        memory.registerProcess(pid, lines, clock);

        int needed = memory.getMemoryNeeded(pid);

        // Make room if needed
        if (memory.findFreeBlock(needed) == -1) {
            System.out.println(">> Memory FULL! Need to make room for Process " + pid + " (" + needed + " words).");
            memory.makeRoom(pid, needed);
        }

        // Load into memory
        if (!memory.loadProcess(pid)) {
            System.out.println("  [OS] Error: Not enough memory for Process " + pid);
            return;
        }

        scheduler.addProcess(pid);

        int[] bounds = memory.getProcessBounds().get(pid);
        System.out.println(">> Process " + pid + " created. Memory: [" + bounds[0] + ", " + bounds[1] + "]");
        scheduler.printQueues();
    }

    private static void executeProcess(int pid) {
        memory.ensureInMemory(pid);

        if (!memory.getProcessInMemory().get(pid)) {
            System.out.println("  [OS] Process " + pid + " could not be loaded. Skipping.");
            return;
        }

        memory.getProcessState().put(pid, "RUNNING");
        memory.updatePCB(pid);

        System.out.println("\n>> Currently Executing: Process " + pid);

        int totalInstructions = memory.getInstructionCount(pid);
        int quantum = scheduler.getQuantum(pid);
        int executed = 0;
        boolean blocked = false;
        boolean terminated = false;

        int limit = scheduler.getAlgorithm().equals("HRRN") ? Integer.MAX_VALUE : quantum;

        for (int i = 0; i < limit && memory.getProcessPC().get(pid) < totalInstructions; i++) {
            String result = interpreter.execute(pid);
            executed++;

            if (result.equals("BLOCKED")) {
                blocked = true;
                break;
            } else if (result.startsWith("UNBLOCKED:")) {
                int unblockedPID = Integer.parseInt(result.split(":")[1]);
                scheduler.unblockProcess(unblockedPID);
                scheduler.printQueues();
            }
        }

        if (!blocked && memory.getProcessPC().get(pid) >= totalInstructions) {
            terminated = true;
        }

        if (terminated) {
            System.out.println(">> Process " + pid + " TERMINATED.");
            scheduler.terminateProcess(pid);
            memory.freeProcessMemory(pid);
            System.out.println(">> Process " + pid + " memory freed.");
        } else if (blocked) {
            System.out.println(">> Process " + pid + " BLOCKED.");
            scheduler.blockProcess(pid);
        } else {
            memory.getProcessState().put(pid, "READY");
            memory.updatePCB(pid);
            scheduler.requeueAfterTimeSlice(pid, executed);
            System.out.println(">> Process " + pid + " time slice expired. Back to Ready Queue.");
        }

        scheduler.printQueues();
        mutexes.printStates();
    }

    private static List<String> readProgramFile(String filename) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (IOException e) {
            System.out.println("  [OS] Error reading file: " + e.getMessage());
            return null;
        }
        return lines;
    }
}