package uk.ac.manchester.tornado.runtime.utils;

import java.util.HashMap;

public class JsonHandler {

    private StringBuffer indent;

    private void increaseIndent() {
        indent.append("    ");
    }

    private void decreaseIndent() {
        indent.delete(0, 4);
    }

    public String createJSon(HashMap<String, Integer> entry, String name) {
        indent = new StringBuffer();
        StringBuffer json = new StringBuffer("");
        json.append("{\n");
        increaseIndent();
        json.append(indent.toString() + "\"" + name + "\": { \n");
        increaseIndent();
        for (String s : entry.keySet()) {
            json.append(indent.toString() + "\"" + s + "\":  \"" + entry.get(s) + "\",\n");
        }
        json.delete(json.length() - 2, json.length() - 1); // remove last comma
        decreaseIndent();
        json.append(indent.toString() + "}\n");
        decreaseIndent();
        json.append(indent.toString() + "}\n");
        return json.toString();
    }
}
