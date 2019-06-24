package com.wxson.homemonitor.commlib

interface ITransmitInstructionListener {
    fun onTransmitInstructionArrived(transmitOn: Boolean)
}