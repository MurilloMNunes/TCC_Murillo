package com.murillonunes.appsistdetec

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.EditText
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.auth.FirebaseAuth
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var adapter: DocumentoAdapter
    private lateinit var monitoradoAdapter: MonitoradoAdapter
    private val channelId = "realtime_updates_channel"
    private val listeners = mutableListOf<Pair<com.google.firebase.database.Query, ValueEventListener>>()
    private var posicoes: MutableList<String> = mutableListOf()
    //private var posicaodocumento: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Inicializar o Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Inicialize o Firebase Realtime Database
        db = FirebaseDatabase.getInstance()

        // Criar o Notification Channel para Android 8.0+
        createNotificationChannel()

        adapter = DocumentoAdapter(mutableListOf())

        val userTextView = findViewById<TextView>(R.id.userTextView)
        val documentIdEditText = findViewById<EditText>(R.id.documentIdEditText)
        val adicionarButton = findViewById<Button>(R.id.adicionarButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val logoutButton: Button = findViewById(R.id.logoutButton)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val buttonClose: Button = findViewById<Button>(R.id.buttonClose)
        val toolbar: Toolbar = findViewById(R.id.toolbar_home)
        val recyclerMonitorados = findViewById<RecyclerView>(R.id.recyclerMonitorados)

        monitoradoAdapter = MonitoradoAdapter(mutableListOf())
        recyclerMonitorados.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerMonitorados.adapter = monitoradoAdapter

        setSupportActionBar(toolbar)
        toolbar.overflowIcon = AppCompatResources.getDrawable(this,R.drawable.ic_baseline_more_vert_24)
        //supportActionBar?.setDisplayHomeAsUpEnabled(false)

        val user = auth.currentUser
        if (user != null) {
            // Exibir uma mensagem de boas-vindas com o e-mail do usuário
            //userTextView.text = "Usuário atual: ${user.email}"
            carregarDadosUsuario(user.uid, userTextView)
        } else {
            // Se o usuário não estiver autenticado, redirecionar para a tela de login
            Toast.makeText(this, "Usuário não autenticado", Toast.LENGTH_SHORT).show()
            finish()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        adicionarButton.setOnClickListener {
            val documentId = documentIdEditText.text.toString().trim()

            if (documentId.isNotEmpty()) {
                if ((posicoes.indexOf(documentId)) == -1) {
                    progressBar.visibility = View.VISIBLE
                    carregarEmTempoReal(documentId, progressBar)
                    documentIdEditText.text.clear()
                }
                else {
                    Toast.makeText(this,"Este identificador já está sendo monitorado", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Por favor, insira um identificador", Toast.LENGTH_SHORT).show()
            }
        }

        buttonClose.setOnClickListener {
            adapter.removerDocumentos()
            monitoradoAdapter.limpar()
            posicoes.clear()
            posicoes = mutableListOf()
            listeners.forEach { (query, listener) -> query.removeEventListener(listener) }
            listeners.clear()
        }

        // Configuração do botão de logout
        logoutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Logout realizado com sucesso!", Toast.LENGTH_SHORT).show()
            // Redirecionar para a tela de login
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun carregarEmTempoReal(documentId: String, progressBar: ProgressBar) {
        val query = db.getReference("teste1").child(documentId)

        val listener = object : ValueEventListener {

            // Flag para identificar e ignorar a primeira leitura do DB (para não exibir campos vazios na tela)
            private var PrimeiraLeitura = true

            override fun onDataChange(snapshot: DataSnapshot) {

                progressBar.visibility = View.GONE

                // Verifica se o identificador existe no DB
                if (!snapshot.exists()) {
                    Toast.makeText(this@HomeActivity, "Veículo da placa $documentId não encontrado", Toast.LENGTH_SHORT).show()
                    return
                }

                if(PrimeiraLeitura) {
                    PrimeiraLeitura = false

                    monitoradoAdapter.adicionar(documentId)

                    Toast.makeText(this@HomeActivity, "Veículo da placa $documentId sendo monitorado.", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    val documento = Documento(
                        id = documentId,
                        nome = snapshot.child("Nome").value?.toString(),
                        placa = snapshot.child("Placa").value?.toString(),
                        alerta = snapshot.child("Alerta").value?.toString(),
                        data = snapshot.child("Data").value?.toString(),
                        horario = snapshot.child("Horario").value?.toString(),
                        latitude = snapshot.child("Latitude").value?.toString(),
                        longitude = snapshot.child("Longitude").value?.toString(),
                    )

                    val index = posicoes.indexOf(documentId)

                    if (index == -1) {
                        adapter.adicionarDocumento(documento)
                        posicoes.add(documentId)
                    } else {
                        adapter.atualizarDocumento(index, documento)
                    }

                    showLocalNotification("Novo Alerta", "Confira o novo alerta")
                    //else {
                    //Toast.makeText(this@HomeActivity, "Veículo $documentId não encontrado", Toast.LENGTH_SHORT).show()
                    //}
                } catch (e: Exception) {
                    Log.e("RealtimeDB_Error", "Erro ao processar dados do Firebase: ${e.message}", e)
                    Toast.makeText(this@HomeActivity, "Erro no formato dos dados recebidos", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Log.e(
                    "RealtimeDB",
                    "Erro ao escutar mudanças: ${error.message}",
                    error.toException()
                )
                Toast.makeText(this@HomeActivity, "Erro de conexão com o banco de dados", Toast.LENGTH_SHORT).show()
            }
        }

        query.addValueEventListener(listener)

        if ((posicoes.indexOf(documentId)) == -1) {
            listeners.add(Pair(query, listener))
        }
    }

    private fun carregarDadosUsuario(userId: String, userTextView: TextView) {
        // Referência direta ao nó "usuarios/ID_DO_USUARIO"
        val userRef = db.getReference("usuarios").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Converter os dados do nó diretamente para o objeto Usuario
                    val usuario = snapshot.getValue(Usuario::class.java)

                    val nome = usuario?.nome ?: "Usuário"
                    // val email = usuario?.email ?: ""

                    // Exibe os dados na TextView
                    userTextView.text = "Usuário atual: $nome" //\nEmail: $email"
                } else {
                    Log.w("RealtimeDB", "Dados do usuário não encontrados")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RealtimeDB", "Erro ao carregar dados do usuário: ${error.message}")
            }
        })
    }

    private fun showLocalNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.warning_9392940)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Atualizações em Tempo Real",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificações locais de atualizações em tempo real"
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflar o menu (adicionar opções à Toolbar)
        menuInflater.inflate(R.menu.menu_principal, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Verificar qual item foi selecionado no menu
        return when (item.itemId) {
            android.R.id.home -> {
                // Ação para o ícone de "hamburger"
                true
            }
            R.id.menu_sair -> {
                // Ação para o item "Sair" (fechar o aplicativo ou realizar logout)
                auth.signOut()
                Toast.makeText(this, "Logout realizado com sucesso!", Toast.LENGTH_SHORT).show()
                // Redirecionar para a tela de login
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listeners.forEach { (query, listener) -> query.removeEventListener(listener) }
        listeners.clear()
    }
}

