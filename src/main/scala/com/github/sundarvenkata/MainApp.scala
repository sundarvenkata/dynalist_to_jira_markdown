package com.github.sundarvenkata

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.ConfigFactory
import scalaj.http._
import java.awt.Toolkit


object MainApp extends App {
  val dynalist_url = ConfigFactory.load().getString("dynalist.doc.url")
  val dynalist_api_token = ConfigFactory.load().getString("dynalist.api.token")
  val document_id_node_id_separator = "#z="
  def clipboard = Toolkit.getDefaultToolkit.getSystemClipboard

  val Array(document_id, node_id) = dynalist_url.split("/").last.split(document_id_node_id_separator)

  val JsonToPost = """{"token":"%s", "file_id":"%s"}""".format(dynalist_api_token, document_id)
  val response = Http(ConfigFactory.load().getString("dynalist.api.doc_read_url")).postData(JsonToPost.getBytes)
    .header("content-type", "application/json").asString
  val mapper = new ObjectMapper()
  val jsonHttpResponse = mapper.readValue(response.body, classOf[Object])  
  println(DynalistToJiraMarkdownUtils.getJiraMarkdown(
    DynalistToJiraMarkdownUtils.getNodeJson(node_id, jsonHttpResponse), jsonHttpResponse))
}
