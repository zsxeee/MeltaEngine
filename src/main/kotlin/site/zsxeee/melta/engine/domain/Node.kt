/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */

package site.zsxeee.melta.engine.domain

import org.jsoup.nodes.Element
import site.zsxeee.melta.engine.MeltaEngine
import site.zsxeee.melta.engine.util.MeltaParseException
import site.zsxeee.melta.engine.util.parseDuration
import site.zsxeee.melta.engine.util.splitDataTypeView

/**
 * 抽象的节点对象，所有节点均继承于此
 * @property meltaContext melta上下文，方便获取插件信息
 * @property dataType 节点的DataType类型
 * @property url 节点链接或pattern TODO
 * @property request request方法名
 * @property parse parse方法名
 * @property process process方法名
 * @property cacheDuration 节点数据缓存时间
 */
abstract class Node {
    val meltaContext: MeltaEngine
    val dataType: Pair<String, String>
    val url: String?
    val request: String?
    val parse: String?
    val cacheDuration: Long

    protected constructor(
        meltaContext: MeltaEngine,
        dataType: Pair<String, String>,
        url: String?,
        request: String?,
        parse: String?,
        cacheDuration: Long
    ) {
        this.meltaContext = meltaContext
        this.dataType = dataType
        this.url = url
        this.request = request
        this.parse = parse
        this.cacheDuration = cacheDuration
    }

    protected constructor(meltaContext: MeltaEngine, node: Element) {
        this.meltaContext = meltaContext
        this.url = node.attr("url").takeIf { it.isNotBlank() }
        this.request = node.attr("request").takeIf { it.isNotBlank() }
        this.parse = node.attr("parse").takeIf { it.isNotBlank() }
        this.cacheDuration = node.parseDuration()

        val type = node.attr("type")
        if (type.isNotEmpty()) {
            this.dataType = node.attr("type").splitDataTypeView() ?: throw MeltaParseException(
                String.format(
                    "节点DataType.View的格式不对: '%s' 于 '%s'。",
                    node.attr("type"),
                    node.toString()
                )
            )
        } else {
            throw MeltaParseException(
                String.format(
                    "节点缺少应有的'type'属性: '%s'。",
                    node.toString()
                )
            )
        }
    }
}

class EntryNode(meltaContext: MeltaEngine, node: Element) : Node(meltaContext, node) {
    val title: String = node.attr("title").also {
        if (it.isBlank()) {
            throw MeltaParseException(
                String.format(
                    "Enter节点缺少应有的'title'属性: '%s'。",
                    node.toString()
                )
            )
        }
    }

}

class ViewNode(meltaContext: MeltaEngine, node: Element) : Node(meltaContext, node) {
    val expr: Regex = Regex(node.attr("expr").also {
        if (it.isBlank()) {
            throw MeltaParseException(
                String.format(
                    "View节点缺少应有的'expr'属性: '%s'。",
                    node.toString()
                )
            )
        }
    })

}

class SearchNode(meltaContext: MeltaEngine, node: Element) : Node(meltaContext, node)

class RedirectNode(meltaContext: MeltaEngine, request: String?) :
    Node(meltaContext, "native" to "redirect", null, request, null, 0)