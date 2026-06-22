package com.zaneschepke.pinger

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

class Socks5SocketFactory(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val username: String?,
    private val password: String?,
) : SocketFactory() {

    private val proxyAddress = InetSocketAddress(proxyHost, proxyPort)

    override fun createSocket() = Socket()

    override fun createSocket(host: String?, port: Int): Socket {
        return createSocks5Socket(host ?: throw IOException("Host is required"), port)
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket {
        val socket = createSocks5Socket(host ?: throw IOException("Host is required"), port)
        if (localHost != null) socket.bind(InetSocketAddress(localHost, localPort))
        return socket
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        if (host == null) throw IOException("Host address is required")
        return createSocks5Socket(host.hostAddress, port)
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket {
        if (address == null) throw IOException("Host address is required")
        val socket = createSocks5Socket(address.hostAddress, port)
        if (localAddress != null) socket.bind(InetSocketAddress(localAddress, localPort))
        return socket
    }

    private fun createSocks5Socket(targetHost: String, targetPort: Int): Socket {
        val socket = Socket()
        socket.connect(proxyAddress, 10_000)

        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        // Greeting
        output.writeByte(0x05)
        if (username != null && password != null) {
            output.writeByte(2)
            output.writeByte(0x00)
            output.writeByte(0x02)
        } else {
            output.writeByte(1)
            output.writeByte(0x00)
        }
        output.flush()

        val version = input.readUnsignedByte()
        if (version != 0x05) throw IOException("Bad SOCKS version: $version")
        val method = input.readUnsignedByte()

        if (method == 0x02) {
            if (username == null || password == null)
                throw IOException("SOCKS5 requires credentials")
            output.writeByte(0x01) // auth version
            val u = username.toByteArray(Charsets.UTF_8)
            output.writeByte(u.size)
            output.write(u)
            val p = password.toByteArray(Charsets.UTF_8)
            output.writeByte(p.size)
            output.write(p)
            output.flush()

            val authVer = input.readUnsignedByte()
            if (authVer != 0x01) throw IOException("Bad auth version")
            if (input.readUnsignedByte() != 0x00) throw IOException("SOCKS5 authentication failed")
        } else if (method != 0x00) {
            throw IOException("Unsupported SOCKS method: $method")
        }

        // CONNECT
        output.writeByte(0x05) // version
        output.writeByte(0x01) // CONNECT
        output.writeByte(0x00) // reserved

        val addr = InetAddress.getByName(targetHost)
        when (addr) {
            is java.net.Inet4Address -> {
                output.writeByte(0x01)
                output.write(addr.address)
            }
            is java.net.Inet6Address -> {
                output.writeByte(0x04)
                output.write(addr.address)
            }
            else -> { // domain name
                output.writeByte(0x03)
                val bytes = targetHost.toByteArray(Charsets.UTF_8)
                output.writeByte(bytes.size)
                output.write(bytes)
            }
        }
        output.writeShort(targetPort)
        output.flush()

        // Read reply
        val repVer = input.readUnsignedByte()
        if (repVer != 0x05) throw IOException("Bad reply version")
        val status = input.readUnsignedByte()
        if (status != 0x00) throw IOException("SOCKS5 connect failed: status $status")
        input.skip(1) // reserved
        when (input.readUnsignedByte()) {
            0x01 -> input.skip(4)
            0x04 -> input.skip(16)
            0x03 -> input.skip(input.readUnsignedByte().toLong())
        }
        input.skip(2) // port

        return socket
    }
}
