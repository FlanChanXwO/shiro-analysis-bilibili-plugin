package com.gitub.shiroanalysisbilibiliplugin.config;


import java.io.InputStream;
import java.util.Properties;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-06-16:36
 */
public class PluginConfig {

    private static volatile PluginConfig instance;

    /**
     * 插件是否启用
     */
    private boolean enable = true;

    /**
     * B 站 Cookie，部分数据可能需要登录后才能获取
     */
    private String cookie = "";
    /**
     * 是否跳过视频信息分析总结
     */
    private boolean skipVideoAnalysis = false;
    /**
     * 是否在分析结果中显示图片
     */
    private boolean analysisDisplayImage = true;
    /**
     * 对于视频类型，是否发送视频资源
     */
    private boolean analysisVideoSend = true;
    /**
     * 视频时长限制，单位秒，超过该时长的视频将不进行分析，0 或负数表示不限制
     */
    private long durationSecLimit = 600;
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
    private long reanalysisTimeSeconds = 3L;

    // 私有构造，保证单例
    private PluginConfig() {
        try (InputStream in = getClass().getResourceAsStream("/analysis.plugin.properties")) {
            if (in != null) {
                Properties prop = new Properties();
                prop.load(in);
                this.cookie = prop.getProperty("cookie", cookie);
                this.enable = Boolean.parseBoolean(prop.getProperty("enable", String.valueOf(enable)));
                this.skipVideoAnalysis = Boolean.parseBoolean(prop.getProperty("skipVideoAnalysis", String.valueOf(skipVideoAnalysis)));
                this.analysisDisplayImage = Boolean.parseBoolean(prop.getProperty("analysisDisplayImage", String.valueOf(analysisDisplayImage)));
                this.analysisVideoSend = Boolean.parseBoolean(prop.getProperty("analysisVideoSend", String.valueOf(analysisVideoSend)));
                this.durationSecLimit = Long.parseLong(prop.getProperty("durationSecLimit", String.valueOf(durationSecLimit)));
                this.tmpPath = prop.getProperty("tmpPath", tmpPath);
                this.imagesSize = prop.getProperty("imagesSize", imagesSize);
                this.coverImagesSize = prop.getProperty("coverImagesSize", coverImagesSize);
                this.reanalysisTimeSeconds = Long.parseLong(prop.getProperty("reanalysisTimeSeconds", String.valueOf(reanalysisTimeSeconds)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 使用默认值
        }
    }

    // 获取单例实例（线程安全双重检查）
    public static PluginConfig getInstance() {
        if (instance == null) {
            synchronized (PluginConfig.class) {
                if (instance == null) {
                    instance = new PluginConfig();
                }
            }
        }
        return instance;
    }

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

    // --- getter ---
    public String getCookie() { return cookie; }
    public boolean isSkipVideoAnalysis() { return skipVideoAnalysis; }
    public boolean isAnalysisDisplayImage() { return analysisDisplayImage; }
    public boolean isAnalysisVideoSend() { return analysisVideoSend; }
    public long getDurationSecLimit() { return durationSecLimit; }
    public String getTmpPath() { return tmpPath; }
    public String getImagesSize() { return imagesSize; }
    public String getCoverImagesSize() { return coverImagesSize; }
    public long getReanalysisTimeSeconds() { return reanalysisTimeSeconds; }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    // 可选 setter，如果你希望在运行时修改配置
    public void setCookie(String cookie) { this.cookie = cookie; }
    public void setSkipVideoAnalysis(boolean skipVideoAnalysis) { this.skipVideoAnalysis = skipVideoAnalysis; }
    public void setAnalysisDisplayImage(boolean analysisDisplayImage) { this.analysisDisplayImage = analysisDisplayImage; }
    public void setAnalysisVideoSend(boolean analysisVideoSend) { this.analysisVideoSend = analysisVideoSend; }
    public void setDurationSecLimit(long durationSecLimit) { this.durationSecLimit = durationSecLimit; }
    public void setTmpPath(String tmpPath) { this.tmpPath = tmpPath; }
    public void setImagesSize(String imagesSize) { this.imagesSize = imagesSize; }
    public void setCoverImagesSize(String coverImagesSize) { this.coverImagesSize = coverImagesSize; }
    public void setReanalysisTimeSeconds(long reanalysisTimeSeconds) { this.reanalysisTimeSeconds = reanalysisTimeSeconds; }
}