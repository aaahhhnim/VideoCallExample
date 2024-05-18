package com.example.mywebrtcvideocall.util

import com.example.mywebrtcvideocall.models.MessageModel

interface NewMessageInterface {
    fun onNewMessage(message:MessageModel)
}