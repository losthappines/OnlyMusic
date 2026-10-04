package app.onlymusic.player;
import org.json.*;

public class Track {
 public final String id, uri, title, artist, album;
 public final long duration;
 public Track(String id,String uri,String title,String artist,String album,long duration){
  this.id=id;this.uri=uri;this.title=title;this.artist=artist;this.album=album;this.duration=duration;
 }
 public JSONObject json(){
  JSONObject j=new JSONObject();
  try{j.put("id",id).put("uri",uri).put("title",title).put("artist",artist).put("album",album).put("duration",duration);}catch(JSONException ignored){}
  return j;
 }
 public static Track from(JSONObject j)throws JSONException{
  return new Track(j.getString("id"),j.getString("uri"),j.getString("title"),j.optString("artist","Unknown artist"),j.optString("album","Local files"),j.optLong("duration"));
 }
}
