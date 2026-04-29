#include <jni.h>
#include <stdlib.h>

struct go_string { const char *str; long n; };

extern void SetDNSConfig(struct go_string handle);
extern char* ResolveBootstrap(const char* host, int bypass);

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_DnsConfigManager_setDNSConfig(
        JNIEnv* env, jclass clazz, jstring json)
{
    if (json == NULL) {
        return;
    }

    const char* cjson = (*env)->GetStringUTFChars(env, json, 0);
    if (cjson != NULL) {
        size_t len = (*env)->GetStringUTFLength(env, json);

        SetDNSConfig((struct go_string){
            .str = cjson,
            .n = (long)len
        });

        (*env)->ReleaseStringUTFChars(env, json, cjson);
    }
}

JNIEXPORT jstring JNICALL
Java_com_zaneschepke_tunnel_DnsConfigManager_resolveBootstrap(
        JNIEnv* env,
        jclass clazz,
        jstring host,
        jboolean bypass)
{
    if (host == NULL) {
        return (*env)->NewStringUTF(env, "{\"error\":\"invalid host\"}");
    }

    const char* chost = (*env)->GetStringUTFChars(env, host, NULL);
    if (chost == NULL) {
        return (*env)->NewStringUTF(env, "{\"error\":\"out of memory\"}");
    }

    char* resultC = ResolveBootstrap(
        (char*)chost,
        bypass ? 1 : 0
    );

    (*env)->ReleaseStringUTFChars(env, host, chost);

    if (resultC == NULL) {
        return (*env)->NewStringUTF(env, "{\"error\":\"null response\"}");
    }

    jstring jresult = (*env)->NewStringUTF(env, resultC);
    free(resultC);
    return jresult;
}