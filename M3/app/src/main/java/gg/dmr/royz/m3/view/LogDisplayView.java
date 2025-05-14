package gg.dmr.royz.m3.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import gg.dmr.royz.m3.R;
import gg.dmr.royz.m3.utils.LogUtil;

/**
 * 日志显示组件
 * 用于显示调试日志，支持自动滚动和清除
 */
public class LogDisplayView extends LinearLayout implements LogUtil.LogListener {
    private TextView logTextView;
    private ScrollView scrollView;
    private Button clearButton;

    private StringBuilder logBuilder = new StringBuilder();
    private static final int MAX_LOG_LENGTH = 8000; // 防止日志过长导致性能问题

    public LogDisplayView(Context context) {
        super(context);
        init(context);
    }

    public LogDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LogDisplayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.view_log_display, this, true);

        // 获取组件引用
        logTextView = findViewById(R.id.log_text_view);
        scrollView = findViewById(R.id.log_scroll_view);
        clearButton = findViewById(R.id.clear_log_button);

        // 设置清除按钮点击事件
        clearButton.setOnClickListener(v -> clearLog());

        // 注册日志监听器
        LogUtil.setLogListener(this);
    }

    @Override
    public void onNewLog(String logMessage) {
        post(() -> {
            // 添加时间戳
            String timestamp = android.text.format.DateFormat.format("HH:mm:ss",
                    System.currentTimeMillis()).toString();

            // 添加到日志
            if (logBuilder.length() > 0) {
                logBuilder.append("\n");
            }
            logBuilder.append("[").append(timestamp).append("] ").append(logMessage);

            // 检查日志长度，防止过长
            if (logBuilder.length() > MAX_LOG_LENGTH) {
                int cutIndex = logBuilder.length() - MAX_LOG_LENGTH;
                int newlineIndex = logBuilder.indexOf("\n", cutIndex);
                if (newlineIndex >= 0) {
                    logBuilder.delete(0, newlineIndex + 1);
                }
            }

            // 更新文本
            logTextView.setText(logBuilder.toString());

            // 自动滚动到底部
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    // 清除日志
    public void clearLog() {
        logBuilder.setLength(0);
        logTextView.setText("");
    }

    // 获取日志内容
    public String getLogContent() {
        return logBuilder.toString();
    }
}