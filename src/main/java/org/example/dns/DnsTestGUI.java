package org.example.dns;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * DNS延迟测试工具主界面 - 使用emoji-java库优化emoji显示
 */
public class DnsTestGUI extends JFrame {
    private JTextArea dnsInputArea;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JButton testButton;
    private JButton applyButton;
    private JComboBox<WindowsDnsManager.NetworkInterface> interfaceComboBox;
    private JComboBox<String> primaryDnsCombo;
    private JComboBox<String> secondaryDnsCombo;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton applyOptimalButton;
    private JButton restoreAutoButton;
    private JButton flushDnsCacheButton;

    private final DnsTester dnsTester;
    private final WindowsDnsManager dnsManager;
    private List<DnsTestResult> testResults;

    // 添加自定义字体变量
    private Font customFont;

    public DnsTestGUI() {
        // 加载自定义字体
        customFont = loadCustomFont();

        this.dnsTester = new DnsTester();
        this.dnsManager = new WindowsDnsManager();
        this.testResults = new ArrayList<>();

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadNetworkInterfaces();
    }

    private void initializeComponents() {
        setTitle("DNS延迟测试工具 v1.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 600));

        // 设置窗口图标
        try {
            setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icon.ico")));
        } catch (Exception e) {
            // 忽略图标加载错误
        }

        // DNS输入区域 - 使用黑色字体
        dnsInputArea = new JTextArea(12, 35);
        dnsInputArea.setFont(customFont.deriveFont(Font.PLAIN, 13f));
        dnsInputArea.setText(String.join("\n", DnsTester.getCommonDnsServers()));
        dnsInputArea.setLineWrap(true);
        dnsInputArea.setWrapStyleWord(true);
        dnsInputArea.setBackground(new Color(255, 255, 255));
        dnsInputArea.setForeground(Color.BLACK);
        dnsInputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(206, 212, 218), 1, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        dnsInputArea.setSelectionColor(new Color(0, 123, 255, 80));
        dnsInputArea.setCaretColor(new Color(0, 123, 255));
        dnsInputArea.setTabSize(4);

        // 结果表格 - 使用纯文本表头
        String[] columnNames = {
            "DNS服务器",
            "延迟(ms)",
            "状态"
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultTable = new JTable(tableModel);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setFont(customFont.deriveFont(Font.PLAIN, 13f));
        resultTable.getTableHeader().setFont(customFont.deriveFont(Font.BOLD, 14f));
        resultTable.setRowHeight(32);
        resultTable.setShowGrid(true);
        resultTable.setGridColor(new Color(220, 220, 220));
        resultTable.setIntercellSpacing(new Dimension(1, 1));
        resultTable.setSelectionBackground(new Color(0, 123, 255, 100));
        resultTable.setSelectionForeground(Color.WHITE);
        resultTable.setBackground(Color.WHITE);
        resultTable.setForeground(Color.BLACK);
        resultTable.getTableHeader().setBackground(new Color(240, 242, 246));
        resultTable.getTableHeader().setForeground(Color.BLACK);
        resultTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(206, 212, 218)));

        // 设置列宽
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(150);

        // 主要按钮 - 去除emoji
        testButton = createStyledButton("开始测试", new Color(40, 167, 69), Color.BLACK);

        applyButton = createStyledButton("应用最优DNS", new Color(0, 123, 255), Color.BLACK);
        applyButton.setEnabled(false);

        // 网络接口选择 - 美化下拉框
        interfaceComboBox = new JComboBox<>();
        styleComboBox(interfaceComboBox);

        // 主DNS和辅助DNS选择 - 美化下拉框
        primaryDnsCombo = new JComboBox<>();
        styleComboBox(primaryDnsCombo);

        secondaryDnsCombo = new JComboBox<>();
        styleComboBox(secondaryDnsCombo);

        // 进度条和状态标签
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setFont(customFont.deriveFont(Font.PLAIN, 11f));
        progressBar.setForeground(new Color(40, 167, 69));
        progressBar.setBackground(new Color(233, 236, 239));
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        progressBar.setPreferredSize(new Dimension(200, 25));

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(customFont.deriveFont(Font.PLAIN, 13f));
        statusLabel.setForeground(new Color(40, 167, 69));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // 其他功能按钮 - 去除emoji
        applyOptimalButton = createStyledButton("应用选定DNS", new Color(13, 110, 253), Color.BLACK);

        restoreAutoButton = createStyledButton("恢复自动DNS", new Color(108, 117, 125), Color.BLACK);

        flushDnsCacheButton = createStyledButton("刷新DNS缓存", new Color(255, 193, 7), Color.BLACK);
    }

