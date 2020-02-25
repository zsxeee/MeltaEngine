/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */

package site.zsxeee.melta.engine.domain

import org.jsoup.select.Elements
import site.zsxeee.melta.engine.MeltaEngine
import java.util.*

/**
 * 节点集实体类
 * @param meltaContext melta的上下文
 * @param nodesElem nodes的jsop实例
 *
 * @property entryNodes 入口节点集
 * @property viewNodes 视图节点集
 * @property searchNodes 搜索节点集
 * @property redirectNode 重定向节点
 * @property catchCollection 预处理节点集
 *
 * @exception site.zsxeee.melta.engine.util.MeltaParseException
 */
class Nodes(private val meltaContext:MeltaEngine, nodesElem: Elements) {
    val entryNodes:ArrayList<EntryNode> = ArrayList()
    val viewNodes:ArrayList<ViewNode> = ArrayList()
    val searchNodes:ArrayList<SearchNode> = ArrayList()
    val redirectNode:RedirectNode?

    val catchCollection:MutableMap<String, String> = HashMap()

    init {
        //nodes
        nodesElem.select("nodes>*:not(catch)").forEach { element ->
            val tagName = element.tagName()
            element.children().forEach(
                    when(tagName){
                        "entry" -> { children ->
                            entryNodes.add(EntryNode(meltaContext, children))
                        }
                        "view" -> { children ->
                            viewNodes.add(ViewNode(meltaContext, children))
                        }
                        "search" -> { children ->
                            searchNodes.add(SearchNode(meltaContext, children))
                        }
                        else -> { _ ->
                            throw Exception("Unknown node collection name: '$tagName'.")
                        }
                    }
            )
        }

        //catch
        nodesElem.select("nodes>catch>processor").forEach{
            this.catchCollection[it.attr("type")] = it.attr("value")
        }

        this.redirectNode = if(this.catchCollection.containsKey("redirect")){
            RedirectNode(meltaContext, catchCollection["redirect"])
        }else null
    }
}
