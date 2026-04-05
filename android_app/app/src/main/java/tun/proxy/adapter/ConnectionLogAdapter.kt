package tun.proxy.adapter

import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import tun.proxy.R
import tun.proxy.model.ConnectionEvent

class ConnectionLogAdapter(
    private var items: List<ConnectionEvent> = emptyList()
) : RecyclerView.Adapter<ConnectionLogAdapter.ViewHolder>() {

    fun submitList(newItems: List<ConnectionEvent>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot: View = itemView.findViewById(R.id.log_dot)
        private val action: TextView = itemView.findViewById(R.id.log_action)
        private val detail: TextView = itemView.findViewById(R.id.log_detail)
        private val time: TextView = itemView.findViewById(R.id.log_time)

        fun bind(event: ConnectionEvent) {
            val ctx = itemView.context
            val colorRes = when (event.action) {
                "Connected" -> R.color.colorSuccess
                "Disconnected" -> R.color.colorOnBackground
                "Failed" -> R.color.colorError
                else -> R.color.colorWarning
            }
            (dot.background as? GradientDrawable)?.setColor(ContextCompat.getColor(ctx, colorRes))

            val label = "${event.action} — ${event.protocol.uppercase()}"
            action.text = label

            if (!event.error.isNullOrEmpty()) {
                detail.text = event.error
                detail.visibility = View.VISIBLE
            } else if (event.duration != null && event.duration > 0) {
                val mins = event.duration / 60000
                val secs = (event.duration % 60000) / 1000
                detail.text = if (mins > 0) "Duration: ${mins}m ${secs}s" else "Duration: ${secs}s"
                detail.visibility = View.VISIBLE
            } else {
                detail.visibility = View.GONE
            }

            time.text = DateUtils.getRelativeTimeSpanString(
                event.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
        }
    }
}
