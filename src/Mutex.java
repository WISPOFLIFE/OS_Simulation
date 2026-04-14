import java.util.*;

public class Mutex {
    private String resourceName;
    private boolean locked;
    private int ownerID; // process ID of owner, -1 if none
    private Queue<Integer> blockedQueue; // process IDs waiting for this resource

    public Mutex(String resourceName) {
        this.resourceName = resourceName;
        this.locked = false;
        this.ownerID = -1;
        this.blockedQueue = new LinkedList<>();
    }

    public String getResourceName() { return resourceName; }
    public boolean isLocked() { return locked; }
    public int getOwnerID() { return ownerID; }
    public Queue<Integer> getBlockedQueue() { return blockedQueue; }

    // Returns true if acquired, false if blocked
    public boolean semWait(int processID) {
        if (!locked) {
            locked = true;
            ownerID = processID;
            System.out.println("  [Mutex] Process " + processID + " acquired '" + resourceName + "'");
            return true;
        } else {
            blockedQueue.add(processID);
            System.out.println("  [Mutex] Process " + processID + " BLOCKED on '" + resourceName
                    + "' (held by Process " + ownerID + ")");
            return false;
        }
    }

    // Returns the process ID that was unblocked, or -1 if no one was waiting
    public int semSignal(int processID) {
        if (ownerID != processID) {
            System.out.println("  [Mutex] WARNING: Process " + processID
                    + " tried to release '" + resourceName + "' but doesn't own it.");
            return -1;
        }

        System.out.println("  [Mutex] Process " + processID + " released '" + resourceName + "'");

        if (!blockedQueue.isEmpty()) {
            int nextProcess = blockedQueue.poll();
            ownerID = nextProcess;
            // locked stays true
            System.out.println("  [Mutex] Process " + nextProcess + " UNBLOCKED and acquired '" + resourceName + "'");
            return nextProcess;
        } else {
            locked = false;
            ownerID = -1;
            return -1;
        }
    }
}