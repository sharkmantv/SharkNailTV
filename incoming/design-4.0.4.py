from pathlib import Path
p=Path("app/src/main/java/com/example/xtreamtv/MainActivity.kt")
s=p.read_text()
def swap(a,b):
    global s
    assert s.count(a)==1, "Unexpected UI source: "+a[:120]
    s=s.replace(a,b,1)

# Keep the approved nine settings tiles; size the grid to the available height.
a=s.index('                        "Settings" -> {')
b=s.index('                        "Playback & Display" ->',a)
block=s[a:b]
old='''                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {'''
assert block.count(old)==1
block=block.replace(old,'''                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical=10.dp)) {
                            val rowHeight=maxOf(uniformBarHeight, ((maxHeight-24.dp)/3f).coerceAtMost(116.dp))
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)) {''')
block=block.replace('.height(uniformBarHeight)','.height(rowHeight)')
block=block.replace('focused = focused,\n                                                repeatPulse = true','focused = focused,\n                                                steady = true,\n                                                repeatPulse = true')
block=block.replace('.hudSurface()','.background(Brush.verticalGradient(listOf(Color(0xFF102B63),Color(0xFF071A42),Color(0xFF030E2B))))')
assert block.endswith('                        }\n')
block=block[:-len('                        }\n')]+'                            }\n                        }\n'
s=s[:a]+block+s[b:]
swap('val focusScale = if (focused) 1.10f else 1f','val focusScale = if (focused && !steady) 1.10f else 1f')
# Static metallic lettering and pink/blue orbital highlights, rendered at display density.
a=s.index('@Composable private fun GlassChannelWord() {')
b=s.index('@Composable private fun BrowserRow(',a)
s=s[:a]+'''@Composable private fun GlassChannelWord() {
    Box(Modifier.width(46.dp).height(30.dp).padding(end=4.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke=androidx.compose.ui.graphics.drawscope.Stroke(width=1.2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
            val bounds=androidx.compose.ui.geometry.Size(size.width*.91f,size.height*.86f)
            val origin=Offset(size.width*.045f,size.height*.07f)
            drawArc(brush=Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFFFF42C5),Color(0xFFE8B9FF),Color.Transparent)),startAngle=185f,sweepAngle=155f,useCenter=false,topLeft=origin,size=bounds,style=stroke)
            drawArc(brush=Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFF5868FF),Color(0xFF45E8FF))),startAngle=3f,sweepAngle=150f,useCenter=false,topLeft=origin,size=bounds,style=stroke)
        }
        MaterialText("sky",style=TextStyle(fontSize=20.sp,fontWeight=FontWeight.Bold,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic,shadow=Shadow(Color(0xAA02091C),Offset(1f,2f),2f),brush=Brush.verticalGradient(listOf(Color.White,Color(0xFFE5F2FF),Color(0xFF7187A4),Color(0xFFF0F7FF)))))
    }
}
'''+s[b:]
swap('.hudSurface(active).glassFrame(2.5.dp,if(focused)SharkFocusGold else Color(0xFF304564),shape, focused = focused, repeatPulse = true)', '.background(Brush.verticalGradient(if(active) listOf(Color(0xFF133671),Color(0xFF091F4D),Color(0xFF04112F)) else listOf(Color(0xFF0C2455),Color(0xFF06183C),Color(0xFF030D29))),shape).glassFrame(2.dp,if(focused)SharkFocusGold else Color(0xFF489BFF),shape, focused = focused, repeatPulse = true)')
# Only animate the currently focused Settings tile rather than all nine.
swap('''    val fireMotion = rememberInfiniteTransition(label = "Shark TV fire focus")
    val firePhase by fireMotion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "Yellow orange red focus pulse"
    )''','''    val firePhase = if (focused || selected) {
        val fireMotion = rememberInfiniteTransition(label = "Shark TV fire focus")
        fireMotion.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "Yellow orange red focus pulse"
        ).value
    } else 0f''')
# The common frame intentionally dims blue; overlay a clear electric-blue edge
# only for these channel/category rows, leaving the focus pulse visible.
swap('.glassFrame(2.dp,if(focused)SharkFocusGold else Color(0xFF489BFF),shape, focused = focused, repeatPulse = true).clickable(onClick=onClick)', '.glassFrame(2.dp,if(focused)SharkFocusGold else Color(0xFF489BFF),shape, focused = focused, repeatPulse = true).then(if(focused) Modifier else Modifier.border(1.dp,Brush.verticalGradient(listOf(Color(0xFF67B8FF),Color(0xFF2456A8))),shape)).clickable(onClick=onClick)')
p.write_text(s)
print("Design 2 channel styling and evenly spaced Settings grid applied; Home artwork untouched.")
