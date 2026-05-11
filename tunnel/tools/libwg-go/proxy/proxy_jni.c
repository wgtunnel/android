#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <unistd.h>

#define LOG_TAG "AmneziaWG/BypassSocket"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

struct go_string { const char *str; long n; };

extern int awgStartProxy(struct go_string ifname, struct go_string settings, struct go_string uapipath, int bypass);
extern void awgStopProxy();
extern char *awgGetProxyConfig(int handle);
extern int awgUpdateProxyTunnelPeers(int handle, struct go_string settings);
extern void awgTurnProxyTunnelOff(int handle);

// Global JNI state
static JavaVM *g_jvm = NULL;

// Socket protector
static jobject g_protector = NULL;
static jmethodID g_protectMethod = NULL;

// Status callback (nullable)
static jobject g_statusCallbackObj = NULL;
static jmethodID g_statusCallbackMethod = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGD("JNI_OnLoad: g_jvm cached = %p", g_jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        if (g_protector != NULL) {
            (*env)->DeleteGlobalRef(env, g_protector);
            g_protector = NULL;
        }
        if (g_statusCallbackObj != NULL) {
            (*env)->DeleteGlobalRef(env, g_statusCallbackObj);
            g_statusCallbackObj = NULL;
        }
    }
    g_protectMethod = NULL;
    g_statusCallbackMethod = NULL;
    g_jvm = NULL;
    LOGD("JNI_OnUnload: cleared all globals");
}

JNIEXPORT jint JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgStartProxy(JNIEnv *env, jclass c, jstring ifname, jstring settings, jstring uapipath, jint bypass)
{
    const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
    size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
    const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
    size_t settings_len = (*env)->GetStringUTFLength(env, settings);
    const char *uapipath_str = (*env)->GetStringUTFChars(env, uapipath, 0);
        size_t uapipath_len = (*env)->GetStringUTFLength(env, uapipath);
    int ret = awgStartProxy((struct go_string){
        .str = ifname_str,
        .n = ifname_len
    }, (struct go_string){
        .str = settings_str,
        .n = settings_len
    }, (struct go_string){
            .str = uapipath_str,
            .n = uapipath_len
        },bypass);
    (*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
    (*env)->ReleaseStringUTFChars(env, settings, settings_str);
    (*env)->ReleaseStringUTFChars(env, uapipath, uapipath_str);
    return ret;
}

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgStopProxy(JNIEnv *env, jclass c)
{
    awgStopProxy();
}

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgTurnProxyTunnelOff(JNIEnv *env, jclass c, jint handle)
{
    awgTurnProxyTunnelOff(handle);
}

JNIEXPORT jstring JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgGetProxyConfig(JNIEnv *env, jclass c, jint handle)
{
    jstring ret;
    char *config = awgGetProxyConfig(handle);
    if (!config)
        return NULL;
    ret = (*env)->NewStringUTF(env, config);
    free(config);
    return ret;
}

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgSetSocketProtector(
        JNIEnv *env, jclass c, jobject protector) {

    LOGD("JNI: awgSetSocketProtector called from Kotlin - protector=%p", protector);

    // Clear old protector
    if (g_protector != NULL) {
        (*env)->DeleteGlobalRef(env, g_protector);
        g_protector = NULL;
        g_protectMethod = NULL;
        LOGD("JNI: Cleared previous socket protector");
    }

    if (protector != NULL) {
        g_protector = (*env)->NewGlobalRef(env, protector);
        LOGD("JNI: Created new global ref for protector = %p", g_protector);

        jclass protectorClass = (*env)->GetObjectClass(env, protector);
        if (protectorClass != NULL) {
            g_protectMethod = (*env)->GetMethodID(env, protectorClass, "bypass", "(I)I");
            (*env)->DeleteLocalRef(env, protectorClass);
        }

        if (g_protectMethod != NULL) {
            LOGD("JNI: Socket protector SUCCESSFULLY REGISTERED (methodID = %p)", g_protectMethod);
        } else {
            LOGE("JNI: FAILED to get bypass method ID");
        }
    } else {
        LOGD("JNI: Socket protector CLEARED (null passed)");
    }
}

int bypass_socket(int fd) {
    if (fd < 0) {
        LOGE("Invalid FD passed to bypass_socket: %d", fd);
        return 0;  // Fail early on bad FD
    }

    JNIEnv *env = NULL;
    jboolean attached = JNI_FALSE;
    jint rs = -1;

    LOGD("bypass_socket called with FD: %d", fd);

    if (g_jvm == NULL) {
        LOGE("g_jvm is NULL - not initialized in JNI_OnLoad?");
        return 0;
    }

    rs = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    LOGD("GetEnv returned: %d (env=%p)", rs, env);

    if (rs == JNI_EDETACHED) {
        LOGD("Thread detached, attempting AttachCurrentThread");
        int retries = 3;
        while (retries-- > 0 && (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            usleep(10000); // 10ms backoff
        }
        if (retries < 0) {
            LOGE("AttachCurrentThread failed after retries");
            return 0;
        }
        attached = JNI_TRUE;
        LOGD("Attached successfully, env=%p", env);
    } else if (rs != JNI_OK) {
        LOGE("GetEnv failed with %d (not OK or detached)", rs);
        return 0;
    } else {
        LOGD("Thread already attached, env=%p", env);
    }

    if (env == NULL) {
        LOGE("Env is NULL after attachment/GetEnv");
        if (attached) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
        }
        return 0;
    }

    if (g_protector == NULL) {
        LOGE("g_protector is NULL - VpnService ref not set?");
        if (attached) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
        }
        return 0;
    }
    LOGD("g_protector ref valid: %p", g_protector);

    if (g_protectMethod == NULL) {
        LOGE("g_protectMethod is NULL - method ID not cached?");
        if (attached) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
        }
        return 0;
    }
    LOGD("g_protectMethod valid");

    // Clear any pending exceptions before call
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Pending exception before CallIntMethod - clearing");
        (*env)->ExceptionClear(env);
    }

    int result = (*env)->CallIntMethod(env, g_protector, g_protectMethod, fd);
    LOGD("CallIntMethod returned: %d", result);

    // Check for exceptions after call
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Exception thrown from CallIntMethod - describing");
        (*env)->ExceptionDescribe(env);  // Logs the exception to logcat
        (*env)->ExceptionClear(env);
        result = 0;  // Fail on exception
    }

    if (attached) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
        LOGD("Detached thread");
    }

    LOGD("bypass_socket returning: %d for FD %d", result, fd);
    return result;
}

