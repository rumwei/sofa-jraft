package com.alipay.sofa.jraft.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class FileUtil {

    public static Map<String, String> readConfigFromFile(String filePath, String split) throws Exception {
        HashMap<String, String> map = new HashMap<>();

        String line;
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(split, 2);
            if (parts.length >= 2) {
                String key = parts[0];
                String value = parts[1];
                map.put(key, value);
            } else {
                System.out.println("ignoring line: " + line);
            }
        }
        reader.close();
        return map;
    }
}
