package com.ipvio.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val skyBg = Color(0xFF030A20)
private val skyPanel = Color(0xFF071735)
private val skyBlue = Color(0xFF1683FF)
private val skyGold = Color(0xFFFFD329)
private val skyWhite = Color(0xFFF4F6FB)
private val skyMuted = Color(0xFFADC0DF)

@Composable
private fun SkyBar(label: String, detail: String, back: () -> Unit) {
  Row(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF082554))
    .border(2.dp, skyBlue).padding(horizontal=10.dp),
    verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("SKY RETRO", color=skyGold, fontWeight=FontWeight.Black, fontSize=19.sp,
      modifier=Modifier.clickable(onClick=back))
    Text(label, color=skyWhite, fontWeight=FontWeight.ExtraBold, fontSize=20.sp)
    Spacer(Modifier.weight(1f))
    Text(detail, color=skyMuted, fontSize=12.sp, maxLines=1)
    SkyButton("HOME", Modifier.width(75.dp), back)
  }
}

@Composable
private fun SkyButton(title: String, modifier: Modifier=Modifier, action: () -> Unit) {
  var focus by remember { mutableStateOf(false) }
  Box(modifier.height(38.dp).background(Color(0xFF0A2D63))
    .border(if(focus) 3.dp else 1.dp, if(focus) skyGold else skyBlue)
    .onFocusChanged { focus = it.isFocused }.clickable(onClick=action),
    contentAlignment=Alignment.Center) {
    Text(title, color=if(focus)skyGold else skyWhite, fontSize=12.sp,
      fontWeight=FontWeight.Bold, maxLines=1)
  }
}

@Composable
private fun SkyCategory(title: String, count: Int, selected: Boolean, action: () -> Unit) {
  var focus by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxWidth().height(44.dp)
    .background(if(focus || selected) Color(0xFF163C7A) else skyPanel)
    .border(if(focus) 2.dp else 1.dp, if(focus)skyGold else if(selected)skyBlue else Color(0xFF214A83))
    .onFocusChanged { focus = it.isFocused }.clickable(onClick=action).padding(horizontal=8.dp),
    verticalAlignment=Alignment.CenterVertically) {
    Text(title, color=if(focus)skyGold else skyWhite,
      fontSize=14.sp, maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
    if(count>=0) Text(count.toString(),color=skyMuted,fontSize=12.sp)
  }
}

@Composable
private fun SkyChannel(ch: Item, number: Int, selected: Boolean, focused: () -> Unit, play: () -> Unit) {
  var hasFocus by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxWidth().height(54.dp)
    .background(if(hasFocus) Color(0xFF164A92) else if(selected)Color(0xFF112C65) else skyPanel)
    .border(if(hasFocus)2.dp else 1.dp,if(hasFocus)skyGold else if(selected)skyBlue else Color(0xFF17376B))
    .onFocusChanged { hasFocus=it.isFocused; if(it.isFocused)focused() }
    .clickable(onClick=play).padding(horizontal=6.dp),
    verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Text(number.toString(),color=skyGold,fontSize=12.sp,modifier=Modifier.width(31.dp))
    if(!ch.logo.isNullOrBlank()) AsyncImage(ch.logo,null,
      Modifier.size(width=45.dp,height=35.dp),contentScale=ContentScale.Fit)
    Text(ch.title,color=skyWhite,fontSize=14.sp,maxLines=1,
      overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
    Text("›",color=skyGold,fontSize=17.sp)
  }
}

