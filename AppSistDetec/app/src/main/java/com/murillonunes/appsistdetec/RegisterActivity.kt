package com.murillonunes.appsistdetec

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializar o Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Inicialize o Firestore
        db = FirebaseDatabase.getInstance()

        // Referências aos componentes do layout
        val nomeEditText = findViewById<EditText>(R.id.nomeEditText)
        val emailEditText: EditText = findViewById(R.id.registerEmailEditText)
        val passwordEditText: EditText = findViewById(R.id.registerPasswordEditText)
        val registerButton: Button = findViewById(R.id.registerButton)
        val toolbar: Toolbar = findViewById(R.id.toolbar_register)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        registerButton.setOnClickListener {
            val nome = nomeEditText.text.toString()
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

        // Configuração do clique no botão de cadastro
        if (nome.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
        } else {
            cadastrarUsuario(nome,email,password)
            finish()
        }
    }
}

    private fun cadastrarUsuario(nome: String, email: String, senha: String) {
        auth.createUserWithEmailAndPassword(email, senha)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        salvarUsuarioRTDB(userId, nome, email)
                    }
                } else {
                    Toast.makeText(this, "Erro ao cadastrar: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun salvarUsuarioRTDB(userId: String, nome: String, email: String) {
        val usuario = Usuario(nome = nome, email = email)

        db.getReference("usuarios").child(userId)
            .setValue(usuario)
            .addOnSuccessListener {
                Toast.makeText(this, "Usuário cadastrado com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao salvar dados: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
