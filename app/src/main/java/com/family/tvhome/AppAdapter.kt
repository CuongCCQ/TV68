package com.family.tvhome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.family.tvhome.databinding.ItemAppBinding

class AppAdapter(
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    var items: List<AppItem> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val app = items[pos]
        h.b.icon.setImageDrawable(app.icon)
        h.b.label.text = app.label
        h.b.root.setOnClickListener { onClick(app) }
        h.b.root.setOnLongClickListener { onLongClick(app); true }
        h.b.root.setOnFocusChangeListener { v, focused ->
            v.animate().scaleX(if (focused) 1.12f else 1f).scaleY(if (focused) 1.12f else 1f).setDuration(150).start()
            h.b.label.visibility = if (focused) View.VISIBLE else View.INVISIBLE
        }
    }
}
