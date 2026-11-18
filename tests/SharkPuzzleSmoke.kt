package com.example.xtreamtv
import kotlin.random.Random
fun main(){
 for(seed in 0..99){
  val maze=SharkPuzzle("maze",Random(seed)); val seen=mutableSetOf(0); val queue=ArrayDeque<Int>();queue.add(0);while(queue.isNotEmpty()){val i=queue.removeFirst();for(n in listOf(i-9,i+9,i-1,i+1)){if(n in maze.cells.indices && kotlin.math.abs(n%9-i%9)+kotlin.math.abs(n/9-i/9)==1 && maze.cells[n]==0 && seen.add(n))queue.add(n)}};check(80 in seen)
  val mem=SharkPuzzle("memory",Random(seed));check(mem.cells.groupBy{it}.values.all{it.size==2})
  val slide=SharkPuzzle("slide",Random(seed));check(slide.cells.sorted()==(0..15).toList());check(slide.cells[slide.cursor]==0)
  val lights=SharkPuzzle("lights",Random(seed));check(lights.cells.any{it==1})
  val mines=SharkPuzzle("mines",Random(seed));mines.action();check(mines.cells[0]>=0 && mines.cells.count{it==-1}==7 && !mines.ended)
  val ttt=SharkPuzzle("ttt",Random(seed));ttt.action();check(ttt.cells.count{it==1}==1 && ttt.cells.count{it==2}==1)
 }
 println("600 seeded game initialization/rule checks passed")
}