JNIEXPORT jint JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgUpdateProxyTunnelPeers(JNIEnv *env, jclass c, jint handle, jstring settings)
{
    const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
    size_t settings_len = (*env)->GetStringUTFLength(env, settings);
    int ret = awgUpdateProxyTunnelPeers(handle, (struct go_string){
        .str = settings_str,
        .n = settings_len
    });
    (*env)->ReleaseStringUTFChars(env, settings, settings_str);
    return ret;
}

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_VpnBackend_awgSetStatusCallback(
        JNIEnv *env, jclass clazz, jobject callback) {
    LOGD("JNI: awgSetStatusCallback called - callback=%p", callback);

    if (g_statusCallbackObj != NULL) {
        (*env)->DeleteGlobalRef(env, g_statusCallbackObj);
        g_statusCallbackObj = NULL;
        g_statusCallbackMethod = NULL;
    }

    if (callback != NULL) {
        g_statusCallbackObj = (*env)->NewGlobalRef(env, callback);
        jclass callbackClass = (*env)->GetObjectClass(env, callback);
        if (callbackClass != NULL) {
            // UPDATED signature: (II)V  → only handle + statusCode
            g_statusCallbackMethod = (*env)->GetMethodID(env, callbackClass,
                "onStatusChanged", "(II)V");
            (*env)->DeleteLocalRef(env, callbackClass);
        }
        if (g_statusCallbackMethod != NULL) {
            LOGD("JNI: Status callback SUCCESSFULLY REGISTERED (2-param)");
        } else {
            LOGE("JNI: FAILED to get onStatusChanged method ID");
        }
    } else {
        LOGD("JNI: Status callback CLEARED");
    }
}


/* Helper that both VPN and Proxy Go code will call (modelled exactly after your bypass_socket) */
void awgNotifyStatus(int32_t handle, int32_t code) {
    JNIEnv *env = NULL;
    jboolean attached = JNI_FALSE;

    if (g_jvm == NULL || g_statusCallbackObj == NULL || g_statusCallbackMethod == NULL) {
        LOGW("JNI: awgNotifyStatus called but no callback registered");
        return;
    }

    jint rs = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    if (rs == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("JNI: AttachCurrentThread failed");
            return;
        }
        attached = JNI_TRUE;
    } else if (rs != JNI_OK) {
        return;
    }

    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);

    (*env)->CallVoidMethod(env, g_statusCallbackObj, g_statusCallbackMethod,
            (jint)handle, (jint)code);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    if (attached) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
}