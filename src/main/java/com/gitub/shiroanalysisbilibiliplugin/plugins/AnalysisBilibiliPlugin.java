package com.gitub.shiroanalysisbilibiliplugin.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.gitub.shiroanalysisbilibiliplugin.cache.ExpiringCache;
import com.gitub.shiroanalysisbilibiliplugin.config.PluginConfig;
import com.gitub.shiroanalysisbilibiliplugin.utils.BiliUtils;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.sisu.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
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

    private static final PluginConfig pluginConfig = PluginConfig.getInstance();

    // 短链接正则
    private static final String shortLinkRegex = "(https?://\\S+)";
    // 触发正则
    private static final String REGEX =
            "^(?:(?:av|cv)\\d+|BV[a-zA-Z0-9]{10})|(?:b23\\.tv|bili(?:22|23|33|2233)\\.cn|\\.bilibili\\.com|QQ小程序(?:&amp;#93;|&#93;|\\])哔哩哔哩).{0,500}";

    private final Map<Long, ExpiringCache> analysisStat = new ConcurrentHashMap<>();

    public AnalysisBilibiliPlugin() {
        super();
        logger.info("AnalysisBilibiliPlugin 配置信息: {}", pluginConfig.toString());
        logger.info("AnalysisBilibiliPlugin 加载完成");
    }

    @Override
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "http")
    public int onGroupMessage(Bot bot, GroupMessageEvent event) {
        // 插件已禁用，不进行任何行为
        if (!pluginConfig.isEnable()) {
            return 0;
        }

        try {
            String msgText = event.getMessage();
            // 内部进行正则匹配，严谨
            Pattern patternTrigger = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcherTrigger = patternTrigger.matcher(msgText);
            if (!matcherTrigger.find()) {
                // 不匹配触发正则，跳过
                return 0;
            }
            logger.info("AnalysisBilibiliPlugin 触发: {}", msgText);
            // 先尝试处理短链接 b23
            if (msgText.toLowerCase().contains("b23.tv") || msgText.toLowerCase().contains("bili23.cn")) {
                // 取出短链
                Pattern pattern = Pattern.compile(shortLinkRegex);
                Matcher matcher = pattern.matcher(msgText);
                if (matcher.find()) {
                    String shortLink = matcher.group(1);
                    // 试 expand（包装在 try 中）
                    try {
                        String expanded = BiliUtils.expandShortLink(shortLink);
                        if (expanded != null && !expanded.isEmpty()) {
                            msgText = msgText.replace(shortLink, expanded);
                        }
                    } catch (Exception e) {
                        logger.debug("短链接展开失败: {}", e.getMessage());
                    }
                } else {
                    logger.debug("未找到短链接，跳过展开");
                    return 0;
                }
            }

            String[] extracted = BiliUtils.extract(msgText);
            String type = extracted[0];
            String api = extracted[1];
            String cvid = extracted[2];
            logger.debug("解析结果 type={} api={}", type, api);
            if (type == null || api == null) {
                // 没有可解析的类型
                return 0;
            }

            long groupId = event.getGroupId();
            // 去重检查：如果已存在则跳过
            ExpiringCache cache = analysisStat.computeIfAbsent(groupId, k -> new ExpiringCache(pluginConfig.getReanalysisTimeSeconds()));
            String dedupKeyMarker = api; // 以调用的 api url 作为去重 key（足够唯一）
            if (cache.get(dedupKeyMarker)) {
                logger.debug("重复解析，忽略: group={} url={}", groupId, api);
                return 0;
            }
            cache.set(dedupKeyMarker);

            // 调用 API 并组织返回文本
            String sendText = parseAndFormat(type, api, cvid);
            if (sendText != null && !sendText.isEmpty()) {
                // 发送文本到群
                bot.sendGroupMsg(groupId, sendText, false);
            }

            // 如果是视频类型且配置允许，下载视频并发送
            if (pluginConfig.isAnalysisVideoSend() && "video".equals(type)) {
                File file = downloadVideo(api);
                if (file != null) {
                    logger.info("下载到视频，准备发送: {}", file.getAbsolutePath());
                    String videoMsg = MsgUtils.builder()
                            .video("file:///" + file.getAbsolutePath(), Strings.EMPTY)
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
        return 0;
    }

    private String parseAndFormat(String type, String apiUrl, String cvid) {
        try {
            JsonNode root = BiliUtils.httpGetJson(apiUrl);
//            logger.debug("AnalysisBilibiliPlugin 解析 json 内容: {}", root.toPrettyString());
//            logger.debug("AnalysisBilibiliPlugin 解析到的类型={} ", type);
            if (!pluginConfig.isSkipVideoAnalysis() && "video".equals(type)) {
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
                if (pluginConfig.isAnalysisDisplayImage()) {
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
                if (pluginConfig.isAnalysisDisplayImage()) {
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
                if (pluginConfig.isAnalysisDisplayImage()) {
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
                if (pluginConfig.isAnalysisDisplayImage()) {
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
                    if (pluginConfig.isAnalysisDisplayImage()) {
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
                    if (pluginConfig.isAnalysisDisplayImage()) {
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
            JsonNode root = BiliUtils.httpGetJson(apiUrl);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            long duration = data.get("duration").asLong(); // 秒
            long cid = data.get("cid").asLong();

            if (duration <= 600) {
                String bvid = data.get("bvid").asText();
                File video = BiliUtils.downloadBiliVideo(bvid, cid, duration);

                return video;
            }
            return null;
        } catch (Exception e) {
            logger.error("下载视频失败 apiUrl=" + apiUrl, e);
            return null;
        }
    }
}
