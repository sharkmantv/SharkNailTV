package com.example.xtreamtv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Reference layout 1280x720; artwork, typography and interactive controls are separate layers. */
final class PremiumWaveLogin {
 final EditText server,user,password;
 final TextView submit,status;
 final TextView back;
 final MainActivity activity;
 static final int WHITE=0xfff7faff, MUTED=0xffc4d6f4;
 static final class Slot {
  final View view; final int x,y,w,h; final float text;
  Slot(View v,int x,int y,int w,int h,float text){view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.text=text;}
 }
 static final class Layout extends ViewGroup{
  final List<Slot> slots=new ArrayList<>(); float scale=1;int ox,oy;
  Layout(Context c){super(c);setClipChildren(false);setFocusable(false);}
  void place(View v,int x,int y,int w,int h,float text){slots.add(new Slot(v,x,y,w,h,text));addView(v,new LayoutParams(w,h));}
  @Override protected void onMeasure(int ws,int hs){
   int w=MeasureSpec.getSize(ws), h=MeasureSpec.getMode(hs)==MeasureSpec.UNSPECIFIED?Math.round(w*720f/1280):MeasureSpec.getSize(hs);
   setMeasuredDimension(w,h);scale=Math.min(w/1280f,h/720f);ox=Math.round((w-1280*scale)/2);oy=Math.round((h-720*scale)/2);
   for(Slot s:slots){if(s.text>0&&s.view instanceof TextView)((TextView)s.view).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,s.text*scale);
    if(s.view instanceof EditText){int right=s.view.getTag() instanceof Integer?(Integer)s.view.getTag():16;s.view.setPadding(Math.round(68*scale),0,Math.round(right*scale),0);}
    s.view.measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(s.w*scale)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(s.h*scale)),MeasureSpec.EXACTLY));
   }
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(Slot s:slots){int x=ox+Math.round(s.x*scale),y=oy+Math.round(s.y*scale);s.view.layout(x,y,x+s.view.getMeasuredWidth(),y+s.view.getMeasuredHeight());}}
 }
 static final class Backdrop extends Drawable{
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  @Override public void draw(Canvas c){Rect r=getBounds();float w=r.width(),h=r.height();if(w<1||h<1)return;int save=c.save();c.translate(r.left,r.top);
   p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff09152f,0xff153a79,0xff0a1637},new float[]{0,.53f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
   p.setShader(new RadialGradient(w*.30f,h*.94f,w*.61f,new int[]{0xc71d69b9,0x001d69b9},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,w/1500));p.setColor(0x474a91dc);
   Path curve=new Path();curve.moveTo(0,h*.93f);curve.cubicTo(w*.35f,h*1.14f,w*.76f,h*.92f,w,h*.65f);c.drawPath(curve,p);
   curve.reset();curve.moveTo(w*.48f,h);curve.cubicTo(w*.72f,h*.93f,w*.88f,h*.84f,w,h*.68f);c.drawPath(curve,p);p.setStyle(Paint.Style.FILL);c.restoreToCount(save);
  }
  @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
 }
 static GradientDrawable surface(int a,int b,int border,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{a,b});g.setCornerRadius(radius);g.setStroke(1,border);return g;}
 static final class Glyph extends View{
  final int kind; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  Glyph(Context c,int k){super(c);kind=k;setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/32f,getHeight()/32f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.65f);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xffc9e4ff);
   if(kind==0){c.drawRoundRect(4,5,28,14,2,2,p);c.drawRoundRect(4,18,28,27,2,2,p);c.drawLine(9,9.5f,10,9.5f,p);c.drawLine(9,22.5f,10,22.5f,p);c.drawLine(15,9.5f,23,9.5f,p);c.drawLine(15,22.5f,23,22.5f,p);}
   else if(kind==1){c.drawCircle(16,9,5,p);Path q=new Path();q.moveTo(5,28);q.lineTo(5,24);q.cubicTo(5,12,27,12,27,24);q.lineTo(27,28);q.close();c.drawPath(q,p);}
   else {c.drawRoundRect(6,14,26,28,2,2,p);c.drawArc(10,3,22,19,180,180,false,p);c.drawLine(10,11,10,14,p);c.drawLine(22,11,22,14,p);c.drawLine(16,20,16,24,p);}
   c.restoreToCount(save);
  }
 }
 TextView label(String value,boolean bold,int gravity){TextView v=new TextView(activity);v.setText(value);v.setTextColor(WHITE);v.setTypeface(bold?activity.uiMedium:activity.uiRegular);v.setGravity(gravity);v.setIncludeFontPadding(false);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);return v;}
 EditText field(Layout layout,String hint,String value,int type,int y,int icon,int right){
  EditText e=new EditText(activity);e.setId(View.generateViewId());activity.styleInput(e);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setInputType(type);e.setTypeface(activity.uiRegular);e.setHintTextColor(MUTED);e.setTextColor(WHITE);e.setGravity(Gravity.CENTER_VERTICAL);e.setTag(right);e.setImeOptions(EditorInfo.IME_ACTION_NEXT|EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setBackground(surface(0xff193b69,0xff0b1934,0xff4c6d98,7));activity.premiumHomeFocus(e);layout.place(e,722,y,465,61,21);
  layout.place(new Glyph(activity,icon),735,y+15,29,29,0);
  View divider=new View(activity);divider.setBackgroundColor(0x70789bc7);divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);layout.place(divider,775,y+16,1,29,0);
  return e;
 }
 PremiumWaveLogin(MainActivity a){
  activity=a;a.body.removeAllViews();a.body.setPadding(0,0,0,0);a.body.setBackground(new Backdrop());
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setFocusable(false);scroll.setClipToPadding(false);a.body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
  Layout layout=new Layout(a);scroll.addView(layout,new ScrollView.LayoutParams(-1,-2));
  ImageView logo=new ImageView(a);logo.setImageResource(R.drawable.sky_shark_home_brand);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("Sky Shark");layout.place(logo,62,62,265,88,0);
  View accent=new View(a);accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xffffce5b,0xffff6a20}));layout.place(accent,60,236,4,91,0);
  layout.place(label("Premium IPTV Streaming",true,Gravity.CENTER_VERTICAL),78,231,540,46,32);
  TextView description=label("Turn your TV into a premium entertainment hub.\nEnjoy live TV, movies and series in one place.",false,Gravity.TOP);description.setTextColor(MUTED);layout.place(description,78,281,560,58,20);
  ImageView promo=new ImageView(a);BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;android.app.ActivityManager am=(android.app.ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);if(am!=null&&am.isLowRamDevice())o.inSampleSize=2;
  Bitmap bitmap=BitmapFactory.decodeResource(a.getResources(),R.drawable.wave_login_promo,o);promo.setImageBitmap(bitmap);promo.setScaleType(ImageView.ScaleType.FIT_CENTER);promo.setContentDescription("Streaming on TV, mobile and Android devices");layout.place(promo,60,368,484,255,0);
  TextView footer=label("Live TV  •  Movies  •  Series  •  Android TV",false,Gravity.CENTER_VERTICAL);footer.setTextColor(MUTED);layout.place(footer,62,662,600,40,16);
  View panel=new View(a);panel.setBackground(surface(0xff152c50,0xff0a142c,0xff3b5a84,13));panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);layout.place(panel,686,73,536,560,0);
  layout.place(label("Login",true,Gravity.CENTER),722,109,465,51,38);
  TextView sub=label("Connect your Xtream Codes account",false,Gravity.CENTER);sub.setTextColor(MUTED);layout.place(sub,722,157,465,29,18);
  server=field(layout,"Server URL / DNS",a.server,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,206,0,16);
  user=field(layout,"Username",a.user,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,287,1,16);
  password=field(layout,"Password",a.pass,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,368,2,58);
  password.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
  TextView eye=label("◉",false,Gravity.CENTER);eye.setId(View.generateViewId());eye.setContentDescription("Show password");eye.setFocusable(true);eye.setClickable(true);a.premiumHomeFocus(eye);layout.place(eye,1134,376,43,45,24);
  eye.setOnClickListener(v->{boolean hidden=password.getTransformationMethod() instanceof PasswordTransformationMethod;password.setTransformationMethod(hidden?null:PasswordTransformationMethod.getInstance());password.setSelection(password.length());eye.setContentDescription(hidden?"Hide password":"Show password");});
  submit=label("Load playlist",true,Gravity.CENTER);submit.setId(View.generateViewId());submit.setFocusable(true);submit.setFocusableInTouchMode(true);submit.setClickable(true);submit.setBackground(surface(0xff2d70bd,0xff0c2b62,0xff719fde,8));a.premiumHomeFocus(submit);layout.place(submit,722,453,465,64,23);
  View rule=new View(a);rule.setBackgroundColor(0x505a7aa9);layout.place(rule,722,544,465,1,0);
  TextView help=label("•  Use your own IPTV provider and server address\n•  Simple setup for Android TV and mobile devices",false,Gravity.CENTER_VERTICAL);help.setTextColor(MUTED);layout.place(help,727,553,455,53,15);
  status=label("",false,Gravity.CENTER);status.setTextColor(MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);layout.place(status,686,636,536,36,15);
  back=label("Back",false,Gravity.CENTER);back.setId(View.generateViewId());back.setFocusable(true);back.setClickable(true);back.setBackground(surface(0xff15355d,0xff10203c,0xff43618d,6));a.premiumHomeFocus(back);layout.place(back,1116,668,106,35,15);
  server.setNextFocusDownId(user.getId());user.setNextFocusUpId(server.getId());user.setNextFocusDownId(password.getId());password.setNextFocusUpId(user.getId());password.setNextFocusDownId(submit.getId());password.setNextFocusRightId(eye.getId());eye.setNextFocusLeftId(password.getId());eye.setNextFocusDownId(submit.getId());eye.setNextFocusUpId(user.getId());submit.setNextFocusUpId(password.getId());submit.setNextFocusDownId(back.getId());back.setNextFocusUpId(submit.getId());
  server.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_NEXT){user.requestFocus();return true;}return false;});
  user.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_NEXT){password.requestFocus();return true;}return false;});
  password.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE){submit.requestFocus();submit.performClick();return true;}return false;});
  a.body.post(()->{if(server.isAttachedToWindow())server.requestFocus();});
 }
 void hideKeyboard(){InputMethodManager manager=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(manager!=null)manager.hideSoftInputFromWindow(server.getWindowToken(),0);}
}
