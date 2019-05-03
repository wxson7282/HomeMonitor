package com.wxson.homemonitor.camera

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import com.wxson.homemonitor.commlib.AutoFitTextureView
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private val TAG = this.javaClass.simpleName

    private lateinit var viewModel: MainViewModel
    private lateinit var textureView: AutoFitTextureView
    private lateinit var imageConnectStatus: ImageView
    //requestCode
    private val REQUEST_CAMERA_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        imageConnectStatus = findViewById(R.id.imageConnected)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = viewModel.surfaceTextureListener
        viewModel.rotation = this.windowManager.defaultDisplay.rotation

        // registers observer for information from viewModel
        val localMsgObserver: Observer<String> = Observer { localMsg -> showMsg(localMsg.toString()) }
        viewModel.getLocalMsg().observe(this, localMsgObserver)

        val previewSizeObserver: Observer<Size> = Observer { previewSize -> setPreviewSize(previewSize!!) }
        viewModel.getPreviewSize().observe(this,previewSizeObserver)

        val connectStatusObserver: Observer<Boolean> = Observer { isConnected -> connectStatusHandler(isConnected!!) }
        viewModel.getClientConnected().observe(this, connectStatusObserver)

        val surfaceTextureStatusObserver: Observer<String> = Observer { msg -> surfaceTextureStatusHandler(msg.toString()) }
        viewModel.getSurfaceTextureStatus().observe(this, surfaceTextureStatusObserver)

//        //首次运行时设置默认值
//        PreferenceManager.setDefaultValues(this, R.xml.pref_codec, false)

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
//        viewModel.bindService()
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
        val returnValue: Boolean
        returnValue = when (item.itemId){
            R.id.action_settings ->{
                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
        return returnValue
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionsResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.i(TAG, "onPermissionsDenied")
        Log.i(TAG, "获取权限失败，退出当前页面$perms")
        showMsg("获取权限失败")
        this.finish()  //退出当前页面
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.i(TAG, "onPermissionsGranted")
        Log.i(TAG, "获取权限成功$perms")
        showMsg("获取权限成功")
        //打开相机
        viewModel.openCamera()
    }

    private fun surfaceTextureStatusHandler(msg: String){
        when (msg){
            "onSurfaceTextureAvailable" -> requestCameraPermission()
            else -> {}
        }
    }

    private fun connectStatusHandler(isConnected: Boolean){
        if (isConnected){
            imageConnectStatus.setImageDrawable(getDrawable(R.drawable.ic_connected))
        }
        else{
            imageConnectStatus.setImageDrawable(getDrawable(R.drawable.ic_disconnected))
        }
    }

}
