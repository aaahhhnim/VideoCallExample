package com.example.mywebrtcvideocall

import android.util.Log
import com.example.mywebrtcvideocall.models.MessageModel
import com.example.mywebrtcvideocall.util.NewMessageInterface
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Exception
import java.net.URI


class SocketRepository(private val messageInterface: NewMessageInterface) {
    private  var webSocket: WebSocketClient?= null
    private var userName:String?=null
    private val TAG = "SocketRepository"
    private val gson = Gson()

    fun initSocket(username:String){
        userName = username
        // if you are using android emulator your local websocket address is "ws://10.0.2.2:3000"
        // if you are using you phone as emulator your local address is going to be : "ws://192.168.56.1:3000"
        // if your websocket is deployed you can add your websocket address here
        webSocket = object:WebSocketClient(URI("ws://10.0.2.2:3000")){
            override fun onOpen(handshakedata: ServerHandshake?) {
                //whenever my socket is open, i want to send a message
                sendMessageToSocket(
                    MessageModel(
                    "store_user",username,null,null
                )
                )
            }

            override fun onMessage(message: String?) {
                //whenever we receive a msg from the server
                // we want to pass it to my NewMessageInterface
                try{
                    messageInterface.onNewMessage(gson.fromJson(message,MessageModel::class.java)) // convert message to message model
                }catch (e:Exception){
                    e.printStackTrace()
                }

            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG,"onClose: $reason")
            }

            override fun onError(ex: Exception?) {
                Log.d(TAG,"onError: $ex")
            }

        }
        // you also need to connect your client to your websocket
        webSocket?.connect()
    }
    // interact with websockets method
    fun sendMessageToSocket(message:MessageModel){
        try{ // send string
            webSocket?.send(Gson().toJson(message))
        }catch (e:Exception){
            e.printStackTrace()
        }
    }
}