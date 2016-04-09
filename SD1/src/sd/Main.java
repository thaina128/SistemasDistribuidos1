
package sd;

import java.io.IOException;



public class Main {


  
    public static void main(String[] args) throws IOException {
        
        Thread pb = new ProcessoBase("224.0.0.22");
        pb.start();
        
    }
}