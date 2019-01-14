package edu.ucsd.msjava.params;

import edu.ucsd.msjava.parser.BufferedLineReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;


public class UserParam {
    public static ArrayList<String> parseFromFile(String fileName, int tokenLength) {
        ArrayList<String> paramStrs = new ArrayList<String>();
        BufferedLineReader in = null;
        try {
            in = new BufferedLineReader(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String s;
        while ((s = in.readLine()) != null) {
            if (s.startsWith("#") || s.length() == 0)
                continue;
            String[] token = s.split(",");
            if (token.length != tokenLength)
                continue;
            else
                paramStrs.add(s);
        }
        return paramStrs;
    }

}
