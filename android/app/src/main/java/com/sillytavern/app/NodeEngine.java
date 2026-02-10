package com.sillytavern.app;

public class NodeEngine {
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    public static native void setICUData(String icuDir);
    public static native int startNodeWithArguments(String[] arguments);
}
