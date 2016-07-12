import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class Test {
    public static void main(String[] args) throws IOException {
        File in = new File("src/AllTheText/in.txt");
        File out = new File("src/AllTheText/out.txt");

        Generator a = new Generator(in, out);
        a.create();
//        a.printReader();

    }
}
