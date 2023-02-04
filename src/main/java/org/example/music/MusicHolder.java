package org.example.music;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MusicHolder {

    private final Map<String,MusicMeta> musicMetaMap=new HashMap<>();

    private static final int URL_INDEX=0;
    public static final int MUSIC_INDEX=1;
    public static final int ARTIST_INDEX=2;
    public static final int ALBUM_INDEX=3;

    public boolean isEmpty(){
        return musicMetaMap.isEmpty();
    }

    public void addMusic(String musicId,String[] strings) {
        for (int i = 0; i < 4; i++) {
            MusicMeta musicMeta = new MusicMeta(musicId);
            if(null != strings[URL_INDEX]){
                musicMeta.setShareLink(strings[URL_INDEX]);
            }
            if (null != strings[MUSIC_INDEX]) {
                musicMeta.setMusicName(strings[MUSIC_INDEX]);
            }
            if (null != strings[ARTIST_INDEX]) {
                musicMeta.setArtist(strings[ARTIST_INDEX]);
            }
            if (null != strings[ALBUM_INDEX]) {
                musicMeta.setAlbum(strings[ALBUM_INDEX]);
            }
            musicMetaMap.put(musicId,musicMeta);
        }
    }

    public void run(Consumer<MusicMeta> musicMetaConsumer){
        musicMetaMap.values().forEach(musicMetaConsumer);
    }
}
