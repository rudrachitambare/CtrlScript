package com.ctrlscript.csua;

/** Base interface every lazy module implements. */
public interface CsuaModule {
    Object call(String method, Object... args);
}
