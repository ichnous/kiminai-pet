package com.kiminai.pet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("pet_config", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }
        root.addView(layout)

        val title = TextView(this).apply {
            text = "🐾 基米奈手机桌宠控制中心"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val btnToggle = Button(this).apply {
            text = "✨ 召唤 / 刷新悬浮窗猫猫"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "请先开启【显示在其他应用上层】权限喵~", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } else {
                    startPetService()
                }
            }
        }
        layout.addView(btnToggle)

        val btnStop = Button(this).apply {
            text = "💤 让猫猫回去休息（关闭悬浮窗）"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingPetService::class.java))
            }
        }
        layout.addView(btnStop)

        layout.addView(createSliderItem("猫猫体型大小", "scale", 50, 200, 100, "%"))
        layout.addView(createSliderItem("悬浮窗透明度", "alpha", 20, 100, 100, "%"))
        layout.addView(createSliderItem("猫猫活跃度（自动漫步频率）", "activity_level", 0, 10, 5, " 级"))
        layout.addView(createSwitchItem("松手自动贴边停靠", "snap_edge", true))
        layout.addView(createSwitchItem("开启随机互动气泡台词", "show_bubble", true))
        layout.addView(createSwitchItem("防误触穿透模式（点击穿透给下层游戏）", "click_through", false))

        setContentView(root)
    }

    private fun createSliderItem(label: String, key: String, min: Int, max: Int, defaultVal: Int, unit: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 16)
        }
        val current = prefs.getInt(key, defaultVal)
        val tv = TextView(this).apply { text = "$label：$current$unit" }
        val seek = SeekBar(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) this.min = min
            this.max = max
            progress = current
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p.coerceAtLeast(min)
                    tv.text = "$label：$v$unit"
                    prefs.edit().putInt(key, v).apply()
                    sendBroadcast(Intent("com.kiminai.pet.UPDATE_CONFIG").setPackage(packageName))
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(tv)
        box.addView(seek)
        return box
    }

    private fun createSwitchItem(label: String, key: String, defaultVal: Boolean): Switch {
        return Switch(this).apply {
            text = label
            isChecked = prefs.getBoolean(key, defaultVal)
            setPadding(0, 28, 0, 28)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).apply()
                sendBroadcast(Intent("com.kiminai.pet.UPDATE_CONFIG").setPackage(packageName))
            }
        }
    }

    private fun startPetService() {
        val intent = Intent(this, FloatingPetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
