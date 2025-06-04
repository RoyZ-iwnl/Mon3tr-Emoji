package gg.dmr.royz.m3;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 设置版本信息
        setupVersionInfo();

        // 设置点击事件
        setupClickListeners();
    }

    private void setupVersionInfo() {
        TextView versionText = findViewById(R.id.version_text);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("版本 " + versionName);
        } catch (Exception e) {
            versionText.setText("版本 1.0.0");
        }
    }

    private void setupClickListeners() {
        // GitHub贡献者
        MaterialCardView githubCard = findViewById(R.id.github_card);
        githubCard.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/RoyZ-iwnl/Mon3tr-Emoji"));
            startActivity(intent);
        });

        // B站主页
        MaterialCardView bilibiliCard = findViewById(R.id.bilibili_card);
        bilibiliCard.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://space.bilibili.com/13059944"));
            startActivity(intent);
        });

        // 邮箱联系
        MaterialCardView emailCard = findViewById(R.id.email_card);
        emailCard.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:Roy@DMR.gg"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Mon3tr反馈");
            startActivity(Intent.createChooser(intent, "发送邮件"));
        });

        // 开源协议
        MaterialCardView licenseCard = findViewById(R.id.license_card);
        licenseCard.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"));
            startActivity(intent);
        });
    }
}
