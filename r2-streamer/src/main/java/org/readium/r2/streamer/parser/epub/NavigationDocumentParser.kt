/*
 * Module: r2-streamer-kotlin
 * Developers: Quentin Gliosca
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.parser.epub

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.parser.xml.ElementNode
import org.readium.r2.streamer.parser.normalize

internal object NavigationDocumentParser {
    fun parse(document: ElementNode, filePath: String) : NavigationData? {
        val prefixAttribute = document.getAttrNs("prefix", Namespaces.Ops)
        val docPrefixes = if (prefixAttribute == null) emptyMap() else parsePrefixes(prefixAttribute)
        val prefixMap = CONTENT_RESERVED_PREFIXES + docPrefixes // prefix element overrides reserved prefixes
        val body = document.getFirst("body", Namespaces.Xhtml) ?: return null

        return  body.collect("nav", Namespaces.Xhtml).flatMap { nav ->
           val types = nav.getAttrNs("type", Namespaces.Ops)?.trim()?.split("\\s+".toRegex())
                   ?.mapNotNull { resolveProperty(it, prefixMap, DEFAULT_VOCAB.TYPE) }
           val links = parseNavElement(nav, filePath)
           if (types != null && links != null) types.map {  Pair(it, links) } else emptyList()
        }.toMap().filterValues { it.isNotEmpty() }.mapKeys {
            val suffix = it.key.removePrefix(DEFAULT_VOCAB.TYPE.iri)
            if (suffix in listOf("toc", "page-list", "landmarks", "lot", "loi", "loa", "lov")) suffix else it.key
        }
    }

    private fun parseNavElement(nav: ElementNode, filePath: String) : List<Link>? =
        nav.getFirst("ol", Namespaces.Xhtml)?.let { parseOlElement(it, filePath) }

    private fun parseOlElement(element: ElementNode, filePath: String): List<Link> =
        element.get("li", Namespaces.Xhtml).mapNotNull {  parseLiElement(it, filePath) }

    private fun parseLiElement(element: ElementNode, filePath: String): Link? {
        val first = element.getAll().firstOrNull() ?: return null // should be <a>,  <span>, or <ol>
        val title = if (first.name == "ol") "" else first.collectText().replace("\\s+".toRegex(), " ").trim()
        val rawHref = first.getAttr("href")
        val href = if (first.name == "a" && !rawHref.isNullOrBlank()) normalize(filePath, rawHref) else "#"
        val children = element.getFirst("ol", Namespaces.Xhtml)?.let { parseOlElement(it, filePath) }.orEmpty()
        return if (children.isEmpty() && (href == "#" || title == ""))
            null
        else
            Link(
                title = title,
                href = href,
                children = children
            )
    }
}

