package com.example.mywebrtcvideocall

import android.app.Application
import com.example.mywebrtcvideocall.models.MessageModel
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.lang.IllegalStateException

class RTCClient(
    private val application: Application,
    private val username: String,
    private val socketRepository: SocketRepository,
    private val observer: PeerConnection.Observer
) {
    init { //whenever this class is made , this method is being called
        initPeerConnectionFactory(application)
    }

    private val eglContext = EglBase.create() // egl은 렌더링 된 그래픽을 출력하여 보여줄 추상 객체
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val iceServer = listOf( //list of peer connection ice/stun server
        PeerConnection.IceServer.builder("stun:iphone-stun.strato-iphone.de:3478").createIceServer() // free stun server
    )
    // this iceserver only works since you're using a local connection , both peer is in the same network
    // so stun server is now okay but if you want to build real application you have to use turn server

    private val peerConnection by lazy { createPeerConnection(observer) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }

    //local video source for startVideo
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }

    // 위 코드는 오디오 관련이라서 필요없듬
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

    private fun initPeerConnectionFactory(application: Application) {
        val peerConnectionOption = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(peerConnectionOption)
    } // initialize peerConnectionFactory

    private fun createPeerConnectionFactory(): PeerConnectionFactory { //WebRTC PeerConnection을 생성하기 위한 Factory
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglContext.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    // activity call 화면의 viewrenderer 세팅 -> your surface view rederer is going to b  ready to
    //preview a stream or show your camera as a local stream
    // Callactivity에서 갖다 쓸 수 있도록 public으로 구성
    public fun initializeSurfaceView(surface: SurfaceViewRenderer) {
        surface.run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(eglContext.eglBaseContext, null)
        }
    }

    fun startLocalVideo(surface: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglContext.eglBaseContext)
        videoCapturer = getVideoCapturer(application) // after that it starts recording
        videoCapturer?.initialize(
            surfaceTextureHelper,
            surface.context, localVideoSource.capturerObserver
        )
        videoCapturer?.startCapture(320, 40, 30)
        //after that create a video track using this video source and pass it to peerConnection
        //create video and audio tracks
        localVideoTrack = peerConnectionFactory.createVideoTrack("local_track", localVideoSource)
        localVideoTrack?.addSink(surface) // surface에 비디오 추가
        localAudioTrack =
            peerConnectionFactory.createAudioTrack("local_track_audio", localAudioSource) // audio관련
        //after that i want to create a stream and pass it to my peerConnection
        val localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack) // audio

        peerConnection?.addStream(localStream) //peerConnection에 localStream추가
    }

    // our video capture from the web RTC SDK that start recording from our camera
    private fun getVideoCapturer(application: Application): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find { // if you find anything
                isFrontFacing(it)
            }?.let { //create capturer and pass null to eventsHandler
                createCapturer(it, null)
            } ?: throw //otherwise throw illegal~
            IllegalStateException()
        }
    }


    //call하면 session desciption을 생성해서 시그널링 서버로 보냄
    fun call(target: String) {
        val mediaConstraints = MediaConstraints()
        // you are offering to receive a video
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceivedVideo",
                "true"
            )
        )
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                //whenever this offer is created, we also add this offer to local description
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        socketRepository?.sendMessageToSocket(
                            MessageModel(
                                "create_offer", username, target, offer
                            )
                        )
                        // send it to signaling server using socket
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                    }

                }, desc)
            }

            override fun onSetSuccess() {

            }

            override fun onCreateFailure(p0: String?) {

            }

            override fun onSetFailure(p0: String?) {

            }

        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, session)
    }

    fun answer(target: String) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReciveVideo", "true"))
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                //add it as local description
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onSetSuccess() {
                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        // on success set, we (callee) want to send a message to caller
                        // message "create_answer", username, target, sessionDescription(answer)
                        socketRepository?.sendMessageToSocket(
                            MessageModel(
                                "create_answer", username, target, answer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                    }

                }, desc)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }
        }, constraints)

    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection?.addIceCandidate(p0)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleCamera(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun endCall() {
        peerConnection?.close()
    }
}