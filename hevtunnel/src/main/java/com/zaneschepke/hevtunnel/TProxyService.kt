package com.zaneschepke.hevtunnel

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TProxyService {
    private const val HEV_CONFIG_FILE_NAME: String = "tproxy.conf"
    private const val TASK_STACK_SIZE = 24576

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic external fun TProxyStartService(config_path: String?, fd: Int)

    @JvmStatic external fun TProxyStopService()

    @JvmStatic external fun TProxyGetStats(): LongArray?

    @Throws(IOException::class)
    fun createHevTunnelConfig(config: HevTunnelConfig, context: Context): File {
        val tproxyFile = File(context.cacheDir, HEV_CONFIG_FILE_NAME)

        val hevConf =
            """
        misc:
          task-stack-size: $TASK_STACK_SIZE
        tunnel:
          mtu: ${config.mtu}
          ipv4: '${config.ipv4}'
          ipv6: '${config.ipv6}'
        socks5:
          address: '${config.address}'
          port: ${config.port}
          username: '${config.username}'
          password: '${config.password}'
          udp: 'udp'
    """
                .trimIndent()

        FileOutputStream(tproxyFile, false).use { fos -> fos.write(hevConf.toByteArray()) }

        return tproxyFile
    }
}
