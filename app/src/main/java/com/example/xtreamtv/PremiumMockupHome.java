package com.example.xtreamtv;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/** Native interactive, four-tile landscape home; artwork is blended into full-screen background. */
final class PremiumMockupHome {
 static void show(MainActivity a){
  PremiumVisuals.prepare(a);a.body.removeAllViews();a.body.setPadding(0,0,0,0);
  DesignLayout root=new DesignLayout(a);a.body.addView(root,new LinearLayout.LayoutParams(-1,-1));root.setBackground(new PremiumVisuals.Backdrop(true));
  View logo=PremiumVisuals.brand(a);if(PremiumVisuals.fourSharks!=null)((ImageView)logo).setImageDrawable(null);root.place(logo,24,18,380,67);
  TextClock clock=new TextClock(a);clock.setFormat12Hour("h:mm a");clock.setFormat24Hour("HH:mm");clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);clock.setTextColor(Color.WHITE);clock.setTextSize(22);clock.setTypeface(a.uiRegular);root.place(clock,1250,20,345,52);
  String[] dests={"Live TV","Movies","Series","Settings"};View[] tiles=new View[4];
  for(int i=0;i<4;i++){String dest=dests[i];PremiumVisuals.Tile tile=new PremiumVisuals.Tile(a,dest,i,true);tile.setId(View.generateViewId());tile.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.requestFocus();return false;});tile.setOnClickListener(v->a.navigateHome(dest));root.place(tile,52+i*395,674,365,194);tiles[i]=tile;}
  for(int i=0;i<4;i++){tiles[i].setNextFocusLeftId(tiles[Math.max(0,i-1)].getId());tiles[i].setNextFocusRightId(tiles[Math.min(3,i+1)].getId());}
  tiles[0].requestFocus();root.post(()->{if(root.isAttachedToWindow()&&!root.hasFocus())tiles[0].requestFocus();});
 }
 static final class DesignLayout extends ViewGroup{
  final List<int[]> coords=new ArrayList<>();DesignLayout(MainActivity a){super(a);}
  void place(View v,int x,int y,int w,int h){coords.add(new int[]{x,y,w,h});addView(v,new LayoutParams(w,h));}
  int xx(int x,int w){return Math.round(x*w/1648f);}int yy(int y,int h){return Math.round(y*h/928f);}
  @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1,xx(p[0]+p[2],w)-xx(p[0],w)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,yy(p[1]+p[3],h)-yy(p[1],h)),MeasureSpec.EXACTLY));}}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).layout(xx(p[0],r-l),yy(p[1],b-t),xx(p[0]+p[2],r-l),yy(p[1]+p[3],b-t));}}
 }
}
