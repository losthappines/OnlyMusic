package app.onlymusic.player;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.net.Uri;
import java.util.*;
import org.json.*;

public class PlaybackService extends Service {
 public static PlaybackService live;
 // Queue requests stay in this process, avoiding Binder size limits with big libraries.
 private static final Map<String,List<Track>> pendingQueues=new HashMap<>();
 public static synchronized String stageQueue(List<Track> queue){
  String key=UUID.randomUUID().toString();pendingQueues.put(key,new ArrayList<>(queue));return key;
 }
 private static synchronized List<Track> takeQueue(String key){return pendingQueues.remove(key);}
 private MediaPlayer player;
 private MediaSession session;
 private AudioManager audio;
 private AudioFocusRequest focus;
 private boolean resumeFocus=false,prepared=false,shuffle=false;
 private final ArrayList<Track> queue=new ArrayList<>();
 private int index=-1,repeat=0;
 private String error="";
 private final Random random=new Random();
 private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause();}};

 @Override public void onCreate(){
  super.onCreate();live=this;audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("music","Music playback",NotificationManager.IMPORTANCE_LOW));
  session=new MediaSession(this,"Onlymusic");
  session.setCallback(new MediaSession.Callback(){
   public void onPlay(){resume();}public void onPause(){pause();}
   public void onSkipToNext(){next(false);}public void onSkipToPrevious(){previous();}
   public void onSeekTo(long p){seek((int)p);}
   public void onStop(){pause();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
  });session.setActive(true);
  AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{
   if(change==AudioManager.AUDIOFOCUS_LOSS){resumeFocus=false;pause();}
   else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeFocus=playing();if(prepared&&player!=null)player.pause();update();}
   else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(player!=null)player.setVolume(.2f,.2f);}
   else if(change==AudioManager.AUDIOFOCUS_GAIN){if(player!=null)player.setVolume(1,1);if(resumeFocus){resumeFocus=false;resume();}}
  }).build();
  if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);
  else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  startForeground(1,notification());
  if(intent!=null){String a=intent.getAction();
   if("PLAY".equals(a)){
    List<Track> incoming=takeQueue(intent.getStringExtra("queueKey"));
    if(incoming!=null&&!incoming.isEmpty()){
     queue.clear();queue.addAll(incoming);index=Math.max(0,Math.min(intent.getIntExtra("index",0),queue.size()-1));playCurrent();
    }else if(current()==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
   }else if("TOGGLE".equals(a)){if(playing())pause();else resume();}
   else if("NEXT".equals(a))next(false);else if("PREV".equals(a))previous();
  }return START_NOT_STICKY;
 }
 private Track current(){return index>=0&&index<queue.size()?queue.get(index):null;}
 private void playCurrent(){
  Track t=current();if(t==null){stopSelf();return;}
  if(player!=null)player.release();prepared=false;error="";resumeFocus=false;
  player=new MediaPlayer();
  try{
   player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
   player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);player.setDataSource(this,Uri.parse(t.uri));
   player.setOnPreparedListener(mp->{if(player!=mp)return;prepared=true;resume();});
   player.setOnCompletionListener(mp->next(true));
   player.setOnErrorListener((mp,w,e)->{fail("This file cannot be played. Try another track.");return true;});
   player.prepareAsync();update();
  }catch(Exception e){fail("File unavailable. Import it again or grant music access.");}
 }
 private void fail(String message){
  error=message;prepared=false;if(player!=null){player.release();player=null;}
  audio.abandonAudioFocusRequest(focus);update();stopForeground(STOP_FOREGROUND_DETACH);
 }
 public boolean playing(){try{return prepared&&player!=null&&player.isPlaying();}catch(Exception e){return false;}}
 public void resume(){
  if(!prepared||player==null){if(current()!=null&&player==null)playCurrent();return;}
  startForeground(1,notification());
  if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){error="Audio is being used by another app.";update();return;}
  error="";player.start();update();
 }
 public void pause(){resumeFocus=false;if(prepared&&player!=null)player.pause();audio.abandonAudioFocusRequest(focus);update();stopForeground(STOP_FOREGROUND_DETACH);}
 public void seek(int position){if(prepared&&player!=null){player.seekTo(Math.max(0,Math.min(position,player.getDuration())));update();}}
 public void next(boolean completed){
  if(queue.isEmpty())return;
  if(completed&&repeat==2){seek(0);resume();return;}
  if(shuffle&&queue.size()>1){int n;do{n=random.nextInt(queue.size());}while(n==index);index=n;}
  else if(index+1<queue.size())index++;
  else if(repeat==1||!completed)index=0;
  else{pause();seek(0);return;}playCurrent();
 }
 public void previous(){if(prepared&&player!=null&&player.getCurrentPosition()>3000){seek(0);return;}if(!queue.isEmpty()){index=(index-1+queue.size())%queue.size();playCurrent();}}
 public void command(String action,int value){
  switch(action){case "toggle":if(playing())pause();else resume();break;case "next":next(false);break;case "previous":previous();break;case "seek":seek(value);break;case "shuffle":shuffle=!shuffle;break;case "repeat":repeat=(repeat+1)%3;break;}
  update();
 }
 public JSONObject state(){
  JSONObject j=new JSONObject();try{
   j.put("playing",playing()).put("loading",player!=null&&!prepared).put("shuffle",shuffle).put("repeat",repeat).put("error",error).put("index",index).put("position",prepared?player.getCurrentPosition():0).put("duration",prepared?player.getDuration():0);
   Track t=current();j.put("track",t==null?JSONObject.NULL:t.json());JSONArray q=new JSONArray();for(Track x:queue)q.put(x.json());j.put("queue",q);
  }catch(Exception ignored){}return j;
 }
 private PendingIntent action(String name){return PendingIntent.getService(this,name.hashCode(),new Intent(this,PlaybackService.class).setAction(name),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 private Notification notification(){
  Track t=current();PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,"music").setSmallIcon(R.drawable.ic_music).setContentTitle(t==null?"Onlymusic":t.title).setContentText(t==null?"Your music, beautifully local.":t.artist).setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(playing())
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",action("PREV")).build())
   .addAction(new Notification.Action.Builder(playing()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing()?"Pause":"Play",action("TOGGLE")).build())
   .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",action("NEXT")).build())
   .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();
 }
 private void update(){
  Track t=current();if(t!=null)session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,t.title).putString(MediaMetadata.METADATA_KEY_ARTIST,t.artist).putString(MediaMetadata.METADATA_KEY_ALBUM,t.album).putLong(MediaMetadata.METADATA_KEY_DURATION,prepared?player.getDuration():t.duration).build());
  session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP).setState(playing()?PlaybackState.STATE_PLAYING:prepared?PlaybackState.STATE_PAUSED:error.isEmpty()?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_ERROR,prepared?player.getCurrentPosition():0,playing()?1:0).build());
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification());
 }
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onDestroy(){live=null;unregisterReceiver(noisy);if(player!=null)player.release();audio.abandonAudioFocusRequest(focus);session.release();super.onDestroy();}
}
