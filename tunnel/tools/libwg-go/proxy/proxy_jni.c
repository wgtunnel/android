#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <unistd.h>
#include <pthread.h>

static pthread_mutex_t g_protector_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t g_status_mutex = PTHREAD_MUTEX_INITIALIZER;

#define LOG_TAG "AmneziaWG/BypassSocket"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct go_string { const char *str; long n; };

extern int awgStartProxy(struct go_string ifname, struct go_string settings, struct go_string uapipath, int bypass);
extern char *awgGetProxyConfig(int handle);
extern void awgTriggerProxyBindUpdate(int handle);
extern int awgUpdateProxyTunnelPeers(int handle, struct go_string settings);
extern void awgTurnProxyTunnelOff(int handle);

// Global JNI state
static JavaVM *g_jvm = NULL;

// Socket protector
static jobject g_protector = NULL;
static jmethodID g_protectMethod = NULL;

// Status callback
static jobject g_statusCallbackObj = NULL;
static jmethodID g_statusCallbackMethod = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGD("JNI_OnLoad: g_jvm cached = %p", g_jvm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (JNIEnv **)&env, JNI_VERSION_1_6) == JNI_OK) {
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
    pthread_mutex_lock(&g_protector_mutex);
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
    pthread_mutex_unlock(&g_protector_mutex);
}

int bypass_socket(int fd) {
    if (fd < 0) {
        LOGE("bypass_socket: Invalid FD %d", fd);
        return 0;
    }

    JNIEnv *env = NULL;
    if (g_jvm == NULL) {
        LOGE("bypass_socket: g_jvm is NULL");
        return 0;
    }

    jint rs = (*g_jvm)->GetEnv(g_jvm, (JNIEnv **)&env, JNI_VERSION_1_6);

    // Short retry for AttachCurrentThreadAsDaemon
    if (rs == JNI_EDETACHED) {
        int retries = 3;
        while (retries-- > 0) {
            if ((*g_jvm)->AttachCurrentThreadAsDaemon(g_jvm, (JNIEnv **)&env, NULL) == JNI_OK) {
                break;
            }
            usleep(5000);
        }
        if (env == NULL) {
            LOGE("bypass_socket: AttachCurrentThreadAsDaemon failed after retries (fd=%d)", fd);
            return 0;
        }
    } else if (rs != JNI_OK) {
        LOGE("bypass_socket: GetEnv failed with code %d (fd=%d)", rs, fd);
        return 0;
    }

    if (env == NULL) {
        LOGE("bypass_socket: env is NULL after attach/GetEnv (fd=%d)", fd);
        return 0;
    }

    // Short retry when protector is not yet visible
    const int maxRetries = 4;
    const useconds_t backoff = 8000;

    for (int attempt = 0; attempt < maxRetries; attempt++) {
        pthread_mutex_lock(&g_protector_mutex);

        if (g_protector != NULL && g_protectMethod != NULL) {
            jobject local = (*env)->NewLocalRef(env, g_protector);
            jmethodID method = g_protectMethod;
            pthread_mutex_unlock(&g_protector_mutex);

            if (local == NULL) {
                LOGE("bypass_socket: NewLocalRef failed (fd=%d)", fd);
                return 0;
            }

            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);

            int result = (*env)->CallIntMethod(env, local, method, fd);

            if ((*env)->ExceptionCheck(env)) {
                LOGE("bypass_socket: Exception from protector.bypass() (fd=%d)", fd);
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
                result = 0;
            }

            (*env)->DeleteLocalRef(env, local);

            if (attempt > 0) {
                LOGD("bypass_socket: succeeded after %d retries (fd=%d)", attempt, fd);
            }
            LOGD("bypass_socket: fd=%d result=%d", fd, result);
            return result;
        }

        pthread_mutex_unlock(&g_protector_mutex);

        if (attempt == 0) {
            LOGD("bypass_socket: protector not visible yet, retrying (fd=%d)", fd);
        }

        if (attempt < maxRetries - 1) {
            usleep(backoff);
        }
    }

    LOGE("bypass_socket: protector still not ready after retries (fd=%d)", fd);
    return 0;
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
    pthread_mutex_lock(&g_status_mutex);
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
    pthread_mutex_unlock(&g_status_mutex);
}


void awgNotifyStatus(int32_t handle, int32_t code) {
    JNIEnv *env = NULL;
    if (g_jvm == NULL) {
        LOGE("g_jvm is NULL in awgNotifyStatus");
        return;
    }
    jint rs = (*g_jvm)->GetEnv(g_jvm, (JNIEnv **)&env, JNI_VERSION_1_6);
    if (rs == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThreadAsDaemon(g_jvm, (JNIEnv **)&env, NULL) != JNI_OK) {
            LOGE("AttachCurrentThreadAsDaemon failed in awgNotifyStatus");
            return;
        }
    } else if (rs != JNI_OK) {
        LOGE("GetEnv failed with code %d in awgNotifyStatus", rs);
        return;
    }
    if (env == NULL) {
        return;
    }
    pthread_mutex_lock(&g_status_mutex);
    if (g_statusCallbackObj == NULL || g_statusCallbackMethod == NULL) {
        pthread_mutex_unlock(&g_status_mutex);
        return;
    }
    jobject local_callback = (*env)->NewLocalRef(env, g_statusCallbackObj);
    jmethodID local_method = g_statusCallbackMethod;
    pthread_mutex_unlock(&g_status_mutex);
    if (local_callback == NULL) {
        return;
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    (*env)->CallVoidMethod(env, local_callback, local_method, (jint)handle, (jint)code);
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Exception thrown from status callback onStatusChanged()");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, local_callback);
}

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_ProxyBackend_awgTriggerProxyBindUpdate
(JNIEnv *env, jclass clazz, jint handle) {
    awgTriggerProxyBindUpdate(handle);
}