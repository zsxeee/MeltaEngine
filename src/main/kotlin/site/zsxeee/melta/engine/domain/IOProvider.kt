package site.zsxeee.melta.engine.domain

import site.zsxeee.melta.engine.util.MeltaException
import site.zsxeee.melta.engine.util.each
import site.zsxeee.melta.engine.util.splitColonLink
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

data class IOProvider(
    val fetch: Fetch,
    val logger:Logger,
    var customFunctions:Map<String, (parameter:ArrayList<Any?>)->Any?>? = null
)

interface Fetch{
    var request:Request
    var cacheDuration:Long
    fun requestRaw(url: String, request: Request = this.request): Response
    fun callRequest(url: String, request: Request = this.request): Response {
        val colonLink = url.splitColonLink()
        return if (colonLink != null&&colonLink.first=="NULL") {
            Response(colonLink.second.toByteArray(), request, url)
        }else{
            requestRaw(url, request)
        }
    }
}

data class Request(
    val headers: MutableMap<String, String> = HashMap(),
    val formData: MutableMap<String, String> = HashMap(),
    var encoding: Charset = Charset.forName("UTF-8")
) {
    var headerStr: String
        set(value) {
            val list = value.split("\n")
            val reg = Regex("([^:]+):\\s*(.+)")
            list.forEach {
                val result = reg.find(it)?.groupValues
                if (result !==null){
                    this.headers[result[1]] = URLDecoder.decode(result[2], this.encoding.toString())
                }
            }
        }
        get() {
            val str = arrayOf("")
            this.headers.each { key, value -> str[0] += "$key: $value\n" }

            return URLEncoder.encode(str[0], this.encoding.toString())
        }

    var formStr: String
        set(value) {
            val list = value.split("&")
            val reg = Regex("([^=]+)=(.+)")
            list.forEach {
                val result = reg.find(it)?.groupValues
                if (result !==null){
                    this.formData[result[1]] = URLDecoder.decode(result[2], this.encoding.toString())
                }
            }
        }
        get() {
            val str = arrayOf("")
            this.formData.each { key, value -> str[0] += "$key=$value&" }
            str[0].substring(0,str[0].length - 1)

            return URLEncoder.encode(str[0], this.encoding.toString())
        }
}

data class Response(val resultBody:ByteArray, val request: Request, val location: String){
    override fun toString():String {
        return this.resultBody.toString(Charset.forName(request.encoding.toString()))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Response

        if (!resultBody.contentEquals(other.resultBody)) return false
        if (request != other.request) return false
        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resultBody.contentHashCode()
        result = 31 * result + request.hashCode()
        result = 31 * result + location.hashCode()
        return result
    }
}

interface Logger{
    fun log(content:String, pluginName:String)
    fun error(pluginName: String, cause: MeltaException)
}