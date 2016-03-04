package com.sun.bingo.ui.activity;

import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;
import android.widget.TextView;

import com.sun.bingo.R;
import com.sun.bingo.util.theme.AppUtil;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * 关于软件 view
 * Created by sunfusheng on 15/9/8.
 */
public class AboutAppActivity extends BaseActivity {


    @Bind(R.id.tv_version)
    TextView tvVersion;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.collapsing_toolbar)
    CollapsingToolbarLayout collapsingToolbar;
    @Bind(R.id.webView)
    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_app);
        ButterKnife.bind(this);

        initView();
    }

    private void initView() {
        initToolBar(toolbar, true, "");
        // 隐藏toolbar之后,显示"关于软件"作为toolbar的title
        collapsingToolbar.setTitle(getString(R.string.about_app));
        // show web view, the local web page locates in assets folder
        webView.loadUrl("file:///android_asset/about_app.html");
        // show versionName, no versionCode
        tvVersion.setText("Version " + AppUtil.getVersionName(this));
    }

}
