package com.wxson.homemonitor.camera

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import pub.devrel.easypermissions.EasyPermissions


class CameraActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private val TAG = this.javaClass.simpleName

    private lateinit var viewModel: CameraViewModel
    private lateinit var surfaceView: CameraBridgeViewBase
    private lateinit var imageConnectStatus: ImageView
    //requestCode
    private val REQUEST_CAMERA_PERMISSION = 1

    private val loaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                    surfaceView.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_main)
        setSupportActionBar(toolbar)

        imageConnectStatus = findViewById(R.id.imageConnected)

        viewModel = ViewModelProviders.of(this).get(CameraViewModel::class.java)
        surfaceView = findViewById(R.id.surface_view)
        surfaceView.visibility = CameraBridgeViewBase.VISIBLE
        surfaceView.setCvCameraViewListener(viewModel)
        viewModel.rotation = this.windowManager.defaultDisplay.rotation

        // registers observer for information from viewModel
        val localMsgObserver: Observer<String> = Observer { localMsg -> showMsg(localMsg.toString()) }
        viewModel.getLocalMsg().observe(this, localMsgObserver)

//        val previewSizeObserver: Observer<Size> = Observer { previewSize -> setPreviewSize(previewSize!!) }
//        viewModel.getPreviewSize().observe(this,previewSizeObserver)

        val connectStatusObserver: Observer<Boolean> = Observer { isConnected -> connectStatusHandler(isConnected!!) }
        viewModel.getClientConnected().observe(this, connectStatusObserver)

        val surfaceTextureStatusObserver: Observer<String> = Observer { msg -> surfaceTextureStatusHandler(msg.toString()) }
        viewModel.getSurfaceTextureStatus().observe(this, surfaceTextureStatusObserver)

    }

    override fun onPause() {
        super.onPause()
        surfaceView.disableView()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, loaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        surfaceView.disableView()
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
                val intent = Intent(this@CameraActivity, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
        return returnValue
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged")
    }

    private fun showMsg(msg: String){
        Snackbar.make(surfaceView, msg, Snackbar.LENGTH_LONG).show()
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
