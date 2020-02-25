/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */

package site.zsxeee.melta.engine.domain

import org.jsoup.select.Elements

class Meta(elements: Elements) {
    val title:String = elements.select("title").text()
    val version:Int = elements.select("meta>version").text().toInt()

    val author:String = elements.select("meta>author").text()

    val property:Map<String, Boolean> = HashMap<String, Boolean>().also{ map ->
        elements.select("meta>property").first()?.attributes()?.forEach {  attr ->
            map[attr.key] = attr.value == "true"
        }
    }

    val sourceUrl:String = elements.select("meta>source").attr("url")
    val sourceExpr:Regex = Regex(elements.select("meta>source").attr("expr"))

    //info
    val themeColor:String = elements.select("meta>info>theme").attr("color")
    val logoUrl:String = elements.select("meta>info>theme").attr("logo")
    val summary:String = elements.select("meta>info>summary").text()
    val contentType:String = elements.select("meta>info>tags").attr("type")
    val tags:ArrayList<String> = ArrayList()

    //actions
    val actions:ArrayList<Map<String, String>> = ArrayList()

    init {
        elements.select("meta>info>tags>tag").forEach{
            this.tags.add(it.text())
        }

        elements.select("meta>actions>action").forEach{
            val map = HashMap<String, String>()
            map["title"] = it.attr("title")
            map["type"] = it.attr("type")
            map["value"] = it.attr("value")
            this.actions.add(map)
        }
    }
}