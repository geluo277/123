package com.neox.npkunpacker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var selectedNpk: Uri? = null
    private var outputTree: Uri? = null

    private lateinit var fileLabel: TextView
    private lateinit var outputLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var logView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var unpackButton: Button

    companion object {
        private const val REQUEST_NPK = 1001
        private const val REQUEST_OUTPUT = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val title = TextView(this).apply {
            text = "NeoX NPK 解包器"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(title, matchWrap())

        fileLabel = label("尚未选择 NPK 文件")
        root.addView(fileLabel, matchWrap())

        root.addView(button("选择 NPK 文件") { chooseNpk() }, matchWrap())

        outputLabel = label("输出目录：尚未选择")
        root.addView(outputLabel, matchWrap())

        root.addView(button("选择输出目录") { chooseOutputDirectory() }, matchWrap())

        unpackButton = button("开始解包") { startUnpack() }
        root.addView(unpackButton, matchWrap())

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(12)
        ).apply { setMargins(0, dp(10), 0, dp(8)) })

        statusLabel = label("就绪")
        root.addView(statusLabel, matchWrap())

        val logTitle = TextView(this).apply {
            text = "运行日志"
            textSize = 16f
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(logTitle, matchWrap())

        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFFF2F2F2.toInt())
        }
        val scroll = ScrollView(this)
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(button("清空日志") { logView.text = "" }, matchWrap())
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, dp(4), 0, dp(4)) }

    private fun label(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 14f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        setOnClickListener { action() }
    }

    private fun chooseNpk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_NPK)
    }

    private fun chooseOutputDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_OUTPUT)
    }

    @Deprecated("Using startActivityForResult for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQUEST_NPK -> {
                selectedNpk = uri
                contentResolver.takePersistableUriPermission(
                    uri, data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                )
                fileLabel.text = "已选择：${displayName(uri)}"
                appendLog("输入文件：${displayName(uri)}")
            }
            REQUEST_OUTPUT -> {
                outputTree = uri
                contentResolver.takePersistableUriPermission(
                    uri, data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                )
                outputLabel.text = "输出目录：已授权"
                appendLog("已选择输出目录")
            }
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "所选文件"
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    private fun startUnpack() {
        val input = selectedNpk
        val output = outputTree
        if (input == null) {
            toast("请先选择 NPK 文件")
            return
        }
        if (output == null) {
            toast("请先选择输出目录")
            return
        }

        unpackButton.isEnabled = false
        progress.progress = 0
        statusLabel.text = "正在读取文件……"
        appendLog("开始解包")
        executor.execute {
            try {
                val bytes = contentResolver.openInputStream(input)?.use { it.readBytes() }
                    ?: throw IOException("无法读取输入文件")
                val entries = parseNpk(bytes)
                runOnUiThread {
                    appendLog("解析成功：${entries.size} 个数据项")
                    statusLabel.text = "已解析 ${entries.size} 项，开始导出……"
                }

                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, output)
                    ?: throw IOException("无法打开输出目录")
                var exported = 0
                entries.forEachIndexed { idx, entry ->
                    val name = "npkdump-${entry.index}"
                    val existing = rootDoc.findFile(name)
                    existing?.delete()
                    val doc = rootDoc.createFile("application/octet-stream", name)
                        ?: throw IOException("无法创建输出文件：$name")
                    contentResolver.openOutputStream(doc.uri, "w")?.use { stream ->
                        BufferedOutputStream(stream).use { out ->
                            out.write(bytes, entry.offset, entry.size)
                        }
                    } ?: throw IOException("无法写入输出文件：$name")
                    exported++
                    val percent = ((idx + 1) * 100 / entries.size.coerceAtLeast(1))
                    runOnUiThread {
                        progress.progress = percent
                        statusLabel.text = "正在导出：$exported / ${entries.size}"
                        if (idx < 20 || idx == entries.lastIndex) {
                            appendLog("[$exported/${entries.size}] $name offset=0x${entry.offset.toString(16).uppercase()} size=${entry.size}")
                        }
                    }
                }
                runOnUiThread {
                    statusLabel.text = "完成：已导出 $exported 个文件"
                    appendLog("解包完成")
                    toast("解包完成：$exported 个文件")
                    unpackButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusLabel.text = "失败"
                    appendLog("错误：${e.message ?: e.javaClass.simpleName}")
                    toast("解包失败：${e.message ?: "未知错误"}")
                    unpackButton.isEnabled = true
                }
            }
        }
    }

    private data class Entry(val index: Int, val offset: Int, val size: Int)

    // 采用原 NeteaseUnpackTools Python 脚本的 28 字节表项解析逻辑。
    private fun parseNpk(data: ByteArray): List<Entry> {
        if (data.size < 0x18) throw IOException("文件太小，不像受支持的 NPK")
        fun readU32(pos: Int): Long {
            if (pos < 0 || pos + 4 > data.size) throw IOException("文件头/索引读取越界")
            return ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN)
                .int.toLong() and 0xFFFFFFFFL
        }

        val countLong = readU32(4)
        val tableOffsetLong = readU32(0x14)
        if (countLong > 1_000_000L) throw IOException("索引数量异常：$countLong")
        if (countLong == 0L) return emptyList()
        val tableSize = countLong * 28L
        if (tableOffsetLong > data.size || tableSize > data.size - tableOffsetLong) {
            throw IOException("文件表超出文件范围，可能是不同 NPK 版本或格式")
        }

        val result = ArrayList<Entry>(countLong.toInt())
        for (i in 0 until countLong.toInt()) {
            val p = tableOffsetLong.toInt() + i * 28
            val offLong = readU32(p + 4)
            val sizeLong = readU32(p + 8)
            if (offLong > data.size || sizeLong > data.size - offLong) {
                throw IOException("第 $i 项数据范围超出文件，offset=$offLong size=$sizeLong")
            }
            if (offLong > Int.MAX_VALUE || sizeLong > Int.MAX_VALUE) {
                throw IOException("第 $i 项过大，当前版本无法处理")
            }
            result.add(Entry(i, offLong.toInt(), sizeLong.toInt()))
        }
        return result
    }

    private fun appendLog(line: String) {
        logView.append(line + "\n")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
