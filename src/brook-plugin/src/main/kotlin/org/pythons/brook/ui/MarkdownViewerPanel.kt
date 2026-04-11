package org.pythons.brook.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.pythons.brook.runner.BrookApiClient
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

class MarkdownViewerPanel(
    private val exerciseId: String,
    private val fileName: String
) {

    val root = JPanel(BorderLayout())
    private val browser = JBCefBrowser()

    init {
        buildUI()
        loadExerciseContent()
    }

    private fun loadExerciseContent() {
        // Initial state
        browser.loadHTML("<html><body><h3>Loading exercise content...</h3></body></html>")
        
        CoroutineScope(Dispatchers.IO).launch {
            val result = BrookApiClient.getExerciseContent(exerciseId, fileName)
            
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val rawHtml = result.getOrNull() ?: ""
                    println("Brook: Fetched content for $exerciseId, ${rawHtml.length} bytes.")
                    
                    try {
                        // Check if it's already a full HTML document
                        val contentToLoad = if (rawHtml.trim().startsWith("<html", ignoreCase = true) || 
                                              rawHtml.trim().startsWith("<!DOCTYPE", ignoreCase = true)) {
                            // Minimal injection for full documents
                            RAW_INJECT_STYLES + rawHtml
                        } else {
                            injectStyles(rawHtml)
                        }
                        
                        browser.loadHTML(contentToLoad)
                        println("Brook: Successfully sent HTML to JBCefBrowser.")
                    } catch (e: Exception) {
                        println("Brook: Error during HTML injection: ${e.message}")
                        browser.loadHTML("<html><body><h3>Error rendering HTML</h3><pre>${e.stackTraceToString()}</pre></body></html>")
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    println("Brook: Failed to fetch exercise $exerciseId: $error")
                    val errorHtml = """
                        <html><body>
                        <h3 style='color: red;'>Error loading exercise</h3>
                        <p>$error</p>
                        <button onclick='window.location.reload()'>Retry</button>
                        </body></html>
                    """.trimIndent()
                    browser.loadHTML(errorHtml)
                }
            }
        }
    }

    private val RAW_INJECT_STYLES: String
        get() = """
            <style>
                /* Helper styles injected for full documents */
                body { margin: 10px !important; }
            </style>
        """.trimIndent()

    private fun injectStyles(html: String): String {
        val isDark = javax.swing.UIManager.getColor("Panel.background")?.let {
            (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 128
        } ?: false

        val bg          = if (isDark) "#1e1e1e" else "#ffffff"
        val fg          = if (isDark) "#d4d4d4" else "#1a1a1a"
        val codeBg      = if (isDark) "#2b2b2b" else "#f4f4f4"
        val codeColor   = if (isDark) "#e0e0e0" else "#1a1a1a"
        val borderColor = if (isDark) "#444444" else "#dddddd"
        val linkColor   = if (isDark) "#6db3f2" else "#0070c1"

        val style = """
            <style>
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                    font-size: 14px;
                    line-height: 1.7;
                    color: $fg;
                    background-color: $bg;
                    margin: 0;
                    padding: 16px 20px;
                }
                h1, h2, h3, h4 {
                    color: $fg;
                    margin-top: 24px;
                    margin-bottom: 8px;
                    font-weight: 600;
                }
                h1 { font-size: 22px; border-bottom: 1px solid $borderColor; padding-bottom: 8px; }
                h2 { font-size: 18px; border-bottom: 1px solid $borderColor; padding-bottom: 6px; }
                h3 { font-size: 15px; }
                p { margin: 8px 0; }
                a { color: $linkColor; text-decoration: none; }
                a:hover { text-decoration: underline; }
                code {
                    font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
                    font-size: 13px;
                    background-color: $codeBg;
                    color: $codeColor;
                    padding: 2px 5px;
                    border-radius: 4px;
                    border: 1px solid $borderColor;
                }
                pre {
                    background-color: $codeBg;
                    color: $codeColor;
                    font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
                    font-size: 13px;
                    padding: 14px 16px;
                    border-radius: 6px;
                    border: 1px solid $borderColor;
                    overflow-x: auto;
                    margin: 12px 0;
                    white-space: pre;
                    word-wrap: normal;
                }
                pre code {
                    background: none;
                    border: none;
                    padding: 0;
                    border-radius: 0;
                    font-size: 13px;
                }
                blockquote {
                    border-left: 4px solid $borderColor;
                    margin: 12px 0;
                    padding: 4px 16px;
                    color: ${if (isDark) "#aaaaaa" else "#666666"};
                    background-color: $codeBg;
                    border-radius: 0 4px 4px 0;
                }
                ul, ol { padding-left: 24px; margin: 8px 0; }
                li { margin: 4px 0; }
                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 12px 0;
                }
                th, td {
                    border: 1px solid $borderColor;
                    padding: 8px 12px;
                    text-align: left;
                }
                th { background-color: $codeBg; font-weight: 600; }
                hr {
                    border: none;
                    border-top: 1px solid $borderColor;
                    margin: 20px 0;
                }
                .highlight { background-color: ${if (isDark) "#3a3a00" else "#fffbcc"}; }
            </style>
        """.trimIndent()

        return if (html.contains("<head>", ignoreCase = true)) {
            html.replace(Regex("<head>", RegexOption.IGNORE_CASE), "<head>$style")
        } else {
            "<html><head>$style</head><body>${html}</body></html>"
        }
    }

    private fun buildUI() {
        val content = JPanel(BorderLayout())
        content.border = JBUI.Borders.empty(0)

        // Top bar
        val topBar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8, 12, 8, 12)

            add(JBLabel(
                "${exerciseId.replaceFirstChar { it.uppercase() }} · $fileName"
            ).apply { font = font.deriveFont(Font.BOLD) })
            add(Box.createHorizontalGlue())
        }
        content.add(topBar, BorderLayout.NORTH)
        content.add(browser.component, BorderLayout.CENTER)
        root.add(content, BorderLayout.CENTER)
    }
}
