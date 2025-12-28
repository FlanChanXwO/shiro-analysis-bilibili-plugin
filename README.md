![License](https://img.shields.io/badge/license-MIT-blue)
![Spring Boot Version](https://img.shields.io/badge/SpringBootVersion-SpringBoot3-brightgreen)
![Java Version](https://img.shields.io/badge/JavaVersion-17+-orange)
![Shiro Framework](https://img.shields.io/badge/基于-Shiro框架-green)
# AnalysisBilibiliPlugin 配置说明

`AnalysisBilibiliPlugin` 是一个基于 Spring Boot 3 和 Shiro 针对 B 站内容的插件，支持视频、动态、专栏和直播分析。  
插件采用 **即放即用** 机制：只需将插件 jar 放入插件目录，Bot 会自动加载，无需导入依赖或配置 Spring。

## 如何使用
下载 jar 包并放入插件扫描路径中\
详细参考文档 https://misakatat.github.io/shiro-docs/#%E5%8A%A0%E8%BD%BD%E5%A4%96%E9%83%A8%E6%8F%92%E4%BB%B6

## 注意事项
视频下载功能需要安装 `ffmpeg`，请确保 `ffmpeg` 可执行文件在系统 PATH 中。
如果你是windows用户，你可以通过`winget`工具进行`winget install ffmpeg`来安装 `ffmpeg`。
linux用户可以通过包管理器安装，例如 `apt-get install ffmpeg` 或 `yum install ffmpeg`。
## 效果展示
### 视频分析
![assets/1.png](assets/1.png)
### 专栏分析
![assets/2.png](assets/2.png)
### 番剧分析
![assets/3.png](assets/3.png)
### 动态分析
![assets/5.png](assets/5.png)
### 直播间分析
![assets/6.png](assets/6.png)
### 短链识别
![assets/4.png](assets/4.png)


## 配置说明
在你的Spring应用配置文件（如 `application.yml`）中添加以下配置：
### 示例内容：
```yaml
analysis-bilibili:
    # [必填] 插件总开关，true|on 为启用，false|off 为禁用
    enable: true
    # [建议配置] 您的 Bilibili 账户 Cookie。部分视频或动态可能需要登录才能获取完整信息
    cookie: "SESSDATA=xxxxxxxx; bili_jct=xxxxxxxx; DedeUserID=xxxxxxx; ..."
    # [可选] 是否在解析结果中展示视频/动态的封面或图片
    analysisDisplayImage: true
    # [可选] 是否跳过对视频内容的 AI 分析总结（仅解析基本信息）
    skipVideoAnalysis: false
    # [可选] 对于视频链接，是否尝试发送视频文件本身（如果找到合适的资源）
    analysisVideoSend: true
    # [可选] 视频时长限制（单位：秒）。超过此长度的视频将不会被下载和发送。0 或负数表示不限制
    durationSecLimit: 600
    # [可选] 解析结果的缓存时间（单位：秒）。在缓存时间内重复发送相同链接，将直接返回上次的解析结果
    reanalysisTimeSeconds: 3
    # [可选] 临时文件（如视频、图片）的存放路径
    tmpPath: "data/bili_temp"
    # [可选] (高级) 指定动态中图片的尺寸，格式如 "800w_450h_1c"
    imagesSize: ""
    # [可选] (高级) 指定视频封面的尺寸
    coverImagesSize: ""

```

### 可用属性
| 属性名                 | 类型      | 默认值            | 说明                                   |
|-----------------------|---------|------------------|--------------------------------------|
| enable               | Boolean | true             | 插件是否启用                          |
| cookie               | String  | ""               | B 站 Cookie，用于获取需要登录的数据     |
| skipVideoAnalysis    | Boolean | false            | 是否跳过视频信息分析总结               |
| analysisDisplayImage | Boolean | true             | 是否在分析结果中显示图片               |
| analysisVideoSend    | Boolean | true             | 对于视频类型，是否发送视频资源         |
| durationSecLimit     | Long    | 600              | 视频时长限制（秒），0 或负数表示不限制  |
| tmpPath              | String  | data/bili_temp   | 临时文件存放路径                       |
| imagesSize           | String  | ""               | 图片尺寸                               |
| coverImagesSize      | String  | ""               | 封面图片尺寸                           |
| reanalysisTimeSeconds| Long    | 3                | 重新分析时间间隔                       |

## 许可证
本项目采用 MIT 许可证，详情请参阅 [LICENSE](LICENSE) 文件。

## 贡献与反馈
欢迎任何形式的贡献与反馈！
如果你发现问题或有功能建议，请提交 Issue
如有需求可提交 Pull Request 来改进插件功能或文档
