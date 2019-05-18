package com.wxson.homemonitor.commlib

class RegexpUtil {
    companion object{
        fun regexCheck_IpAddress(str: String): Boolean{
            val regex = Regex(pattern = """(2(5[0-5]|[0-4]\d)|[0-1]?\d{1,2})(\.(2(5[0-5]|[0-4]\d)|[0-1]?\d{1,2})){3}""")
            return str.matches(regex)
        }
    }
}