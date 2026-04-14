public class Main {
    public static void main(String[] args) {

        
        String algorithm = "RR";   // Options: "RR", "HRRN", "MLFQ"
        int timeSlice = 2;         // For RR (can be changed during evaluation)
        int memorySize = 40;       // Fixed memory size

        
        OS os = new OS(algorithm, timeSlice, memorySize);

        
        os.setArrival(0, "Program_1.txt");
        os.setArrival(1, "Program_2.txt");
        os.setArrival(4, "Program_3.txt");

        
        os.run();
    }
}