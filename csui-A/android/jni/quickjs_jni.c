/*
 * quickjs_jni.c — JNI bridge between QuickJS (C) and Java (Android)
 *
 * Implements the native methods declared in QuickJS.java:
 *   createContext / destroy / eval / injectBridge / callFunction
 *
 * Threading model:
 *   - QuickJS runs on a dedicated JS thread (never the UI thread)
 *   - Java calls from JS functions AttachCurrentThread as needed
 *   - All bridge method calls go through BridgeInterface (Java handles UI threading)
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "quickjs.h"

#define TAG      "CSUA"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define PKG      "com/ctrlscript/csua"

// ── Context wrapper ───────────────────────────────────
// One of these per app — holds the QuickJS runtime + context
// and cached JNI references to the bridge object.

typedef struct {
    JSRuntime  *rt;
    JSContext  *ctx;
    JavaVM     *jvm;

    // Cached refs to BridgeInterface methods
    jobject     bridge;         // global ref to Bootstrap instance
    jmethodID   m_createView;
    jmethodID   m_setProp;
    jmethodID   m_addChild;
    jmethodID   m_call;

    // Cached java.lang.* class refs
    jclass      cls_Object;
    jclass      cls_String;
    jclass      cls_Integer;
    jclass      cls_Double;
    jclass      cls_Boolean;
    jmethodID   m_intValue;
    jmethodID   m_dblValue;
    jmethodID   m_boolValue;
    jmethodID   m_toString;
} CsuaCtx;

static CsuaCtx *_get(jlong handle) {
    return (CsuaCtx *)(intptr_t)handle;
}

// Attach the calling thread to the JVM (safe to call repeatedly)
static JNIEnv *_env(CsuaCtx *c) {
    JNIEnv *env = NULL;
    (*c->jvm)->AttachCurrentThread(c->jvm, &env, NULL);
    return env;
}


// ── Type converters ───────────────────────────────────

// JS value → Java Object (String-based fallback for everything)
static jobject js_to_java(CsuaCtx *c, JNIEnv *env, JSValue v) {
    if (JS_IsNull(v) || JS_IsUndefined(v)) return NULL;

    if (JS_IsBool(v)) {
        jmethodID mid = (*env)->GetStaticMethodID(env, c->cls_Boolean,
            "valueOf", "(Z)Ljava/lang/Boolean;");
        return (*env)->CallStaticObjectMethod(env, c->cls_Boolean, mid,
            (jboolean)JS_ToBool(c->ctx, v));
    }

    if (JS_IsNumber(v)) {
        double d;
        JS_ToFloat64(c->ctx, &d, v);
        // Use Integer if it's a whole number within int32 range, else Double
        if (d >= -2147483648.0 && d <= 2147483647.0 && d == (double)(int32_t)d) {
            jmethodID mid = (*env)->GetStaticMethodID(env, c->cls_Integer,
                "valueOf", "(I)Ljava/lang/Integer;");
            return (*env)->CallStaticObjectMethod(env, c->cls_Integer, mid, (jint)d);
        } else {
            jmethodID mid = (*env)->GetStaticMethodID(env, c->cls_Double,
                "valueOf", "(D)Ljava/lang/Double;");
            return (*env)->CallStaticObjectMethod(env, c->cls_Double, mid, d);
        }
    }

    // Everything else → String
    const char *str = JS_ToCString(c->ctx, v);
    if (!str) return NULL;
    jobject jstr = (*env)->NewStringUTF(env, str);
    JS_FreeCString(c->ctx, str);
    return jstr;
}

// Java Object → JS value
static JSValue java_to_js(CsuaCtx *c, JNIEnv *env, jobject obj) {
    if (obj == NULL) return JS_NULL;

    if ((*env)->IsInstanceOf(env, obj, c->cls_Boolean)) {
        jboolean b = (*env)->CallBooleanMethod(env, obj, c->m_boolValue);
        return JS_NewBool(c->ctx, b);
    }
    if ((*env)->IsInstanceOf(env, obj, c->cls_Integer)) {
        jint i = (*env)->CallIntMethod(env, obj, c->m_intValue);
        return JS_NewInt32(c->ctx, i);
    }
    if ((*env)->IsInstanceOf(env, obj, c->cls_Double)) {
        jdouble d = (*env)->CallDoubleMethod(env, obj, c->m_dblValue);
        return JS_NewFloat64(c->ctx, d);
    }
    if ((*env)->IsInstanceOf(env, obj, c->cls_String)) {
        const char *str = (*env)->GetStringUTFChars(env, (jstring)obj, NULL);
        JSValue v = JS_NewString(c->ctx, str);
        (*env)->ReleaseStringUTFChars(env, (jstring)obj, str);
        return v;
    }
    // Fallback: toString()
    jstring jstr = (jstring)(*env)->CallObjectMethod(env, obj, c->m_toString);
    if (jstr == NULL) return JS_NewString(c->ctx, "[object]");
    const char *str = (*env)->GetStringUTFChars(env, jstr, NULL);
    JSValue v = str ? JS_NewString(c->ctx, str) : JS_NewString(c->ctx, "");
    if (str) (*env)->ReleaseStringUTFChars(env, jstr, str);
    return v;
}


// ── Bridge JS functions ───────────────────────────────
// Each one is a C function registered as a global in the QuickJS context.
// They call back into Java through the BridgeInterface.

// bridge.createView(type) → int
static JSValue js_createView(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv) {
    CsuaCtx *c = JS_GetContextOpaque(ctx);
    if (argc < 1) return JS_NewInt32(ctx, 0);

    JNIEnv *env = _env(c);
    const char *type = JS_ToCString(ctx, argv[0]);
    jstring jtype = (*env)->NewStringUTF(env, type ? type : "LinearLayout");
    JS_FreeCString(ctx, type);

    jint id = (*env)->CallIntMethod(env, c->bridge, c->m_createView, jtype);
    (*env)->DeleteLocalRef(env, jtype);
    return JS_NewInt32(ctx, (int)id);
}

// bridge.setProp(viewId, key, value) → undefined
static JSValue js_setProp(JSContext *ctx, JSValueConst this_val,
                           int argc, JSValueConst *argv) {
    CsuaCtx *c = JS_GetContextOpaque(ctx);
    if (argc < 3) return JS_UNDEFINED;

    JNIEnv *env = _env(c);
    jint id = 0;
    JS_ToInt32(ctx, &id, argv[0]);

    const char *key = JS_ToCString(ctx, argv[1]);
    jstring jkey = (*env)->NewStringUTF(env, key ? key : "");
    JS_FreeCString(ctx, key);

    jobject jval = js_to_java(c, env, argv[2]);
    (*env)->CallVoidMethod(env, c->bridge, c->m_setProp, id, jkey, jval);
    (*env)->DeleteLocalRef(env, jkey);
    if (jval) (*env)->DeleteLocalRef(env, jval);
    return JS_UNDEFINED;
}

// bridge.addChild(parentId, childId) → undefined
static JSValue js_addChild(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv) {
    CsuaCtx *c = JS_GetContextOpaque(ctx);
    if (argc < 2) return JS_UNDEFINED;

    JNIEnv *env = _env(c);
    jint pid = 0, cid = 0;
    JS_ToInt32(ctx, &pid, argv[0]);
    JS_ToInt32(ctx, &cid, argv[1]);
    (*env)->CallVoidMethod(env, c->bridge, c->m_addChild, pid, cid);
    return JS_UNDEFINED;
}

// bridge.call(module, method, arg0, arg1, ...) → Object
static JSValue js_call(JSContext *ctx, JSValueConst this_val,
                        int argc, JSValueConst *argv) {
    CsuaCtx *c = JS_GetContextOpaque(ctx);
    if (argc < 2) return JS_NULL;

    JNIEnv *env = _env(c);

    const char *mod = JS_ToCString(ctx, argv[0]);
    const char *mth = JS_ToCString(ctx, argv[1]);
    jstring jmod = (*env)->NewStringUTF(env, mod ? mod : "");
    jstring jmth = (*env)->NewStringUTF(env, mth ? mth : "");
    JS_FreeCString(ctx, mod);
    JS_FreeCString(ctx, mth);

    // Pack remaining args into Object[]
    int extra = argc - 2;
    jobjectArray jargs = (*env)->NewObjectArray(env, extra, c->cls_Object, NULL);
    for (int i = 0; i < extra; i++) {
        jobject jarg = js_to_java(c, env, argv[2 + i]);
        (*env)->SetObjectArrayElement(env, jargs, i, jarg);
        if (jarg) (*env)->DeleteLocalRef(env, jarg);
    }

    jobject result = (*env)->CallObjectMethod(env, c->bridge, c->m_call,
                                              jmod, jmth, jargs);
    (*env)->DeleteLocalRef(env, jmod);
    (*env)->DeleteLocalRef(env, jmth);
    (*env)->DeleteLocalRef(env, jargs);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return JS_NULL;
    }

    JSValue ret = java_to_js(c, env, result);
    if (result) (*env)->DeleteLocalRef(env, result);
    return ret;
}


// ── Cache Java class/method refs ─────────────────────

static void _cache_refs(CsuaCtx *c, JNIEnv *env) {
    // java.lang.*
    jclass tmp;
    tmp = (*env)->FindClass(env, "java/lang/Object");
    c->cls_Object  = (*env)->NewGlobalRef(env, tmp);
    tmp = (*env)->FindClass(env, "java/lang/String");
    c->cls_String  = (*env)->NewGlobalRef(env, tmp);
    tmp = (*env)->FindClass(env, "java/lang/Integer");
    c->cls_Integer = (*env)->NewGlobalRef(env, tmp);
    tmp = (*env)->FindClass(env, "java/lang/Double");
    c->cls_Double  = (*env)->NewGlobalRef(env, tmp);
    tmp = (*env)->FindClass(env, "java/lang/Boolean");
    c->cls_Boolean = (*env)->NewGlobalRef(env, tmp);

    c->m_intValue  = (*env)->GetMethodID(env, c->cls_Integer, "intValue",     "()I");
    c->m_dblValue  = (*env)->GetMethodID(env, c->cls_Double,  "doubleValue",  "()D");
    c->m_boolValue = (*env)->GetMethodID(env, c->cls_Boolean, "booleanValue", "()Z");
    c->m_toString  = (*env)->GetMethodID(env, c->cls_Object,  "toString",     "()Ljava/lang/String;");

    // BridgeInterface methods on Bootstrap
    jclass bridgeCls = (*env)->FindClass(env, PKG "/BridgeInterface");
    if (!bridgeCls) { LOGE("BridgeInterface class not found"); return; }
    c->m_createView = (*env)->GetMethodID(env, bridgeCls, "createView",
        "(Ljava/lang/String;)I");
    c->m_setProp    = (*env)->GetMethodID(env, bridgeCls, "setProp",
        "(ILjava/lang/String;Ljava/lang/Object;)V");
    c->m_addChild   = (*env)->GetMethodID(env, bridgeCls, "addChild",
        "(II)V");
    c->m_call       = (*env)->GetMethodID(env, bridgeCls, "call",
        "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
}


// ── JNI exported functions ────────────────────────────

// QuickJS.createContext()
JNIEXPORT jlong JNICALL
Java_com_ctrlscript_csua_QuickJS_createContext(JNIEnv *env, jclass cls) {
    CsuaCtx *c = calloc(1, sizeof(CsuaCtx));
    (*env)->GetJavaVM(env, &c->jvm);

    c->rt  = JS_NewRuntime();
    JS_SetMaxStackSize(c->rt, 8 * 1024 * 1024); // 8 MB
    c->ctx = JS_NewContext(c->rt);
    JS_SetContextOpaque(c->ctx, c);

    _cache_refs(c, env);
    LOGD("QuickJS context created");
    return (jlong)(intptr_t)c;
}

// QuickJS.destroy(ctx)
JNIEXPORT void JNICALL
Java_com_ctrlscript_csua_QuickJS_destroy(JNIEnv *env, jclass cls, jlong handle) {
    CsuaCtx *c = _get(handle);
    if (!c) return;

    if (c->bridge)     (*env)->DeleteGlobalRef(env, c->bridge);
    if (c->cls_Object) (*env)->DeleteGlobalRef(env, c->cls_Object);
    if (c->cls_String) (*env)->DeleteGlobalRef(env, c->cls_String);
    if (c->cls_Integer)(*env)->DeleteGlobalRef(env, c->cls_Integer);
    if (c->cls_Double) (*env)->DeleteGlobalRef(env, c->cls_Double);
    if (c->cls_Boolean)(*env)->DeleteGlobalRef(env, c->cls_Boolean);

    JS_FreeContext(c->ctx);
    JS_FreeRuntime(c->rt);
    free(c);
    LOGD("QuickJS context destroyed");
}

// QuickJS.eval(ctx, source, filename)
JNIEXPORT jobject JNICALL
Java_com_ctrlscript_csua_QuickJS_eval(JNIEnv *env, jclass cls,
                                       jlong handle, jstring jsource, jstring jfilename) {
    CsuaCtx *c = _get(handle);
    const char *src  = (*env)->GetStringUTFChars(env, jsource,   NULL);
    const char *file = (*env)->GetStringUTFChars(env, jfilename, NULL);

    JSValue result = JS_Eval(c->ctx, src, strlen(src), file,
                             JS_EVAL_TYPE_GLOBAL);

    (*env)->ReleaseStringUTFChars(env, jsource,   src);
    (*env)->ReleaseStringUTFChars(env, jfilename, file);

    jobject ret = NULL;
    if (JS_IsException(result)) {
        JSValue exc = JS_GetException(c->ctx);
        JSValue stack = JS_GetPropertyStr(c->ctx, exc, "stack");
        const char *msg   = JS_ToCString(c->ctx, exc);
        const char *trace = JS_ToCString(c->ctx, stack);
        LOGE("JS Error: %s\n%s", msg ? msg : "unknown", trace ? trace : "");
        JS_FreeCString(c->ctx, msg);
        JS_FreeCString(c->ctx, trace);
        JS_FreeValue(c->ctx, stack);
        JS_FreeValue(c->ctx, exc);
    } else if (!JS_IsUndefined(result)) {
        const char *str = JS_ToCString(c->ctx, result);
        if (str) { ret = (*env)->NewStringUTF(env, str); JS_FreeCString(c->ctx, str); }
    }

    JS_FreeValue(c->ctx, result);
    return ret;
}

// QuickJS.injectBridge(ctx, bridge)
// Injects a `bridge` global object into QuickJS with the 4 primitive methods.
JNIEXPORT void JNICALL
Java_com_ctrlscript_csua_QuickJS_injectBridge(JNIEnv *env, jclass cls,
                                               jlong handle, jobject bridgeObj) {
    CsuaCtx *c = _get(handle);
    c->bridge = (*env)->NewGlobalRef(env, bridgeObj);

    JSValue global = JS_GetGlobalObject(c->ctx);
    JSValue bridge = JS_NewObject(c->ctx);

    JS_SetPropertyStr(c->ctx, bridge, "createView",
        JS_NewCFunction(c->ctx, js_createView, "createView", 1));
    JS_SetPropertyStr(c->ctx, bridge, "setProp",
        JS_NewCFunction(c->ctx, js_setProp, "setProp", 3));
    JS_SetPropertyStr(c->ctx, bridge, "addChild",
        JS_NewCFunction(c->ctx, js_addChild, "addChild", 2));
    JS_SetPropertyStr(c->ctx, bridge, "call",
        JS_NewCFunction(c->ctx, js_call, "call", 2));

    JS_SetPropertyStr(c->ctx, global, "bridge", bridge);
    JS_FreeValue(c->ctx, global);
    LOGD("Bridge injected into QuickJS");
}

// QuickJS.callFunction(ctx, name, args)
JNIEXPORT jobject JNICALL
Java_com_ctrlscript_csua_QuickJS_callFunction(JNIEnv *env, jclass cls,
                                               jlong handle, jstring jname,
                                               jobjectArray jargs) {
    CsuaCtx *c = _get(handle);
    const char *name = (*env)->GetStringUTFChars(env, jname, NULL);

    JSValue global = JS_GetGlobalObject(c->ctx);
    JSValue fn     = JS_GetPropertyStr(c->ctx, global, name);
    JS_FreeValue(c->ctx, global);
    (*env)->ReleaseStringUTFChars(env, jname, name);

    if (!JS_IsFunction(c->ctx, fn)) {
        JS_FreeValue(c->ctx, fn);
        return NULL;
    }

    int argc = jargs ? (*env)->GetArrayLength(env, jargs) : 0;
    JSValue *argv = NULL;
    if (argc > 0) {
        argv = malloc(sizeof(JSValue) * argc);
        if (!argv) { JS_FreeValue(c->ctx, fn); return NULL; }
    }
    for (int i = 0; i < argc; i++) {
        jobject jarg = (*env)->GetObjectArrayElement(env, jargs, i);
        argv[i] = java_to_js(c, env, jarg);
        if (jarg) (*env)->DeleteLocalRef(env, jarg);
    }

    JSValue result = JS_Call(c->ctx, fn, JS_UNDEFINED, argc, argv);
    JS_FreeValue(c->ctx, fn);
    for (int i = 0; i < argc; i++) JS_FreeValue(c->ctx, argv[i]);
    free(argv);

    jobject ret = NULL;
    if (!JS_IsException(result) && !JS_IsUndefined(result)) {
        const char *str = JS_ToCString(c->ctx, result);
        if (str) { ret = (*env)->NewStringUTF(env, str); JS_FreeCString(c->ctx, str); }
    }
    JS_FreeValue(c->ctx, result);
    return ret;
}