@Composable
private fun SkyPreview(ch: Item?, preview: Item?, selectPreview: (Item)->Unit, play: (Item)->Unit,
                       modifier: Modifier=Modifier) {
  val ctx=LocalContext.current
  val player=remember(ctx){ExoPlayer.Builder(ctx).build()}
  DisposableEffect(player){ onDispose { player.release() } }
  LaunchedEffect(preview?.id){
    player.stop();player.clearMediaItems()
    if(preview!=null) runCatching {
      player.setMediaItem(MediaItem.fromUri(preview.url))
      player.prepare();player.playWhenReady=true
    }
  }
  val epg by produceState<List<EpgEntry>>(emptyList(),ch?.id){
    value=ch?.let { Epg.peek(it) }?:emptyList()
    if(ch!=null){ delay(350); value=runCatching{Epg.now(ch)}.getOrDefault(emptyList()) }
  }
  val now=Epg.current(epg)
  Column(modifier.background(skyPanel).border(2.dp,skyBlue).padding(8.dp),
    verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("LIVE PREVIEW / EPG",color=skyGold,fontWeight=FontWeight.Bold,fontSize=15.sp)
    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black).border(2.dp,skyBlue),
      contentAlignment=Alignment.Center){
      if(preview!=null) AndroidView(factory={ PlayerView(it).apply { useController=false; this.player=player } },
        update={it.player=player},modifier=Modifier.fillMaxSize())
      else {
        Column(horizontalAlignment=Alignment.CenterHorizontally){
          if(ch?.logo!=null)AsyncImage(ch.logo,null,Modifier.size(76.dp),contentScale=ContentScale.Fit)
          Text("Press PREVIEW to start video",color=skyMuted,fontSize=12.sp)
        }
      }
    }
    Text(ch?.title?:"Select a channel",color=skyWhite,fontSize=17.sp,fontWeight=FontWeight.ExtraBold,
      maxLines=2,overflow=TextOverflow.Ellipsis)
    Text("NOW  "+(now?.title?:"No programme information"),color=skyWhite,fontSize=13.sp,
      maxLines=2,overflow=TextOverflow.Ellipsis)
    if(now!=null)Text(hhmm(now.start)+" - "+hhmm(now.end),color=skyGold,fontSize=12.sp)
    val next=if(now!=null)epg.firstOrNull{it.start>=now.end}else epg.firstOrNull()
    if(next!=null)Text("NEXT  "+next.title,color=skyMuted,fontSize=12.sp,maxLines=2)
    if(ch!=null)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      SkyButton("PREVIEW",Modifier.weight(1f)){selectPreview(ch)}
      SkyButton("FULL SCREEN",Modifier.weight(1f)){play(ch)}
    }
  }
}

