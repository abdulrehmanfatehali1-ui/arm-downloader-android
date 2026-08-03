package com.arm.downloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FormatAdapter(
    private val formats: List<FormatItem>,
    private val onSelect: (FormatItem) -> Unit
) : RecyclerView.Adapter<FormatAdapter.FormatVH>() {

    private var selectedPosition = 0

    inner class FormatVH(v: View) : RecyclerView.ViewHolder(v) {
        val cardRoot: LinearLayout = v.findViewById(R.id.cardRoot)
        val tvBadge: TextView = v.findViewById(R.id.tvQualityBadge)
        val tvLabel: TextView = v.findViewById(R.id.tvQualityLabel)
        val tvSize: TextView = v.findViewById(R.id.tvQualitySize)
        val ivCheck: ImageView = v.findViewById(R.id.ivSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_format_card, parent, false)
        return FormatVH(v)
    }

    override fun getItemCount() = formats.size

    override fun onBindViewHolder(holder: FormatVH, position: Int) {
        val fmt = formats[position]
        val isSelected = position == selectedPosition

        holder.tvBadge.text = fmt.qualityBadge
        holder.tvLabel.text = fmt.qualityLabel
        holder.tvSize.text = fmt.sizeEstimate
        holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

        holder.cardRoot.background = holder.itemView.context.getDrawable(
            if (isSelected) R.drawable.format_card_selected_bg else R.drawable.format_card_bg
        )
        holder.tvBadge.setTextColor(
            holder.itemView.context.getColor(
                if (isSelected) R.color.accent_purple else R.color.text_secondary
            )
        )

        holder.cardRoot.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onSelect(fmt)
        }
    }

    fun getSelected(): FormatItem? = formats.getOrNull(selectedPosition)
}
