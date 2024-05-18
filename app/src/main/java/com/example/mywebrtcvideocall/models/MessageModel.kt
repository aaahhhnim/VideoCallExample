package com.example.mywebrtcvideocall.models

//create the model that i want to interact with my server
data class MessageModel(
    val type:String,
    val name:String?=null,
    val target:String?=null,
    val data:Any?=null
)
