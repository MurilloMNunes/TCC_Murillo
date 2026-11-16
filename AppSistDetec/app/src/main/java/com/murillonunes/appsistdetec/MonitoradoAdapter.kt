package com.murillonunes.appsistdetec

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MonitoradoAdapter(
    private val listaMonitorados: MutableList<String>
) : RecyclerView.Adapter<MonitoradoAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val idTextView: TextView = itemView.findViewById(R.id.textIdentificador)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monitorado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.idTextView.text = "Veículo da Placa ${listaMonitorados[position]} em monitoramento"
    }

    override fun getItemCount(): Int = listaMonitorados.size

    fun adicionar(id: String) {
        if (!listaMonitorados.contains(id)) {
            listaMonitorados.add(id)
            notifyItemInserted(listaMonitorados.size - 1)
        }
    }

    fun limpar() {
        val tamanho = listaMonitorados.size
        listaMonitorados.clear()
        notifyItemRangeRemoved(0, tamanho)
    }
}