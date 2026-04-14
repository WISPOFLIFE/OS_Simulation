import java.util.List;

public class Process {
    private int processID;
    private List<String> instructions;
    private int arrivalTime;
    private boolean inMemory;
    private boolean swappedToDisk;
    private String diskFileName;

    public Process(int processID, List<String> instructions, int arrivalTime) {
        this.processID = processID;
        this.instructions = instructions;
        this.arrivalTime = arrivalTime;
        this.inMemory = false;
        this.swappedToDisk = false;
        this.diskFileName = null;
    }

    public int getProcessID() { return processID; }
    public List<String> getInstructions() { return instructions; }
    public int getArrivalTime() { return arrivalTime; }
    public boolean isInMemory() { return inMemory; }
    public void setInMemory(boolean inMemory) { this.inMemory = inMemory; }
    public boolean isSwappedToDisk() { return swappedToDisk; }
    public void setSwappedToDisk(boolean swapped) { this.swappedToDisk = swapped; }
    public String getDiskFileName() { return diskFileName; }
    public void setDiskFileName(String fileName) { this.diskFileName = fileName; }

    public int getMemoryNeeded() {
        // 4 PCB words + instruction lines + 3 variable slots
        return 4 + instructions.size() + 3;
    }
}