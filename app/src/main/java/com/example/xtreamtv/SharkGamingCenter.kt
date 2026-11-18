package com.example.xtreamtv

import android.content.Intent
import android.net.Uri
import android.view.InputDevice
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class GamingApp(val name:String,val pkg:String,val details:String,val url:String)
private val engines=listOf(
    GamingApp("RetroArch","com.retroarch","Classic consoles and arcade","https://www.retroarch.com/?page=platforms"),
    GamingApp("Dolphin","org.dolphinemu.dolphinemu","GameCube / Wii","https://dolphin-emu.org/download/"),
    GamingApp("PPSSPP","org.ppsspp.ppsspp","PSP","https://www.ppsspp.org/download/"),
    GamingApp("Flycast","com.flycast.emulator","Dreamcast / NAOMI","https://github.com/flyinghead/flycast/releases"),
    GamingApp("Azahar","org.azahar.azahar","Nintendo 3DS","https://github.com/azahar-emu/azahar/releases"),
    GamingApp("Vita3K","org.vita3k.emulator","Experimental PS Vita","https://vita3k.org/"),
    GamingApp("Moonlight","com.limelight","PC streaming with Sunshine","https://github.com/moonlight-stream/moonlight-android/releases"))
private val freeGames=listOf(
    GamingApp("SuperTuxKart","org.supertuxkart.stk","Kart racing","https://supertuxkart.net/Download"),
    GamingApp("SuperTux","org.lethargik.supertux2","Platform adventure","https://github.com/SuperTux/supertux/releases"),
    GamingApp("Pixel Wheels","com.agateau.tinywheels.android","Retro racing","https://github.com/agateau/pixelwheels/releases"),
    GamingApp("Mindustry","io.anuke.mindustry","Strategy","https://github.com/Anuken/Mindustry/releases"),
    GamingApp("OpenBOR","org.openbor.engine","Beat-em-up engine; requires compatible games","https://github.com/DCurrent/openbor/releases"))

@Composable fun SharkGamingCenter(section:String) {
    val context=LocalContext.current
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    fun openUrl(url:String) { try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) } catch(e:Exception) { message="No browser available. Use $url on another device." } }
    Text(section,color=Color(0xFF26E7FF),style=MaterialTheme.typography.headlineSmall)
    if(section=="Device & Controllers") {
        val manager=context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory=android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(memory)
        val devices: List<InputDevice> = remember(revision) {
            val result = mutableListOf<InputDevice>()
            for (id in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(id) ?: continue
                val sources: Int = device.sources
                if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                    result.add(device)
                }
            }
            result
        }
        Text("Device: ${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE}",color=Color.White)
        Text("CPU: ${android.os.Build.SUPPORTED_ABIS.joinToString()} • RAM: ${memory.totalMem/1048576} MB",color=Color.White)
        Text(if(memory.totalMem<3L*1024*1024*1024) "Recommended: classic arcade, NES, SNES and Mega Drive. Use PC streaming for demanding games." else "Recommended: start with RetroArch; test demanding emulators individually.",color=Color.White)
        Text(if(devices.isEmpty()) "No gamepad detected" else devices.joinToString("\n") { it.name },color=Color.White)
        Button(onClick={revision++}) { Text("Refresh controllers") }
        Button(onClick={try { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } catch(e:Exception){message="Open Bluetooth settings on your device."}}){Text("Pair Bluetooth controller")}
    } else {
        val apps=if(section=="Free Games Store") freeGames else engines
        Text(if(section=="Free Games Store") "Official project downloads • Android builds and controller support vary" else "Optional gaming engines • install from official sources",color=Color.White)
        Button(onClick={revision++}){Text("Refresh installed apps")}
        apps.forEach { app ->
            val launch=remember(app,revision) { val packages=if(app.name=="RetroArch") listOf("com.retroarch.aarch64","com.retroarch","com.retroarch.ra32") else listOf(app.pkg); packages.firstNotNullOfOrNull { context.packageManager.getLeanbackLaunchIntentForPackage(it) ?: context.packageManager.getLaunchIntentForPackage(it) } }
            Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(app.name,style=MaterialTheme.typography.titleLarge)
                    Text(app.details)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={if(launch!=null) { try {context.startActivity(launch)} catch(e:Exception){message="Unable to open ${app.name}"} } else openUrl(app.url)}) {Text(if(launch!=null) "Play / Open" else "Official download")}
                        OutlinedButton(onClick={openUrl(app.url)}){Text("Project / Updates")}
                    }
                }
            }
        }
        if(section=="Gaming Engines") Button(onClick={openUrl("https://github.com/LizardByte/Sunshine/releases")}){Text("Sunshine for your gaming PC")}
    }
    if(message.isNotEmpty())Text(message,color=Color.Yellow)
}
