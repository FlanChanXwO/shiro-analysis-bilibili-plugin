package com.github.shiropluginanalysisbilibili.plugins;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.shiropluginanalysisbilibili.utils.BotUtils;
import com.github.shiropluginanalysisbilibili.utils.FileUtil;
import com.github.shiropluginanalysisbilibili.cache.ExpiringCache;
import com.github.shiropluginanalysisbilibili.config.PluginConfig;
import com.github.shiropluginanalysisbilibili.utils.BiliUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shiro 插件：自动解析消息中的 B 站链接并以纯文本形式发送解析结果。
 * 使用方式：把编译好的 jar 放到 Shiro 插件路径并启用。
 */
@Component
public class AnalysisBilibiliPlugin extends BotPlugin {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisBilibiliPlugin.class);

    private  final Gson gson = new Gson();
    // 触发正则
    private static final String REGEX =
            "^(?:(?:av|cv)\\d+|BV[a-zA-Z0-9]{10})|(?:b23\\.tv|bili(?:22|23|33|2233)\\.cn|\\.bilibili\\.com|QQ小程序(?:&amp;#93;|&#93;|\\])哔哩哔哩).{0,500}";

    private final Map<Long, ExpiringCache> analysisStat = new ConcurrentHashMap<>();

    private final PluginConfig pluginConfig;

    private final ObjectMapper mapper;

    private final OkHttpClient client;

    private final static String PLUGIN_NAME = "analysis-bilibili";

    public AnalysisBilibiliPlugin() {
        super();
        logger.info("{} 正在加载...", this.getClass().getSimpleName());
        Environment env = SpringUtil.getBean(Environment.class);
        ObjectMapper objectMapper = SpringUtil.getBean(ObjectMapper.class);
        mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        OkHttpClient httpClient = SpringUtil.getBean(OkHttpClient.class);
        client = httpClient != null ? httpClient : new OkHttpClient.Builder().build();
        pluginConfig = PluginConfig.getFromEnv(env, PLUGIN_NAME);
        logger.info("{} 配置信息: {}", pluginConfig, this.getClass().getSimpleName());
        logger.info("{}} 加载完成", this.getClass().getSimpleName());
    }


    @Override
    @MessageHandlerFilter(cmd = "http")
    public int onGroupMessage(Bot bot, GroupMessageEvent event) {
        // 插件已禁用，不进行任何行为
        if (!pluginConfig.getEnable()) {
            return MESSAGE_IGNORE;
        }

        try {
            String msgText = event.getMessage();
            String urlToParse = null; // 3. 定义一个变量，用于存放最终待解析的纯净URL

            // ========================= 核心改造区域 开始 =========================

            // 4. 优先判断是否为QQ小程序 (JSON CQ码)
            if (msgText.trim().startsWith("[CQ:json")) {
                logger.debug("检测到JSON CQ码，尝试作为QQ小程序进行解析...");

                // 使用你的工具类将CQ码字符串解析为JsonObject
                JsonObject cqJsonObj = BotUtils.parseCQToJsonObject(msgText);

                // 检查解析是否成功，并逐层深入获取qqdocurl
                if (cqJsonObj != null && cqJsonObj.has("data")) {
                    JsonObject dataObj = cqJsonObj.getAsJsonObject("data");

                    // QQ小程序的数据本身又是一个内嵌的JSON字符串，需要再次解析
                    if (dataObj.has("data")) {
                        String innerJsonStr = dataObj.get("data").getAsString();
                        try {
                            // 使用Gson解析内嵌的JSON字符串
                            JsonObject innerData = gson.fromJson(innerJsonStr, JsonObject.class);

                            // 安全地逐层获取 'qqdocurl'
                            JsonElement metaEl = innerData.get("meta");
                            if (metaEl != null && metaEl.isJsonObject()) {
                                JsonElement detailEl = metaEl.getAsJsonObject().get("detail_1");
                                if (detailEl != null && detailEl.isJsonObject()) {
                                    JsonElement urlEl = detailEl.getAsJsonObject().get("qqdocurl");
                                    if (urlEl != null && !urlEl.isJsonNull()) {
                                        urlToParse = urlEl.getAsString();
                                        logger.info("从QQ小程序中成功提取到URL: {}", urlToParse);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("解析QQ小程序内嵌JSON失败", e);
                        }
                    }
                }

                if (urlToParse == null) {
                    logger.warn("这是一个QQ小程序，但未能成功提取bilibili链接。");
                    return MESSAGE_IGNORE;
                }

            } else {
                // 5. 如果不是JSON CQ码，则执行原有的纯文本链接匹配逻辑
                Pattern patternTrigger = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher matcherTrigger = patternTrigger.matcher(msgText);
                if (matcherTrigger.find()) {
                    // 对于纯文本，直接将整个消息作为待解析内容
                    urlToParse = msgText;
                    logger.info("AnalysisBilibiliPlugin 触发 (纯文本): {}", msgText);
                } else {
                    // 不匹配任何触发规则，跳过
                    return MESSAGE_IGNORE;
                }
            }

            // ========================= 核心改造区域 结束 =========================

            // 6. 后续所有操作都基于我们提取出的纯净URL (urlToParse)

            // 先尝试处理短链接 b23
            if (urlToParse.toLowerCase().contains("b23.tv") || urlToParse.toLowerCase().contains("bili23.cn")) {
                // ... (短链展开逻辑，注意: 这里操作的对象是 urlToParse)
                try {
                    String expanded = expandShortLink(urlToParse); // 直接展开提取出的URL
                    if (expanded != null && !expanded.isEmpty()) {
                        urlToParse = expanded; // 更新为展开后的长链接
                    }
                } catch (Exception e) {
                    logger.debug("短链接展开失败: {}", e.getMessage());
                }
            }

            // 使用BiliUtils从纯净URL中提取信息
            String[] extracted = BiliUtils.extract(urlToParse);
            String type = extracted[0];
            String api = extracted[1];
            String cvid = extracted[2];
            logger.debug("解析结果 type={} api={}", type, api);
            if (type == null || api == null) {
                // 没有可解析的类型
                return MESSAGE_IGNORE;
            }

            // ... 后续的去重检查、API调用、发送消息逻辑保持不变 ...
            // (注意：确保这些逻辑使用解析后的 api, type 等变量，而不是原始的 msgText)

            long groupId = event.getGroupId();
            // 去重检查...
            ExpiringCache cache = analysisStat.computeIfAbsent(groupId, k -> new ExpiringCache(pluginConfig.getReanalysisTimeSeconds()));
            String dedupKeyMarker = api;
            if (cache.get(dedupKeyMarker)) {
                logger.debug("重复解析，忽略: group={} url={}", groupId, api);
                return MESSAGE_IGNORE;
            }
            cache.set(dedupKeyMarker);

            // 调用 API 并组织返回文本
            String sendText = parseAndFormat(type, api, cvid);
            if (sendText != null && !sendText.isEmpty()) {
                // 发送文本到群
                bot.sendGroupMsg(groupId, sendText, false);
            }

            // 如果是视频类型且配置允许，下载视频并发送
            if (pluginConfig.getAnalysisVideoSend() && "video".equals(type)) {
                File file = downloadVideo(api);
                if (file != null) {
                    logger.info("下载到视频，准备发送: {}", file.getAbsolutePath());
                    String videoMsg = MsgUtils.builder()
                            .video(FileUtil.getFileUrlPrefix() + file.getAbsolutePath(), Strings.EMPTY)
                            .build();
                    bot.sendGroupMsg(groupId, videoMsg, false);
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        if (!deleted) {
                            logger.warn("下载后删除临时视频文件失败: {}", file.getAbsolutePath());
                        }
                    }
                }
            }

        } catch (Exception ex) {
            logger.error("解析出错", ex);
        }
        return MESSAGE_IGNORE;
    }

    private String parseAndFormat(String type, String apiUrl, String cvid) {
        try {
            JsonNode root = httpGetJson(apiUrl);
           logger.debug("AnalysisBilibiliPlugin 解析 json 内容: {}", root.toPrettyString());
           logger.debug("AnalysisBilibiliPlugin 解析到的类型={} ", type);
            if (!pluginConfig.getSkipVideoAnalysis() && "video".equals(type)) {
                JsonNode data = root.path("data");
                if (data.isMissingNode() || data.isNull()) {
                    return "无法获取视频信息（可能已被删除或不可见）";
                }
                String title = data.path("title").asText("");
                long aid = data.path("aid").asLong(0L);
                String vurl = "https://www.bilibili.com/video/av" + aid;
                StringBuilder sb = new StringBuilder();
                sb.append("标题：").append(title).append("\n");
                sb.append("链接：").append(vurl).append("\n");
                JsonNode stat = data.path("stat");
                if (!stat.isMissingNode()) {
                    sb.append("播放：").append(BiliUtils.handleNum(stat.path("view").asLong(0L))).append(" | ");
                    sb.append("弹幕：").append(BiliUtils.handleNum(stat.path("danmaku").asLong(0L))).append(" | ");
                    sb.append("点赞：").append(BiliUtils.handleNum(stat.path("like").asLong(0L))).append("\n");
                }
                String desc = data.path("desc").asText("");
                if (desc != null && !desc.isEmpty()) {
                    String[] lines = desc.split("\n");
                    int limit = Math.min(lines.length, 3);
                    sb.append("简介：");
                    for (int i = 0; i < limit; i++) {
                        sb.append(lines[i]).append(" ");
                    }
                    if (lines.length > 3) {
                        sb.append("……");
                    }
                    sb.append("\n");
                }
                MsgUtils msg = MsgUtils.builder().text(sb.toString());
                if (pluginConfig.getAnalysisDisplayImage()) {
                    String picSrc = data.path("pic").asText("");
                    msg.img(BiliUtils.resizeImage(picSrc, pluginConfig.getImagesSize(), pluginConfig.getCoverImagesSize(), true));
                }
                return msg.build();
            } else if ("bangumi".equals(type)) {
                JsonNode res = root.path("result");
                if (res.isMissingNode()) {
                    return "无法获取番剧信息";
                }

                String title = res.path("title").asText("");
                String vurl = res.has("media_id") ? "https://www.bilibili.com/bangumi/media/md" + res.path("media_id").asText() : "https://www.bilibili.com/";
                StringBuilder sb = new StringBuilder();
                sb.append("番剧：").append(title).append("\n");
                sb.append("链接：").append(vurl).append("\n");
                String evaluate = res.path("evaluate").asText("");
                if (!evaluate.isEmpty()) {
                    sb.append("简介：").append(evaluate).append("\n");
                }
                MsgUtils msg = MsgUtils.builder().text(sb.toString());
                if (pluginConfig.getAnalysisDisplayImage()) {
                    String picSrc = res.path("cover").asText("");
                    msg.img(BiliUtils.resizeImage(picSrc, pluginConfig.getImagesSize(), pluginConfig.getCoverImagesSize(), true));
                }
                return msg.build();
            } else if ("live".equals(type)) {
                JsonNode data = root.path("data");
                if (data.isMissingNode()) {
                    return "无法获取直播间信息";
                }
                JsonNode room = data.path("room_info");
                String title = room.path("title").asText("");
                String roomId = room.path("room_id").asText("");
                String uname = data.path("anchor_info").path("base_info").path("uname").asText("");
                long online = room.path("online").asLong(0L);
                StringBuilder sb = new StringBuilder();
                sb.append("直播：").append(title).append("\n");
                sb.append("主播：").append(uname).append(" | ");
                sb.append("人气：").append(BiliUtils.handleNum(online)).append("\n");
                sb.append("链接：https://live.bilibili.com/").append(roomId).append("\n");
                MsgUtils msg = MsgUtils.builder().text(sb.toString());
                if (pluginConfig.getAnalysisDisplayImage()) {
                    String picSrc = room.path("cover").asText("");
                    msg.img(BiliUtils.resizeImage(picSrc, pluginConfig.getImagesSize(), pluginConfig.getCoverImagesSize(), true));
                }
                return msg.build();
            } else if ("article".equals(type)) {
                JsonNode data = root.path("data");
                if (data.isMissingNode()) {
                    return "无法获取专栏信息";
                }
                String title = data.path("title").asText("");
                String author = data.path("author_name").asText("");
                long view = data.path("stats").path("view").asLong(0L);
                StringBuilder sb = new StringBuilder();
                sb.append("标题：").append(title).append("\n");
                sb.append("作者：").append(author).append("\n");
                sb.append("阅读：").append(BiliUtils.handleNum(view)).append("\n");
                if (cvid != null) {
                    sb.append("链接：https://www.bilibili.com/read/cv").append(cvid).append("\n");
                }
                MsgUtils msg = MsgUtils.builder().text(sb.toString());
                if (pluginConfig.getAnalysisDisplayImage()) {
                    String picSrc = data.path("cover").asText("");
                    msg.img(BiliUtils.resizeImage(picSrc, pluginConfig.getImagesSize(), pluginConfig.getCoverImagesSize(), true));
                }
                return msg.build();
            } else if ("dynamic".equals(type)) {
                JsonNode data = root.path("data").path("item");
                if (data.isMissingNode()) {
                    JsonNode maybe = root.path("data");
                    if (!maybe.isMissingNode()) {
                        data = maybe;
                    }
                }
                if (data.isMissingNode()) {
                    return "无法获取动态信息";
                }

                MsgUtils msgBuilder = MsgUtils.builder();

                // 取 major
                JsonNode major = data.path("modules").path("module_dynamic").path("major");
                String majorType = major.path("type").asText("");

                // ==========================================================
                //              【1】图片动态  MAJOR_TYPE_DRAW
                // ==========================================================
                if ("MAJOR_TYPE_DRAW".equals(majorType)) {

                    JsonNode items = major.path("draw").path("items");
                    if (pluginConfig.getAnalysisDisplayImage()) {
                        for (JsonNode item : items) {
                            String src = item.path("src").asText("");
                            msgBuilder.img(BiliUtils.resizeImage(src, pluginConfig.getImagesSize(), pluginConfig.getCoverImagesSize(), true));
                        }
                    }

                    String dynamicId = data.path("id_str").asText("");
                    StringBuilder sb = new StringBuilder();
                    sb.append("动态\n");
                    sb.append("链接：https://t.bilibili.com/").append(dynamicId).append("\n");

                    msgBuilder.text(sb.toString());
                    return msgBuilder.build();
                }

                // ==========================================================
                //              【2】图文动态  MAJOR_TYPE_ARTICLE
                // ==========================================================
                else if ("MAJOR_TYPE_ARTICLE".equals(majorType)) {

                    JsonNode article = major.path("article");
                    String content = article.path("desc").asText("");
                    String title = article.path("title").asText("");
                    String label = article.path("label").asText("");

                    ArrayNode covers = article.withArray("covers");
                    if (pluginConfig.getAnalysisDisplayImage()) {
                        for (JsonNode cover : covers) {
                            String picSrc = cover.asText("");
                            msgBuilder.img(BiliUtils.resizeImage(picSrc, pluginConfig.getImagesSize(), pluginConfig.getCoverImagesSize(), true));
                        }
                    }

                    String dynamicId = data.path("id_str").asText("");
                    StringBuilder sb = new StringBuilder();
                    sb.append("标题：").append(title).append("\n");
                    sb.append("动态：").append(content).append("...").append("\n");
                    sb.append("链接：https://t.bilibili.com/").append(dynamicId).append("\n");
                    sb.append("阅读量：").append(label).append("\n");

                    msgBuilder.text(sb.toString());
                    return msgBuilder.build();
                }
                // ==========================================================
                //              其他未处理类型（保证不崩溃）
                // ==========================================================
                return "暂不支持解析该类型动态：" + majorType;
            }
        } catch (Exception e) {
            logger.error("解析 API 失败 url=" + apiUrl, e);
            return "bili 解析出错: " + e.getMessage();
        }
        return null;
    }


    private File downloadVideo(String apiUrl) {
        try {
            JsonNode root = httpGetJson(apiUrl);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            long duration = data.get("duration").asLong(); // 秒
            long cid = data.get("cid").asLong();

            if (duration <= 600) {
                String bvid = data.get("bvid").asText();
                File video = downloadBiliVideo(bvid, cid, duration);

                return video;
            }
            return null;
        } catch (Exception e) {
            logger.error("下载视频失败 apiUrl=" + apiUrl, e);
            return null;
        }
    }



    public JsonNode httpGetJson(String url) throws IOException {
        Request request = buildHttpRequest(url);
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP error " + resp.code() + " for " + url);
            }
            String body = resp.body().string();
            return mapper.readTree(body);
        }
    }

    private Request buildHttpRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; BiliAnalysisBot/1.0)")
                .header("Referer", "https://www.bilibili.com/") // 防盗链必加
                .header("Cookie", pluginConfig.getCookie())
                .build();
    }

    public String expandShortLink(String shortUrl) throws IOException {
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
     * 下载 B 站视频
     * @param bvid
     * @param cid
     * @param durationSec
     * @return
     * @throws Exception
     */
    public File downloadBiliVideo(String bvid, long cid, long durationSec) throws Exception {
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
    private void downloadResource(String url, File out) throws IOException {
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
    private void mergeAv(File video, File audio, File output) throws Exception {
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
}
