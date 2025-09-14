package org.example.dns;

import javax.swing.*;
import java.awt.*;

/**
 * 支持Emoji显示的标签组件 - 使用NotoEmoji.ttf字体
 */
public class EmojiLabel extends JLabel {
    private String emojiText;
    private Font notoEmojiFont;
    private Font mainFont;

    public EmojiLabel(String text) {
        super();
        this.emojiText = text;
        initializeEmojiFonts();
        setText(text);
    }

    private void initializeEmojiFonts() {
        // 加载NotoEmoji字体
        notoEmojiFont = loadNotoEmojiFont();

        // 获取主字体
        mainFont = getFont();
        if (mainFont == null) {
            mainFont = loadSourceHanSansFont();
        }
    }

    /**
     * 优先从资源加载 SourceHanSansSC-Regular-2.otf，失败则回退到系统已安装字体族名
     */
    private Font loadSourceHanSansFont() {
        try {
            java.io.InputStream fontStream = getClass().getResourceAsStream("/font/SourceHanSansSC-Regular-2.otf");
            if (fontStream != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                fontStream.close();
                Font font = baseFont.deriveFont(Font.PLAIN, 12f);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(baseFont);
                return font;
            }
        } catch (Exception ignore) {
        }
        // 回退到系统字体族名
        try {
            return new Font("Source Han Sans SC", Font.PLAIN, 12);
        } catch (Exception e) {
            return new Font("Dialog", Font.PLAIN, 12);
        }
    }

    /**
     * 加载NotoEmoji.ttf字体
     */
    private Font loadNotoEmojiFont() {
        try {
            java.io.InputStream fontStream = getClass().getResourceAsStream("/font/NotoEmoji.ttf");
            if (fontStream == null) {
                System.err.println("EmojiLabel: 无法找到NotoEmoji.ttf字体文件");
                return null;
            }

            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            fontStream.close();

            // 设置字体大小为当前组件的字体大小
            int fontSize = mainFont != null ? mainFont.getSize() : 12;
            Font notoFont = baseFont.deriveFont(Font.PLAIN, (float)fontSize);

            System.out.println("EmojiLabel: 成功加载NotoEmoji字体");
            return notoFont;

        } catch (Exception e) {
            System.err.println("EmojiLabel: 加载NotoEmoji字体失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (emojiText != null && containsEmoji(emojiText) && notoEmojiFont != null) {
            paintEmojiText(g);
        } else {
            super.paintComponent(g);
        }
    }

    private void paintEmojiText(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        // 启用抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 绘制背景
        if (isOpaque()) {
            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }

        // 设置字体和颜色
        g2d.setColor(getForeground());

        // 分割文本，分别使用不同字体渲染emoji和普通文本
        renderMixedText(g2d, emojiText);

        g2d.dispose();
    }

    /**
     * 渲染混合文本（emoji + 普通文本）
     */
    private void renderMixedText(Graphics2D g2d, String text) {
        FontMetrics mainFm = g2d.getFontMetrics(mainFont);
        FontMetrics emojiFm = g2d.getFontMetrics(notoEmojiFont);

        int x = getInsets().left;
        int y = getInsets().top + Math.max(mainFm.getAscent(), emojiFm.getAscent());

        // 处理对齐方式
        if (getHorizontalAlignment() == SwingConstants.CENTER) {
            int textWidth = getTextWidth(text);
            x = (getWidth() - textWidth) / 2;
        } else if (getHorizontalAlignment() == SwingConstants.RIGHT) {
            int textWidth = getTextWidth(text);
            x = getWidth() - textWidth - getInsets().right;
        }

        if (getVerticalAlignment() == SwingConstants.CENTER) {
            y = (getHeight() + Math.max(mainFm.getAscent(), emojiFm.getAscent()) -
                 Math.max(mainFm.getDescent(), emojiFm.getDescent())) / 2;
        } else if (getVerticalAlignment() == SwingConstants.BOTTOM) {
            y = getHeight() - Math.max(mainFm.getDescent(), emojiFm.getDescent()) - getInsets().bottom;
        }

        // 逐字符渲染
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);

            if (isEmoji(c)) {
                // 使用NotoEmoji字体渲染emoji
                g2d.setFont(notoEmojiFont);
                g2d.drawString(charStr, x, y);
                x += g2d.getFontMetrics().stringWidth(charStr);
            } else {
                // 使用主字体渲染普通文本
                g2d.setFont(mainFont);
                g2d.drawString(charStr, x, y);
                x += g2d.getFontMetrics().stringWidth(charStr);
            }
        }
    }

    /**
     * 计算混合文本的总宽度
     */
    private int getTextWidth(String text) {
        Graphics g = getGraphics();
        if (g == null) return 0;

        Graphics2D g2d = (Graphics2D) g;
        FontMetrics mainFm = g2d.getFontMetrics(mainFont);
        FontMetrics emojiFm = g2d.getFontMetrics(notoEmojiFont);

        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);

            if (isEmoji(c)) {
                width += emojiFm.stringWidth(charStr);
            } else {
                width += mainFm.stringWidth(charStr);
            }
        }

        g.dispose();
        return width;
    }

    private boolean containsEmoji(String text) {
        if (text == null) return false;

        for (int i = 0; i < text.length(); i++) {
            if (isEmoji(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查字符是否为emoji
     */
    private boolean isEmoji(char c) {
        // 检查Unicode emoji范围
        return (c >= 0x1F000 && c <= 0x1FFFF) ||     // 各种符号和象形文字
               (c >= 0x2600 && c <= 0x27BF) ||       // 杂项符号
               (c >= 0x1F300 && c <= 0x1F5FF) ||     // 杂项符号和象形文字
               (c >= 0x1F600 && c <= 0x1F64F) ||     // 表情符号
               (c >= 0x1F680 && c <= 0x1F6FF) ||     // 交通和地图符号
               (c >= 0x1F700 && c <= 0x1F77F) ||     // 炼金术符号
               (c >= 0x1F780 && c <= 0x1F7FF) ||     // 几何图形扩展
               (c >= 0x1F800 && c <= 0x1F8FF) ||     // 补充箭头-C
               (c >= 0x1F900 && c <= 0x1F9FF) ||     // 补充符号和象形文字
               (c >= 0x1FA00 && c <= 0x1FA6F) ||     // 象棋符号
               (c >= 0x1FA70 && c <= 0x1FAFF) ||     // 扩展符号和象形文字-A
               Character.getType(c) == Character.OTHER_SYMBOL;
    }

    @Override
    public void setText(String text) {
        this.emojiText = text;
        super.setText(text);
        repaint();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        this.mainFont = font;

        // 重新加载emoji字体以匹配新的字体大小
        if (font != null && notoEmojiFont != null) {
            notoEmojiFont = notoEmojiFont.deriveFont(Font.PLAIN, (float)font.getSize());
        }

        repaint();
    }
}
