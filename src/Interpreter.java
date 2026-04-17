
@SuppressWarnings("FieldMayBeFinal")

public class Interpreter {

    private Memory memory;
    private SystemCalls sysCalls;
    private Mutexes mutexes;
    @SuppressWarnings("unused")
    private Scheduler scheduler;

    public Interpreter(Memory memory, SystemCalls sysCalls, Mutexes mutexes, Scheduler scheduler) {
        this.memory = memory;
        this.sysCalls = sysCalls;
        this.mutexes = mutexes;
        this.scheduler = scheduler;
    }

    /**
     * Execute one instruction for a process.
     * Returns: "OK", "BLOCKED", "UNBLOCKED:pid", or "TERMINATED"
     */
    public String execute(int pid) {
        int pc = memory.getProcessPC().get(pid);
        String instruction = memory.getInstruction(pid, pc);

        if (instruction == null) return "TERMINATED";

        System.out.println("  Executing: [P" + pid + "] Instruction " + pc + ": " + instruction);

        String[] parts = instruction.split(" ", 3);
        String cmd = parts[0];

        switch (cmd) {
            case "assign" -> {
                return doAssign(pid, parts);
            }
            case "print" -> {
                return doPrint(pid, parts);
            }
            case "writeFile" -> {
                return doWriteFile(pid, parts);
            }
            case "readFile" -> {
                return doReadFile(pid, parts);
            }
            case "printFromTo" -> {
                return doPrintFromTo(pid, parts);
            }
            case "semWait" -> {
                return doSemWait(pid, parts);
            }
            case "semSignal" -> {
                return doSemSignal(pid, parts);
            }
            default -> {
                System.out.println("  Unknown command: " + cmd);
                advancePC(pid);
                return "OK";
            }
        }
    }

    private void advancePC(int pid) {
        int pc = memory.getProcessPC().get(pid);
        memory.getProcessPC().put(pid, pc + 1);
        memory.updatePCB(pid);
    }

    // ========== ASSIGN ==========

    private String doAssign(int pid, String[] parts) {
        String varName = parts[1];
        String value;

        if (parts.length > 2 && parts[2].equals("input")) {
            value = sysCalls.input();
        } else {
            value = parts.length > 2 ? parts[2] : "";
        }

        sysCalls.writeMem(pid, varName, value);
        advancePC(pid);
        return "OK";
    }

    // ========== PRINT ==========

    private String doPrint(int pid, String[] parts) {
        String val = sysCalls.readMem(pid, parts[1]);
        sysCalls.print(val != null ? val : parts[1]);
        advancePC(pid);
        return "OK";
    }

    // ========== WRITE FILE ==========

    private String doWriteFile(int pid, String[] parts) {
        String filename = sysCalls.readMem(pid, parts[1]);
        String data = parts.length > 2 ? sysCalls.readMem(pid, parts[2]) : "";
        if (filename != null && data != null) {
            sysCalls.writeFile(filename, data);
        }
        advancePC(pid);
        return "OK";
    }

    // ========== READ FILE ==========

    private String doReadFile(int pid, String[] parts) {
        String filename = sysCalls.readMem(pid, parts[1]);
        if (filename != null) {
            String content = sysCalls.readFile(filename);
            // Store file content back into the variable
            sysCalls.writeMem(pid, parts[1], content);
        }
        advancePC(pid);
        return "OK";
    }

    // ========== PRINT FROM TO ==========

    private String doPrintFromTo(int pid, String[] parts) {
        String val1 = sysCalls.readMem(pid, parts[1]);
        String val2 = parts.length > 2 ? sysCalls.readMem(pid, parts[2]) : "0";
        try {
            int from = Integer.parseInt(val1);
            int to = Integer.parseInt(val2);
            for (int i = from; i <= to; i++) {
                sysCalls.print(String.valueOf(i));
            }
        } catch (NumberFormatException e) {
            System.out.println("  Error: printFromTo needs integers");
        }
        advancePC(pid);
        return "OK";
    }

    // ========== SEM WAIT ==========

    private String doSemWait(int pid, String[] parts) {
        String resource = parts[1];
        boolean acquired = mutexes.semWait(resource, pid);

        if (acquired) {
            advancePC(pid);
            return "OK";
        } else {
            // Process is blocked — do NOT advance PC
            memory.getProcessState().put(pid, "BLOCKED");
            memory.updatePCB(pid);
            return "BLOCKED";
        }
    }

    // ========== SEM SIGNAL ==========

    private String doSemSignal(int pid, String[] parts) {
        String resource = parts[1];
        int unblockedPID = mutexes.semSignal(resource, pid);
        advancePC(pid);

        if (unblockedPID != -1) {
            return "UNBLOCKED:" + unblockedPID;
        }
        return "OK";
    }
}