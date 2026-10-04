package app.onlymusic.player;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.webkit.*;
import android.view.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public class MainActivity extends Activity {
 private WebView web;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayList<Track> tracks=new ArrayList<>();
 private boolean visible=false;
 private CredentialManager credentials;
 private final Runnable ticker=new Runnable(){public void run(){if(!visible)return;PlaybackService s=PlaybackService.live;if(s!=null)emit("playback",s.state());handler.postDelayed(this,500);}};

 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
  web=new WebView(this);web.setBackgroundColor(Color.rgb(239,202,183));setContentView(web);
  web.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.addJavascriptInterface(new Bridge(),"Native");
  web.setWebViewClient(new WebViewClient(){
   public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
    Uri u=req.getUrl();
    if("https".equals(u.getScheme())&&"app.onlymusic.local".equals(u.getHost())&&"/index.html".equals(u.getPath())){
     try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}catch(IOException ignored){}
    }
    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
   }
   public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   public void onPageFinished(WebView v,String url){scan();profile();}
  });
  credentials=CredentialManager.create(this);web.loadUrl("https://app.onlymusic.local/index.html");
 }
 @Override public void onResume(){super.onResume();visible=true;handler.post(ticker);}
 @Override public void onPause(){visible=false;handler.removeCallbacks(ticker);super.onPause();}
 @Override public void onDestroy(){visible=false;handler.removeCallbacks(ticker);worker.shutdownNow();web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
 @Override public void onBackPressed(){web.evaluateJavascript("window.handleBack && window.handleBack()",value->{if(!"true".equals(value))super.onBackPressed();});}
 private void emit(String event,Object obj){runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.receive("+JSONObject.quote(event)+","+obj.toString()+")",null);});}
 private void message(String text){emit("message",JSONObject.quote(text));}
 private boolean allowed(){return checkSelfPermission(Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_AUDIO":"android.permission.READ_EXTERNAL_STORAGE")==PackageManager.PERMISSION_GRANTED;}
 private void scan(){
  if(worker.isShutdown())return;
  worker.execute(()->{
   ArrayList<Track> result=new ArrayList<>();
   if(allowed()){
    String[] cols={"_id","title","artist","album","duration"};
    try(Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,"duration > 0",null,"title COLLATE NOCASE ASC")){
     if(c!=null)while(c.moveToNext())result.add(new Track("m"+c.getLong(0),ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,c.getLong(0)).toString(),c.getString(1),"<unknown>".equals(c.getString(2))?"Unknown artist":c.getString(2),c.getString(3),c.getLong(4)));
    }catch(Exception e){message("Couldn't scan your music. Try granting access again.");}
   }
   try{
    JSONArray saved=new JSONArray(getPreferences(0).getString("imports","[]"));
    HashSet<String> known=new HashSet<>();for(Track t:result)known.add(t.uri);
    for(int i=0;i<saved.length();i++){Track t=Track.from(saved.getJSONObject(i));if(known.add(t.uri))result.add(t);}
   }catch(JSONException ignored){}
   synchronized(tracks){tracks.clear();tracks.addAll(result);}
   JSONArray a=new JSONArray();for(Track t:result)a.put(t.json());JSONObject payload=new JSONObject();
   try{payload.put("tracks",a).put("permission",allowed());}catch(JSONException ignored){}emit("library",payload);
  });
 }
 private void requestMusic(){
  if(allowed()){scan();return;}
  new AlertDialog.Builder(this).setTitle("Find music on your device").setMessage("Allow music and audio access to show songs stored on your phone. You can also import individual files without granting library access.")
   .setPositiveButton("Continue",(d,w)->requestPermissions(new String[]{Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_AUDIO":"android.permission.READ_EXTERNAL_STORAGE"},11))
   .setNegativeButton("Choose files",(d,w)->pick()).show();
 }
 private void pick(){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,12);
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] results){
  super.onRequestPermissionsResult(r,p,results);
  if(r==11){scan();if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED)message("Music access wasn't granted. You can still import files.");}
 }
 @Override public void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if(request!=12||result!=RESULT_OK||data==null)return;
  ArrayList<Uri> uris=new ArrayList<>();
  if(data.getClipData()!=null)for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri());
  else if(data.getData()!=null)uris.add(data.getData());
  for(Uri u:uris)try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
  worker.execute(()->{
   try{
    JSONArray saved=new JSONArray(getPreferences(0).getString("imports","[]"));HashSet<String> known=new HashSet<>();for(int i=0;i<saved.length();i++)known.add(saved.getJSONObject(i).getString("uri"));
    for(Uri u:uris){
     if(known.contains(u.toString()))continue;
     String title="Audio file",artist="Unknown artist",album="Imported files";long duration=0;
     try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())title=c.getString(0);}catch(Exception ignored){}
     try(MediaMetadataRetriever m=new MediaMetadataRetriever()){
      m.setDataSource(this,u);String s=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);if(s!=null)title=s;
      s=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);if(s!=null)artist=s;
      s=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);if(s!=null)album=s;
      s=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(s!=null)duration=Long.parseLong(s);
     }catch(Exception ignored){}
     saved.put(new Track("u"+UUID.randomUUID(),u.toString(),title,artist,album,duration).json());known.add(u.toString());
    }
    getPreferences(0).edit().putString("imports",saved.toString()).apply();scan();
   }catch(Exception e){message("Couldn't import those files.");}
  });
 }
 private void profile(){
  JSONObject j=new JSONObject();try{
   if(!FirebaseApp.getApps(this).isEmpty()){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();if(u!=null)j.put("name",u.getDisplayName()).put("email",u.getEmail());}
   j.put("configured",getResources().getIdentifier("default_web_client_id","string",getPackageName())!=0);
  }catch(Exception ignored){}emit("profile",j);
 }
 private void signIn(){
  int id=getResources().getIdentifier("default_web_client_id","string",getPackageName());
  if(id==0||FirebaseApp.getApps(this).isEmpty()){
   new AlertDialog.Builder(this).setTitle("Google sign-in needs setup").setMessage("This preview is ready for local music. To enable Google sign-in, the app owner must connect a Firebase project and rebuild with google-services.json. No account is needed to listen.").setPositiveButton("Continue offline",null).show();return;
  }
  GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(getString(id)).build();
  GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
  credentials.getCredentialAsync(this,request,new CancellationSignal(),getMainExecutor(),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
   public void onResult(GetCredentialResponse response){
    try{
     Credential c=response.getCredential();
     if(!(c instanceof CustomCredential)||!c.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)){message("Google returned an unsupported credential.");return;}
     GoogleIdTokenCredential g=GoogleIdTokenCredential.createFrom(c.getData());
     FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(g.getIdToken(),null)).addOnCompleteListener(MainActivity.this,t->{
      if(t.isSuccessful()){profile();message("Signed in with Google.");}
      else message("Sign-in could not be verified. Check your connection and try again.");
     });
    }catch(Exception e){message("Couldn't read the Google sign-in response.");}
   }
   public void onError(GetCredentialException e){message(e instanceof GetCredentialCancellationException?"Sign-in cancelled.":"Google sign-in unavailable. Check your connection, Google account, and app configuration.");}
  });
 }
 public class Bridge {
  @JavascriptInterface public void scan(){MainActivity.this.scan();}
  @JavascriptInterface public void permission(){runOnUiThread(()->requestMusic());}
  @JavascriptInterface public void pick(){runOnUiThread(()->MainActivity.this.pick());}
  @JavascriptInterface public void notifications(){runOnUiThread(()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},13);else message("Playback controls are enabled.");});}
  @JavascriptInterface public void settings(){runOnUiThread(()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));}
  @JavascriptInterface public void signIn(){runOnUiThread(()->MainActivity.this.signIn());}
  @JavascriptInterface public void signOut(){runOnUiThread(()->{
   if(!FirebaseApp.getApps(MainActivity.this).isEmpty())FirebaseAuth.getInstance().signOut();
   credentials.clearCredentialStateAsync(new ClearCredentialStateRequest(),new CancellationSignal(),getMainExecutor(),new CredentialManagerCallback<Void,ClearCredentialException>(){public void onResult(Void v){}public void onError(ClearCredentialException e){}});profile();
  });}
  @JavascriptInterface public void play(String ids,String selected){runOnUiThread(()->{
   try{
    JSONArray requested=new JSONArray(ids);ArrayList<Track> q=new ArrayList<>();int index=0;
    synchronized(tracks){HashMap<String,Track> map=new HashMap<>();for(Track t:tracks)map.put(t.id,t);for(int i=0;i<requested.length();i++){Track t=map.get(requested.getString(i));if(t!=null){if(t.id.equals(selected))index=q.size();q.add(t);}}}
    if(q.isEmpty())return;
    String key=PlaybackService.stageQueue(q);
    startForegroundService(new Intent(MainActivity.this,PlaybackService.class).setAction("PLAY").putExtra("queueKey",key).putExtra("index",index));
   }catch(Exception e){message("Couldn't start playback.");}
  });}
  @JavascriptInterface public void command(String action,int value){runOnUiThread(()->{PlaybackService s=PlaybackService.live;if(s!=null)s.command(action,value);});}
  @JavascriptInterface public void artwork(String id){
   if(worker.isShutdown())return;
   worker.execute(()->{
    Track found=null;synchronized(tracks){for(Track t:tracks)if(t.id.equals(id)){found=t;break;}}
    String data="";
    if(found!=null)try(MediaMetadataRetriever m=new MediaMetadataRetriever()){
     m.setDataSource(MainActivity.this,Uri.parse(found.uri));byte[] bytes=m.getEmbeddedPicture();
     if(bytes!=null){
      BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
      o.inSampleSize=Math.max(1,Math.max(o.outWidth,o.outHeight)/384);o.inJustDecodeBounds=false;
      Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
      if(b!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,80,out);data="data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);b.recycle();}
     }
    }catch(Exception ignored){}
    JSONObject j=new JSONObject();try{j.put("id",id).put("data",data);}catch(JSONException ignored){}emit("artwork",j);
   });
  }
 }
}
