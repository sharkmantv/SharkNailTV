package com.example.xtreamtv;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import okhttp3.*;
/** Public regional XMLTV for lawful use, bounded disk index, no provider credentials. */
final class RegionalGuide {
 static final long DAY=86400000L;
 static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).followSslRedirects(false).build();
 final File dir;final ExecutorService worker=Executors.newSingleThreadExecutor();
 final Set<String> pending=new HashSet<>();final Map<String,Long> retry=new HashMap<>();
 final Runnable changed;volatile boolean closed,busy;volatile Call active;
 RegionalGuide(Context context,Runnable changed){dir=new File(context.getCacheDir(),"regional-epg-v1");this.changed=changed;}
 File file(String region){return new File(dir,region+".db");}
 synchronized void ensure(String region){
  if(closed||busy||RegionalGuideNames.file(region).isEmpty()||pending.contains(region))return;
  long now=System.currentTimeMillis();File f=file(region);
  if(f.isFile()&&now-f.lastModified()<DAY)return;
  if(now<retry.getOrDefault(region,0L))return;
  pending.add(region);
  try{worker.execute(()->{boolean ok=false,cancelled=false;try{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);download(region);ok=true;}
   catch(InterruptedException e){cancelled=true;}
    catch(Exception e){android.util.Log.w("SharkEPG","Regional "+region+" unavailable: "+e.getClass().getSimpleName());}
   finally{synchronized(this){pending.remove(region);if(!cancelled)retry.put(region,System.currentTimeMillis()+(ok?DAY:3600000L));}if(ok&&!closed)changed.run();}
  });}catch(RejectedExecutionException ignored){pending.remove(region);}
 }
 synchronized void setBusy(boolean value){busy=value;if(value&&active!=null)active.cancel();}
 synchronized void close(){closed=true;Call call=active;if(call!=null)call.cancel();worker.shutdownNow();}
 static String attr(XmlPullParser p,String k){String v=p.getAttributeValue(null,k);return v==null?"":v;}
 static String limit(String s,int n){return s.length()>n?s.substring(0,n):s;}
 static final class Bounded extends FilterInputStream{
  long left;Bounded(InputStream in,long size){super(in);left=size;}
  @Override public int read()throws IOException{if(left<=0)throw new IOException("EPG size limit");int v=super.read();if(v>=0)left--;return v;}
  @Override public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;if(left<=0)throw new IOException("EPG size limit");int n=super.read(b,off,(int)Math.min(len,left));if(n>0)left-=n;return n;}
 }
 void download(String region)throws Exception{
  if(closed||busy)throw new InterruptedException();
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("EPG cache");
  File tmp=File.createTempFile(region+"-", ".tmp",dir);
  String url="https://epgshare01.online/epgshare01/epg_ripper_"+RegionalGuideNames.file(region)+".xml.gz";
  Call call=HTTP.newCall(new Request.Builder().url(url).header("User-Agent","SkyShark-EPG/1.0").build());synchronized(this){if(closed||busy){tmp.delete();throw new InterruptedException();}active=call;}
  try(Response res=call.execute()){
   if(!res.isSuccessful()||res.body()==null)throw new IOException("HTTP "+res.code());
   try(InputStream input=new Bounded(new GZIPInputStream(new Bounded(res.body().byteStream(),24L*1024*1024)),200L*1024*1024)){importXml(input,tmp);}
   if(closed||busy||call.isCanceled()||Thread.currentThread().isInterrupted())throw new InterruptedException();
   android.system.Os.rename(tmp.getAbsolutePath(),file(region).getAbsolutePath());
   file(region).setLastModified(System.currentTimeMillis());
   android.util.Log.i("SharkEPG","Regional "+region+" cache ready");
  }catch(IOException e){if(call.isCanceled())throw new InterruptedException();throw e;}finally{active=null;tmp.delete();new File(tmp.getPath()+"-journal").delete();}
 }
 static void importXml(InputStream in,File tmp)throws Exception{
  long now=System.currentTimeMillis();int count=0,channels=0;
  try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(tmp,null)){
   db.execSQL("PRAGMA journal_mode=DELETE");
   db.execSQL("CREATE TABLE aliases (name TEXT NOT NULL,id TEXT NOT NULL,PRIMARY KEY(name,id))");
   db.execSQL("CREATE TABLE programmes (id TEXT NOT NULL,start INTEGER,end INTEGER,title TEXT,description TEXT,PRIMARY KEY(id,start,end))");
   XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);p.setInput(in,null);
   String channel="",pid="",title="",desc="";long start=0,end=0;boolean programme=false;
   db.beginTransaction();
   try(SQLiteStatement alias=db.compileStatement("INSERT OR IGNORE INTO aliases VALUES (?,?)");SQLiteStatement insert=db.compileStatement("INSERT OR IGNORE INTO programmes VALUES (?,?,?,?,?)")){
    for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
     if(Thread.currentThread().isInterrupted())throw new InterruptedException();
     if(event==XmlPullParser.START_TAG){
      String tag=p.getName();
      if("channel".equals(tag)){channel=attr(p,"id");if(++channels>10000)throw new IOException("Too many channels");addAlias(alias,RegionalGuideNames.normalize(channel),channel);addAlias(alias,"id:"+channel.toLowerCase(Locale.ROOT),channel);}
      else if("display-name".equals(tag)&&!channel.isEmpty()){String value=p.nextText();addAlias(alias,RegionalGuideNames.normalize(value),channel);}
      else if("programme".equals(tag)){programme=true;pid=attr(p,"channel");start=RegionalGuideNames.epoch(attr(p,"start"));end=RegionalGuideNames.epoch(attr(p,"stop"));title=desc="";}
      else if(programme&&("title".equals(tag)||"desc".equals(tag))){String lang=attr(p,"lang");String value=p.nextText();if(lang.isEmpty()||lang.toLowerCase(Locale.ROOT).startsWith("en")){if("title".equals(tag)&&title.isEmpty())title=limit(value,300);else if("desc".equals(tag)&&desc.isEmpty())desc=limit(value,600);}}
     }else if(event==XmlPullParser.END_TAG){
      if("channel".equals(p.getName()))channel="";
      else if("programme".equals(p.getName())){programme=false;
       if(!pid.isEmpty()&&!title.isEmpty()&&start>0&&end>start&&end>now-6*3600000L&&start<now+3*DAY){
        insert.clearBindings();insert.bindString(1,pid);insert.bindLong(2,start);insert.bindLong(3,end);insert.bindString(4,title);insert.bindString(5,desc);insert.executeInsert();if(++count>300000)throw new IOException("Too many programmes");
       }
      }
     }
    }
    if(count==0||channels==0)throw new IOException("Empty or stale feed");
    db.setTransactionSuccessful();
   }finally{db.endTransaction();}
  }
 }
 static void addAlias(SQLiteStatement s,String name,String id){if(name.isEmpty()||id.isEmpty()||name.length()>300||id.length()>250)return;s.clearBindings();s.bindString(1,name);s.bindString(2,id);s.executeInsert();}
 static String unique(SQLiteDatabase db,String name){
  if(name.isEmpty())return "";
  try(Cursor c=db.rawQuery("SELECT id FROM aliases WHERE name=? LIMIT 2",new String[]{name})){if(!c.moveToFirst())return "";String id=c.getString(0);return c.moveToNext()?"":id;}
 }
 List<MainActivity.Programme> lookup(String region,String name,String epgId){
  List<MainActivity.Programme> out=new ArrayList<>();File f=file(region);if(!f.isFile()||System.currentTimeMillis()-f.lastModified()>3*DAY)return out;
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(f.getPath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
   String id=unique(db,"id:"+epgId.toLowerCase(Locale.ROOT));
   if(id.isEmpty())id=unique(db,RegionalGuideNames.normalize(name));if(id.isEmpty())return out;
   try(Cursor c=db.rawQuery("SELECT start,end,title,description FROM programmes WHERE id=? AND end>? ORDER BY start LIMIT 300",new String[]{id,Long.toString(System.currentTimeMillis()-6*3600000L)})){
    while(c.moveToNext()){MainActivity.Programme p=new MainActivity.Programme();p.start=c.getLong(0);p.end=c.getLong(1);p.title=c.getString(2);p.description=c.getString(3);p.rawStart=p.rawEnd="";out.add(p);}
   }
  }catch(Exception ignored){}
  return out;
 }
}
