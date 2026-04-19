package com.ctrlscript.csua;

/**
 * The 4 primitives the JS bridge calls into Java.
 * Bootstrap.java implements this. QuickJS JNI looks up
 * these exact method names — don't rename them.
 */
public interface BridgeInterface {
    int    createView(String type);
    void   setProp(int viewId, String key, Object value);
    void   addChild(int parentId, int childId);
    Object call(String module, String method, Object[] args);
}
