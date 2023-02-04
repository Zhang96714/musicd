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

    private static final String urlPre = "https://music.163.com/song/media/outer/url?id=";
    Map<String, String> errMap = new HashMap<>();
    static Map<String, String> musicNameMap = new HashMap<>();

    private static final String SAVE_DIR_NAME="\\Music\\musicd";

    public void batchDownload() {
        if (musicNameMap.isEmpty()) {
            failThrows("未指定 音乐id，无法进行下载。", null);
        }
        File dir = checkDirExist();

        for (String musicId : musicNameMap.keySet()) {

            String u = urlPre + musicId;
            doDownloadV1(u, dir, musicId);
        }

        printErrs();
    }

    private String genUniqueName(String file, String musicId) {
        String originName = file;
        if (file.contains("/")) {
            originName = file.substring(file.lastIndexOf("/"));
        }
        String musicName = musicNameMap.get(musicId);
        String ext=originName.substring(originName.lastIndexOf("."));
        return musicName +ext;
    }

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    /**
     * 使用 httpGet
     */
    private void doDownloadV1(String url, File dir, String musicId) {
        System.out.println("url: " + url);

        RequestConfig requestConfig = RequestConfig
                .custom()
                .setRedirectsEnabled(true)//默认 true
                .build();

        HttpGet httpGet = new HttpGet();
        httpGet.setConfig(requestConfig);
        try {
            httpGet.setURI(new URI(url));
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
                    String uniqueName = genUniqueName(pairOptional.get().getValue(), musicId);
                    saveToDisk(is, uniqueName, dir);

                    //close is
                    try {
                        is.close();
                    } catch (IOException ignore) {
                        //ignore
                    }
                }
            }

        } catch (IOException e) {
            failRecord(musicId, e);
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

    private void saveToDisk(InputStream inputStream, String uniqueId, File dir) {
        File tmpFile = new File(dir, uniqueId);
        if (tmpFile.exists()) {
            //避免重复下载
            return;
        }

        //close os
        try (OutputStream outputStream = Files.newOutputStream(tmpFile.toPath())) {
            copy(inputStream, outputStream);
//            StreamUtils.copy(inputStream,outputStream);
        } catch (IOException ioException) {
            String s = tmpFile.getAbsolutePath();
            failThrows("保存文件失败 " + s, ioException);
        }
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

    public static void prepare() {
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

    public static void prepareMusicUrlData(String text) {
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
            int i = 1;
            for (String line : lines
            ) {
                String[] data = line.split("@");
                if (data.length != 2) {
                    System.err.println("line num:" + i + " 数据不正常。(请先检查)");
                    continue;
                }
                String urlString = data[0];
                String musicName = data[1];

                //null key allow
                musicNameMap.put(getQueryValue(urlString), musicName);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        new MusicDownloader().batchDownload();
    }
}
