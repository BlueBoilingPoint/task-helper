package com.taskhelper

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppPickerActivity : AppCompatActivity() {

    data class AppInfo(val name: String, val pkg: String)

    private val all = mutableListOf<AppInfo>()
    private val shown = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        val pm = packageManager
        val resolve = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }, 0
        )
        all.addAll(
            resolve.map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
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
                shown.addAll(all.filter { it.name.lowercase().contains(q) })
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
            val icon: android.view.View = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.task_name)
            val meta: TextView = v.findViewById(R.id.task_meta)
            val edit: android.widget.ImageButton = v.findViewById(R.id.btn_edit)
            val delete: android.widget.ImageButton = v.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int): VH {
            val v = android.view.LayoutInflater.from(p.context)
                .inflate(R.layout.item_task, p, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            val it = items[i]
            h.name.text = it.name
            h.meta.text = it.pkg
            h.icon.setBackgroundColor(Color.parseColor("#4C6FFF"))
            h.edit.visibility = android.view.View.GONE
            h.delete.visibility = android.view.View.GONE
            h.itemView.setOnClickListener { onClick(it) }
        }

        override fun getItemCount() = items.size
    }
}
