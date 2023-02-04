package org.example.music;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class MusicDownloader {

    Map<String, String> errMap = new HashMap<>();

    static MusicHolder musicHolder=new MusicHolder();

    private static final String SAVE_DIR_NAME="\\Music\\musicd";

    /**
     * 批量下载
     */
    public void startBatchDownload() {
        if (musicHolder.isEmpty()) {
            failThrows("未指定 音乐id，无法进行下载。", null);
        }
        File dir = checkDirExist();
        musicHolder.run(musicMeta -> doDownload(musicMeta,dir));

        printErrs();
    }

    private String genUniqueName(String file, String musicId) {
        String originName = file;
        if (file.contains("/")) {
            originName = file.substring(file.lastIndexOf("/"));
        }
        String ext=originName.substring(originName.lastIndexOf("."));
        return musicId +ext;
    }

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    /**
     * 使用 httpGet
     */
    private void doDownload(MusicMeta musicMeta, File dir) {
        System.out.println("musicMeta:"+musicMeta);

        RequestConfig requestConfig = RequestConfig
                .custom()
                .setRedirectsEnabled(true)//默认 true
                .build();

        HttpGet httpGet = new HttpGet();
        httpGet.setConfig(requestConfig);
        try {
            httpGet.setURI(new URI(musicMeta.getDownloadUrl()));
        } catch (URISyntaxException e) {
            failThrows("url 格式错误", e);
        }

        //close response
        try (
                CloseableHttpResponse response = httpClient.execute(httpGet)
        ) {
            StatusLine statusLine = response.getStatusLine();
            System.out.println("status code:" + statusLine.getStatusCode());
            Header[] headers = response.getHeaders("Content-Disposition");
            Optional<Header> headerOptional = Stream.of(headers).findFirst();
            if (headerOptional.isPresent()) {
                Header header = headerOptional.get();
                Optional<NameValuePair> pairOptional = Optional.ofNullable(header.getElements()[0].getParameterByName("filename"));

                if (pairOptional.isPresent()) {
                    //do save operation
                    InputStream is = response.getEntity().getContent();
                    String uniqueName = genUniqueName(pairOptional.get().getValue(), musicMeta.getMusicId());
                    File souceFile=saveToDisk(is, uniqueName, dir);
                    if(null != souceFile){
                        //update info
                        MusicMeta.updateMp3File(souceFile,musicMeta);
                    }

                    //close is
                    try {
                        is.close();
                    } catch (IOException ignore) {
                        //ignore
                    }
                }
            }

        } catch (IOException e) {
            failRecord(musicMeta.getMusicId(), e);
        }


    }

    private File checkDirExist() {
        String saveDir=System.getProperty("user.home")+MusicDownloader.SAVE_DIR_NAME;
        File dir = new File(saveDir);
        if (!dir.exists() && !dir.mkdir()) {
            failThrows("无法创建 " + dir.getAbsolutePath(), null);
        }
        return dir;
    }

    private File saveToDisk(InputStream inputStream, String uniqueId, File dir) {
        File tmpFile = new File(dir, uniqueId);
        if (tmpFile.exists()) {
            //避免重复下载
            return null;
        }

        //close os
        try (OutputStream outputStream = Files.newOutputStream(tmpFile.toPath())) {
            copy(inputStream, outputStream);
//            StreamUtils.copy(inputStream,outputStream);
        } catch (IOException ioException) {
            String s = tmpFile.getAbsolutePath();
            failThrows("保存文件失败 " + s, ioException);
        }
        return tmpFile;
    }

    private void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffers = new byte[1024];
        int len;
        while ((len = is.read(buffers)) > 0) {
            os.write(buffers, 0, len);
        }
        os.flush();
    }

    private void failThrows(String message, Exception cause) {
        throw new MusicDownloadException(message, cause);
    }

    private void failRecord(String musicId, Exception e) {
        errMap.put(musicId, e.getMessage());
    }

    private void printErrs() {
        if (errMap.isEmpty()) {
            return;
        }

        //print errs
        for (Map.Entry<String, String> entry : errMap.entrySet()) {
            System.err.println("musicId:" + entry.getKey() + " url:" + entry.getValue());
        }
    }

    private static class MusicDownloadException extends RuntimeException {

        public MusicDownloadException(String message, Exception cause) {
            super(message, cause);
        }
    }

    /**
     * 准备操作
     */
    private static void prepare() {
        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");

        String[] locations = {
                userHome + "\\desktop",
                userDir
        };
        for (String location : locations
        ) {
            prepareMusicUrlData(location);
        }
    }

    private static void prepareMusicUrlData(String text) {
        String configFileName = "music";
        text = text + "\\" + configFileName;
        File file = new File(text);
        if (!file.exists()) {
            file = new File(text + ".txt");
        }
        if (!file.exists()) {
            return;
        }

        Path filePath = Paths.get(file.toURI());
        try {
            List<String> lines = Files.readAllLines(filePath);
            boolean find=false;
            //length=10 避免下标越界
            String[] strings=new String[10];
            int lineCount=0;
            for (String line : lines) {
                if(!find && "".equals(line)){
                    //跳过空行
                    continue;
                }
                if(!find && line.startsWith("https")){
                    find=true;
                    lineCount++;
                    strings[0]=line;
                    continue;
                }
                if(find && "".equals(line)){
                    //找到一组信息
                    String musicId=getQueryValue(strings[0]);
                    musicHolder.addMusic(musicId,strings);

                    find=false;
                    resetArray(strings);
                }else{
                    lineCount++;
                    appendToArray(strings,line);
                    boolean isLastLine=lines.size() == lineCount;
                    if(isLastLine){
                        String musicId=getQueryValue(strings[0]);
                        musicHolder.addMusic(musicId,strings);
                    }
                }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void resetArray(String[] strings){
        Arrays.fill(strings, null);
    }

    private static void appendToArray(String[] strings,String s){
        for (int i = 0; i < strings.length; i++) {
            if( null == strings[i]){
                strings[i]=s;
                break;
            }
        }
    }

    private static String getQueryValue(String url) {
        String queryKey="id";
        if (!url.contains("?")) {
            return null;
        }
        String[] query = url.substring(url.indexOf("?") + 1).split("&");
        Map<String, String> map = new HashMap<>();
        for (String nameValue : query
        ) {
            if (nameValue.contains("=")) {
                String[] strings = nameValue.split("=");
                if (strings.length == 2 && queryKey.equals(strings[0])) {
                    map.put(strings[0], strings[1]);
                }
            }
        }
        return map.get(queryKey);
    }

    public static void main(String[] args) {
        prepare();
        //开始下载
        new MusicDownloader().startBatchDownload();
    }
}
