/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */

package site.zsxeee.melta.engine

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import site.zsxeee.melta.engine.domain.*
import site.zsxeee.melta.engine.util.*
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlin.collections.set

/**
 * Melta插件操作类，通过插件文件和IO接口来实例化对象。
 * @param source 插件文本的数据
 * @param ioProvider IO接口，提供插件统一的输入输出接口，便于请求的管理与缓存
 *
 * @property ioProvider 暴露接口便于管理请求
 * @property meta 插件的元数据部分，即'meta'标签中的信息
 * @property guid TODO
 * @property entries 获取entry节点列表
 * @property search 获取搜索节点
 *
 * @exception site.zsxeee.melta.engine.util.MeltaParseException 插件格式造成的解析问题
 */
class MeltaEngine(val source: String, val ioProvider: IOProvider) {
    //content
    private val dom = Jsoup.parse(source, "", Parser.xmlParser())
    val meta: Meta = Meta(dom.select("meta"))
    private val nodes: Nodes = Nodes(this, dom.select("nodes"))
    private val script: JsEngine by lazy {
        JsEngine(dom.select("script").text(), ioProvider, meta.title)
    }

    val guid: String by lazy { (this.meta.author + this.meta.title).toByteArray(charset("utf-8")).toMD5() }

    init {
        //catch
        this.nodes.catchCollection.forEach {
            when (it.key) {
                "ua" -> ioProvider.fetch.request.headers["User-Agent"] = it.value
                "encoding" -> ioProvider.fetch.request.encoding = Charset.forName(it.value)
                //"redirect" -> [has been catch]
            }
        }
    }

    /**
     * 判断插件是否和插件匹配，并返回第一个匹配到的view节点
     * @param url 需要匹配的链接
     * @param allowRedirect 是否在无匹配结果的情况下尝试返回redirect节点
     *
     * @return 第一个与链接匹配的view节点，没有则为<code>null</code>
     */
    fun findNode(url: String, allowRedirect: Boolean = false): Node? {
        if (this.isMatch(url)) {
            nodes.viewNodes.forEach {
                if (it.expr.containsMatchIn(url)) {
                    return it
                }
            }
            if (this.nodes.redirectNode != null && allowRedirect) {
                return this.nodes.redirectNode
            }
        }
        return null
    }

    /**
     * 使用指定节点来解析链接，并返回数据
     * @param node 解析所使用的节点
     * @param input 需要解析的链接或pattern TODO
     *
     * @return 解析结果的响应数据
     */
    fun getData(node: Node, input: String? = null, page: Int = 1): Response {
        //声明响应结果变量
        var result: Response
        //初始化链接
        val url = (node.url ?: input ?: "NULL::")
            .urlFormat(
                this.ioProvider.fetch.request.encoding.toString(),
                Regex("%input") to input.orEmpty(),
                Regex("%page") to page.toString()
            )
        //request
        if (node.request == null) {
            //如果没有定义request，那么不处理请求直接获取源数据
            result = this.ioProvider.fetch.callRequest(url)
        } else {
            //在第一次请求开始之间先进行处理，避免无谓的请求
            result = this.script.callJs(node.request, Response(byteArrayOf(), this.ioProvider.fetch.request, url))
            //通过CALL::循环获取、处理数据，最后得到目标链接
            var colonLink = result.toString().splitColonLink()
            while (colonLink != null && colonLink.first == "CALL") {
                result =
                    this.script.callJs(node.request, ioProvider.fetch.callRequest(colonLink.second, result.request))
                colonLink = result.toString().splitColonLink()
            }

            //如果不是redirect节点那么就获取链接源数据以传给parse
            if (node !is RedirectNode) {
                result = this.ioProvider.fetch.callRequest(result.toString())
            }
        }
        //parse
        //有parse就处理一下
        return if (node.parse === null) {
            result
        } else {
            script.callJs(node.parse, result)
        }
    }

    fun processData(colonUrl: String): ByteArray {
        val pair = colonUrl.splitColonLink() ?: throw MeltaParseException("url格式不对: $colonUrl")
        val redirectNode = RedirectNode(this, pair.first)
        return this.getData(redirectNode, pair.second).resultBody
    }

    val entries: ArrayList<EntryNode> = this.nodes.entryNodes

    val search: Node? = if (this.nodes.searchNodes.size > 0) this.nodes.searchNodes[0] else null //FIXME: single? multi?

    private fun isMatch(url: String): Boolean = this.meta.sourceExpr.containsMatchIn(url)

    /**
     * 释放插件实例，其实就是释放实例中的JS引擎。
     */
    fun release() {
        this.script.release()
    }
}