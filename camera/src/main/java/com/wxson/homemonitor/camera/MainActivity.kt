package com.wxson.homemonitor.camera

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.res.Configuration
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import com.wxson.homemonitor.commlib.AutoFitTextureView
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity() {

    private val TAG = this.javaClass.simpleName

    private lateinit var viewModel: MainViewModel
    private lateinit var textureView: AutoFitTextureView
    //requestCode
    private val REQUEST_CAMERA_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = viewModel.surfaceTextureListener
        viewModel.bindService()

        // registers observer for information from viewModel
        val localMsgObserver: Observer<String> = Observer { localMsg -> localMsgHandler(localMsg.toString()) }
        viewModel.getLocalMsg().observe(this, localMsgObserver)

        val previewSizeObserver: Observer<Size> = Observer { previewSize -> setPreviewSize(previewSize!!) }
        viewModel.getPreviewSize().observe(this,previewSizeObserver)

//        fab.setOnClickListener { view ->
//            if(IsTcpSocketServiceOn)
//                Snackbar.make(view, "关闭相机监控服务", Snackbar.LENGTH_LONG)
//                    .setAction("确定", View.OnClickListener {
//                        viewModel.cameraIntentService.stopTcpSocketService()
//                    }).show()
//            else
//                Snackbar.make(view, "启动相机监控服务", Snackbar.LENGTH_LONG)
//                    .setAction("确定", View.OnClickListener {
//                        CameraIntentService.startActionTcp(this)
//                    }).show()
//        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showMsg(msg: String){
        Snackbar.make(fab, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun setPreviewSize(previewSize: Size){
        // 根据选中的预览尺寸来调整预览组件（TextureView的）的长宽比
            if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.width, previewSize.height)
            } else {
                textureView.setAspectRatio(previewSize.height, previewSize.width)
            }
    }

    private fun requestCameraPermission() {
        Log.i(TAG, "requestCameraPermission")
        val perms = Manifest.permission.CAMERA
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            Log.i(TAG, "已获取相机权限")
            //打开相机
            viewModel.openCamera()
        } else {
            // Do not have permissions, request them now
            Log.i(TAG, "申请相机权限")
            EasyPermissions.requestPermissions(
                this, getString(R.string.camera_rationale),
                REQUEST_CAMERA_PERMISSION, perms
            )
        }
    }

    private fun localMsgHandler(localMsg: String){
        when (localMsg){
            getString(R.string.to_requestCameraPermission) -> {
                requestCameraPermission()
            }
            else -> {
                showMsg(localMsg)
            }
        }
    }

}
