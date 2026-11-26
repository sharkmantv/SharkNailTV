package com.example.xtreamtv;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** UHD artwork layers with accessible, independent native TV controls. */
final class PremiumMockupHome {
 static final int[][] AREAS={{20,477,438,390},{459,477,385,390},{845,477,389,390},{1235,477,397,390}};
 static final int[][] RIMS={{58,510,439,831},{475,510,829,828},{867,511,1219,828},{1257,511,1601,828}};
 static Bitmap backdrop;
 static final Bitmap[] cards=new Bitmap[4];
 static void prepare(MainActivity a){
  if(backdrop!=null)return;
  // Always decode the full prepared UHD layers; the device compositor chooses output resolution.
  BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inSampleSize=1;
  backdrop=BitmapFactory.decodeResource(a.getResources(),R.drawable.design34_backdrop,o);
  int[] ids={R.drawable.design34_card_0,R.drawable.design34_card_1,R.drawable.design34_card_2,R.drawable.design34_card_3};
  for(int i=0;i<4;i++)cards[i]=BitmapFactory.decodeResource(a.getResources(),ids[i],o);
  if(backdrop==null||backdrop.getWidth()!=3840||backdrop.getHeight()!=2160)throw new IllegalStateException("Full UHD home artwork required");
  for(int i=0;i<4;i++){int[] area=AREAS[i];int expectedW=Math.round((area[0]+area[2])*3840f/1648)-Math.round(area[0]*3840f/1648);int expectedH=Math.round((area[1]+area[3])*2160f/928)-Math.round(area[1]*2160f/928);if(cards[i]==null||cards[i].getWidth()!=expectedW||cards[i].getHeight()!=expectedH)throw new IllegalStateException("Full resolution card "+i+" required");}
  android.util.Log.i("SharkUHD","Full resolution 3840x2160 artwork and all four card layers decoded");
 }
 static void show(MainActivity a){
  prepare(a);a.body.removeAllViews();a.body.setPadding(0,0,0,0);
  DesignLayout root=new DesignLayout(a);a.body.addView(root,new LinearLayout.LayoutParams(-1,-1));
  root.setBackgroundColor(0xff06122c);
  ImageView art=new ImageView(a);art.setImageBitmap(backdrop);art.setScaleType(ImageView.ScaleType.FIT_XY);
  art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);art.setFocusable(false);
  root.place(art,0,0,1648,928);
  View logo=PremiumVisuals.brand(a);root.place(logo,574,5,500,128);
  // Anchored to the lower lip of each character in the approved render.
  int[][] mouths={{300,294},{669,295},{1008,301},{1439,285}};
  for(int i=0;i<4;i++)root.place(new MouthDrop(a,i),mouths[i][0]-16,mouths[i][1],32,106);
  String[] dests={"Live TV","Movies","Series","Settings"};View[] tiles=new View[4];
  for(int i=0;i<4;i++){
   String dest=dests[i];HoldingTile tile=new HoldingTile(a,i,dest);tile.setId(View.generateViewId());
   tile.setOnTouchListener((view,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)view.requestFocus();return false;});
   tile.setOnClickListener(view->a.navigateHome(dest));int[] p=AREAS[i];root.place(tile,p[0],p[1],p[2],p[3]);tiles[i]=tile;
  }
  for(int i=0;i<4;i++){tiles[i].setNextFocusLeftId(tiles[Math.max(0,i-1)].getId());tiles[i].setNextFocusRightId(tiles[Math.min(3,i+1)].getId());}
  tiles[0].requestFocus();root.post(()->{if(root.isAttachedToWindow()&&!root.hasFocus())tiles[0].requestFocus();});
 }
 static boolean animations(){return android.os.Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled();}
 static final class MouthDrop extends View{
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path drop=new Path();final int offset;
  MouthDrop(Context c,int i){super(c);offset=i*613;setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas c){
   if(!isShown()||getWindowVisibility()!=VISIBLE||!animations())return;
   float phase=((SystemClock.uptimeMillis()+offset)%2800)/2800f;
   float w=getWidth(),h=getHeight();
   if(phase<.75f){
    float t=phase/.75f,x=w*.50f,y=h*.07f+h*.78f*t*t;
    float radius=Math.max(1.5f,w*.105f);int opacity=(int)(255*Math.min(1,(1-t)*5));
    drop.reset();drop.moveTo(x,y-radius*2.4f);drop.cubicTo(x-radius*.3f,y-radius*.5f,x-radius,y,x-radius,y+radius*.45f);drop.cubicTo(x-radius,y+radius*2.05f,x+radius,y+radius*2.05f,x+radius,y+radius*.45f);drop.cubicTo(x+radius,y,x+radius*.3f,y-radius*.5f,x,y-radius*2.4f);drop.close();
    p.setShader(null);p.setColor(Color.argb(opacity,150,9,20));c.drawPath(drop,p);
    p.setColor(Color.argb(opacity*3/4,239,62,72));c.drawOval(x-radius*.55f,y,x-radius*.05f,y+radius*.9f,p);
   }
   if(isShown())postInvalidateDelayed(40);
  }
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
    boolean animate=animations();
    float phase=FlamePulse.phase();
    FlamePulse.draw(c,glow,rim,35,1,phase);
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
