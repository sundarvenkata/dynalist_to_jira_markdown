# Overview
Convert Dynalist lists to JIRA markdown

# Dependencies
### Mandatory
**application.conf** - You can place this file somewhere in the Java classpath or explicitly specify the path to this file with the _config.file_ parameter as shown in the "Run" section below.
```conf
dynalist {
  api {
    token = "<API token from Dynalist>",
    doc_read_url = "https://dynalist.io/api/v1/doc/read",
    file_list_url = "https://dynalist.io/api/v1/file/list"
  }
}
```

### Optional
**zenity** - To create confirmation dialog boxes as shown in the "Run" section below.

**xsel** - To operate this conversion utility on the clipboard when it contains the Dynalist URL (works in conjunction with the "Copy link to clipboard" option that Dynalist provides).


# Build
```bash
git clone https://github.com/sundarvenkata/dynalist_to_jira_markdown.git
cd dynalist_to_jira_markdown
sbt assembly
```

After running the steps above, you can find the resulting "fat JAR" file inside the target/scala-2.12/classes directory.


# Run

```bash
# Specify URL for a specific Dynalist node on the command line
# Output will be printed to the console
java -Dconfig.file=application.conf -Ddynalist.doc.url="https://dynalist.io/d/vEmRK7VZRM9lcPEe0VJvpRrD#z=nLZmLZetQWdIvp4lOcTe3UYo" -jar dynalist_to_jira_markdown-assembly-0.1.jar | xsel -ib

# Specify URL for a specific Dynalist node from the clipboard
# Output will be copied to the clipboard
(java -Dconfig.file=application.conf -Ddynalist.doc.url="`xsel -ob`" -jar dynalist_to_jira_markdown-assembly-0.1.jar | xsel -ib) && zenity --info --text="JIRA markdown copied to clipboard!"
```