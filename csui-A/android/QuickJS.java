package com.ctrlscript.csua;

/**
 * JNI bridge to the QuickJS C engine.
 * The pre-built libquickjs.so in jniLibs/ implements these native methods.
 *
 * Users never interact with this class directly.
 */
public class QuickJS {

    static {
        System.loadLibrary("quickjs");  // loads jniLibs/<abi>/libquickjs.so
    }

    /** Create a new QuickJS context. Returns a native pointer (as long). */
    public static native long createContext();

    /** Destroy a QuickJS context and free all memory. */
    public static native void destroy(long ctx);

    /**
     * Evaluate JS source code in the given context.
     * Returns the string representation of the result, or null.
     */
    public static native Object eval(long ctx, String source, String filename);

    /**
     * Inject the bridge implementation into the JS context.
     * Creates a global `bridge` object in JS with the 4 primitives:
     * createView · setProp · addChild · call
     */
    public static native void injectBridge(long ctx, BridgeInterface bridge);

    /**
     * Call a named JS function in the context.
     * Returns the string result.
     */
    public static native Object callFunction(long ctx, String name, Object... args);
}
