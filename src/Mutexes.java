import java.util.*;
@SuppressWarnings("FieldMayBeFinal")
public class Mutexes {

    private Map<String, Boolean> locked;            // resource -> locked?
    private Map<String, Integer> owner;              // resource -> owner pid
    private Map<String, Queue<Integer>> waiting;     // resource -> waiting pids

    public Mutexes() {
        locked = new HashMap<>();
        owner = new HashMap<>();
        waiting = new HashMap<>();

        String[] resources = {"file", "userInput", "userOutput"};
        for (String r : resources) {
            locked.put(r, false);
            owner.put(r, -1);
            waiting.put(r, new LinkedList<>());
        }
    }

    /**
     * Try to acquire a resource.
     * Returns true if acquired, false if blocked.
     */
    public boolean semWait(String resource, int pid) {
        if (!locked.get(resource)) {
            locked.put(resource, true);
            owner.put(resource, pid);
            System.out.println("  [Mutex] Process " + pid + " acquired '" + resource + "'");
            return true;
        } else {
            waiting.get(resource).add(pid);
            System.out.println("  [Mutex] Process " + pid + " BLOCKED on '" + resource
                    + "' (held by P" + owner.get(resource) + ")");
            return false;
        }
    }

    /**
     * Release a resource.
     * Returns the PID of the unblocked process, or -1 if none.
     */
    public int semSignal(String resource, int pid) {
        System.out.println("  [Mutex] Process " + pid + " released '" + resource + "'");

        Queue<Integer> waitQueue = waiting.get(resource);
        if (!waitQueue.isEmpty()) {
            int nextPid = waitQueue.poll();
            owner.put(resource, nextPid);
            // locked stays true
            System.out.println("  [Mutex] Process " + nextPid + " UNBLOCKED and acquired '" + resource + "'");
            return nextPid;
        } else {
            locked.put(resource, false);
            owner.put(resource, -1);
            return -1;
        }
    }

    public void printStates() {
        System.out.println("--- Mutex States ---");
        for (String r : new String[]{"file", "userInput", "userOutput"}) {
            String status = locked.get(r) ? "LOCKED by P" + owner.get(r) : "FREE";
            System.out.println("  " + r + ": " + status + " | Waiting: " + waiting.get(r));
        }
        System.out.println();
    }
}