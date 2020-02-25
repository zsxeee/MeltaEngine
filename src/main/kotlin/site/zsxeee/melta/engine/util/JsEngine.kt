/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */
package site.zsxeee.melta.engine.util

import com.eclipsesource.v8.*
import com.eclipsesource.v8.utils.V8ObjectUtils
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import site.zsxeee.melta.engine.domain.IOProvider
import site.zsxeee.melta.engine.domain.Request
import site.zsxeee.melta.engine.domain.Response
import java.nio.charset.Charset
import java.util.concurrent.Executors


class JsEngine(script: String, private val ioProvider: IOProvider, private val pluginName: String) {
    private lateinit var runtime: V8
    private val thread = Schedulers.from(Executors.newSingleThreadExecutor { r -> Thread(r, "observeOnThread") })
    private val compositeDisposable = CompositeDisposable()

    private val nativeFunction:HashMap<String, (parameter:ArrayList<Any?>)->Any?> = hashMapOf(
        "print" to { params ->
            if (params.size > 0) {
                val msg = params[0]
                ioProvider.logger.log(msg.toString(), pluginName)
                if (msg is Releasable) {
                    msg.release()
                }
            }
        },
        "require" to { params ->
            if (params.size > 0) {
                val url = params[0]
                try {
                    val scriptFile = ioProvider.fetch.requestRaw(url as String).toString()
                    runtime.executeVoidScript(scriptFile)
                } catch (e: Throwable) {
                    val ex = MeltaEngineRuntimeException(
                        e.message.orEmpty(),
                        RuntimeException("Can not load script '$url': ${e.message}", e)
                    )
                    ioProvider.logger.error(pluginName, ex)
                    throw ex
                }

                if (url is Releasable) {
                    url.release()
                }
            } else {
                val e = RuntimeException("'require': 1 argument required, but only 0 present.")
                val ex = MeltaEngineRuntimeException(
                    e.message.orEmpty(),
                    e
                )
                ioProvider.logger.error(pluginName, ex)
                throw ex
            }
        }
    )

    init {
        try {
            Observable.create<Unit> {
                runtime = V8.createV8Runtime()
                regCallback()
                runtime.executeVoidScript(script)
                it.onNext(Unit)
                it.onComplete()
            }
                .subscribeOn(thread)
                .doOnSubscribe { compositeDisposable.add(it) }
                .blockingSingle()
        } catch (e: Exception) {
            if (e is V8ScriptException) {
                val ex = MeltaEngineRuntimeException(
                    "插件Script区块第${e.lineNumber}行错误，无法初始化：\n\n${e.sourceLine.trim()}\n\n${e.jsStackTrace ?: "<无栈信息>"}"
                )
                ioProvider.logger.error(pluginName, ex)
                throw ex
            } else {
                val ex = MeltaEngineRuntimeException("插件因未知JS错误，无法初始化：\n\n${e.message ?: e}")
                ioProvider.logger.error(pluginName, ex)
                throw ex
            }
        }
    }

    fun callJs(callTarget: String, response: Response): Response {
        try {
            return Observable.create<Response> {
                val v8Request = this.toV8Request(response.request)
                val params = V8Array(runtime)
                    .push(response.toString())
                    .push(response.location)
                    .push(v8Request)

                it.onNext(
                    Response(
                        runtime.executeStringFunction(
                            callTarget,
                            params
                        ).toByteArray(Charset.forName(response.request.encoding.toString())),
                        toRequest(v8Request),
                        response.location
                    )
                )

                params.release()
                it.onComplete()
            }
                .subscribeOn(thread)
                .doOnSubscribe { compositeDisposable.add(it) }
                .blockingSingle()
        } catch (e: Exception) {
            if (e is V8ScriptException) {
                val ex = MeltaEngineRuntimeException(
                    "JavaScript于Script区块第${e.lineNumber}行出错：\n\n${e.sourceLine.trim()}\n\n${e.jsStackTrace ?: "<无栈信息>"}"
                )
                ioProvider.logger.error(pluginName, ex)
                throw ex
            } else {
                val ex = MeltaEngineRuntimeException(
                    e.message.orEmpty(),
                    e
                )
                ioProvider.logger.error(pluginName, ex)
                throw ex
            }
        }
    }

    private fun regCallback() {
        //将本地函数与自定义函数列表进行合并
        nativeFunction.putAll(ioProvider.customFunctions ?: mapOf())
        //循环绑定每一个方法
        nativeFunction.forEach { map ->
            this.runtime.registerJavaMethod(JavaCallback { _, parameters ->
                val params = ArrayList<Any?>()
                //将参数列表转换成List，并释放原生参数列表实例
                for (i: Int in 0 until parameters.length()) {
                    val parameter = parameters[i]
                    params.add(parameter)
                    if (parameter is Releasable) {
                        parameter.release()
                    }
                }
                val result = map.value(params)
                return@JavaCallback if (result is Unit) V8.getUndefined() else result
            }, map.key)
        }
    }

    fun release() {
        Observable.just(0)
            .doAfterTerminate {
                compositeDisposable.clear()
                this.thread.shutdown()
            }
            .observeOn(thread)
            .subscribe {
                try {
                    runtime.release()
                } catch (e: Throwable) {
                    ioProvider.logger.log("JS引擎无法被正确释放！(${e.message})", "MeltaEngine")
                }
            }
    }

    private fun toV8Request(request: Request): V8Object {
        val header = V8ObjectUtils.toV8Object(this.runtime, request.headers)
        val formData = V8ObjectUtils.toV8Object(this.runtime, request.formData)

        val v8Request = V8Object(this.runtime)
            .add("headers", header)
            .add("formData", formData)
            .add("encoding", request.encoding.toString())

        header.release()
        formData.release()

        return v8Request
    }

    private fun toRequest(v8Request: V8Object): Request {
        val headers = v8Request.getObject("headers")
        val formData = v8Request.getObject("formData")

        @Suppress("UNCHECKED_CAST")
        val request = Request(
            HashMap(V8ObjectUtils.toMap(headers) as MutableMap<String, String>),
            HashMap(V8ObjectUtils.toMap(formData) as MutableMap<String, String>),
            Charset.forName(v8Request.getString("encoding"))
        )

        headers.release()
        formData.release()
        v8Request.release()

        try {
            //checkResult
            HashMap<String, String>()
                .also {
                    it.putAll(request.headers)
                    it.putAll(request.formData)
                }.forEach {
                    @Suppress("USELESS_IS_CHECK")
                    if (it.value !is String) {
                        val e = ClassCastException("Wrong result type: ${it.value} not String type!")
                        val ex = MeltaInvalidResultException(
                            e.message.orEmpty(),
                            e
                        )
                        ioProvider.logger.error(pluginName, ex)
                        throw ex
                    }
                }

            return request

        } catch (e: ClassCastException) {
            val ex = MeltaEngineRuntimeException("Request中有对象包含了非字符串类型的值: $request", e)
            ioProvider.logger.error(pluginName, ex)
            throw ex
        }
    }
}