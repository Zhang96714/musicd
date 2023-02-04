package org.example.music;

import com.mpatric.mp3agic.*;

import java.io.File;

public class MusicMeta {

    private final String musicId;
    private static final String url_pre="https://music.163.com/song/media/outer/url?id=";
    private final String downloadUrl;

    private String shareLink="";
    private String musicName="";
    private String album="";
    private String artist="";

    public MusicMeta(String musicId) {
        this.musicId = musicId;
        this.downloadUrl=url_pre+musicId;
    }

    public String getShareLink() {
        return shareLink;
    }

    public void setShareLink(String shareLink) {
        this.shareLink = shareLink;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getMusicName() {
        return musicName;
    }

    public void setMusicName(String musicName) {
        this.musicName = musicName;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getMusicId() {
        return musicId;
    }

    public static void updateMp3File(File source,MusicMeta musicMeta){
        try{
            Mp3File mp3File=new Mp3File(source);
            ID3v2 id3v2=mp3File.getId3v2Tag();
            id3v2.setAlbum(musicMeta.getAlbum());
            id3v2.setArtist(musicMeta.getArtist());

            String newFile=source.getAbsolutePath().replace(musicMeta.getMusicId(), musicMeta.getMusicName());
            mp3File.save(newFile);

            //delete source
            boolean delete =source.delete();
            if(!delete){
                System.out.println("delete "+source.getName()+" fail.");
            }
        }catch (Exception ignore){
        }
    }

    @Override
    public String toString() {
        return "MusicMeta{" +
                "musicId='" + musicId + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", musicName='" + musicName + '\'' +
                ", album='" + album + '\'' +
                ", artist='" + artist + '\'' +
                ", shareLink='"+shareLink+'\''+
                '}';
    }
}
