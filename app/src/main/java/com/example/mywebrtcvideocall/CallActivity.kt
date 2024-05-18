package com.example.mywebrtcvideocall

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mywebrtcvideocall.databinding.ActivityCallBinding
import com.example.mywebrtcvideocall.models.MessageModel
import com.example.mywebrtcvideocall.util.NewMessageInterface
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

class CallActivity : AppCompatActivity(), NewMessageInterface {
    lateinit var binding : ActivityCallBinding
    private var userName : String?= null
    private var socketRepository:SocketRepository?= null
    private var rtcClient : RTCClient?=null
    private val TAG = "CallActivity"
    private var target:String = ""
    private val gson = Gson()
    private var isMute = true //for mute event // originally false, but i changed it to start mute
    private var isCameraPause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }
    private fun init(){
        userName = intent.getStringExtra("username")
        socketRepository = SocketRepository(this)
        userName?.let{socketRepository?.initSocket(it)} //null check
        //initialize rtcClient
        rtcClient = RTCClient(application,userName!!,socketRepository!!,object : PeerConnectionObserver(){
            override fun onIceCandidate(p0: IceCandidate?) {
                //whenever an ice candidate is generated
                // i want to add that on my peerconnection and send it to opponent
                super.onIceCandidate(p0)
                rtcClient?.addIceCandidate(p0)
                //여기선 opponent에 대한 정보가 없으므로 target 변수 생성해서 55,107 line에서 받아옴
                val candidate = hashMapOf(
                    "sdpMid" to p0?.sdpMid,
                    "sdpMLineIndex" to p0?.sdpMLineIndex,
                    "sdpCandidate" to p0?.sdp
                )
                socketRepository?.sendMessageToSocket( // send ice candidate to my opponent
                    MessageModel("ice_candidate",userName,target,candidate)
                )
            }


            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                //whenever a stream is added to your peerConnection,
                // you want to show it in surfaceViewRender
                // get the first element and addsink in binding.remoteiew
                //p0?.videoTracks?.get(0)?.addSink(binding.remoteView)
                p0?.videoTracks?.get(0)?.addSink(binding.remoteView)
            }
        })


        binding.apply{  //아마 한번에 하려고
            callBtn.setOnClickListener{ //callBtn 누르면 server에 메세지 전송
                socketRepository?.sendMessageToSocket(MessageModel(
                    type="start_call",userName,targetUserNameEt.text.toString(),null
                ))
                target = targetUserNameEt.text.toString()

            }
            switchCameraButton.setOnClickListener {
                rtcClient?.switchCamera()
            }
            micButton.setOnClickListener{
                if (isMute){
                    isMute = false
                    micButton.setImageResource(R.drawable.ic_baseline_mic_off) // should be changed
                } else{
                    isMute = true
                    micButton.setImageResource(R.drawable.ic_baseline_mic) // should be changed
                }
                rtcClient?.toggleAudio(isMute)

            }
            videoButton.setOnClickListener{
                if (isCameraPause){
                    isCameraPause = false
                    videoButton.setImageResource(R.drawable.ic_videocam_off) // should be changed
                } else{
                    isCameraPause = true
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam)
                }
                rtcClient?.toggleCamera(isCameraPause)

            }
            audioOutputButton.setOnClickListener {
                // skip this button for speaker mode
            }
            endCallButton.setOnClickListener {
                setCallLayoutGone()
                setIncomingCallLayoutVisible()
                setIncomingCallLayoutGone()
                rtcClient?.endCall()
            }
        }
    }

    override fun onNewMessage(message: MessageModel) {
        //everytime a new message is coming from socket repository
        Log.d(TAG,"onNewMessage: $message")
        when(message.type){
            "call_response"->{
                if(message.data == "user is not online"){
                    // user is not reachable
                    runOnUiThread{
                        Toast.makeText(this,"user is not reachable",Toast.LENGTH_LONG).show()
                    }
                } else{
                    // we are ready to call -> so we start a call
                    // ->  call button 이 있는 layout은 gone 처리하고 CallLayout이 visible
                    runOnUiThread{
                        setWhoToCallLayoutGone()
                        setCallLayoutVisible()
                        binding.apply {
                            rtcClient?.initializeSurfaceView(localView)
                            rtcClient?.initializeSurfaceView(remoteView)
                            rtcClient?.startLocalVideo(localView)
                            rtcClient?.call(targetUserNameEt.text.toString())
                        }
                    }
                }
            }
            "answer_received" -> {
                val session = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    message.data.toString()
                )
                // whenever we received session description as type of answer_received,
                // we want to set it in my rtcClient or my peerConnection as remote description
                rtcClient?.onRemoteSessionReceived(session)
                binding.remoteViewLoading.visibility = View.GONE
            }
            "offer_received"->{
                runOnUiThread {
                    setIncomingCallLayoutVisible()
                    binding.incomingNameTV.text = "${message.name.toString()} is calling you"
                    binding.acceptButton.setOnClickListener{
                        setIncomingCallLayoutGone()
                        setCallLayoutVisible()
                        setWhoToCallLayoutGone()
                        //initialize my surface view renderer
                        binding.apply{
                            rtcClient?.initializeSurfaceView(localView)
                            rtcClient?.initializeSurfaceView(remoteView)
                            rtcClient?.startLocalVideo(localView)
                        }
                        val session = SessionDescription(
                            SessionDescription.Type.OFFER,
                            message.data.toString()
                        )
                        rtcClient?.onRemoteSessionReceived(session)
                        // we set our session as a remote session so we are ready to create a answer that called me
                        rtcClient?.answer(message.name!!)
                        target = message.name!!
                    }
                    binding.rejectButton.setOnClickListener{
                        setIncomingCallLayoutGone()
                    }
                    binding.remoteViewLoading.visibility = View.GONE
                }
            }
            "ice_candidate"->{
                runOnUiThread{
                    try {
                        //convert my simple data from string to ice candidate model
                        val receivingCandidate = gson.fromJson(gson.toJson(message.data),IceCandidateModel::class.java)
                        //add ice candidate peerconnection
                        rtcClient?.addIceCandidate(IceCandidate(receivingCandidate.sdpMid,
                            Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),
                            receivingCandidate.sdpCandidate))
                    }catch (e:Exception){
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    private fun setIncomingCallLayoutGone(){
        binding.incomingCallLayout.visibility = View.GONE
    }
    private fun setIncomingCallLayoutVisible() {
        binding.incomingCallLayout.visibility = View.VISIBLE
    }

    private fun setCallLayoutGone() {
        binding.callLayout.visibility = View.GONE
    }

    private fun setCallLayoutVisible() {
        binding.callLayout.visibility = View.VISIBLE
    }

    private fun setWhoToCallLayoutGone() {
        binding.whoToCallLayout.visibility = View.GONE
    }

    private fun setWhoToCallLayoutVisible() {
        binding.whoToCallLayout.visibility = View.VISIBLE
    }
}