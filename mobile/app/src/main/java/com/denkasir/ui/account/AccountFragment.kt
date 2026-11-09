package com.denkasir.ui.account

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.denkasir.utils.DistributorRule
import com.denkasir.utils.SmsGatewayHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
            binding.btnStockAlertRule.visibility = View.VISIBLE
            binding.btnH2HConfig.visibility = View.VISIBLE
            binding.btnBackupRestore.visibility = View.VISIBLE
            binding.layoutKasirLockBanner.visibility = View.GONE
        } else {
            binding.tvUserRoleBadge.text = "KASIR"
            binding.tvUserRoleBadge.setTextColor(Color.parseColor("#0284C7"))
            binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_kasir)
            binding.ivUserAvatar.setImageResource(R.drawable.ic_cash_register)
            binding.ivUserAvatar.setColorFilter(Color.parseColor("#0284C7"))

            binding.btnEditStoreProfile.visibility = View.GONE
            binding.btnManageCashiers.visibility = View.GONE
            binding.btnStockAlertRule.visibility = View.GONE
            binding.btnH2HConfig.visibility = View.GONE
            binding.btnBackupRestore.visibility = View.GONE
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
        var cashiersList: List<UserEntity> = emptyList()

        // Ambil daftar akun kasir aktif untuk dropdown
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    appDatabase.userDao().getCashiers().first()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            cashiersList = if (list.isNotEmpty()) {
                list
            } else {
                listOf(
                    UserEntity(
                        id = "kasir_1",
                        storeId = "default_store",
                        username = "kasir",
                        fullName = "Kasir 1 (Staff)",
                        role = "KASIR"
                    )
                )
            }

            val spinnerLabels = cashiersList.map { "${it.fullName} (@${it.username})" }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                spinnerLabels
            )
            dialogBinding.spCashierList.adapter = adapter

            // Jika sedang login sebagai kasir tertentu, posisikan spinner pada kasir tersebut
            val currentUserId = sessionManager.getCurrentUserId()
            val preselectedIndex = cashiersList.indexOfFirst { it.id == currentUserId }
            if (preselectedIndex >= 0) {
                dialogBinding.spCashierList.setSelection(preselectedIndex)
            }
        }

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

                // Tampilkan input password jika saat ini pengguna sedang berstatus KASIR
                if (sessionManager.isKasir()) {
                    dialogBinding.layoutOwnerPasswordAuth.visibility = View.VISIBLE
                } else {
                    dialogBinding.layoutOwnerPasswordAuth.visibility = View.GONE
                }
                dialogBinding.layoutCashierSelect.visibility = View.GONE
            } else {
                dialogBinding.cardRoleKasir.strokeColor = Color.parseColor("#0284C7")
                dialogBinding.cardRoleKasir.strokeWidth = 4
                dialogBinding.cardRoleKasir.setCardBackgroundColor(Color.parseColor("#F0F9FF"))
                dialogBinding.rbKasir.isChecked = true

                dialogBinding.cardRoleOwner.strokeColor = Color.parseColor("#E2E8F0")
                dialogBinding.cardRoleOwner.strokeWidth = 2
                dialogBinding.cardRoleOwner.setCardBackgroundColor(Color.WHITE)
                dialogBinding.rbOwner.isChecked = false

                dialogBinding.layoutOwnerPasswordAuth.visibility = View.GONE
                dialogBinding.layoutCashierSelect.visibility = View.VISIBLE
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
            if (selectedRole == SessionManager.ROLE_OWNER) {
                // Verifikasi keamanan: kasir tidak boleh eskalasi ke OWNER tanpa password
                if (sessionManager.isKasir()) {
                    val inputPass = dialogBinding.etOwnerPassword.text.toString().trim()
                    if (inputPass.isEmpty()) {
                        dialogBinding.tilOwnerPassword.error = "Password / PIN Owner wajib diisi!"
                        return@setOnClickListener
                    }
                    if (!sessionManager.verifyOwnerPassword(inputPass)) {
                        dialogBinding.tilOwnerPassword.error = "Password / PIN Owner salah! (Default: admin123)"
                        return@setOnClickListener
                    }
                    dialogBinding.tilOwnerPassword.error = null
                }

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ownerUser = appDatabase.userDao().getUserByRole(SessionManager.ROLE_OWNER)
                    val ownerId = ownerUser?.id ?: "owner_1"
                    val ownerUsername = ownerUser?.username ?: "owner"
                    val ownerFullName = ownerUser?.fullName ?: "Pemilik Toko (Owner)"

                    sessionManager.login(ownerId, ownerUsername, ownerFullName, SessionManager.ROLE_OWNER)

                    withContext(Dispatchers.Main) {
                        setupUserRoleView()
                        dialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "Berhasil beralih ke peran OWNER (Akses Penuh)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                // Peran KASIR dipilih: terapkan staf kasir yang dipilih dari dropdown
                val selectedPos = dialogBinding.spCashierList.selectedItemPosition
                val selectedCashier = if (selectedPos in cashiersList.indices) {
                    cashiersList[selectedPos]
                } else {
                    cashiersList.firstOrNull() ?: UserEntity(
                        id = "kasir_1",
                        storeId = "default_store",
                        username = "kasir",
                        fullName = "Kasir 1 (Staff)",
                        role = "KASIR"
                    )
                }

                sessionManager.login(
                    selectedCashier.id,
                    selectedCashier.username,
                    selectedCashier.fullName,
                    SessionManager.ROLE_KASIR
                )
                setupUserRoleView()
                dialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    "Berhasil masuk sebagai staf kasir: ${selectedCashier.fullName} (@${selectedCashier.username})",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        val currentOwnerPass = sessionManager.getOwnerPassword()

        dialogBinding.etStoreName.setText(currentName)
        dialogBinding.etStoreAddress.setText(currentAddr)
        dialogBinding.etStorePhone.setText(currentPhone)
        dialogBinding.etStoreOwnerPassword.setText(currentOwnerPass)

        dialogBinding.btnCancelEditStore.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveEditStore.setOnClickListener {
            val name = dialogBinding.etStoreName.text.toString().trim()
            val addr = dialogBinding.etStoreAddress.text.toString().trim()
            val phone = dialogBinding.etStorePhone.text.toString().trim()
            val ownerPass = dialogBinding.etStoreOwnerPassword.text.toString().trim()

            if (name.isEmpty()) {
                dialogBinding.etStoreName.error = "Nama toko wajib diisi!"
                return@setOnClickListener
            }

            if (ownerPass.isNotEmpty()) {
                sessionManager.saveOwnerPassword(ownerPass)
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
                    Toast.makeText(requireContext(), "Profil Toko & PIN Owner berhasil diperbarui!", Toast.LENGTH_SHORT).show()
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

                            withContext(Dispatchers.Main) {
                                // Jika kasir yang dihapus sedang aktif login, alihkan sesi secara aman ke OWNER
                                if (sessionManager.getCurrentUserId() == user.id) {
                                    sessionManager.switchRole(SessionManager.ROLE_OWNER)
                                    setupUserRoleView()
                                    Toast.makeText(
                                        requireContext(),
                                        "Kasir aktif '${user.fullName}' dihapus. Sesi dialihkan ke OWNER.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Kasir '${user.fullName}' berhasil dihapus.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
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
            val rawUsername = dialogBinding.etCashierUsername.text.toString().trim()
            val username = rawUsername.lowercase()

            if (fullName.isEmpty()) {
                dialogBinding.etCashierFullName.error = "Nama Lengkap wajib diisi!"
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                dialogBinding.etCashierUsername.error = "Username wajib diisi!"
                return@setOnClickListener
            }
            if (!username.matches(Regex("^[a-zA-Z0-9_]{3,20}$"))) {
                dialogBinding.etCashierUsername.error = "Username 3-20 karakter, hanya huruf, angka, underscore!"
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                // Validasi agar username tidak duplikat
                val existing = appDatabase.userDao().getUserByUsername(username)
                if (existing != null) {
                    withContext(Dispatchers.Main) {
                        dialogBinding.etCashierUsername.error = "Username '@$username' sudah terdaftar!"
                    }
                    return@launch
                }

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
                    Toast.makeText(
                        requireContext(),
                        "Akun kasir '$fullName' (@$username) berhasil dibuat!",
                        Toast.LENGTH_LONG
                    ).show()
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

        val smsCenterList = mutableListOf<String>()
        val distributorRules = mutableListOf<DistributorRule>()
        var renderDistributorsListAction: (() -> Unit)? = null

        fun updateSmsPreview() {
            val pin = dialogBinding.etH2hPin.text.toString().trim().ifEmpty { "1234" }
            val sampleCmd = when {
                dialogBinding.rbFormatHash.isChecked -> "PLN20#14123456789#$pin"
                dialogBinding.rbFormatSpace.isChecked -> "BELI DANA50 081234567890 $pin"
                else -> "S10.081234567890.$pin"
            }
            dialogBinding.tvSmsFormatPreview.text = sampleCmd
        }

        dialogBinding.rgSmsFormat.setOnCheckedChangeListener { _, _ ->
            updateSmsPreview()
        }

        dialogBinding.etH2hPin.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSmsPreview()
            }
        })

        fun renderSmsCenterList() {
            dialogBinding.layoutSmsCenterList.removeAllViews()
            if (smsCenterList.isEmpty()) {
                dialogBinding.tvEmptySmsCenter.visibility = View.VISIBLE
            } else {
                dialogBinding.tvEmptySmsCenter.visibility = View.GONE
                for (number in smsCenterList) {
                    val chipBinding = ItemSmsCenterChipBinding.inflate(layoutInflater, dialogBinding.layoutSmsCenterList, false)
                    chipBinding.tvSmsCenterNumber.text = number
                    chipBinding.btnRemoveSmsCenter.setOnClickListener {
                        smsCenterList.remove(number)
                        renderSmsCenterList()
                    }
                    dialogBinding.layoutSmsCenterList.addView(chipBinding.root)
                }
            }
        }

        fun showAddDistributorDialog(existingRule: DistributorRule? = null) {
            val distDialog = BottomSheetDialog(requireContext())
            val addBinding = DialogAddDistributorBinding.inflate(layoutInflater)
            distDialog.setContentView(addBinding.root)

            val categories = listOf(
                "Pulsa",
                "Paket Data",
                "PLN",
                "PDAM",
                "Uang Elektronik",
                "Voucher Game",
                "BPJS",
                "SEMUA"
            )
            val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
            addBinding.spDistributorCategory.adapter = spinnerAdapter

            fun updateAddPreview() {
                val pin = addBinding.etDistributorPin.text.toString().trim().ifEmpty { "1234" }
                val tmpl = when {
                    addBinding.rbFormatHash.isChecked -> SmsGatewayHelper.TEMPLATE_HASH
                    addBinding.rbFormatSpace.isChecked -> SmsGatewayHelper.TEMPLATE_SPACE
                    else -> SmsGatewayHelper.TEMPLATE_DOT
                }
                val delim = when {
                    addBinding.rbFormatHash.isChecked -> "#"
                    addBinding.rbFormatSpace.isChecked -> " "
                    else -> "."
                }
                addBinding.tvDistributorPreview.text = SmsGatewayHelper.formatSmsCommand("S10", "081234567890", pin, tmpl, delim)
            }

            addBinding.rgDistributorFormat.setOnCheckedChangeListener { _, _ -> updateAddPreview() }
            addBinding.etDistributorPin.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { updateAddPreview() }
            })

            if (existingRule != null) {
                addBinding.etDistributorName.setText(existingRule.name)
                addBinding.etDistributorCenter.setText(existingRule.centerNumber)
                addBinding.etDistributorPin.setText(existingRule.pin)
                val catIndex = categories.indexOfFirst { it.equals(existingRule.category, ignoreCase = true) }
                if (catIndex >= 0) addBinding.spDistributorCategory.setSelection(catIndex)

                when (existingRule.smsTemplate) {
                    SmsGatewayHelper.TEMPLATE_HASH -> addBinding.rbFormatHash.isChecked = true
                    SmsGatewayHelper.TEMPLATE_SPACE -> addBinding.rbFormatSpace.isChecked = true
                    else -> addBinding.rbFormatDot.isChecked = true
                }

                when (existingRule.simSlot) {
                    0 -> addBinding.rbDistSim1.isChecked = true
                    1 -> addBinding.rbDistSim2.isChecked = true
                    else -> addBinding.rbDistSimDefault.isChecked = true
                }
                updateAddPreview()
            }

            addBinding.btnCancelDistributor.setOnClickListener {
                distDialog.dismiss()
            }

            addBinding.btnSaveDistributor.setOnClickListener {
                val name = addBinding.etDistributorName.text.toString().trim()
                val center = addBinding.etDistributorCenter.text.toString().trim().replace(Regex("[^0-9+]"), "")
                val pin = addBinding.etDistributorPin.text.toString().trim()
                val selectedCategory = addBinding.spDistributorCategory.selectedItem?.toString() ?: "SEMUA"

                if (name.isEmpty()) {
                    addBinding.etDistributorName.error = "Nama Distributor wajib diisi!"
                    return@setOnClickListener
                }
                if (center.isEmpty() || !SmsGatewayHelper.isValidCenterNumber(center)) {
                    addBinding.etDistributorCenter.error = "Nomor SMS Center tidak valid (min 8 digit)!"
                    return@setOnClickListener
                }
                if (pin.isEmpty()) {
                    addBinding.etDistributorPin.error = "PIN Distributor wajib diisi!"
                    return@setOnClickListener
                }

                val tmpl = when {
                    addBinding.rbFormatHash.isChecked -> SmsGatewayHelper.TEMPLATE_HASH
                    addBinding.rbFormatSpace.isChecked -> SmsGatewayHelper.TEMPLATE_SPACE
                    else -> SmsGatewayHelper.TEMPLATE_DOT
                }
                val delim = when {
                    addBinding.rbFormatHash.isChecked -> "#"
                    addBinding.rbFormatSpace.isChecked -> " "
                    else -> "."
                }

                val chosenDistSim = when {
                    addBinding.rbDistSim1.isChecked -> 0
                    addBinding.rbDistSim2.isChecked -> 1
                    else -> -1
                }

                val newRule = DistributorRule(
                    id = existingRule?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    category = selectedCategory,
                    centerNumber = center,
                    pin = pin,
                    smsTemplate = tmpl,
                    smsDelimiter = delim,
                    simSlot = chosenDistSim
                )

                if (existingRule != null) {
                    val idx = distributorRules.indexOfFirst { it.id == existingRule.id }
                    if (idx >= 0) {
                        distributorRules[idx] = newRule
                    } else {
                        distributorRules.add(newRule)
                    }
                } else {
                    distributorRules.add(newRule)
                }

                renderDistributorsListAction?.invoke()
                distDialog.dismiss()
                Toast.makeText(requireContext(), "Rute distributor '$name' ($selectedCategory) disimpan", Toast.LENGTH_SHORT).show()
            }

            distDialog.show()
        }

        fun renderDistributorsList() {
            dialogBinding.layoutDistributorsList.removeAllViews()
            if (distributorRules.isEmpty()) {
                dialogBinding.tvEmptyDistributors.visibility = View.VISIBLE
            } else {
                dialogBinding.tvEmptyDistributors.visibility = View.GONE
                for (rule in distributorRules) {
                    val cardBinding = ItemDigitalDistributorCardBinding.inflate(layoutInflater, dialogBinding.layoutDistributorsList, false)
                    cardBinding.tvDistributorName.text = rule.name
                    cardBinding.tvDistributorCategoryBadge.text = rule.category.uppercase()
                    cardBinding.tvDistributorCenter.text = "Center: ${rule.centerNumber}"
                    cardBinding.tvDistributorPin.text = "PIN: ${rule.pin}"
                    when (rule.simSlot) {
                        0 -> {
                            cardBinding.tvDistributorSimBadge.visibility = View.VISIBLE
                            cardBinding.tvDistributorSimBadge.text = "SIM 1"
                        }
                        1 -> {
                            cardBinding.tvDistributorSimBadge.visibility = View.VISIBLE
                            cardBinding.tvDistributorSimBadge.text = "SIM 2"
                        }
                        else -> {
                            cardBinding.tvDistributorSimBadge.visibility = View.GONE
                        }
                    }
                    val samplePreview = SmsGatewayHelper.formatSmsCommand("S10", "081234567890", rule.pin, rule.smsTemplate, rule.smsDelimiter)
                    cardBinding.tvDistributorFormatPreview.text = "Perintah: $samplePreview"

                    cardBinding.btnDeleteDistributor.setOnClickListener {
                        distributorRules.remove(rule)
                        renderDistributorsList()
                        Toast.makeText(requireContext(), "Rute distributor '${rule.name}' dihapus", Toast.LENGTH_SHORT).show()
                    }

                    cardBinding.root.setOnClickListener {
                        showAddDistributorDialog(rule)
                    }

                    dialogBinding.layoutDistributorsList.addView(cardBinding.root)
                }
            }
        }
        renderDistributorsListAction = { renderDistributorsList() }

        dialogBinding.btnAddDistributor.setOnClickListener {
            showAddDistributorDialog(null)
        }

        dialogBinding.btnAddSmsCenter.setOnClickListener {
            val inputNum = dialogBinding.etNewSmsCenter.text.toString().trim()
            if (inputNum.isEmpty()) {
                Toast.makeText(requireContext(), "Masukkan nomor SMS Center!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!SmsGatewayHelper.isValidCenterNumber(inputNum)) {
                Toast.makeText(requireContext(), "Nomor SMS Center tidak valid (min 8 digit)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clean = inputNum.replace(Regex("[^0-9+]"), "")
            if (smsCenterList.contains(clean)) {
                Toast.makeText(requireContext(), "Nomor SMS Center '$clean' sudah ada di daftar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            smsCenterList.add(clean)
            dialogBinding.etNewSmsCenter.setText("")
            renderSmsCenterList()
            Toast.makeText(requireContext(), "Nomor SMS Center '$clean' ditambahkan", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val config = appDatabase.digitalConfigDao().getConfig()
            withContext(Dispatchers.Main) {
                if (config != null) {
                    dialogBinding.etH2hApiUrl.setText(if (config.apiUrl.isNotBlank()) config.apiUrl else "https://st.riungan.my.id/api/isipulsa/h2h-order")
                    dialogBinding.etH2hApiKey.setText(if (config.apiKey.isNotBlank()) config.apiKey else "DENKASIR-H2H-LIVE")
                    dialogBinding.etH2hMemberId.setText(config.memberId)
                    dialogBinding.etH2hPin.setText(config.pinPassword)
                    dialogBinding.etH2hBalance.setText(config.balance.toInt().toString())

                    when (config.trxMode) {
                        SmsGatewayHelper.MODE_SMS_CENTER -> dialogBinding.rbModeSms.isChecked = true
                        SmsGatewayHelper.MODE_AUTO_FALLBACK -> dialogBinding.rbModeAuto.isChecked = true
                        else -> dialogBinding.rbModeApi.isChecked = true
                    }

                    smsCenterList.clear()
                    smsCenterList.addAll(SmsGatewayHelper.parseSmsCenterList(config.smsCenterNumbers))
                    renderSmsCenterList()

                    distributorRules.clear()
                    distributorRules.addAll(SmsGatewayHelper.parseDistributorRules(config.distributorRulesJson))
                    renderDistributorsList()

                    when (config.smsTemplate) {
                        SmsGatewayHelper.TEMPLATE_HASH -> dialogBinding.rbFormatHash.isChecked = true
                        SmsGatewayHelper.TEMPLATE_SPACE -> dialogBinding.rbFormatSpace.isChecked = true
                        else -> dialogBinding.rbFormatDot.isChecked = true
                    }

                    // Deteksi & Tampilkan Kartu SIM
                    val simCards = SmsGatewayHelper.getActiveSimCards(requireContext())
                    if (simCards.isNotEmpty()) {
                        dialogBinding.tvDetectedSimInfo.text = "Terdeteksi: " + simCards.joinToString(", ") { it.displayName }
                        if (simCards.isNotEmpty()) {
                            dialogBinding.rbSim1.text = simCards[0].displayName
                        }
                        if (simCards.size >= 2) {
                            dialogBinding.rbSim2.text = simCards[1].displayName
                        } else {
                            dialogBinding.rbSim2.text = "SIM 2 (Tidak Terpasang / 1 SIM)"
                        }
                    } else {
                        dialogBinding.tvDetectedSimInfo.text = "Menggunakan kartu SIM bawaan sistem."
                    }

                    when (config.preferredSimSlot) {
                        0 -> dialogBinding.rbSim1.isChecked = true
                        1 -> dialogBinding.rbSim2.isChecked = true
                        else -> dialogBinding.rbSimDefault.isChecked = true
                    }
                    dialogBinding.switchAutoSendSms.isChecked = config.autoSendSms

                    updateSmsPreview()
                } else {
                    renderSmsCenterList()
                    renderDistributorsList()
                    updateSmsPreview()
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

            val chosenMode = when {
                dialogBinding.rbModeSms.isChecked -> SmsGatewayHelper.MODE_SMS_CENTER
                dialogBinding.rbModeAuto.isChecked -> SmsGatewayHelper.MODE_AUTO_FALLBACK
                else -> SmsGatewayHelper.MODE_API_H2H
            }

            if (chosenMode != SmsGatewayHelper.MODE_SMS_CENTER && url.isEmpty()) {
                Toast.makeText(requireContext(), "URL Endpoint API wajib diisi untuk mode API H2H!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if ((chosenMode == SmsGatewayHelper.MODE_SMS_CENTER || chosenMode == SmsGatewayHelper.MODE_AUTO_FALLBACK) && pin.isEmpty() && distributorRules.isEmpty()) {
                dialogBinding.etH2hPin.error = "PIN Transaksi wajib diisi untuk transaksi SMS Center!"
                dialogBinding.etH2hPin.requestFocus()
                Toast.makeText(requireContext(), "PIN Transaksi wajib diisi agar perintah SMS Center tidak ditolak server!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if ((chosenMode == SmsGatewayHelper.MODE_SMS_CENTER || chosenMode == SmsGatewayHelper.MODE_AUTO_FALLBACK) && smsCenterList.isEmpty() && distributorRules.isEmpty()) {
                Toast.makeText(requireContext(), "Tambahkan minimal satu nomor SMS Center atau rute distributor!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val chosenTemplate = when {
                dialogBinding.rbFormatHash.isChecked -> SmsGatewayHelper.TEMPLATE_HASH
                dialogBinding.rbFormatSpace.isChecked -> SmsGatewayHelper.TEMPLATE_SPACE
                else -> SmsGatewayHelper.TEMPLATE_DOT
            }

            val delimiter = when (chosenTemplate) {
                SmsGatewayHelper.TEMPLATE_HASH -> "#"
                SmsGatewayHelper.TEMPLATE_SPACE -> " "
                else -> "."
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
                    updatedAt = System.currentTimeMillis(),
                    trxMode = chosenMode,
                    smsCenterNumbers = SmsGatewayHelper.joinSmsCenterList(smsCenterList),
                    smsTemplate = chosenTemplate,
                    smsDelimiter = delimiter,
                    distributorRulesJson = SmsGatewayHelper.distributorRulesToJson(distributorRules),
                    preferredSimSlot = when {
                        dialogBinding.rbSim1.isChecked -> 0
                        dialogBinding.rbSim2.isChecked -> 1
                        else -> -1
                    },
                    autoSendSms = dialogBinding.switchAutoSendSms.isChecked
                )
                appDatabase.digitalConfigDao().saveConfig(config)

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    val modeName = when (chosenMode) {
                        SmsGatewayHelper.MODE_SMS_CENTER -> "SMS Center (${smsCenterList.size} nomor)"
                        SmsGatewayHelper.MODE_AUTO_FALLBACK -> "Otomatis (API + ${smsCenterList.size} SMS Center)"
                        else -> "API H2H Internet"
                    }
                    val distInfo = if (distributorRules.isNotEmpty()) " & ${distributorRules.size} Rute Distributor" else ""
                    Toast.makeText(requireContext(), "Konfigurasi Gateway $modeName$distInfo berhasil disimpan!", Toast.LENGTH_LONG).show()
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