@Composable
fun SkyIboLive(liveAll: List<Item>, groupsAll: Map<String,List<Item>>, favs: List<Item>,
               onOpen: (Item)->Unit, onHome: ()->Unit) {
  BackHandler { onHome() }
  val live=remember(liveAll){PrivateCats.filter(liveAll)}
  val liveIds=remember(live){live.mapTo(HashSet()){it.id}}
  val groups=remember(groupsAll,liveIds){
    groupsAll.mapValues{(_,ls)->ls.filter{it.id in liveIds}}.filterValues{it.isNotEmpty()}
  }
  val keys=remember(groups){ listOf("ALL","FAVS")+groups.keys }
  var cat by remember {mutableStateOf("ALL")}
  val list=remember(cat,live,groups,favs){
    when(cat){"ALL"->live;"FAVS"->favs;else->groups[cat].orEmpty()}
  }
  var focusCh by remember {mutableStateOf<Item?>(null)}
  var previewCh by remember {mutableStateOf<Item?>(null)}
  LaunchedEffect(list){focusCh=list.firstOrNull();previewCh=null}
  val cur=focusCh?:list.firstOrNull()
  val wide=LocalConfiguration.current.screenWidthDp>=700
  Column(Modifier.fillMaxSize().background(skyBg)){
    SkyBar("LIVE TV",live.size.toString()+" CHANNELS",onHome)
    if(wide)Row(Modifier.fillMaxSize().padding(6.dp),
      horizontalArrangement=Arrangement.spacedBy(7.dp)){
      Column(Modifier.weight(.27f).fillMaxHeight().border(2.dp,skyBlue)){
        Text("CATEGORIES",modifier=Modifier.fillMaxWidth().background(Color(0xFF082F6A))
          .padding(9.dp),color=skyGold,fontWeight=FontWeight.Bold,fontSize=15.sp)
        LazyColumn(contentPadding=PaddingValues(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
          items(keys,key={it}){k->
            SkyCategory(if(k=="ALL")"All Channels" else if(k=="FAVS")"Favorites" else k,
              if(k=="ALL")live.size else if(k=="FAVS")favs.size else groups[k]?.size?:0,
              cat==k){cat=k}
          }
        }
      }
      Column(Modifier.weight(.38f).fillMaxHeight().border(2.dp,skyBlue)){
        Text("CHANNEL TABLE  ·  "+list.size,modifier=Modifier.fillMaxWidth()
          .background(Color(0xFF082F6A)).padding(9.dp),color=skyGold,
          fontWeight=FontWeight.Bold,fontSize=15.sp)
        LazyColumn(contentPadding=PaddingValues(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
          items(list.size){i->val c=list[i]
            SkyChannel(c,i+1,c.id==focusCh?.id,{focusCh=c}){
              ZapContext.list=list;ZapContext.key=cat;onOpen(c)
            }
          }
        }
      }
      SkyPreview(cur,previewCh,{previewCh=it},{
        ZapContext.list=list;ZapContext.key=cat;onOpen(it)
      },Modifier.weight(.35f).fillMaxHeight())
    }else Column(Modifier.fillMaxSize().padding(5.dp),
      verticalArrangement=Arrangement.spacedBy(5.dp)){
      LazyRow(Modifier.fillMaxWidth().height(45.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        items(keys){k->
          Box(Modifier.widthIn(min=105.dp,max=220.dp)){
            SkyCategory(if(k=="ALL")"All" else if(k=="FAVS")"Favorites" else k,
              if(k=="ALL")live.size else if(k=="FAVS")favs.size else groups[k]?.size?:0,
              cat==k){cat=k}
          }
        }
      }
      LazyColumn(Modifier.weight(1f).border(2.dp,skyBlue),
        contentPadding=PaddingValues(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
        items(list.size){i->val c=list[i]
          SkyChannel(c,i+1,c.id==focusCh?.id,{focusCh=c}){
            ZapContext.list=list;ZapContext.key=cat;onOpen(c)
          }
        }
      }
      SkyPreview(cur,previewCh,{previewCh=it},{
        ZapContext.list=list;ZapContext.key=cat;onOpen(it)
      },Modifier.fillMaxWidth().height(245.dp))
    }
  }
}

@Composable
private fun SkyPoster(item: Item, selected: Boolean, onFocus: ()->Unit, open: ()->Unit){
  var focus by remember {mutableStateOf(false)}
  Column(Modifier.fillMaxWidth().background(skyPanel)
    .border(if(focus)3.dp else 1.dp,if(focus)skyGold else if(selected)skyBlue else Color(0xFF205AA5))
    .onFocusChanged{focus=it.isFocused;if(it.isFocused)onFocus()}
    .clickable(onClick=open).padding(3.dp)){
    Box(Modifier.fillMaxWidth().aspectRatio(.74f).background(Color(0xFF0C326B)),
      contentAlignment=Alignment.Center){
      if(!item.logo.isNullOrBlank())AsyncImage(item.logo,item.title,Modifier.fillMaxSize(),
        contentScale=ContentScale.Crop)
      else Text("SKY",color=skyGold,fontSize=21.sp,fontWeight=FontWeight.Black)
    }
    Text(item.title,color=skyWhite,fontWeight=FontWeight.Bold,fontSize=11.sp,
      maxLines=1,overflow=TextOverflow.Ellipsis,
      modifier=Modifier.fillMaxWidth().background(Color(0xFF06337A)).padding(5.dp))
  }
}

@Composable
fun SkyIboCatalog(kind: Kind, onOpen:(Item)->Unit, onHome:()->Unit){
  BackHandler{onHome()}
  val data=PlaylistStore.channels
  val all=remember(data,kind){data.filter{it.kind==kind}}
  val groups=remember(all){all.groupBy{it.group}}
  val keys=remember(groups){listOf("ALL")+groups.keys}
  var cat by remember(kind){mutableStateOf("ALL")}
  val list=remember(cat,all,groups){if(cat=="ALL")all else groups[cat].orEmpty()}
  var focused by remember {mutableStateOf<Item?>(null)}
  LaunchedEffect(list){focused=list.firstOrNull()}
  val wide=LocalConfiguration.current.screenWidthDp>=700
  Column(Modifier.fillMaxSize().background(skyBg)){
    SkyBar(if(kind==Kind.MOVIE)"MOVIES" else "SERIES",all.size.toString()+" TITLES",onHome)
    if(wide)Row(Modifier.fillMaxSize().padding(6.dp),
      horizontalArrangement=Arrangement.spacedBy(7.dp)){
      Column(Modifier.weight(.23f).fillMaxHeight().border(2.dp,skyBlue)){
        Text("CATEGORIES",color=skyGold,fontWeight=FontWeight.Bold,
          modifier=Modifier.fillMaxWidth().background(Color(0xFF082F6A)).padding(9.dp))
        LazyColumn(contentPadding=PaddingValues(4.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
          items(keys){k->SkyCategory(if(k=="ALL")"All Titles" else k,
            if(k=="ALL")all.size else groups[k]?.size?:0,cat==k){cat=k}}
        }
      }
      LazyVerticalGrid(GridCells.Fixed(4),Modifier.weight(.52f).fillMaxHeight().border(2.dp,skyBlue),
        contentPadding=PaddingValues(6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)){
        items(list,key={it.id}){item->
          SkyPoster(item,item.id==focused?.id,{focused=item},{onOpen(item)})
        }
      }
      Column(Modifier.weight(.25f).fillMaxHeight().background(skyPanel)
        .border(2.dp,skyBlue).padding(10.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("TITLE DETAILS",color=skyGold,fontWeight=FontWeight.Bold)
        val cur=focused
        if(cur!=null){
          if(!cur.logo.isNullOrBlank())AsyncImage(cur.logo,cur.title,
            Modifier.fillMaxWidth().weight(1f),contentScale=ContentScale.Fit)
          Text(cur.title,color=skyWhite,fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=2)
          Text(cur.group,color=skyMuted,fontSize=13.sp,maxLines=2)
          SkyButton("OPEN / PLAY",Modifier.fillMaxWidth()){onOpen(cur)}
        }
      }
    }else{
      LazyRow(Modifier.height(46.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        items(keys){k->Box(Modifier.widthIn(min=110.dp,max=210.dp)){
          SkyCategory(if(k=="ALL")"All Titles" else k,
            if(k=="ALL")all.size else groups[k]?.size?:0,cat==k){cat=k}
        }}
      }
      LazyVerticalGrid(GridCells.Fixed(2),Modifier.fillMaxSize(),
        contentPadding=PaddingValues(8.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp)){
        items(list,key={it.id}){item->
          SkyPoster(item,item.id==focused?.id,{focused=item},{onOpen(item)})
        }
      }
    }
  }
}

@Composable
fun SkyIboSettings(onAdd:()->Unit,reload:()->Unit,onHome:()->Unit){
  BackHandler{onHome()}
  val areas=listOf(
    "GENERAL" to 0,"DISPLAY & THEME" to 3,"PLAYER" to 5,
    "AUDIO / SUBTITLES" to 6,"LIVE TV / EPG" to 8,"PLAYLISTS" to 9,
    "PROFILES" to 10,"PARENTAL CONTROL" to 12,"STORAGE" to 13,
    "SPORTS GUIDE" to 18,"DIAGNOSTICS" to 14,"ABOUT" to 19)
  Column(Modifier.fillMaxSize().background(skyBg)){
    SkyBar("SETTINGS","SKY RETRO",onHome)
    Row(Modifier.fillMaxSize().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
      LazyColumn(Modifier.weight(.27f).fillMaxHeight().border(2.dp,skyBlue),
        contentPadding=PaddingValues(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        items(areas){(title,id)->SkyCategory(title,-1,false){SettingsNav.openPage=id}}
      }
      LazyColumn(Modifier.weight(.73f).fillMaxHeight().border(2.dp,skyBlue)
        .background(skyPanel)){
        item{SettingsHub(onAdd,reload)}
      }
    }
  }
}
