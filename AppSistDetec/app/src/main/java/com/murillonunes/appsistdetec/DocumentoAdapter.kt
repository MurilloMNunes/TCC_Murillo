package com.murillonunes.appsistdetec

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

data class Documento(val id: String, val nome: String?, val placa: String?, val alerta: String?, val data: String?, val horario: String?, var latitude: String?, var longitude: String?)

class DocumentoAdapter(private val documentos: MutableList<Documento>) : RecyclerView.Adapter<DocumentoAdapter.DocumentoViewHolder>() {

    class DocumentoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        //val buttonRemoveCard: ImageButton = itemView.findViewById(R.id.buttonRemoveCard)
        val nomeTextView: TextView = itemView.findViewById(R.id.nomeTextView)
        val placaTextView: TextView = itemView.findViewById(R.id.placaTextView)
        val alertaTextView: TextView = itemView.findViewById(R.id.alertaTextView)
        val dataTextView: TextView = itemView.findViewById(R.id.dataTextView)
        val horarioTextView: TextView = itemView.findViewById(R.id.horarioTextView)
        val latitudeTextView: TextView = itemView.findViewById(R.id.latitudeTextView)
        val longitudeTextView: TextView = itemView.findViewById(R.id.longitudeTextView)
        //val buttonClose: Button = itemView.findViewById(R.id.buttonClose)

        fun mapsBtn(userLatitude: String, userLongitude: String) {
            val buttonMaps: Button = itemView.findViewById(R.id.buttonMaps)

            buttonMaps.setOnClickListener {
                if ((userLatitude != "Latitude não encontrada") && (userLongitude != "Longitude não encontrada")) {
                    val gmmIntentUri = Uri.parse("geo:$userLatitude,$userLongitude")  // Coordenadas para São Paulo, Brasil
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")  // Garantir que o Google Maps seja aberto
                    Toast.makeText(itemView.context, "abrindo", Toast.LENGTH_SHORT).show()
                    if (mapIntent.resolveActivity(itemView.context.packageManager) != null) {
                        itemView.context.startActivity(mapIntent)
                    } else {
                        Toast.makeText(itemView.context, "Nao foi possível abrir", Toast.LENGTH_SHORT).show()
                    }
                }
                else {
                    Toast.makeText(itemView.context, "Localização não disponível", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /*fun closeBtn(documentos: MutableList<Documento>, position: Int) {
            val buttonClose: Button = itemView.findViewById(R.id.buttonClose)

            buttonClose.setOnClickListener {
                documentos.remove(position - 1)
                noti
            }
        }*/
    }

    class UserPosition {
        var lat: String? = ""
        var lng: String? = ""

        fun changePosition(newLat: String?, newLng: String?) {
            lat = newLat
            lng = newLng
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_documento, parent, false)
        return DocumentoViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentoViewHolder, position: Int) {
        val userposition = UserPosition()
        val documento = documentos[position]
        holder.nomeTextView.text = "Nome: ${documento.nome ?: "Nome não encontrado"}"
        holder.placaTextView.text = "Placa: ${documento.placa ?: "Placa não encontrado"}"
        holder.alertaTextView.text = "Alerta: ${documento.alerta ?: "Alerta não encontrado"}"
        holder.dataTextView.text = "Data: ${documento.data ?: "Data não encontrada"}"
        holder.horarioTextView.text = "Horário: ${documento.horario ?: "Horário não encontrado"}"
        holder.latitudeTextView.text =
            "Latitude: ${documento.latitude ?: "Latitude não encontrada"}"
        holder.longitudeTextView.text =
            "Longitude: ${documento.longitude ?: "Longitude não encontrada"}"
        userposition.changePosition(
            documento.latitude ?: "Latitude não encontrada",
            documento.longitude ?: "Longitude não encontrada"
        )
        //holder.closeBtn(documentos, position)
        holder.mapsBtn(userposition.lat.toString(), userposition.lng.toString())
//        holder.buttonRemoveCard.setOnClickListener {
//            val currentPos = holder.bindingAdapterPosition
//            if (currentPos != RecyclerView.NO_POSITION) {
//                documentos.removeAt(currentPos)
//                notifyItemRemoved(currentPos)
//                onItemRemoved(documento, currentPos)
//            }
//        }
    }

    override fun getItemCount() = documentos.size

    fun adicionarDocumento(documento: Documento) {
        documentos.add(documento)
        notifyItemInserted(documentos.size - 1)
    }

    fun atualizarDocumento(posicao: Int, documento: Documento) {
        documentos[(posicao - 1)] = documento
        notifyItemChanged((posicao - 1))
    }

    fun removerDocumentos() {
        val total_itens = itemCount
        documentos.clear()
        notifyItemRangeRemoved(0,total_itens)
    }

}
