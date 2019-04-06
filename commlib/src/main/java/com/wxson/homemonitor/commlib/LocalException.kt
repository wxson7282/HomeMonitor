package com.wxson.homemonitor.commlib

class LocalException: Exception {
    constructor(){}
    constructor(msg: String): super(msg){}
    constructor(t: Throwable): super(t){}
}