package com.example.xtreamtv

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class Console(val name:String,val core:String,val examples:String)
private val consoles = listOf(
    Console("Arcade / Neo Geo","fbneo","Final Fight • Metal Slug • Street Fighter II"),
    Console("NES","fceumm","Super Mario Bros."), Console("SNES","snes9x","Super Mario World • Mario Kart"),
    Console("Mega Drive / Genesis","genesis_plus_gx","Sonic • Streets of Rage"), Console("Master System / Game Gear","genesis_plus_gx","Alex Kidd • Sonic"),
    Console("Game Boy / Color","gambatte","Super Mario Land"), Console("Game Boy Advance","mgba","Mario Kart: Super Circuit"),
    Console("PlayStation","pcsx_rearmed","Tekken 3 • Crash Bandicoot"), Console("Nintendo 64","mupen64plus_next","Super Mario 64"),
    Console("Dreamcast","flycast","Sonic Adventure"), Console("Saturn","yabasanshiro","Sega Rally"),
    Console("PSP","ppsspp","Handheld games"), Console("Nintendo DS","melonds","Dual-screen games"),
    Console("PC Engine","mednafen_pce_fast","Classic arcade ports"), Console("Atari 2600","stella","Classic Atari games"))
private val retroPackages=listOf("com.retroarch.aarch64","com.retroarch","com.retroarch.ra32")
private data class SharkRetroLibraryEntry(val title:String,val system:String,val path:String)
private fun openApp(context:Context,pkg:String):Boolean = try {
    val intent=context.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: context.packageManager.getLaunchIntentForPackage(pkg)
    if(intent==null) false else {context.startActivity(intent);true}
} catch(e:Exception){false}
private fun browser(context:Context,url:String):Boolean = try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)));true} catch(e:Exception){false}

