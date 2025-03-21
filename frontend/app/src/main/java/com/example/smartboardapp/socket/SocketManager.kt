package com.example.smartboardapp.socket

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {
    private lateinit var socket: Socket
    private var isConnected = false

    fun connect(onConnect: (() -> Unit)? = null) {
        socket = IO.socket("http://10.0.2.2:5001") // 서버 주소
        socket.on(Socket.EVENT_CONNECT) {
            isConnected = true
            onConnect?.invoke() // 연결 완료 후 콜백 호출
        }
        socket.connect()
    }

    fun on(event: String, listener: (data: Any) -> Unit) {
        socket.on(event) { args ->
            listener(args[0])
        }
    }

    fun emit(event: String, data: JSONObject) {
        if (isConnected) {
            socket.emit(event, data)
        } else {
            throw IllegalStateException("Socket is not connected. Call connect() first.")
        }
    }

    fun disconnect() {
        socket.disconnect()
        isConnected = false
    }
}
