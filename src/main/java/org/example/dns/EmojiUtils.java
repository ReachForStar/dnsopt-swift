package org.example.dns;

import com.vdurmont.emoji.EmojiParser;
import javax.swing.*;
import java.awt.*;

/**
 * Emoji处理工具类 - 使用emoji-java库
 * 注意：本项目中不再直接显示任何 emoji 字符，本工具仅保留解析功能以兼容历史代码。
 */
public class EmojiUtils {

    /**
     * 将emoji别名转换为Unicode字符
     * 例如: ":globe_with_meridians:" -> 相应的Unicode字符
     */
    public static String parseToUnicode(String text) {
        return EmojiParser.parseToUnicode(text);
    }

    /**
     * 将Unicode emoji转换为别名
     * 例如: 某Unicode字符 -> ":globe_with_meridians:"
     */
    public static String parseToAliases(String text) {
        return EmojiParser.parseToAliases(text);
    }

    /**
     * 移除文本中的所有emoji
     */
    public static String removeAllEmojis(String text) {
        return EmojiParser.removeAllEmojis(text);
    }

    /**
     * 检查文本是否包含emoji
     */
    public static boolean containsEmoji(String text) {
        return !EmojiParser.extractEmojis(text).isEmpty();
    }

    /**
     * 创建支持emoji显示的JLabel（不包含任何硬编码emoji字符）
     */
    public static JLabel createEmojiLabel(String text) {
        String parsedText = parseToUnicode(text);
        JLabel label = new JLabel(parsedText);

        // 设置支持emoji的字体
        Font emojiFont = getEmojiFont(label.getFont().getSize());
        if (emojiFont != null) {
            label.setFont(emojiFont);
        }

        return label;
    }

    /**
     * 创建支持emoji显示的JButton（不包含任何硬编码emoji字符）
     */
    public static JButton createEmojiButton(String text) {
        String parsedText = parseToUnicode(text);
        JButton button = new JButton(parsedText);

        // 设置支持emoji的字体
        Font emojiFont = getEmojiFont(button.getFont().getSize());
        if (emojiFont != null) {
            button.setFont(emojiFont);
        }

        return button;
    }

    /**
     * 为现有组件设置emoji支持
     */
    public static void enableEmojiSupport(JComponent component) {
        Font emojiFont = getEmojiFont(component.getFont().getSize());
        if (emojiFont != null) {
            component.setFont(emojiFont);
        }

        // 如果是文本组件，解析emoji
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            String text = label.getText();
            if (text != null) {
                label.setText(parseToUnicode(text));
            }
        } else if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            String text = button.getText();
            if (text != null) {
                button.setText(parseToUnicode(text));
            }
        }
    }

    /**
     * 获取支持emoji的字体
     */
    private static Font getEmojiFont(int size) {
        // 尝试加载NotoEmoji字体
        try {
            java.io.InputStream fontStream = EmojiUtils.class.getResourceAsStream("/font/NotoEmoji.ttf");
            if (fontStream != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                fontStream.close();

                Font emojiFont = baseFont.deriveFont(Font.PLAIN, (float) size);

                // 注册字体
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(emojiFont);

                return emojiFont;
            }
        } catch (Exception e) {
            System.err.println("加载NotoEmoji字体失败: " + e.getMessage());
        }

        // 回退到系统emoji字体
        String[] systemEmojiFonts = {
            "Segoe UI Emoji",    // Windows
            "Apple Color Emoji", // macOS
            "Noto Color Emoji",  // Linux
            "Symbola"            // 通用
        };

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();

        for (String emojiFont : systemEmojiFonts) {
            for (String availableFont : availableFonts) {
                if (availableFont.equalsIgnoreCase(emojiFont)) {
                    return new Font(emojiFont, Font.PLAIN, size);
                }
            }
        }

        return null;
    }

    /**
     * 常用emoji别名常量（不含任何Unicode表情字符）
     */
    public static class Emojis {
        public static final String GLOBE = ":globe_with_meridians:";
        public static final String ROCKET = ":rocket:";
        public static final String CHECK_MARK = ":white_check_mark:";
        public static final String TARGET = ":dart:";
        public static final String RECYCLE = ":arrows_counterclockwise:";
        public static final String TRASH = ":wastebasket:";
        public static final String GEAR = ":gear:";
        public static final String LINK = ":link:";
        public static final String GREEN_CIRCLE = ":green_circle:";
        public static final String RED_CIRCLE = ":red_circle:";
        public static final String CROSS_MARK = ":x:";
        public static final String CLIPBOARD = ":clipboard:";
        public static final String FOLDER = ":file_folder:";
        public static final String FLOPPY_DISK = ":floppy_disk:";
        public static final String CHART = ":bar_chart:";
        public static final String ZAP = ":zap:";
        public static final String WARNING = ":warning:";

        // 为避免在源码中出现任何emoji字符，本类不再提供 *_UNICODE 常量。
    }
}
