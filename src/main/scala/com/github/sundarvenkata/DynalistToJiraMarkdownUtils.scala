package com.github.sundarvenkata

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.gatling.jsonpath.JsonPath
import org.apache.commons.text.StringEscapeUtils

object DynalistToJiraMarkdownUtils {

  private val hyperlinkPatternInOPML = raw"\[(.+)\]\(([^ ]+)\)".r
  private val boldPatternInOPML = raw"\*\*((?:(?!\*\*).)+)\*\*".r
  private val italicPatternInOPML = raw"__(.+)__".r
  private val codeNotePatternInOPML = raw"```(.+)```".r
  private val tagMentionPatternInOPML = raw"@[A-Za-z]+[0-9]*[A-Za-z]*".r

  def transformHyperlink(hyperLinkNodeText: String, wholeNodeText: String): String = {
    val hyperlinkPatternInOPML(linkDesc, link) = hyperLinkNodeText
    linkDesc.toLowerCase match {
      case "pasted image" => wholeNodeText.replace("!" + hyperLinkNodeText, "!%s!".format(link))
      case _ => wholeNodeText.replace(hyperLinkNodeText, "[%s|%s]".format(linkDesc, link))
    }
  }

  def transformHyperlinks(nodeText: String): String = {
    val firstHyperLinkMatch = hyperlinkPatternInOPML.findFirstIn(nodeText)
    firstHyperLinkMatch match {
      case Some(firstMatch) => transformHyperlinks(transformHyperlink(firstMatch, nodeText))
      case _ => nodeText
    }
  }

  def transformBoldSection(boldedNodeText: String, wholeNodeText: String): String = {
    val boldPatternInOPML(boldedSectionText) = boldedNodeText
    wholeNodeText.replace(boldedNodeText, "*%s*".format(boldedSectionText.trim.replace("*", "\\*")))
  }

  def transformBoldSections(nodeText: String): String = {
    val firstBoldSectionMatch = boldPatternInOPML.findFirstIn(nodeText)
    firstBoldSectionMatch match {
      case Some(firstMatch) => transformBoldSections(transformBoldSection(firstMatch, nodeText))
      case _ => nodeText
    }
  }

  def transformItalicSection(italicNodeText: String, wholeNodeText: String): String = {
    val italicPatternInOPML(italicSectionText) = italicNodeText
    wholeNodeText.replace(italicNodeText, "_%s_".format(italicSectionText.trim.replace("_", "\\_")))
  }

  def transformItalicSections(nodeText: String): String = {
    val firstItalicSectionMatch = italicPatternInOPML.findFirstIn(nodeText)
    firstItalicSectionMatch match {
      case Some(firstMatch) => transformItalicSections(transformItalicSection(firstMatch, nodeText))
      case _ => nodeText
    }
  }

  def transformTagMention(tagMentionNodeText: String, wholeNodeText: String): String = {
    wholeNodeText.replace(tagMentionNodeText, "[~%s]".format(tagMentionNodeText.replace("@", "")))
  }

  def transformTagMentions(nodeText: String): String = {
    val firstTagMentionMatch = tagMentionPatternInOPML.findFirstIn(nodeText)
    firstTagMentionMatch match {
      case Some(firstMatch) => transformTagMentions(transformTagMention(firstMatch, nodeText))
      case _ => nodeText
    }
  }

  def transformNodeText(nodeText: String): String = {
    val transformations = transformHyperlinks _ andThen transformBoldSections andThen transformItalicSections andThen
      transformTagMentions andThen StringEscapeUtils.unescapeXml
    transformations(nodeText)
  }

  def transformNodeNote(noteNode: String): String = {
    val noteComps = noteNode.split("\n").mkString("&#10;")
    noteComps match {
      case codeNotePatternInOPML(codeChunk) =>
        val codeChunkComponents: Array[String] = codeChunk.split("&#10;")
        val langType = codeChunkComponents.head.toLowerCase
        val codeChunkText = codeChunkComponents.tail.mkString("\n")
        "{code:%s}\n%s\n{code}".format(langType, codeChunkText)
      case _ => transformNodeText(noteComps)
    }
  }

  def getNodeJson(node_id: String, jsonObject: Object): java.util.LinkedHashMap[String, Object] = {
    val mapper = new ObjectMapper();
    val json = mapper.writeValueAsString(jsonObject);
    val node: JsonNode = mapper.readTree(json);
    JsonPath.query("$.*[?(@['id']=='%s')]".format(node_id), node).right.get.map(x =>  mapper.convertValue(x, classOf[java.util.LinkedHashMap[String, Object]])).next()
  }

  def getJiraMarkdown(node: java.util.LinkedHashMap[String, Object], jsonObject: Object, level: Int = 1,
                      checkBoxFlag: Boolean = false): String = {
    val isCheckbox = checkBoxFlag || node.get("checkbox").asInstanceOf[Boolean]
    val nodeContent = node.get("content").toString.trim
    val isChecked = node.get("checked").asInstanceOf[Boolean]
    val isFailed = nodeContent.toLowerCase().contains("failed")
    val checkBoxSymbol: String = if (isCheckbox) if (isChecked && !isFailed) "(/) " else if (isFailed) "(x) " else "" else ""
    val transformedNodeText: String =
      if (!nodeContent.equals("")) ("*" * level) + " " + checkBoxSymbol + transformNodeText(nodeContent) else ""

    val transformedNodeNote: String = {
      if (node.containsKey("note")) {
        val nodeNote = node.get("note").toString.trim
        if (!nodeNote.equals("")) ("*" * level) + " " + transformNodeNote(nodeNote) else ""
      }
      else ""
    }

    val childrenJiraMarkdown = {
      if (node.containsKey("children") && !transformedNodeText.contains("MESS") && !transformedNodeText.contains("STATUS_CHECK") && !transformedNodeText.contains("OPS_NOTE")) {
        node.get("children").asInstanceOf[java.util.ArrayList[String]].stream()
          .map(child_node => getJiraMarkdown(getNodeJson(child_node, jsonObject), jsonObject, level + 1, isCheckbox))
          .toArray()
      }
      else Array()
    }

    (Array[String](transformedNodeText, transformedNodeNote) ++ childrenJiraMarkdown)
      .map(a => a.toString)
      .filter(resultString => !(resultString.equals("") || resultString.contains("STATUS_CHECK") || resultString.contains("MESS") || resultString.contains("OPS_NOTE")))
      .mkString("\n")
  }
}
