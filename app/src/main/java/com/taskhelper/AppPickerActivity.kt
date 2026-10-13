package com.taskhelper

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppPickerActivity : AppCompatActivity() {

    data class AppInfo(val name: String, val pkg: String, val icon: Drawable?)

    private val all = mutableListOf<AppInfo>()
    private val shown = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        val pm = packageManager
        // 查询所有具备桌面启动入口的应用（配合 Manifest 中的 <queries> 声明，
        // Android 11+ 也能列出微信、企业微信等第三方应用）
        val resolve = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            PackageManager.MATCH_ALL
        )
        all.addAll(
            resolve.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                val label = try { ri.loadLabel(pm).toString() } catch (_: Exception) { pkg }
                if (label.isBlank()) return@mapNotNull null
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                AppInfo(label, pkg, icon)
            }
                .distinctBy { it.pkg }
                .sortedBy { it.name }
        )
        shown.addAll(all)

        adapter = AppAdapter(shown) { info ->
            setResult(RESULT_OK, Intent().apply {
                putExtra("app", info.name)
                putExtra("pkg", info.pkg)
            })
            finish()
        }

        val rv = findViewById<RecyclerView>(R.id.app_list)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<EditText>(R.id.search).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val q = s.toString().lowercase()
                shown.clear()
                shown.addAll(all.filter { it.name.lowercase().contains(q) || it.pkg.lowercase().contains(q) })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        findViewById<android.widget.ImageButton>(R.id.btn_back).setOnClickListener { finish() }
    }

    class AppAdapter(
        private val items: List<AppInfo>,
        private val onClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.task_name)
            val meta: TextView = v.findViewById(R.id.task_meta)
        }

        override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int): VH {
            val v = android.view.LayoutInflater.from(p.context)
                .inflate(R.layout.item_app, p, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            val it = items[i]
            h.name.text = it.name
            h.meta.text = it.pkg
            if (it.icon != null) {
                h.icon.setImageDrawable(it.icon)
            } else {
                h.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            h.itemView.setOnClickListener { onClick(items[i]) }
        }

        override fun getItemCount() = items.size
    }
}
