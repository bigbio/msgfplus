package edu.ucsd.msjava.parser;

import java.io.*;
import net.pempek.unicode.UnicodeBOMInputStream;

public class BufferedLineReader extends BufferedReader implements LineReader {

    public BufferedLineReader(String fileName) throws IOException {
        super(new InputStreamReader(new UnicodeBOMInputStream(new FileInputStream(fileName))));
    }

    @Override
    public String readLine() {
        try {
            return super.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
