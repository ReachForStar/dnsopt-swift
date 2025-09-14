package org.example.dns;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;

/**
 * DNS延迟测试工具 - 现代化Swing应用程序入口
 */
public class DnsTestApplication {

    public static void main(String[] args) {
        // 设置系统属性以获得更好的显示效果
        System.setProperty("sun.java2d.uiScale", "1.0");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("swing.plaf.metal.controlFont", "Source Han Sans SC");
        System.setProperty("swing.plaf.metal.userFont", "Source Han Sans SC");

        // 启用现代化Look and Feel
        SwingUtilities.invokeLater(() -> {
            try {
                // 使用FlatLaf现代化主题
                FlatLightLaf.setup();

                // 加载并注册主字体 SourceHanSansSC-Regular-2.otf
                Font defaultFont = loadSourceHanSansFont(12f);

                // 设置全局UI字体属性
                if (defaultFont != null) {
                    UIManager.put("defaultFont", defaultFont);
                    UIManager.put("Label.font", defaultFont);
                    UIManager.put("Button.font", defaultFont);
                    UIManager.put("TextField.font", defaultFont);
                    UIManager.put("TextArea.font", defaultFont);
                    UIManager.put("ComboBox.font", defaultFont);
                    UIManager.put("Table.font", defaultFont);
                    UIManager.put("TableHeader.font", defaultFont.deriveFont(Font.BOLD));
                    UIManager.put("Menu.font", defaultFont);
                    UIManager.put("MenuItem.font", defaultFont);
                    UIManager.put("TitledBorder.font", defaultFont.deriveFont(Font.BOLD));
                    UIManager.put("OptionPane.buttonFont", defaultFont);
                    UIManager.put("OptionPane.messageFont", defaultFont);
                }

                // 统一控件圆角与主题色
                UIManager.put("Button.arc", 6);
                UIManager.put("Component.arc", 6);
                UIManager.put("ProgressBar.arc", 6);
                UIManager.put("TextComponent.arc", 6);
                UIManager.put("ScrollBar.thumb.arc", 6);
                UIManager.put("ScrollBar.track.arc", 6);

                UIManager.put("Button.default.background", new Color(0, 123, 255));
                UIManager.put("Button.default.foreground", Color.WHITE);
                UIManager.put("Button.default.focusedBackground", new Color(0, 86, 179));
                UIManager.put("ProgressBar.foreground", new Color(40, 167, 69));
                UIManager.put("Table.selectionBackground", new Color(0, 123, 255, 30));
                UIManager.put("Table.selectionForeground", new Color(0, 123, 255));

            } catch (Exception e) {
                try {
                    // 如果FlatLaf不可用，使用系统默认主题
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            // 创建并显示主窗口
            SwingUtilities.invokeLater(() -> {
                try {
                    new DnsTestGUI().setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                        "应用程序启动失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    /**
     * 从资源加载 SourceHanSansSC-Regular-2.otf 并注册，返回指定大小的派生字体；失败则回退到系统字体族名
     */
    private static Font loadSourceHanSansFont(float size) {
        try {
            java.io.InputStream is = DnsTestApplication.class.getResourceAsStream("/font/SourceHanSansSC-Regular-2.otf");
            if (is != null) {
                Font base = Font.createFont(Font.TRUETYPE_FONT, is);
                is.close();
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(base);
                return base.deriveFont(Font.PLAIN, size);
            } else {
                System.err.println("未找到资源字体 SourceHanSansSC-Regular-2.otf，尝试系统字体族名");
            }
        } catch (Exception e) {
            System.err.println("加载 SourceHanSansSC-Regular-2.otf 失败: " + e.getMessage());
        }
        try {
            return new Font("Source Han Sans SC", Font.PLAIN, (int) size);
        } catch (Exception ignore) {
            return new Font("Dialog", Font.PLAIN, (int) size);
        }
    }
}
