import java.io.*;
import java.util.*;

public class OS {
    private Memory memory;
    private Scheduler scheduler;
    private Interpreter interpreter;
    private MutexManager mutexManager;
    private SystemCallHandler sysCallHandler;

    private Map<Integer, String> arrivalMap; // clock -> program file path
    private int nextProcessID;

    public OS(String algorithm, int timeSlice, int memorySize) {
        this.memory = new Memory(memorySize);
        this.mutexManager = new MutexManager();
        this.sysCallHandler = new SystemCallHandler(memory);
        this.scheduler = new Scheduler(algorithm, timeSlice);
        this.interpreter = new Interpreter(sysCallHandler, memory, mutexManager);
        this.arrivalMap = new TreeMap<>();
        this.nextProcessID = 1;
    }

    public void setArrival(int clockCycle, String programFile) {
        arrivalMap.put(clockCycle, programFile);
    }

    public void run() {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║          OS SIMULATOR STARTED                    ║");
        System.out.println("║  Algorithm: " + scheduler.getAlgorithm()
                + "  |  Time Slice: " + scheduler.getTimeSlice() + "            ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        while (!scheduler.allTerminated() || !arrivalMap.isEmpty()) {
            int clock = scheduler.getClock();

            System.out.println("========================================");
            System.out.println("         CLOCK CYCLE: " + clock);
            System.out.println("========================================");

            // 1. Check arrivals
            checkArrivals(clock);

            // 2. Schedule and execute
            if (scheduler.hasReadyProcesses()) {
                int pid = scheduler.pickNextProcess();
                if (pid != -1) {
                    executeProcess(pid);
                }
            } else if (!scheduler.getBlockedQueue().isEmpty()) {
                System.out.println("  All processes are blocked. Waiting...");
            }

            // 3. Print memory
            memory.printMemory(clock);

            // 4. Advance clock
            scheduler.incrementClock();

            // Safety check to avoid infinite loop
            if (clock > 200) {
                System.out.println("  [OS] Max clock cycles reached. Terminating.");
                break;
            }
        }

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║          ALL PROCESSES TERMINATED                ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

private void checkArrivals(int clock) {
    if (arrivalMap.containsKey(clock)) {
        String programFile = arrivalMap.get(clock);
        arrivalMap.remove(clock);

        System.out.println(">> Process arriving at clock " + clock + ": " + programFile);

        List<String> instructions = readProgramFile(programFile);
        if (instructions == null || instructions.isEmpty()) {
            System.out.println("  [OS] Error: Could not read program file: " + programFile);
            return;
        }

        int pid = nextProcessID++;
        Process process = new Process(pid, instructions, clock);

        int needed = process.getMemoryNeeded();
        if (memory.findFreeBlock(needed) == -1) {
            System.out.println(">> Memory FULL! Need to swap out process(es). Need " + needed + " contiguous words.");
            swapOutProcess(pid, needed);
        }

        int memStart = memory.loadProcess(process, pid);
        if (memStart == -1) {
            System.out.println("  [OS] Error: Still not enough memory after swap.");
            return;
        }

        int memEnd = memStart + needed - 1;
        PCB pcb = new PCB(pid, "READY", 0, memStart, memEnd);
        process.setInMemory(true);

        scheduler.addProcess(pid, pcb, process);

        System.out.println(">> Process " + pid + " created. Memory: [" + memStart + ", " + memEnd + "]");
        scheduler.printQueues();
    }
}

private void swapOutProcess(int excludePID, int spaceNeeded) {
    // Keep swapping until we have enough contiguous space
    while (memory.findFreeBlock(spaceNeeded) == -1) {
        boolean swapped = false;
        for (Map.Entry<Integer, PCB> entry : scheduler.getPcbTable().entrySet()) {
            int pid = entry.getKey();
            PCB pcb = entry.getValue();
            Process proc = scheduler.getProcessTable().get(pid);

            if (pid != excludePID 
                    && !pcb.getProcessState().equals("TERMINATED") 
                    && proc.isInMemory()) {
                System.out.println(">> Swapping out Process " + pid + " to disk.");
                memory.swapOutToDisk(pcb.getMemStart(), pcb.getMemEnd(), pid);
                proc.setInMemory(false);
                proc.setSwappedToDisk(true);
                proc.setDiskFileName("disk_process_" + pid + ".txt");
                swapped = true;
                break;
            }
        }
        if (!swapped) {
            System.out.println("  [OS] Error: No process available to swap out.");
            break;
        }
    }
}

private void ensureInMemory(int pid) {
    Process proc = scheduler.getProcessTable().get(pid);
    PCB pcb = scheduler.getPcbTable().get(pid);

    if (proc == null || pcb == null) return;

    if (!proc.isInMemory() && proc.isSwappedToDisk()) {
        int needed = proc.getMemoryNeeded();
        if (memory.findFreeBlock(needed) == -1) {
            System.out.println(">> Need to swap out process(es) to make room for Process " + pid);
            swapOutProcess(pid, needed);
        }

        int newMemStart = memory.swapInFromDisk(pid);
        if (newMemStart != -1) {
            int newMemEnd = newMemStart + needed - 1;
            pcb.setMemStart(newMemStart);
            pcb.setMemEnd(newMemEnd);
            proc.setInMemory(true);
            proc.setSwappedToDisk(false);
        }
    }
}

    private void executeProcess(int pid) {
        PCB pcb = scheduler.getPcbTable().get(pid);
        Process proc = scheduler.getProcessTable().get(pid);
        if (pcb == null || proc == null) return;

        // Ensure process is in memory
        ensureInMemory(pid);

        pcb.setProcessState("RUNNING");
        memory.updatePCBState(pcb.getMemStart(), pid, "RUNNING");

        System.out.println("\n>> Currently Executing: Process " + pid);

        int quantum = scheduler.getQuantumForProcess(pid);
        int instructionsExecuted = 0;
        boolean blocked = false;
        boolean terminated = false;

        if (scheduler.getAlgorithm().equals("HRRN")) {
            // Non-preemptive: run until done or blocked
            int totalInstructions = memory.getInstructionCount(pcb.getMemStart(), pcb.getMemEnd(), pid);
            while (pcb.getProgramCounter() < totalInstructions) {
                String result = interpreter.execute(pcb);
                instructionsExecuted++;
                scheduler.incrementClock();

                // Check arrivals during execution
                checkArrivals(scheduler.getClock());

                if (result.equals("BLOCKED")) {
                    blocked = true;
                    break;
                } else if (result.equals("TERMINATED")) {
                    terminated = true;
                    break;
                } else if (result.startsWith("UNBLOCKED:")) {
                    int unblockedPID = Integer.parseInt(result.split(":")[1]);
                    scheduler.unblockProcess(unblockedPID);
                    scheduler.printQueues();
                }
            }
            if (!blocked && !terminated) terminated = true;
        } else {
            // RR or MLFQ: run for quantum instructions
            int totalInstructions = memory.getInstructionCount(pcb.getMemStart(), pcb.getMemEnd(), pid);
            for (int i = 0; i < quantum && pcb.getProgramCounter() < totalInstructions; i++) {
                String result = interpreter.execute(pcb);
                instructionsExecuted++;

                if (result.equals("BLOCKED")) {
                    blocked = true;
                    break;
                } else if (result.equals("TERMINATED")) {
                    terminated = true;
                    break;
                } else if (result.startsWith("UNBLOCKED:")) {
                    int unblockedPID = Integer.parseInt(result.split(":")[1]);
                    scheduler.unblockProcess(unblockedPID);
                    scheduler.printQueues();
                }
            }
            if (!blocked && !terminated && pcb.getProgramCounter() >= totalInstructions) {
                terminated = true;
            }
        }

        // Handle result
        if (terminated) {
            System.out.println(">> Process " + pid + " TERMINATED.");
            scheduler.terminateProcess(pid);
            memory.updatePCBState(pcb.getMemStart(), pid, "TERMINATED");
        } else if (blocked) {
            System.out.println(">> Process " + pid + " BLOCKED.");
            scheduler.blockProcess(pid);
        } else {
            // Time slice expired, still has work to do
            pcb.setProcessState("READY");
            memory.updatePCBState(pcb.getMemStart(), pid, "READY");

            if (scheduler.getAlgorithm().equals("MLFQ")) {
                if (instructionsExecuted >= quantum) {
                    scheduler.demoteMLFQ(pid);
                } else {
                    scheduler.keepLevelMLFQ(pid);
                }
            } else {
                scheduler.reAddToReadyQueue(pid);
            }
            System.out.println(">> Process " + pid + " time slice expired. Back to Ready Queue.");
        }

        scheduler.printQueues();
        mutexManager.printMutexStates();
    }

    private List<String> readProgramFile(String filename) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            System.out.println("  [OS] Error reading program file: " + e.getMessage());
            return null;
        }
        return lines;
    }
}