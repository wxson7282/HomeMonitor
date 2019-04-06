package com.wxson.homemonitor.monitor.ui.main

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.wxson.homemonitor.monitor.R
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : Fragment() {

    private val TAG = this.javaClass.simpleName

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.i(TAG, "onActivityCreated")
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        //定义浮动按钮
        fab.setOnClickListener{
                view ->
            if(viewModel.transferStatus == MainViewModel.TransferStatus.OFF)
                Snackbar.make(view, "开始传送", Snackbar.LENGTH_LONG)
                    .setAction("确定", View.OnClickListener{
                        viewModel.sendMsgToServer("Start Video Transfer")
                        viewModel.transferStatus = MainViewModel.TransferStatus.ON
                    }).show()
            else
                Snackbar.make(view, "停止传送", Snackbar.LENGTH_LONG)
                    .setAction("确定", View.OnClickListener{
                        viewModel.sendMsgToServer("Stop Video Transfer")
                        viewModel.transferStatus = MainViewModel.TransferStatus.OFF
                    }).show()
        }

        val serverMsgObserver: Observer<Any> = Observer { serverMsg -> msgShow.text = serverMsg!!.javaClass.simpleName }
        viewModel.getServerMsg().observe(this, serverMsgObserver)

        val localMsgObserver: Observer<String> = Observer { localMsg -> showMsg(localMsg.toString()) }
        viewModel.getLocalMsg().observe(this, localMsgObserver)

    }

    private fun showMsg(msg: String){
        Snackbar.make(fab, msg, Snackbar.LENGTH_LONG).show()
    }
}
