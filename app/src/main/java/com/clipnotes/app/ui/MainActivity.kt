package com.clipnotes.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.data.NoteRepository
import com.clipnotes.app.databinding.ActivityMainBinding
import com.clipnotes.app.service.ClipboardMonitorService
import com.clipnotes.app.service.FloatingWindowService
import com.clipnotes.app.service.NetworkDiscoveryService
import com.clipnotes.app.utils.AudioRecorderManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var audioRecorder: AudioRecorderManager
    private lateinit var networkService: NetworkDiscoveryService
    private var isRecording = false
    
    private val viewModel: MainViewModel by viewModels {
        val app = application as NoteApplication
        MainViewModelFactory(app.repository, app.preferenceManager)
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleRecording()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestClipboardPermission()
        }
    }

    private val clipboardPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startServices()
        } else {
            startServices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            audioRecorder = AudioRecorderManager(this)
            networkService = NetworkDiscoveryService(this)

            setupRecyclerView()
            
            setupClickListeners()
            
            requestPermissions()
            
            observeNotes()

            networkService.startServer(8888) { notesJson, callback ->
                showReceiveDialog(notesJson, callback)
            }
            
            handleIntent(intent)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onNoteClick = { note ->
                // 已在 adapter 中自动复制，此处可做其他操作
            },
            onNoteLongClick = { note ->
                showNoteOptionsDialog(note)
            },
            onAudioClick = { audioPath ->
                audioRecorder.playAudio(audioPath) {
                    notesAdapter.notifyDataSetChanged()
                }
            },
            onNoteMarkRead = { note ->
                viewModel.markNoteAsRead(note)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
        }
        setupFloatingButtons()
    }
    
    private fun setupFloatingButtons() {
        // 回到顶部
        binding.fabScrollToTop.setOnClickListener {
            binding.recyclerView.smoothScrollToPosition(0)
        }
        binding.fabScrollToTop.setOnTouchListener { v, event ->
            makeFabDraggable(v as android.view.View, event)
        }
        
        // 跳到最后一次点击已读
        binding.fabScrollToLastRead.setOnClickListener {
            lifecycleScope.launch {
                val lastReadNote = viewModel.getAllNotesSnapshot().lastOrNull { it.isRead }
                if (lastReadNote != null) {
                    val notes = viewModel.getAllNotesSnapshot()
                    val index = notes.indexOfFirst { it.id == lastReadNote.id }
                    if (index >= 0) {
                        binding.recyclerView.smoothScrollToPosition(index)
                    }
                }
            }
        }
        binding.fabScrollToLastRead.setOnTouchListener { v, event ->
            makeFabDraggable(v as android.view.View, event)
        }
        
        // 回到底部
        binding.fabScrollToBottom.setOnClickListener {
            lifecycleScope.launch {
                val notes = viewModel.getAllNotesSnapshot()
                if (notes.isNotEmpty()) {
                    binding.recyclerView.smoothScrollToPosition(notes.size - 1)
                }
            }
        }
        binding.fabScrollToBottom.setOnTouchListener { v, event ->
            makeFabDraggable(v as android.view.View, event)
        }
    }
    
    private val fabTouchData = mutableMapOf<android.view.View, Pair<Float, Float>>()
    private var isFabDragging = false
    
    private fun makeFabDraggable(view: android.view.View, event: android.view.MotionEvent): Boolean {
        val params = (view.layoutParams as android.widget.FrameLayout.LayoutParams)
        
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                fabTouchData[view] = Pair(event.rawX, event.rawY)
                isFabDragging = false
                return false  // 允许点击事件继续传递
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val (lastX, lastY) = fabTouchData[view] ?: return false
                val deltaX = (event.rawX - lastX).toInt()
                val deltaY = (event.rawY - lastY).toInt()
                
                // 移动距离超过5像素才认为是拖动
                if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                    isFabDragging = true
                    params.rightMargin = Math.max(0, params.rightMargin - deltaX)
                    params.bottomMargin = Math.max(0, params.bottomMargin - deltaY)
                    view.layoutParams = params
                    
                    fabTouchData[view] = Pair(event.rawX, event.rawY)
                    return true  // 拦截事件
                }
                return false
            }
            android.view.MotionEvent.ACTION_UP -> {
                fabTouchData.remove(view)
                return isFabDragging  // 如果是拖动则拦截，否则允许点击
            }
        }
        return false
    }

    private fun setupClickListeners() {
        binding.fabAddNote.setOnClickListener {
            showAddNoteDialog()
        }

        binding.fabRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                toggleRecording()
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            val (file, duration) = audioRecorder.stopRecording()
            file?.let {
                saveAudioNote(it.absolutePath, duration)
            }
            binding.fabRecord.text = "录音"
            isRecording = false
        } else {
            audioRecorder.startRecording()
            binding.fabRecord.text = "录音中"
            isRecording = true
        }
    }

    private fun saveAudioNote(path: String, duration: Long) {
        viewModel.insertNote(
            "录音 (${duration / 1000}秒)",
            ContentType.AUDIO_RECORDING,
            path,
            duration
        )
    }

    private fun showAddNoteDialog() {
        val builder = AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.hint = "输入笔记内容"
        
        builder.setTitle("添加笔记")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    saveUserNote(text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveUserNote(text: String) {
        viewModel.insertNote(text, ContentType.USER_INPUT_TEXT)
    }

    private fun showNoteOptionsDialog(note: NoteEntity) {
        val options = arrayOf("编辑", "删除")
        AlertDialog.Builder(this)
            .setTitle("笔记操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editNote(note)
                    1 -> deleteNote(note)
                }
            }
            .show()
    }

    private fun editNote(note: NoteEntity) {
        if (note.contentType == ContentType.AUDIO_RECORDING) {
            // 无法编辑
            return
        }
        
        val builder = AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.setText(note.content)
        
        builder.setTitle("编辑笔记")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    viewModel.updateNote(note.copy(content = text))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteNote(note: NoteEntity) {
        AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteNote(note)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("note", text)
        clipboard.setPrimaryClip(clip)
        // 已复制
    }

    private fun observeNotes() {
        lifecycleScope.launch {
            viewModel.notes.collectLatest { notes ->
                notesAdapter.submitList(notes)
                updateTitleWithCounts(notes)
            }
        }
    }
    
    private fun updateTitleWithCounts(notes: List<NoteEntity>) {
        val totalCount = notes.size
        val readCount = notes.count { it.isRead }
        supportActionBar?.title = "$readCount/$totalCount"
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                requestClipboardPermission()
            }
        } else {
            startServices()
        }
    }

    private fun requestClipboardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // READ_CLIPBOARD_DATA only exists on Android 13+
            val readClipboardPermission = "android.permission.READ_CLIPBOARD_DATA"
            if (ContextCompat.checkSelfPermission(this, readClipboardPermission)
                != PackageManager.PERMISSION_GRANTED) {
                clipboardPermissionLauncher.launch(readClipboardPermission)
            } else {
                startServices()
            }
        } else {
            startServices()
        }
    }

    private fun startServices() {
        val app = application as NoteApplication
        if (app.preferenceManager.isClipboardMonitoringEnabled) {
            ClipboardMonitorService.start(this)
            FloatingWindowService.start(this)
        }
        requestAccessibilityService()
    }

    private fun requestAccessibilityService() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            Toast.makeText(
                this,
                "请在无障碍服务中启用\"剪贴板笔记\"以实现后台监听",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
        }
    }

    private fun clearAllNotes() {
        AlertDialog.Builder(this)
            .setTitle("清空笔记")
            .setMessage("确定要清空所有笔记吗？此操作无法撤销。")
            .setPositiveButton("清空") { _, _ ->
                viewModel.deleteAllNotes()
                Toast.makeText(this@MainActivity, "已清空所有笔记", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendNotes() {
        lifecycleScope.launch {
            networkService.discoverServices()
            
            launch {
                networkService.discoveredDevices.collect { devices ->
                    if (devices.isNotEmpty()) {
                        showDeviceSelectionDialog(devices)
                    }
                }
            }
        }
    }

    private fun showDeviceSelectionDialog(devices: List<NetworkDiscoveryService.DeviceInfo>) {
        val deviceNames = devices.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择设备")
            .setItems(deviceNames) { _, which ->
                val device = devices[which]
                performSendNotes(device)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performSendNotes(device: NetworkDiscoveryService.DeviceInfo) {
        lifecycleScope.launch {
            val notesList = viewModel.getAllNotesSnapshot()
            
            val response = networkService.sendNotes(device, notesList)
            
            when (response) {
                "ACCEPTED" -> {
                    val count = notesList.size
                    Toast.makeText(
                        this@MainActivity,
                        "发送成功！共 $count 条笔记",
                        Toast.LENGTH_LONG
                    ).show()
                }
                "REJECTED" -> {
                    Toast.makeText(this@MainActivity, "对方拒绝接收", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this@MainActivity, "发送失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showReceiveDialog(notesJson: String, callback: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("接收笔记")
            .setMessage("是否接收来自其他设备的笔记？")
            .setPositiveButton("接收") { _, _ ->
                receiveNotes(notesJson)
                callback(true)
            }
            .setNegativeButton("拒绝") { _, _ ->
                callback(false)
            }
            .show()
    }

    private fun receiveNotes(notesJson: String) {
        viewModel.receiveNotes(
            notesJson,
            onSuccess = {
                Toast.makeText(this@MainActivity, "接收成功！", Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(this@MainActivity, "接收失败", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_send -> {
                sendNotes()
                true
            }
            R.id.action_clear -> {
                clearAllNotes()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("QUICK_BACKGROUND", false) == true) {
            Handler(Looper.getMainLooper()).postDelayed({
                moveTaskToBack(true)
            }, 500)
        } else if (intent?.getBooleanExtra("AUTO_MOVE_TO_BACKGROUND", false) == true) {
            lifecycleScope.launch {
                val notes = viewModel.getAllNotesSnapshot()
                if (notes.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(notes.size - 1)
                }
            }
            Handler(Looper.getMainLooper()).postDelayed({
                moveTaskToBack(true)
            }, 1000)
        } else if (intent?.getBooleanExtra("SHOW_BRIEFLY", false) == true) {
            lifecycleScope.launch {
                val notes = viewModel.getAllNotesSnapshot()
                if (notes.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(notes.size - 1)
                }
            }
            Handler(Looper.getMainLooper()).postDelayed({
                moveTaskToBack(true)
            }, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        ClipboardMonitorService.pause(this)
        scrollToLatestNote()
    }
    
    private fun scrollToLatestNote() {
        lifecycleScope.launch {
            try {
                val notes = viewModel.getAllNotesSnapshot()
                if (notes.isNotEmpty()) {
                    // 滚动到最后一条（最新的笔记）
                    binding.recyclerView.scrollToPosition(notes.size - 1)
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ClipboardMonitorService.resume(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.release()
        networkService.stopDiscovery()
    }
}
