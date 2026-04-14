import java.util.*;

public class Scheduler {
    private Queue<Integer> readyQueue;      // process IDs
    private Queue<Integer> blockedQueue;    // process IDs
    private Map<Integer, PCB> pcbTable;     // processID -> PCB
    private Map<Integer, Process> processTable; // processID -> Process
    private String algorithm; // "RR", "HRRN", "MLFQ"
    private int timeSlice;
    private int clock;

    // MLFQ specific
    private List<Queue<Integer>> mlfqQueues;
    private Map<Integer, Integer> mlfqLevels; // processID -> current queue level

    public Scheduler(String algorithm, int timeSlice) {
        this.readyQueue = new LinkedList<>();
        this.blockedQueue = new LinkedList<>();
        this.pcbTable = new LinkedHashMap<>();
        this.processTable = new LinkedHashMap<>();
        this.algorithm = algorithm;
        this.timeSlice = timeSlice;
        this.clock = 0;

        // MLFQ: 4 levels
        if (algorithm.equals("MLFQ")) {
            mlfqQueues = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                mlfqQueues.add(new LinkedList<>());
            }
            mlfqLevels = new HashMap<>();
        }
    }

    public int getClock() { return clock; }
    public void incrementClock() { clock++; }
    public Map<Integer, PCB> getPcbTable() { return pcbTable; }
    public Map<Integer, Process> getProcessTable() { return processTable; }
    public Queue<Integer> getReadyQueue() { return readyQueue; }
    public Queue<Integer> getBlockedQueue() { return blockedQueue; }
    public String getAlgorithm() { return algorithm; }
    public int getTimeSlice() { return timeSlice; }
    public void setTimeSlice(int ts) { this.timeSlice = ts; }

    public void addProcess(int processID, PCB pcb, Process process) {
        pcbTable.put(processID, pcb);
        processTable.put(processID, process);

        if (algorithm.equals("MLFQ")) {
            mlfqQueues.get(0).add(processID); // start at highest priority
            mlfqLevels.put(processID, 0);
        } else {
            readyQueue.add(processID);
        }
    }

    public void blockProcess(int processID) {
        readyQueue.remove(processID);
        if (algorithm.equals("MLFQ")) {
            for (Queue<Integer> q : mlfqQueues) q.remove(processID);
        }
        if (!blockedQueue.contains(processID)) {
            blockedQueue.add(processID);
        }
        PCB pcb = pcbTable.get(processID);
        if (pcb != null) pcb.setProcessState("BLOCKED");
    }

    public void unblockProcess(int processID) {
        blockedQueue.remove(processID);
        PCB pcb = pcbTable.get(processID);
        if (pcb != null) {
            pcb.setProcessState("READY");
            if (algorithm.equals("MLFQ")) {
                int level = mlfqLevels.getOrDefault(processID, 0);
                mlfqQueues.get(level).add(processID);
            } else {
                readyQueue.add(processID);
            }
        }
    }

    public void terminateProcess(int processID) {
        readyQueue.remove(processID);
        blockedQueue.remove(processID);
        if (algorithm.equals("MLFQ")) {
            for (Queue<Integer> q : mlfqQueues) q.remove(processID);
            mlfqLevels.remove(processID);
        }
        PCB pcb = pcbTable.get(processID);
        if (pcb != null) pcb.setProcessState("TERMINATED");
    }

    // Pick next process based on algorithm
    public int pickNextProcess() {
        switch (algorithm) {
            case "RR":
                return pickRR();
            case "HRRN":
                return pickHRRN();
            case "MLFQ":
                return pickMLFQ();
            default:
                return pickRR();
        }
    }

    private int pickRR() {
        if (readyQueue.isEmpty()) return -1;
        return readyQueue.poll();
    }

    private int pickHRRN() {
        if (readyQueue.isEmpty()) return -1;

        int bestPID = -1;
        double bestRatio = -1;

        for (int pid : readyQueue) {
            PCB pcb = pcbTable.get(pid);
            Process proc = processTable.get(pid);
            if (pcb == null || proc == null) continue;

            int burstTime = proc.getInstructions().size();
            int waitingTime = clock - proc.getArrivalTime() - pcb.getProgramCounter();
            if (waitingTime < 0) waitingTime = 0;

            double ratio = (double)(waitingTime + burstTime) / burstTime;

            if (ratio > bestRatio) {
                bestRatio = ratio;
                bestPID = pid;
            }
        }

        if (bestPID != -1) {
            readyQueue.remove(bestPID);
        }
        return bestPID;
    }

    private int pickMLFQ() {
        for (int level = 0; level < 4; level++) {
            if (!mlfqQueues.get(level).isEmpty()) {
                return mlfqQueues.get(level).poll();
            }
        }
        return -1;
    }

    public int getQuantumForProcess(int processID) {
        if (algorithm.equals("MLFQ")) {
            int level = mlfqLevels.getOrDefault(processID, 0);
            return (int) Math.pow(2, level);
        }
        return timeSlice;
    }

    // After a process uses its full quantum in MLFQ, demote it
    public void demoteMLFQ(int processID) {
        if (!algorithm.equals("MLFQ")) return;
        int currentLevel = mlfqLevels.getOrDefault(processID, 0);
        int newLevel = Math.min(currentLevel + 1, 3);
        mlfqLevels.put(processID, newLevel);
        mlfqQueues.get(newLevel).add(processID);
    }

    // After a process doesn't use full quantum in MLFQ, keep at same level
    public void keepLevelMLFQ(int processID) {
        if (!algorithm.equals("MLFQ")) return;
        int level = mlfqLevels.getOrDefault(processID, 0);
        mlfqQueues.get(level).add(processID);
    }

    public void reAddToReadyQueue(int processID) {
        if (!algorithm.equals("MLFQ")) {
            readyQueue.add(processID);
        }
        // For MLFQ, use demoteMLFQ or keepLevelMLFQ
    }

    public boolean allTerminated() {
        for (PCB pcb : pcbTable.values()) {
            if (!pcb.getProcessState().equals("TERMINATED")) {
                return false;
            }
        }
        return !pcbTable.isEmpty();
    }

    public boolean hasReadyProcesses() {
        if (algorithm.equals("MLFQ")) {
            for (Queue<Integer> q : mlfqQueues) {
                if (!q.isEmpty()) return true;
            }
            return false;
        }
        return !readyQueue.isEmpty();
    }

    public void printQueues() {
        System.out.println("--- Queues ---");
        if (algorithm.equals("MLFQ")) {
            for (int i = 0; i < 4; i++) {
                System.out.println("  MLFQ Level " + i + " (quantum=" + (int)Math.pow(2,i) + "): " + mlfqQueues.get(i));
            }
        } else {
            System.out.println("  Ready Queue:   " + readyQueue);
        }
        System.out.println("  Blocked Queue: " + blockedQueue);
        System.out.println();
    }
}