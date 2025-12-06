package com.gitub.shiroanalysisbilibiliplugin.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitub.shiroanalysisbilibiliplugin.config.PluginConfig;
import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提供：
 *  - extract 类似函数：从消息文本中推断出对应的 API URL 或视频/房间 id
 *  - httpGetJson(url)：同步请求并返回 JsonNode
 *  - expandShortLink：处理 b23.tv 短链（跟随重定向）
 */
public class BiliUtils {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private static final PluginConfig pluginConfig = PluginConfig.getInstance();

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Pattern bvidPattern = Pattern.compile("BV([A-Za-z0-9]{10})");
    private static final Pattern aidPattern = Pattern.compile("av(\\d+)");
    private static final Pattern epidPattern = Pattern.compile("ep(\\d+)");
    private static final Pattern ssidPattern = Pattern.compile("ss(\\d+)");
    private static final Pattern mdidPattern = Pattern.compile("md(\\d+)");
    private static final Pattern roomPattern = Pattern.compile("live.bilibili.com/(?:blanc/|h5/)?(\\d+)");
    private static final Pattern cvidPattern = Pattern.compile("(?:/read/(?:cv|mobile|native)(?:/|\\?id=)?|^cv)(\\d+)");
    private static final Pattern dynamic2Pattern = Pattern.compile(".bilibili.com/(\\d+)\\?.*type=2");
    private static final Pattern dynamicPattern = Pattern.compile(".bilibili.com/(?:opus/)?(\\d+)");


