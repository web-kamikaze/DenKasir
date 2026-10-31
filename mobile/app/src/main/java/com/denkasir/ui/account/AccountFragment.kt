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
import com.denkasir.data.repository.SyncRepository
import com.denkasir.databinding.*
import com.denkasir.hardware.printer.PrinterSettingActivity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
            showCloudSyncDialog()
        }

        binding.btnBackupRestore.setOnClickListener {
            if (sessionManager.isOwner()) {
                startActivity(Intent(requireContext(), com.denkasir.ui.backup.DataManagementActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "Hanya OWNER yang memiliki akses ke Pengelolaan Data!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCloudSyncDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogCloudSyncBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentUrl = sessionManager.getServerUrl()
        val lastSync = sessionManager.getLastSyncTime()

        dialogBinding.etServerUrl.setText(currentUrl)

        if (lastSync > 0) {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))
            dialogBinding.tvLastSyncInfo.text = "Terakhir Sinkron: ${sdf.format(Date(lastSync))}"
        } else {
            dialogBinding.tvLastSyncInfo.text = "Terakhir Sinkron: Belum pernah sinkron"
        }

        dialogBinding.btnPresetLocal.setOnClickListener {
            dialogBinding.etServerUrl.setText("http://10.0.2.2:8000/")
        }

        dialogBinding.btnPresetLan.setOnClickListener {
            dialogBinding.etServerUrl.setText("http://192.168.1.100:8000/")
        }

        dialogBinding.btnPresetCloud.setOnClickListener {
            dialogBinding.etServerUrl.setText("https://api.denkasir.com/")
        }

        dialogBinding.btnCancelSync.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDoSyncNow.setOnClickListener {
            val url = dialogBinding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "URL Endpoint Server wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sessionManager.saveServerUrl(url)
            dialogBinding.progressBarSync.visibility = View.VISIBLE
            dialogBinding.btnDoSyncNow.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val repo = SyncRepository(requireContext())
                val result = repo.performFullSync()

                dialogBinding.progressBarSync.visibility = View.GONE
                dialogBinding.btnDoSyncNow.isEnabled = true

                if (result.isSuccess) {
                    val body = result.getOrNull()
                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))
                    val timeStr = sdf.format(Date(body?.serverTimestamp ?: System.currentTimeMillis()))
                    dialogBinding.tvLastSyncInfo.text = "Terakhir Sinkron: $timeStr (Berhasil)"

                    val counts = body?.syncedCounts?.entries?.joinToString(", ") { "${it.key}: ${it.value}" } ?: ""
                    Toast.makeText(requireContext(), "✓ Sinkronisasi Cloud Berhasil! ($counts)", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Gagal terhubung ke server"
                    Toast.makeText(requireContext(), "Gagal Sinkronisasi: $err", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showLoginRoleDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogLoginRoleBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var selectedRole = sessionManager.getCurrentRole()

        fun updateRoleSelectionUI(role: String) {
            if (role == SessionManager.ROLE_OWNER) {
                dialogBinding.cardRoleOwner.strokeColor = Color.parseColor("#3E24E2")
                dialogBinding.cardRoleOwner.strokeWidth = 4
                dialogBinding.cardRoleOwner.setCardBackgroundColor(Color.parseColor("#F5F3FF"))
                dialogBinding.rbOwner.isChecked = true

                dialogBinding.cardRoleKasir.strokeColor = Color.parseColor("#E2E8F0")
                dialogBinding.cardRoleKasir.strokeWidth = 2
                dialogBinding.cardRoleKasir.setCardBackgroundColor(Color.WHITE)
                dialogBinding.rbKasir.isChecked = false
            } else {
                dialogBinding.cardRoleKasir.strokeColor = Color.parseColor("#0284C7")
                dialogBinding.cardRoleKasir.strokeWidth = 4
                dialogBinding.cardRoleKasir.setCardBackgroundColor(Color.parseColor("#F0F9FF"))
                dialogBinding.rbKasir.isChecked = true

                dialogBinding.cardRoleOwner.strokeColor = Color.parseColor("#E2E8F0")
                dialogBinding.cardRoleOwner.strokeWidth = 2
                dialogBinding.cardRoleOwner.setCardBackgroundColor(Color.WHITE)
                dialogBinding.rbOwner.isChecked = false
            }
        }

        updateRoleSelectionUI(selectedRole)

        dialogBinding.cardRoleOwner.setOnClickListener {
            selectedRole = SessionManager.ROLE_OWNER
            updateRoleSelectionUI(selectedRole)
        }

        dialogBinding.cardRoleKasir.setOnClickListener {
            selectedRole = SessionManager.ROLE_KASIR
            updateRoleSelectionUI(selectedRole)
        }

        dialogBinding.btnCancelLogin.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmLogin.setOnClickListener {
            sessionManager.switchRole(selectedRole)
            setupUserRoleView()
            dialog.dismiss()
            Toast.makeText(requireContext(), "Berhasil beralih mode ke $selectedRole", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditStoreDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogEditStoreProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentName = binding.tvStoreNameVal.text.toString()
        val currentAddr = currentStore?.address ?: ""
        val currentPhone = currentStore?.phone ?: ""

        dialogBinding.etStoreName.setText(currentName)
        dialogBinding.etStoreAddress.setText(currentAddr)
        dialogBinding.etStorePhone.setText(currentPhone)

        dialogBinding.btnCancelEditStore.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveEditStore.setOnClickListener {
            val name = dialogBinding.etStoreName.text.toString().trim()
            val addr = dialogBinding.etStoreAddress.text.toString().trim()
            val phone = dialogBinding.etStorePhone.text.toString().trim()

            if (name.isEmpty()) {
                dialogBinding.etStoreName.error = "Nama toko wajib diisi!"
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val storeId = currentStore?.id ?: "default_store"
                val updatedStore = StoreEntity(
                    id = storeId,
                    name = name,
                    address = addr,
                    phone = phone,
                    ownerId = "owner_1",
                    updatedAt = System.currentTimeMillis()
                )
                appDatabase.storeDao().insertStore(updatedStore)

                withContext(Dispatchers.Main) {
                    sessionManager.saveStoreInfo(name, addr, phone)
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Profil Toko berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showManageCashiersDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogManageCashiersBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val cashierAdapter = CashierUserAdapter(
            onCashierSelected = { user ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Hapus Kasir")
                    .setMessage("Yakin ingin menghapus kasir '${user.fullName}' (@${user.username})?")
                    .setPositiveButton("Hapus") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            appDatabase.userDao().deleteUser(user)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )

        dialogBinding.rvCashierUsersList.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvCashierUsersList.adapter = cashierAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.userDao().getCashiers().collectLatest { cashiers ->
                cashierAdapter.submitList(cashiers)
            }
        }

        dialogBinding.btnAddCashierUser.setOnClickListener {
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

            if (fullName.isEmpty()) {
                dialogBinding.etCashierFullName.error = "Nama Lengkap wajib diisi!"
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                dialogBinding.etCashierUsername.error = "Username wajib diisi!"
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
            Toast.makeText(requireContext(), "Aturan stok opname (Batas: ${threshold.toInt()} unit) berhasil disimpan!", Toast.LENGTH_LONG).show()
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
