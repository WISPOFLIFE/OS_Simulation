import java.util.*;
@SuppressWarnings("FieldMayBeFinal")
public class Scheduler {

    private Queue<Integer> readyQueue;
    private Queue<Integer> blockedQueue;
    private String algorithm;
    private int timeSlice;
    private int clock;

    // MLFQ
    private List<Queue<Integer>> mlfqQueues;
    private Map<Integer, Integer> mlfqLevels;

    private Memory memory; // reference to get process info for HRRN

    public Scheduler(String algorithm, int timeSlice, Memory memory) {
        this.algorithm = algorithm;
        this.timeSlice = timeSlice;
        this.memory = memory;
        this.clock = 0;

        readyQueue = new LinkedList<>();
        blockedQueue = new LinkedList<>();

        if (algorithm.equals("MLFQ")) {
            mlfqQueues = new ArrayList<>();
            for (int i = 0; i < 4; i++) mlfqQueues.add(new LinkedList<>());
            mlfqLevels = new HashMap<>();
        }
    }

    public String getAlgorithm() { return algorithm; }
    public int getTimeSlice() { return timeSlice; }
    public int getClock() { return clock; }
    public void incrementClock() { clock++; }
    public Queue<Integer> getReadyQueue() { return readyQueue; }
    public Queue<Integer> getBlockedQueue() { return blockedQueue; }

    // ========== ADD TO READY QUEUE ==========

    public void addProcess(int pid) {
        if (algorithm.equals("MLFQ")) {
            mlfqQueues.get(0).add(pid);
            mlfqLevels.put(pid, 0);
        } else {
            readyQueue.add(pid);
        }
    }

    // ========== PICK NEXT PROCESS ==========

    public int pickNextProcess() {
        return switch (algorithm) {
            case "HRRN" -> pickHRRN();
            case "MLFQ" -> pickMLFQ();
            default -> readyQueue.isEmpty() ? -1 : readyQueue.poll();
        };
    }

    private int pickHRRN() {
        if (readyQueue.isEmpty()) return -1;
        int bestPID = -1;
        double bestRatio = -1;
        for (int pid : readyQueue) {
            int burst = memory.getInstructionCount(pid);
            int waiting = clock - memory.getProcessArrival().get(pid) - memory.getProcessPC().get(pid);
            if (waiting < 0) waiting = 0;
            double ratio = (double) (waiting + burst) / burst;
            if (ratio > bestRatio) {
                bestRatio = ratio;
                bestPID = pid;
            }
        }
        if (bestPID != -1) readyQueue.remove(bestPID);
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

    // ========== GET QUANTUM ==========

    public int getQuantum(int pid) {
        if (algorithm.equals("MLFQ")) {
            int level = mlfqLevels.getOrDefault(pid, 0);
            return (int) Math.pow(2, level);
        }
        return timeSlice;
    }

    // ========== BLOCK PROCESS ==========

    public void blockProcess(int pid) {
        readyQueue.remove(pid);
        if (algorithm.equals("MLFQ")) {
            for (Queue<Integer> q : mlfqQueues) q.remove(pid);
        }
        if (!blockedQueue.contains(pid)) blockedQueue.add(pid);
        memory.getProcessState().put(pid, "BLOCKED");
    }

    // ========== UNBLOCK PROCESS ==========

    public void unblockProcess(int pid) {
        blockedQueue.remove(pid);
        memory.getProcessState().put(pid, "READY");
        if (algorithm.equals("MLFQ")) {
            int level = mlfqLevels.getOrDefault(pid, 0);
            mlfqQueues.get(level).add(pid);
        } else {
            readyQueue.add(pid);
        }
    }

    // ========== REQUEUE AFTER TIME SLICE ==========

    public void requeueAfterTimeSlice(int pid, int executed) {
        if (algorithm.equals("MLFQ")) {
            int level = mlfqLevels.get(pid);
            int quantum = (int) Math.pow(2, level);
            if (executed >= quantum) {
                int newLevel = Math.min(level + 1, 3);
                mlfqLevels.put(pid, newLevel);
                mlfqQueues.get(newLevel).add(pid);
            } else {
                mlfqQueues.get(level).add(pid);
            }
        } else {
            readyQueue.add(pid);
        }
    }

    // ========== TERMINATE PROCESS ==========

    public void terminateProcess(int pid) {
        readyQueue.remove(pid);
        blockedQueue.remove(pid);
        if (algorithm.equals("MLFQ")) {
            for (Queue<Integer> q : mlfqQueues) q.remove(pid);
            mlfqLevels.remove(pid);
        }
        memory.getProcessState().put(pid, "TERMINATED");
    }

    // ========== CHECKS ==========

    public boolean hasReadyProcesses() {
        if (algorithm.equals("MLFQ")) {
            for (Queue<Integer> q : mlfqQueues) if (!q.isEmpty()) return true;
            return false;
        }
        return !readyQueue.isEmpty();
    }

    // ========== PRINT ==========

    public void printQueues() {
        System.out.println("\n--- Queues ---");
        if (algorithm.equals("MLFQ")) {
            for (int i = 0; i < 4; i++) {
                System.out.println("  MLFQ Level " + i + " (quantum=" + (int) Math.pow(2, i) + "): " + mlfqQueues.get(i));
            }
        } else {
            System.out.println("  Ready Queue:   " + readyQueue);
        }
        System.out.println("  Blocked Queue: " + blockedQueue);
        System.out.println();
    }
}