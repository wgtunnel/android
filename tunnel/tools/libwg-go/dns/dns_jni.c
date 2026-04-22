#include <jni.h>

struct go_string { const char *str; long n; };

extern void SetDNSConfig(struct go_string handle);

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