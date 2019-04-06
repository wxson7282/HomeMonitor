package com.wxson.homemonitor.monitor

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.wxson.homemonitor.monitor.ui.main.MainFragment

class MainActivity : AppCompatActivity() {

    private val TAG = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance())
                .commitNow()
        }
    }

}
