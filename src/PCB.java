public class PCB {
    private int processID;
    private String processState; // NEW, READY, RUNNING, BLOCKED, TERMINATED
    private int programCounter;
    private int memStart;
    private int memEnd;

    public PCB(int processID, String processState, int programCounter, int memStart, int memEnd) {
        this.processID = processID;
        this.processState = processState;
        this.programCounter = programCounter;
        this.memStart = memStart;
        this.memEnd = memEnd;
    }

    public int getProcessID() { return processID; }
    public String getProcessState() { return processState; }
    public void setProcessState(String state) { this.processState = state; }
    public int getProgramCounter() { return programCounter; }
    public void setProgramCounter(int pc) { this.programCounter = pc; }
    public int getMemStart() { return memStart; }
    public void setMemStart(int memStart) { this.memStart = memStart; }
    public int getMemEnd() { return memEnd; }
    public void setMemEnd(int memEnd) { this.memEnd = memEnd; }
}