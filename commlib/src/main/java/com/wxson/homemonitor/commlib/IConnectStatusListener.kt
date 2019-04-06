package com.wxson.homemonitor.commlib

interface IConnectStatusListener {
    fun onConnectStatusChanged(connected: Boolean)
}