    public static JsonNode httpGetJson(String url) throws IOException {
        Request request = buildHttpRequest(url);
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP error " + resp.code() + " for " + url);
            }
            String body = resp.body().string();
            return mapper.readTree(body);
        }
    }

    private static Request buildHttpRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; BiliAnalysisBot/1.0)")
                .header("Referer", "https://www.bilibili.com/") // 防盗链必加
                .header("Cookie", pluginConfig.getCookie())
                .build();
    }

    public static String expandShortLink(String shortUrl) throws IOException {
        Request request = new Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; BiliAnalysisBot/1.0)")
                .build();
        try (Response resp = client.newCall(request).execute()) {
            // OkHttp 默认跟随重定向；最终 URL 可以从 resp.request().url()
            HttpUrl finalUrl = resp.request().url();
            return finalUrl.toString();
        }
    }




    /**
     * 根据输入文本判断并返回 API url 或 web api url 与 type 标识
     * 返回格式：String[] {type, apiUrl, originalIdOrNull}
     * type: "video","bangumi","live","article","dynamic", or null
     */
    public static String[] extract(String text) {
        try {
            Matcher m;
            m = bvidPattern.matcher(text);
            if (m.find()) {
                String bvid = m.group(0);
                String api = "https://api.bilibili.com/x/web-interface/view?bvid=" + URLEncoder.encode(bvid, StandardCharsets.UTF_8);
                return new String[]{"video", api, null};
            }
            m = aidPattern.matcher(text);
            if (m.find()) {
                String aid = m.group(1);
                String api = "https://api.bilibili.com/x/web-interface/view?aid=" + URLEncoder.encode(aid, StandardCharsets.UTF_8);
                return new String[]{"video", api, null};
            }
            m = epidPattern.matcher(text);
            if (m.find()) {
                String ep = m.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + ep;
                return new String[]{"bangumi", api, null};
            }
            m = ssidPattern.matcher(text);
            if (m.find()) {
                String ss = m.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?season_id=" + ss;
                return new String[]{"bangumi", api, null};
            }
            m = mdidPattern.matcher(text);
            if (m.find()) {
                String md = m.group(1);
                String api = "https://api.bilibili.com/pgc/review/user?media_id=" + md;
                return new String[]{"bangumi", api, null};
            }
            m = roomPattern.matcher(text);
            if (m.find()) {
                String room = m.group(1);
                String api = "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=" + room;
                return new String[]{"live", api, null};
            }
            m = cvidPattern.matcher(text);
            if (m.find()) {
                String cv = m.group(1);
                String api = "https://api.bilibili.com/x/article/viewinfo?id=" + cv + "&mobi_app=pc&from=web";
                return new String[]{"article", api, cv};
            }
            m = dynamic2Pattern.matcher(text);
            if (m.find()) {
                String rid = m.group(1);
                String api = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?rid=" + rid + "&type=2";
                return new String[]{"dynamic", api, null};
            }
            m = dynamicPattern.matcher(text);
            if (m.find()) {
                String id = m.group(1);
                String api = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=" + id;
                return new String[]{"dynamic", api, null};
            }
        } catch (Exception e) {
            // ignore and return null
        }
        return new String[]{null, null, null};
    }

    public static String resizeImage(String src, String imagesSize, String coverImagesSize, boolean isCover) {
        if (src == null || src.isEmpty()) {
            return src;
        }
        if (isCover && coverImagesSize != null && !coverImagesSize.isEmpty()) {
            String imgType = src.length() >= 3 ? src.substring(src.length() - 3) : "";
            return src + "@" + coverImagesSize + "." + imgType;
        }
        if (imagesSize != null && !imagesSize.isEmpty()) {
            String imgType = src.length() >= 3 ? src.substring(src.length() - 3) : "";
            return src + "@" + imagesSize + "." + imgType;
        }
        return src;
    }

    // helper to convert big numbers like Python handle_num
    public static String handleNum(long num) {
        if (num > 10000) {
            double v = (double) num / 10000.0;
            return String.format("%.2f万", v);
        }
        return String.valueOf(num);
    }


    /**
     * 下载 B 站视频
     * @param bvid
     * @param cid
     * @param durationSec
     * @return
     * @throws Exception
     */
    public static File downloadBiliVideo(String bvid, long cid, long durationSec) throws Exception {
        // 限时判断
        if (pluginConfig.getDurationSecLimit() > 0 && durationSec > pluginConfig.getDurationSecLimit()) {
            return null;
        }

        String url = "https://api.bilibili.com/x/player/playurl?bvid=" +
                bvid + "&cid=" + cid + "&qn=80&fnval=16";

        JsonNode root = httpGetJson(url).get("data");
        JsonNode dash = root.get("dash");

        String videoUrl = dash.get("video").get(0).get("baseUrl").asText();
        String audioUrl = dash.get("audio").get(0).get("baseUrl").asText();

        File tempDir = new File(pluginConfig.getTmpPath());
        tempDir.mkdirs();

        File videoFile = new File(tempDir, bvid + "_v.mp4");
        File audioFile = new File(tempDir, bvid + "_a.mp3");
        File outputFile = new File(tempDir, bvid + ".mp4");

        downloadResource(videoUrl, videoFile);
        downloadResource(audioUrl, audioFile);

        mergeAv(videoFile, audioFile, outputFile);

        return outputFile;
    }

    /**
     * 下载音视频资源
     * @param url
     * @param out
     * @throws IOException
     */
    private static void downloadResource(String url, File out) throws IOException {
        Request req =  buildHttpRequest(url);
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code());
            }
            try (var in = resp.body().byteStream();
                 var outStream = new FileOutputStream(out)) {
                in.transferTo(outStream);
            }
        }
    }


    /**
     * 使用 ffmpeg 合并音视频文件
     * @param video
     * @param audio
     * @param output
     * @throws Exception
     */
    private static void mergeAv(File video, File audio, File output) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", video.getAbsolutePath(),
                "-i", audio.getAbsolutePath(),
                "-c:v", "copy",
                "-c:a", "aac",
                output.getAbsolutePath()
        );
        pb.inheritIO(); // 直接把 ffmpeg 输出绑定到控制台
        Process process = pb.start();
        process.waitFor();
        // 合并完成后，删除单独的音频文件和视频文件
        video.deleteOnExit();
        audio.deleteOnExit();
    }

    public static void main(String[] args) throws IOException {
        String[] extract = BiliUtils.extract("https://www.bilibili.com/opus/1142849604453138434?spm_id_from=333.1387.0.0");

        System.out.println(Arrays.toString(extract));
    }

}
