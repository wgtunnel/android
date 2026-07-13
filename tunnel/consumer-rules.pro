-keepclassmembers class com.zaneschepke.tunnel.service.VpnService {
    int bypass(int);
}

# JNI callback called from native code via GetStaticMethodID
-keepclassmembers,includedescriptorclasses class com.zaneschepke.tunnel.backend.dns.NativeDnsResolver {
    public static void onResolutionComplete(long, java.lang.String);
}