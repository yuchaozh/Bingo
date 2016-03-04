package com.sun.bingo.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sun.bingo.R;
import com.sun.bingo.constant.ConstantParams;
import com.sun.bingo.control.NavigateManager;
import com.sun.bingo.control.SingleControl;
import com.sun.bingo.control.UrlManager;
import com.sun.bingo.entity.SinaUserInfoEntity;
import com.sun.bingo.entity.UserEntity;
import com.sun.bingo.framework.dialog.LoadingDialog;
import com.sun.bingo.framework.dialog.ToastTip;
import com.sun.bingo.framework.okhttp.OkHttpProxy;
import com.sun.bingo.framework.okhttp.callback.JsonCallBack;
import com.sun.bingo.framework.okhttp.request.RequestCall;
import com.sun.bingo.util.KeyBoardUtil;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.listener.OtherLoginListener;
import cn.bmob.v3.listener.UpdateListener;
import okhttp3.Call;

/**
 * Created by sunfusheng on 15/7/27.
 */
public class LoginActivity extends BaseActivity<SingleControl> implements View.OnClickListener {

    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.tv_login_sina)
    TextView tvLoginSina;
    @Bind(R.id.ll_root_view)
    LinearLayout llRootView;

    private AuthInfo mAuthInfo;
    private SsoHandler mSsoHandler;
    private Oauth2AccessToken mAccessToken;
    private LoadingDialog mLoadingDialog;
    private static String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);

        initData();
        initView();
        initListener();
    }

    private void initData() {
        mAuthInfo = new AuthInfo(this, ConstantParams.SINA_APP_KEY, ConstantParams.SINA_REDIRECT_URL, ConstantParams.SINA_SCOPE);
        mSsoHandler = new SsoHandler(this, mAuthInfo);
        mLoadingDialog = new LoadingDialog(this);
    }

    // add "Login" to app bar
    // 在低版本的SDK里使用高版本函数,使用@SupperssLint("NewApi")会使得编译通过,和@Target()类似
    @SuppressLint("NewApi")
    private void initView() {
        initToolBar(toolbar, false, "登录");
    }

    private void initListener() {
        llRootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "llRootView onTouch, hide keyboard");
                KeyBoardUtil.hideKeyboard(LoginActivity.this);
                return false;
            }
        });
        tvLoginSina.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            // onClick event on login sina text view
            case R.id.tv_login_sina:
                mSsoHandler.authorize(new AuthListener());
                break;
        }
    }

    class AuthListener implements WeiboAuthListener {
        // login successful
        @Override
        public void onComplete(Bundle values) {
            mAccessToken = Oauth2AccessToken.parseAccessToken(values);
            if (mAccessToken.isSessionValid()) {
                getAccountSharedPreferences().uid(mAccessToken.getUid());
                getAccountSharedPreferences().access_token(mAccessToken.getToken());
                getAccountSharedPreferences().refresh_token(mAccessToken.getRefreshToken());
                getAccountSharedPreferences().expires_in(mAccessToken.getExpiresTime());
                // 登入成功后跳转
                loginSuccess();
            } else {
                ToastTip.show(values.getString("code", ""));
            }
        }

        // login cancel
        @Override
        public void onCancel() {
            ToastTip.show("暂时只支持微博账号登录哦");
        }

        // login fail
        @Override
        public void onWeiboException(WeiboException e) {
            ToastTip.show("微博授权异常");
        }
    }

    // 新浪账号登录成功后跳转
    private void loginSuccess() {
        // Best practise, check dialog is null or not at first
        if (mLoadingDialog != null) {
            mLoadingDialog.showCancelDialog("正在登录，请稍候....");
        }
        BmobUser.BmobThirdUserAuth authEntity = new BmobUser.BmobThirdUserAuth(BmobUser.BmobThirdUserAuth.SNS_TYPE_WEIBO, mAccessToken.getToken(), mAccessToken.getExpiresTime() + "", mAccessToken.getUid());
        BmobUser.loginWithAuthData(LoginActivity.this, authEntity, new OtherLoginListener() {
            @Override
            public void onSuccess(JSONObject jsonObject) {
                myEntity = BmobUser.getCurrentUser(LoginActivity.this, UserEntity.class);
                getSinaUserInfo();
            }

            @Override
            public void onFailure(int i, String s) {
                ToastTip.show("登录失败");
                mLoadingDialog.dismiss();
            }
        });
    }

    // 获取新浪用户信息
    private void getSinaUserInfo() {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", getAccountSharedPreferences().access_token());
        params.put("uid", getAccountSharedPreferences().uid());

        // call get user info api
        RequestCall build = OkHttpProxy.get().url(UrlManager.URL_SINA_USER_INFO).params(params).build();
        build.execute(new JsonCallBack<SinaUserInfoEntity>() {
            @Override
            public void onSuccess(SinaUserInfoEntity response) {
                updateUserInfo(response);
            }

            @Override
            public void onFailure(Call request, Exception e) {
                gotoMainOrProfile();
            }
        });
    }

    // 更新Bmob用户信息
    private void updateUserInfo(SinaUserInfoEntity entity) {
        if (entity == null) {
            gotoMainOrProfile();
            return;
        }
        boolean isUpdate = false;
        if (TextUtils.isEmpty(myEntity.getNickName()) && !TextUtils.isEmpty(entity.getScreen_name())) {
            myEntity.setNickName(entity.getScreen_name());
            isUpdate = true;
        }
        if (TextUtils.isEmpty(myEntity.getUserAvatar()) && !TextUtils.isEmpty(entity.getAvatar_hd())) {
            myEntity.setUserAvatar(entity.getAvatar_hd());
            isUpdate = true;
        }
        if (TextUtils.isEmpty(myEntity.getUserSign()) && !TextUtils.isEmpty(entity.getDescription())) {
            myEntity.setUserSign(entity.getDescription());
            isUpdate = true;
        }
        if (isUpdate) {
            myEntity.update(LoginActivity.this, myEntity.getObjectId(), new UpdateListener() {
                @Override
                public void onSuccess() {
                    gotoMainOrProfile();
                }

                @Override
                public void onFailure(int i, String s) {
                    gotoMainOrProfile();
                }
            });
        } else {
            gotoMainOrProfile();
        }
    }

    // 跳转到主界面或用户信息界面
    // if missing some user information, go to profile view first, else go to main view
    private void gotoMainOrProfile() {
        mLoadingDialog.dismiss();
        if (TextUtils.isEmpty(myEntity.getNickName()) || TextUtils.isEmpty(myEntity.getUserAvatar())) {
            Log.d(TAG, "missing user info, go to profile view");
            NavigateManager.gotoProfileActivity(LoginActivity.this, true);
        } else {
            Log.d(TAG, "go to main view");
            NavigateManager.gotoMainActivity(LoginActivity.this);
        }
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLoadingDialog.dismiss();
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransition(R.anim.push_bottom_in, R.anim.hold_long);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.hold_long, R.anim.push_bottom_out);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        overridePendingTransition(R.anim.push_bottom_in, R.anim.hold_long);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
        }
    }
}
