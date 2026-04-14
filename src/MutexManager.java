import java.util.*;

public class MutexManager {
    private Map<String, Mutex> mutexes;
    private Set<Integer> generalBlockedSet; // process IDs of all blocked processes

    public MutexManager() {
        mutexes = new HashMap<>();
        mutexes.put("file", new Mutex("file"));
        mutexes.put("userInput", new Mutex("userInput"));
        mutexes.put("userOutput", new Mutex("userOutput"));
        generalBlockedSet = new LinkedHashSet<>();
    }

    public boolean semWait(String resource, int processID) {
        Mutex mutex = mutexes.get(resource);
        if (mutex == null) {
            System.out.println("  [MutexManager] Unknown resource: " + resource);
            return true;
        }
        boolean acquired = mutex.semWait(processID);
        if (!acquired) {
            generalBlockedSet.add(processID);
        }
        return acquired;
    }

    public int semSignal(String resource, int processID) {
        Mutex mutex = mutexes.get(resource);
        if (mutex == null) {
            System.out.println("  [MutexManager] Unknown resource: " + resource);
            return -1;
        }
        int unblockedProcess = mutex.semSignal(processID);
        if (unblockedProcess != -1) {
            generalBlockedSet.remove(unblockedProcess);
        }
        return unblockedProcess;
    }

    public boolean isBlocked(int processID) {
        return generalBlockedSet.contains(processID);
    }

    public Set<Integer> getGeneralBlockedSet() {
        return generalBlockedSet;
    }

    public void printMutexStates() {
        System.out.println("--- Mutex States ---");
        for (Map.Entry<String, Mutex> entry : mutexes.entrySet()) {
            Mutex m = entry.getValue();
            System.out.println("  " + m.getResourceName() + ": "
                    + (m.isLocked() ? "LOCKED by P" + m.getOwnerID() : "FREE")
                    + " | Waiting: " + m.getBlockedQueue());
        }
    }
}