package com.gitub.shiroanalysisbilibiliplugin.utils;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-3:26
 */
public final class FileUtil {

    private FileUtil() {}

    public static String getFileUrlPrefix() {
        return System.getProperty("os.name").toLowerCase().contains("linux") ? "file://" : "file:///";
    }
}