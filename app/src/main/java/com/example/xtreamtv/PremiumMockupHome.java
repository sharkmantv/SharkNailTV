package com.example.xtreamtv;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Four independently rendered, accessible controls with the approved shark-held artwork. */
final class PremiumMockupHome {
 static final int[][] AREAS={{48,490,400,350},{456,490,380,350},{842,490,386,350},{1238,490,382,350}};
 static final int[][] RIMS={{70,516,428,823},{484,527,812,815},{858,527,1196,815},{1264,527,1593,815}};
 static Bitmap backdrop;
 static final Bitmap[] cards=new Bitmap[4];
 static void prepare(MainActivity a){
  if(backdrop!=null)return;
  BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;
  backdrop=BitmapFactory.decodeResource(a.getResources(),R.drawable.design2_backdrop,o);
  int[] ids={R.drawable.design2_card_0,R.drawable.design2_card_1,R.drawable.design2_card_2,R.drawable.design2_card_3};
  for(int i=0;i<4;i++)cards[i]=BitmapFactory.decodeResource(a.getResources(),ids[i],o);
 }
 static void show(MainActivity a){
  prepare(a);a.body.removeAllViews();a.body.setPadding(0,0,0,0);
  DesignLayout root=new DesignLayout(a);a.body.addView(root,new LinearLayout.LayoutParams(-1,-1));
  root.setBackgroundColor(0xff06122c);
  ImageView art=new ImageView(a);art.setImageBitmap(backdrop);art.setScaleType(ImageView.ScaleType.FIT_XY);
  art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);art.setContentDescription("SHARK SKY");art.setFocusable(false);
  root.place(art,0,0,1648,928);
  String[] dests={"Live TV","Movies","Series","Settings"};View[] tiles=new View[4];
  for(int i=0;i<4;i++){
   String dest=dests[i];HoldingTile tile=new HoldingTile(a,i,dest);tile.setId(View.generateViewId());
   tile.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.requestFocus();return false;});
   tile.setOnClickListener(v->a.navigateHome(dest));int[] p=AREAS[i];root.place(tile,p[0],p[1],p[2],p[3]);tiles[i]=tile;
  }
  for(int i=0;i<4;i++){tiles[i].setNextFocusLeftId(tiles[Math.max(0,i-1)].getId());tiles[i].setNextFocusRightId(tiles[Math.min(3,i+1)].getId());}
  tiles[0].requestFocus();root.post(()->{if(root.isAttachedToWindow()&&!root.hasFocus())tiles[0].requestFocus();});
 }
 static final class HoldingTile extends View{
  final int index;final Paint art=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG);
  final RectF rim;
  HoldingTile(MainActivity a,int i,String title){super(a);index=i;int[] box=AREAS[i],r=RIMS[i];
   rim=new RectF(r[0]-box[0],r[1]-box[1],r[2]-box[0],r[3]-box[1]);
   setContentDescription(title);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }
  @Override protected void onFocusChanged(boolean f,int direction,Rect previous){super.onFocusChanged(f,direction,previous);invalidate();}
  @Override protected void onDraw(Canvas c){super.onDraw(c);int saved=c.save();
   int[] b=AREAS[index];c.scale(getWidth()/(float)b[2],getHeight()/(float)b[3]);
   c.drawBitmap(cards[index],null,new RectF(0,0,b[2],b[3]),art);
   if(isFocused()){
    boolean animate=android.os.Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled();
    float phase=animate?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1800*Math.PI*2/1800.0)):1;
    // Keep the gold stroke underneath the shark's gripping fingers, not over their hands.
    int clip=c.save();
    float handDepth=index==0?41:112;
    if(android.os.Build.VERSION.SDK_INT>=26){
     c.clipOutRect(rim.left-20,rim.top-20,rim.left+(index==0?91:36),rim.top+handDepth);
     c.clipOutRect(rim.right-(index==0?85:36),rim.top-20,rim.right+20,rim.top+handDepth);
    }else{
     c.clipRect(rim.left-20,rim.top-20,rim.left+(index==0?91:36),rim.top+handDepth,Region.Op.DIFFERENCE);
     c.clipRect(rim.right-(index==0?85:36),rim.top-20,rim.right+20,rim.top+handDepth,Region.Op.DIFFERENCE);
    }
    glow.setStyle(Paint.Style.STROKE);
    for(int layer=5;layer>=1;layer--){glow.setStrokeWidth(4+layer*(2+phase));glow.setColor(Color.argb((int)((9+phase*9)*(6-layer)),255,179,0));c.drawRoundRect(rim,32,32,glow);}
    glow.setStrokeWidth(5+2*phase);glow.setColor(0xffffdb18);c.drawRoundRect(rim,32,32,glow);
    glow.setStrokeWidth(2);glow.setColor(0xffffffb0);c.drawRoundRect(rim,32,32,glow);
    c.restoreToCount(clip);
    if(animate&&isShown())postInvalidateDelayed(40);
   }
   c.restoreToCount(saved);
  }
 }
 static final class DesignLayout extends ViewGroup{
  final List<int[]> coords=new ArrayList<>();DesignLayout(MainActivity a){super(a);}
  void place(View v,int x,int y,int w,int h){coords.add(new int[]{x,y,w,h});addView(v,new LayoutParams(w,h));}
  int xx(int x,int w){return Math.round(x*w/1648f);}int yy(int y,int h){return Math.round(y*h/928f);}
  @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1,xx(p[0]+p[2],w)-xx(p[0],w)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,yy(p[1]+p[3],h)-yy(p[1],h)),MeasureSpec.EXACTLY));}}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).layout(xx(p[0],r-l),yy(p[1],b-t),xx(p[0]+p[2],r-l),yy(p[1]+p[3],b-t));}}
 }
}
