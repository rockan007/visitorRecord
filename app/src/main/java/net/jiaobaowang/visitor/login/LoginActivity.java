package net.jiaobaowang.visitor.login;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;

import net.jiaobaowang.visitor.R;
import net.jiaobaowang.visitor.base.FlagObject;
import net.jiaobaowang.visitor.common.VisitorConfig;
import net.jiaobaowang.visitor.entity.SchoolLoginResult;
import net.jiaobaowang.visitor.entity.ShakeHandData;
import net.jiaobaowang.visitor.entity.ShakeHandResult;
import net.jiaobaowang.visitor.utils.SharePreferencesUtil;
import net.jiaobaowang.visitor.utils.Tools;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int REQUEST_FLAG_SHAKEHAND = 0;
    private static final int REQUEST_FLAG_LOGIN = 1;
    OkHttpClient okHttpClient = new OkHttpClient();
    String mUserName;
    String mPassword;
    private int mSchoolId;
    ShakeHandResult mShakeHandResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        findViewById(R.id.login_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUserName = ((EditText) findViewById(R.id.login_userName)).getText().toString();
                mPassword = ((EditText) findViewById(R.id.login_password)).getText().toString();
                mSchoolId = Tools.getSchoolId(LoginActivity.this);
                if (mSchoolId == -1) {
                    Toast.makeText(LoginActivity.this, "请设置学校id", Toast.LENGTH_LONG).show();
                    showSetDialog();
                    return;
                }
                if (mUserName.equals("")) {
                    Toast.makeText(LoginActivity.this, "请输入用户名", Toast.LENGTH_LONG).show();
                    return;
                }
                if (mPassword.equals("")) {
                    Toast.makeText(LoginActivity.this, "请输入密码", Toast.LENGTH_LONG).show();
                    return;
                }
                new LoginTask(LoginActivity.this, REQUEST_FLAG_SHAKEHAND).execute();

            }
        });
        findViewById(R.id.menu_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSetDialog();
            }
        });
    }

    private ShakeHandResult shakeHand() {
        TreeMap<String, String> map = new TreeMap<>();
        map.put("uuid", Tools.getDeviceId(LoginActivity.this));
        map.put("shaketype", "login");
        map.put("appid", Tools.getAppId(LoginActivity.this));
        RequestBody body = null;
        try {
            String a = Tools.getSign(map);
            map.put("sign", a);
            Gson gson = new Gson();
            String json = gson.toJson(map);
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            body = RequestBody.create(JSON, json);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        Request request = new Request.Builder().url("https://jsypay.jiaobaowang.net/useradminwebapi/api/data/ShakeHand").post(body).build();
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                String result = response.body().string();
                Log.d(result, "");
                Gson gson = new Gson();
                ShakeHandResult result1 = gson.fromJson(result, ShakeHandResult.class);
                if (result1.getRspCode().equals("0000")) {
                    Log.d("这事对的吗", result);
                    result1.setFlag(REQUEST_FLAG_SHAKEHAND);
                    return result1;
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SchoolLoginResult schoolLogin(ShakeHandData shakeHandData) throws Exception {
        TreeMap<String, String> map = new TreeMap<>();
        map.put("uuid", Tools.getDeviceId(LoginActivity.this));
        map.put("shaketype", "login");
        map.put("appid", Tools.getAppId(LoginActivity.this));
        map.put("schid", mSchoolId + "");
        map.put("utp", "0");
//        RSAPublicKey key = EncryptUtil.getPublicKey(shakeHandData.getModulus(), shakeHandData.getExponent());
        String uid = Tools.RSAEncrypt(mUserName, shakeHandData);
        map.put("uid", uid);
        String pw = Tools.RSAEncrypt(mPassword, shakeHandData);
        map.put("pw", pw);
        map.put("sign", Tools.getSign(map));
        RequestBody body = null;

        Gson gson = new Gson();
        String json = gson.toJson(map);
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        body = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url("https://jsypay.jiaobaowang.net/useradminwebapi/api/data/Login").post(body).build();
        Response response = okHttpClient.newCall(request).execute();
        if (response.isSuccessful()) {
            String s = response.body().string();
            Log.d(TAG, s);
            SharePreferencesUtil preferencesUtil = new SharePreferencesUtil(LoginActivity.this, VisitorConfig.VISIT_LOCAL_STORAGE);
            preferencesUtil.putString(VisitorConfig.VISIT_LOCAL_USERINFO, s);
            SchoolLoginResult result = gson.fromJson(s, SchoolLoginResult.class);
            result.setFlag(REQUEST_FLAG_LOGIN);
            return result;
        }
        return null;
    }


    private void showSetDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        SharePreferencesUtil preferencesUtil = new SharePreferencesUtil(LoginActivity.this, VisitorConfig.VISIT_LOCAL_STORAGE, false);
        int schoolId = preferencesUtil.getInt(VisitorConfig.VISIT_LOCAL_SCHOOL_ID);
        if (schoolId > 0) {
            editText.setText(String.valueOf(schoolId));
        }
        new AlertDialog.Builder(LoginActivity.this)
                .setTitle("设置学校ID")
                .setView(editText)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (editText.getText().toString().equals("")) {
                            Toast.makeText(LoginActivity.this, "请输入学校ID", Toast.LENGTH_LONG).show();
                            return;
                        }
                        SharePreferencesUtil util = new SharePreferencesUtil(LoginActivity.this, VisitorConfig.VISIT_LOCAL_STORAGE);
                        util.putInt(VisitorConfig.VISIT_LOCAL_SCHOOL_ID, Integer.valueOf(editText.getText().toString()));
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create().show();
    }


    class LoginTask extends AsyncTask<Void, Void, FlagObject> {
        private int flag;
        private Context mContext;

        public LoginTask(Context context, int flag) {
            super();
            mContext = context;
            this.flag = flag;
        }

        @Override
        protected void onPostExecute(FlagObject flagObject) {
            super.onPostExecute(flagObject);
            if (flagObject == null) {
                return;
            }
            Log.d(TAG, "omPostExecute");
            switch (flagObject.getFlag()) {
                case REQUEST_FLAG_SHAKEHAND:
                    mShakeHandResult = (ShakeHandResult) flagObject;
                    if (mShakeHandResult.getRspCode().equals("0000")) {
                        new LoginTask(mContext, REQUEST_FLAG_LOGIN).execute();
                    } else {
                        Toast.makeText(mContext, mShakeHandResult.getRspTxt(), Toast.LENGTH_LONG).show();
                    }
                    break;
                case REQUEST_FLAG_LOGIN:
                    SchoolLoginResult data = (SchoolLoginResult) flagObject;
                    Log.d(TAG, "获取登录信息成功");
                    if (data.getRspCode().equals("0000")) {
                        SharePreferencesUtil util = new SharePreferencesUtil(LoginActivity.this, VisitorConfig.VISIT_LOCAL_STORAGE);
                        util.putString(VisitorConfig.VISIT_LOCAL_TOKEN, data.getRspData().getUtoken());
                        Intent intent = new Intent();
                        intent.setClass(mContext, HomeActivity.class);
                        startActivity(intent);
                    } else if (data.getRspCode().equals("0005")) {
                        Toast.makeText(mContext, "用户名、密码或学校id设置错误", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(mContext, data.getRspTxt(), Toast.LENGTH_LONG).show();
                    }
                    break;
                default:
                    break;

            }
        }

        @Override
        protected FlagObject doInBackground(Void... voids) {
            switch (flag) {
                case REQUEST_FLAG_SHAKEHAND:
                    return shakeHand();
                case REQUEST_FLAG_LOGIN:
                    try {
                        return schoolLogin(mShakeHandResult.getRspData());
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                default:
                    return null;
            }
        }
    }

}

