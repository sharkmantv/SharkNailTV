package com.example.xtreamtv

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AColor
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.random.Random
import kotlin.math.abs

private data class Arcade(val id:String,val title:String,val category:String,val symbol:String,val color:Long)
private val arcade = listOf(Arcade("snake","Shark Snake","Arcade","◆",0xFF16C79A),Arcade("race","Neon Racer","Racing","▲",0xFFFF8055),Arcade("shoot","Space Defender","Shooter","✦",0xFF9966FF),Arcade("pong","Ocean Pong","Sports","●",0xFF30BFFF),Arcade("break","Reef Breaker","Arcade","▦",0xFFFFC34D),Arcade("merge","Shark 2048","Puzzle","2048",0xFFEB65AC),Arcade("maze","Shark Maze","Puzzle","▧",0xFF43D9B8),Arcade("memory","Reef Memory","Puzzle","▣",0xFFBA85FF),Arcade("slide","Slide Fifteen","Puzzle","15",0xFFFFAD66),Arcade("lights","Neon Lights Out","Puzzle","✹",0xFF70E5FF),Arcade("mines","Deep Sea Mines","Strategy","✸",0xFFF9D66A),Arcade("ttt","Shark Tic Tac Toe","Strategy","×○",0xFFFF86B4))

@Composable fun SharkGameHub() {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("shark_arcade",0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites",emptySet())!!.toSet()) }
    var filter by remember { mutableStateOf("All") }
    var recent by remember { mutableStateOf(prefs.getString("history", prefs.getString("recent", "")).orEmpty().split(",").filter { it.isNotBlank() }) }
    var section by remember { mutableStateOf("Instant Arcade") }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Instant Arcade","Retro Consoles","Gaming Engines","Free Games Store","Device & Controllers").forEach { tab -> androidx.compose.material3.Button(onClick={section=tab}) { Text(tab) } }
    }
    if(section=="Retro Consoles") { RetroConsoleHub(); return }
    if(section!="Instant Arcade") { SharkGamingCenter(section); return }
    Text("SHARK GAME HUB",color=Color(0xFF26E7FF),fontSize=25.sp,fontWeight=FontWeight.Bold)
    Text("Instant offline arcade • D-pad / controller • No accounts",color=Color.White,fontSize=14.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All","Favourites","Recent","Arcade","Racing","Shooter","Sports","Puzzle","Strategy").forEach { item -> androidx.compose.material3.Button(onClick={filter=item}) { Text(item) } } }
    val games=if(filter=="Recent") recent.mapNotNull { id -> arcade.find { it.id==id } } else arcade.filter { filter=="All" || (filter=="Favourites" && it.id in favorites) || filter==it.category }
    if(games.isEmpty()) Text("No games here yet",color=Color.White)
    games.chunked(3).forEach { line -> Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) { line.forEach { game ->
        var focused by remember { mutableStateOf(false) }
        Column(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().height(158.dp).onFocusChanged { focused=it.isFocused }.border(if(focused) 3.dp else 1.dp,if(focused) Color.Yellow else Color(game.color),RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(game.color).copy(alpha=.35f),Color(0xFF07142C))),RoundedCornerShape(12.dp)).clickable {
                recent=(listOf(game.id)+recent.filter { it!=game.id }).take(12); prefs.edit().putString("history",recent.joinToString(",")).apply(); context.startActivity(Intent(context,SharkGameActivity::class.java).putExtra("game",game.id))
            }.padding(10.dp)) { val cover=context.resources.getIdentifier("game_"+game.id,"drawable",context.packageName); if(cover!=0) Image(painterResource(cover),contentDescription=null,modifier=Modifier.fillMaxWidth().height(86.dp),contentScale=ContentScale.Crop) else Text(game.symbol,color=Color(game.color),fontSize=30.sp); Text(game.title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp); Text(game.category+" • PLAY",color=Color.White,fontSize=12.sp) }
            androidx.compose.material3.TextButton(onClick={ favorites=if(game.id in favorites) favorites-game.id else favorites+game.id; prefs.edit().putStringSet("favorites",favorites).apply() }) { Text(if(game.id in favorites) "★ Favourite" else "☆ Favourite") }
        }
    }; repeat(3-line.size) { Spacer(Modifier.weight(1f)) } } }
}

