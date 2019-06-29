package com.wxson.homemonitor.monitor.ui.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import com.wxson.homemonitor.monitor.R
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : androidx.fragment.app.Fragment() {

    private val TAG = this.javaClass.simpleName

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var imageConnectStatus: ImageView
    private var isConnected = false
    private var isTransmitOn = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.i(TAG, "onActivityCreated")
        imageConnectStatus = activity!!.findViewById(R.id.imageConnected)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        fab_capture.hide()
        fab_transmit.hide()
        //定义拍照浮动按钮
        fab_capture.setOnClickListener{
                view ->
            if (isConnected){

                Snackbar.make(view, "服务器端拍照", Snackbar.LENGTH_SHORT).show()
                viewModel.sendMsgToServer("Capture Still Picture")
            }
            else {
                showMsg("服务器未连接")
            }
        }
        //定义视频传送浮动按钮
        fab_transmit.setOnClickListener{
            view ->
            if (isTransmitOn){
                viewModel.sendMsgToServer("Stop Video Transmit")    // notify server to stop video transmit
                Snackbar.make(view, "暂停视频传送", Snackbar.LENGTH_SHORT).show()
                fab_transmit.backgroundTintList = ContextCompat.getColorStateList(this.activity!!.baseContext, R.color.button_light)
                isTransmitOn = false
                viewModel.setHeartBeat(true)
            }
            else{
                viewModel.sendMsgToServer("Start Video Transmit")    // notify server to start video transmit
                Snackbar.make(view, "开始视频传送", Snackbar.LENGTH_SHORT).show()
                fab_transmit.backgroundTintList = ContextCompat.getColorStateList(this.activity!!.baseContext, R.color.colorAccent)
                isTransmitOn = true
                viewModel.setHeartBeat(false)
            }
        }

        val serverMsgObserver: Observer<Any> = Observer { serverMsg -> showMsg(serverMsg!!.javaClass.simpleName) }
        viewModel.getServerMsg().observe(this, serverMsgObserver)

        val localMsgObserver: Observer<String> = Observer { localMsg -> localMsgHandler(localMsg.toString()) }
        viewModel.getLocalMsg().observe(this, localMsgObserver)

    }

    private fun showMsg(msg: String){
        Snackbar.make(fab_capture, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun localMsgHandler(localMsg: String){
        when (localMsg){
            "Connected" -> {
                imageConnectStatus.setImageDrawable(resources.getDrawable(R.drawable.ic_connected, null))
                isConnected = true
                fab_capture.show()
                fab_transmit.show()
            }
            "Disconnected" -> {
                imageConnectStatus.setImageDrawable(resources.getDrawable(R.drawable.ic_disconnected, null))
                isConnected = false
                fab_capture.hide()
                fab_transmit.hide()
            }
            else -> showMsg(localMsg)
        }
    }
}
