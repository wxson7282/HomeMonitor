package com.wxson.homemonitor.monitor.ui.main

import android.util.Log

/**
 *  Class for handlers invoked when a Thread abruptly
 *  terminates due to an uncaught exception.
 */
class MyUncheckedExceptionHandler: Thread.UncaughtExceptionHandler {
    private val TAG = this.javaClass.simpleName
    private lateinit var uncaughtExceptionListener: IUncaughtExceptionListener
    private var exceptionName = ""
    override fun uncaughtException(t: Thread?, e: Throwable?) {
        Log.e(TAG, "uncaughtException $t $e")
        if (e.toString() == "java.io.EOFException"){
            exceptionName = "EOFException"
        }
        else{
            exceptionName = ""
        }
        uncaughtExceptionListener.onUnUncaughtException(exceptionName)
    }

    interface IUncaughtExceptionListener{
        fun onUnUncaughtException(exceptionName: String)
    }

    fun setUncaughtExceptionListener(exceptionListener: IUncaughtExceptionListener){
        uncaughtExceptionListener = exceptionListener
    }
}