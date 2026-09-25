package com.example.hvstudio

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hvstudio.databinding.ActivityMainBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * MainActivity (Login & Autenticação)
 * Permite: entrar com e-mail, criar conta, recuperar palavra-passe
 * e entrar como convidado (login anónimo).
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding: dá acesso direto aos elementos do XML
    private lateinit var binding: ActivityMainBinding

    // Ponto de acesso ao Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // true = ecrã em modo "Criar conta" | false = ecrã em modo "Entrar"
    private var isRegisterMode = false

    // ---------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Garante a inicialização do Firebase para evitar a IllegalStateException
        FirebaseApp.initializeApp(this)

        // Liga o layout XML a esta Activity
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializa o Firebase Auth
        auth = FirebaseAuth.getInstance()

        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        // Se já existe sessão iniciada (conta ou convidado), avança na app
        if (auth.currentUser != null) {
            goToMain()
        }
    }

    // ---------------------------------------------------------------
    // Cliques nos botões e links
    // ---------------------------------------------------------------

    private fun setupListeners() {

        // Botão principal: faz login OU registo, conforme o modo atual
        binding.btnPrimary.setOnClickListener {
            if (validateFields()) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()

                if (isRegisterMode) registerUser(email, password)
                else loginUser(email, password)
            }
        }

        // Link para alternar entre "Entrar" e "Criar conta"
        binding.tvToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateModeUi()
        }

        // Link "Esqueci-me da palavra-passe"
        binding.tvForgotPassword.setOnClickListener {
            resetPassword()
        }

        // Botão "Entrar como convidado"
        binding.btnGuest.setOnClickListener {
            loginAsGuest()
        }
    }

    /**
     * Atualiza textos e campos visíveis conforme o modo (Entrar / Criar conta).
     */
    private fun updateModeUi() {
        clearErrors()

        if (isRegisterMode) {
            binding.tvTitle.text = "Criar conta"
            binding.btnPrimary.text = "Criar conta"
            binding.tvToggleMode.text = "Já tens conta? Entrar"
            binding.tilConfirmPassword.visibility = View.VISIBLE // mostra "confirmar"
            binding.tvForgotPassword.visibility = View.GONE      // esconde "esqueci-me"
        } else {
            binding.tvTitle.text = "Entrar na tua conta"
            binding.btnPrimary.text = "Entrar"
            binding.tvToggleMode.text = "Ainda não tens conta? Criar conta"
            binding.tilConfirmPassword.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.VISIBLE
        }
    }

    // ---------------------------------------------------------------
    // Validação dos campos
    // ---------------------------------------------------------------

    private fun validateFields(): Boolean {
        clearErrors()

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        var valid = true

        if (email.isEmpty()) {
            binding.tilEmail.error = "Escreve o teu e-mail"
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "E-mail inválido"
            valid = false
        }

        if (password.length < 6) {
            binding.tilPassword.error = "Mínimo de 6 caracteres"
            valid = false
        }

        if (isRegisterMode) {
            val confirm = binding.etConfirmPassword.text.toString()
            if (confirm != password) {
                binding.tilConfirmPassword.error = "As palavras-passe não coincidem"
                valid = false
            }
        }

        return valid
    }

    private fun clearErrors() {
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    // ---------------------------------------------------------------
    // Operações com o Firebase Authentication
    // ---------------------------------------------------------------

    private fun loginUser(email: String, password: String) {
        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    goToMain()
                } else {
                    showAuthError(task.exception)
                }
            }
    }

    private fun registerUser(email: String, password: String) {
        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                    goToMain()
                } else {
                    showAuthError(task.exception)
                }
            }
    }

    private fun loginAsGuest() {
        setLoading(true)
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    goToMain()
                } else {
                    showAuthError(task.exception)
                }
            }
    }

    private fun resetPassword() {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Escreve o teu e-mail para recuperar a palavra-passe"
            return
        }

        setLoading(true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Enviámos um e-mail para redefinires a palavra-passe.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showAuthError(task.exception)
                }
            }
    }

    // ---------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------

    private fun showAuthError(exception: Exception?) {
        val message = when (exception) {
            is FirebaseAuthWeakPasswordException -> "Palavra-passe demasiado fraca."
            is FirebaseAuthUserCollisionException -> "Já existe uma conta com este e-mail."
            is FirebaseAuthInvalidUserException -> "Não existe conta com este e-mail."
            is FirebaseAuthInvalidCredentialsException -> "E-mail ou palavra-passe incorretos."
            else -> "Erro: ${exception?.localizedMessage ?: "tenta novamente."}"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnPrimary.isEnabled = !loading
        binding.btnGuest.isEnabled = !loading
        binding.tvToggleMode.isEnabled = !loading
        binding.tvForgotPassword.isEnabled = !loading
    }

    private fun goToMain() {
        Toast.makeText(this, "Sessão iniciada com sucesso!", Toast.LENGTH_SHORT).show()
    }
}