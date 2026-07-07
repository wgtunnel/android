/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright © 2017-2021 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <vpn_jni.h>

struct go_string { const char *str; long n; };
extern int awgTurnOn(struct go_string ifname, int tun_fd, struct go_string settings, struct go_string uapipath);
extern void awgTurnOff(int handle);
extern char *awgGetConfig(int handle);
extern char *awgVersion();
extern void awgTriggerBindUpdate(int handle);
extern int awgUpdateTunnelPeers(int handle, struct go_string settings);

JNIEXPORT jint JNICALL  Java_com_zaneschepke_tunnel_VpnBackend_awgTurnOn(JNIEnv *env, jclass c, jstring ifname, jint tun_fd, jstring settings, jstring uapipath)
{
const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
size_t settings_len = (*env)->GetStringUTFLength(env, settings);
const char *uapipath_str = (*env)->GetStringUTFChars(env, uapipath, 0);
size_t uapipath_len = (*env)->GetStringUTFLength(env, uapipath);
int ret = awgTurnOn((struct go_string){
.str = ifname_str,
.n = ifname_len
}, tun_fd, (struct go_string){
.str = settings_str,
.n = settings_len
}, (struct go_string){
.str = uapipath_str,
.n = uapipath_len
});
(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
(*env)->ReleaseStringUTFChars(env, settings, settings_str);
(*env)->ReleaseStringUTFChars(env, uapipath, uapipath_str);
return ret;
}

JNIEXPORT void JNICALL  Java_com_zaneschepke_tunnel_VpnBackend_awgTurnOff(JNIEnv *env, jclass c, jint handle)
{
awgTurnOff(handle);
}

JNIEXPORT jstring JNICALL  Java_com_zaneschepke_tunnel_VpnBackend_awgGetConfig(JNIEnv *env, jclass c, jint handle)
{
jstring ret;
char *config = awgGetConfig(handle);
if (!config)
return NULL;
ret = (*env)->NewStringUTF(env, config);
free(config);
return ret;
}

JNIEXPORT jstring JNICALL  Java_com_zaneschepke_tunnel_VpnBackend_awgVersion(JNIEnv *env, jclass c)
{
jstring ret;
char *version = awgVersion();
if (!version)
return NULL;
ret = (*env)->NewStringUTF(env, version);
free(version);
return ret;
}

JNIEXPORT jint JNICALL  Java_com_zaneschepke_tunnel_VpnBackend_awgUpdateTunnelPeers(JNIEnv *env, jclass c, jint handle, jstring settings)
{
const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
size_t settings_len = (*env)->GetStringUTFLength(env, settings);
int ret = awgUpdateTunnelPeers(handle, (struct go_string){
.str = settings_str,
.n = settings_len
});
(*env)->ReleaseStringUTFChars(env, settings, settings_str);
return ret;
}

JNIEXPORT void JNICALL Java_com_zaneschepke_tunnel_VpnBackend_awgTriggerBindUpdate
(JNIEnv *env, jclass clazz, jint handle) {
    awgTriggerBindUpdate(handle);
}
