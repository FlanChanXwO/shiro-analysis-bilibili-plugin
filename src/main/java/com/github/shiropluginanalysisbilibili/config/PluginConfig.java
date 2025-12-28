package com.github.shiropluginanalysisbilibili.config;


import org.springframework.core.env.Environment;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-06-16:36
 */
public class PluginConfig {

    /**
     * 插件是否启用
     */
    private Boolean enable = true;

    /**
     * B 站 Cookie，部分数据可能需要登录后才能获取
     */
    private String cookie = "";
    /**
     * 是否跳过视频信息分析总结
     */
    private Boolean skipVideoAnalysis = false;
    /**
     * 是否在分析结果中显示图片
     */
    private Boolean analysisDisplayImage = true;
    /**
     * 对于视频类型，是否发送视频资源
     */
    private Boolean analysisVideoSend = true;
    /**
     * 视频时长限制，单位秒，超过该时长的视频将不进行分析，0 或负数表示不限制
     */
    private Long durationSecLimit = 600L;
    /**
     * 临时文件存放路径
     */
    private String tmpPath = "data/bili_temp";
    /**
     * 图片尺寸
     */
    private String imagesSize = "";
    /**
     * 封面图片尺寸
     */
    private String coverImagesSize = "";
    /**
     * 重新分析时间间隔，单位秒
     */
    private Long reanalysisTimeSeconds = 3L;

    @Override
    public String toString() {
        return "PluginConfig{" +
                "cookie='" + cookie + '\'' +
                ", skipVideoAnalysis=" + skipVideoAnalysis +
                ", analysisDisplayImage=" + analysisDisplayImage +
                ", analysisVideoSend=" + analysisVideoSend +
                ", durationSecLimit=" + durationSecLimit +
                ", tmpPath='" + tmpPath + '\'' +
                ", imagesSize='" + imagesSize + '\'' +
                ", coverImagesSize='" + coverImagesSize + '\'' +
                ", reanalysisTimeSeconds=" + reanalysisTimeSeconds +
                '}';
    }
    
    public static PluginConfig getFromEnv(Environment env, String propertiesPrefix) {
       PluginConfig pluginConfig = new PluginConfig();

    // 2. 逐个读取配置并设置到 pluginConfig 对象中
    // 注意：propertiesPrefix 应该等于 "analysis-bilibili"
    // 如果 propertiesPrefix 变量不可用，可以直接使用字符串 "analysis-bilibili"
    
    // 读取 enable (布尔值)
        pluginConfig.setEnable(env.getProperty(
                propertiesPrefix + ".enable",          // 属性键: "analysis-bilibili.enable"
                Boolean.class,                    // 期望类型
                pluginConfig.getEnable()          // 如果未配置，使用对象中的默认值 true
        ));

// 读取 cookie (字符串)
        pluginConfig.setCookie(env.getProperty(
                propertiesPrefix + ".cookie",
                String.class,
                pluginConfig.getCookie()          // 默认值 ""
        ));

// 读取 skipVideoAnalysis (布尔值)
        pluginConfig.setSkipVideoAnalysis(env.getProperty(
                propertiesPrefix + ".skipVideoAnalysis",
                Boolean.class,
                pluginConfig.getSkipVideoAnalysis() // 默认值 false
        ));

// 读取 analysisDisplayImage (布尔值)
        pluginConfig.setAnalysisDisplayImage(env.getProperty(
                propertiesPrefix + ".analysisDisplayImage",
                Boolean.class,
                pluginConfig.getAnalysisDisplayImage() // 默认值 true
        ));

// 读取 reanalysisTimeSeconds (长整型)
        pluginConfig.setReanalysisTimeSeconds(env.getProperty(
                propertiesPrefix + ".reanalysisTimeSeconds",
                Long.class,
                pluginConfig.getReanalysisTimeSeconds() // 默认值 3L
        ));

// 其他在 PluginConfig 中但 YAML 里可能没有的配置项，也用同样的方式处理
// 这样可以确保所有字段都被正确初始化

        pluginConfig.setAnalysisVideoSend(env.getProperty(
                propertiesPrefix + ".analysisVideoSend",
                Boolean.class,
                pluginConfig.getAnalysisVideoSend() // 默认值 true
        ));

        pluginConfig.setDurationSecLimit(env.getProperty(
                propertiesPrefix + ".durationSecLimit",
                Long.class,
                pluginConfig.getDurationSecLimit() // 默认值 600L
        ));
        return pluginConfig;
    }

    public Boolean getEnable() {
        return enable;
    }

    public void setEnable(Boolean enable) {
        this.enable = enable;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public Boolean getSkipVideoAnalysis() {
        return skipVideoAnalysis;
    }

    public void setSkipVideoAnalysis(Boolean skipVideoAnalysis) {
        this.skipVideoAnalysis = skipVideoAnalysis;
    }

    public Boolean getAnalysisDisplayImage() {
        return analysisDisplayImage;
    }

    public void setAnalysisDisplayImage(Boolean analysisDisplayImage) {
        this.analysisDisplayImage = analysisDisplayImage;
    }

    public Boolean getAnalysisVideoSend() {
        return analysisVideoSend;
    }

    public void setAnalysisVideoSend(Boolean analysisVideoSend) {
        this.analysisVideoSend = analysisVideoSend;
    }

    public Long getDurationSecLimit() {
        return durationSecLimit;
    }

    public void setDurationSecLimit(Long durationSecLimit) {
        this.durationSecLimit = durationSecLimit;
    }

    public String getTmpPath() {
        return tmpPath;
    }

    public void setTmpPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }

    public String getImagesSize() {
        return imagesSize;
    }

    public void setImagesSize(String imagesSize) {
        this.imagesSize = imagesSize;
    }

    public String getCoverImagesSize() {
        return coverImagesSize;
    }

    public void setCoverImagesSize(String coverImagesSize) {
        this.coverImagesSize = coverImagesSize;
    }

    public Long getReanalysisTimeSeconds() {
        return reanalysisTimeSeconds;
    }

    public void setReanalysisTimeSeconds(Long reanalysisTimeSeconds) {
        this.reanalysisTimeSeconds = reanalysisTimeSeconds;
    }
}