package com.wxson.homemonitor.camera.preview

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.TextureView

/**
 *  The thread used to display a content stream on
 *  @param textureView
 */
class PreviewThread(private val textureView: TextureView) : Runnable {
    lateinit var canvasRect: Rect
    // 定义接收preview image的Handler对象
    lateinit var revHandler: Handler
    class MyHandler(private val textureView: TextureView, private val dstRect: Rect) : Handler(){
        override fun handleMessage(msg: Message?) {
            val canvas = textureView.lockCanvas() ?: return
            if (msg != null){
                val bitmap: Bitmap? = msg.data.getParcelable("bitmap")
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, null, dstRect, null)
                }
            }
            textureView.unlockCanvasAndPost(canvas)
        }
    }

    override fun run() {
        Looper.prepare()
        revHandler = MyHandler(textureView, canvasRect)
        Looper.loop()
    }
}