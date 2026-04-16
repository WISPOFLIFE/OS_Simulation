import javax.swing.JFrame;

public class FrontEnd extends JFrame {
    
    public FrontEnd() {
        setTitle("Front End");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center the window
    }

    public static void main(String[] args) {
        FrontEnd frontEnd = new FrontEnd();
        frontEnd.setVisible(true);
    }
    
}
