package com.denkasir.ui.account

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.denkasir.R
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.DigitalConfigEntity
import com.denkasir.data.local.entity.StoreEntity
import com.denkasir.data.local.entity.UserEntity
import com.denkasir.databinding.*
import com.denkasir.hardware.printer.PrinterSettingActivity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var appDatabase: AppDatabase

    private var currentStore: StoreEntity? = null
    private var allUsers: List<UserEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager.getInstance(requireContext())
        appDatabase = AppDatabase.getDatabase(requireContext())

        setupUserRoleView()
        observeStoreProfile()
        observeUsers()
        setupListeners()
    }

    private fun setupUserRoleView() {
        val isOwner = sessionManager.isOwner()
        val fullName = sessionManager.getCurrentFullName()
        val username = sessionManager.getCurrentUsername()

        binding.tvUserFullName.text = fullName
        binding.tvUsername.text = "@$username"

        if (isOwner) {
            binding.tvUserRoleBadge.text = "OWNER"
            binding.tvUserRoleBadge.setTextColor(Color.parseColor("#3E24E2"))
            binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_owner)
            binding.ivUserAvatar.setImageResource(R.drawable.ic_person)
            binding.ivUserAvatar.setColorFilter(Color.parseColor("#3E24E2"))

            binding.btnEditStoreProfile.visibility = View.VISIBLE
            binding.btnManageCashiers.visibility = View.VISIBLE
            binding.layoutKasirLockBanner.visibility = View.GONE
        } else {
            binding.tvUserRoleBadge.text = "KASIR"
            binding.tvUserRoleBadge.setTextColor(Color.parseColor("#0284C7"))
            binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_kasir)
            binding.ivUserAvatar.setImageResource(R.drawable.ic_cash_register)
            binding.ivUserAvatar.setColorFilter(Color.parseColor("#0284C7"))

            binding.btnEditStoreProfile.visibility = View.GONE
            binding.btnManageCashiers.visibility = View.GONE
            binding.layoutKasirLockBanner.visibility = View.VISIBLE
        }
    }

    private fun observeStoreProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.storeDao().getStoreFlow().collectLatest { store ->
                if (store != null) {
                    currentStore = store
                    binding.tvStoreNameVal.text = store.name
                    binding.tvStoreAddressVal.text = store.address ?: "Belum diatur"
                    binding.tvStorePhoneVal.text = store.phone ?: "Belum diatur"
                    sessionManager.saveStoreInfo(store.name, store.address, store.phone)
                } else {
                    binding.tvStoreNameVal.text = sessionManager.getStoreName()
                    binding.tvStoreAddressVal.text = sessionManager.getStoreAddress()
                    binding.tvStorePhoneVal.text = sessionManager.getStorePhone()
                }
            }
        }
    }

    private fun observeUsers() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.userDao().getAllUsers().collectLatest { users ->
                allUsers = users
            }
        }
    }

    private fun setupListeners() {
        binding.btnSwitchRole.setOnClickListener {
            showLoginRoleDialog()
        }

        binding.btnEditStoreProfile.setOnClickListener {
            if (sessionManager.isOwner()) {
                showEditStoreDialog()
            } else {
                Toast.makeText(requireContext(), "Hanya OWNER yang dapat mengubah profil toko!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManageCashiers.setOnClickListener {
            if (sessionManager.isOwner()) {
                showManageCashiersDialog()
            } else {
                Toast.makeText(requireContext(), "Hanya OWNER yang dapat mengelola akun kasir!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStockAlertRule.setOnClickListener {
            if (sessionManager.isOwner()) {
                showStockAlertRuleDialog()
            } else {
                Toast.makeText(requireContext(), "Hanya OWNER yang dapat mengatur aturan batas stok opname!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnH2HConfig.setOnClickListener {
            if (sessionManager.isOwner()) {
                showH2hConfigDialog()
            } else {
                Toast.makeText(requireContext(), "Hanya OWNER yang dapat mengubah konfigurasi server H2H!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBluetoothPrinter.setOnClickListener {
            startActivity(Intent(requireContext(), PrinterSettingActivity::class.java))
        }

        binding.btnCloudSync.setOnClickListener {
            Toast.makeText(requireContext(), "Sinkronisasi data ke Cloud FastAPI dimulai...", Toast.LENGTH_SHORT).show()
        }

        binding.btnBackupRestore.setOnClickListener {
            if (sessionManager.isOwner()) {
                Toast.makeText(requireContext(), "Backup database lokal & Excel (.xlsx) selesai", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Hanya OWNER yang memiliki akses backup & restore!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoginRoleDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogLoginRoleBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var selectedRole = sessionManager.getCurrentRole()

        fun updateRoleSelectionUI(role: String) {
            selectedRole = role
            if (role == SessionManager.ROLE_OWNER) {
                dialogBinding.cardRoleOwner.setCardBackgroundColor(Color.parseColor("#EBEDFE"))
                dialogBinding.cardRoleOwner.strokeColor = Color.parseColor("#3E24E2")
                dialogBinding.cardRoleOwner.strokeWidth = 4
                dialogBinding.rbOwner.isChecked = true

                dialogBinding.cardRoleKasir.setCardBackgroundColor(Color.WHITE)
                dialogBinding.cardRoleKasir.strokeColor = Color.parseColor("#CBD5E1")
                dialogBinding.cardRoleKasir.strokeWidth = 2
                dialogBinding.rbKasir.isChecked = false
            } else {
                dialogBinding.cardRoleKasir.setCardBackgroundColor(Color.parseColor("#E0F2FE"))
                dialogBinding.cardRoleKasir.strokeColor = Color.parseColor("#0284C7")
                dialogBinding.cardRoleKasir.strokeWidth = 4
                dialogBinding.rbKasir.isChecked = true

                dialogBinding.cardRoleOwner.setCardBackgroundColor(Color.WHITE)
                dialogBinding.cardRoleOwner.strokeColor = Color.parseColor("#CBD5E1")
                dialogBinding.cardRoleOwner.strokeWidth = 2
                dialogBinding.rbOwner.isChecked = false
            }
        }

        updateRoleSelectionUI(selectedRole)

        dialogBinding.cardRoleOwner.setOnClickListener {
            updateRoleSelectionUI(SessionManager.ROLE_OWNER)
        }

        dialogBinding.cardRoleKasir.setOnClickListener {
            updateRoleSelectionUI(SessionManager.ROLE_KASIR)
        }

        dialogBinding.btnCancelLogin.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmLogin.setOnClickListener {
            sessionManager.switchRole(selectedRole)
            setupUserRoleView()
            dialog.dismiss()
            val roleName = if (selectedRole == SessionManager.ROLE_OWNER) "OWNER (Pemilik Toko)" else "KASIR (Staff Kasir)"
            Toast.makeText(requireContext(), "Berhasil masuk sebagai $roleName", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditStoreDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogEditStoreProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentName = currentStore?.name ?: sessionManager.getStoreName()
        val currentAddress = currentStore?.address ?: sessionManager.getStoreAddress()
        val currentPhone = currentStore?.phone ?: sessionManager.getStorePhone()

        dialogBinding.etStoreName.setText(currentName)
        dialogBinding.etStoreAddress.setText(currentAddress)
        dialogBinding.etStorePhone.setText(currentPhone)

        dialogBinding.btnCancelEditStore.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveEditStore.setOnClickListener {
            val newName = dialogBinding.etStoreName.text.toString().trim()
            val newAddress = dialogBinding.etStoreAddress.text.toString().trim()
            val newPhone = dialogBinding.etStorePhone.text.toString().trim()

            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Nama toko tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val storeId = currentStore?.id ?: "default_store"
                val updatedEntity = StoreEntity(
                    id = storeId,
                    name = newName,
                    address = newAddress,
                    phone = newPhone,
                    ownerId = "owner_1",
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )
                appDatabase.storeDao().insertStore(updatedEntity)
                sessionManager.saveStoreInfo(newName, newAddress, newPhone)

                withContext(Dispatchers.Main) {
                    binding.tvStoreNameVal.text = newName
                    binding.tvStoreAddressVal.text = if (newAddress.isNotEmpty()) newAddress else "Belum diatur"
                    binding.tvStorePhoneVal.text = if (newPhone.isNotEmpty()) newPhone else "Belum diatur"

                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Profil toko '$newName' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showManageCashiersDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogManageCashiersBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val cashierAdapter = CashierUserAdapter { selectedUser ->
            Toast.makeText(requireContext(), "Staf: ${selectedUser.fullName} (@${selectedUser.username})", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.rvCashierUsersList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cashierAdapter
        }

        val cashiers = allUsers.filter { it.role == "KASIR" }
        cashierAdapter.submitList(cashiers)

        dialogBinding.btnAddCashierUser.setOnClickListener {
            dialog.dismiss()
            showAddCashierDialog()
        }

        dialogBinding.btnCloseCashiers.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddCashierDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAddCashierBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.btnCancelAddCashier.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveCashierUser.setOnClickListener {
            val fullName = dialogBinding.etCashierFullName.text.toString().trim()
            val username = dialogBinding.etCashierUsername.text.toString().trim().lowercase()

            if (fullName.isEmpty() || username.isEmpty()) {
                Toast.makeText(requireContext(), "Nama lengkap dan username wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val newUser = UserEntity(
                    id = UUID.randomUUID().toString(),
                    storeId = "default_store",
                    username = username,
                    fullName = fullName,
                    role = "KASIR",
                    createdAt = System.currentTimeMillis()
                )
                appDatabase.userDao().insertUser(newUser)

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Akun kasir '$fullName' (@$username) berhasil dibuat!", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showStockAlertRuleDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogStockAlertRuleBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentThreshold = sessionManager.getMinStockThreshold()
        val isAutoDraft = sessionManager.isAutoDraftEnabled()
        val defaultSupplier = sessionManager.getDefaultSupplier()
        val reorderQty = sessionManager.getAutoReorderQty()

        dialogBinding.etMinStockThreshold.setText(currentThreshold.toInt().toString())
        dialogBinding.switchAutoDraft.isChecked = isAutoDraft
        dialogBinding.etDefaultSupplier.setText(defaultSupplier)
        dialogBinding.etAutoReorderQty.setText(reorderQty.toInt().toString())

        dialogBinding.btnCancelStockRule.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveStockRule.setOnClickListener {
            val threshold = dialogBinding.etMinStockThreshold.text.toString().toDoubleOrNull() ?: 5.0
            val autoDraft = dialogBinding.switchAutoDraft.isChecked
            val supplier = dialogBinding.etDefaultSupplier.text.toString().trim().ifEmpty { "CV. Sumber Makmur" }
            val qty = dialogBinding.etAutoReorderQty.text.toString().toDoubleOrNull() ?: 20.0

            sessionManager.saveStockAlertRule(threshold, autoDraft, supplier, qty)
            dialog.dismiss()
            Toast.makeText(requireContext(), "Aturan stok opname (Batas:  unit) berhasil disimpan!", Toast.LENGTH_LONG).show()
        }

        dialog.show()
    }

    private fun showH2hConfigDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogH2hConfigBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val config = appDatabase.digitalConfigDao().getConfig()
            withContext(Dispatchers.Main) {
                if (config != null) {
                    dialogBinding.etH2hApiUrl.setText(config.apiUrl)
                    dialogBinding.etH2hApiKey.setText(config.apiKey)
                    dialogBinding.etH2hMemberId.setText(config.memberId)
                    dialogBinding.etH2hPin.setText(config.pinPassword)
                    dialogBinding.etH2hBalance.setText(config.balance.toInt().toString())
                }
            }
        }

        dialogBinding.btnCancelH2h.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveH2h.setOnClickListener {
            val url = dialogBinding.etH2hApiUrl.text.toString().trim()
            val apiKey = dialogBinding.etH2hApiKey.text.toString().trim()
            val memberId = dialogBinding.etH2hMemberId.text.toString().trim()
            val pin = dialogBinding.etH2hPin.text.toString().trim()
            val balance = dialogBinding.etH2hBalance.text.toString().toDoubleOrNull() ?: 0.0

            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "URL Endpoint API wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val config = DigitalConfigEntity(
                    id = "default_config",
                    providerName = "OtomaX H2H Server",
                    apiUrl = url,
                    apiKey = apiKey,
                    memberId = memberId,
                    pinPassword = pin,
                    balance = balance,
                    isActive = true,
                    updatedAt = System.currentTimeMillis()
                )
                appDatabase.digitalConfigDao().saveConfig(config)

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Konfigurasi API H2H & Saldo (${CurrencyUtils.formatRupiah(balance)}) berhasil disimpan!", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