    /**
     * 创建统一样式的按钮
     */
    private JButton createStyledButton(String text, Color bgColor, Color fgColor) {
        JButton button = new JButton(text);
        button.setFont(customFont.deriveFont(Font.BOLD, 12f));
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bgColor.darker(), 1),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);

        // 添加悬停效果
        Color originalBg = bgColor;
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(originalBg.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(originalBg);
            }
        });

        return button;
    }

    /**
     * 统一样式化下拉框
     */
    private void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setFont(customFont.deriveFont(Font.PLAIN, 12f));
        comboBox.setBackground(Color.WHITE);
        comboBox.setForeground(Color.BLACK); // 改为黑色字体
        comboBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(206, 212, 218), 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        comboBox.setOpaque(true);
        comboBox.setPreferredSize(new Dimension(200, 32));
    }

    private void setupLayout() {
        setLayout(new BorderLayout(8, 8));

        // 创建主容器面板，添加边距
        JPanel mainContainer = new JPanel(new BorderLayout(10, 10));
        mainContainer.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainContainer.setBackground(new Color(248, 249, 250));

        // 左侧面板 - DNS输入区域 (重新设计)
        JPanel leftPanel = createDnsInputPanel();

        // 右侧面板 - 测试结果和控制区域 (重新设计)
        JPanel rightPanel = createResultPanel();

        // 顶部控制栏 (重新设计)
        JPanel topControlPanel = createTopControlPanel();

        // 底部状态栏 (重新设计)
        JPanel bottomStatusPanel = createBottomStatusPanel();

        // 主分割面板
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplitPane.setDividerLocation(350);
        mainSplitPane.setDividerSize(8);
        mainSplitPane.setOneTouchExpandable(true);
        mainSplitPane.setBorder(null);
        mainSplitPane.setBackground(new Color(248, 249, 250));

        mainContainer.add(topControlPanel, BorderLayout.NORTH);
        mainContainer.add(mainSplitPane, BorderLayout.CENTER);
        mainContainer.add(bottomStatusPanel, BorderLayout.SOUTH);

        add(mainContainer, BorderLayout.CENTER);
    }

    /**
     * 创建DNS输入面板
     */
    private JPanel createDnsInputPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
        leftPanel.setBackground(new Color(248, 249, 250));

        // 标题栏 - 现代化设计
        JPanel titlePanel = new JPanel(new BorderLayout(10, 0));
        titlePanel.setBackground(new Color(255, 255, 255));
        titlePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("DNS服务器配置");
        titleLabel.setFont(customFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Color.BLACK); // 改为黑色

        JLabel hintLabel = new JLabel("<html><span style='color: black;'>每行输入一个DNS地址</span></html>"); // 改为黑色
        hintLabel.setFont(customFont.deriveFont(Font.PLAIN, 11f));
        hintLabel.setForeground(Color.BLACK); // 改为黑色

        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(hintLabel, BorderLayout.EAST);

        // DNS输入区域容器
        JPanel inputContainer = new JPanel(new BorderLayout());
        inputContainer.setBackground(Color.WHITE);
        inputContainer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JScrollPane dnsScrollPane = new JScrollPane(dnsInputArea);
        dnsScrollPane.setBorder(null);
        dnsScrollPane.setBackground(Color.WHITE);
        dnsScrollPane.getViewport().setBackground(new Color(252, 253, 254));
        dnsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        dnsScrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        inputContainer.add(dnsScrollPane, BorderLayout.CENTER);

        // 按钮工具栏
        JPanel buttonToolbar = createDnsButtonToolbar();

        leftPanel.add(titlePanel, BorderLayout.NORTH);
        leftPanel.add(inputContainer, BorderLayout.CENTER);
        leftPanel.add(buttonToolbar, BorderLayout.SOUTH);

        return leftPanel;
    }

    /**
     * 创建DNS按钮工具栏
     */
    private JPanel createDnsButtonToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        toolbar.setBackground(new Color(248, 249, 250));
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        // 加载常用DNS按钮
        JButton loadCommonButton = createStyledButton("加载常用", new Color(13, 110, 253), Color.BLACK);
        loadCommonButton.addActionListener(e ->
            dnsInputArea.setText(String.join("\n", DnsTester.getCommonDnsServers())));

        // 清空按钮
        JButton clearButton = createStyledButton("清空", new Color(220, 53, 69), Color.BLACK);
        clearButton.addActionListener(e -> dnsInputArea.setText(""));

        // 导入按钮
        JButton importButton = createStyledButton("导入", new Color(108, 117, 125), Color.BLACK);
        importButton.addActionListener(e -> importDnsFromFile());

        // 导出按钮
        JButton exportButton = createStyledButton("导出", new Color(108, 117, 125), Color.BLACK);
        exportButton.addActionListener(e -> exportDnsToFile());

        toolbar.add(loadCommonButton);
        toolbar.add(clearButton);
        toolbar.add(importButton);
        toolbar.add(exportButton);

        return toolbar;
    }

    /**
     * 创建结果面板
     */
    private JPanel createResultPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setBackground(new Color(248, 249, 250));

        // 结果表格标题
        JPanel resultTitlePanel = new JPanel(new BorderLayout());
        resultTitlePanel.setBackground(Color.WHITE);
        resultTitlePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel resultTitleLabel = new JLabel("测试结果");
        resultTitleLabel.setFont(customFont.deriveFont(Font.BOLD, 16f));
        resultTitleLabel.setForeground(Color.BLACK); // 改为黑色
        resultTitlePanel.add(resultTitleLabel, BorderLayout.WEST);

        // 表格容器
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBackground(Color.WHITE);
        tableContainer.setBorder(BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true));

        JScrollPane tableScrollPane = new JScrollPane(resultTable);
        tableScrollPane.setBorder(null);
        tableScrollPane.getViewport().setBackground(Color.WHITE);
        tableContainer.add(tableScrollPane, BorderLayout.CENTER);

        // DNS设置面板
        JPanel dnsSettingsPanel = createDnsSettingsPanel();

        rightPanel.add(resultTitlePanel, BorderLayout.NORTH);
        rightPanel.add(tableContainer, BorderLayout.CENTER);
        rightPanel.add(dnsSettingsPanel, BorderLayout.SOUTH);

        return rightPanel;
    }

    /**
     * 创建DNS设置面板
     */
    private JPanel createDnsSettingsPanel() {
        JPanel settingsPanel = new JPanel(new BorderLayout(0, 8));
        settingsPanel.setBackground(new Color(248, 249, 250));
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        // 标题
        JPanel settingsTitlePanel = new JPanel(new BorderLayout());
        settingsTitlePanel.setBackground(Color.WHITE);
        settingsTitlePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        JLabel settingsLabel = new JLabel("DNS设置");
        settingsLabel.setFont(customFont.deriveFont(Font.BOLD, 14f));
        settingsLabel.setForeground(Color.BLACK); // 改为黑色
        settingsTitlePanel.add(settingsLabel, BorderLayout.WEST);

        // 设置内容
        JPanel settingsContent = new JPanel(new GridBagLayout());
        settingsContent.setBackground(Color.WHITE);
        settingsContent.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // 首选DNS
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel primaryLabel = new JLabel("首选DNS:");
        primaryLabel.setFont(customFont.deriveFont(Font.PLAIN, 12f));
        primaryLabel.setForeground(Color.BLACK); // 改为黑色
        settingsContent.add(primaryLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        primaryDnsCombo.setPreferredSize(new Dimension(200, 32));
        settingsContent.add(primaryDnsCombo, gbc);

        // 辅助DNS
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel secondaryLabel = new JLabel("辅助DNS:");
        secondaryLabel.setFont(customFont.deriveFont(Font.PLAIN, 12f));
        secondaryLabel.setForeground(Color.BLACK); // 改为黑色
        settingsContent.add(secondaryLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        secondaryDnsCombo.setPreferredSize(new Dimension(200, 32));
        settingsContent.add(secondaryDnsCombo, gbc);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 8));
        buttonPanel.setBackground(Color.WHITE);

        buttonPanel.add(applyOptimalButton);
        buttonPanel.add(restoreAutoButton);
        buttonPanel.add(flushDnsCacheButton);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        settingsContent.add(buttonPanel, gbc);

        settingsPanel.add(settingsTitlePanel, BorderLayout.NORTH);
        settingsPanel.add(settingsContent, BorderLayout.CENTER);

        return settingsPanel;
    }

    /**
     * 创建顶部控制面板
     */
    private JPanel createTopControlPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        // 左侧 - 网络接口选择
        JPanel leftSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftSection.setBackground(Color.WHITE);

        JLabel interfaceLabel = new JLabel("网络接口:");
        interfaceLabel.setFont(customFont.deriveFont(Font.BOLD, 12f));
        interfaceLabel.setForeground(Color.BLACK); // 改为黑色

        interfaceComboBox.setPreferredSize(new Dimension(250, 32));

        leftSection.add(interfaceLabel);
        leftSection.add(interfaceComboBox);

        // 右侧 - 主要操作按钮
        JPanel rightSection = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightSection.setBackground(Color.WHITE);

        rightSection.add(testButton);
        rightSection.add(applyButton);

        topPanel.add(leftSection, BorderLayout.WEST);
        topPanel.add(rightSection, BorderLayout.EAST);

        return topPanel;
    }

    /**
     * 创建底部状态面板
     */
    private JPanel createBottomStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout(10, 0));
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(222, 226, 230)),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        // 左侧状态标签
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);

        // 右侧进度条
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        progressPanel.setBackground(Color.WHITE);
        progressPanel.add(progressBar);

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressPanel, BorderLayout.EAST);

        return statusPanel;
    }

    /**
     * 导入DNS配置文件
     */
    private void importDnsFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                java.nio.file.Path path = file.toPath();
                String content = new String(java.nio.file.Files.readAllBytes(path), "UTF-8");
                dnsInputArea.setText(content);
                statusLabel.setText("已导入DNS配置: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "导入失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 导出DNS配置到文件
     */
    private void exportDnsToFile() {
        String content = dnsInputArea.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可导出的DNS配置", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        fileChooser.setSelectedFile(new java.io.File("dns_config.txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".txt")) {
                    file = new java.io.File(file.getParentFile(), file.getName() + ".txt");
                }
                java.nio.file.Files.write(file.toPath(), content.getBytes("UTF-8"));
                statusLabel.setText("已导出DNS配置: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setupEventHandlers() {
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performDnsTest();
            }
        });

        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyOptimalDns();
            }
        });

        // 直接绑定按钮事件
        applyOptimalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applySelectedDns();
            }
        });

        restoreAutoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restoreAutoDns();
            }
        });

        flushDnsCacheButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flushDnsCache();
            }
        });
    }

    private void loadNetworkInterfaces() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("正在加载网络接口...");
            statusLabel.setForeground(new Color(255, 193, 7));

            SwingWorker<List<WindowsDnsManager.NetworkInterface>, Void> worker =
                new SwingWorker<List<WindowsDnsManager.NetworkInterface>, Void>() {

                @Override
                protected List<WindowsDnsManager.NetworkInterface> doInBackground() {
                    return dnsManager.getNetworkInterfaces();
                }

                @Override
                protected void done() {
                    try {
                        List<WindowsDnsManager.NetworkInterface> interfaces = get();
                        interfaceComboBox.removeAllItems();
                        for (WindowsDnsManager.NetworkInterface intf : interfaces) {
                            interfaceComboBox.addItem(intf);
                        }
                        statusLabel.setText("就绪 - 找到 " + interfaces.size() + " 个网络接口");
                        statusLabel.setForeground(new Color(40, 167, 69));
                    } catch (Exception e) {
                        statusLabel.setText("加载网络接口失败: " + e.getMessage());
                        statusLabel.setForeground(new Color(220, 53, 69));
                        showErrorDialog("加载网络接口失败", e.getMessage());
                    }
                }
            };

            worker.execute();
        });
    }

    private void performDnsTest() {
        String dnsText = dnsInputArea.getText().trim();
        if (dnsText.isEmpty()) {
            showWarningDialog("请输入要测试的DNS服务器");
            return;
        }

        List<String> dnsServers = new ArrayList<>();
        for (String line : dnsText.split("\n")) {
            String dns = line.trim();
            if (!dns.isEmpty() && !dns.startsWith("#")) {
                dnsServers.add(dns);
            }
        }

        if (dnsServers.isEmpty()) {
            showWarningDialog("没有找到有效的DNS服务器");
            return;
        }

        testButton.setEnabled(false);
        applyButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false); // 改为确定进度模式
        progressBar.setMinimum(0);
        progressBar.setMaximum(dnsServers.size());
        progressBar.setValue(0);
        progressBar.setString("0/" + dnsServers.size() + " (0%)");
        statusLabel.setText("正在测试 " + dnsServers.size() + " 个DNS服务器...");
        statusLabel.setForeground(new Color(13, 110, 253));

        // 清空结果表格
        tableModel.setRowCount(0);

        SwingWorker<List<DnsTestResult>, Integer> worker = new SwingWorker<List<DnsTestResult>, Integer>() {
            @Override
            protected List<DnsTestResult> doInBackground() {
                List<DnsTestResult> results = new ArrayList<>();

                for (int i = 0; i < dnsServers.size(); i++) {
                    String dnsServer = dnsServers.get(i);
                    final int currentIndex = i; // 创建final变量
                    final String currentDnsServer = dnsServer; // 创建final变量

                    // 发布进度更新
                    publish(i);

                    try {
                        // 测试单个DNS服务器
                        DnsTestResult result = dnsTester.testSingleDns(dnsServer);
                        results.add(result);

                        // 更新状态信息
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText(String.format("正在测试 %s (%d/%d)...",
                                currentDnsServer, currentIndex + 1, dnsServers.size()));
                        });

                    } catch (Exception e) {
                        // 如果测试失败，创建失败结果
                        DnsTestResult failResult = new DnsTestResult(dnsServer, 0, false, e.getMessage());
                        results.add(failResult);
                    }
                }

                // 按延迟排序结果
                results.sort((a, b) -> {
                    if (a.isSuccess() && b.isSuccess()) {
                        return Long.compare(a.getLatency(), b.getLatency());
                    } else if (a.isSuccess()) {
                        return -1;
                    } else if (b.isSuccess()) {
                        return 1;
                    } else {
                        return 0;
                    }
                });

                return results;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                // 更新进度条
                for (Integer progress : chunks) {
                    int completed = progress + 1;
                    progressBar.setValue(completed);
                    int percentage = (int) ((completed * 100.0) / dnsServers.size());
                    progressBar.setString(completed + "/" + dnsServers.size() + " (" + percentage + "%)");
                }
            }

            @Override
            protected void done() {
                try {
                    testResults = get();
                    displayResults(testResults);

                    if (!testResults.isEmpty() && testResults.get(0).isSuccess()) {
                        applyButton.setEnabled(true);
                        statusLabel.setText("测试完成 - 最快DNS: " + testResults.get(0).getDnsServer() +
                                          " (" + testResults.get(0).getLatency() + "ms)");
                        statusLabel.setForeground(new Color(40, 167, 69));
                    } else {
                        statusLabel.setText("测试完成 - 没有可用的DNS服务器");
                        statusLabel.setForeground(new Color(255, 193, 7));
                    }

                } catch (Exception e) {
                    statusLabel.setText("测试失败: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 53, 69));
                    showErrorDialog("DNS测试失败", e.getMessage());
                } finally {
                    testButton.setEnabled(true);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                }
            }
        };

        worker.execute();
    }

    private void displayResults(List<DnsTestResult> results) {
        tableModel.setRowCount(0);

        // 清空DNS选择列表
        primaryDnsCombo.removeAllItems();
        secondaryDnsCombo.removeAllItems();

        // 添加空选项到辅助DNS
        secondaryDnsCombo.addItem("无");

        for (DnsTestResult result : results) {
            Object[] row = {
                result.getDnsServer(),
                result.isSuccess() ? result.getLatency() : "N/A",
                result.isSuccess() ? ("成功") : ("失败: " + result.getErrorMessage())
            };
            tableModel.addRow(row);

            // 只添加成功的DNS到选择列表
            if (result.isSuccess()) {
                String dnsInfo = result.getDnsServer() + " (" + result.getLatency() + "ms)";
                primaryDnsCombo.addItem(dnsInfo);
                secondaryDnsCombo.addItem(dnsInfo);
            }
        }

        // 自动选择最优的两个DNS
        if (primaryDnsCombo.getItemCount() > 0) {
            primaryDnsCombo.setSelectedIndex(0);
            if (secondaryDnsCombo.getItemCount() > 2) { // 0=无, 1=第一个DNS, 2=第二个DNS
                secondaryDnsCombo.setSelectedIndex(2);
            }
        }
    }

    private void applyOptimalDns() {
        if (testResults.isEmpty() || !testResults.get(0).isSuccess()) {
            showWarningDialog("没有可用的DNS服务器可以应用");
            return;
        }

        WindowsDnsManager.NetworkInterface selectedInterface =
            (WindowsDnsManager.NetworkInterface) interfaceComboBox.getSelectedItem();

        if (selectedInterface == null) {
            showWarningDialog("请选择一个网络接口");
            return;
        }

        String primaryDns = testResults.get(0).getDnsServer();
        final String secondaryDns;
        String tempSecondaryDns = null;
        for (int i = 1; i < testResults.size(); i++) {
            if (testResults.get(i).isSuccess()) {
                tempSecondaryDns = testResults.get(i).getDnsServer();
                break;
            }
        }
        secondaryDns = tempSecondaryDns;

        String message = String.format(
            "<html><div style='font-size: 12px;'>" +
            "<p><b>确定要将最优DNS应用到网络接口吗？</b></p>" +
            "<p><b>接口:</b> %s</p>" +
            "<p><b>主DNS:</b> %s</p>" +
            "<p><b>备用DNS:</b> %s</p>" +
            "</div></html>",
            selectedInterface.getName(),
            primaryDns,
            (secondaryDns != null ? secondaryDns : "无")
        );

        int confirm = JOptionPane.showConfirmDialog(this, message,
            "确认应用DNS设置", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        applyButton.setEnabled(false);
        statusLabel.setText("正在应用DNS设置...");
        statusLabel.setForeground(new Color(13, 110, 253));

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                boolean success = dnsManager.setDns(selectedInterface.getName(), primaryDns, secondaryDns);
                if (success) {
                    dnsManager.flushDnsCache();
                }
                return success;
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        statusLabel.setText("DNS设置已成功应用");
                        statusLabel.setForeground(new Color(40, 167, 69));
                        showSuccessDialog("DNS设置已成功应用到 \"" + selectedInterface.getName() + "\"");
                    } else {
                        statusLabel.setText("DNS设置应用失败");
                        statusLabel.setForeground(new Color(220, 53, 69));
                        showErrorDialog("DNS设置应用失败", "请检查是否有管理员权限");
                    }
                } catch (Exception e) {
                    statusLabel.setText("DNS设置应用出错: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 53, 69));
                    showErrorDialog("DNS设置应用出错", e.getMessage());
                } finally {
                    applyButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    /**
     * 应用用户选定的DNS设置
     */
    private void applySelectedDns() {
        if (primaryDnsCombo.getItemCount() == 0) {
            showWarningDialog("请先进行DNS测试");
            return;
        }

        String primaryDnsSelection = (String) primaryDnsCombo.getSelectedItem();
        String secondaryDnsSelection = (String) secondaryDnsCombo.getSelectedItem();

        if (primaryDnsSelection == null) {
            showWarningDialog("请选择首选DNS");
            return;
        }

        // 从选择项中提取DNS地址（格式：IP (延迟ms)）
        final String primaryDns = primaryDnsSelection.split(" \\(")[0];
        final String secondaryDns;
        if (secondaryDnsSelection != null && !"无".equals(secondaryDnsSelection)) {
            secondaryDns = secondaryDnsSelection.split(" \\(")[0];
        } else {
            secondaryDns = null;
        }

        String message = String.format(
            "<html><div style='font-size: 12px;'>" +
            "<p><b>确定要应用以下DNS设置吗？</b></p>" +
            "<p><b>首选DNS:</b> %s</p>" +
            "<p><b>辅助DNS:</b> %s</p>" +
            "<p><i>注意：将为所有活动网络接口设置DNS</i></p>" +
            "</div></html>",
            primaryDns,
            (secondaryDns != null ? secondaryDns : "无")
        );

        int confirm = JOptionPane.showConfirmDialog(this, message,
            "确认应用DNS设置", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        statusLabel.setText("正在应用DNS设置...");
        statusLabel.setForeground(new Color(13, 110, 253));

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return dnsManager.setDnsForAllInterfaces(primaryDns, secondaryDns);
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        statusLabel.setText("DNS设置已成功应用");
                        statusLabel.setForeground(new Color(40, 167, 69));
                        String successMessage = String.format(
                            "DNS设置已成功应用！\n首选DNS: %s\n辅助DNS: %s",
                            primaryDns, (secondaryDns != null ? secondaryDns : "无")
                        );
                        showSuccessDialog(successMessage);
                    } else {
                        statusLabel.setText("DNS设置应用失败");
                        statusLabel.setForeground(new Color(220, 53, 69));
                        showErrorDialog("DNS设置应用失败",
                            "请检查是否有管理员权限\n建议右键选择\"以管理员身份运行\"程序");
                    }
                } catch (Exception e) {
                    statusLabel.setText("DNS设置应用出错: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 53, 69));
                    showErrorDialog("DNS设置应用出错", e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * 恢复为自动获取DNS
     */
    private void restoreAutoDns() {
        String message = "<html><div style='font-size: 12px;'>" +
                        "<p><b>确定要恢复为自动获取DNS吗？</b></p>" +
                        "<p><i>这将为所有网络接口恢复DHCP自动获取DNS设置</i></p>" +
                        "</div></html>";

        int confirm = JOptionPane.showConfirmDialog(this, message,
            "确认恢复自动DNS", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        statusLabel.setText("正在恢复自动DNS设置...");
        statusLabel.setForeground(new Color(13, 110, 253));

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                boolean success = false;
                List<WindowsDnsManager.NetworkInterface> interfaces = dnsManager.getNetworkInterfaces();

                for (WindowsDnsManager.NetworkInterface intf : interfaces) {
                    if (intf.isConnected()) {
                        boolean result = dnsManager.setDnsToAuto(intf.getName());
                        if (result) {
                            success = true;
                            System.out.println("成功为接口 " + intf.getName() + " 恢复自动DNS");
                        } else {
                            System.out.println("为接口 " + intf.getName() + " 恢复自动DNS失败");
                        }
                    }
                }

                if (success) {
                    dnsManager.flushDnsCache();
                }

                return success;
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        statusLabel.setText("已恢复自动DNS设置");
                        statusLabel.setForeground(new Color(40, 167, 69));
                        showSuccessDialog("已成功恢复为自动获取DNS设置");
                    } else {
                        statusLabel.setText("恢复自动DNS失败");
                        statusLabel.setForeground(new Color(220, 53, 69));
                        showErrorDialog("恢复自动DNS失败", "请检查是否有管理员权限");
                    }
                } catch (Exception e) {
                    statusLabel.setText("恢复自动DNS出错: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 53, 69));
                    showErrorDialog("恢复自动DNS出错", e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * 刷新DNS缓存
     */
    private void flushDnsCache() {
        String message = "<html><div style='font-size: 12px;'>" +
                        "<p><b>确定要刷新DNS缓存吗？</b></p>" +
                        "<p><i>这将清除本地DNS缓存并强制重新获取DNS信息</i></p>" +
                        "</div></html>";

        int confirm = JOptionPane.showConfirmDialog(this, message,
            "确认刷新DNS缓存", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        statusLabel.setText("正在刷新DNS缓存...");
        statusLabel.setForeground(new Color(13, 110, 253));

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    dnsManager.flushDnsCache();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        statusLabel.setText("DNS缓存已成功刷新");
                        statusLabel.setForeground(new Color(40, 167, 69));
                        showSuccessDialog("DNS缓存已成功刷新");
                    } else {
                        statusLabel.setText("DNS缓存刷新失败");
                        statusLabel.setForeground(new Color(220, 53, 69));
                        showErrorDialog("DNS缓存刷新失败", "请检查是否有管理员权限");
                    }
                } catch (Exception e) {
                    statusLabel.setText("刷新DNS缓存出错: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 53, 69));
                    showErrorDialog("刷新DNS缓存出错", e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * 显示成功对话框
     */
    private void showSuccessDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "成功", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 显示警告对话框
     */
    private void showWarningDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * 显示错误对话框
     */
    private void showErrorDialog(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        // 设置系统属性解决中文乱码问题
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");

        // 设置系统外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // 加载自定义字体
            Font customFont = loadCustomFont();

            // 设置所有UI组件的默认字体
            UIManager.put("Label.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("Button.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("TextField.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("TextArea.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("ComboBox.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("Table.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("TableHeader.font", customFont.deriveFont(Font.BOLD, 12f));
            UIManager.put("Menu.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("MenuItem.font", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("TitledBorder.font", customFont.deriveFont(Font.BOLD, 12f));
            UIManager.put("OptionPane.buttonFont", customFont.deriveFont(Font.PLAIN, 12f));
            UIManager.put("OptionPane.messageFont", customFont.deriveFont(Font.PLAIN, 12f));

        } catch (Exception e) {
            // 如果系统外观不可用，使用默认外观
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ex) {
                // 忽略外观设置错误，使用默认外观
                System.err.println("无法设置外观: " + ex.getMessage());
            }
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new DnsTestGUI().setVisible(true);
            }
        });
    }

    /**
     * 加载自定义字体 - 使用NotoEmoji.ttf支持Emoji显示
     */
    private static Font loadCustomFont() {
        try {
            // 首先加载NotoEmoji字体用于Emoji显示
            Font notoEmojiFont = loadNotoEmojiFont();

            // 然后尝试加载自定义主字体
            Font customFont = loadMainFont();

            // 如果主字体加载失败，使用系统默认字体（优先 Source Han Sans SC）
            if (customFont == null) {
                try {
                    customFont = new Font("Source Han Sans SC", Font.PLAIN, 12);
                } catch (Exception ignore) {
                    customFont = new Font("Dialog", Font.PLAIN, 12);
                }
            }

            // 设置字体回退策略，确保Emoji能够正确显示
            setupEmojiSupport(customFont, notoEmojiFont);

            return customFont;

        } catch (Exception e) {
            System.err.println("加载字体失败: " + e.getMessage());
            // 如果加载失败，尝试创建支持Emoji的默认字体
            return createFallbackFont();
        }
    }

    /**
     * 加载NotoEmoji字体
     */
    private static Font loadNotoEmojiFont() {
        try {
            java.io.InputStream fontStream = DnsTestGUI.class.getResourceAsStream("/font/NotoEmoji.ttf");
            if (fontStream == null) {
                System.err.println("无法找到NotoEmoji.ttf字体文件");
                return null;
            }

            Font notoEmojiFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            fontStream.close();

            // 设置字体大小
            notoEmojiFont = notoEmojiFont.deriveFont(Font.PLAIN, 12f);

            // 注册字体到系统中
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(notoEmojiFont);

            System.out.println("成功加载NotoEmoji字体: " + notoEmojiFont.getFontName());
            return notoEmojiFont;

        } catch (Exception e) {
            System.err.println("加载NotoEmoji字体失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 加载主要字体
     */
    private static Font loadMainFont() {
        try {
            // 改为加载 SourceHanSansSC-Regular-2.otf 作为主字体
            java.io.InputStream fontStream = DnsTestGUI.class.getResourceAsStream("/font/SourceHanSansSC-Regular-2.otf");
            if (fontStream == null) {
                System.out.println("未找到 SourceHanSansSC-Regular-2.otf，尝试使用已安装字体族名");
                // 优先尝试使用已安装字体族名
                return new Font("Source Han Sans SC", Font.PLAIN, 12);
            }

            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            fontStream.close();
            Font customFont = baseFont.deriveFont(Font.PLAIN, 12f);

            // 注册字体到系统中
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(customFont);

            System.out.println("成功加载主字体: " + customFont.getFontName());
            return customFont;

        } catch (Exception e) {
            System.err.println("加载主字体失败: " + e.getMessage());
            // 回退顺序：已安装的Source Han Sans SC -> Dialog
            try {
                return new Font("Source Han Sans SC", Font.PLAIN, 12);
            } catch (Exception ignore) {
                return new Font("Dialog", Font.PLAIN, 12);
            }
        }
    }

    /**
     * 设置Emoji支持
     */
    private static void setupEmojiSupport(Font mainFont, Font emojiFont) {
        try {
            // 设置系统属性以支持Unicode和Emoji
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("sun.jnu.encoding", "UTF-8");
            System.setProperty("awt.font.desktophints", "on");
            System.setProperty("swing.aatext", "true");

            // 如果有NotoEmoji字体，将其设置为全局emoji字体
            if (emojiFont != null) {
                // 设置UIManager属性以使用NotoEmoji字体处理特殊字符
                UIManager.put("Label.font", mainFont);
                UIManager.put("Button.font", mainFont);

                // 创建自定义的字体回退映射
                System.setProperty("swing.plaf.metal.controlFont", mainFont.getFamily());
                System.setProperty("swing.plaf.metal.userFont", mainFont.getFamily());

                System.out.println("已设置NotoEmoji字体作为Emoji显示字体");
            }

        } catch (Exception e) {
            System.err.println("设置Emoji支持失败: " + e.getMessage());
        }
    }

    /**
     * 创建备用字体（当加载失败时使用）
     */
    private static Font createFallbackFont() {
        // 尝试使用系统中的Emoji字体
        String[] systemEmojiFonts = {
            "Segoe UI Emoji",
            "Noto Color Emoji",
            "Apple Color Emoji",
            "Symbola"
        };

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();

        for (String emojiFont : systemEmojiFonts) {
            for (String availableFont : availableFonts) {
                if (availableFont.equalsIgnoreCase(emojiFont)) {
                    System.out.println("使用系统Emoji字体: " + emojiFont);
                    return new Font(emojiFont, Font.PLAIN, 12);
                }
            }
        }

        // 如果都没有找到，使用默认字体
        return new Font("Dialog", Font.PLAIN, 12);
    }
}
