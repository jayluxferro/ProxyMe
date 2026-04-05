package tun.proxy.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tun.proxy.R
import tun.proxy.model.ProxyConfig

class RotationConfigAdapter(
    private val onRemoveClick: (ProxyConfig) -> Unit
) : RecyclerView.Adapter<RotationConfigAdapter.ViewHolder>() {

    private val items = mutableListOf<ProxyConfig>()

    fun submitList(configs: List<ProxyConfig>) {
        items.clear()
        items.addAll(configs)
        notifyDataSetChanged()
    }

    fun getConfigIds(): List<String> = items.map { it.id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rotation_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val index: TextView = itemView.findViewById(R.id.rotation_index)
        private val name: TextView = itemView.findViewById(R.id.rotation_config_name)
        private val badge: TextView = itemView.findViewById(R.id.rotation_config_badge)
        private val removeBtn: ImageButton = itemView.findViewById(R.id.btn_remove_from_rotation)

        fun bind(config: ProxyConfig, position: Int) {
            index.text = "${position + 1}."
            name.text = config.name
            badge.text = config.protocol.uppercase()
            removeBtn.setOnClickListener { onRemoveClick(config) }
        }
    }
}
