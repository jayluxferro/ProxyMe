package tun.proxy.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tun.proxy.R
import tun.proxy.model.ProxyConfig

class SavedConfigsAdapter(
    private val onUseClick: (ProxyConfig) -> Unit,
    private val onEditClick: (ProxyConfig) -> Unit,
    private val onShareClick: (ProxyConfig) -> Unit,
    private val onDeleteClick: (ProxyConfig) -> Unit
) : ListAdapter<ProxyConfig, SavedConfigsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.config_name)
        private val address: TextView = itemView.findViewById(R.id.config_address)
        private val typeBadge: TextView = itemView.findViewById(R.id.config_type_badge)
        private val shareBtn: ImageButton = itemView.findViewById(R.id.btn_share)
        private val editBtn: ImageButton = itemView.findViewById(R.id.btn_edit)
        private val deleteBtn: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(config: ProxyConfig) {
            name.text = config.name
            address.text = config.displayAddress
            typeBadge.text = config.protocol.uppercase()

            itemView.setOnClickListener { onUseClick(config) }
            shareBtn.setOnClickListener { onShareClick(config) }
            editBtn.setOnClickListener { onEditClick(config) }
            deleteBtn.setOnClickListener { onDeleteClick(config) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ProxyConfig>() {
        override fun areItemsTheSame(oldItem: ProxyConfig, newItem: ProxyConfig): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProxyConfig, newItem: ProxyConfig): Boolean {
            return oldItem == newItem
        }
    }
}
