package com.github.shiropluginanalysisbilibili.utils;

import com.github.shiropluginanalysisbilibili.dto.MessageSegment;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:02
 */
public final class BotUtils {
    private static final Logger logger = LoggerFactory.getLogger(BotUtils.class);

    private BotUtils() {}


    public static String getPlainTextMessage(String message) {
        // 移除消息中的CQ码
        return message.replaceAll("\\[CQ:.*?\\]", "").trim();
    }

    private static final Gson gson = new GsonBuilder()
            .disableHtmlEscaping().create();

    private static final Pattern cqCodePattern = Pattern.compile("\\[CQ:([a-zA-Z0-9_.-]+)((,.*?)*?)\\]");

    /**
     * 使用 Gson 将包含CQ码的字符串解析为JSON。
     * - 如果解析结果只有一个消息段，则返回该消息段的JSON对象字符串。
     * - 如果解析结果有多个消息段，则返回包含这些消息段的JSON数组字符串。
     * - 如果输入为空或无法解析出任何段，则返回一个空数组 "[]"。
     *
     * @param message 输入的消息字符串.
     * @return 代表消息段的JSON对象或JSON数组字符串.
     */
    public static String parseCQtoJsonStr(String message) {
        // 如果输入是 null 或空字符串，直接返回空数组
        if (message == null || message.isEmpty()) {
            return "[]";
        }

        List<MessageSegment<?>> segments = new ArrayList<>();
        Matcher matcher = cqCodePattern.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            // 1. 添加在CQ码之前的文本段
            if (matcher.start() > lastEnd) {
                String text = message.substring(lastEnd, matcher.start());
                segments.add(new MessageSegment<>("text", new MessageSegment.TextData(text)));
            }

            // 2. 处理并添加CQ码段
            String cqType = matcher.group(1);
            String paramsStr = matcher.group(2); // 可能为 null

            MessageSegment.CQCodeData cqData = new MessageSegment.CQCodeData();
            if (paramsStr != null && !paramsStr.isEmpty()) {
                // 移除前导逗号并分割
                String[] params = paramsStr.substring(1).split(",");
                for (String param : params) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        // 这里对值进行简单的URL解码，因为CQ码中的特殊字符通常是编码的
                        // 例如, & -> &amp; , -> &#44; [ -> &#91; ] -> &#93;
                        // 实际的解码逻辑可能需要根据您的CQ码标准进行调整
                        String decodedValue = kv[1].replace("&amp;", "&")
                                .replace("&#44;", ",")
                                .replace("&#91;", "[")
                                .replace("&#93;", "]");
                        cqData.put(kv[0], decodedValue);
                    }
                }
            }
            segments.add(new MessageSegment<>(cqType, cqData));

            lastEnd = matcher.end();
        }

        // 3. 添加最后一个CQ码之后的任何尾随文本
        if (lastEnd < message.length()) {
            String text = message.substring(lastEnd);
            segments.add(new MessageSegment<>("text", new MessageSegment.TextData(text)));
        }

        // 4. 根据 segments 的大小决定如何序列化
        if (segments.size() == 1) {
            // 如果只有一个元素，直接序列化该对象
            return gson.toJson(segments.get(0));
        } else {
            // 如果有0个或多个元素，序列化整个列表为数组
            return gson.toJson(segments);
        }
    }


    /**
     * 将包含CQ码的字符串解析为 Gson 的 JsonObject 或 JsonArray。
     * - 如果解析结果只有一个消息段，则返回代表该段的 JsonObject。
     * - 如果解析结果有多个消息段，则返回包含这些段的 JsonArray。
     * - 如果输入为空或无法解析出任何段，则返回一个空的 JsonArray。
     *
     * @param message 输入的消息字符串.
     * @return 代表消息段的 JsonObject 或 JsonArray (以 JsonElement 形式)。
     */
    public static JsonElement parseCQToJson(String message) {
        // 如果输入是 null 或空字符串，直接返回空 JsonArray
        if (message == null || message.isEmpty()) {
            return new JsonArray();
        }

        List<MessageSegment<?>> segments = new ArrayList<>();
        Matcher matcher = cqCodePattern.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            // 1. 添加在CQ码之前的文本段
            if (matcher.start() > lastEnd) {
                String text = message.substring(lastEnd, matcher.start());
                segments.add(new MessageSegment<>("text", new MessageSegment.TextData(text)));
            }

            // 2. 处理并添加CQ码段
            String cqType = matcher.group(1);
            String paramsStr = matcher.group(2);

            MessageSegment.CQCodeData cqData = new MessageSegment.CQCodeData();
            if (paramsStr != null && !paramsStr.isEmpty()) {
                String[] params = paramsStr.substring(1).split(",");
                for (String param : params) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        // 简单的CQ码解码
                        String decodedValue = kv[1].replace("&amp;", "&")
                                .replace("&#44;", ",")
                                .replace("&#91;", "[")
                                .replace("&#93;", "]");
                        cqData.put(kv[0], decodedValue);
                    }
                }
            }
            segments.add(new MessageSegment<>(cqType, cqData));
            lastEnd = matcher.end();
        }

        // 3. 添加最后一个CQ码之后的任何尾随文本
        if (lastEnd < message.length()) {
            String text = message.substring(lastEnd);
            segments.add(new MessageSegment<>("text", new MessageSegment.TextData(text)));
        }

        // 4. 根据 segments 的大小决定返回 JsonObject 还是 JsonArray
        if (segments.size() == 1) {
            // 如果只有一个元素，将其转换为 JsonElement，然后转为 JsonObject
            return gson.toJsonTree(segments.get(0));
        } else {
            // 如果有0个或多个元素，将整个列表转换为 JsonArray
            return gson.toJsonTree(segments);
        }
    }

    public static JsonElement parseCQToJson(String message, boolean alwaysArray) {
        JsonElement element = parseCQToJson(message);
        if (alwaysArray && element.isJsonObject()) {
            JsonArray jsonArray = new JsonArray();
            jsonArray.add(element.getAsJsonObject());
            return jsonArray;
        }
        return element;
    }

    public static JsonArray parseCQToJsonArray(String message) {
        JsonElement element = parseCQToJson(message, true);
        return element.getAsJsonArray();
    }

    public static JsonObject parseCQToJsonObject(String message) {
        JsonElement element = parseCQToJson(message, false);
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        } else {
            return null;
        }
    }



    /**
     * 过滤JSON格式的消息字符串，提取纯文本信息。
     * 此方法现在作为入口，会根据JSON结构调用相应的重载方法。
     * @param jsonMessage 消息的JSON字符串表示.
     * @return 过滤后的文本字符串.
     */
    public static String getFilteredTextMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.trim().isEmpty()) {
            return "";
        }

        try {
            JsonElement rootElement = JsonParser.parseString(jsonMessage);

            if (rootElement.isJsonArray()) {
                // 如果是数组，调用处理JsonArray的方法
                return getFilteredTextMessage(rootElement.getAsJsonArray());
            } else if (rootElement.isJsonObject()) {
                // 如果是对象，调用处理JsonObject的方法
                return getFilteredTextMessage(rootElement.getAsJsonObject());
            } else {
                // 如果是JsonPrimitive (例如纯数字或"true") 或 JsonNull，则返回其字符串形式
                // 或者，如果您的业务逻辑认为这是一种无效输入，可以返回空字符串
                return rootElement.getAsString();
            }
        } catch (JsonSyntaxException e) {
            logger.error("JSON解析失败，返回原始消息。JSON: {}", jsonMessage, e);
            // 如果解析失败，说明它可能不是JSON，直接返回原始字符串
            return jsonMessage;
        }
    }



    /**
     * 从单个消息段的JsonObject中提取纯文本。
     * 示例: {"type":"text","data":{"text":"紫真是无底洞"}}
     * @param jsonMessage 代表单个消息段的JsonObject.
     * @return 过滤后的文本字符串.
     */
    public static String getFilteredTextMessage(JsonObject jsonMessage) {
        return processSingleSegment(jsonMessage).trim();
    }

    /**
     * 从消息段数组JsonArray中提取所有纯文本信息。
     * 示例: [{"type":"text","data":{"text":"紫真是无底洞"}}]
     * @param jsonMessage 代表消息段数组的JsonArray.
     * @return 拼接并过滤后的文本字符串.
     */
    public static String getFilteredTextMessage(JsonArray jsonMessage) {
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            return "";
        }
        StringBuilder plainText = new StringBuilder();
        for (JsonElement segmentElement : jsonMessage) {
            if (segmentElement.isJsonObject()) {
                plainText.append(processSingleSegment(segmentElement.getAsJsonObject()));
            }
        }
        // 对最终拼接的结果进行trim
        return plainText.toString().trim();
    }

    /**
     * 【私有辅助方法】处理单个消息段JsonObject，并返回其文本表示。
     * @param segment 代表单个消息段的JsonObject.
     * @return 过滤后的文本字符串.
     */
    private static String processSingleSegment(JsonObject segment) {
        if (segment == null || !segment.isJsonObject()) {
            return "";
        }

        String type = segment.has("type") ? segment.get("type").getAsString() : "";
        JsonObject data = segment.has("data") && segment.get("data").isJsonObject()
                ? segment.get("data").getAsJsonObject()
                : null;

        if (data == null) {
            return "";
        }

        switch (type) {
            case "text":
                return data.has("text") ? data.get("text").getAsString() : "";
            case "at":
                if (data.has("qq")) {
                    return "@" + data.get("qq").getAsString() + " ";
                }
                break;
            case "image":
                return "[图片]";
            case "face":
                return "[表情]";
            default:
                break;
        }
        return "";
    }


}