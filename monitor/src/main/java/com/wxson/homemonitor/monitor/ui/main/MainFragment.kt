package com.wxson.homemonitor.monitor.ui.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //        imageConnectStatus = activity!!.findViewById(R.id.imageConnected)
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.i(TAG, "onActivityCreated")
        imageConnectStatus = activity!!.findViewById(R.id.imageConnected)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        //定义浮动按钮
        fab.setOnClickListener{
                view ->
            if (isConnected){

                Snackbar.make(view, "服务器端拍照", Snackbar.LENGTH_SHORT).show()
                viewModel.sendMsgToServer("Capture Still Picture")

//                if (viewModel.transferStatus == MainViewModel.TransferStatus.OFF)
//                    Snackbar.make(view, "开始传送", Snackbar.LENGTH_LONG)
//                        .setAction("确定", View.OnClickListener{
//                            viewModel.sendMsgToServer("Start Video Transfer")
//                            viewModel.transferStatus = MainViewModel.TransferStatus.ON
//                        }).show()
//                else
//                    Snackbar.make(view, "停止传送", Snackbar.LENGTH_LONG)
//                        .setAction("确定", View.OnClickListener{
//                            viewModel.sendMsgToServer("Stop Video Transfer")
//                            viewModel.transferStatus = MainViewModel.TransferStatus.OFF
//                        }).show()
            }
            else {
                showMsg("服务器未连接")
            }
        }

        val serverMsgObserver: Observer<Any> = Observer { serverMsg -> showMsg(serverMsg!!.javaClass.simpleName) }
        viewModel.getServerMsg().observe(this, serverMsgObserver)

        val localMsgObserver: Observer<String> = Observer { localMsg -> localMsgHandler(localMsg.toString()) }
        viewModel.getLocalMsg().observe(this, localMsgObserver)

    }

    private fun showMsg(msg: String){
        Snackbar.make(fab, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun localMsgHandler(localMsg: String){
        when (localMsg){
            "Connected" -> {
                imageConnectStatus.setImageDrawable(resources.getDrawable(R.drawable.ic_connected, null))
                isConnected = true
            }
            "Disconnected" -> {
                imageConnectStatus.setImageDrawable(resources.getDrawable(R.drawable.ic_disconnected, null))
                isConnected = false
            }
            else -> showMsg(localMsg)
        }
    }
}
