public class Interpreter {
    private SystemCallHandler sysCall;
    private Memory memory;
    private MutexManager mutexManager;

    public Interpreter(SystemCallHandler sysCall, Memory memory, MutexManager mutexManager) {
        this.sysCall = sysCall;
        this.memory = memory;
        this.mutexManager = mutexManager;
    }

    /**
     * Execute one instruction for a process.
     * Returns: "OK", "BLOCKED", or "TERMINATED"
     */
    public String execute(PCB pcb) {
        int pc = pcb.getProgramCounter();
        int pid = pcb.getProcessID();
        int memStart = pcb.getMemStart();
        int memEnd = pcb.getMemEnd();

        String instruction = memory.getInstruction(memStart, pid, pc);

        if (instruction == null) {
            return "TERMINATED";
        }

        System.out.println("  Executing: [P" + pid + "] Instruction " + pc + ": " + instruction);

        String[] parts = instruction.split(" ", 3);
        String command = parts[0];

        switch (command) {
            case "assign":
                return executeAssign(parts, pcb);
            case "print":
                return executePrint(parts, pcb);
            case "writeFile":
                return executeWriteFile(parts, pcb);
            case "readFile":
                return executeReadFile(parts, pcb);
            case "printFromTo":
                return executePrintFromTo(parts, pcb);
            case "semWait":
                return executeSemWait(parts, pcb);
            case "semSignal":
                return executeSemSignal(parts, pcb);
            default:
                System.out.println("  [Interpreter] Unknown command: " + command);
                pcb.setProgramCounter(pc + 1);
                memory.updatePCBProgramCounter(memStart, pid, pc + 1);
                return "OK";
        }
    }

    private String executeAssign(String[] parts, PCB pcb) {
        int pid = pcb.getProcessID();
        int memStart = pcb.getMemStart();
        int memEnd = pcb.getMemEnd();

        String varName = parts[1];
        String value;

        if (parts.length > 2 && parts[2].equals("input")) {
            value = sysCall.takeInput();
        } else {
            value = parts.length > 2 ? parts[2] : "";
        }

        sysCall.writeToMemory(memStart, memEnd, varName, value, pid);

        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        memory.updatePCBProgramCounter(memStart, pid, pcb.getProgramCounter());
        return "OK";
    }

    private String executePrint(String[] parts, PCB pcb) {
        int pid = pcb.getProcessID();
        String varName = parts[1];
        String value = sysCall.readFromMemory(pcb.getMemStart(), pcb.getMemEnd(), varName, pid);
        sysCall.printToScreen(value != null ? value : varName);

        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        memory.updatePCBProgramCounter(pcb.getMemStart(), pid, pcb.getProgramCounter());
        return "OK";
    }

    private String executeWriteFile(String[] parts, PCB pcb) {
        int pid = pcb.getProcessID();
        String fileVarName = parts[1];
        String dataVarName = parts.length > 2 ? parts[2] : "";

        String filename = sysCall.readFromMemory(pcb.getMemStart(), pcb.getMemEnd(), fileVarName, pid);
        String data = sysCall.readFromMemory(pcb.getMemStart(), pcb.getMemEnd(), dataVarName, pid);

        if (filename != null && data != null) {
            sysCall.writeFile(filename, data);
        }

        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        memory.updatePCBProgramCounter(pcb.getMemStart(), pid, pcb.getProgramCounter());
        return "OK";
    }

    private String executeReadFile(String[] parts, PCB pcb) {
        int pid = pcb.getProcessID();
        String fileVarName = parts[1];

        String filename = sysCall.readFromMemory(pcb.getMemStart(), pcb.getMemEnd(), fileVarName, pid);
        if (filename != null) {
            String content = sysCall.readFile(filename);
            // Overwrite the variable with the file content
            // so "print a" will print the file contents, not the filename
            sysCall.writeToMemory(pcb.getMemStart(), pcb.getMemEnd(), fileVarName, content, pid);
        }

        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        memory.updatePCBProgramCounter(pcb.getMemStart(), pid, pcb.getProgramCounter());
        return "OK";
    }

    private String executePrintFromTo(String[] parts, PCB pcb) {
        int pid = pcb.getProcessID();
        String var1 = parts[1];
        String var2 = parts.length > 2 ? parts[2] : "";

        String val1 = sysCall.readFromMemory(pcb.getMemStart(), pcb.getMemEnd(), var1, pid);
        String val2 = sysCall.readFromMemory(pcb.getMemStart(), pcb.getMemEnd(), var2, pid);

        try {
            int from = Integer.parseInt(val1);
            int to = Integer.parseInt(val2);
            for (int i = from; i <= to; i++) {
                sysCall.printToScreen(String.valueOf(i));
            }
        } catch (NumberFormatException e) {
            System.out.println("  [Interpreter] printFromTo error: non-integer values");
        }

        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        memory.updatePCBProgramCounter(pcb.getMemStart(), pid, pcb.getProgramCounter());
        return "OK";
    }

    private String executeSemWait(String[] parts, PCB pcb) {
        String resource = parts[1];
        boolean acquired = mutexManager.semWait(resource, pcb.getProcessID());

        if (acquired) {
            pcb.setProgramCounter(pcb.getProgramCounter() + 1);
            memory.updatePCBProgramCounter(pcb.getMemStart(), pcb.getProcessID(), pcb.getProgramCounter());
            return "OK";
        } else {
            // Process is blocked — do NOT advance PC, it will retry semWait when unblocked
            pcb.setProcessState("BLOCKED");
            memory.updatePCBState(pcb.getMemStart(), pcb.getProcessID(), "BLOCKED");
            return "BLOCKED";
        }
    }

    private String executeSemSignal(String[] parts, PCB pcb) {
        String resource = parts[1];
        int unblockedPID = mutexManager.semSignal(resource, pcb.getProcessID());

        pcb.setProgramCounter(pcb.getProgramCounter() + 1);
        memory.updatePCBProgramCounter(pcb.getMemStart(), pcb.getProcessID(), pcb.getProgramCounter());

        // Return the unblocked PID info (OS will handle moving it to ready queue)
        if (unblockedPID != -1) {
            return "UNBLOCKED:" + unblockedPID;
        }
        return "OK";
    }
}