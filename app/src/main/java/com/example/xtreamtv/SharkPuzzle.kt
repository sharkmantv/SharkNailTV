package com.example.xtreamtv

import kotlin.random.Random
import kotlin.math.abs

/** Pure Kotlin game rules, independent of Android rendering. */
internal class SharkPuzzle(val mode:String, private val rng:Random=Random.Default) {
    val size=when(mode){"maze"->9;"memory","slide","lights"->4;"mines"->6;else->3}
    val cells=IntArray(size*size)
    val revealed=BooleanArray(cells.size)
    val flags=BooleanArray(cells.size)
    var cursor=0; private set
    var score=0; private set
    var ended=false; private set
    var won=false; private set
    private var first=-1; private var second=-1
    private var started=false
    init { reset() }
    private fun neighbors(i:Int)=listOf(i-1,i+1,i-size,i+size).filter{it in cells.indices && abs(it%size-i%size)+abs(it/size-i/size)==1}
    private fun toggle(i:Int){(neighbors(i)+i).forEach{cells[it]=1-cells[it]}}
    fun reset(){cells.fill(0);revealed.fill(false);flags.fill(false);cursor=0;score=0;ended=false;won=false;first=-1;second=-1;started=false
        when(mode){
            "memory"->(0 until 8).flatMap{listOf(it+1,it+1)}.shuffled(rng).forEachIndexed{i,v->cells[i]=v}
            "slide"->{for(i in cells.indices)cells[i]=(i+1)%cells.size;var blank=cells.lastIndex;repeat(80){val n=neighbors(blank).random(rng);cells[blank]=cells[n];cells[n]=0;blank=n};cursor=blank}
            "lights"->{repeat(12){toggle(rng.nextInt(cells.size))};if(cells.all{it==0})toggle(0)}
            "maze"->{cells.fill(1);fun carve(i:Int){cells[i]=0;val x=i%size;val y=i/size;listOf(2 to 0,-2 to 0,0 to 2,0 to -2).shuffled(rng).forEach{(dx,dy)->val a=x+dx;val b=y+dy;if(a in 0 until size && b in 0 until size){val n=b*size+a;if(cells[n]==1){cells[(n+i)/2]=0;carve(n)}}}};carve(0)}
        }
    }
    fun move(key:Int){if(ended)return;val next=when(key){19->cursor-size;20->cursor+size;21->if(cursor%size>0)cursor-1 else cursor;22->if(cursor%size<size-1)cursor+1 else cursor;else->cursor};if(next !in cells.indices)return
        if(mode=="maze"){if(cells[next]==0){cursor=next;score++;if(cursor==cells.lastIndex)finish(true)}}else cursor=next
    }
    private fun finish(success:Boolean){ended=true;won=success}
    private fun lineWinner():Int {val lines=listOf(listOf(0,1,2),listOf(3,4,5),listOf(6,7,8),listOf(0,3,6),listOf(1,4,7),listOf(2,5,8),listOf(0,4,8),listOf(2,4,6));return lines.firstOrNull{cells[it[0]]!=0&&it.all{n->cells[n]==cells[it[0]]}}?.let{cells[it[0]]}?:0}
    fun action(alt:Boolean=false){if(ended){reset();return};when(mode){
        "memory"->{if(second>=0){revealed[first]=false;revealed[second]=false;first=-1;second=-1;return};if(revealed[cursor])return;revealed[cursor]=true;if(first<0)first=cursor else if(cells[first]==cells[cursor]){score+=10;first=-1;if(revealed.all{it})finish(true)}else second=cursor}
        "slide"->{val blank=cells.indexOf(0);if(cursor in neighbors(blank)){cells[blank]=cells[cursor];cells[cursor]=0;score++;if(cells.indices.all{cells[it]==(it+1)%cells.size})finish(true)}}
        "lights"->{toggle(cursor);score++;if(cells.all{it==0})finish(true)}
        "mines"->{if(alt){if(!revealed[cursor])flags[cursor]=!flags[cursor];return};if(flags[cursor]||revealed[cursor])return;if(!started){started=true;cells.indices.filter{it!=cursor}.shuffled(rng).take(7).forEach{cells[it]=-1};for(i in cells.indices)if(cells[i]!=-1){cells[i]=cells.indices.count{cells[it]==-1&&abs(it%size-i%size)<=1&&abs(it/size-i/size)<=1}}};if(cells[cursor]==-1){revealed[cursor]=true;finish(false)}else{revealed[cursor]=true;score++;if(cells.indices.all{cells[it]==-1||revealed[it]})finish(true)}}
        "ttt"->{if(cells[cursor]!=0)return;cells[cursor]=1;score++;if(lineWinner()==1){finish(true);return};val free=cells.indices.filter{cells[it]==0};if(free.isEmpty()){finish(false);return};fun tactical(v:Int):Int? { return free.firstOrNull{cells[it]=v;val win=lineWinner()==v;cells[it]=0;win} };val ai=tactical(2)?:tactical(1)?:free.random(rng);cells[ai]=2;if(lineWinner()==2||cells.none{it==0})finish(false)}
    }}
}
