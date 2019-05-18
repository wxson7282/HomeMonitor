package com.wxson.homemonitor.monitor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.wxson.homemonitor.commlib.RegexpUtil





class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // set OnPreferenceChangeListener
            val editTextPreference = findPreference<EditTextPreference>("server_ip")
            editTextPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener{ _, newValue ->
                // if ip address is going to be changed
                val oldValue = PreferenceManager.
                    getDefaultSharedPreferences(this.context).getString("server_ip", "")
                if (oldValue != newValue){
                    // syntax check
                    if (RegexpUtil.regexCheck_IpAddress(newValue as String)){
                        // write newValue into preference
                        editTextPreference?.text = newValue
                        restartApp()
                    }
                    else{
                        Snackbar.make(this.view!!, "server ip address syntax error", Snackbar.LENGTH_LONG).show()
                    }
                }
                false
            }
        }

        private fun restartApp(){
            Handler().postDelayed(Runnable {
                val app = this.activity!!.application
                val launchIntent = this.activity!!.packageManager?.getLaunchIntentForPackage(app.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
            }, 100)
        }
    }
}