class SharkGameActivity:Activity() {
    private lateinit var game: ArcadeView
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.decorView.systemUiVisibility=5894; game=ArcadeView(this,intent.getStringExtra("game").orEmpty()); setContentView(game); game.requestFocus() }
    override fun onPause(){ super.onPause(); game.paused=true }
    override fun onResume(){ super.onResume(); if(::game.isInitialized){game.paused=false;game.invalidate()} }
}
private class ArcadeView(val host:Activity,val mode:String):View(host) {
    private val p=Paint(3); private val random=Random.Default
    private val prefs=host.getSharedPreferences("shark_arcade",0)
    private var high=prefs.getInt("high_$mode",0); private var score=0; private var over=false; var paused=false
    private var x=10; private var y=15; private var dx=1; private var dy=0; private var tick=0
    private var food=Pair(15,8); private val snake=mutableListOf(Pair(10,10),Pair(9,10),Pair(8,10)); private val objects=mutableListOf<Pair<Int,Int>>(); private val shots=mutableListOf<Pair<Int,Int>>()
    private var bx=10; private var by=10; private var vx=1; private var vy=1; private var ai=10; private val bricks=mutableSetOf<Pair<Int,Int>>()
    private var board=IntArray(16); private var last=0L
    private val puzzle=if(mode in listOf("maze","memory","slide","lights","mines","ttt")) SharkPuzzle(mode) else null
    private val title=arcade.firstOrNull{it.id==mode}?.title ?: "Arcade"
    init { isFocusableInTouchMode=true; reset() }
    private fun reset(){ score=0;over=false;tick=0;x=10;y=15;dx=1;dy=0;snake.clear();snake.addAll(listOf(10 to 10,9 to 10,8 to 10));food=15 to 8;objects.clear();shots.clear();bx=10;by=10;vx=1;vy=1;ai=10;bricks.clear();for(i in 0..19)for(j in 2..5)bricks.add(i to j);board=IntArray(16);spawn();spawn();invalidate() }
    private fun add(n:Int){score+=n;if(score>high){high=score;prefs.edit().putInt("high_$mode",high).apply()}}
    private fun spawn(){val free=board.indices.filter{board[it]==0};if(free.isNotEmpty())board[free.random()]=if(random.nextInt(10)==0)4 else 2}
    private fun merged(b:IntArray,key:Int):IntArray { val out=b.copyOf();for(i in 0..3){val indices=(0..3).map{j->when(key){21->i*4+j;22->i*4+3-j;19->j*4+i;else->(3-j)*4+i}};val vals=indices.map{b[it]}.filter{it!=0};val result=mutableListOf<Int>();var j=0;while(j<vals.size){if(j+1<vals.size&&vals[j]==vals[j+1]){result.add(vals[j]*2);j+=2}else{result.add(vals[j]);j++}};indices.forEachIndexed{k,v->out[v]=result.getOrElse(k){0}}};return out }
    private fun move(key:Int){puzzle?.let { if(key==23)it.action() else it.move(key);invalidate();return };if(over){if(key==23)reset();return};if(mode=="merge"){if(key in 19..22){val next=merged(board,key);if(!next.contentEquals(board)){add(next.sum()-board.sum()+1);board=next;spawn()};over=(19..22).all{merged(board,it).contentEquals(board)}};invalidate();return};when(key){19->if(mode=="snake"&&dy!=1){dx=0;dy=-1};20->if(mode=="snake"&&dy!=-1){dx=0;dy=1};21->if(mode=="snake"){if(dx!=1){dx=-1;dy=0}}else{x=(x-1).coerceAtLeast(if(mode=="pong"||mode=="break")2 else 0)};22->if(mode=="snake"){if(dx!=-1){dx=1;dy=0}}else{x=(x+1).coerceAtMost(if(mode=="pong"||mode=="break")17 else 19)};23->if(mode=="shoot")shots.add(x to 14)} }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {when(keyCode){KeyEvent.KEYCODE_BACK->{host.finish();return true};KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_MENU->{puzzle?.action(true);invalidate();return true};19,20,21,22,23,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_ENTER->{move(if(keyCode==KeyEvent.KEYCODE_BUTTON_A||keyCode==KeyEvent.KEYCODE_ENTER)23 else keyCode);return true}};return super.onKeyDown(keyCode,event)}
    override fun onTouchEvent(e:MotionEvent):Boolean {if(e.action==MotionEvent.ACTION_DOWN){if(e.y<height*.12f&&e.x<width*.22f){host.finish();return true};if(puzzle!=null){val g=puzzle;val a=(e.x/width*960f-280f)/(400f/g.size);val b=(e.y/height*540f-65f)/(400f/g.size);if(a>=0&&b>=0&&a<g.size&&b<g.size){val target=b.toInt()*g.size+a.toInt();if(g.ended)g.action() else if(mode=="maze"){val delta=target-g.cursor;when(delta){-1->g.move(21);1->g.move(22);-g.size->g.move(19);g.size->g.move(20)}}else{while(g.cursor%g.size<target%g.size)g.move(22);while(g.cursor%g.size>target%g.size)g.move(21);while(g.cursor/g.size<target/g.size)g.move(20);while(g.cursor/g.size>target/g.size)g.move(19);g.action()};invalidate()};return true};val k=if(over)23 else if(mode=="snake"||mode=="merge"){val rx=e.x/width-.5f;val ry=e.y/height-.5f;if(abs(rx)>abs(ry))if(rx<0)21 else 22 else if(ry<0)19 else 20}else if(e.x<width*.4f)21 else if(e.x>width*.6f)22 else 23;move(k);return true};return true }
    private fun step(){if(puzzle!=null||over||mode=="merge")return;tick++;when(mode){"snake"->{val h=snake.first();val n=h.first+dx to h.second+dy;val eat=n==food;if(n.first !in 0..19||n.second !in 0..15||n in (if(eat)snake else snake.dropLast(1))){over=true;return};snake.add(0,n);if(eat){add(10);val free=(0..19).flatMap{a->(0..15).map{b->a to b}}.filter{it !in snake};if(free.isEmpty()){over=true;return};food=free.random()}else snake.removeAt(snake.lastIndex)};"race","shoot"->{if(tick%5==0)objects.add(random.nextInt(20) to 0);val moved=objects.map{it.first to it.second+1};objects.clear();objects.addAll(moved.filter{it.second<=15});if(mode=="shoot"){val b=shots.map{it.first to it.second-1};shots.clear();shots.addAll(b.filter{it.second>=0});val hit=objects.filter{it in shots};objects.removeAll(hit.toSet());shots.removeAll(hit.toSet());add(hit.size*10)};if((x to 15) in objects)over=true;if(mode=="race")add(1)};"pong","break"->{bx+=vx;by+=vy;if(bx<=0||bx>=19){bx=bx.coerceIn(0,19);vx=-vx};if(mode=="break"){if(by<=0){by=0;vy=1};if(bricks.remove(bx to by)){vy=-vy;add(10);if(bricks.isEmpty())over=true}}else{if(tick%2==0)ai+=(bx-ai).coerceIn(-1,1);if(by<=1){if(abs(bx-ai)<=2){vy=1;by=1}else{add(10);bx=10;by=8;vy=1}}};if(by>=15){if(abs(bx-x)<=2){by=14;vy=-1;add(1)}else over=true}}} }
    private fun rect(c:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int){p.color=color;c.drawRoundRect(x,y,x+w,y+h,5f,5f,p)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int=AColor.WHITE){p.color=color;p.textSize=size;c.drawText(s,x,y,p)}
    override fun onDraw(c:Canvas){super.onDraw(c);val now=android.os.SystemClock.uptimeMillis();if(!paused&&now-last >= (if(mode=="snake") 140 else 95)){last=now;step()};c.drawColor(0xFF061225.toInt());c.save();c.scale(width/960f,height/540f);text(c,"‹ BACK",25f,38f,22f);text(c,title,220f,38f,28f);text(c,if(puzzle!=null) "PUZZLE" else "SCORE $score   BEST $high",620f,38f,20f);val cyan=0xFF26E7FF.toInt();val pink=0xFFFF5C8A.toInt();fun cell(a:Int,b:Int,color:Int){rect(c,180f+a*30f,56f+b*26f,28f,24f,color)}
    if(puzzle!=null){val g=puzzle;val side=400f/g.size;for(i in g.cells.indices){val a=280f+i%g.size*side;val b=65f+i/g.size*side;val v=g.cells[i];val visible=mode!="memory"&&mode!="mines"||g.revealed[i]||g.ended;val label=when(mode){"maze"->if(i==g.cells.lastIndex)"EXIT" else if(i==g.cursor)"●" else "";"memory"->if(visible)v.toString() else "?";"slide"->if(v==0)"" else v.toString();"lights"->if(v==1)"●" else "";"mines"->if(g.flags[i])"F" else if(!visible)"?" else if(v<0)"*" else v.toString();else->when(v){1->"X";2->"O";else->""}};val fill=if(mode=="maze"&&v==1)0xFF071225.toInt() else if(mode=="lights"&&v==1)0xFF32AACC.toInt() else 0xFF193B55.toInt();if(i==g.cursor)rect(c,a,b,side-2,side-2,AColor.YELLOW);rect(c,a+3,b+3,side-8,side-8,fill);text(c,label,a+side*.18f,b+side*.64f,if(label=="EXIT")12f else side*.42f)};text(c,"MOVES / SCORE ${g.score}",25f,85f,18f);if(g.ended)text(c,if(g.won)"YOU WIN!" else "ROUND OVER",25f,125f,22f)}else if(mode=="merge"){for(i in board.indices){val a=280f+i%4*100;val b=65f+i/4*100;rect(c,a,b,94f,94f,if(board[i]==0)0xFF19314B.toInt() else 0xFF126F88.toInt());if(board[i]>0)text(c,board[i].toString(),a+15,b+58,32f)}}else{rect(c,178f,54f,604f,420f,0xFF132842.toInt());when(mode){"snake"->{snake.forEach{cell(it.first,it.second,cyan)};cell(food.first,food.second,pink)};"race","shoot"->{objects.forEach{cell(it.first,it.second,pink)};shots.forEach{cell(it.first,it.second,AColor.YELLOW)};cell(x,15,cyan)};else->{bricks.filter{mode=="break"}.forEach{cell(it.first,it.second,0xFF9966FF.toInt())};for(i in x-2..x+2)cell(i,15,cyan);if(mode=="pong")for(i in (ai-2).coerceAtLeast(0)..(ai+2).coerceAtMost(19))cell(i,0,pink);cell(bx,by,AColor.WHITE)}}};text(c,if(puzzle!=null) { if(puzzle.ended) "OK / A to restart • Back to hub" else if(mode=="mines") "D-pad • OK reveal • Menu / X flag" else when(mode){"maze"->"D-pad: reach the exit";"memory"->"Match pairs • OK turns cards / clears mismatch";"slide"->"Slide tiles into order • OK moves adjacent tile";"lights"->"Switch all lights off • OK toggles neighbours";"ttt"->"Three in a row • OK places X";else->"D-pad • OK / A select • Back to hub"} } else if(over)"ROUND OVER — press OK / A to restart" else if(mode=="shoot")"Left / Right • OK / A to fire • Back to hub" else "D-pad to play • Back to hub",220f,515f,20f);c.restore();if(!paused)postInvalidateDelayed(32) }
}
