/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */

package site.zsxeee.melta.engine.util

import org.jsoup.nodes.Node
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

inline fun<K,V> MutableMap<K,V>.each(action:(key:K, value:V)->Unit){
    this.keys.forEach { action(it, this[it]!!) }
}

fun Node.parseDuration():Long{
    val hours = if(!this.hasAttr("cache")) 24 else {
        val str = this.attr("cache")
        val result = Regex("^(\\d+)([hdwmy]?)$").find(str) ?: throw MeltaParseException(
            String.format("节点的'cache'属性值格式不正确: '%s' 于 '%s'。",
                    str,
                    this.toString())
        )

        when(result.groupValues[2]){
            "h" -> 1
            "d" -> 24
            "w" -> 24*7
            "m" -> 24*30
            "y" -> 24*365
            "" -> 0
            else -> throw MeltaParseException(
                String.format("节点的'cache'属性值单位不正确: '%s' 于 '%s'。",
                        str,
                        this.toString())
            )
        }.toLong()
    }
    return TimeUnit.HOURS.toMillis(hours)
}

fun ByteArray.toMD5(): String {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(this)

    val hex = "0123456789ABCDEF".toCharArray()
    val ret = StringBuilder(bytes.size * 2)
    for (i in bytes.indices) {
        ret.append(hex[bytes[i].toInt() shr 4 and 0x0f])
        ret.append(hex[bytes[i].toInt() and 0x0f])
    }
    return ret.toString()
}

fun String.splitColonLink():Pair<String, String>?{
    val reg = Regex("^([a-zA-Z_$][a-zA-Z\\d_$]*)::(.+)$")
    return if (!reg.matches(this)) null else{
        val result = reg.find(this)!!
        return result.groupValues[1] to result.groupValues[2]
    }
}

fun String.splitDataTypeView():Pair<String, String>?{
    val reg = Regex("^(\\w+)\\.(\\w+)\$")
    return if (!reg.matches(this)) null else{
        val result = reg.find(this)!!
        return result.groupValues[1] to result.groupValues[2]
    }
}

fun String.urlFormat(encoding:String, vararg replacement:Pair<Regex, String>):String{
    var url = this
    replacement.forEach {
        val decodedStr = URLEncoder.encode(it.second, encoding)
        url = url.replace(it.first, decodedStr.orEmpty())
    }
    return url
}