@Composable fun RetroConsoleHub() {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("shark_retro_v1",0) }
    var selected by remember { mutableStateOf(consoles.first()) }
    var entries by remember { mutableStateOf(runCatching { val a=JSONArray(prefs.getString("library","[]")); (0 until a.length()).map { val o=a.getJSONObject(it);SharkRetroLibraryEntry(o.getString("title"),o.getString("system"),o.getString("path")) } }.getOrDefault(emptyList())) }
    fun save(list:List<SharkRetroLibraryEntry>){entries=list;val a=JSONArray();list.forEach { a.put(JSONObject().put("title",it.title).put("system",it.system).put("path",it.path)) };prefs.edit().putString("library",a.toString()).apply()}
    var title by remember { mutableStateOf("") };var path by remember { mutableStateOf("") };var message by remember { mutableStateOf("") }
    var packageChoice by remember { mutableStateOf(prefs.getString("package",retroPackages.first()).orEmpty()) }
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                require(uri.authority == "com.android.externalstorage.documents") { "Choose a local internal storage or USB folder" }
                val root = android.provider.DocumentsContract.getTreeDocumentId(uri)
                // RetroArch uses shared filesystem paths, not Shark's private files.
                val volume = root.substringBefore(':')
                val base = if (volume.equals("primary", true)) "/storage/emulated/0" else "/storage/$volume"
                val found = mutableListOf<SharkRetroLibraryEntry>()
                val pending = java.util.ArrayDeque<String>()
                pending.add(root)
                var count = 0
                while (pending.isNotEmpty() && count < 10000) {
                    val parent = pending.removeFirst()
                    val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(uri, parent)
                    context.contentResolver.query(children, arrayOf("document_id", "_display_name", "mime_type"), null, null, null)?.use { cursor ->
                        while (cursor.moveToNext() && count < 10000) {
                            count++
                            val id = cursor.getString(0)
                            val name = cursor.getString(1)
                            if (cursor.getString(2) == "vnd.android.document/directory") { pending.add(id); continue }
                            if (!id.startsWith("$volume:")) continue
                            val ext = name.substringAfterLast('.', "").lowercase()
                            val system = when(ext) {
                                "nes" -> "NES"; "sfc", "smc" -> "SNES"; "md", "gen", "smd" -> "Mega Drive / Genesis"
                                "sms", "gg" -> "Master System / Game Gear"; "gb", "gbc" -> "Game Boy / Color"; "gba" -> "Game Boy Advance"
                                "n64", "z64", "v64" -> "Nintendo 64"; "nds" -> "Nintendo DS"; "pce" -> "PC Engine"; "a26" -> "Atari 2600"
                                "zip", "7z", "chd", "cue", "iso", "cso", "gdi", "m3u" -> selected.name
                                else -> null
                            } ?: continue
                            found.add(SharkRetroLibraryEntry(name.substringBeforeLast('.'), system, base + "/" + id.substringAfter(':')))
                        }
                    }
                }
                val merged = (entries + found).distinctBy { it.system to it.path }
                val added = merged.size - entries.size
                save(merged)
                message = "Imported $added new games. Check ambiguous disc/archive files and RetroArch storage access."
            } catch (ex: Exception) { message = "Folder scan failed: ${ex.message ?: "storage unavailable"}" }
        }
    }
    Text("RETRO SETUP WIZARD", color=Color(0xFF26E7FF), style=MaterialTheme.typography.headlineSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!downloading,onClick={
            val installed = retroPackages.firstOrNull { runCatching { context.packageManager.getApplicationInfo(it,0) }.isSuccess }
            if (installed != null) { packageChoice=installed; prefs.edit().putString("package",installed).apply(); message="RetroArch detected. Open Core Downloader and install ${selected.core}." }
            else if (!RetroInstaller.permitted(context)) {
                try { RetroInstaller.settings(context); message="Allow SharkNailTV to install apps, then return and press Install RetroArch again." }
                catch(ex:Exception) { message="Installation settings unavailable. Use the official download page." }
            } else {
                downloading=true; message="Downloading official RetroArch APK (approximately 180–200 MB)…"
                scope.launch {
                    try { val apk=RetroInstaller.prepare(context); RetroInstaller.install(context,apk); message="Approve Android installation, then return and press Detect RetroArch." }
                    catch(ex:kotlinx.coroutines.CancellationException) { throw ex }
                    catch(ex:Exception) { message="Download/install failed: ${ex.message}. Try Get RetroArch for manual installation." }
                    finally { downloading=false }
                }
            }
        }) { Text(if(downloading) "Downloading…" else "1. Install / detect RetroArch") }
        Button(onClick={if(!openApp(context,packageChoice)) message="Install RetroArch first." else message="In RetroArch: Online Updater → Core Downloader → ${selected.core}"}) { Text("2. Set up emulator core") }
        Button(onClick={try { folderPicker.launch(null) } catch(ex:Exception) { message="No folder picker available. Use manual game path below." }}) { Text("3. Scan ROM folder / USB") }
    }
    Text("Select the console before scanning ZIP or disc files. BIOS and commercial ROMs are supplied by you. Android requires installation approval.",color=Color.White)
    Text("RETRO CONSOLES",color=Color(0xFF26E7FF),style=MaterialTheme.typography.headlineSmall)
    Text("Add your own game files • examples below are not included",color=Color.White)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        consoles.forEach { c -> FilterChip(selected==c,{selected=c},label={Text(c.name)}) }
    }
    Text(selected.name+"  /  "+selected.core,color=Color.Yellow)
    Text("Examples: "+selected.examples,color=Color.White)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={if(!openApp(context,packageChoice)) message="Install RetroArch first using the official site."}){Text("Open RetroArch")}
        Button(onClick={if(!browser(context,"https://www.retroarch.com/?page=platforms"))message="Open retroarch.com on another device."}){Text("Get RetroArch")}
        Button(onClick={if(!openApp(context,"org.ppsspp.ppsspp"))message="Install PPSSPP from ppsspp.org first."}){Text("Open PPSSPP")}
        Button(onClick={if(!openApp(context,"org.dolphinemu.dolphinemu"))message="Install Dolphin from dolphin-emu.org first."}){Text("Open Dolphin")}
        Button(onClick={if(!openApp(context,"com.limelight"))message="Install Moonlight and pair a Sunshine PC first."}){Text("Open Moonlight")}
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {retroPackages.forEach { pkg -> FilterChip(packageChoice==pkg,{packageChoice=pkg;prefs.edit().putString("package",pkg).apply()},label={Text(pkg)}) }}
    Text("One-time setup: install the matching core in RetroArch. Put games in a shared ROMs folder accessible to RetroArch. Some systems need BIOS files. Use a Bluetooth controller.",color=Color.White)
    OutlinedTextField(title,{title=it},label={Text("Game title")},singleLine=true,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(path,{path=it},label={Text("Full shared file path, e.g. /storage/emulated/0/ROMs/nes/game.nes")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Button(onClick={val p=path.trim();if(title.isBlank()||!p.startsWith("/")||p.contains('\n'))message="Enter a title and absolute file path." else {save(entries.filterNot{it.system==selected.name&&it.path==p}+SharkRetroLibraryEntry(title.trim(),selected.name,p));title="";path="";message="Game added. Check core and file access before launching."}}){Text("Add game to library")}
    if(message.isNotBlank()) Text(message,color=Color.Yellow)
    var search by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("Console") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("retro_favorites",emptySet())!!.toSet()) }
    var history by remember { mutableStateOf(runCatching { val a=JSONArray(prefs.getString("retro_history","[]")); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())) }
    fun gameKey(e:SharkRetroLibraryEntry)=e.system+"|"+e.path
    OutlinedTextField(search,{search=it},label={Text("Search game library")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Console","All","Favourites","Recent").forEach { f -> FilterChip(libraryFilter==f,{libraryFilter=f},label={Text(f)}) }
    }
    val visible=entries.filter { e -> (search.isBlank() || e.title.contains(search,true)) && when(libraryFilter) { "Console" -> e.system==selected.name; "Favourites" -> gameKey(e) in favorites; "Recent" -> gameKey(e) in history; else -> true } }.let { list -> if(libraryFilter=="Recent") list.sortedBy { history.indexOf(gameKey(it)) } else list.sortedBy { it.title.lowercase() } }
    if(visible.isEmpty())Text("No imported games for this console yet.",color=Color.White)
    visible.forEach { e -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(modifier=Modifier.weight(1f),onClick={try {
            val pkg = retroPackages.firstOrNull { it == packageChoice && runCatching { context.packageManager.getApplicationInfo(it,0) }.isSuccess } ?: retroPackages.firstOrNull { runCatching { context.packageManager.getApplicationInfo(it,0) }.isSuccess } ?: error("RetroArch not installed")
            val info=context.packageManager.getApplicationInfo(pkg,0)
            val intent=Intent().setComponent(ComponentName(pkg,"com.retroarch.browser.retroactivity.RetroActivityFuture"))
                .putExtra("ROM",e.path).putExtra("LIBRETRO",info.dataDir+"/cores/"+(consoles.firstOrNull { it.name==e.system } ?: error("Unknown console")).core+"_libretro_android.so")
                .putExtra("QUITFOCUS","")
            context.startActivity(intent);history=(listOf(gameKey(e))+history.filter { it!=gameKey(e) }).take(100);prefs.edit().putString("retro_history",JSONArray(history).toString()).apply();message="Launch requested. If it fails, check ROM access and installed core."
        }catch(ex:Exception){message="RetroArch could not launch. Install the selected version and core first."}}){Text("▶ "+e.title)}
        OutlinedButton(onClick={favorites=if(gameKey(e) in favorites) favorites-gameKey(e) else favorites+gameKey(e);prefs.edit().putStringSet("retro_favorites",favorites).apply()}){Text(if(gameKey(e) in favorites) "★" else "☆")}
        OutlinedButton(onClick={save(entries-e)}){Text("Remove")}
    }}
}
